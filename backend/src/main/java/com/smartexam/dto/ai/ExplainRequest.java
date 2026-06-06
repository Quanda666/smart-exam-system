package com.smartexam.dto.ai;

import jakarta.validation.constraints.NotBlank;

public class ExplainRequest {
    @NotBlank
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
