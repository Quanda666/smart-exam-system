package com.smartexam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartexam.dto.ai.AiGeneratedQuestion;
import com.smartexam.dto.ai.MaterialQuestionGenerationRequest;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.material.MaterialQuestionFromLibraryRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MaterialLibraryService {

    private static final int MAX_CONTEXT_LENGTH = 18_000;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final DocumentTextExtractorService documentTextExtractorService;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public MaterialLibraryService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                  DocumentTextExtractorService documentTextExtractorService,
                                  AiService aiService,
                                  ObjectMapper objectMapper) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.documentTextExtractorService = documentTextExtractorService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> uploadMaterial(MultipartFile file, Long subjectId, String title, AuthUser user) {
        JdbcTemplate jdbc = requireJdbcTemplate();
        validateSubject(jdbc, subjectId);
        String text = documentTextExtractorService.extract(file);
        String filename = safeFilename(file.getOriginalFilename());
        String materialTitle = firstNonBlank(title, filename.isBlank() ? "未命名课程资料" : filename);
        List<MaterialChunk> chunks = buildChunks(text);
        List<Map<String, Object>> outline = aiService.generateMaterialOutline(materialTitle, materialContext(chunks));

        jdbc.update("""
                INSERT INTO course_material (subject_id, title, file_name, file_type, content_text, outline_json, uploaded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, subjectId, truncate(materialTitle, 200), truncate(filename, 255), extensionOf(filename),
                text, toJson(outline), user.getId());
        Long materialId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        for (MaterialChunk chunk : chunks) {
            jdbc.update("""
                    INSERT INTO course_material_chunk (material_id, chunk_order, page_no, paragraph_no, heading, content, keywords)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, materialId, chunk.order(), chunk.pageNo(), chunk.paragraphNo(), truncate(chunk.heading(), 200),
                    chunk.content(), truncate(chunk.keywords(), 500));
        }
        insertOutline(jdbc, materialId, outline);
        return getMaterialDetail(materialId, user);
    }

    public List<Map<String, Object>> listMaterials(String keyword, Long subjectId, AuthUser user) {
        JdbcTemplate jdbc = requireJdbcTemplate();
        String kw = blankToNull(keyword);
        int globalScope = user.hasRole("ADMIN") ? 1 : 0;
        return jdbc.queryForList("""
                SELECT m.id, m.subject_id AS subjectId, s.subject_name AS subjectName, m.title,
                       m.file_name AS fileName, m.file_type AS fileType, m.uploaded_by AS uploadedBy,
                       u.real_name AS uploaderName, m.status, m.created_at AS createdAt, m.updated_at AS updatedAt,
                       (SELECT COUNT(*) FROM course_material_chunk c WHERE c.material_id = m.id) AS chunkCount,
                       (SELECT COUNT(*) FROM course_material_outline o WHERE o.material_id = m.id) AS outlineCount
                FROM course_material m
                JOIN edu_subject s ON s.id = m.subject_id
                LEFT JOIN sys_user u ON u.id = m.uploaded_by
                WHERE m.deleted = 0
                  AND (? = 1 OR m.uploaded_by = ?)
                  AND (? IS NULL OR m.subject_id = ?)
                  AND (? IS NULL OR m.title LIKE CONCAT('%', ?, '%') OR m.file_name LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%'))
                ORDER BY m.id DESC
                """, globalScope, user.getId(), subjectId, subjectId, kw, kw, kw, kw);
    }

    public Map<String, Object> getMaterialDetail(Long materialId, AuthUser user) {
        JdbcTemplate jdbc = requireJdbcTemplate();
        Map<String, Object> material = getMaterialRow(jdbc, materialId, user);
        material.put("outline", jdbc.queryForList("""
                SELECT id, outline_order AS outlineOrder, title, summary, keywords,
                       source_page AS sourcePage, source_paragraph AS sourceParagraph
                FROM course_material_outline
                WHERE material_id = ?
                ORDER BY outline_order, id
                """, materialId));
        material.put("chunks", jdbc.queryForList("""
                SELECT id, chunk_order AS chunkOrder, page_no AS pageNo, paragraph_no AS paragraphNo,
                       heading, content, keywords
                FROM course_material_chunk
                WHERE material_id = ?
                ORDER BY chunk_order, id
                """, materialId));
        return material;
    }

    public List<AiGeneratedQuestion> generateQuestions(Long materialId,
                                                       MaterialQuestionFromLibraryRequest request,
                                                       AuthUser user) {
        JdbcTemplate jdbc = requireJdbcTemplate();
        Map<String, Object> material = getMaterialRow(jdbc, materialId, user);
        List<MaterialChunk> chunks = listChunks(jdbc, materialId);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("资料未生成有效分段，请重新上传");
        }
        List<MaterialChunk> selected = selectChunks(chunks, request);
        MaterialQuestionGenerationRequest aiRequest = toAiRequest(material, request);
        List<AiGeneratedQuestion> questions = aiService.generateQuestionDraftsFromMaterial(materialContext(selected), aiRequest);
        for (int i = 0; i < questions.size(); i++) {
            AiGeneratedQuestion question = questions.get(i);
            MaterialChunk chunk = selected.get(i % selected.size());
            question.setSourceType("AI_RAG");
            question.setSourceDetail("资料库生成：" + material.get("title"));
            question.setMaterialId(materialId);
            if (question.getSourcePage() == null) {
                question.setSourcePage(chunk.pageNo());
            }
            if (question.getSourceParagraph() == null) {
                question.setSourceParagraph(chunk.paragraphNo());
            }
            if (blankToNull(question.getSourceExcerpt()) == null) {
                question.setSourceExcerpt(truncate(chunk.content().replaceAll("\\s+", " "), 500));
            }
            question.setAiModel(aiService.currentModel());
            question.setPromptVersion(AiService.PROMPT_VERSION_MATERIAL_GENERATE);
        }
        return questions;
    }

    private Map<String, Object> getMaterialRow(JdbcTemplate jdbc, Long materialId, AuthUser user) {
        int globalScope = user.hasRole("ADMIN") ? 1 : 0;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.id, m.subject_id AS subjectId, s.subject_name AS subjectName, m.title,
                       m.file_name AS fileName, m.file_type AS fileType, m.content_text AS contentText,
                       m.outline_json AS outlineJson, m.uploaded_by AS uploadedBy, u.real_name AS uploaderName,
                       m.status, m.created_at AS createdAt, m.updated_at AS updatedAt
                FROM course_material m
                JOIN edu_subject s ON s.id = m.subject_id
                LEFT JOIN sys_user u ON u.id = m.uploaded_by
                WHERE m.id = ? AND m.deleted = 0 AND (? = 1 OR m.uploaded_by = ?)
                """, materialId, globalScope, user.getId());
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("资料不存在或无权访问");
        }
        return new LinkedHashMap<>(rows.get(0));
    }

    private List<MaterialChunk> listChunks(JdbcTemplate jdbc, Long materialId) {
        return jdbc.query("""
                SELECT chunk_order, page_no, paragraph_no, heading, content, keywords
                FROM course_material_chunk
                WHERE material_id = ?
                ORDER BY chunk_order, id
                """, (rs, rowNum) -> new MaterialChunk(
                rs.getInt("chunk_order"),
                rs.getInt("page_no"),
                rs.getInt("paragraph_no"),
                rs.getString("heading"),
                rs.getString("content"),
                rs.getString("keywords")
        ), materialId);
    }

    private List<MaterialChunk> buildChunks(String text) {
        List<MaterialChunk> chunks = new ArrayList<>();
        String[] lines = text.split("\\R");
        String heading = null;
        StringBuilder buffer = new StringBuilder();
        int paragraph = 0;
        int startParagraph = 1;
        for (String raw : lines) {
            String line = raw.replaceAll("\\s+", " ").trim();
            if (line.isBlank()) {
                continue;
            }
            paragraph++;
            if (looksLikeHeading(line)) {
                if (!buffer.isEmpty()) {
                    chunks.add(chunk(chunks.size() + 1, startParagraph, heading, buffer.toString()));
                    buffer.setLength(0);
                }
                heading = line;
                startParagraph = paragraph;
                continue;
            }
            if (buffer.isEmpty()) {
                startParagraph = paragraph;
            }
            if (buffer.length() + line.length() > 1200) {
                chunks.add(chunk(chunks.size() + 1, startParagraph, heading, buffer.toString()));
                buffer.setLength(0);
                startParagraph = paragraph;
            }
            buffer.append(buffer.isEmpty() ? "" : "\n").append(line);
        }
        if (!buffer.isEmpty()) {
            chunks.add(chunk(chunks.size() + 1, startParagraph, heading, buffer.toString()));
        }
        if (chunks.isEmpty() && !text.isBlank()) {
            chunks.add(chunk(1, 1, heading, text));
        }
        return chunks;
    }

    private MaterialChunk chunk(int order, int paragraph, String heading, String content) {
        int pageNo = Math.max(1, (paragraph - 1) / 6 + 1);
        String normalized = truncate(content.trim(), 4000);
        return new MaterialChunk(order, pageNo, Math.max(1, paragraph), heading, normalized, keywordsOf(heading + " " + normalized));
    }

    private List<MaterialChunk> selectChunks(List<MaterialChunk> chunks, MaterialQuestionFromLibraryRequest request) {
        String query = (safeText(request.getKnowledgePointName()) + " " + safeText(request.getRequirements())).trim();
        if (query.isBlank()) {
            return chunks.stream().limit(12).toList();
        }
        List<MaterialChunk> selected = chunks.stream()
                .sorted(Comparator.comparingInt((MaterialChunk chunk) -> score(chunk, query)).reversed())
                .limit(12)
                .sorted(Comparator.comparingInt(MaterialChunk::order))
                .toList();
        return selected.isEmpty() ? chunks.stream().limit(12).toList() : selected;
    }

    private int score(MaterialChunk chunk, String query) {
        String haystack = (safeText(chunk.heading()) + " " + safeText(chunk.keywords()) + " " + safeText(chunk.content())).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : query.toLowerCase(Locale.ROOT).split("\\s+|,|，|、")) {
            if (term.length() >= 2 && haystack.contains(term)) {
                score += term.length();
            }
        }
        return score;
    }

    private String materialContext(List<MaterialChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        for (MaterialChunk chunk : chunks) {
            if (builder.length() >= MAX_CONTEXT_LENGTH) {
                break;
            }
            builder.append("[页").append(chunk.pageNo()).append(" 段").append(chunk.paragraphNo()).append("] ");
            if (blankToNull(chunk.heading()) != null) {
                builder.append(chunk.heading()).append("\n");
            }
            builder.append(chunk.content()).append("\n\n");
        }
        String context = builder.toString().trim();
        return context.length() > MAX_CONTEXT_LENGTH ? context.substring(0, MAX_CONTEXT_LENGTH) : context;
    }

    private MaterialQuestionGenerationRequest toAiRequest(Map<String, Object> material,
                                                          MaterialQuestionFromLibraryRequest request) {
        int total = request.getTypeCounts().values().stream().mapToInt(value -> Math.max(0, value == null ? 0 : value)).sum();
        if (total <= 0) {
            throw new IllegalArgumentException("请至少设置一种题型数量");
        }
        MaterialQuestionGenerationRequest aiRequest = new MaterialQuestionGenerationRequest();
        aiRequest.setSubjectId(longValue(material.get("subjectId")));
        aiRequest.setSubjectName(String.valueOf(material.get("subjectName")));
        aiRequest.setKnowledgePointId(request.getKnowledgePointId());
        aiRequest.setKnowledgePointName(request.getKnowledgePointName());
        aiRequest.setDifficulty(firstNonBlank(request.getDifficulty(), "MEDIUM"));
        aiRequest.setDefaultScore(request.getDefaultScore() == null ? BigDecimal.valueOf(5) : request.getDefaultScore());
        aiRequest.setRequirements(request.getRequirements());
        aiRequest.setTypeCounts(request.getTypeCounts());
        return aiRequest;
    }

    private void insertOutline(JdbcTemplate jdbc, Long materialId, List<Map<String, Object>> outline) {
        int order = 1;
        for (Map<String, Object> item : outline) {
            jdbc.update("""
                    INSERT INTO course_material_outline (material_id, outline_order, title, summary, keywords, source_page, source_paragraph)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, materialId, order++, truncate(String.valueOf(item.getOrDefault("title", "知识点")), 200),
                    truncate(stringValue(item.get("summary")), 1000), truncate(stringValue(item.get("keywords")), 500),
                    intValue(item.get("sourcePage")), intValue(item.get("sourceParagraph")));
        }
    }

    private void validateSubject(JdbcTemplate jdbc, Long subjectId) {
        if (subjectId == null) {
            throw new IllegalArgumentException("请先选择科目");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM edu_subject WHERE id = ? AND deleted = 0", Integer.class, subjectId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("科目不存在");
        }
    }

    private boolean looksLikeHeading(String line) {
        return line.length() >= 4 && line.length() <= 60
                && !line.endsWith("。")
                && line.matches("^(第[一二三四五六七八九十\\d]+[章节].*|[一二三四五六七八九十]+[、.].*|\\d+(?:\\.\\d+)*[、.\\s].*|[#*-]+\\s*.+|.*(概述|原理|流程|机制|方法|应用|特性|结构|案例|总结))$");
    }

    private String keywordsOf(String text) {
        String cleaned = safeText(text).replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5]+", " ").trim();
        List<String> keywords = new ArrayList<>();
        for (String part : cleaned.split("\\s+")) {
            if (part.length() >= 2 && keywords.stream().noneMatch(part::equalsIgnoreCase)) {
                keywords.add(part);
            }
            if (keywords.size() >= 8) {
                break;
            }
        }
        return String.join(",", keywords);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (value == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String extensionOf(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String safeFilename(String filename) {
        return filename == null ? "" : filename.trim();
    }

    private String firstNonBlank(String first, String fallback) {
        String trimmed = blankToNull(first);
        return trimmed == null ? fallback : trimmed;
    }

    private String blankToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record MaterialChunk(int order, int pageNo, int paragraphNo, String heading, String content, String keywords) {
    }
}
