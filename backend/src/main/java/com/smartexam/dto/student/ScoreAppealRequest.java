package com.smartexam.dto.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ScoreAppealRequest {

    @NotNull(message = "答卷ID不能为空")
    @Positive(message = "attemptId must be positive")
    private Long attemptId;

    @Positive(message = "questionId must be positive")
    private Long questionId;

    @NotBlank(message = "申诉原因不能为空")
    @Size(max = 1000, message = "申诉原因不能超过1000字")
    private String reason;

    public Long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Long attemptId) {
        this.attemptId = attemptId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
