package com.smartexam.dto.ai;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class GenerateQuestionBatchRequest {

    @NotNull(message = "科目ID不能为空")
    private Long subjectId;

    @NotBlank(message = "科目名称不能为空")
    @Size(max = 128, message = "科目名称长度不能超过128个字符")
    private String subjectName;

    private Long knowledgePointId;

    @Size(max = 128, message = "知识点名称长度不能超过128个字符")
    private String knowledgePointName;

    @NotBlank(message = "题型不能为空")
    @Size(max = 32, message = "题型长度不能超过32个字符")
    private String questionType;

    @NotBlank(message = "难度不能为空")
    @Size(max = 32, message = "难度长度不能超过32个字符")
    private String difficulty = "MEDIUM";

    @Min(value = 1, message = "至少生成1道题")
    @Max(value = 10, message = "单次最多生成10道题")
    private Integer count = 3;

    @DecimalMin(value = "0.1", message = "默认分值必须大于0")
    private BigDecimal defaultScore = BigDecimal.valueOf(5);

    @Size(max = 1000, message = "补充要求长度不能超过1000个字符")
    private String requirements;

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(Long knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public String getKnowledgePointName() {
        return knowledgePointName;
    }

    public void setKnowledgePointName(String knowledgePointName) {
        this.knowledgePointName = knowledgePointName;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty == null || difficulty.isBlank() ? "MEDIUM" : difficulty;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count == null ? 3 : count;
    }

    public BigDecimal getDefaultScore() {
        return defaultScore;
    }

    public void setDefaultScore(BigDecimal defaultScore) {
        this.defaultScore = defaultScore == null ? BigDecimal.valueOf(5) : defaultScore;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }
}
