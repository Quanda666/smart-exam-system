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
import org.springframework.transaction.annotation.Transactional;

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
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.id, p.subject_id AS subjectId, s.subject_name AS subjectName, p.paper_name AS paperName,
                       p.description, p.total_score AS totalScore, p.status, p.created_by AS createdBy,
                       u.real_name AS creatorName, p.created_at AS createdAt, p.updated_at AS updatedAt,
                       COUNT(DISTINCT pq.id) AS questionCount,
                       COUNT(DISTINCT e.id) AS examCount
                FROM paper p
                JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN sys_user u ON u.id = p.created_by
                LEFT JOIN paper_question pq ON pq.paper_id = p.id
                LEFT JOIN exam e ON e.paper_id = p.id AND e.deleted = 0
                WHERE p.deleted = 0
                  AND (? = 1 OR p.created_by = ? OR p.status = 1)
                  AND (? IS NULL OR p.subject_id = ?)
                  AND (? IS NULL OR p.status = ?)
                  AND (? IS NULL OR p.paper_name LIKE CONCAT('%', ?, '%') OR p.description LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%'))
                GROUP BY p.id, p.subject_id, s.subject_name, p.paper_name, p.description, p.total_score, p.status,
                         p.created_by, u.real_name, p.created_at, p.updated_at
                ORDER BY p.id DESC
                """, ownerScope, ownerId, subjectId, subjectId, status, status, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword));
        rows.forEach(row -> enrichPaperState(row, user));
        return rows;
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
                       COUNT(DISTINCT pq.id) AS questionCount,
                       COUNT(DISTINCT e.id) AS examCount
                FROM paper p
                JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN sys_user u ON u.id = p.created_by
                LEFT JOIN paper_question pq ON pq.paper_id = p.id
                LEFT JOIN exam e ON e.paper_id = p.id AND e.deleted = 0
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
        list.forEach(row -> enrichPaperState(row, user));
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public Map<String, Object> getPaper(Long id, AuthUser user) {
        return getPaperById(id, user);
    }

    @Transactional
    public Map<String, Object> createPaper(PaperRequest request, AuthUser creator) {
        validatePaperRequest(request);
        BigDecimal totalScore = totalScore(request.getQuestions());
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int desiredStatus = normalizedPaperStatus(request.getStatus());
        try {
            jdbcTemplate.update("""
                    INSERT INTO paper (subject_id, paper_name, description, total_score, status, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, request.getSubjectId(), trim(request.getPaperName()), trim(request.getDescription()), totalScore, 0, creator.getId());
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            replacePaperQuestionsInDatabase(id, request.getSubjectId(), request.getQuestions());
            refreshTotalScoreInDatabase(id);
            if (desiredStatus == 1) {
                validatePaperPublishable(jdbcTemplate, id);
                jdbcTemplate.update("UPDATE paper SET status = 1 WHERE id = ? AND deleted = 0", id);
            }
            return getPaperById(id, creator);
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

    private void enrichPaperState(Map<String, Object> row, AuthUser user) {
        int status = intValue(row.getOrDefault("status", 0));
        int examCount = intValue(row.getOrDefault("examCount", 0));
        int questionCount = intValue(row.getOrDefault("questionCount", 0));
        boolean owner = canManagePaper(row, user);
        boolean published = status == 1;
        boolean referenced = examCount > 0;
        boolean locked = published || referenced;
        String lockReason = "";
        if (referenced) {
            lockReason = "Referenced by " + examCount + " exam(s)";
        } else if (published) {
            lockReason = "Published paper cannot be edited directly";
        }
        row.put("examCount", examCount);
        row.put("locked", locked);
        row.put("lockReason", lockReason);
        row.put("canEdit", owner && !locked);
        row.put("canDelete", owner && !locked);
        row.put("canPublish", owner && status == 0 && !referenced && questionCount > 0);
        row.put("canRevoke", owner && status == 1 && !referenced);
        row.put("canCopy", owner);
    }

    private boolean canManagePaper(Map<String, Object> row, AuthUser user) {
        if (user == null || user.hasRole("ADMIN")) {
            return true;
        }
        Object createdBy = row.get("createdBy");
        return createdBy != null && String.valueOf(user.getId()).equals(String.valueOf(createdBy));
    }

    private int normalizedPaperStatus(Integer status) {
        if (status == null) {
            return 0;
        }
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("Paper status must be 0 or 1");
        }
        return status;
    }

    private void ensurePaperStructurallyEditable(JdbcTemplate jdbcTemplate, Long id) {
        List<Integer> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM paper WHERE id = ? AND deleted = 0", Integer.class, id);
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("Paper does not exist");
        }
        if (Objects.equals(statuses.get(0), 1)) {
            throw new IllegalStateException("Published paper cannot be edited or deleted directly");
        }
        ensurePaperNotReferenced(jdbcTemplate, id, "Paper is referenced by exams and cannot be changed");
    }

    private void ensurePaperNotReferenced(JdbcTemplate jdbcTemplate, Long id, String message) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam WHERE paper_id = ? AND deleted = 0", Integer.class, id);
        if (count != null && count > 0) {
            throw new IllegalStateException(message);
        }
    }

    private int currentPaperStatus(JdbcTemplate jdbcTemplate, Long id) {
        List<Integer> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM paper WHERE id = ? AND deleted = 0", Integer.class, id);
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("Paper does not exist");
        }
        return statuses.get(0) == null ? 0 : statuses.get(0);
    }

    private void validatePaperPublishable(JdbcTemplate jdbcTemplate, Long id) {
        Integer paperCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM paper WHERE id = ? AND deleted = 0", Integer.class, id);
        if (paperCount == null || paperCount == 0) {
            throw new IllegalArgumentException("Paper does not exist");
        }
        Integer questionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM paper_question WHERE paper_id = ?", Integer.class, id);
        if (questionCount == null || questionCount == 0) {
            throw new IllegalStateException("Paper must contain at least one question before publishing");
        }
        Integer invalidQuestions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM paper_question pq
                LEFT JOIN question q ON q.id = pq.question_id
                WHERE pq.paper_id = ?
                  AND (q.id IS NULL OR q.deleted = 1 OR q.status <> 1 OR q.review_status <> 'APPROVED')
                """, Integer.class, id);
        if (invalidQuestions != null && invalidQuestions > 0) {
            throw new IllegalStateException("Paper contains unavailable or unapproved questions");
        }
        Integer missingSnapshots = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM paper_question pq
                LEFT JOIN question_version qv ON qv.id = pq.question_version_id
                WHERE pq.paper_id = ? AND (pq.question_version_id IS NULL OR qv.id IS NULL)
                """, Integer.class, id);
        if (missingSnapshots != null && missingSnapshots > 0) {
            throw new IllegalStateException("Paper question version snapshots are incomplete");
        }
    }

    @Transactional
    public Map<String, Object> updatePaper(Long id, PaperRequest request, AuthUser updater) {
        validatePaperRequest(request);
        BigDecimal totalScore = totalScore(request.getQuestions());
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requirePaperOwner(jdbcTemplate, id, updater);
        ensurePaperStructurallyEditable(jdbcTemplate, id);
        int desiredStatus = normalizedPaperStatus(request.getStatus());
        try {
            int rows = jdbcTemplate.update("""
                    UPDATE paper
                    SET subject_id = ?, paper_name = ?, description = ?, total_score = ?, status = ?
                    WHERE id = ? AND deleted = 0
                    """, request.getSubjectId(), trim(request.getPaperName()), trim(request.getDescription()), totalScore, 0, id);
            if (rows == 0) {
                throw new IllegalArgumentException("试卷不存在");
            }
            replacePaperQuestionsInDatabase(id, request.getSubjectId(), request.getQuestions());
            refreshTotalScoreInDatabase(id);
            if (desiredStatus == 1) {
                validatePaperPublishable(jdbcTemplate, id);
                jdbcTemplate.update("UPDATE paper SET status = 1 WHERE id = ? AND deleted = 0", id);
            }
            return getPaperById(id, updater);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("试卷名称已存在");
        }
    }

    @Transactional
    public Map<String, Object> updateStatus(Long id, Integer status, AuthUser user) {
        int nextStatus = normalizedPaperStatus(status);
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requirePaperOwner(jdbcTemplate, id, user);
        int currentStatus = currentPaperStatus(jdbcTemplate, id);
        if (currentStatus == nextStatus) {
            return getPaperById(id, user);
        }
        if (nextStatus == 1) {
            ensurePaperNotReferenced(jdbcTemplate, id, "Paper is referenced by exams and cannot be published");
            validatePaperPublishable(jdbcTemplate, id);
        } else {
            ensurePaperNotReferenced(jdbcTemplate, id, "Paper is referenced by exams and cannot be revoked");
        }
        int rows = jdbcTemplate.update("UPDATE paper SET status = ? WHERE id = ? AND deleted = 0", nextStatus, id);
        if (rows == 0) {
            throw new IllegalArgumentException("试卷不存在");
        }
        return getPaperById(id, user);
    }

    @Transactional
    public Map<String, Object> deletePaper(Long id, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requirePaperOwner(jdbcTemplate, id, user);
        ensurePaperStructurallyEditable(jdbcTemplate, id);
        int rows = jdbcTemplate.update("UPDATE paper SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("试卷不存在");
        }
        return Map.of("deleted", true, "id", id);
    }

    @Transactional
    public Map<String, Object> copyPaper(Long sourceId, AuthUser copier) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requirePaperOwner(jdbcTemplate, sourceId, copier);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT subject_id AS subjectId, paper_name AS paperName, description
                FROM paper
                WHERE id = ? AND deleted = 0
                """, sourceId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Paper does not exist");
        }
        List<Map<String, Object>> questions = listPaperQuestionsFromDatabase(sourceId);
        if (questions.isEmpty()) {
            throw new IllegalStateException("Source paper has no questions to copy");
        }
        Map<String, Object> source = rows.get(0);
        Long subjectId = longValue(source.get("subjectId"));
        PaperRequest request = new PaperRequest();
        request.setSubjectId(subjectId);
        request.setPaperName(nextCopyPaperName(jdbcTemplate, String.valueOf(source.get("paperName"))));
        request.setDescription(trim((String) source.get("description")));
        request.setStatus(0);
        List<PaperQuestionRequest> copiedQuestions = new ArrayList<>();
        int sort = 1;
        for (Map<String, Object> questionRow : questions) {
            PaperQuestionRequest question = new PaperQuestionRequest();
            question.setQuestionId(longValue(questionRow.get("questionId")));
            question.setScore(bigDecimalValue(questionRow.get("score")));
            question.setSortOrder(intValue(questionRow.getOrDefault("sortOrder", sort)));
            copiedQuestions.add(question);
            sort++;
        }
        request.setQuestions(copiedQuestions);
        return createPaper(request, copier);
    }

    private String nextCopyPaperName(JdbcTemplate jdbcTemplate, String sourceName) {
        String normalized = trim(sourceName);
        String base = normalized == null || normalized.isBlank() ? "Paper Copy" : normalized + " Copy";
        base = truncate(base, 112);
        for (int index = 1; index <= 999; index++) {
            String candidate = index == 1 ? base : truncate(base, 112) + " " + index;
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM paper WHERE paper_name = ?", Integer.class, candidate);
            if (count == null || count == 0) {
                return candidate;
            }
        }
        return truncate(base, 100) + " " + System.currentTimeMillis();
    }

    @Transactional
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
                    .filter(row -> "APPROVED".equals(String.valueOf(row.get("reviewStatus"))))
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
        if (!Objects.equals(intValue(question.get("status")), 1)
                || !"APPROVED".equals(String.valueOf(question.get("reviewStatus")))) {
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

    private Map<String, Object> getPaperById(Long id, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.id, p.subject_id AS subjectId, s.subject_name AS subjectName, p.paper_name AS paperName,
                       p.description, p.total_score AS totalScore, p.status, p.created_by AS createdBy,
                       u.real_name AS creatorName, p.created_at AS createdAt, p.updated_at AS updatedAt,
                       (SELECT COUNT(*) FROM exam e WHERE e.paper_id = p.id AND e.deleted = 0) AS examCount
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
        enrichPaperState(row, user);
        return row;
    }

    private List<Map<String, Object>> listPaperQuestionsFromDatabase(Long paperId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("""
                SELECT pq.id, pq.paper_id AS paperId, pq.question_id AS questionId,
                       pq.question_version_id AS questionVersionId, COALESCE(qv.version_no, q.version_no) AS versionNo,
                       pq.score, pq.sort_order AS sortOrder,
                       COALESCE(qv.question_type, q.question_type) AS questionType,
                       COALESCE(qv.difficulty, q.difficulty) AS difficulty,
                       COALESCE(qv.stem, q.stem) AS stem,
                       COALESCE(qv.analysis, q.analysis) AS analysis,
                       q.subject_id AS subjectId, s.subject_name AS subjectName,
                       q.knowledge_point_id AS knowledgePointId, kp.point_name AS knowledgePointName
                FROM paper_question pq
                JOIN question q ON q.id = pq.question_id
                LEFT JOIN question_version qv ON qv.id = pq.question_version_id
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
                    INSERT INTO paper_question (paper_id, question_id, question_version_id, score, sort_order)
                    VALUES (?, ?, ?, ?, ?)
                    """, paperId, question.getQuestionId(), currentQuestionVersionId(jdbcTemplate, question.getQuestionId()),
                    question.getScore(), question.getSortOrder());
        }
    }

    private Long currentQuestionVersionId(JdbcTemplate jdbcTemplate, Long questionId) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT qv.id
                FROM question q
                JOIN question_version qv ON qv.question_id = q.id AND qv.version_no = q.version_no
                WHERE q.id = ?
                """, Long.class, questionId);
        return ids.isEmpty() ? null : ids.get(0);
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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

    private BigDecimal bigDecimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
