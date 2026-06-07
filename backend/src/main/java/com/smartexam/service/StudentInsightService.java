package com.smartexam.service;

import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师学情分析：从班级下钻到单个学生的成绩历史与趋势，补齐教师以学生为中心的工作流。
 */
@Service
public class StudentInsightService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public StudentInsightService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /** 列出某班级的学生，附带其已完成考试数与平均分。 */
    public List<Map<String, Object>> listClassStudents(Long classId) {
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForList("""
                SELECT u.id AS userId, u.username, u.real_name AS realName, sp.student_no AS studentNo,
                       u.status, c.class_name AS className,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.user_id = u.id AND ea.status = 5) AS completedCount,
                       (SELECT COALESCE(ROUND(AVG(ea.score), 2), 0) FROM exam_attempt ea WHERE ea.user_id = u.id AND ea.status = 5) AS avgScore
                FROM student_profile sp
                JOIN sys_user u ON u.id = sp.user_id AND u.deleted = 0
                JOIN edu_class c ON c.id = sp.class_id
                WHERE sp.class_id = ? AND sp.deleted = 0
                ORDER BY sp.student_no, u.id
                """, classId);
    }

    /** 单个学生的档案 + 历次已完成考试成绩（按时间，供趋势图）+ 汇总统计。 */
    public Map<String, Object> studentInsight(Long userId) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();

        List<Map<String, Object>> profile = jt.queryForList("""
                SELECT u.real_name AS realName, u.username, sp.student_no AS studentNo, c.class_name AS className
                FROM sys_user u
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.class_id
                WHERE u.id = ? AND u.deleted = 0
                """, userId);
        if (profile.isEmpty()) {
            throw new IllegalArgumentException("学生不存在");
        }
        data.put("student", profile.get(0));

        data.put("exams", jt.queryForList("""
                SELECT e.exam_name AS examName, s.subject_name AS subjectName, ea.score,
                       p.total_score AS totalScore, ea.submit_time AS submitTime
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE ea.user_id = ? AND ea.status = 5
                ORDER BY ea.submit_time
                """, userId));

        data.put("summary", jt.queryForMap("""
                SELECT COUNT(*) AS count,
                       COALESCE(ROUND(AVG(score), 2), 0) AS avgScore,
                       COALESCE(MAX(score), 0) AS maxScore,
                       COALESCE(MIN(score), 0) AS minScore
                FROM exam_attempt WHERE user_id = ? AND status = 5 AND score IS NOT NULL
                """, userId));

        return data;
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }
}
