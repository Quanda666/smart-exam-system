package com.smartexam.dto.paper;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class GenerateRuleRequest {

    private Long knowledgePointId;

    @NotBlank(message = "题型不能为空")
    @Size(max = 32, message = "题型长度不能超过32个字符")
    private String questionType;

    @Size(max = 32, message = "难度长度不能超过32个字符")
    private String difficulty;

    @NotNull(message = "抽题数量不能为空")
    @Min(value = 1, message = "抽题数量必须大于0")
    private Integer count = 1;

    @NotNull(message = "每题分值不能为空")
    @DecimalMin(value = "0.1", message = "每题分值必须大于0")
    private BigDecimal score = BigDecimal.valueOf(5);

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(Long knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
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
        this.difficulty = difficulty;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count == null ? 1 : count;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score == null ? BigDecimal.valueOf(5) : score;
    }
}
