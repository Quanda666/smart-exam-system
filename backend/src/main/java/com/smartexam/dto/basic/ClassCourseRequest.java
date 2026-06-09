package com.smartexam.dto.basic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ClassCourseRequest {

    @NotNull(message = "Class is required")
    private Long classId;

    @NotNull(message = "Course is required")
    private Long courseId;

    @NotBlank(message = "Term name is required")
    @Size(max = 64, message = "Term name must be 64 characters or less")
    private String termName = "Default Term";

    @Min(value = 0, message = "Status must be 0 or 1")
    @Max(value = 1, message = "Status must be 0 or 1")
    private Integer status = 1;

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTermName() { return termName; }
    public void setTermName(String termName) { this.termName = termName == null || termName.isBlank() ? "Default Term" : termName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status == null ? 1 : status; }
}
