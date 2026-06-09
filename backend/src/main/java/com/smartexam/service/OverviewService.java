package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OverviewService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public OverviewService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public Map<String, Object> adminOverview() {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();

        // 统计卡片
        data.put("totalStudents", queryInt(jt, "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id WHERE r.role_code = 'STUDENT' AND u.deleted = 0"));
        data.put("totalTeachers", queryInt(jt, "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id WHERE r.role_code = 'TEACHER' AND u.deleted = 0"));
        data.put("todayExams", queryInt(jt, "SELECT COUNT(*) FROM exam WHERE deleted = 0 AND DATE(start_time) = CURDATE()"));
        data.put("runningExams", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0
                  AND start_time <= NOW()
                  AND (end_time IS NULL OR end_time >= NOW())
                """));
        data.put("pendingReviews", queryInt(jt, "SELECT COUNT(*) FROM exam_attempt WHERE status = 4"));
        data.put("totalPapers", queryInt(jt, "SELECT COUNT(*) FROM paper WHERE deleted = 0"));
        data.put("totalQuestions", queryInt(jt, "SELECT COUNT(*) FROM question WHERE deleted = 0"));

        // 学科分布（teacher_profile 无学科关联字段，改以各科目下的题目数量反映学科分布）
        data.put("teacherSubjects", jt.queryForList("""
                SELECT s.subject_name AS name, COUNT(q.id) AS value
                FROM edu_subject s
                LEFT JOIN question q ON q.subject_id = s.id AND q.deleted = 0
                WHERE s.deleted = 0
                GROUP BY s.id, s.subject_name
                ORDER BY value DESC
                LIMIT 8
                """));

        // 学生年级分布
        data.put("studentGrades", jt.queryForList("""
                SELECT c.grade AS name, COUNT(sp.user_id) AS value
                FROM student_profile sp
                JOIN sys_user u ON u.id = sp.user_id AND u.deleted = 0
                LEFT JOIN edu_class c ON c.id = COALESCE(sp.primary_class_id, sp.class_id)
                GROUP BY c.grade
                ORDER BY value DESC
                """));

        // 考试通过率趋势（最近7天）
        data.put("examTrend", jt.queryForList("""
                SELECT DATE(ea.submit_time) AS date,
                       COUNT(*) AS total,
                       SUM(CASE WHEN ea.score >= COALESCE(e.pass_score, 60) THEN 1 ELSE 0 END) AS passed
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                WHERE ea.submit_time IS NOT NULL AND ea.submit_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                GROUP BY DATE(ea.submit_time)
                ORDER BY date
                """));

        data.put("recentExams", jt.queryForList("""
                SELECT e.id, e.exam_name AS name, e.start_time AS time, e.end_time AS endTime,
                       CASE
                         WHEN e.end_time IS NOT NULL AND e.end_time < NOW() THEN 2
                         WHEN e.start_time IS NOT NULL AND e.start_time <= NOW()
                              AND (e.end_time IS NULL OR e.end_time >= NOW()) THEN 1
                         ELSE 0
                       END AS phase,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id) AS attemptCount,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status IN (2,4,5)) AS submittedCount
                FROM exam e
                WHERE e.deleted = 0
                ORDER BY e.start_time DESC
                LIMIT 6
                """));

        return data;
    }

    public Map<String, Object> teacherOverview(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();

        // 我的考试任务数
        data.put("myExams", queryInt(jt, "SELECT COUNT(*) FROM exam WHERE deleted = 0 AND created_by = ?", user.getId()));
        data.put("runningExams", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0 AND created_by = ?
                  AND start_time <= NOW()
                  AND (end_time IS NULL OR end_time >= NOW())
                """, user.getId()));
        data.put("upcomingExams", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0 AND created_by = ?
                  AND (start_time IS NULL OR start_time > NOW())
                """, user.getId()));
        data.put("finishedExams", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0 AND created_by = ?
                  AND end_time IS NOT NULL AND end_time < NOW()
                """, user.getId()));
        // 待批阅试卷（status=1 表示已提交未批阅）
        data.put("pendingReviews", queryInt(jt,
                "SELECT COUNT(*) FROM exam_attempt WHERE status = 4 AND exam_id IN (SELECT id FROM exam WHERE created_by = ? AND deleted = 0)",
                user.getId()));
        // 我创建的试卷数
        data.put("myPapers", queryInt(jt, "SELECT COUNT(*) FROM paper WHERE deleted = 0 AND created_by = ?", user.getId()));
        data.put("publishedPapers", queryInt(jt, "SELECT COUNT(*) FROM paper WHERE deleted = 0 AND created_by = ? AND status = 1", user.getId()));
        data.put("avgScore", queryInt(jt, """
                SELECT COALESCE(ROUND(AVG(ea.score), 0), 0)
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                WHERE e.created_by = ? AND e.deleted = 0 AND ea.score IS NOT NULL AND ea.status IN (2,4,5)
                """, user.getId()));

        // 成绩分布
        data.put("scoreDistribution", jt.queryForList("""
                SELECT CASE
                    WHEN ea.score >= 90 THEN '90-100'
                    WHEN ea.score >= 80 THEN '80-89'
                    WHEN ea.score >= 70 THEN '70-79'
                    WHEN ea.score >= 60 THEN '60-69'
                    ELSE '60以下'
                END AS name, COUNT(*) AS value
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                WHERE e.created_by = ? AND e.deleted = 0 AND ea.submit_time IS NOT NULL
                GROUP BY name
                ORDER BY name
                """, user.getId()));

        // 近期考试
        data.put("recentExams", jt.queryForList("""
                SELECT e.id, e.exam_name AS name, e.start_time AS time, e.end_time AS endTime,
                       CASE
                         WHEN e.end_time IS NOT NULL AND e.end_time < NOW() THEN 2
                         WHEN e.start_time IS NOT NULL AND e.start_time <= NOW()
                              AND (e.end_time IS NULL OR e.end_time >= NOW()) THEN 1
                         ELSE 0
                       END AS phase,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id) AS attemptCount,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status IN (2,4,5)) AS submittedCount
                FROM exam e
                WHERE e.deleted = 0 AND e.created_by = ?
                ORDER BY e.start_time DESC LIMIT 6
                """, user.getId()));

        return data;
    }

    public Map<String, Object> studentOverview(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();

        // 即将开始/进行中的考试
        data.put("upcomingExams", queryInt(jt,
                """
                SELECT COUNT(*)
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                WHERE ea.user_id = ? AND e.deleted = 0 AND ea.status = 0
                  AND (e.end_time IS NULL OR e.end_time >= NOW())
                """,
                user.getId()));
        data.put("activeExams", queryInt(jt,
                "SELECT COUNT(*) FROM exam_attempt WHERE user_id = ? AND status = 1",
                user.getId()));
        // 已完成考试
        data.put("finishedExams", queryInt(jt,
                "SELECT COUNT(*) FROM exam_attempt WHERE user_id = ? AND status IN (2,4,5)", user.getId()));
        // 错题数
        data.put("wrongQuestions", queryInt(jt,
                "SELECT COUNT(*) FROM wrong_question_book WHERE user_id = ?", user.getId()));
        data.put("avgScore", queryInt(jt,
                "SELECT COALESCE(ROUND(AVG(score), 0), 0) FROM exam_attempt WHERE user_id = ? AND score IS NOT NULL AND status IN (2,4,5)",
                user.getId()));
        data.put("bestScore", queryInt(jt,
                "SELECT COALESCE(ROUND(MAX(score), 0), 0) FROM exam_attempt WHERE user_id = ? AND score IS NOT NULL AND status IN (2,4,5)",
                user.getId()));

        // 成绩趋势
        data.put("scoreTrend", jt.queryForList("""
                SELECT DATE(ea.submit_time) AS date, ea.score AS score, e.exam_name AS examName
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                WHERE ea.user_id = ? AND ea.submit_time IS NOT NULL
                ORDER BY ea.submit_time DESC LIMIT 10
                """, user.getId()));

        data.put("recentExams", jt.queryForList("""
                SELECT ea.id AS attemptId, e.exam_name AS name, e.start_time AS time, e.end_time AS endTime,
                       ea.attempt_no AS attemptNo, e.max_attempts AS maxAttempts, ea.status,
                       CASE
                         WHEN e.end_time IS NOT NULL AND e.end_time < NOW() THEN 2
                         WHEN e.start_time IS NOT NULL AND e.start_time <= NOW()
                              AND (e.end_time IS NULL OR e.end_time >= NOW()) THEN 1
                         ELSE 0
                       END AS phase
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                WHERE ea.user_id = ? AND e.deleted = 0 AND ea.status < 2
                ORDER BY e.start_time ASC, ea.attempt_no ASC
                LIMIT 5
                """, user.getId()));

        // 知识点掌握度
        // 知识点掌握度（基于答题记录，wrong_answer 表不存在，改用 answer_record 关联 exam_attempt 取 user）
        data.put("knowledgePoints", jt.queryForList("""
                SELECT kp.point_name AS name,
                       ROUND(AVG(CASE WHEN ar.is_correct = 1 THEN 100 ELSE 30 END), 0) AS mastery
                FROM answer_record ar
                JOIN exam_attempt ea ON ea.id = ar.attempt_id
                JOIN question q ON q.id = ar.question_id
                JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                WHERE ea.user_id = ?
                GROUP BY kp.id, kp.point_name
                ORDER BY mastery ASC LIMIT 8
                """, user.getId()));

        return data;
    }

    private int queryInt(JdbcTemplate jt, String sql, Object... args) {
        Long val = jt.queryForObject(sql, (rs, rowNum) -> rs.getLong(1), args);
        return val == null ? 0 : val.intValue();
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jt = jdbcTemplateProvider.getIfAvailable();
        if (jt == null) {
            throw new DatabaseUnavailableException("数据库连接不可用");
        }
        return jt;
    }
}
