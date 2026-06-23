package com.smartexam.dto.exam;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExamUpdateRequest {

    @NotBlank(message = "Exam name is required")
    @Size(max = 128, message = "Exam name must be 128 characters or less")
    private String examName;

    @Size(max = 512, message = "Description must be 512 characters or less")
    private String description;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be greater than 0")
    private Integer durationMinutes;

    @NotNull(message = "Max attempts is required")
    @Min(value = 1, message = "Max attempts must be greater than 0")
    private Integer maxAttempts = 1;

    @DecimalMin(value = "0.0", message = "Pass score must be greater than or equal to 0")
    private BigDecimal passScore;

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public BigDecimal getPassScore() {
        return passScore;
    }

    public void setPassScore(BigDecimal passScore) {
        this.passScore = passScore;
    }
}
