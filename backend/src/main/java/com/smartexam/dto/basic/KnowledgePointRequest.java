package com.smartexam.dto.basic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class KnowledgePointRequest {

    @NotNull(message = "科目ID不能为空")
    private Long subjectId;

    private Long parentId;

    @NotBlank(message = "知识点名称不能为空")
    @Size(max = 128, message = "知识点名称长度不能超过128个字符")
    private String pointName;

    private Integer sortOrder = 0;

    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status = 1;

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getPointName() {
        return pointName;
    }

    public void setPointName(String pointName) {
        this.pointName = pointName;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status == null ? 1 : status;
    }
}
