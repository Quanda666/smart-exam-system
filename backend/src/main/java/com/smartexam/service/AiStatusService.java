package com.smartexam.service;

import com.smartexam.config.AiProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AiStatusService {

    private final AiProperties aiProperties;

    public AiStatusService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public Map<String, Object> getStatus() {
        boolean apiKeyConfigured = StringUtils.hasText(aiProperties.getApiKey());
        boolean mockEnabled = aiProperties.isMockEnabled();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseUrl", aiProperties.getBaseUrl());
        data.put("model", aiProperties.getModel());
        data.put("mockEnabled", mockEnabled);
        data.put("apiKeyConfigured", apiKeyConfigured);
        data.put("apiKeyMasked", apiKeyConfigured ? maskKey(aiProperties.getApiKey()) : "未配置");
        data.put("timeoutSeconds", aiProperties.getTimeoutSeconds());
        data.put("available", mockEnabled || apiKeyConfigured);
        data.put("mode", resolveMode(mockEnabled, apiKeyConfigured));
        data.put("notice", "AI 模块为可选辅助能力，阶段 1 仅提供配置状态检查，不影响核心考试流程。未返回明文 API Key。");
        return data;
    }

    private String resolveMode(boolean mockEnabled, boolean apiKeyConfigured) {
        if (mockEnabled) {
            return "MOCK";
        }
        if (apiKeyConfigured) {
            return "REMOTE";
        }
        return "DISABLED";
    }

    private String maskKey(String apiKey) {
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
