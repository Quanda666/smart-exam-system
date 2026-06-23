package com.smartexam.dto.basic;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CourseRequest {

    @NotBlank(message = "Course code is required")
    @Size(max = 64, message = "Course code must be 64 characters or less")
    private String courseCode;

    @NotBlank(message = "Course name is required")
    @Size(max = 128, message = "Course name must be 128 characters or less")
    private String courseName;

    private Long subjectId;

    @DecimalMax(value = "99.0", message = "Credit must be 99 or less")
    @DecimalMin(value = "0.0", message = "Credit cannot be negative")
    private BigDecimal credit;

    @Size(max = 500, message = "Description must be 500 characters or less")
    private String description;

    @Min(value = 0, message = "Status must be 0 or 1")
    @Max(value = 1, message = "Status must be 0 or 1")
    private Integer status = 1;

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = credit; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status == null ? 1 : status; }
}
