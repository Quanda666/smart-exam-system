package com.smartexam.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateUserRequest {

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 64, message = "真实姓名不能超过 64 个字符")
    private String realName;

    @NotBlank(message = "角色类型不能为空")
    @Pattern(regexp = "^(ADMIN|TEACHER|STUDENT)$", message = "角色类型无效")
    private String roleType;

    @Size(max = 64, message = "学号不能超过 64 个字符")
    private String studentNo;

    private Long classId;

    @Size(max = 64, message = "工号不能超过 64 个字符")
    private String teacherNo;

    @Size(max = 64, message = "职称不能超过 64 个字符")
    private String title;

    @Size(max = 32, message = "手机号不能超过 32 个字符")
    private String phone;

    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;

    // getters and setters

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getTeacherNo() { return teacherNo; }
    public void setTeacherNo(String teacherNo) { this.teacherNo = teacherNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
