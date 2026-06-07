package com.smartexam.service;

import com.smartexam.common.CsvExport;
import com.smartexam.common.ExportFile;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 把成绩/名单/学情数据导出为 CSV（UTF-8 BOM，Excel 可直接打开）。
 * 复用现有查询口径，不引入额外依赖，适配云端小内存部署。
 */
@Service
public class ExportService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public ExportService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /** 某场考试的成绩单：按得分从高到低排名，含学号/姓名/班级/得分/满分/交卷时间。教师仅能导出本人创建的考试。 */
    public ExportFile examScoreSheet(Long examId, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> examRows = jt.queryForList(
                "SELECT exam_name, created_by FROM exam WHERE id = ? AND deleted = 0", examId);
        if (examRows.isEmpty()) {
            throw new IllegalArgumentException("考试不存在");
        }
        if (!user.hasRole("ADMIN")) {
            Object createdBy = examRows.get(0).get("created_by");
            if (createdBy == null || !createdBy.toString().equals(String.valueOf(user.getId()))) {
                throw new IllegalArgumentException("只能导出本人创建的考试成绩单");
            }
        }
        String examName = String.valueOf(examRows.get(0).get("exam_name"));

        List<Map<String, Object>> records = jt.queryForList("""
                SELECT u.real_name AS realName, u.username, sp.student_no AS studentNo,
                       c.class_name AS className, ea.score, p.total_score AS totalScore,
                       ea.submit_time AS submitTime
                FROM exam_attempt ea
                JOIN sys_user u ON u.id = ea.user_id AND u.deleted = 0
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.class_id
                JOIN exam e ON e.id = ea.exam_id
                JOIN paper p ON p.id = e.paper_id
                WHERE ea.exam_id = ? AND ea.status = 5
                ORDER BY ea.score DESC, ea.submit_time
                """, examId);

        List<String> headers = List.of("排名", "学号", "姓名", "班级", "得分", "满分", "交卷时间");
        List<List<Object>> rows = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> r : records) {
            rows.add(Arrays.asList(rank++, r.get("studentNo"), r.get("realName"), r.get("className"),
                    r.get("score"), r.get("totalScore"), r.get("submitTime")));
        }
        return new ExportFile(safeName(examName) + "-成绩单.csv", CsvExport.build(headers, rows));
    }

    /** 某班级学生名单：学号/姓名/账号/已完成考试数/平均分（与学情分析列表口径一致）。 */
    public ExportFile classRoster(Long classId) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> classRows = jt.queryForList(
                "SELECT class_name FROM edu_class WHERE id = ?", classId);
        String className = classRows.isEmpty() ? ("班级" + classId) : String.valueOf(classRows.get(0).get("class_name"));

        List<Map<String, Object>> students = jt.queryForList("""
                SELECT sp.student_no AS studentNo, u.real_name AS realName, u.username,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.user_id = u.id AND ea.status = 5) AS completedCount,
                       (SELECT COALESCE(ROUND(AVG(ea.score), 2), 0) FROM exam_attempt ea WHERE ea.user_id = u.id AND ea.status = 5) AS avgScore
                FROM student_profile sp
                JOIN sys_user u ON u.id = sp.user_id AND u.deleted = 0
                WHERE sp.class_id = ? AND sp.deleted = 0
                ORDER BY sp.student_no, u.id
                """, classId);

        List<String> headers = List.of("学号", "姓名", "账号", "已完成考试", "平均分");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> s : students) {
            rows.add(Arrays.asList(s.get("studentNo"), s.get("realName"), s.get("username"),
                    s.get("completedCount"), s.get("avgScore")));
        }
        return new ExportFile(safeName(className) + "-学生名单.csv", CsvExport.build(headers, rows));
    }

    /** 单个学生的历次已完成考试成绩：考试/科目/得分/满分/交卷时间（按时间正序）。 */
    public ExportFile studentScores(Long userId) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> profile = jt.queryForList(
                "SELECT real_name FROM sys_user WHERE id = ? AND deleted = 0", userId);
        if (profile.isEmpty()) {
            throw new IllegalArgumentException("学生不存在");
        }
        String realName = String.valueOf(profile.get(0).get("real_name"));

        List<Map<String, Object>> exams = jt.queryForList("""
                SELECT e.exam_name AS examName, s.subject_name AS subjectName, ea.score,
                       p.total_score AS totalScore, ea.submit_time AS submitTime
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE ea.user_id = ? AND ea.status = 5
                ORDER BY ea.submit_time
                """, userId);

        List<String> headers = List.of("考试", "科目", "得分", "满分", "交卷时间");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> e : exams) {
            rows.add(Arrays.asList(e.get("examName"), e.get("subjectName"), e.get("score"),
                    e.get("totalScore"), e.get("submitTime")));
        }
        return new ExportFile(safeName(realName) + "-成绩历史.csv", CsvExport.build(headers, rows));
    }

    /** 去除文件名里 Windows/类 Unix 都禁用的字符，避免下载落盘失败。 */
    private static String safeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "导出";
        }
        return raw.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }
}
