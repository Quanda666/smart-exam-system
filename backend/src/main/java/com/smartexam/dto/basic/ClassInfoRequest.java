package com.smartexam.dto.basic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ClassInfoRequest {

    @NotBlank(message = "Class name is required")
    @Size(max = 128, message = "Class name must be 128 characters or less")
    private String className;

    @Size(max = 64, message = "Class code must be 64 characters or less")
    private String classCode;

    @Pattern(regexp = "^(MAJOR|ELECTIVE|TEMPORARY)$", message = "Class type must be MAJOR, ELECTIVE, or TEMPORARY")
    private String classType = "MAJOR";

    @Size(max = 128, message = "Major must be 128 characters or less")
    private String major;

    @Size(max = 32, message = "Grade must be 32 characters or less")
    private String grade;

    @Min(value = 0, message = "Status must be 0 or 1")
    @Max(value = 1, message = "Status must be 0 or 1")
    private Integer status = 1;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassCode() {
        return classCode;
    }

    public void setClassCode(String classCode) {
        this.classCode = classCode;
    }

    public String getClassType() {
        return classType;
    }

    public void setClassType(String classType) {
        this.classType = classType == null || classType.isBlank() ? "MAJOR" : classType;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status == null ? 1 : status;
    }
}
