package com.smartexam.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ScoreAppealRecheckCloseRequest {

    @NotBlank(message = "复核说明不能为空")
    @Size(max = 1000, message = "复核说明不能超过1000字")
    private String recheckNote;

    public String getRecheckNote() {
        return recheckNote;
    }

    public void setRecheckNote(String recheckNote) {
        this.recheckNote = recheckNote;
    }
}
