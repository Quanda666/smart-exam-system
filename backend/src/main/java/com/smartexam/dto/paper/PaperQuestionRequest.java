package com.smartexam.dto.paper;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class PaperQuestionRequest {

    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    @NotNull(message = "题目分值不能为空")
    @DecimalMin(value = "0.1", message = "题目分值必须大于0")
    private BigDecimal score = BigDecimal.valueOf(5);

    private Integer sortOrder = 0;

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score == null ? BigDecimal.valueOf(5) : score;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }
}
