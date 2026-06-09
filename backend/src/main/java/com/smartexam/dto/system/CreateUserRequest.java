package com.smartexam.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 64, message = "Username must be 3 to 64 characters")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 64, message = "Password must be 6 to 64 characters")
    private String password;

    @NotBlank(message = "Real name is required")
    @Size(max = 64, message = "Real name must be 64 characters or less")
    private String realName;

    @NotBlank(message = "Role type is required")
    @Pattern(regexp = "^(ADMIN|TEACHER|STUDENT)$", message = "Invalid role type")
    private String roleType;

    @Size(max = 64, message = "Student number must be 64 characters or less")
    private String studentNo;

    private Long classId;

    private List<Long> electiveClassIds = new ArrayList<>();

    @Size(max = 32, message = "Enrollment year must be 32 characters or less")
    private String enrollmentYear;

    @Size(max = 128, message = "College must be 128 characters or less")
    private String college;

    @Size(max = 128, message = "Major must be 128 characters or less")
    private String major;

    @Size(max = 64, message = "Teacher number must be 64 characters or less")
    private String teacherNo;

    private LocalDate hireDate;

    @Size(max = 64, message = "Title must be 64 characters or less")
    private String title;

    @Size(max = 1000, message = "Introduction must be 1000 characters or less")
    private String introduction;

    @Size(max = 32, message = "Phone must be 32 characters or less")
    private String phone;

    @Size(max = 128, message = "Email must be 128 characters or less")
    private String email;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public List<Long> getElectiveClassIds() { return electiveClassIds; }
    public void setElectiveClassIds(List<Long> electiveClassIds) { this.electiveClassIds = electiveClassIds == null ? new ArrayList<>() : electiveClassIds; }
    public String getEnrollmentYear() { return enrollmentYear; }
    public void setEnrollmentYear(String enrollmentYear) { this.enrollmentYear = enrollmentYear; }
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    public String getTeacherNo() { return teacherNo; }
    public void setTeacherNo(String teacherNo) { this.teacherNo = teacherNo; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
