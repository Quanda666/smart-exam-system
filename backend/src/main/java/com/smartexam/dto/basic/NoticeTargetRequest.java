package com.smartexam.dto.basic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoticeTargetRequest {

    @NotBlank(message = "Target type is required")
    @Size(max = 32, message = "Target type must be 32 characters or less")
    private String targetType;

    private Long targetId = 0L;

    @Size(max = 64, message = "Target code must be 64 characters or less")
    private String targetCode = "";

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId == null ? 0L : targetId;
    }

    public String getTargetCode() {
        return targetCode;
    }

    public void setTargetCode(String targetCode) {
        this.targetCode = targetCode == null ? "" : targetCode;
    }
}
