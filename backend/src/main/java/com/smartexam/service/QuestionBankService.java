package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.question.QuestionOptionRequest;
import com.smartexam.dto.question.QuestionRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class QuestionBankService {

    private static final List<String> OBJECTIVE_TYPES = List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");
    private static final List<String> ALL_TYPES = List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SUBJECTIVE");
    private static final List<String> ALL_DIFFICULTIES = List.of("EASY", "MEDIUM", "HARD");

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ConcurrentMap<Long, Map<String, Object>> fallbackQuestions = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, List<Map<String, Object>>> fallbackOptions = new ConcurrentHashMap<>();
    private final AtomicLong fallbackQuestionId = new AtomicLong(10);
    private final AtomicLong fallbackOptionId = new AtomicLong(100);

    public QuestionBankService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        seedFallbackData();
    }

    public List<Map<String, Object>> listQuestions(String keyword, Long subjectId, Long knowledgePointId,
                                                   String questionType, String difficulty, Integer status) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT q.id, q.subject_id AS subjectId, s.subject_name AS subjectName,
                               q.knowledge_point_id AS knowledgePointId, kp.point_name AS knowledgePointName,
                               q.question_type AS questionType, q.difficulty, q.stem, q.correct_answer AS correctAnswer,
                               q.analysis, q.default_score AS defaultScore, q.status, q.created_by AS createdBy,
                               u.real_name AS creatorName, q.created_at AS createdAt, q.updated_at AS updatedAt
                        FROM question q
                        JOIN edu_subject s ON s.id = q.subject_id
                        LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                        LEFT JOIN sys_user u ON u.id = q.created_by
                        WHERE q.deleted = 0
                          AND (? IS NULL OR q.subject_id = ?)
                          AND (? IS NULL OR q.knowledge_point_id = ?)
                          AND (? IS NULL OR q.question_type = ?)
                          AND (? IS NULL OR q.difficulty = ?)
                          AND (? IS NULL OR q.status = ?)
                          AND (? IS NULL OR q.stem LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%') OR kp.point_name LIKE CONCAT('%', ?, '%'))
                        ORDER BY q.id DESC
                        """, subjectId, subjectId, knowledgePointId, knowledgePointId, blankToNull(questionType), blankToNull(questionType),
                        blankToNull(difficulty), blankToNull(difficulty), status, status, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword));
                rows.forEach(row -> row.put("options", listOptionsFromDatabase(longValue(row.get("id")))));
                return rows;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据，保证阶段 4 可本地演示。
            }
        }
        return fallbackQuestions.values().stream()
                .filter(row -> subjectId == null || Objects.equals(longValue(row.get("subjectId")), subjectId))
                .filter(row -> knowledgePointId == null || Objects.equals(longValue(row.get("knowledgePointId")), knowledgePointId))
                .filter(row -> blankToNull(questionType) == null || blankToNull(questionType).equals(row.get("questionType")))
                .filter(row -> blankToNull(difficulty) == null || blankToNull(difficulty).equals(row.get("difficulty")))
                .filter(row -> status == null || Objects.equals(intValue(row.get("status")), status))
                .filter(row -> matchesKeyword(row, keyword, "stem", "subjectName", "knowledgePointName"))
                .sorted(Comparator.comparing(row -> -longValue(row.get("id"))))
                .map(this::copyQuestionWithOptions)
                .collect(Collectors.toList());
    }

    public Map<String, Object> createQuestion(QuestionRequest request, AuthUser creator) {
        validateQuestion(request);
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO question (subject_id, knowledge_point_id, question_type, difficulty, stem, correct_answer,
                                              analysis, default_score, status, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, request.getSubjectId(), request.getKnowledgePointId(), normalizeCode(request.getQuestionType()),
                        normalizeCode(request.getDifficulty()), trim(request.getStem()), trim(request.getCorrectAnswer()), trim(request.getAnalysis()),
                        request.getDefaultScore(), request.getStatus(), creator.getId());
                Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                replaceOptionsInDatabase(id, request.getOptions());
                return getQuestionById(id);
            } catch (DuplicateKeyException ex) {
                throw new IllegalArgumentException("题目已存在或选项标识重复");
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        long id = fallbackQuestionId.incrementAndGet();
        Map<String, Object> row = buildFallbackQuestionRow(id, request, creator);
        fallbackQuestions.put(id, row);
        fallbackOptions.put(id, buildFallbackOptions(id, request.getOptions()));
        return copyQuestionWithOptions(row);
    }

    public Map<String, Object> updateQuestion(Long id, QuestionRequest request, AuthUser updater) {
        validateQuestion(request);
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                int rows = jdbcTemplate.update("""
                        UPDATE question
                        SET subject_id = ?, knowledge_point_id = ?, question_type = ?, difficulty = ?, stem = ?, correct_answer = ?,
                            analysis = ?, default_score = ?, status = ?
                        WHERE id = ? AND deleted = 0
                        """, request.getSubjectId(), request.getKnowledgePointId(), normalizeCode(request.getQuestionType()),
                        normalizeCode(request.getDifficulty()), trim(request.getStem()), trim(request.getCorrectAnswer()), trim(request.getAnalysis()),
                        request.getDefaultScore(), request.getStatus(), id);
                if (rows == 0) {
                    throw new IllegalArgumentException("题目不存在");
                }
                replaceOptionsInDatabase(id, request.getOptions());
                return getQuestionById(id);
            } catch (DuplicateKeyException ex) {
                throw new IllegalArgumentException("题目已存在或选项标识重复");
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        Map<String, Object> row = requireFallbackQuestion(id);
        row.putAll(buildFallbackQuestionRow(id, request, updater));
        row.put("updatedAt", LocalDateTime.now());
        fallbackOptions.put(id, buildFallbackOptions(id, request.getOptions()));
        return copyQuestionWithOptions(row);
    }

    public Map<String, Object> deleteQuestion(Long id) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                int rows = jdbcTemplate.update("UPDATE question SET deleted = 1 WHERE id = ? AND deleted = 0", id);
                if (rows == 0) {
                    throw new IllegalArgumentException("题目不存在");
                }
                return Map.of("deleted", true, "id", id);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        requireFallbackQuestion(id);
        fallbackQuestions.remove(id);
        fallbackOptions.remove(id);
        return Map.of("deleted", true, "id", id);
    }

    public Map<String, Object> updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("题目状态只能为0或1");
        }
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                int rows = jdbcTemplate.update("UPDATE question SET status = ? WHERE id = ? AND deleted = 0", status, id);
                if (rows == 0) {
                    throw new IllegalArgumentException("题目不存在");
                }
                return getQuestionById(id);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        Map<String, Object> row = requireFallbackQuestion(id);
        row.put("status", status);
        row.put("updatedAt", LocalDateTime.now());
        return copyQuestionWithOptions(row);
    }

    public Map<String, Object> summary() {
        List<Map<String, Object>> questions = listQuestions(null, null, null, null, null, null);
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
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT q.id, q.subject_id AS subjectId, s.subject_name AS subjectName,
                       q.knowledge_point_id AS knowledgePointId, kp.point_name AS knowledgePointName,
                       q.question_type AS questionType, q.difficulty, q.stem, q.correct_answer AS correctAnswer,
                       q.analysis, q.default_score AS defaultScore, q.status, q.created_by AS createdBy,
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
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        return jdbcTemplate.queryForList("""
                SELECT id, question_id AS questionId, option_label AS optionLabel, option_content AS optionContent,
                       is_correct AS correct, sort_order AS sortOrder
                FROM question_option
                WHERE question_id = ?
                ORDER BY sort_order, id
                """, questionId);
    }

    private void replaceOptionsInDatabase(Long questionId, List<QuestionOptionRequest> options) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
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

    private Map<String, Object> buildFallbackQuestionRow(Long id, QuestionRequest request, AuthUser creator) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("subjectId", request.getSubjectId());
        row.put("subjectName", subjectNameOf(request.getSubjectId()));
        row.put("knowledgePointId", request.getKnowledgePointId());
        row.put("knowledgePointName", knowledgePointNameOf(request.getKnowledgePointId()));
        row.put("questionType", normalizeCode(request.getQuestionType()));
        row.put("difficulty", normalizeCode(request.getDifficulty()));
        row.put("stem", trim(request.getStem()));
        row.put("correctAnswer", trim(request.getCorrectAnswer()));
        row.put("analysis", trim(request.getAnalysis()));
        row.put("defaultScore", request.getDefaultScore());
        row.put("status", request.getStatus());
        row.put("createdBy", creator.getId());
        row.put("creatorName", creator.getRealName());
        row.put("createdAt", LocalDateTime.now());
        row.put("updatedAt", LocalDateTime.now());
        return row;
    }

    private List<Map<String, Object>> buildFallbackOptions(Long questionId, List<QuestionOptionRequest> options) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (options == null) {
            return rows;
        }
        int sort = 1;
        for (QuestionOptionRequest option : options) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", fallbackOptionId.incrementAndGet());
            row.put("questionId", questionId);
            row.put("optionLabel", trim(option.getOptionLabel()));
            row.put("optionContent", trim(option.getOptionContent()));
            row.put("correct", Boolean.TRUE.equals(option.getCorrect()));
            row.put("sortOrder", sort++);
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> copyQuestionWithOptions(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>(source);
        Long id = longValue(row.get("id"));
        row.put("options", fallbackOptions.getOrDefault(id, List.of()).stream().map(LinkedHashMap::new).collect(Collectors.toList()));
        return row;
    }

    private Map<String, Object> requireFallbackQuestion(Long id) {
        Map<String, Object> row = fallbackQuestions.get(id);
        if (row == null) {
            throw new IllegalArgumentException("题目不存在");
        }
        return row;
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
        if (!fallbackSubjectExists(subjectId)) {
            throw new IllegalArgumentException("科目不存在");
        }
    }

    private void validateKnowledgePointExists(Long subjectId, Long knowledgePointId) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edu_knowledge_point WHERE id = ? AND subject_id = ? AND deleted = 0", Integer.class, knowledgePointId, subjectId);
                if (count == null || count == 0) {
                    throw new IllegalArgumentException("知识点不存在或不属于当前科目");
                }
                return;
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        if (!fallbackKnowledgePointExists(subjectId, knowledgePointId)) {
            throw new IllegalArgumentException("知识点不存在或不属于当前科目");
        }
    }

    private boolean fallbackSubjectExists(Long subjectId) {
        return subjectId != null && (subjectId == 1L || subjectId == 2L);
    }

    private boolean fallbackKnowledgePointExists(Long subjectId, Long knowledgePointId) {
        return knowledgePointId != null && ((subjectId == 1L && (knowledgePointId == 1L || knowledgePointId == 2L))
                || (subjectId == 2L && (knowledgePointId == 3L || knowledgePointId == 4L)));
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
        return "阶段4测试科目";
    }

    private String knowledgePointNameOf(Long knowledgePointId) {
        if (knowledgePointId == null) {
            return null;
        }
        return switch (knowledgePointId.intValue()) {
            case 1 -> "集合框架";
            case 2 -> "线程与并发";
            case 3 -> "SQL查询";
            case 4 -> "事务与ACID";
            default -> "阶段4测试知识点";
        };
    }

    private void seedFallbackData() {
        QuestionRequest request = new QuestionRequest();
        request.setSubjectId(1L);
        request.setKnowledgePointId(1L);
        request.setQuestionType("SINGLE_CHOICE");
        request.setDifficulty("EASY");
        request.setStem("Java 中用于存储键值对的数据结构通常是？");
        request.setCorrectAnswer("B");
        request.setAnalysis("Map 接口用于存储键值对数据。");
        request.setDefaultScore(BigDecimal.valueOf(5));
        request.setStatus(1);
        QuestionOptionRequest a = option("A", "List", false);
        QuestionOptionRequest b = option("B", "Map", true);
        QuestionOptionRequest c = option("C", "Set", false);
        request.setOptions(List.of(a, b, c));
        AuthUser teacher = new AuthUser(2L, "teacher1", "演示教师一", List.of("TEACHER"), Map.of("teacher_no", "T2024001"));
        fallbackQuestions.put(1L, buildFallbackQuestionRow(1L, request, teacher));
        fallbackOptions.put(1L, buildFallbackOptions(1L, request.getOptions()));
    }

    private QuestionOptionRequest option(String label, String content, boolean correct) {
        QuestionOptionRequest option = new QuestionOptionRequest();
        option.setOptionLabel(label);
        option.setOptionContent(content);
        option.setCorrect(correct);
        return option;
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
