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
        data.put("totalPapers", queryInt(jt, "SELECT COUNT(*) FROM paper WHERE deleted = 0"));

        // 教师学科分布
        data.put("teacherSubjects", jt.queryForList("""
                SELECT COALESCE(s.subject_name, '未分配') AS name, COUNT(tp.user_id) AS value
                FROM teacher_profile tp
                JOIN sys_user u ON u.id = tp.user_id AND u.deleted = 0
                LEFT JOIN edu_subject s ON s.id = tp.subject_id AND s.deleted = 0
                GROUP BY s.id, s.subject_name
                ORDER BY value DESC
                LIMIT 8
                """));

        // 学生年级分布
        data.put("studentGrades", jt.queryForList("""
                SELECT c.grade AS name, COUNT(sp.user_id) AS value
                FROM student_profile sp
                JOIN sys_user u ON u.id = sp.user_id AND u.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.class_id
                GROUP BY c.grade
                ORDER BY value DESC
                """));

        // 考试通过率趋势（最近7天）
        data.put("examTrend", jt.queryForList("""
                SELECT DATE(ea.submit_time) AS date,
                       COUNT(*) AS total,
                       SUM(CASE WHEN ea.total_score >= 60 THEN 1 ELSE 0 END) AS passed
                FROM exam_attempt ea
                WHERE ea.submit_time IS NOT NULL AND ea.submit_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                GROUP BY DATE(ea.submit_time)
                ORDER BY date
                """));

        return data;
    }

    public Map<String, Object> teacherOverview(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();

        // 我的考试任务数
        data.put("myExams", queryInt(jt, "SELECT COUNT(*) FROM exam WHERE deleted = 0 AND created_by = ?", user.getId()));
        // 待批阅试卷（status=1 表示已提交未批阅）
        data.put("pendingReviews", queryInt(jt,
                "SELECT COUNT(*) FROM exam_attempt WHERE status = 1 AND exam_id IN (SELECT id FROM exam WHERE created_by = ? AND deleted = 0)",
                user.getId()));
        // 我创建的试卷数
        data.put("myPapers", queryInt(jt, "SELECT COUNT(*) FROM paper WHERE deleted = 0 AND created_by = ?", user.getId()));

        // 成绩分布
        data.put("scoreDistribution", jt.queryForList("""
                SELECT CASE
                    WHEN ea.total_score >= 90 THEN '90-100'
                    WHEN ea.total_score >= 80 THEN '80-89'
                    WHEN ea.total_score >= 70 THEN '70-79'
                    WHEN ea.total_score >= 60 THEN '60-69'
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
                SELECT exam_name AS name, start_time AS time, status
                FROM exam WHERE deleted = 0 AND created_by = ?
                ORDER BY start_time DESC LIMIT 5
                """, user.getId()));

        return data;
    }

    public Map<String, Object> studentOverview(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();

        // 即将开始/进行中的考试
        data.put("upcomingExams", queryInt(jt,
                "SELECT COUNT(*) FROM exam_attempt ea JOIN exam e ON e.id = ea.exam_id WHERE ea.user_id = ? AND e.deleted = 0 AND ea.status = 0",
                user.getId()));
        // 已完成考试
        data.put("finishedExams", queryInt(jt,
                "SELECT COUNT(*) FROM exam_attempt WHERE user_id = ? AND status = 2", user.getId()));
        // 错题数
        data.put("wrongQuestions", queryInt(jt,
                "SELECT COUNT(*) FROM wrong_question WHERE user_id = ?", user.getId()));

        // 成绩趋势
        data.put("scoreTrend", jt.queryForList("""
                SELECT DATE(ea.submit_time) AS date, ea.total_score AS score, e.exam_name AS examName
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                WHERE ea.user_id = ? AND ea.submit_time IS NOT NULL
                ORDER BY ea.submit_time DESC LIMIT 10
                """, user.getId()));

        // 知识点掌握度
        data.put("knowledgePoints", jt.queryForList("""
                SELECT kp.point_name AS name,
                       ROUND(AVG(CASE WHEN wa.is_correct = 1 THEN 100 ELSE 30 END), 0) AS mastery
                FROM wrong_answer wa
                JOIN question q ON q.id = wa.question_id
                LEFT JOIN edu_knowledge_point kp ON kp.id = q.knowledge_point_id
                WHERE wa.user_id = ?
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
