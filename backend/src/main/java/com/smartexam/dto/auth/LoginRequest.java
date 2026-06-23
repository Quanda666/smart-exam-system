package com.smartexam.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    @Size(max = 64, message = "账号长度不能超过64个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过128个字符")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
