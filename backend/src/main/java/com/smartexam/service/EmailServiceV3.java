package com.smartexam.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 邮件发送服务 V3 - 使用 Resend HTTP API
 * 通过 HTTPS 发送邮件，绕过 Railway 的 SMTP 端口封锁
 */
@Service
public class EmailServiceV3 {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceV3.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final org.springframework.core.env.Environment env;
    private final WebClient webClient;

    public EmailServiceV3(org.springframework.core.env.Environment env) {
        this.env = env;
        this.webClient = WebClient.builder()
                .baseUrl(RESEND_API_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 同步发送验证码邮件（HTTP API）
     */
    public boolean sendVerificationCode(String to, String code) {
        String apiKey = getProperty("resend.api.key");
        String fromEmail = getProperty("resend.from.email");

        if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
            log.warn("Resend 未配置，验证码已生成但无法发送: {} -> {}", to, code);
            return false;
        }

        try {
            log.info("准备通过 Resend HTTP API 发送验证码至: {}", to);

            String requestBody = String.format("""
                {
                  "from": "%s",
                  "to": ["%s"],
                  "subject": "【广理考试中心】邮箱验证码",
                  "html": "%s"
                }
                """,
                    fromEmail,
                    to,
                    buildEmailBody(code).replace("\n", "\\n").replace("\"", "\\\"")
            );

            String response = webClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("验证码邮件已通过 Resend 发送至: {}, 响应: {}", to, response);
            return true;

        } catch (Exception e) {
            log.error("Resend API 调用失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 异步发送验证码邮件
     */
    @Async
    public void sendVerificationCodeAsync(String to, String code) {
        log.info("【异步任务】开始通过 Resend API 发送邮件，线程: {}", Thread.currentThread().getName());
        boolean result = sendVerificationCode(to, code);
        log.info("【异步任务】邮件发送结果: {}, 目标: {}", result ? "成功" : "失败", to);
    }

    private String buildEmailBody(String code) {
        return """
                <div style="max-width:480px;margin:0 auto;padding:24px;font-family:Arial,sans-serif;">
                  <div style="text-align:center;margin-bottom:24px;">
                    <img src="https://img.110995.xyz/file/logo/iBhVRiau.png" alt="广理考试中心" style="height:48px;width:auto;"/>
                  </div>
                  <h2 style="color:#1677FF;text-align:center;">广理考试中心</h2>
                  <p>您的验证码是：</p>
                  <div style="background:#f5f7fa;padding:20px;text-align:center;border-radius:8px;margin:16px 0;">
                    <span style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#303133;">{code}</span>
                  </div>
                  <p style="color:#909399;font-size:13px;">验证码 5 分钟内有效，请勿泄露给他人。</p>
                  <hr style="border:none;border-top:1px solid #eee;margin:24px 0;"/>
                  <p style="color:#c0c4cc;font-size:12px;">此邮件由系统自动发送，请勿回复。</p>
                </div>
                """.replace("{code}", code);
    }

    public boolean isConfigured() {
        String apiKey = getProperty("resend.api.key");
        String fromEmail = getProperty("resend.from.email");
        return apiKey != null && !apiKey.isEmpty() && fromEmail != null && !fromEmail.isEmpty();
    }

    private String getProperty(String key) {
        return getProperty(key, "");
    }

    private String getProperty(String key, String defaultValue) {
        return env.getProperty(key, defaultValue);
    }
}
