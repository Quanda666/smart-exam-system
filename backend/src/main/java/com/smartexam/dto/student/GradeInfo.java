package com.smartexam.dto.student;

import java.math.BigDecimal;

public class GradeInfo {

    // Getters and setters
    private Long attemptId;
    private String examName;
    private String subjectName;
    private BigDecimal score;
    private String submitTime;
    private Integer status;
    private Boolean scoreVisible;
    private String scoreVisibility;
    private Integer scoreReleaseStatus;
    private String scorePublishedAt;
    private String scoreRevokedAt;
    private String scoreRevokeReason;
    private Boolean appealOpen;
    private String appealDeadlineAt;
    private Integer appealWindowDays;
    private Integer questionCount;
    private Integer answeredCount;
    private Integer unansweredCount;

    public GradeInfo(Long attemptId, String examName, String subjectName, BigDecimal score, String submitTime, Integer status) {
        this(attemptId, examName, subjectName, score, submitTime, status,
                score != null, score != null ? "RELEASED" : "PENDING_RELEASE",
                null, null, null, null, false, null, null, null, null, null);
    }

    public GradeInfo(Long attemptId, String examName, String subjectName, BigDecimal score, String submitTime, Integer status,
                     Boolean scoreVisible, String scoreVisibility, Integer scoreReleaseStatus,
                     String scorePublishedAt, String scoreRevokedAt, String scoreRevokeReason,
                     Boolean appealOpen, String appealDeadlineAt, Integer appealWindowDays,
                     Integer questionCount, Integer answeredCount, Integer unansweredCount) {
        this.attemptId = attemptId;
        this.examName = examName;
        this.subjectName = subjectName;
        this.score = score;
        this.submitTime = submitTime;
        this.status = status;
        this.scoreVisible = scoreVisible;
        this.scoreVisibility = scoreVisibility;
        this.scoreReleaseStatus = scoreReleaseStatus;
        this.scorePublishedAt = scorePublishedAt;
        this.scoreRevokedAt = scoreRevokedAt;
        this.scoreRevokeReason = scoreRevokeReason;
        this.appealOpen = appealOpen;
        this.appealDeadlineAt = appealDeadlineAt;
        this.appealWindowDays = appealWindowDays;
        this.questionCount = questionCount;
        this.answeredCount = answeredCount;
        this.unansweredCount = unansweredCount;
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

    public Boolean getScoreVisible() {
        return scoreVisible;
    }

    public void setScoreVisible(Boolean scoreVisible) {
        this.scoreVisible = scoreVisible;
    }

    public String getScoreVisibility() {
        return scoreVisibility;
    }

    public void setScoreVisibility(String scoreVisibility) {
        this.scoreVisibility = scoreVisibility;
    }

    public Integer getScoreReleaseStatus() {
        return scoreReleaseStatus;
    }

    public void setScoreReleaseStatus(Integer scoreReleaseStatus) {
        this.scoreReleaseStatus = scoreReleaseStatus;
    }

    public String getScorePublishedAt() {
        return scorePublishedAt;
    }

    public void setScorePublishedAt(String scorePublishedAt) {
        this.scorePublishedAt = scorePublishedAt;
    }

    public String getScoreRevokedAt() {
        return scoreRevokedAt;
    }

    public void setScoreRevokedAt(String scoreRevokedAt) {
        this.scoreRevokedAt = scoreRevokedAt;
    }

    public String getScoreRevokeReason() {
        return scoreRevokeReason;
    }

    public void setScoreRevokeReason(String scoreRevokeReason) {
        this.scoreRevokeReason = scoreRevokeReason;
    }

    public Boolean getAppealOpen() {
        return appealOpen;
    }

    public void setAppealOpen(Boolean appealOpen) {
        this.appealOpen = appealOpen;
    }

    public String getAppealDeadlineAt() {
        return appealDeadlineAt;
    }

    public void setAppealDeadlineAt(String appealDeadlineAt) {
        this.appealDeadlineAt = appealDeadlineAt;
    }

    public Integer getAppealWindowDays() {
        return appealWindowDays;
    }

    public void setAppealWindowDays(Integer appealWindowDays) {
        this.appealWindowDays = appealWindowDays;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public Integer getAnsweredCount() {
        return answeredCount;
    }

    public void setAnsweredCount(Integer answeredCount) {
        this.answeredCount = answeredCount;
    }

    public Integer getUnansweredCount() {
        return unansweredCount;
    }

    public void setUnansweredCount(Integer unansweredCount) {
        this.unansweredCount = unansweredCount;
    }
}
