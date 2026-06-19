package com.smartexam.dto.monitor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MonitorActionRequest {

    @NotBlank(message = "Action type cannot be blank")
    @Size(max = 32, message = "Action type cannot exceed 32 characters")
    private String actionType;

    @Size(max = 1000, message = "Note cannot exceed 1000 characters")
    private String note;

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
