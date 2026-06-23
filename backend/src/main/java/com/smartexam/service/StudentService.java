package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.student.ExamResult;
import com.smartexam.dto.student.GradeInfo;
import com.smartexam.dto.student.WrongQuestionInfo;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StudentService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public StudentService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public List<GradeInfo> getGrades(AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        boolean appealEnabled = configBoolean(jdbcTemplate, "score.appealEnabled", true);
        int appealWindowDays = configNumber(jdbcTemplate, "score.appealWindowDays", 7);
        return jdbcTemplate.query("""
                SELECT ea.id as attemptId, e.exam_name, s.subject_name,
                       CASE WHEN COALESCE(sr.status, 0) = 1 AND ea.status = 5 AND ea.score IS NOT NULL
                              AND NOT EXISTS (
                                SELECT 1 FROM score_appeal sa
                                WHERE sa.attempt_id = ea.id
                                  AND sa.status = 1
                                  AND sa.handling_result = 'RECHECK_REQUIRED'
                              )
                            THEN ea.score ELSE NULL END AS visible_score,
                       ea.submit_time, ea.status,
                       COALESCE(sr.status, 0) AS scoreReleaseStatus,
                       sr.published_at AS scorePublishedAt,
                       sr.revoked_at AS scoreRevokedAt,
                       COALESCE(sr.revoke_reason, sr.note) AS scoreRevokeReason,
                       COALESCE(NULLIF((SELECT COUNT(*)
                                        FROM exam_question_snapshot eqs_count
                                        WHERE eqs_count.exam_id = e.id), 0),
                                (SELECT COUNT(*)
                                 FROM paper_question pq_count
                                 WHERE pq_count.paper_id = e.paper_id)) AS questionCount,
                       CASE WHEN EXISTS (
                         SELECT 1
                         FROM exam_question_snapshot eqs_exists
                         WHERE eqs_exists.exam_id = e.id
                       ) THEN (
                         SELECT COUNT(DISTINCT ar_answered.question_id)
                         FROM answer_record ar_answered
                         JOIN exam_question_snapshot eqs_answered
                           ON eqs_answered.exam_id = e.id
                          AND eqs_answered.question_id = ar_answered.question_id
                         WHERE ar_answered.attempt_id = ea.id
                           AND ar_answered.answer_content IS NOT NULL
                           AND TRIM(ar_answered.answer_content) <> ''
                       ) ELSE (
                         SELECT COUNT(DISTINCT ar_answered.question_id)
                         FROM answer_record ar_answered
                         JOIN paper_question pq_answered
                           ON pq_answered.paper_id = e.paper_id
                          AND pq_answered.question_id = ar_answered.question_id
                         WHERE ar_answered.attempt_id = ea.id
                           AND ar_answered.answer_content IS NOT NULL
                           AND TRIM(ar_answered.answer_content) <> ''
                       ) END AS answeredCount,
                       GREATEST(0,
                           COALESCE(NULLIF((SELECT COUNT(*)
                                            FROM exam_question_snapshot eqs_total
                                            WHERE eqs_total.exam_id = e.id), 0),
                                    (SELECT COUNT(*)
                                     FROM paper_question pq_total
                                     WHERE pq_total.paper_id = e.paper_id))
                           - CASE WHEN EXISTS (
                               SELECT 1
                               FROM exam_question_snapshot eqs_exists
                               WHERE eqs_exists.exam_id = e.id
                             ) THEN (
                               SELECT COUNT(DISTINCT ar_missing.question_id)
                               FROM answer_record ar_missing
                               JOIN exam_question_snapshot eqs_answered
                                 ON eqs_answered.exam_id = e.id
                                AND eqs_answered.question_id = ar_missing.question_id
                               WHERE ar_missing.attempt_id = ea.id
                                 AND ar_missing.answer_content IS NOT NULL
                                 AND TRIM(ar_missing.answer_content) <> ''
                             ) ELSE (
                               SELECT COUNT(DISTINCT ar_missing.question_id)
                               FROM answer_record ar_missing
                               JOIN paper_question pq_answered
                                 ON pq_answered.paper_id = e.paper_id
                                AND pq_answered.question_id = ar_missing.question_id
                               WHERE ar_missing.attempt_id = ea.id
                                 AND ar_missing.answer_content IS NOT NULL
                                 AND TRIM(ar_missing.answer_content) <> ''
                             ) END) AS unansweredCount,
                       CASE WHEN COALESCE(sr.status, 0) = 1 AND ea.status = 5 AND ea.score IS NOT NULL
                              AND NOT EXISTS (
                                SELECT 1 FROM score_appeal sa
                                WHERE sa.attempt_id = ea.id
                                  AND sa.status = 1
                                  AND sa.handling_result = 'RECHECK_REQUIRED'
                              )
                            THEN 1 ELSE 0 END AS scoreVisible,
                       CASE
                         WHEN ea.status = 4 THEN 'PENDING_REVIEW'
                         WHEN EXISTS (
                                SELECT 1 FROM score_appeal sa
                                WHERE sa.attempt_id = ea.id
                                  AND sa.status = 1
                                  AND sa.handling_result = 'RECHECK_REQUIRED'
                              ) THEN 'PENDING_RECHECK'
                         WHEN ea.status <> 5 THEN 'PENDING_FINALIZE'
                         WHEN ea.score IS NULL THEN 'PENDING_SCORE'
                         WHEN COALESCE(sr.status, 0) = 1 THEN 'RELEASED'
                         WHEN sr.revoked_at IS NOT NULL OR COALESCE(sr.revoke_reason, sr.note) IS NOT NULL THEN 'REVOKED'
                         ELSE 'PENDING_RELEASE'
                       END AS scoreVisibility
                FROM exam_attempt ea
                JOIN exam e ON ea.exam_id = e.id
                JOIN paper p ON e.paper_id = p.id
                JOIN edu_subject s ON p.subject_id = s.id
                LEFT JOIN score_release sr ON sr.exam_id = e.id
                WHERE ea.user_id = ? AND ea.status IN (2, 4, 5) AND e.deleted = 0
                ORDER BY COALESCE(sr.published_at, ea.submit_time) DESC, ea.id DESC
                """,
                (rs, rowNum) -> mapGradeInfo(rs, "visible_score", appealEnabled, appealWindowDays), user.getId());
    }

    public ExamResult getExamResult(Long attemptId, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        boolean appealEnabled = configBoolean(jdbcTemplate, "score.appealEnabled", true);
        int appealWindowDays = configNumber(jdbcTemplate, "score.appealWindowDays", 7);
        List<GradeInfo> gradeRows = jdbcTemplate.query("""
                SELECT ea.id as attemptId, e.exam_name, s.subject_name, ea.score, ea.submit_time, ea.status,
                       COALESCE(sr.status, 0) AS scoreReleaseStatus,
                       sr.published_at AS scorePublishedAt,
                       sr.revoked_at AS scoreRevokedAt,
                       COALESCE(sr.revoke_reason, sr.note) AS scoreRevokeReason,
                       COALESCE(NULLIF((SELECT COUNT(*)
                                        FROM exam_question_snapshot eqs_count
                                        WHERE eqs_count.exam_id = e.id), 0),
                                (SELECT COUNT(*)
                                 FROM paper_question pq_count
                                 WHERE pq_count.paper_id = e.paper_id)) AS questionCount,
                       CASE WHEN EXISTS (
                         SELECT 1
                         FROM exam_question_snapshot eqs_exists
                         WHERE eqs_exists.exam_id = e.id
                       ) THEN (
                         SELECT COUNT(DISTINCT ar_answered.question_id)
                         FROM answer_record ar_answered
                         JOIN exam_question_snapshot eqs_answered
                           ON eqs_answered.exam_id = e.id
                          AND eqs_answered.question_id = ar_answered.question_id
                         WHERE ar_answered.attempt_id = ea.id
                           AND ar_answered.answer_content IS NOT NULL
                           AND TRIM(ar_answered.answer_content) <> ''
                       ) ELSE (
                         SELECT COUNT(DISTINCT ar_answered.question_id)
                         FROM answer_record ar_answered
                         JOIN paper_question pq_answered
                           ON pq_answered.paper_id = e.paper_id
                          AND pq_answered.question_id = ar_answered.question_id
                         WHERE ar_answered.attempt_id = ea.id
                           AND ar_answered.answer_content IS NOT NULL
                           AND TRIM(ar_answered.answer_content) <> ''
                       ) END AS answeredCount,
                       GREATEST(0,
                           COALESCE(NULLIF((SELECT COUNT(*)
                                            FROM exam_question_snapshot eqs_total
                                            WHERE eqs_total.exam_id = e.id), 0),
                                    (SELECT COUNT(*)
                                     FROM paper_question pq_total
                                     WHERE pq_total.paper_id = e.paper_id))
                           - CASE WHEN EXISTS (
                               SELECT 1
                               FROM exam_question_snapshot eqs_exists
                               WHERE eqs_exists.exam_id = e.id
                             ) THEN (
                               SELECT COUNT(DISTINCT ar_missing.question_id)
                               FROM answer_record ar_missing
                               JOIN exam_question_snapshot eqs_answered
                                 ON eqs_answered.exam_id = e.id
                                AND eqs_answered.question_id = ar_missing.question_id
                               WHERE ar_missing.attempt_id = ea.id
                                 AND ar_missing.answer_content IS NOT NULL
                                 AND TRIM(ar_missing.answer_content) <> ''
                             ) ELSE (
                               SELECT COUNT(DISTINCT ar_missing.question_id)
                               FROM answer_record ar_missing
                               JOIN paper_question pq_answered
                                 ON pq_answered.paper_id = e.paper_id
                                AND pq_answered.question_id = ar_missing.question_id
                               WHERE ar_missing.attempt_id = ea.id
                                 AND ar_missing.answer_content IS NOT NULL
                                 AND TRIM(ar_missing.answer_content) <> ''
                             ) END) AS unansweredCount,
                       1 AS scoreVisible,
                       'RELEASED' AS scoreVisibility
                FROM exam_attempt ea
                JOIN exam e ON ea.exam_id = e.id
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                JOIN paper p ON e.paper_id = p.id
                JOIN edu_subject s ON p.subject_id = s.id
                WHERE ea.id = ? AND ea.user_id = ? AND ea.status = 5 AND ea.score IS NOT NULL AND e.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                """,
                (rs, rowNum) -> mapGradeInfo(rs, "score", appealEnabled, appealWindowDays), attemptId, user.getId());
        if (gradeRows.isEmpty()) {
            throw new IllegalStateException("Score has not been released");
        }
        GradeInfo gradeInfo = gradeRows.get(0);

        List<Map<String, Object>> answers = jdbcTemplate.queryForList("""
                SELECT expected.questionId AS questionId,
                       ea.exam_id AS examId,
                       expected.stem AS stem,
                       expected.questionType AS questionType,
                       expected.correctAnswer AS correctAnswer,
                       expected.analysis AS analysis,
                       COALESCE(ar.answer_content, '') AS studentAnswer,
                       COALESCE(ar.score, 0) AS score,
                       COALESCE(ar.is_correct, 0) AS isCorrect
                FROM exam_attempt ea
                JOIN (
                    SELECT eqs.exam_id AS examId,
                           eqs.question_id AS questionId,
                           eqs.stem AS stem,
                           eqs.question_type AS questionType,
                           eqs.correct_answer AS correctAnswer,
                           eqs.analysis AS analysis,
                           eqs.sort_order AS sortOrder
                    FROM exam_question_snapshot eqs
                    WHERE eqs.exam_id = (SELECT snapshot_attempt.exam_id FROM exam_attempt snapshot_attempt WHERE snapshot_attempt.id = ?)
                    UNION ALL
                    SELECT e2.id AS examId,
                           pq.question_id AS questionId,
                           q.stem AS stem,
                           q.question_type AS questionType,
                           q.correct_answer AS correctAnswer,
                           q.analysis AS analysis,
                           pq.sort_order AS sortOrder
                    FROM exam e2
                    JOIN paper_question pq ON pq.paper_id = e2.paper_id
                    JOIN question q ON q.id = pq.question_id
                    WHERE e2.id = (SELECT paper_attempt.exam_id FROM exam_attempt paper_attempt WHERE paper_attempt.id = ?)
                      AND NOT EXISTS (
                        SELECT 1 FROM exam_question_snapshot snapshot_check
                        WHERE snapshot_check.exam_id = e2.id
                      )
                ) expected ON expected.examId = ea.exam_id
                LEFT JOIN answer_record ar ON ar.attempt_id = ea.id AND ar.question_id = expected.questionId
                WHERE ea.id = ?
                ORDER BY expected.sortOrder, expected.questionId
                """, attemptId, attemptId, attemptId);
        for (Map<String, Object> answer : answers) {
            answer.put("options", snapshotQuestionOptions(jdbcTemplate,
                    numberValue(answer.get("examId")), numberValue(answer.get("questionId"))));
            answer.remove("examId");
        }

        return new ExamResult(gradeInfo, answers);
    }

    private String timestampText(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toString();
    }

    private GradeInfo mapGradeInfo(ResultSet rs, String scoreColumn, boolean appealEnabled, int appealWindowDays)
            throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("scorePublishedAt");
        boolean scoreVisible = rs.getInt("scoreVisible") == 1;
        String appealDeadlineAt = appealDeadlineText(publishedAt, appealWindowDays);
        boolean appealOpen = scoreVisible && appealEnabled && !appealWindowExpired(publishedAt, appealWindowDays);
        return new GradeInfo(
                rs.getLong("attemptId"),
                rs.getString("exam_name"),
                rs.getString("subject_name"),
                rs.getBigDecimal(scoreColumn),
                timestampText(rs.getTimestamp("submit_time")),
                rs.getInt("status"),
                scoreVisible,
                rs.getString("scoreVisibility"),
                rs.getInt("scoreReleaseStatus"),
                timestampText(publishedAt),
                timestampText(rs.getTimestamp("scoreRevokedAt")),
                rs.getString("scoreRevokeReason"),
                appealOpen,
                appealDeadlineAt,
                appealWindowDays,
                rs.getInt("questionCount"),
                rs.getInt("answeredCount"),
                rs.getInt("unansweredCount")
        );
    }

    private boolean appealWindowExpired(Timestamp publishedAt, int appealWindowDays) {
        if (appealWindowDays <= 0 || publishedAt == null) {
            return false;
        }
        return publishedAt.toLocalDateTime().plusDays(appealWindowDays).isBefore(LocalDateTime.now());
    }

    private String appealDeadlineText(Timestamp publishedAt, int appealWindowDays) {
        if (appealWindowDays <= 0 || publishedAt == null) {
            return null;
        }
        return publishedAt.toLocalDateTime().plusDays(appealWindowDays).toString();
    }

    private boolean configBoolean(JdbcTemplate jt, String key, boolean fallback) {
        String value = configValue(jt, key);
        if (value == null) {
            return fallback;
        }
        return "true".equals(value.trim().toLowerCase(Locale.ROOT));
    }

    private int configNumber(JdbcTemplate jt, String key, int fallback) {
        String value = configValue(jt, key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String configValue(JdbcTemplate jt, String key) {
        List<String> rows = jt.queryForList("""
                SELECT config_value
                FROM system_config
                WHERE config_key = ?
                """, String.class, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<WrongQuestionInfo> getWrongQuestions(AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.query("""
            WITH wrong_answers AS (
              SELECT ar.id AS answer_record_id,
                     ar.question_id,
                     e.id AS exam_id,
                     COALESCE(eqs.stem, q.stem) AS stem,
                     COALESCE(eqs.question_type, q.question_type) AS question_type,
                     COALESCE(eqs.correct_answer, q.correct_answer) AS correct_answer,
                     COALESCE(eqs.analysis, q.analysis) AS analysis,
                     COALESCE(ea.submit_time, ar.created_at) AS wrong_time
              FROM answer_record ar
              JOIN exam_attempt ea ON ea.id = ar.attempt_id
              JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
              JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
              JOIN question q ON ar.question_id = q.id
              LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = ar.question_id
                WHERE ea.user_id = ?
                AND ea.status = 5
                AND ea.score IS NOT NULL
                AND ar.review_status = 1
                AND ar.is_correct = 0
                AND NOT EXISTS (
                  SELECT 1 FROM score_appeal sa
                  WHERE sa.attempt_id = ea.id
                    AND sa.status = 1
                    AND sa.handling_result = 'RECHECK_REQUIRED'
                )
            ),
            ranked_wrong AS (
              SELECT wrong_answers.*,
                     COUNT(*) OVER (PARTITION BY question_id, exam_id) AS wrong_count,
                     ROW_NUMBER() OVER (
                       PARTITION BY question_id, exam_id
                       ORDER BY wrong_time DESC, answer_record_id DESC
                     ) AS row_no
              FROM wrong_answers
            )
            SELECT question_id,
                   stem,
                   question_type,
                   correct_answer,
                   analysis,
                   wrong_count,
                   wrong_time AS last_wrong_time,
                   exam_id AS latest_exam_id
            FROM ranked_wrong
            WHERE row_no = 1
            ORDER BY wrong_time DESC, answer_record_id DESC
            """, (rs, rowNum) -> new WrongQuestionInfo(
                        rs.getLong("question_id"),
                        rs.getLong("latest_exam_id"),
                        rs.getString("stem"),
                        rs.getString("question_type"),
                        rs.getString("correct_answer"),
                        rs.getString("analysis"),
                        rs.getInt("wrong_count"),
                        rs.getTimestamp("last_wrong_time").toString(),
                        snapshotQuestionOptions(jdbcTemplate, rs.getLong("latest_exam_id"), rs.getLong("question_id"))
                ), user.getId());
    }

    private List<Map<String, Object>> snapshotQuestionOptions(JdbcTemplate jdbcTemplate, Long examId, Long questionId) {
        List<Map<String, Object>> snapshotOptions = jdbcTemplate.queryForList("""
                SELECT eqos.option_label AS optionLabel,
                       eqos.option_content AS optionContent,
                       eqs.correct_answer AS correctAnswer
                FROM exam_question_option_snapshot eqos
                JOIN exam_question_snapshot eqs
                  ON eqs.exam_id = eqos.exam_id AND eqs.question_id = eqos.question_id
                WHERE eqos.exam_id = ? AND eqos.question_id = ?
                ORDER BY eqos.sort_order
                """, examId, questionId);
        if (!snapshotOptions.isEmpty()) {
            for (Map<String, Object> option : snapshotOptions) {
                option.put("correct", answerContainsOption(option.get("correctAnswer"), option.get("optionLabel")) ? 1 : 0);
                option.remove("correctAnswer");
            }
            return snapshotOptions;
        }
        return jdbcTemplate.queryForList("""
                SELECT option_label AS optionLabel, option_content AS optionContent, is_correct AS correct
                FROM question_option
                WHERE question_id = ?
                ORDER BY sort_order
                """, questionId);
    }

    private boolean answerContainsOption(Object correctAnswer, Object optionLabel) {
        String answer = normalizeAnswerToken(correctAnswer);
        String label = normalizeAnswerToken(optionLabel);
        return !label.isEmpty() && answer.contains(label);
    }

    private String normalizeAnswerToken(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        StringBuilder normalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                normalized.append(Character.toUpperCase(ch));
            }
        }
        return normalized.toString();
    }

    private Long numberValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    public Map<String, Double> getKnowledgePointMastery(AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.query("""
            SELECT kp.point_name, AVG(ar.score / expected.score) as mastery
                FROM answer_record ar
                JOIN exam_attempt ea ON ar.attempt_id = ea.id
                JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                JOIN (
                    SELECT eqs.exam_id AS examId,
                           eqs.question_id AS questionId,
                           eqs.knowledge_point_id AS knowledgePointId,
                           eqs.score AS score
                    FROM exam_question_snapshot eqs
                    UNION ALL
                    SELECT e2.id AS examId,
                           pq.question_id AS questionId,
                           q.knowledge_point_id AS knowledgePointId,
                           pq.score AS score
                    FROM exam e2
                    JOIN paper_question pq ON pq.paper_id = e2.paper_id
                    JOIN question q ON q.id = pq.question_id
                    WHERE NOT EXISTS (
                        SELECT 1 FROM exam_question_snapshot snapshot_check
                        WHERE snapshot_check.exam_id = e2.id
                    )
                ) expected ON expected.examId = e.id AND expected.questionId = ar.question_id
                JOIN edu_knowledge_point kp ON kp.id = expected.knowledgePointId
                WHERE ea.user_id = ? AND ea.status = 5 AND ea.score IS NOT NULL
                  AND expected.score > 0
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                GROUP BY kp.id, kp.point_name
            """, (rs) -> {
            Map<String, Double> masteryMap = new java.util.HashMap<>();
            while (rs.next()) {
                masteryMap.put(rs.getString("point_name"), rs.getDouble("mastery"));
            }
            return masteryMap;
        }, user.getId());
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }
}
