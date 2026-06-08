package com.smartexam.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 邮件发送服务，对接 QQ SMTP。
 * 配置从 application.yml 读取，通过 Environment 在运行时动态构建 JavaMailSender。
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final org.springframework.core.env.Environment env;

    public EmailService(org.springframework.core.env.Environment env) {
        this.env = env;
    }

    /**
     * 发送邮箱验证码。
     *
     * @param to   目标邮箱
     * @param code 6位验证码
     * @return true 发送成功
     */
    public boolean sendVerificationCode(String to, String code) {
        // 如果邮件配置缺失，降级为日志记录（避免启动失败）
        if (!isMailConfigured()) {
            log.warn("邮件服务未配置，验证码已生成但无法发送: {} -> {}", to, code);
            return false;
        }
        try {
            log.info("准备发送验证码邮件至 {} (SMTP: {}:{})", to,
                    getProperty("spring.mail.host"), getProperty("spring.mail.port"));

            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(getProperty("spring.mail.username"));
            helper.setTo(to);
            helper.setSubject("【广理考试中心】邮箱验证码");
            helper.setText(buildEmailBody(code), true);

            log.info("开始连接 SMTP 服务器并发送邮件...");
            mailSender.send(message);
            log.info("验证码邮件已发送至 {}", to);
            return true;
        } catch (Exception e) {
            log.error("邮件发送失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 异步发送邮箱验证码（不阻塞主线程）。
     * 用于验证码发送场景，避免 SMTP 超时导致接口 502。
     *
     * @param to   目标邮箱
     * @param code 6位验证码
     */
    @Async
    public void sendVerificationCodeAsync(String to, String code) {
        log.info("【异步任务】开始执行邮件发送，线程: {}", Thread.currentThread().getName());
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

    private JavaMailSender buildMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(getProperty("spring.mail.host"));
        sender.setPort(Integer.parseInt(getProperty("spring.mail.port", "465")));
        sender.setUsername(getProperty("spring.mail.username"));
        sender.setPassword(getProperty("spring.mail.password"));
        sender.setProtocol(getProperty("spring.mail.protocol", "smtps"));
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", getProperty("spring.mail.properties.mail.smtp.ssl.enable", "true"));
        props.put("mail.smtp.starttls.enable", getProperty("spring.mail.properties.mail.smtp.starttls.enable", "false"));

        // 关键修复：添加超时配置，防止无限等待
        props.put("mail.smtp.timeout", getProperty("spring.mail.properties.mail.smtp.timeout", "10000"));
        props.put("mail.smtp.connectiontimeout", getProperty("spring.mail.properties.mail.smtp.connectiontimeout", "10000"));
        props.put("mail.smtp.writetimeout", getProperty("spring.mail.properties.mail.smtp.writetimeout", "10000"));

        // 启用调试日志（生产环境可通过环境变量关闭）
        props.put("mail.debug", getProperty("spring.mail.properties.mail.debug", "false"));

        return sender;
    }

    private boolean isMailConfigured() {
        String host = getProperty("spring.mail.host");
        String username = getProperty("spring.mail.username");
        return host != null && !host.isEmpty() && username != null && !username.isEmpty();
    }

    /** 对外暴露：邮件服务是否已配置。 */
    public boolean isConfigured() {
        return isMailConfigured();
    }

    private String getProperty(String key) {
        return getProperty(key, "");
    }

    private String getProperty(String key, String defaultValue) {
        return env.getProperty(key, defaultValue);
    }
}
