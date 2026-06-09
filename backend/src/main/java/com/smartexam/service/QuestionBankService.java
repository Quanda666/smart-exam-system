package com.smartexam.service;

import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import com.smartexam.dto.question.QuestionOptionRequest;
import com.smartexam.dto.question.QuestionRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class QuestionBankService {

    private static final List<String> OBJECTIVE_TYPES = List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");
    private static final List<String> ALL_TYPES = List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SUBJECTIVE");
    private static final List<String> ALL_DIFFICULTIES = List.of("EASY", "MEDIUM", "HARD");
    private static final List<String> SOURCE_TYPES = List.of("MANUAL", "AI_GENERATED", "AI_IMPORTED", "AI_MATERIAL", "AI_RAG");

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public QuestionBankService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public List<Map<String, Object>> listQuestions(String keyword, Long subjectId, Long knowledgePointId,
                                                   String questionType, String difficulty, Integer status, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int ownerScope = ownerScope(user);
        Long ownerId = ownerId(user);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT q.id, q.subject_id AS subjectId, s.subject_name AS subjectName,
                       q.knowledge_point_id AS knowledgePointId, kp.point_name AS knowledgePointName,
                       q.question_type AS questionType, q.difficulty, q.stem, q.correct_answer AS correctAnswer,
                       q.analysis, q.default_score AS defaultScore, q.status, q.created_by AS createdBy,
                       q.source_type AS sourceType, q.source_detail AS sourceDetail,
                       q.material_id AS materialId, q.source_page AS sourcePage, q.source_paragraph AS sourceParagraph,
                       q.source_excerpt AS sourceExcerpt, q.ai_model AS aiModel, q.prompt_version AS promptVersion,
                       u.real_name AS creatorName, q.created_at AS createdAt, q.updated_at AS updatedAt
                FROM question q
                JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                LEFT JOIN sys_user u ON u.id = q.created_by
                WHERE q.deleted = 0
                  AND (? = 1 OR q.created_by = ? OR q.status = 1)
                  AND (? IS NULL OR q.subject_id = ?)
                  AND (? IS NULL OR q.knowledge_point_id = ?)
                  AND (? IS NULL OR q.question_type = ?)
                  AND (? IS NULL OR q.difficulty = ?)
                  AND (? IS NULL OR q.status = ?)
                  AND (? IS NULL OR q.stem LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%') OR kp.point_name LIKE CONCAT('%', ?, '%'))
                ORDER BY q.id DESC
                """, ownerScope, ownerId, subjectId, subjectId, knowledgePointId, knowledgePointId, blankToNull(questionType), blankToNull(questionType),
                blankToNull(difficulty), blankToNull(difficulty), status, status, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword));
        rows.forEach(row -> row.put("options", listOptionsFromDatabase(longValue(row.get("id")))));
        return rows;
    }

    public PageResult<Map<String, Object>> listQuestions(String keyword, Long subjectId, Long knowledgePointId,
                                                         String questionType, String difficulty, Integer status,
                                                         int page, int size, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int ownerScope = ownerScope(user);
        Long ownerId = ownerId(user);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM question q
                JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                LEFT JOIN sys_user u ON u.id = q.created_by
                WHERE q.deleted = 0
                  AND (? = 1 OR q.created_by = ? OR q.status = 1)
                  AND (? IS NULL OR q.subject_id = ?)
                  AND (? IS NULL OR q.knowledge_point_id = ?)
                  AND (? IS NULL OR q.question_type = ?)
                  AND (? IS NULL OR q.difficulty = ?)
                  AND (? IS NULL OR q.status = ?)
                  AND (? IS NULL OR q.stem LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%') OR kp.point_name LIKE CONCAT('%', ?, '%'))
                """, Long.class, ownerScope, ownerId, subjectId, subjectId, knowledgePointId, knowledgePointId, blankToNull(questionType), blankToNull(questionType),
                blankToNull(difficulty), blankToNull(difficulty), status, status, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT q.id, q.subject_id AS subjectId, s.subject_name AS subjectName,
                       q.knowledge_point_id AS knowledgePointId, kp.point_name AS knowledgePointName,
                       q.question_type AS questionType, q.difficulty, q.stem, q.correct_answer AS correctAnswer,
                       q.analysis, q.default_score AS defaultScore, q.status, q.created_by AS createdBy,
                       q.source_type AS sourceType, q.source_detail AS sourceDetail,
                       q.material_id AS materialId, q.source_page AS sourcePage, q.source_paragraph AS sourceParagraph,
                       q.source_excerpt AS sourceExcerpt, q.ai_model AS aiModel, q.prompt_version AS promptVersion,
                       u.real_name AS creatorName, q.created_at AS createdAt, q.updated_at AS updatedAt
                FROM question q
                JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                LEFT JOIN sys_user u ON u.id = q.created_by
                WHERE q.deleted = 0
                  AND (? = 1 OR q.created_by = ? OR q.status = 1)
                  AND (? IS NULL OR q.subject_id = ?)
                  AND (? IS NULL OR q.knowledge_point_id = ?)
                  AND (? IS NULL OR q.question_type = ?)
                  AND (? IS NULL OR q.difficulty = ?)
                  AND (? IS NULL OR q.status = ?)
                  AND (? IS NULL OR q.stem LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%') OR kp.point_name LIKE CONCAT('%', ?, '%'))
                ORDER BY q.id DESC
                LIMIT ? OFFSET ?
                """, ownerScope, ownerId, subjectId, subjectId, knowledgePointId, knowledgePointId, blankToNull(questionType), blankToNull(questionType),
                blankToNull(difficulty), blankToNull(difficulty), status, status, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword),
                safeSize, offset);
        rows.forEach(row -> row.put("options", listOptionsFromDatabase(longValue(row.get("id")))));
        return PageResult.of(rows, total == null ? 0 : total, safePage, safeSize);
    }

    public Map<String, Object> createQuestion(QuestionRequest request, AuthUser creator) {
        validateQuestion(request);
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        try {
            jdbcTemplate.update("""
                    INSERT INTO question (subject_id, knowledge_point_id, question_type, difficulty, stem, correct_answer,
                                          analysis, default_score, status, created_by, source_type, source_detail,
                                          material_id, source_page, source_paragraph, source_excerpt, ai_model, prompt_version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, request.getSubjectId(), request.getKnowledgePointId(), normalizeCode(request.getQuestionType()),
                    normalizeCode(request.getDifficulty()), trim(request.getStem()), trim(request.getCorrectAnswer()), trim(request.getAnalysis()),
                    request.getDefaultScore(), request.getStatus(), creator.getId(), questionSourceTypeOrDefault(request.getSourceType()),
                    blankToNull(request.getSourceDetail()), request.getMaterialId(), positiveOrNull(request.getSourcePage()),
                    positiveOrNull(request.getSourceParagraph()), truncate(blankToNull(request.getSourceExcerpt()), 500),
                    truncate(blankToNull(request.getAiModel()), 64), truncate(blankToNull(request.getPromptVersion()), 64));
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            replaceOptionsInDatabase(id, request.getOptions());
            return getQuestionById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("题目已存在或选项标识重复");
        }
    }

    private void requireQuestionOwner(JdbcTemplate jdbcTemplate, Long id, AuthUser user) {
        List<Map<String, Object>> owners = jdbcTemplate.queryForList(
                "SELECT created_by FROM question WHERE id = ? AND deleted = 0", id);
        if (owners.isEmpty()) {
            throw new IllegalArgumentException("题目不存在");
        }
        if (user != null && user.hasRole("ADMIN")) {
            return;
        }
        Object createdBy = owners.get(0).get("created_by");
        if (user == null || createdBy == null || !createdBy.toString().equals(String.valueOf(user.getId()))) {
            throw new IllegalArgumentException("只能管理本人创建的题目");
        }
    }

    public Map<String, Object> updateQuestion(Long id, QuestionRequest request, AuthUser updater) {
        validateQuestion(request);
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireQuestionOwner(jdbcTemplate, id, updater);
        try {
            int rows = jdbcTemplate.update("""
                    UPDATE question
                    SET subject_id = ?, knowledge_point_id = ?, question_type = ?, difficulty = ?, stem = ?, correct_answer = ?,
                        analysis = ?, default_score = ?, status = ?, source_type = COALESCE(?, source_type),
                        source_detail = COALESCE(?, source_detail), material_id = COALESCE(?, material_id),
                        source_page = COALESCE(?, source_page), source_paragraph = COALESCE(?, source_paragraph),
                        source_excerpt = COALESCE(?, source_excerpt), ai_model = COALESCE(?, ai_model),
                        prompt_version = COALESCE(?, prompt_version)
                    WHERE id = ? AND deleted = 0
                    """, request.getSubjectId(), request.getKnowledgePointId(), normalizeCode(request.getQuestionType()),
                    normalizeCode(request.getDifficulty()), trim(request.getStem()), trim(request.getCorrectAnswer()), trim(request.getAnalysis()),
                    request.getDefaultScore(), request.getStatus(), questionSourceTypeOrNull(request.getSourceType()),
                    blankToNull(request.getSourceDetail()), request.getMaterialId(), positiveOrNull(request.getSourcePage()),
                    positiveOrNull(request.getSourceParagraph()), truncate(blankToNull(request.getSourceExcerpt()), 500),
                    truncate(blankToNull(request.getAiModel()), 64), truncate(blankToNull(request.getPromptVersion()), 64), id);
            if (rows == 0) {
                throw new IllegalArgumentException("题目不存在");
            }
            replaceOptionsInDatabase(id, request.getOptions());
            return getQuestionById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("题目已存在或选项标识重复");
        }
    }

    public Map<String, Object> deleteQuestion(Long id, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireQuestionOwner(jdbcTemplate, id, user);
        int rows = jdbcTemplate.update("UPDATE question SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("题目不存在");
        }
        return Map.of("deleted", true, "id", id);
    }

    public Map<String, Object> updateStatus(Long id, Integer status, AuthUser user) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("题目状态只能为0或1");
        }
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireQuestionOwner(jdbcTemplate, id, user);
        int rows = jdbcTemplate.update("UPDATE question SET status = ? WHERE id = ? AND deleted = 0", status, id);
        if (rows == 0) {
            throw new IllegalArgumentException("题目不存在");
        }
        return getQuestionById(id);
    }

    public Map<String, Object> summary(AuthUser user) {
        List<Map<String, Object>> questions = listQuestions(null, null, null, null, null, null, user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", questions.size());
        data.put("published", questions.stream().filter(row -> Objects.equals(intValue(row.get("status")), 1)).count());
        data.put("draft", questions.stream().filter(row -> Objects.equals(intValue(row.get("status")), 0)).count());
        data.put("types", questions.stream().collect(Collectors.groupingBy(row -> String.valueOf(row.get("questionType")), LinkedHashMap::new, Collectors.counting())));
        data.put("difficulties", questions.stream().collect(Collectors.groupingBy(row -> String.valueOf(row.get("difficulty")), LinkedHashMap::new, Collectors.counting())));
        return data;
    }

    private void validateQuestion(QuestionRequest request) {
        String type = normalizeCode(request.getQuestionType());
        String difficulty = normalizeCode(request.getDifficulty());
        if (!ALL_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的题型：" + request.getQuestionType());
        }
        if (!ALL_DIFFICULTIES.contains(difficulty)) {
            throw new IllegalArgumentException("不支持的难度：" + request.getDifficulty());
        }
        validateSubjectExists(request.getSubjectId());
        if (request.getKnowledgePointId() != null) {
            validateKnowledgePointExists(request.getSubjectId(), request.getKnowledgePointId());
        }
        if (OBJECTIVE_TYPES.contains(type)) {
            if (request.getOptions() == null || request.getOptions().isEmpty()) {
                throw new IllegalArgumentException("客观题必须填写选项");
            }
            long correctCount = request.getOptions().stream().filter(option -> Boolean.TRUE.equals(option.getCorrect())).count();
            if ("SINGLE_CHOICE".equals(type) && correctCount != 1) {
                throw new IllegalArgumentException("单选题必须且只能设置一个正确选项");
            }
            if ("MULTIPLE_CHOICE".equals(type) && correctCount < 2) {
                throw new IllegalArgumentException("多选题至少需要两个正确选项");
            }
            if ("TRUE_FALSE".equals(type) && correctCount != 1) {
                throw new IllegalArgumentException("判断题必须且只能设置一个正确选项");
            }
        } else if (trim(request.getCorrectAnswer()) == null || trim(request.getCorrectAnswer()).isBlank()) {
            throw new IllegalArgumentException("非选择题必须填写参考答案");
        }
    }

    private Map<String, Object> getQuestionById(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT q.id, q.subject_id AS subjectId, s.subject_name AS subjectName,
                       q.knowledge_point_id AS knowledgePointId, kp.point_name AS knowledgePointName,
                       q.question_type AS questionType, q.difficulty, q.stem, q.correct_answer AS correctAnswer,
                       q.analysis, q.default_score AS defaultScore, q.status, q.created_by AS createdBy,
                       q.source_type AS sourceType, q.source_detail AS sourceDetail,
                       q.material_id AS materialId, q.source_page AS sourcePage, q.source_paragraph AS sourceParagraph,
                       q.source_excerpt AS sourceExcerpt, q.ai_model AS aiModel, q.prompt_version AS promptVersion,
                       u.real_name AS creatorName, q.created_at AS createdAt, q.updated_at AS updatedAt
                FROM question q
                JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                LEFT JOIN sys_user u ON u.id = q.created_by
                WHERE q.id = ? AND q.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("题目不存在");
        }
        Map<String, Object> row = rows.get(0);
        row.put("options", listOptionsFromDatabase(id));
        return row;
    }

    private List<Map<String, Object>> listOptionsFromDatabase(Long questionId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("""
                SELECT id, question_id AS questionId, option_label AS optionLabel, option_content AS optionContent,
                       is_correct AS correct, sort_order AS sortOrder
                FROM question_option
                WHERE question_id = ?
                ORDER BY sort_order, id
                """, questionId);
    }

    private void replaceOptionsInDatabase(Long questionId, List<QuestionOptionRequest> options) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        jdbcTemplate.update("DELETE FROM question_option WHERE question_id = ?", questionId);
        if (options == null) {
            return;
        }
        int sort = 1;
        for (QuestionOptionRequest option : options) {
            jdbcTemplate.update("""
                    INSERT INTO question_option (question_id, option_label, option_content, is_correct, sort_order)
                    VALUES (?, ?, ?, ?, ?)
                    """, questionId, trim(option.getOptionLabel()), trim(option.getOptionContent()), Boolean.TRUE.equals(option.getCorrect()) ? 1 : 0, sort++);
        }
    }

    private void validateSubjectExists(Long subjectId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edu_subject WHERE id = ? AND deleted = 0", Integer.class, subjectId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("科目不存在");
        }
    }

    private void validateKnowledgePointExists(Long subjectId, Long knowledgePointId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edu_knowledge_point WHERE id = ? AND subject_id = ? AND deleted = 0", Integer.class, knowledgePointId, subjectId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("知识点不存在或不属于当前科目");
        }
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }

    private String normalizeCode(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String questionSourceTypeOrDefault(String value) {
        String sourceType = questionSourceTypeOrNull(value);
        return sourceType == null ? "MANUAL" : sourceType;
    }

    private String questionSourceTypeOrNull(String value) {
        String sourceType = normalizeCode(value);
        if (sourceType == null || sourceType.isBlank()) {
            return null;
        }
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalArgumentException("不支持的题目来源：" + value);
        }
        return sourceType;
    }

    private String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 数据隔离范围标记：管理员或系统级调用（user 为 null）返回 1，表示不受限、可见全部题目；
     * 普通教师返回 0，配合 SQL 中的 {@code (? = 1 OR q.created_by = ? OR q.status = 1)}
     * 仅可见本人创建或已发布（status=1）的题目。
     */
    private int ownerScope(AuthUser user) {
        return user == null || user.hasRole("ADMIN") ? 1 : 0;
    }

    private Long ownerId(AuthUser user) {
        return user == null ? null : user.getId();
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
