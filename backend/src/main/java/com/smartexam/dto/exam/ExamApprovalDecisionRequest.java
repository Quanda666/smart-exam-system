package com.smartexam.dto.exam;

import jakarta.validation.constraints.Size;

public class ExamApprovalDecisionRequest {

    @Size(max = 1000, message = "Approval note must be 1000 characters or less")
    private String note;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
