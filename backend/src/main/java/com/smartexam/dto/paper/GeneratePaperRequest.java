package com.smartexam.dto.paper;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class GeneratePaperRequest {

    @NotNull(message = "科目ID不能为空")
    private Long subjectId;

    @NotBlank(message = "试卷名称不能为空")
    @Size(max = 128, message = "试卷名称长度不能超过128个字符")
    private String paperName;

    @Size(max = 512, message = "试卷说明长度不能超过512个字符")
    private String description;

    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status = 0;

    @Valid
    @NotEmpty(message = "组卷规则不能为空")
    private List<GenerateRuleRequest> rules = new ArrayList<>();

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getPaperName() {
        return paperName;
    }

    public void setPaperName(String paperName) {
        this.paperName = paperName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status == null ? 0 : status;
    }

    public List<GenerateRuleRequest> getRules() {
        return rules;
    }

    public void setRules(List<GenerateRuleRequest> rules) {
        this.rules = rules == null ? new ArrayList<>() : rules;
    }
}
