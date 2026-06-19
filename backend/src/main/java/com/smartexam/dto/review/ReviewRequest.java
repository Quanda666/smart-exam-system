package com.smartexam.dto.review;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class ReviewRequest {

    @NotNull(message = "答案记录ID不能为空")
    @Positive(message = "answerRecordId must be positive")
    private Long answerRecordId;

    @NotNull(message = "分数不能为空")
    @DecimalMin(value = "0.0", message = "分数不能为负")
    private BigDecimal score;

    @Size(max = 1000, message = "评语长度不能超过1000个字符")
    private String comment;

    public Long getAnswerRecordId() {
        return answerRecordId;
    }

    public void setAnswerRecordId(Long answerRecordId) {
        this.answerRecordId = answerRecordId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
