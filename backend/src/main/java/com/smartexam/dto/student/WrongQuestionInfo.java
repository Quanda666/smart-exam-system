package com.smartexam.dto.student;

import java.util.List;
import java.util.Map;

public class WrongQuestionInfo {
    private Long questionId;
    private Long examId;
    private String stem;
    private String questionType;
    private String correctAnswer;
    private String analysis;
    private int wrongCount;
    private String lastWrongTime;
    private List<Map<String, Object>> options;


    public WrongQuestionInfo(Long questionId, Long examId, String stem, String questionType, String correctAnswer, String analysis, int wrongCount, String lastWrongTime, List<Map<String,Object>> options) {
        this.questionId = questionId;
        this.examId = examId;
        this.stem = stem;
        this.questionType = questionType;
        this.correctAnswer = correctAnswer;
        this.analysis = analysis;
        this.wrongCount = wrongCount;
        this.lastWrongTime = lastWrongTime;
        this.options = options;
    }

    public List<Map<String, Object>> getOptions() {
        return options;
    }

    public void setOptions(List<Map<String, Object>> options) {
        this.options = options;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Long getExamId() {
        return examId;
    }

    public void setExamId(Long examId) {
        this.examId = examId;
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

    public int getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(int wrongCount) {
        this.wrongCount = wrongCount;
    }

    public String getLastWrongTime() {
        return lastWrongTime;
    }

    public void setLastWrongTime(String lastWrongTime) {
        this.lastWrongTime = lastWrongTime;
    }
}
