package com.smartexam.dto.basic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TeachingAssignmentRequest {

    @NotNull(message = "Teacher is required")
    private Long teacherUserId;

    @NotNull(message = "Class course is required")
    private Long classCourseId;

    @NotBlank(message = "Teacher role is required")
    @Size(max = 32, message = "Teacher role must be 32 characters or less")
    private String teacherRole = "LECTURER";

    public Long getTeacherUserId() {
        return teacherUserId;
    }

    public void setTeacherUserId(Long teacherUserId) {
        this.teacherUserId = teacherUserId;
    }

    public Long getClassCourseId() {
        return classCourseId;
    }

    public void setClassCourseId(Long classCourseId) {
        this.classCourseId = classCourseId;
    }

    public String getTeacherRole() {
        return teacherRole;
    }

    public void setTeacherRole(String teacherRole) {
        this.teacherRole = teacherRole == null || teacherRole.isBlank() ? "LECTURER" : teacherRole;
    }
}
