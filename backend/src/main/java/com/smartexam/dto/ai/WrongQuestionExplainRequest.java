package com.smartexam.dto.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class WrongQuestionExplainRequest {

    private Long questionId;

    @NotBlank(message = "题干不能为空")
    @Size(max = 4000, message = "题干长度不能超过4000个字符")
    private String stem;

    @Size(max = 32, message = "题型长度不能超过32个字符")
    private String questionType;

    @Size(max = 4000, message = "学生答案长度不能超过4000个字符")
    private String studentAnswer;

    @Size(max = 4000, message = "参考答案长度不能超过4000个字符")
    private String correctAnswer;

    @Size(max = 4000, message = "原解析长度不能超过4000个字符")
    private String analysis;

    private Integer wrongCount;

    @Valid
    private List<AiGeneratedQuestionOption> options = new ArrayList<>();

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getStem() {
        return stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public String getStudentAnswer() {
        return studentAnswer;
    }

    public void setStudentAnswer(String studentAnswer) {
        this.studentAnswer = studentAnswer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public Integer getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(Integer wrongCount) {
        this.wrongCount = wrongCount;
    }

    public List<AiGeneratedQuestionOption> getOptions() {
        return options;
    }

    public void setOptions(List<AiGeneratedQuestionOption> options) {
        this.options = options == null ? new ArrayList<>() : options;
    }
}
