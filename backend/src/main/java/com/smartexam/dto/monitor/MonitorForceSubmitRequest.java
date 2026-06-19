package com.smartexam.dto.monitor;

import jakarta.validation.constraints.Size;

public class MonitorForceSubmitRequest {

    @Size(max = 1000, message = "Note cannot exceed 1000 characters")
    private String note;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
