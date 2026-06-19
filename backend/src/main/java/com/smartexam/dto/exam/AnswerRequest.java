package com.smartexam.dto.exam;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;

public class AnswerRequest {

    @NotNull(message = "答案不能为空")
    @Size(max = 1000, message = "answers cannot contain more than 1000 entries")
    private Map<@Positive(message = "questionId must be positive") Long,
            @Size(max = 20000, message = "answer content must be at most 20000 characters") String> answers;
    @Size(max = 80, message = "submitToken must be at most 80 characters")
    private String submitToken;

    public Map<@Positive(message = "questionId must be positive") Long,
            @Size(max = 20000, message = "answer content must be at most 20000 characters") String> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<@Positive(message = "questionId must be positive") Long,
            @Size(max = 20000, message = "answer content must be at most 20000 characters") String> answers) {
        this.answers = answers;
    }

    public String getSubmitToken() {
        return submitToken;
    }

    public void setSubmitToken(String submitToken) {
        this.submitToken = submitToken;
    }
}
