# 邮件服务配置指南

本文档详细介绍 Smart Exam System 的邮件服务配置方法。邮件服务是**可选功能**，主要用于提升用户体验和账号安全性。

## 目录

- [功能概述](#功能概述)
- [配置方式](#配置方式)
- [推荐邮箱服务商](#推荐邮箱服务商)
- [故障排查](#故障排查)
- [安全建议](#安全建议)

---

## 功能概述

### 邮件服务提供的功能

启用邮件服务后，用户可以：

1. **邮箱验证码登录**
   - 忘记密码时的备用登录方式
   - 仅限已绑定邮箱的用户使用
   - 验证码 5 分钟有效

2. **邮箱绑定/更换**
   - 在账号中心绑定邮箱
   - 更换已绑定的邮箱
   - 提升账号安全性

### 安全机制

系统内置以下安全限制：

- ✅ **频率限制**：每个邮箱每天最多发送 5 次验证码
- ✅ **冷却时间**：60 秒内不可重复发送
- ✅ **有效期**：验证码 5 分钟后自动失效
- ✅ **一次性使用**：验证码验证后立即失效

### 降级策略

如果未配置邮件服务：

- ❌ 登录页的「验证码登录」功能不可用
- ❌ 账号中心的「绑定/更换邮箱」功能不可用
- ✅ 密码登录、注册、考试等核心功能**完全正常**
- ✅ 系统会优雅降级，不会报错

---

## 配置方式

邮件服务通过环境变量配置，支持任何标准 SMTP 服务。

### 必需的环境变量

```bash
# SMTP 服务器地址
SPRING_MAIL_HOST=smtp.example.com

# SMTP 端口
SPRING_MAIL_PORT=465

# 发件邮箱
SPRING_MAIL_USERNAME=your_email@example.com

# 邮箱授权码（不是登录密码！）
SPRING_MAIL_PASSWORD=your_authorization_code

# 协议（smtp 或 smtps）
SPRING_MAIL_PROTOCOL=smtps

# 启用认证
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true

# SSL 配置
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

### 配置位置

**本地开发**：
- 编辑 `backend/.env` 文件
- 或在 IDE 中配置环境变量

**生产部署**：
- Railway：在 Variables 面板添加
- Render：在 Environment 面板添加
- Docker：在 `docker-compose.yml` 的 `environment` 区块添加

---

## 推荐邮箱服务商

### 1. QQ 邮箱（推荐）

**优点**：
- ✅ 免费且稳定
- ✅ 国内访问速度快
- ✅ 每日发送限额充足（500 封/天）
- ✅ 无需域名验证

**配置示例**：

```bash
SPRING_MAIL_HOST=smtp.qq.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=your_qq_email@qq.com
SPRING_MAIL_PASSWORD=abcdefghijklmnop
SPRING_MAIL_PROTOCOL=smtps
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

**获取授权码步骤**：

1. 登录 QQ 邮箱网页版：https://mail.qq.com
2. 点击右上角「设置」→「账户」
3. 向下滚动找到「POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV服务」
4. 开启「IMAP/SMTP服务」（如果已开启则跳过）
5. 点击「生成授权码」
6. 通过手机 QQ 扫码验证
7. 获得 16 位授权码（如 `abcd efgh ijkl mnop`）
8. 将授权码**去除空格**后填入 `SPRING_MAIL_PASSWORD`

**注意事项**：
- 授权码不是 QQ 密码，是专用的应用密码
- 授权码只显示一次，请妥善保存
- 如果忘记授权码，重新生成即可（旧授权码失效）

---

### 2. 163 邮箱

**优点**：
- ✅ 免费且稳定
- ✅ 国内访问速度快
- ✅ 每日发送限额充足（200 封/天）

**配置示例**：

```bash
SPRING_MAIL_HOST=smtp.163.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=your_email@163.com
SPRING_MAIL_PASSWORD=your_authorization_code
SPRING_MAIL_PROTOCOL=smtps
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

**获取授权码步骤**：

1. 登录 163 邮箱网页版：https://mail.163.com
2. 点击「设置」→「POP3/SMTP/IMAP」
3. 开启「IMAP/SMTP服务」
4. 设置授权码（需要短信验证）
5. 将授权码填入 `SPRING_MAIL_PASSWORD`

---

### 3. Gmail（海外服务器）

**优点**：
- ✅ 国际通用
- ✅ 稳定性好
- ✅ 每日发送限额高（500 封/天）

**缺点**：
- ❌ 需要海外服务器（国内服务器无法连接）
- ❌ 需要配置应用专用密码

**配置示例**：

```bash
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com
SPRING_MAIL_PASSWORD=your_app_password
SPRING_MAIL_PROTOCOL=smtp
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

**获取应用密码步骤**：

1. 登录 Google 账户：https://myaccount.google.com/
2. 进入「安全性」→「两步验证」（需先开启）
3. 向下滚动找到「应用专用密码」
4. 选择「邮件」和「其他设备」
5. 生成 16 位应用密码
6. 将密码（去除空格）填入 `SPRING_MAIL_PASSWORD`

---

### 4. 自定义企业邮箱

如果使用企业邮箱（如腾讯企业邮、阿里云企业邮），请咨询邮箱服务商获取 SMTP 配置。

**通用配置模板**：

```bash
SPRING_MAIL_HOST=smtp.your-domain.com
SPRING_MAIL_PORT=465  # 或 587，取决于服务商
SPRING_MAIL_USERNAME=noreply@your-domain.com
SPRING_MAIL_PASSWORD=your_password_or_token
SPRING_MAIL_PROTOCOL=smtps
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
```

---

## 故障排查

### 1. 验证码无法发送

**现象**：点击「发送验证码」后无响应或提示发送失败

**排查步骤**：

1. **检查环境变量是否配置**
   ```bash
   # 在服务器日志中查找
   grep "SPRING_MAIL_HOST" 
   ```

2. **检查授权码是否正确**
   - 确认使用的是授权码，不是登录密码
   - 确认授权码未过期（重新生成测试）

3. **检查 SMTP 服务器连通性**
   ```bash
   # 测试端口连通性
   telnet smtp.qq.com 465
   # 或使用 nc（如果 telnet 不可用）
   nc -zv smtp.qq.com 465
   ```

4. **检查日志**
   - Railway：查看 Deployments → Logs
   - 本地：查看 `backend/logs/application.log`
   - 关键词：`MailException`, `SMTPException`, `Authentication failed`

### 2. 提示「频率限制」

**现象**：提示"该邮箱今日发送次数已达上限"或"请60秒后再试"

**原因**：触发了系统内置的安全限制

**解决方案**：
- 等待冷却时间结束（60 秒）
- 如果是管理员测试，可以直接清空数据库表 `email_verification`
- 每日限额为 5 次，次日零点自动重置

### 3. 验证码收不到

**现象**：提示发送成功，但邮箱收不到验证码

**排查步骤**：

1. **检查垃圾邮件箱**
   - 验证码邮件可能被标记为垃圾邮件
   - 将发件人添加到白名单

2. **检查邮箱地址是否正确**
   - 确认输入的邮箱地址无误
   - 注意区分 `.com` 和 `.cn`

3. **检查发件邮箱状态**
   - 登录发件邮箱（`SPRING_MAIL_USERNAME`）
   - 查看是否有异常提示（如被限制发送）

4. **检查邮件服务商限额**
   - QQ 邮箱：500 封/天
   - 163 邮箱：200 封/天
   - 超出限额需等待次日

### 4. SSL/TLS 连接错误

**现象**：日志中出现 `SSLHandshakeException` 或 `CertificateException`

**解决方案**：

```bash
# 尝试切换端口
SPRING_MAIL_PORT=587  # 使用 STARTTLS

# 调整 SSL 配置
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

---

## 安全建议

### 1. 使用专用发件邮箱

- ✅ 不要使用个人主力邮箱
- ✅ 注册一个专门用于发送验证码的邮箱
- ✅ 邮箱命名建议：`noreply@your-domain.com` 或 `system@your-domain.com`

### 2. 保护授权码安全

- ✅ 授权码通过环境变量配置，不要写入代码
- ✅ 不要将 `.env` 文件提交到 Git 仓库
- ✅ 定期更换授权码（建议每季度一次）
- ✅ 不同环境使用不同的邮箱（开发/生产隔离）

### 3. 监控发送频率

- ✅ 定期检查 `email_verification` 表的记录
- ✅ 如果发现异常高频发送，检查是否有恶意攻击
- ✅ 可以根据实际情况调整频率限制（修改 `AuthService.java`）

### 4. 设置合理的发件人信息

当前系统使用硬编码的发件人名称"广理考试中心"，如需自定义：

编辑 `backend/src/main/java/com/smartexam/service/EmailService.java`：

```java
private void sendEmail(String to, String subject, String content) throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    
    helper.setFrom(from, "你的系统名称");  // 修改这里
    helper.setTo(to);
    helper.setSubject(subject);
    helper.setText(content, true);
    
    mailSender.send(message);
}
```

---

## 验证配置是否生效

### 方法 1：检查系统日志

启动应用后，查看日志中是否有邮件服务初始化信息：

```
[INFO] EmailService - Mail service initialized with host: smtp.qq.com
```

### 方法 2：发送测试验证码

1. 访问登录页
2. 切换到「验证码登录」Tab
3. 输入一个已绑定邮箱的账号
4. 点击「发送验证码」
5. 检查邮箱是否收到验证码

### 方法 3：查看数据库记录

```sql
-- 查看最近发送的验证码记录
SELECT * FROM email_verification 
ORDER BY created_at DESC 
LIMIT 10;
```

如果配置正确，发送验证码后会在此表中看到新记录。

---

## 邮件模板自定义

当前系统使用纯文本邮件模板，如需自定义：

编辑 `backend/src/main/java/com/smartexam/service/EmailService.java`：

```java
public void sendVerificationCode(String to, String code) {
    String subject = "【广理考试中心】验证码";
    String content = String.format(
        """
        您好！
        
        您的验证码是：%s
        
        此验证码5分钟内有效，请勿泄露给他人。
        
        如果这不是您本人的操作，请忽略此邮件。
        
        ---
        广理考试中心
        https://your-domain.com
        """,
        code
    );
    
    try {
        sendEmail(to, subject, content);
        log.info("✅ Verification code sent to: {}", to);
    } catch (Exception e) {
        log.error("❌ Failed to send verification code to: {}", to, e);
        throw new RuntimeException("验证码发送失败，请稍后重试");
    }
}
```

如需使用 HTML 模板，修改 `sendEmail` 方法中的 `helper.setText(content, true)`（第二个参数 `true` 表示启用 HTML）。

---

## 相关文档

- [Railway 部署指南](./deploy-railway.md)
- [Render 部署指南](./deploy-render.md)
- [部署故障排查](./troubleshooting.md)

---

## 常见问题

**Q：不配置邮件服务会影响系统运行吗？**  
A：不会。邮件服务是可选功能，不配置时系统会自动降级，核心功能完全正常。

**Q：可以使用个人 Gmail 邮箱吗？**  
A：可以，但需要服务器能访问 Google 服务（国内服务器不行）。

**Q：每天能发送多少验证码？**  
A：系统限制每个邮箱每天最多接收 5 次验证码。邮箱服务商的发送限额取决于具体服务商。

**Q：验证码有效期可以调整吗？**  
A：可以。编辑 `AuthService.java`，修改 `VERIFICATION_CODE_EXPIRE_MINUTES` 常量。

**Q：可以使用其他邮件服务商吗？**  
A：可以。只要支持标准 SMTP 协议的邮件服务都可以使用。

---

如有其他问题，请参考 [部署故障排查文档](./troubleshooting.md) 或提交 GitHub Issue。
