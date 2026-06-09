package com.smartexam.service;

import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import com.smartexam.dto.paper.GeneratePaperRequest;
import com.smartexam.dto.paper.GenerateRuleRequest;
import com.smartexam.dto.paper.PaperQuestionRequest;
import com.smartexam.dto.paper.PaperRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PaperService {

    private static final List<String> ALL_TYPES = List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SUBJECTIVE");
    private static final List<String> ALL_DIFFICULTIES = List.of("EASY", "MEDIUM", "HARD");

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final QuestionBankService questionBankService;

    public PaperService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, QuestionBankService questionBankService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.questionBankService = questionBankService;
    }

    public Map<String, Object> summary(AuthUser user) {
        List<Map<String, Object>> papers = listPapers(null, null, null, user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", papers.size());
        data.put("published", papers.stream().filter(row -> Objects.equals(intValue(row.get("status")), 1)).count());
        data.put("draft", papers.stream().filter(row -> Objects.equals(intValue(row.get("status")), 0)).count());
        data.put("totalQuestions", papers.stream().mapToInt(row -> intValue(row.getOrDefault("questionCount", 0))).sum());
        return data;
    }

    public List<Map<String, Object>> listPapers(String keyword, Long subjectId, Integer status, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int ownerScope = ownerScope(user);
        Long ownerId = ownerId(user);
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
                  AND (? = 1 OR p.created_by = ? OR p.status = 1)
                  AND (? IS NULL OR p.subject_id = ?)
                  AND (? IS NULL OR p.status = ?)
                  AND (? IS NULL OR p.paper_name LIKE CONCAT('%', ?, '%') OR p.description LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%'))
                GROUP BY p.id, p.subject_id, s.subject_name, p.paper_name, p.description, p.total_score, p.status,
                         p.created_by, u.real_name, p.created_at, p.updated_at
                ORDER BY p.id DESC
                """, ownerScope, ownerId, subjectId, subjectId, status, status, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword));
    }

    public PageResult<Map<String, Object>> listPapers(String keyword, Long subjectId, Integer status,
                                                       int page, int size, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int ownerScope = ownerScope(user);
        Long ownerId = ownerId(user);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT p.id)
                FROM paper p
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE p.deleted = 0
                  AND (? = 1 OR p.created_by = ? OR p.status = 1)
                  AND (? IS NULL OR p.subject_id = ?)
                  AND (? IS NULL OR p.status = ?)
                  AND (? IS NULL OR p.paper_name LIKE CONCAT('%', ?, '%') OR p.description LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%'))
                """, Long.class, ownerScope, ownerId, subjectId, subjectId, status, status, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword));

        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT p.id, p.subject_id AS subjectId, s.subject_name AS subjectName, p.paper_name AS paperName,
                       p.description, p.total_score AS totalScore, p.status, p.created_by AS createdBy,
                       u.real_name AS creatorName, p.created_at AS createdAt, p.updated_at AS updatedAt,
                       COUNT(pq.id) AS questionCount
                FROM paper p
                JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN sys_user u ON u.id = p.created_by
                LEFT JOIN paper_question pq ON pq.paper_id = p.id
                WHERE p.deleted = 0
                  AND (? = 1 OR p.created_by = ? OR p.status = 1)
                  AND (? IS NULL OR p.subject_id = ?)
                  AND (? IS NULL OR p.status = ?)
                  AND (? IS NULL OR p.paper_name LIKE CONCAT('%', ?, '%') OR p.description LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%'))
                GROUP BY p.id, p.subject_id, s.subject_name, p.paper_name, p.description, p.total_score, p.status,
                         p.created_by, u.real_name, p.created_at, p.updated_at
                ORDER BY p.id DESC
                LIMIT ? OFFSET ?
                """, ownerScope, ownerId, subjectId, subjectId, status, status, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword),
                safeSize, offset);
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public Map<String, Object> getPaper(Long id) {
        return getPaperById(id);
    }

    public Map<String, Object> createPaper(PaperRequest request, AuthUser creator) {
        validatePaperRequest(request);
        BigDecimal totalScore = totalScore(request.getQuestions());
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
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
        }
    }

    private void requirePaperOwner(JdbcTemplate jdbcTemplate, Long id, AuthUser user) {
        List<Map<String, Object>> owners = jdbcTemplate.queryForList(
                "SELECT created_by FROM paper WHERE id = ? AND deleted = 0", id);
        if (owners.isEmpty()) {
            throw new IllegalArgumentException("试卷不存在");
        }
        if (user != null && user.hasRole("ADMIN")) {
            return;
        }
        Object createdBy = owners.get(0).get("created_by");
        if (user == null || createdBy == null || !createdBy.toString().equals(String.valueOf(user.getId()))) {
            throw new IllegalArgumentException("只能管理本人创建的试卷");
        }
    }

    public Map<String, Object> updatePaper(Long id, PaperRequest request, AuthUser updater) {
        validatePaperRequest(request);
        BigDecimal totalScore = totalScore(request.getQuestions());
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requirePaperOwner(jdbcTemplate, id, updater);
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
        }
    }

    public Map<String, Object> updateStatus(Long id, Integer status, AuthUser user) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("试卷状态只能为0或1");
        }
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requirePaperOwner(jdbcTemplate, id, user);
        int rows = jdbcTemplate.update("UPDATE paper SET status = ? WHERE id = ? AND deleted = 0", status, id);
        if (rows == 0) {
            throw new IllegalArgumentException("试卷不存在");
        }
        return getPaperById(id);
    }

    public Map<String, Object> deletePaper(Long id, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requirePaperOwner(jdbcTemplate, id, user);
        int rows = jdbcTemplate.update("UPDATE paper SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("试卷不存在");
        }
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
            List<Map<String, Object>> candidates = new ArrayList<>(questionBankService
                    .listQuestions(null, request.getSubjectId(), rule.getKnowledgePointId(), type, difficulty, 1, null)
                    .stream()
                    .filter(row -> !selectedIds.contains(longValue(row.get("id"))))
                    .toList());
            Collections.shuffle(candidates);
            if (candidates.size() < rule.getCount()) {
                throw new IllegalArgumentException("组卷题量不足：题型" + type + "需要" + rule.getCount() + "题，实际可用" + candidates.size() + "题");
            }
            for (Map<String, Object> candidate : candidates.stream().limit(rule.getCount()).toList()) {
                selectedIds.add(longValue(candidate.get("id")));
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
        return questionBankService.listQuestions(null, null, null, null, null, null, null).stream()
                .filter(row -> Objects.equals(longValue(row.get("id")), questionId))
                .findFirst()
                .orElse(null);
    }

    private void validateSubjectExists(Long subjectId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edu_subject WHERE id = ? AND deleted = 0", Integer.class, subjectId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("科目不存在");
        }
    }

    private Map<String, Object> getPaperById(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
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
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
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
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
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
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        BigDecimal totalScore = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(score), 0) FROM paper_question WHERE paper_id = ?", BigDecimal.class, paperId);
        jdbcTemplate.update("UPDATE paper SET total_score = ? WHERE id = ?", totalScore, paperId);
    }

    private BigDecimal totalScore(List<PaperQuestionRequest> questions) {
        return questions.stream().map(PaperQuestionRequest::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    /**
     * 数据隔离范围标记：管理员或系统级调用（user 为 null）返回 1，表示不受限、可见全部试卷；
     * 普通教师返回 0，配合 SQL 中的 {@code (? = 1 OR p.created_by = ? OR p.status = 1)}
     * 仅可见本人创建或已发布（status=1）的试卷。与 QuestionBankService 的题库隔离策略保持一致。
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
