package com.smartexam.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
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
        try {
            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(getProperty("spring.mail.username"));
            helper.setTo(to);
            helper.setSubject("【广理考试中心】邮箱验证码");
            helper.setText(buildEmailBody(code), true);
            mailSender.send(message);
            log.info("验证码邮件已发送至 {}", to);
            return true;
        } catch (Exception e) {
            log.error("邮件发送失败: {}", e.getMessage());
            return false;
        }
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
        sender.setPort(Integer.parseInt(getProperty("spring.mail.port")));
        sender.setUsername(getProperty("spring.mail.username"));
        sender.setPassword(getProperty("spring.mail.password"));
        sender.setProtocol(getProperty("spring.mail.protocol"));
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", getProperty("spring.mail.properties.mail.smtp.ssl.enable"));
        props.put("mail.smtp.starttls.enable", getProperty("spring.mail.properties.mail.smtp.starttls.enable"));
        return sender;
    }

    private String getProperty(String key) {
        return env.getProperty(key, "");
    }
}
