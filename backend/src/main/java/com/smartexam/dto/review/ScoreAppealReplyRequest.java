package com.smartexam.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ScoreAppealReplyRequest {

    @NotBlank(message = "处理意见不能为空")
    @Size(max = 1000, message = "处理意见不能超过1000字")
    private String reply;

    @NotBlank(message = "处理结果不能为空")
    @Pattern(regexp = "MAINTAINED|RECHECK_REQUIRED|ADJUSTED_OFFLINE", message = "处理结果不合法")
    private String handlingResult;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getHandlingResult() {
        return handlingResult;
    }

    public void setHandlingResult(String handlingResult) {
        this.handlingResult = handlingResult;
    }
}
