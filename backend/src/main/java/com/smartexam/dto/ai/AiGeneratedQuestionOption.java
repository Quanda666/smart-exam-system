package com.smartexam.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AiGeneratedQuestionOption {

    @NotBlank(message = "选项标识不能为空")
    @Size(max = 16, message = "选项标识长度不能超过16个字符")
    private String optionLabel;

    @NotBlank(message = "选项内容不能为空")
    @Size(max = 1000, message = "选项内容长度不能超过1000个字符")
    private String optionContent;

    private Boolean correct = false;

    public String getOptionLabel() {
        return optionLabel;
    }

    public void setOptionLabel(String optionLabel) {
        this.optionLabel = optionLabel;
    }

    public String getOptionContent() {
        return optionContent;
    }

    public void setOptionContent(String optionContent) {
        this.optionContent = optionContent;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct == null ? false : correct;
    }
}
