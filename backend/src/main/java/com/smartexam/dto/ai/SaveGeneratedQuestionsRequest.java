package com.smartexam.dto.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class SaveGeneratedQuestionsRequest {

    @Valid
    @NotEmpty(message = "待保存题目不能为空")
    private List<AiGeneratedQuestion> questions = new ArrayList<>();

    public List<AiGeneratedQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<AiGeneratedQuestion> questions) {
        this.questions = questions == null ? new ArrayList<>() : questions;
    }
}
