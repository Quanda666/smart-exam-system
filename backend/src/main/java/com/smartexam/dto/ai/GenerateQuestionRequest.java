package com.smartexam.dto.ai;

import jakarta.validation.constraints.NotBlank;

public class GenerateQuestionRequest {
    @NotBlank
    private String subject;
    private String knowledgePoint;
    @NotBlank
    private String questionType;
    private String difficulty;
    private int count = 1;

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getKnowledgePoint() { return knowledgePoint; }
    public void setKnowledgePoint(String knowledgePoint) { this.knowledgePoint = knowledgePoint; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
