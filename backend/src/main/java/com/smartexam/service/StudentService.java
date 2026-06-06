package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.student.ExamResult;
import com.smartexam.dto.student.GradeInfo;
import com.smartexam.dto.student.WrongQuestionInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class StudentService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public StudentService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public List<GradeInfo> getGrades(AuthUser user) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        return jdbcTemplate.query("SELECT ea.id as attemptId, e.exam_name, s.subject_name, ea.score, ea.submit_time, ea.status FROM exam_attempt ea JOIN exam e ON ea.exam_id = e.id JOIN paper p ON e.paper_id = p.id JOIN edu_subject s ON p.subject_id = s.id WHERE ea.user_id = ? AND ea.status = 5 ORDER BY ea.submit_time DESC",
                (rs, rowNum) -> new GradeInfo(
                        rs.getLong("attemptId"),
                        rs.getString("exam_name"),
                        rs.getString("subject_name"),
                        rs.getBigDecimal("score"),
                        rs.getTimestamp("submit_time").toString(),
                        rs.getInt("status")
                ), user.getId());
    }

    public ExamResult getExamResult(Long attemptId, AuthUser user) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        GradeInfo gradeInfo = jdbcTemplate.queryForObject("SELECT ea.id as attemptId, e.exam_name, s.subject_name, ea.score, ea.submit_time, ea.status FROM exam_attempt ea JOIN exam e ON ea.exam_id = e.id JOIN paper p ON e.paper_id = p.id JOIN edu_subject s ON p.subject_id = s.id WHERE ea.id = ? AND ea.user_id = ?",
                (rs, rowNum) -> new GradeInfo(
                        rs.getLong("attemptId"),
                        rs.getString("exam_name"),
                        rs.getString("subject_name"),
                        rs.getBigDecimal("score"),
                        rs.getTimestamp("submit_time").toString(),
                        rs.getInt("status")
                ), attemptId, user.getId());

        List<Map<String, Object>> answers = jdbcTemplate.queryForList("SELECT q.stem, q.question_type, q.correct_answer, q.analysis, ar.answer_content, ar.score, ar.is_correct FROM answer_record ar JOIN question q ON ar.question_id = q.id WHERE ar.attempt_id = ?", attemptId);

        return new ExamResult(gradeInfo, answers);
    }
    
    public List<WrongQuestionInfo> getWrongQuestions(AuthUser user) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        return jdbcTemplate.query("""
            SELECT w.question_id, q.stem, q.question_type, q.correct_answer, q.analysis, w.wrong_count, w.last_wrong_time 
            FROM wrong_question_book w 
            JOIN question q ON w.question_id = q.id 
            WHERE w.user_id = ? 
            ORDER BY w.last_wrong_time DESC
            """, (rs, rowNum) -> new WrongQuestionInfo(
                        rs.getLong("question_id"),
                        rs.getString("stem"),
                        rs.getString("question_type"),
                        rs.getString("correct_answer"),
                        rs.getString("analysis"),
                        rs.getInt("wrong_count"),
                        rs.getTimestamp("last_wrong_time").toString(),
                        jdbcTemplate.queryForList("SELECT option_label, option_content, is_correct FROM question_option WHERE question_id = ? ORDER BY sort_order", rs.getLong("question_id"))
                ), user.getId());
    }

    public Map<String, Double> getKnowledgePointMastery(AuthUser user) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        return jdbcTemplate.query("""
            SELECT kp.point_name, AVG(ar.score / pq.score) as mastery 
            FROM answer_record ar 
            JOIN exam_attempt ea ON ar.attempt_id = ea.id 
            JOIN question q ON ar.question_id = q.id 
            JOIN paper_question pq ON q.id = pq.question_id AND ea.exam_id = pq.paper_id
            JOIN edu_knowledge_point kp ON q.knowledge_point_id = kp.id 
            WHERE ea.user_id = ? AND q.knowledge_point_id IS NOT NULL
            GROUP BY q.knowledge_point_id, kp.point_name
            """, (rs) -> {
            Map<String, Double> masteryMap = new java.util.HashMap<>();
            while (rs.next()) {
                masteryMap.put(rs.getString("point_name"), rs.getDouble("mastery"));
            }
            return masteryMap;
        }, user.getId());
    }
}
