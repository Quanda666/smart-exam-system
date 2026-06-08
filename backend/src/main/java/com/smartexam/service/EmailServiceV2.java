package com.smartexam.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 邮件发送服务 V2 - 使用 SendGrid HTTP API
 * 彻底绕过 Railway 的 SMTP 端口限制（465/587）
 */
@Service
public class EmailServiceV2 {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceV2.class);

    private final org.springframework.core.env.Environment env;

    public EmailServiceV2(org.springframework.core.env.Environment env) {
        this.env = env;
    }

    /**
     * 同步发送验证码邮件（HTTP API）
     */
    public boolean sendVerificationCode(String to, String code) {
        String apiKey = getProperty("sendgrid.api.key");
        String fromEmail = getProperty("sendgrid.from.email");
        String fromName = getProperty("sendgrid.from.name", "广理考试中心");

        if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
            log.warn("SendGrid 未配置，验证码已生成但无法发送: {} -> {}", to, code);
            return false;
        }

        try {
            log.info("准备通过 SendGrid HTTP API 发送验证码至: {}", to);

            Email from = new Email(fromEmail, fromName);
            Email toEmail = new Email(to);
            String subject = "【广理考试中心】邮箱验证码";
            Content content = new Content("text/html", buildEmailBody(code));

            Mail mail = new Mail(from, subject, toEmail, content);
            SendGrid sg = new SendGrid(apiKey);
            Request request = new Request();

            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);
            int statusCode = response.getStatusCode();

            if (statusCode >= 200 && statusCode < 300) {
                log.info("验证码邮件已通过 SendGrid 发送至: {}, HTTP 状态: {}", to, statusCode);
                return true;
            } else {
                log.error("SendGrid 发送失败，状态码: {}, 响应: {}", statusCode, response.getBody());
                return false;
            }

        } catch (IOException e) {
            log.error("SendGrid API 调用失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 异步发送验证码邮件
     */
    @Async
    public void sendVerificationCodeAsync(String to, String code) {
        log.info("【异步任务】开始通过 HTTP API 发送邮件，线程: {}", Thread.currentThread().getName());
        boolean result = sendVerificationCode(to, code);
        log.info("【异步任务】邮件发送结果: {}, 目标: {}", result ? "成功" : "失败", to);
    }

    private String buildEmailBody(String code) {
        return """
                <div style="max-width:480px;margin:0 auto;padding:24px;font-family:Arial,sans-serif;">
                  <h2 style="color:#1677FF;">广理考试中心</h2>
                  <p>您的验证码是：</p>
                  <div style="background:#f5f7fa;padding:20px;text-align:center;border-radius:8px;margin:16px 0;">
                    <span style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#303133;">%s</span>
                  </div>
                  <p style="color:#909399;font-size:13px;">验证码 5 分钟内有效，请勿泄露给他人。</p>
                  <hr style="border:none;border-top:1px solid #eee;margin:24px 0;"/>
                  <p style="color:#c0c4cc;font-size:12px;">此邮件由系统自动发送，请勿回复。</p>
                </div>
                """.formatted(code);
    }

    public boolean isConfigured() {
        String apiKey = getProperty("sendgrid.api.key");
        String fromEmail = getProperty("sendgrid.from.email");
        return apiKey != null && !apiKey.isEmpty() && fromEmail != null && !fromEmail.isEmpty();
    }

    private String getProperty(String key) {
        return getProperty(key, "");
    }

    private String getProperty(String key, String defaultValue) {
        return env.getProperty(key, defaultValue);
    }
}
