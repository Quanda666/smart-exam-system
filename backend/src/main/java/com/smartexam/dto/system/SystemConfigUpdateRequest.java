package com.smartexam.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SystemConfigUpdateRequest {

    @NotBlank(message = "配置值不能为空")
    @Size(max = 1000, message = "配置值不能超过1000字符")
    private String configValue;

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }
}
