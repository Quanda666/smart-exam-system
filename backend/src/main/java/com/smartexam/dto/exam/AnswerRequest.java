package com.smartexam.dto.exam;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class AnswerRequest {

    @NotNull(message = "答案不能为空")
    private Map<Long, String> answers;

    public Map<Long, String> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<Long, String> answers) {
        this.answers = answers;
    }
}
