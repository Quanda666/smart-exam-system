package com.smartexam.service;

import com.smartexam.common.CsvExport;
import com.smartexam.common.ExportFile;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import com.smartexam.dto.question.QuestionOptionRequest;
import com.smartexam.dto.question.QuestionRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private static final String REVIEW_DRAFT = "DRAFT";
    private static final String REVIEW_PENDING = "PENDING";
    private static final String REVIEW_APPROVED = "APPROVED";
    private static final String REVIEW_REJECTED = "REJECTED";
    private static final int MAX_QUESTION_REVIEW_COMMENT_LENGTH = 500;
    private static final int MAX_SOURCE_DETAIL_LENGTH = 255;
    private static final int MAX_SOURCE_EXCERPT_LENGTH = 500;
    private static final int MAX_AI_MODEL_LENGTH = 64;
    private static final int MAX_PROMPT_VERSION_LENGTH = 64;
    private static final int MAX_QUESTION_TEXT_LENGTH = 4000;
    private static final int MAX_OPTION_LABEL_LENGTH = 16;
    private static final int MAX_OPTION_CONTENT_LENGTH = 1000;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public QuestionBankService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public List<Map<String, Object>> listQuestions(String keyword, Long subjectId, Long knowledgePointId,
                                                   String questionType, String difficulty, Integer status, AuthUser user) {
        return listQuestions(keyword, subjectId, knowledgePointId, questionType, difficulty, status, null, user);
    }

    public List<Map<String, Object>> listQuestions(String keyword, Long subjectId, Long knowledgePointId,
                                                   String questionType, String difficulty, Integer status,
                                                   String reviewStatus, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int ownerScope = ownerScope(user);
        Long ownerId = ownerId(user);
        String normalizedReviewStatus = normalizeReviewStatusOrNull(reviewStatus);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT q.id, q.subject_id AS subjectId, s.subject_name AS subjectName,
                       q.knowledge_point_id AS knowledgePointId, kp.point_name AS knowledgePointName,
                       q.question_type AS questionType, q.difficulty, q.stem, q.correct_answer AS correctAnswer,
                       q.analysis, q.default_score AS defaultScore, q.status, q.version_no AS versionNo, q.review_status AS reviewStatus,
                       q.reviewed_by AS reviewedBy, reviewer.real_name AS reviewerName,
                       q.reviewed_at AS reviewedAt, q.review_comment AS reviewComment, q.created_by AS createdBy,
                       q.source_type AS sourceType, q.source_detail AS sourceDetail,
                       q.material_id AS materialId, q.source_page AS sourcePage, q.source_paragraph AS sourceParagraph,
                       q.source_excerpt AS sourceExcerpt, q.ai_model AS aiModel, q.prompt_version AS promptVersion,
                       u.real_name AS creatorName, q.created_at AS createdAt, q.updated_at AS updatedAt
                FROM question q
                JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                LEFT JOIN sys_user u ON u.id = q.created_by
                LEFT JOIN sys_user reviewer ON reviewer.id = q.reviewed_by
                WHERE q.deleted = 0
                  AND (? = 1 OR q.created_by = ? OR (q.status = 1 AND q.review_status = 'APPROVED') OR q.review_status = 'PENDING')
                  AND (? IS NULL OR q.subject_id = ?)
                  AND (? IS NULL OR q.knowledge_point_id = ?)
                  AND (? IS NULL OR q.question_type = ?)
                  AND (? IS NULL OR q.difficulty = ?)
                  AND (? IS NULL OR q.status = ?)
                  AND (? IS NULL OR q.review_status = ?)
                  AND (? IS NULL OR q.stem LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%') OR kp.point_name LIKE CONCAT('%', ?, '%'))
                ORDER BY q.id DESC
                """, ownerScope, ownerId, subjectId, subjectId, knowledgePointId, knowledgePointId, blankToNull(questionType), blankToNull(questionType),
                blankToNull(difficulty), blankToNull(difficulty), status, status, normalizedReviewStatus, normalizedReviewStatus,
                blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword));
        rows.forEach(row -> enrichQuestionRow(row, user));
        return rows;
    }

    public PageResult<Map<String, Object>> listQuestions(String keyword, Long subjectId, Long knowledgePointId,
                                                         String questionType, String difficulty, Integer status,
                                                         int page, int size, AuthUser user) {
        return listQuestions(keyword, subjectId, knowledgePointId, questionType, difficulty, status, null, page, size, user);
    }

    public PageResult<Map<String, Object>> listQuestions(String keyword, Long subjectId, Long knowledgePointId,
                                                         String questionType, String difficulty, Integer status,
                                                         String reviewStatus, int page, int size, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int ownerScope = ownerScope(user);
        Long ownerId = ownerId(user);
        String normalizedReviewStatus = normalizeReviewStatusOrNull(reviewStatus);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM question q
                JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                LEFT JOIN sys_user u ON u.id = q.created_by
                WHERE q.deleted = 0
                  AND (? = 1 OR q.created_by = ? OR (q.status = 1 AND q.review_status = 'APPROVED') OR q.review_status = 'PENDING')
                  AND (? IS NULL OR q.subject_id = ?)
                  AND (? IS NULL OR q.knowledge_point_id = ?)
                  AND (? IS NULL OR q.question_type = ?)
                  AND (? IS NULL OR q.difficulty = ?)
                  AND (? IS NULL OR q.status = ?)
                  AND (? IS NULL OR q.review_status = ?)
                  AND (? IS NULL OR q.stem LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%') OR kp.point_name LIKE CONCAT('%', ?, '%'))
                """, Long.class, ownerScope, ownerId, subjectId, subjectId, knowledgePointId, knowledgePointId,
                blankToNull(questionType), blankToNull(questionType), blankToNull(difficulty), blankToNull(difficulty),
                status, status, normalizedReviewStatus, normalizedReviewStatus,
                blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT q.id, q.subject_id AS subjectId, s.subject_name AS subjectName,
                       q.knowledge_point_id AS knowledgePointId, kp.point_name AS knowledgePointName,
                       q.question_type AS questionType, q.difficulty, q.stem, q.correct_answer AS correctAnswer,
                       q.analysis, q.default_score AS defaultScore, q.status, q.version_no AS versionNo, q.review_status AS reviewStatus,
                       q.reviewed_by AS reviewedBy, reviewer.real_name AS reviewerName,
                       q.reviewed_at AS reviewedAt, q.review_comment AS reviewComment, q.created_by AS createdBy,
                       q.source_type AS sourceType, q.source_detail AS sourceDetail,
                       q.material_id AS materialId, q.source_page AS sourcePage, q.source_paragraph AS sourceParagraph,
                       q.source_excerpt AS sourceExcerpt, q.ai_model AS aiModel, q.prompt_version AS promptVersion,
                       u.real_name AS creatorName, q.created_at AS createdAt, q.updated_at AS updatedAt
                FROM question q
                JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                LEFT JOIN sys_user u ON u.id = q.created_by
                LEFT JOIN sys_user reviewer ON reviewer.id = q.reviewed_by
                WHERE q.deleted = 0
                  AND (? = 1 OR q.created_by = ? OR (q.status = 1 AND q.review_status = 'APPROVED') OR q.review_status = 'PENDING')
                  AND (? IS NULL OR q.subject_id = ?)
                  AND (? IS NULL OR q.knowledge_point_id = ?)
                  AND (? IS NULL OR q.question_type = ?)
                  AND (? IS NULL OR q.difficulty = ?)
                  AND (? IS NULL OR q.status = ?)
                  AND (? IS NULL OR q.review_status = ?)
                  AND (? IS NULL OR q.stem LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%') OR kp.point_name LIKE CONCAT('%', ?, '%'))
                ORDER BY q.id DESC
                LIMIT ? OFFSET ?
                """, ownerScope, ownerId, subjectId, subjectId, knowledgePointId, knowledgePointId, blankToNull(questionType), blankToNull(questionType),
                blankToNull(difficulty), blankToNull(difficulty), status, status, normalizedReviewStatus, normalizedReviewStatus,
                blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword),
                safeSize, offset);
        rows.forEach(row -> enrichQuestionRow(row, user));
        return PageResult.of(rows, total == null ? 0 : total, safePage, safeSize);
    }

    @Transactional
    public Map<String, Object> createQuestion(QuestionRequest request, AuthUser creator) {
        validateQuestion(request);
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        validateQuestionSource(jdbcTemplate, request, creator);
        try {
            jdbcTemplate.update("""
                    INSERT INTO question (subject_id, knowledge_point_id, question_type, difficulty, stem, correct_answer,
                                          analysis, default_score, status, review_status, created_by, source_type, source_detail,
                                          material_id, source_page, source_paragraph, source_excerpt, ai_model, prompt_version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, request.getSubjectId(), request.getKnowledgePointId(), normalizeCode(request.getQuestionType()),
                    normalizeCode(request.getDifficulty()), normalizeQuestionStem(request.getStem()),
                    normalizeQuestionCorrectAnswer(request.getCorrectAnswer()), normalizeQuestionAnalysis(request.getAnalysis()),
                    request.getDefaultScore(), REVIEW_DRAFT, creator.getId(), questionSourceTypeOrDefault(request.getSourceType()),
                    normalizeSourceDetail(request.getSourceDetail()), request.getMaterialId(), positiveOrNull(request.getSourcePage()),
                    positiveOrNull(request.getSourceParagraph()), normalizeSourceExcerpt(request.getSourceExcerpt()),
                    normalizeAiModel(request.getAiModel()), normalizePromptVersion(request.getPromptVersion()));
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            replaceOptionsInDatabase(id, request.getOptions());
            snapshotQuestionVersion(jdbcTemplate, id, "CREATE", creator);
            Long questionReviewLogId = recordQuestionLog(jdbcTemplate, id, "CREATE", null, getQuestionState(jdbcTemplate, id), null, creator);
            return withQuestionReviewLogId(getQuestionById(id), questionReviewLogId);
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

    @Transactional
    public Map<String, Object> updateQuestion(Long id, QuestionRequest request, AuthUser updater) {
        validateQuestion(request);
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireQuestionOwner(jdbcTemplate, id, updater);
        Map<String, Object> before = getQuestionState(jdbcTemplate, id);
        validateQuestionSource(jdbcTemplate,
                firstNonBlank(request.getSourceType(), stringValue(before.get("sourceType"))),
                request.getMaterialId() == null ? longValue(before.get("materialId")) : request.getMaterialId(),
                request.getSubjectId(),
                updater);
        try {
            int rows = jdbcTemplate.update("""
                    UPDATE question
                    SET subject_id = ?, knowledge_point_id = ?, question_type = ?, difficulty = ?, stem = ?, correct_answer = ?,
                        analysis = ?, default_score = ?, status = 0, version_no = version_no + 1, review_status = ?, reviewed_by = NULL,
                        reviewed_at = NULL, review_comment = NULL, source_type = COALESCE(?, source_type),
                        source_detail = COALESCE(?, source_detail), material_id = COALESCE(?, material_id),
                        source_page = COALESCE(?, source_page), source_paragraph = COALESCE(?, source_paragraph),
                        source_excerpt = COALESCE(?, source_excerpt), ai_model = COALESCE(?, ai_model),
                        prompt_version = COALESCE(?, prompt_version)
                    WHERE id = ? AND deleted = 0
                    """, request.getSubjectId(), request.getKnowledgePointId(), normalizeCode(request.getQuestionType()),
                    normalizeCode(request.getDifficulty()), normalizeQuestionStem(request.getStem()),
                    normalizeQuestionCorrectAnswer(request.getCorrectAnswer()), normalizeQuestionAnalysis(request.getAnalysis()),
                    request.getDefaultScore(), REVIEW_DRAFT, questionSourceTypeOrNull(request.getSourceType()),
                    normalizeSourceDetail(request.getSourceDetail()), request.getMaterialId(), positiveOrNull(request.getSourcePage()),
                    positiveOrNull(request.getSourceParagraph()), normalizeSourceExcerpt(request.getSourceExcerpt()),
                    normalizeAiModel(request.getAiModel()), normalizePromptVersion(request.getPromptVersion()), id);
            if (rows == 0) {
                throw new IllegalArgumentException("题目不存在");
            }
            replaceOptionsInDatabase(id, request.getOptions());
            snapshotQuestionVersion(jdbcTemplate, id, "EDIT", updater);
            Long questionReviewLogId = recordQuestionLog(jdbcTemplate, id, "EDIT", before, getQuestionState(jdbcTemplate, id), "Content edited; review reset to draft", updater);
            return withQuestionReviewLogId(getQuestionById(id), questionReviewLogId);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("题目已存在或选项标识重复");
        }
    }

    @Transactional
    public Map<String, Object> deleteQuestion(Long id, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireQuestionOwner(jdbcTemplate, id, user);
        Map<String, Object> before = getQuestionState(jdbcTemplate, id);
        int rows = jdbcTemplate.update("UPDATE question SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("题目不存在");
        }
        Long questionReviewLogId = recordQuestionLog(jdbcTemplate, id, "DELETE", before, before, null, user);
        return withQuestionReviewLogId(Map.of("deleted", true, "id", id), questionReviewLogId);
    }

    @Transactional
    public Map<String, Object> updateStatus(Long id, Integer status, AuthUser user) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("题目状态只能为0或1");
        }
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireQuestionOwner(jdbcTemplate, id, user);
        Map<String, Object> before = getQuestionState(jdbcTemplate, id);
        if (status == 1 && !REVIEW_APPROVED.equals(questionReviewStatus(jdbcTemplate, id))) {
            throw new IllegalArgumentException("题目未审核通过，不能设为可用");
        }
        int rows = jdbcTemplate.update("UPDATE question SET status = ? WHERE id = ? AND deleted = 0", status, id);
        if (rows == 0) {
            throw new IllegalArgumentException("题目不存在");
        }
        snapshotQuestionVersion(jdbcTemplate, id, status == 1 ? "ONLINE" : "OFFLINE", user);
        Long questionReviewLogId = recordQuestionLog(jdbcTemplate, id, status == 1 ? "ONLINE" : "OFFLINE", before, getQuestionState(jdbcTemplate, id), null, user);
        return withQuestionReviewLogId(getQuestionById(id), questionReviewLogId);
    }

    @Transactional
    public Map<String, Object> submitReview(Long id, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireQuestionOwner(jdbcTemplate, id, user);
        validateQuestionReadyForReview(jdbcTemplate, id);
        Map<String, Object> before = getQuestionState(jdbcTemplate, id);
        int rows = jdbcTemplate.update("""
                UPDATE question
                SET status = 0, review_status = ?, reviewed_by = NULL, reviewed_at = NULL, review_comment = NULL
                WHERE id = ? AND deleted = 0
                """, REVIEW_PENDING, id);
        if (rows == 0) {
            throw new IllegalArgumentException("题目不存在");
        }
        snapshotQuestionVersion(jdbcTemplate, id, "SUBMIT_REVIEW", user);
        Long questionReviewLogId = recordQuestionLog(jdbcTemplate, id, "SUBMIT_REVIEW", before, getQuestionState(jdbcTemplate, id), null, user);
        return withQuestionReviewLogId(getQuestionById(id), questionReviewLogId);
    }

    @Transactional
    public Map<String, Object> approveReview(Long id, String comment, AuthUser reviewer) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireReviewer(jdbcTemplate, id, reviewer);
        validateQuestionReadyForReview(jdbcTemplate, id);
        String safeComment = normalizeOptionalQuestionReviewComment(comment);
        Map<String, Object> before = getQuestionState(jdbcTemplate, id);
        int rows = jdbcTemplate.update("""
                UPDATE question
                SET status = 1, review_status = ?, reviewed_by = ?, reviewed_at = NOW(), review_comment = ?
                WHERE id = ? AND deleted = 0
                """, REVIEW_APPROVED, reviewer.getId(), safeComment, id);
        if (rows == 0) {
            throw new IllegalArgumentException("题目不存在");
        }
        snapshotQuestionVersion(jdbcTemplate, id, "APPROVE", reviewer);
        Long questionReviewLogId = recordQuestionLog(jdbcTemplate, id, "APPROVE", before, getQuestionState(jdbcTemplate, id), safeComment, reviewer);
        return withQuestionReviewLogId(getQuestionById(id), questionReviewLogId);
    }

    @Transactional
    public Map<String, Object> rejectReview(Long id, String comment, AuthUser reviewer) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireReviewer(jdbcTemplate, id, reviewer);
        String safeComment = normalizeRequiredQuestionReviewComment(comment);
        Map<String, Object> before = getQuestionState(jdbcTemplate, id);
        int rows = jdbcTemplate.update("""
                UPDATE question
                SET status = 0, review_status = ?, reviewed_by = ?, reviewed_at = NOW(), review_comment = ?
                WHERE id = ? AND deleted = 0
                """, REVIEW_REJECTED, reviewer.getId(), safeComment, id);
        snapshotQuestionVersion(jdbcTemplate, id, "REJECT", reviewer);
        Long questionReviewLogId = recordQuestionLog(jdbcTemplate, id, "REJECT", before, getQuestionState(jdbcTemplate, id), safeComment, reviewer);
        if (rows == 0) {
            throw new IllegalArgumentException("题目不存在");
        }
        return withQuestionReviewLogId(getQuestionById(id), questionReviewLogId);
    }

    public Map<String, Object> summary(AuthUser user) {
        List<Map<String, Object>> questions = listQuestions(null, null, null, null, null, null, user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", questions.size());
        data.put("published", questions.stream().filter(row -> Objects.equals(intValue(row.get("status")), 1)).count());
        data.put("draft", questions.stream().filter(row -> Objects.equals(intValue(row.get("status")), 0)).count());
        data.put("pendingReview", questions.stream().filter(row -> REVIEW_PENDING.equals(String.valueOf(row.get("reviewStatus")))).count());
        data.put("approvedReview", questions.stream().filter(row -> REVIEW_APPROVED.equals(String.valueOf(row.get("reviewStatus")))).count());
        data.put("rejectedReview", questions.stream().filter(row -> REVIEW_REJECTED.equals(String.valueOf(row.get("reviewStatus")))).count());
        data.put("types", questions.stream().collect(Collectors.groupingBy(row -> String.valueOf(row.get("questionType")), LinkedHashMap::new, Collectors.counting())));
        data.put("difficulties", questions.stream().collect(Collectors.groupingBy(row -> String.valueOf(row.get("difficulty")), LinkedHashMap::new, Collectors.counting())));
        return data;
    }

    public List<Map<String, Object>> listReviewLogs(Long id, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireQuestionVisible(jdbcTemplate, id, user);
        return jdbcTemplate.queryForList("""
                SELECT qrl.id, qrl.question_id AS questionId, qrl.version_no AS versionNo,
                       qrl.action_type AS actionType, qrl.from_status AS fromStatus, qrl.to_status AS toStatus,
                       qrl.from_review_status AS fromReviewStatus, qrl.to_review_status AS toReviewStatus,
                       qrl.comment, qrl.operated_by AS operatedBy, u.real_name AS operatorName,
                       qrl.operated_at AS operatedAt
                FROM question_review_log qrl
                LEFT JOIN sys_user u ON u.id = qrl.operated_by
                WHERE qrl.question_id = ?
                ORDER BY qrl.operated_at DESC, qrl.id DESC
                """, id);
    }

    public PageResult<Map<String, Object>> listReviewAuditLogs(int page, int size, Long logId, Long questionId,
                                                               String keyword, String actionType,
                                                               String reviewStatus, Long subjectId, Long operatorId,
                                                               String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendReviewAuditFilters(where, params, logId, questionId, keyword, actionType, reviewStatus,
                subjectId, operatorId, startFrom, startTo);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM question_review_log qrl
                LEFT JOIN question q ON q.id = qrl.question_id
                LEFT JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN sys_user operator ON operator.id = qrl.operated_by
                LEFT JOIN sys_user creator ON creator.id = q.created_by
                """ + where, Long.class, params.toArray());
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(questionReviewAuditSelectSql() + where + " " + """
                ORDER BY qrl.operated_at DESC, qrl.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(rows, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportReviewAuditLogs(Long logId, Long questionId, String keyword, String actionType,
                                            String reviewStatus, Long subjectId, Long operatorId,
                                            String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendReviewAuditFilters(where, params, logId, questionId, keyword, actionType, reviewStatus,
                subjectId, operatorId, startFrom, startTo);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(questionReviewAuditSelectSql() + where + " " + """
                ORDER BY qrl.operated_at DESC, qrl.id DESC
                LIMIT 5000
                """, params.toArray());
        List<String> headers = List.of("Log ID", "Question ID", "Subject", "Question", "Version", "Action",
                "From Status", "To Status", "From Review", "To Review", "Comment", "Operator", "Operator ID",
                "Creator", "Creator ID", "Operated At");
        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            data.add(List.of(
                    emptyIfNull(row.get("id")),
                    emptyIfNull(row.get("questionId")),
                    emptyIfNull(row.get("subjectName")),
                    emptyIfNull(row.get("questionStem")),
                    emptyIfNull(row.get("versionNo")),
                    emptyIfNull(row.get("actionType")),
                    emptyIfNull(row.get("fromStatus")),
                    emptyIfNull(row.get("toStatus")),
                    emptyIfNull(row.get("fromReviewStatus")),
                    emptyIfNull(row.get("toReviewStatus")),
                    emptyIfNull(row.get("comment")),
                    emptyIfNull(row.get("operatorName")),
                    emptyIfNull(row.get("operatedBy")),
                    emptyIfNull(row.get("creatorName")),
                    emptyIfNull(row.get("createdBy")),
                    emptyIfNull(row.get("operatedAt"))
            ));
        }
        return new ExportFile("question-review-audit-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, data));
    }

    private void requireQuestionVisible(JdbcTemplate jdbcTemplate, Long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT created_by, status, review_status FROM question WHERE id = ? AND deleted = 0", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Question not found: " + id);
        }
        if (user == null || user.hasRole("ADMIN")) {
            return;
        }
        Map<String, Object> row = rows.get(0);
        boolean owner = row.get("created_by") != null && String.valueOf(row.get("created_by")).equals(String.valueOf(user.getId()));
        boolean approved = Objects.equals(intValue(row.get("status")), 1) && REVIEW_APPROVED.equals(String.valueOf(row.get("review_status")));
        boolean pending = REVIEW_PENDING.equals(String.valueOf(row.get("review_status")));
        if (!owner && !approved && !pending) {
            throw new IllegalArgumentException("No permission to view question logs");
        }
    }

    private Map<String, Object> getQuestionState(JdbcTemplate jdbcTemplate, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, status, version_no AS versionNo, review_status AS reviewStatus
                FROM question
                WHERE id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Question not found: " + id);
        }
        return rows.get(0);
    }

    private void snapshotQuestionVersion(JdbcTemplate jdbcTemplate, Long id, String reason, AuthUser operator) {
        jdbcTemplate.update("""
                INSERT INTO question_version (
                    question_id, version_no, subject_id, knowledge_point_id, question_type, difficulty,
                    stem, correct_answer, analysis, default_score, status, review_status, source_type,
                    source_detail, material_id, source_page, source_paragraph, source_excerpt, ai_model,
                    prompt_version, snapshot_reason, snapshot_by
                )
                SELECT id, version_no, subject_id, knowledge_point_id, question_type, difficulty,
                       stem, correct_answer, analysis, default_score, status, review_status, source_type,
                       source_detail, material_id, source_page, source_paragraph, source_excerpt, ai_model,
                       prompt_version, ?, ?
                FROM question
                WHERE id = ? AND deleted = 0
                ON DUPLICATE KEY UPDATE
                    subject_id = VALUES(subject_id),
                    knowledge_point_id = VALUES(knowledge_point_id),
                    question_type = VALUES(question_type),
                    difficulty = VALUES(difficulty),
                    stem = VALUES(stem),
                    correct_answer = VALUES(correct_answer),
                    analysis = VALUES(analysis),
                    default_score = VALUES(default_score),
                    status = VALUES(status),
                    review_status = VALUES(review_status),
                    source_type = VALUES(source_type),
                    source_detail = VALUES(source_detail),
                    material_id = VALUES(material_id),
                    source_page = VALUES(source_page),
                    source_paragraph = VALUES(source_paragraph),
                    source_excerpt = VALUES(source_excerpt),
                    ai_model = VALUES(ai_model),
                    prompt_version = VALUES(prompt_version),
                    snapshot_reason = VALUES(snapshot_reason),
                    snapshot_by = VALUES(snapshot_by)
                """, reason, operator == null ? null : operator.getId(), id);
        Long versionId = currentQuestionVersionId(jdbcTemplate, id);
        jdbcTemplate.update("DELETE FROM question_version_option WHERE question_version_id = ?", versionId);
        jdbcTemplate.update("""
                INSERT INTO question_version_option (
                    question_version_id, question_id, option_label, option_content, is_correct, sort_order
                )
                SELECT ?, question_id, option_label, option_content, is_correct, sort_order
                FROM question_option
                WHERE question_id = ?
                """, versionId, id);
    }

    private Long currentQuestionVersionId(JdbcTemplate jdbcTemplate, Long id) {
        return jdbcTemplate.queryForObject("""
                SELECT qv.id
                FROM question q
                JOIN question_version qv ON qv.question_id = q.id AND qv.version_no = q.version_no
                WHERE q.id = ?
                """, Long.class, id);
    }

    private Long recordQuestionLog(JdbcTemplate jdbcTemplate, Long id, String action, Map<String, Object> before,
                                   Map<String, Object> after, String comment, AuthUser operator) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO question_review_log (
                        question_id, version_no, action_type, from_status, to_status,
                        from_review_status, to_review_status, comment, operated_by
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, id);
            statement.setObject(2, intValue(after == null ? before.get("versionNo") : after.get("versionNo")));
            statement.setString(3, action);
            statement.setObject(4, before == null ? null : intValue(before.get("status")));
            statement.setObject(5, after == null ? null : intValue(after.get("status")));
            statement.setString(6, before == null ? null : String.valueOf(before.get("reviewStatus")));
            statement.setString(7, after == null ? null : String.valueOf(after.get("reviewStatus")));
            statement.setString(8, normalizeOptionalQuestionReviewComment(comment));
            statement.setObject(9, operator == null ? null : operator.getId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private Map<String, Object> withQuestionReviewLogId(Map<String, Object> values, Long questionReviewLogId) {
        Map<String, Object> result = new LinkedHashMap<>(values);
        result.put("questionReviewLogId", questionReviewLogId);
        return result;
    }

    private String questionReviewAuditSelectSql() {
        return """
                SELECT qrl.id,
                       qrl.question_id AS questionId,
                       qrl.version_no AS versionNo,
                       qrl.action_type AS actionType,
                       qrl.from_status AS fromStatus,
                       qrl.to_status AS toStatus,
                       qrl.from_review_status AS fromReviewStatus,
                       qrl.to_review_status AS toReviewStatus,
                       qrl.comment,
                       qrl.operated_by AS operatedBy,
                       operator.real_name AS operatorName,
                       operator.username AS operatorUsername,
                       qrl.operated_at AS operatedAt,
                       q.subject_id AS subjectId,
                       s.subject_name AS subjectName,
                       q.question_type AS questionType,
                       q.difficulty,
                       q.stem AS questionStem,
                       q.status AS questionStatus,
                       q.review_status AS currentReviewStatus,
                       q.deleted AS questionDeleted,
                       q.created_by AS createdBy,
                       creator.real_name AS creatorName,
                       creator.username AS creatorUsername
                FROM question_review_log qrl
                LEFT JOIN question q ON q.id = qrl.question_id
                LEFT JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN sys_user operator ON operator.id = qrl.operated_by
                LEFT JOIN sys_user creator ON creator.id = q.created_by
                """;
    }

    private void appendReviewAuditFilters(StringBuilder where, List<Object> params, Long logId, Long questionId,
                                          String keyword, String actionType, String reviewStatus, Long subjectId,
                                          Long operatorId, String startFrom, String startTo) {
        if (logId != null) {
            where.append(" AND qrl.id = ?");
            params.add(logId);
        }
        if (questionId != null) {
            where.append(" AND qrl.question_id = ?");
            params.add(questionId);
        }
        String safeAction = normalizeCode(actionType);
        if (safeAction != null && !safeAction.isBlank()) {
            where.append(" AND qrl.action_type = ?");
            params.add(safeAction);
        }
        String safeReviewStatus = normalizeReviewStatusOrNull(reviewStatus);
        if (safeReviewStatus != null) {
            where.append(" AND (qrl.from_review_status = ? OR qrl.to_review_status = ? OR q.review_status = ?)");
            params.add(safeReviewStatus);
            params.add(safeReviewStatus);
            params.add(safeReviewStatus);
        }
        if (subjectId != null) {
            where.append(" AND q.subject_id = ?");
            params.add(subjectId);
        }
        if (operatorId != null) {
            where.append(" AND qrl.operated_by = ?");
            params.add(operatorId);
        }
        String kw = blankToNull(keyword);
        if (kw != null) {
            where.append("""
                    AND (q.stem LIKE CONCAT('%', ?, '%')
                      OR s.subject_name LIKE CONCAT('%', ?, '%')
                      OR qrl.action_type LIKE CONCAT('%', ?, '%')
                      OR qrl.comment LIKE CONCAT('%', ?, '%')
                      OR operator.real_name LIKE CONCAT('%', ?, '%')
                      OR operator.username LIKE CONCAT('%', ?, '%')
                      OR creator.real_name LIKE CONCAT('%', ?, '%')
                      OR creator.username LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND qrl.operated_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND qrl.operated_at <= ?");
            params.add(startTo.trim());
        }
    }

    private String questionReviewStatus(JdbcTemplate jdbcTemplate, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT review_status FROM question WHERE id = ? AND deleted = 0", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("题目不存在");
        }
        Object status = rows.get(0).get("review_status");
        return status == null ? REVIEW_DRAFT : String.valueOf(status);
    }

    private void enrichQuestionRow(Map<String, Object> row, AuthUser user) {
        row.put("options", listOptionsFromDatabase(longValue(row.get("id"))));
        boolean owner = isOwner(row, user);
        boolean admin = user != null && user.hasRole("ADMIN");
        boolean teacher = user != null && user.hasRole("TEACHER");
        String reviewStatus = String.valueOf(row.getOrDefault("reviewStatus", REVIEW_DRAFT));
        boolean available = Objects.equals(intValue(row.get("status")), 1);
        row.put("canEdit", admin || owner);
        row.put("canDelete", admin || owner);
        row.put("canSubmitReview", (admin || owner) && !available && (REVIEW_DRAFT.equals(reviewStatus) || REVIEW_REJECTED.equals(reviewStatus)));
        row.put("canReview", REVIEW_PENDING.equals(reviewStatus) && (admin || (teacher && !owner)));
        row.put("canTakeOffline", (admin || owner) && available);
    }

    private boolean isOwner(Map<String, Object> row, AuthUser user) {
        if (user == null) {
            return false;
        }
        Object createdBy = row.get("createdBy");
        return createdBy != null && String.valueOf(createdBy).equals(String.valueOf(user.getId()));
    }

    private String normalizeReviewStatusOrNull(String value) {
        String normalized = normalizeCode(value);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (!List.of(REVIEW_DRAFT, REVIEW_PENDING, REVIEW_APPROVED, REVIEW_REJECTED).contains(normalized)) {
            throw new IllegalArgumentException("Unsupported question review status: " + value);
        }
        return normalized;
    }

    private void requireReviewer(JdbcTemplate jdbcTemplate, Long id, AuthUser reviewer) {
        if (reviewer == null || (!reviewer.hasRole("ADMIN") && !reviewer.hasRole("TEACHER"))) {
            throw new IllegalArgumentException("无题目审核权限");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT created_by FROM question WHERE id = ? AND deleted = 0", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("题目不存在");
        }
        Object createdBy = rows.get(0).get("created_by");
        if (!reviewer.hasRole("ADMIN") && createdBy != null && String.valueOf(createdBy).equals(String.valueOf(reviewer.getId()))) {
            throw new IllegalArgumentException("教师不能审核自己创建的题目");
        }
    }

    private void validateQuestionReadyForReview(JdbcTemplate jdbcTemplate, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT question_type, correct_answer FROM question WHERE id = ? AND deleted = 0", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("题目不存在");
        }
        Map<String, Object> row = rows.get(0);
        String type = String.valueOf(row.get("question_type"));
        if (OBJECTIVE_TYPES.contains(type)) {
            Long correctCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM question_option WHERE question_id = ? AND is_correct = 1", Long.class, id);
            if ("SINGLE_CHOICE".equals(type) && (correctCount == null || correctCount != 1)) {
                throw new IllegalArgumentException("单选题必须且只能有一个正确选项");
            }
            if ("MULTIPLE_CHOICE".equals(type) && (correctCount == null || correctCount < 2)) {
                throw new IllegalArgumentException("多选题至少需要两个正确选项");
            }
            if ("TRUE_FALSE".equals(type) && (correctCount == null || correctCount != 1)) {
                throw new IllegalArgumentException("判断题必须且只能有一个正确选项");
            }
            return;
        }
        Object correctAnswer = row.get("correct_answer");
        if (correctAnswer == null || String.valueOf(correctAnswer).isBlank()) {
            throw new IllegalArgumentException("填空题或主观题提交审核前必须填写参考答案");
        }
    }

    private void validateQuestion(QuestionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Question request is required");
        }
        normalizeQuestionStem(request.getStem());
        normalizeQuestionCorrectAnswer(request.getCorrectAnswer());
        normalizeQuestionAnalysis(request.getAnalysis());
        String type = normalizeCode(request.getQuestionType());
        String difficulty = normalizeCode(request.getDifficulty());
        boolean draft = request.getStatus() == null || request.getStatus() == 0;
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
            validateQuestionOptions(request.getOptions());
            long correctCount = request.getOptions().stream().filter(option -> Boolean.TRUE.equals(option.getCorrect())).count();
            if (draft) {
                return;
            }
            if ("SINGLE_CHOICE".equals(type) && correctCount != 1) {
                throw new IllegalArgumentException("单选题必须且只能设置一个正确选项");
            }
            if ("MULTIPLE_CHOICE".equals(type) && correctCount < 2) {
                throw new IllegalArgumentException("多选题至少需要两个正确选项");
            }
            if ("TRUE_FALSE".equals(type) && correctCount != 1) {
                throw new IllegalArgumentException("判断题必须且只能设置一个正确选项");
            }
        } else if (!draft && normalizeQuestionCorrectAnswer(request.getCorrectAnswer()) == null) {
            throw new IllegalArgumentException("非选择题必须填写参考答案");
        }
    }

    private void validateQuestionSource(JdbcTemplate jdbcTemplate, QuestionRequest request, AuthUser user) {
        validateQuestionSource(jdbcTemplate, request.getSourceType(), request.getMaterialId(), request.getSubjectId(), user);
    }

    private void validateQuestionSource(JdbcTemplate jdbcTemplate,
                                        String sourceTypeValue,
                                        Long materialId,
                                        Long subjectId,
                                        AuthUser user) {
        String sourceType = questionSourceTypeOrDefault(sourceTypeValue);
        if ("AI_RAG".equals(sourceType) && materialId == null) {
            throw new IllegalArgumentException("AI_RAG questions must reference a course material");
        }
        if (materialId == null) {
            return;
        }
        if (materialId <= 0) {
            throw new IllegalArgumentException("Question materialId must be positive");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, subject_id AS subjectId, uploaded_by AS uploadedBy
                FROM course_material
                WHERE id = ? AND deleted = 0
                """, materialId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Question source material not found");
        }
        Map<String, Object> material = rows.get(0);
        if (!Objects.equals(longValue(material.get("subjectId")), subjectId)) {
            throw new IllegalArgumentException("Question source material subject must match question subject");
        }
        boolean admin = user != null && user.hasRole("ADMIN");
        if (!admin && (user == null || !Objects.equals(longValue(material.get("uploadedBy")), user.getId()))) {
            throw new IllegalArgumentException("Question source material is not accessible");
        }
    }

    private Map<String, Object> getQuestionById(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT q.id, q.subject_id AS subjectId, s.subject_name AS subjectName,
                       q.knowledge_point_id AS knowledgePointId, kp.point_name AS knowledgePointName,
                       q.question_type AS questionType, q.difficulty, q.stem, q.correct_answer AS correctAnswer,
                       q.analysis, q.default_score AS defaultScore, q.status, q.version_no AS versionNo, q.review_status AS reviewStatus,
                       q.reviewed_by AS reviewedBy, reviewer.real_name AS reviewerName,
                       q.reviewed_at AS reviewedAt, q.review_comment AS reviewComment, q.created_by AS createdBy,
                       q.source_type AS sourceType, q.source_detail AS sourceDetail,
                       q.material_id AS materialId, q.source_page AS sourcePage, q.source_paragraph AS sourceParagraph,
                       q.source_excerpt AS sourceExcerpt, q.ai_model AS aiModel, q.prompt_version AS promptVersion,
                       u.real_name AS creatorName, q.created_at AS createdAt, q.updated_at AS updatedAt
                FROM question q
                JOIN edu_subject s ON s.id = q.subject_id
                LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                LEFT JOIN sys_user u ON u.id = q.created_by
                LEFT JOIN sys_user reviewer ON reviewer.id = q.reviewed_by
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
                    """, questionId, normalizeOptionLabel(option), normalizeOptionContent(option),
                    Boolean.TRUE.equals(option.getCorrect()) ? 1 : 0, sort++);
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

    private String normalizeQuestionStem(String value) {
        return normalizeRequiredQuestionText(value, MAX_QUESTION_TEXT_LENGTH, "Question stem");
    }

    private String normalizeQuestionCorrectAnswer(String value) {
        return normalizeOptionalQuestionText(value, MAX_QUESTION_TEXT_LENGTH, "Question correct answer");
    }

    private String normalizeQuestionAnalysis(String value) {
        return normalizeOptionalQuestionText(value, MAX_QUESTION_TEXT_LENGTH, "Question analysis");
    }

    private void validateQuestionOptions(List<QuestionOptionRequest> options) {
        for (QuestionOptionRequest option : options) {
            normalizeOptionLabel(option);
            normalizeOptionContent(option);
        }
    }

    private String normalizeOptionLabel(QuestionOptionRequest option) {
        if (option == null) {
            throw new IllegalArgumentException("Question option is required");
        }
        return normalizeRequiredQuestionText(option.getOptionLabel(), MAX_OPTION_LABEL_LENGTH, "Question option label");
    }

    private String normalizeOptionContent(QuestionOptionRequest option) {
        if (option == null) {
            throw new IllegalArgumentException("Question option is required");
        }
        return normalizeRequiredQuestionText(option.getOptionContent(), MAX_OPTION_CONTENT_LENGTH, "Question option content");
    }

    private String normalizeRequiredQuestionText(String value, int maxLength, String fieldName) {
        String normalized = normalizeOptionalQuestionText(value, maxLength, fieldName);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptionalQuestionText(String value, int maxLength, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be " + maxLength + " characters or less");
        }
        return normalized;
    }

    private String normalizeOptionalQuestionReviewComment(String comment) {
        String normalized = blankToNull(comment);
        if (normalized != null && normalized.length() > MAX_QUESTION_REVIEW_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Question review comment must be 500 characters or less");
        }
        return normalized;
    }

    private String normalizeRequiredQuestionReviewComment(String comment) {
        String normalized = normalizeOptionalQuestionReviewComment(comment);
        if (normalized == null) {
            throw new IllegalArgumentException("驳回题目必须填写审核意见");
        }
        return normalized;
    }

    private String normalizeSourceDetail(String value) {
        return normalizeQuestionSourceMetadata(value, MAX_SOURCE_DETAIL_LENGTH, "Question source detail");
    }

    private String normalizeSourceExcerpt(String value) {
        return normalizeQuestionSourceMetadata(value, MAX_SOURCE_EXCERPT_LENGTH, "Question source excerpt");
    }

    private String normalizeAiModel(String value) {
        return normalizeQuestionSourceMetadata(value, MAX_AI_MODEL_LENGTH, "Question AI model");
    }

    private String normalizePromptVersion(String value) {
        return normalizeQuestionSourceMetadata(value, MAX_PROMPT_VERSION_LENGTH, "Question prompt version");
    }

    private String normalizeQuestionSourceMetadata(String value, int maxLength, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be " + maxLength + " characters or less");
        }
        return normalized;
    }

    private Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
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

    private String firstNonBlank(String first, String fallback) {
        String normalized = blankToNull(first);
        return normalized == null ? fallback : normalized;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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

    private Object emptyIfNull(Object value) {
        return value == null ? "" : value;
    }
}
