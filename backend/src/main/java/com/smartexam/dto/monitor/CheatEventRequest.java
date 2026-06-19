package com.smartexam.dto.monitor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CheatEventRequest {

    @NotNull(message = "考试记录ID不能为空")
    @Positive(message = "attemptId must be positive")
    private Long attemptId;

    @NotBlank(message = "事件类型不能为空")
    @Size(max = 64, message = "事件类型长度不能超过64个字符")
    private String eventType;

    @Size(max = 1000, message = "事件附加信息长度不能超过1000个字符")
    private String extraInfo;

    @NotBlank(message = "客户端事件ID不能为空")
    @Size(max = 80, message = "客户端事件ID长度不能超过80个字符")
    private String clientEventId;

    @NotBlank(message = "clientEventTime is required for monitor event reporting")
    @Size(max = 64, message = "客户端事件时间长度不能超过64个字符")
    private String clientEventTime;

    public Long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Long attemptId) {
        this.attemptId = attemptId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
    }

    public String getClientEventId() {
        return clientEventId;
    }

    public void setClientEventId(String clientEventId) {
        this.clientEventId = clientEventId;
    }

    public String getClientEventTime() {
        return clientEventTime;
    }

    public void setClientEventTime(String clientEventTime) {
        this.clientEventTime = clientEventTime;
    }
}
