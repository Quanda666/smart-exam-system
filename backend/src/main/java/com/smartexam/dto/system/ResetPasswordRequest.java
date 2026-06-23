package com.smartexam.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为6-64位")
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
