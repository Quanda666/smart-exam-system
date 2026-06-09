package com.smartexam.dto.basic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class StudentClassMembershipRequest {

    @NotNull(message = "Student is required")
    private Long studentUserId;

    @NotNull(message = "Class is required")
    private Long classId;

    @NotBlank(message = "Membership type is required")
    @Pattern(regexp = "^(PRIMARY|ELECTIVE|TEMPORARY)$", message = "Membership type must be PRIMARY, ELECTIVE, or TEMPORARY")
    private String membershipType = "ELECTIVE";

    @Size(max = 32, message = "Source must be 32 characters or less")
    private String source = "ADMIN";

    public Long getStudentUserId() {
        return studentUserId;
    }

    public void setStudentUserId(Long studentUserId) {
        this.studentUserId = studentUserId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public String getMembershipType() {
        return membershipType;
    }

    public void setMembershipType(String membershipType) {
        this.membershipType = membershipType == null || membershipType.isBlank() ? "ELECTIVE" : membershipType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source == null || source.isBlank() ? "ADMIN" : source;
    }
}
