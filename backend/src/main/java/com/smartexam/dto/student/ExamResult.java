package com.smartexam.dto.student;

import java.util.List;
import java.util.Map;

public class ExamResult {

    private GradeInfo gradeInfo;
    private List<Map<String, Object>> answers;

    public ExamResult(GradeInfo gradeInfo, List<Map<String, Object>> answers) {
        this.gradeInfo = gradeInfo;
        this.answers = answers;
    }

    public GradeInfo getGradeInfo() {
        return gradeInfo;
    }

    public void setGradeInfo(GradeInfo gradeInfo) {
        this.gradeInfo = gradeInfo;
    }

    public List<Map<String, Object>> getAnswers() {
        return answers;
    }

    public void setAnswers(List<Map<String, Object>> answers) {
        this.answers = answers;
    }
}
