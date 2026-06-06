package com.smartexam.dto.student;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class GradeInfo {

    // Getters and setters
    private Long attemptId;
    private String examName;
    private String subjectName;
    private BigDecimal score;
    private String submitTime;
    private Integer status;

    public GradeInfo(Long attemptId, String examName, String subjectName, BigDecimal score, String submitTime, Integer status) {
        this.attemptId = attemptId;
        this.examName = examName;
        this.subjectName = subjectName;
        this.score = score;
        this.submitTime = submitTime;
        this.status = status;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Long attemptId) {
        this.attemptId = attemptId;
    }

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(String submitTime) {
        this.submitTime = submitTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
