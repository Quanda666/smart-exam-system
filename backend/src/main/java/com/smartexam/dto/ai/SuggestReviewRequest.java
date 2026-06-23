package com.smartexam.dto.ai;

import jakarta.validation.constraints.NotBlank;

public class SuggestReviewRequest {
    @NotBlank
    private String question;
    @NotBlank
    private String studentAnswer;
    private String correctAnswer;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getStudentAnswer() { return studentAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
}
