package com.smartexam.dto.exam;

import jakarta.validation.constraints.Size;

public class ScoreRevokeRequest {

    @Size(max = 500, message = "Score revoke reason must be 500 characters or less")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
