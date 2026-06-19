package com.smartexam.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectTeacherReviewRequest {

    @NotBlank(message = "Reject reason is required")
    @Size(max = 500, message = "Reject reason must be 500 characters or less")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
