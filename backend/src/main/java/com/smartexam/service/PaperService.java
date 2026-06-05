package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.paper.GeneratePaperRequest;
import com.smartexam.dto.paper.GenerateRuleRequest;
import com.smartexam.dto.paper.PaperQuestionRequest;
import com.smartexam.dto.paper.PaperRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class PaperService {

    private static final List<String> ALL_TYPES = List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SUBJECTIVE");
    private static final List<String> ALL_DIFFICULTIES = List.of("EASY", "MEDIUM", "HARD");

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final QuestionBankService questionBankService;
    private final ConcurrentMap<Long, Map<String, Object>> fallbackPapers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, List<Map<String, Object>>> fallbackPaperQuestions = new ConcurrentHashMap<>();
    private final AtomicLong fallbackPaperId = new AtomicLong(10);
    private final AtomicLong fallbackPaperQuestionId = new AtomicLong(100);

    public PaperService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, QuestionBankService questionBankService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.questionBankService = questionBankService;
        seedFallbackData();
    }

    public Map<String, Object> summary() {
        List<Map<String, Object>> papers = listPapers(null, null, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", papers.size());
        data.put("published", papers.stream().filter(row -> Objects.equals(intValue(row.get("status")), 1)).count());
        data.put("draft", papers.stream().filter(row -> Objects.equals(intValue(row.get("status")), 0)).count());
        data.put("totalQuestions", papers.stream().mapToInt(row -> intValue(row.getOrDefault("questionCount", 0))).sum());
        return data;
    }

    public List<Map<String, Object>> listPapers(String keyword, Long subjectId, Integer status) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                return jdbcTemplate.queryForList("""
                        SELECT p.id, p.subject_id AS subjectId, s.subject_name AS subjectName, p.paper_name AS paperName,
                               p.description, p.total_score AS totalScore, p.status, p.created_by AS createdBy,
                               u.real_name AS creatorName, p.created_at AS createdAt, p.updated_at AS updatedAt,
                               COUNT(pq.id) AS questionCount
                        FROM paper p
                        JOIN edu_subject s ON s.id = p.subject_id
                        LEFT JOIN sys_user u ON u.id = p.created_by
                        LEFT JOIN paper_question pq ON pq.paper_id = p.id
                        WHERE p.deleted = 0
                          AND (? IS NULL OR p.subject_id = ?)
                          AND (? IS NULL OR p.status = ?)
                          AND (? IS NULL OR p.paper_name LIKE CONCAT('%', ?, '%') OR p.description LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%'))
                        GROUP BY p.id, p.subject_id, s.subject_name, p.paper_name, p.description, p.total_score, p.status,
                                 p.created_by, u.real_name, p.created_at, p.updated_at
                        ORDER BY p.id DESC
                        """, subjectId, subjectId, status, status, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword));
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据，保证阶段 5 可本地演示。
            }
        }
        return fallbackPapers.values().stream()
                .filter(row -> subjectId == null || Objects.equals(longValue(row.get("subjectId")), subjectId))
                .filter(row -> status == null || Objects.equals(intValue(row.get("status")), status))
                .filter(row -> matchesKeyword(row, keyword, "paperName", "description", "subjectName"))
                .sorted(Comparator.comparing(row -> -longValue(row.get("id"))))
                .map(this::copyPaperSummary)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getPaper(Long id) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                return getPaperById(id);
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        return copyPaperWithQuestions(requireFallbackPaper(id));
    }

    public Map<String, Object> createPaper(PaperRequest request, AuthUser creator) {
        validatePaperRequest(request);
        BigDecimal totalScore = totalScore(request.getQuestions());
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO paper (subject_id, paper_name, description, total_score, status, created_by)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, request.getSubjectId(), trim(request.getPaperName()), trim(request.getDescription()), totalScore, request.getStatus(), creator.getId());
                Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                replacePaperQuestionsInDatabase(id, request.getSubjectId(), request.getQuestions());
                refreshTotalScoreInDatabase(id);
                return getPaperById(id);
            } catch (DuplicateKeyException ex) {
                throw new IllegalArgumentException("试卷名称已存在");
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        ensureUniqueFallbackPaperName(request.getPaperName(), null);
        long id = fallbackPaperId.incrementAndGet();
        Map<String, Object> row = buildFallbackPaperRow(id, request, creator, totalScore);
        fallbackPapers.put(id, row);
        fallbackPaperQuestions.put(id, buildFallbackPaperQuestions(id, request.getSubjectId(), request.getQuestions()));
        return copyPaperWithQuestions(row);
    }

    public Map<String, Object> updatePaper(Long id, PaperRequest request, AuthUser updater) {
        validatePaperRequest(request);
        BigDecimal totalScore = totalScore(request.getQuestions());
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                int rows = jdbcTemplate.update("""
                        UPDATE paper
                        SET subject_id = ?, paper_name = ?, description = ?, total_score = ?, status = ?
                        WHERE id = ? AND deleted = 0
                        """, request.getSubjectId(), trim(request.getPaperName()), trim(request.getDescription()), totalScore, request.getStatus(), id);
                if (rows == 0) {
                    throw new IllegalArgumentException("试卷不存在");
                }
                replacePaperQuestionsInDatabase(id, request.getSubjectId(), request.getQuestions());
                refreshTotalScoreInDatabase(id);
                return getPaperById(id);
            } catch (DuplicateKeyException ex) {
                throw new IllegalArgumentException("试卷名称已存在");
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        Map<String, Object> row = requireFallbackPaper(id);
        ensureUniqueFallbackPaperName(request.getPaperName(), id);
        row.putAll(buildFallbackPaperRow(id, request, updater, totalScore));
        row.put("updatedAt", LocalDateTime.now());
        fallbackPaperQuestions.put(id, buildFallbackPaperQuestions(id, request.getSubjectId(), request.getQuestions()));
        row.put("questionCount", request.getQuestions().size());
        return copyPaperWithQuestions(row);
    }

    public Map<String, Object> updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("试卷状态只能为0或1");
        }
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                int rows = jdbcTemplate.update("UPDATE paper SET status = ? WHERE id = ? AND deleted = 0", status, id);
                if (rows == 0) {
                    throw new IllegalArgumentException("试卷不存在");
                }
                return getPaperById(id);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        Map<String, Object> row = requireFallbackPaper(id);
        row.put("status", status);
        row.put("updatedAt", LocalDateTime.now());
        return copyPaperWithQuestions(row);
    }

    public Map<String, Object> deletePaper(Long id) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                int rows = jdbcTemplate.update("UPDATE paper SET deleted = 1 WHERE id = ? AND deleted = 0", id);
                if (rows == 0) {
                    throw new IllegalArgumentException("试卷不存在");
                }
                return Map.of("deleted", true, "id", id);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        requireFallbackPaper(id);
        fallbackPapers.remove(id);
        fallbackPaperQuestions.remove(id);
        return Map.of("deleted", true, "id", id);
    }

    public Map<String, Object> generatePaper(GeneratePaperRequest request, AuthUser creator) {
        PaperRequest paperRequest = new PaperRequest();
        paperRequest.setSubjectId(request.getSubjectId());
        paperRequest.setPaperName(request.getPaperName());
        paperRequest.setDescription(request.getDescription());
        paperRequest.setStatus(request.getStatus());
        paperRequest.setQuestions(generateQuestionsByRules(request));
        return createPaper(paperRequest, creator);
    }

    private List<PaperQuestionRequest> generateQuestionsByRules(GeneratePaperRequest request) {
        validateSubjectExists(request.getSubjectId());
        Set<Long> selectedIds = new HashSet<>();
        List<PaperQuestionRequest> selected = new ArrayList<>();
        int sort = 1;
        for (GenerateRuleRequest rule : request.getRules()) {
            String type = normalizeCode(rule.getQuestionType());
            String difficulty = normalizeCode(rule.getDifficulty());
            if (!ALL_TYPES.contains(type)) {
                throw new IllegalArgumentException("不支持的题型：" + rule.getQuestionType());
            }
            if (difficulty != null && !ALL_DIFFICULTIES.contains(difficulty)) {
                throw new IllegalArgumentException("不支持的难度：" + rule.getDifficulty());
            }
            List<Map<String, Object>> candidates = questionBankService.listQuestions(null, request.getSubjectId(), rule.getKnowledgePointId(), type, difficulty, 1).stream()
                    .filter(row -> selectedIds.add(longValue(row.get("id"))))
                    .sorted(Comparator.comparing(row -> longValue(row.get("id"))))
                    .limit(rule.getCount())
                    .toList();
            if (candidates.size() < rule.getCount()) {
                throw new IllegalArgumentException("组卷题量不足：题型" + type + "需要" + rule.getCount() + "题，实际可用" + candidates.size() + "题");
            }
            for (Map<String, Object> candidate : candidates) {
                PaperQuestionRequest question = new PaperQuestionRequest();
                question.setQuestionId(longValue(candidate.get("id")));
                question.setScore(rule.getScore());
                question.setSortOrder(sort++);
                selected.add(question);
            }
        }
        return selected;
    }

    private void validatePaperRequest(PaperRequest request) {
        validateSubjectExists(request.getSubjectId());
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("试卷至少需要一道题目");
        }
        Set<Long> questionIds = new HashSet<>();
        int sort = 1;
        for (PaperQuestionRequest question : request.getQuestions()) {
            if (!questionIds.add(question.getQuestionId())) {
                throw new IllegalArgumentException("试卷不能包含重复题目");
            }
            validateQuestionUsable(request.getSubjectId(), question.getQuestionId());
            if (question.getSortOrder() == null || question.getSortOrder() <= 0) {
                question.setSortOrder(sort);
            }
            sort++;
        }
    }

    private void validateQuestionUsable(Long subjectId, Long questionId) {
        Map<String, Object> question = findQuestion(questionId);
        if (question == null) {
            throw new IllegalArgumentException("题目不存在");
        }
        if (!Objects.equals(longValue(question.get("subjectId")), subjectId)) {
            throw new IllegalArgumentException("题目不属于当前试卷科目");
        }
        if (!Objects.equals(intValue(question.get("status")), 1)) {
            throw new IllegalArgumentException("草稿题目不可组卷");
        }
    }

    private Map<String, Object> findQuestion(Long questionId) {
        return questionBankService.listQuestions(null, null, null, null, null, null).stream()
                .filter(row -> Objects.equals(longValue(row.get("id")), questionId))
                .findFirst()
                .orElse(null);
    }

    private void validateSubjectExists(Long subjectId) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edu_subject WHERE id = ? AND deleted = 0", Integer.class, subjectId);
                if (count == null || count == 0) {
                    throw new IllegalArgumentException("科目不存在");
                }
                return;
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        if (subjectId == null || (subjectId != 1L && subjectId != 2L)) {
            throw new IllegalArgumentException("科目不存在");
        }
    }

    private Map<String, Object> getPaperById(Long id) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.id, p.subject_id AS subjectId, s.subject_name AS subjectName, p.paper_name AS paperName,
                       p.description, p.total_score AS totalScore, p.status, p.created_by AS createdBy,
                       u.real_name AS creatorName, p.created_at AS createdAt, p.updated_at AS updatedAt
                FROM paper p
                JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN sys_user u ON u.id = p.created_by
                WHERE p.id = ? AND p.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("试卷不存在");
        }
        Map<String, Object> row = rows.get(0);
        row.put("questions", listPaperQuestionsFromDatabase(id));
        row.put("questionCount", ((List<?>) row.get("questions")).size());
        return row;
    }

    private List<Map<String, Object>> listPaperQuestionsFromDatabase(Long paperId) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        return jdbcTemplate.queryForList("""
                SELECT pq.id, pq.paper_id AS paperId, pq.question_id AS questionId, pq.score, pq.sort_order AS sortOrder,
                       q.question_type AS questionType, q.difficulty, q.stem, q.analysis,
                       q.subject_id AS subjectId, s.subject_name AS subjectName,
                       q.knowledge_point_id AS knowledgePointId, kp.point_name AS knowledgePointName
                FROM paper_question pq
                JOIN question q ON q.id = pq.question_id
                JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                WHERE pq.paper_id = ? AND q.deleted = 0
                ORDER BY pq.sort_order, pq.id
                """, paperId);
    }

    private void replacePaperQuestionsInDatabase(Long paperId, Long subjectId, List<PaperQuestionRequest> questions) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        jdbcTemplate.update("DELETE FROM paper_question WHERE paper_id = ?", paperId);
        for (PaperQuestionRequest question : questions) {
            validateQuestionUsable(subjectId, question.getQuestionId());
            jdbcTemplate.update("""
                    INSERT INTO paper_question (paper_id, question_id, score, sort_order)
                    VALUES (?, ?, ?, ?)
                    """, paperId, question.getQuestionId(), question.getScore(), question.getSortOrder());
        }
    }

    private void refreshTotalScoreInDatabase(Long paperId) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        BigDecimal totalScore = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(score), 0) FROM paper_question WHERE paper_id = ?", BigDecimal.class, paperId);
        jdbcTemplate.update("UPDATE paper SET total_score = ? WHERE id = ?", totalScore, paperId);
    }

    private Map<String, Object> buildFallbackPaperRow(Long id, PaperRequest request, AuthUser creator, BigDecimal totalScore) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("subjectId", request.getSubjectId());
        row.put("subjectName", subjectNameOf(request.getSubjectId()));
        row.put("paperName", trim(request.getPaperName()));
        row.put("description", trim(request.getDescription()));
        row.put("totalScore", totalScore);
        row.put("status", request.getStatus());
        row.put("createdBy", creator.getId());
        row.put("creatorName", creator.getRealName());
        row.put("createdAt", LocalDateTime.now());
        row.put("updatedAt", LocalDateTime.now());
        row.put("questionCount", request.getQuestions().size());
        return row;
    }

    private List<Map<String, Object>> buildFallbackPaperQuestions(Long paperId, Long subjectId, List<PaperQuestionRequest> questions) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PaperQuestionRequest questionRequest : questions) {
            validateQuestionUsable(subjectId, questionRequest.getQuestionId());
            Map<String, Object> question = findQuestion(questionRequest.getQuestionId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", fallbackPaperQuestionId.incrementAndGet());
            row.put("paperId", paperId);
            row.put("questionId", questionRequest.getQuestionId());
            row.put("score", questionRequest.getScore());
            row.put("sortOrder", questionRequest.getSortOrder());
            row.put("questionType", question.get("questionType"));
            row.put("difficulty", question.get("difficulty"));
            row.put("stem", question.get("stem"));
            row.put("analysis", question.get("analysis"));
            row.put("subjectId", question.get("subjectId"));
            row.put("subjectName", question.get("subjectName"));
            row.put("knowledgePointId", question.get("knowledgePointId"));
            row.put("knowledgePointName", question.get("knowledgePointName"));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> copyPaperSummary(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>(source);
        Long id = longValue(row.get("id"));
        row.put("questionCount", fallbackPaperQuestions.getOrDefault(id, List.of()).size());
        return row;
    }

    private Map<String, Object> copyPaperWithQuestions(Map<String, Object> source) {
        Map<String, Object> row = copyPaperSummary(source);
        Long id = longValue(row.get("id"));
        row.put("questions", fallbackPaperQuestions.getOrDefault(id, List.of()).stream().map(LinkedHashMap::new).collect(Collectors.toList()));
        return row;
    }

    private Map<String, Object> requireFallbackPaper(Long id) {
        Map<String, Object> row = fallbackPapers.get(id);
        if (row == null) {
            throw new IllegalArgumentException("试卷不存在");
        }
        return row;
    }

    private void ensureUniqueFallbackPaperName(String paperName, Long excludeId) {
        String normalized = trim(paperName);
        boolean exists = fallbackPapers.values().stream()
                .anyMatch(row -> !Objects.equals(longValue(row.get("id")), excludeId) && normalized.equals(row.get("paperName")));
        if (exists) {
            throw new IllegalArgumentException("试卷名称已存在");
        }
    }

    private BigDecimal totalScore(List<PaperQuestionRequest> questions) {
        return questions.stream().map(PaperQuestionRequest::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void seedFallbackData() {
        PaperRequest request = new PaperRequest();
        request.setSubjectId(1L);
        request.setPaperName("Java程序设计阶段5演示试卷");
        request.setDescription("用于演示手动组卷和规则组卷的阶段5试卷。 ");
        request.setStatus(0);
        PaperQuestionRequest question = new PaperQuestionRequest();
        question.setQuestionId(1L);
        question.setScore(BigDecimal.valueOf(5));
        question.setSortOrder(1);
        request.setQuestions(List.of(question));
        AuthUser teacher = new AuthUser(2L, "teacher1", "演示教师一", List.of("TEACHER"), Map.of("teacher_no", "T2024001"));
        fallbackPapers.put(1L, buildFallbackPaperRow(1L, request, teacher, totalScore(request.getQuestions())));
        fallbackPaperQuestions.put(1L, buildFallbackPaperQuestions(1L, request.getSubjectId(), request.getQuestions()));
    }

    private boolean matchesKeyword(Map<String, Object> row, String keyword, String... fields) {
        String normalized = blankToNull(keyword);
        if (normalized == null) {
            return true;
        }
        String lowerKeyword = normalized.toLowerCase(Locale.ROOT);
        for (String field : fields) {
            Object value = row.get(field);
            if (value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                return true;
            }
        }
        return false;
    }

    private String subjectNameOf(Long subjectId) {
        if (subjectId == null) {
            return "未知科目";
        }
        if (subjectId == 1L) {
            return "Java程序设计";
        }
        if (subjectId == 2L) {
            return "数据库系统";
        }
        return "阶段5测试科目";
    }

    private String normalizeCode(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
