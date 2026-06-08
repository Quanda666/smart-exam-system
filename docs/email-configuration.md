# 邮件系统配置指南

本系统支持邮箱验证码登录和邮箱绑定功能，通过标准 SMTP 协议发送验证码邮件。

## 功能概览

配置邮件服务后，用户可以：
- **邮箱验证码登录**：忘记密码时，通过已绑定的邮箱验证码登录
- **邮箱绑定/更换**：在账号中心绑定或更换邮箱
- **提升账号安全性**：双因素认证，减少密码泄露风险

**重要提示**：邮件服务为**可选配置**，不配置不影响系统核心功能（考试、评分、统计等）。

---

## 配置方式

### 方法一：使用 QQ 邮箱（推荐）

QQ 邮箱 SMTP 服务免费、稳定，适合个人和小型项目。

#### 1. 获取 QQ 邮箱授权码

1. 登录 [QQ 邮箱网页版](https://mail.qq.com)
2. 进入「设置」→「账户」
3. 找到「POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV服务」
4. 开启「IMAP/SMTP服务」或「POP3/SMTP服务」
5. 按提示完成身份验证（短信验证码）
6. 生成授权码（16位，如 `abcd efgh ijkl mnop`）
7. **妥善保存授权码**，关闭页面后无法再次查看

#### 2. 配置环境变量

```bash
# QQ 邮箱 SMTP 配置
SPRING_MAIL_HOST=smtp.qq.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=your_qq_email@qq.com          # 你的 QQ 邮箱地址
SPRING_MAIL_PASSWORD=abcdefghijklmnop             # 授权码（去除空格）
SPRING_MAIL_PROTOCOL=smtps
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

**注意事项**：
- `SPRING_MAIL_USERNAME` 填写完整的 QQ 邮箱地址（如 `123456789@qq.com`）
- `SPRING_MAIL_PASSWORD` 填写授权码，**不是 QQ 密码**
- 授权码中的空格需要去除
- 端口 465 使用 SSL 加密，安全可靠

---

### 方法二：使用 163 邮箱

网易 163 邮箱同样提供免费 SMTP 服务。

#### 1. 获取 163 邮箱授权码

1. 登录 [163 邮箱](https://mail.163.com)
2. 进入「设置」→「POP3/SMTP/IMAP」
3. 开启「IMAP/SMTP服务」
4. 设置授权密码（即授权码）

#### 2. 配置环境变量

```bash
# 163 邮箱 SMTP 配置
SPRING_MAIL_HOST=smtp.163.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=your_email@163.com           # 你的 163 邮箱地址
SPRING_MAIL_PASSWORD=your_authorization_code      # 授权码
SPRING_MAIL_PROTOCOL=smtps
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

---

### 方法三：使用 Gmail（需海外服务器）

Gmail 适合部署在海外服务器的项目。

#### 1. 获取 Gmail 应用专用密码

1. 登录 [Google 账号](https://myaccount.google.com)
2. 进入「安全性」→「两步验证」（需先开启）
3. 在「应用专用密码」中生成新密码
4. 选择应用类型为「邮件」，设备类型为「其他」

#### 2. 配置环境变量

```bash
# Gmail SMTP 配置
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com         # 你的 Gmail 地址
SPRING_MAIL_PASSWORD=your_app_password            # 应用专用密码
SPRING_MAIL_PROTOCOL=smtp
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false
```

**注意事项**：
- Gmail 使用 STARTTLS（端口 587），而非 SSL（端口 465）
- 必须开启两步验证才能生成应用专用密码
- 国内服务器可能无法访问 Gmail SMTP

---

### 方法四：使用企业邮箱

企业邮箱（如腾讯企业邮、阿里企业邮）适合正式生产环境。

#### 腾讯企业邮箱

```bash
SPRING_MAIL_HOST=smtp.exmail.qq.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=your_email@your_domain.com
SPRING_MAIL_PASSWORD=your_password_or_auth_code
SPRING_MAIL_PROTOCOL=smtps
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

#### 阿里企业邮箱

```bash
SPRING_MAIL_HOST=smtp.mxhichina.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=your_email@your_domain.com
SPRING_MAIL_PASSWORD=your_password
SPRING_MAIL_PROTOCOL=smtps
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

---

## 配置验证

### 启动时检查日志

配置邮件服务后，启动应用时查看日志：

```bash
# 查看应用日志
docker logs smart-exam-backend

# 或查看 Railway 部署日志
```

成功配置的日志示例：

```
2025-01-15 10:30:45.123  INFO --- [main] o.s.m.j.JavaMailSenderImpl : JavaMail version: 1.6.2
2025-01-15 10:30:45.456  INFO --- [main] c.g.e.config.EmailConfig : 邮件服务已启用：smtp.qq.com:465
```

未配置时的日志（系统正常降级）：

```
2025-01-15 10:30:45.123  WARN --- [main] c.g.e.config.EmailConfig : 邮件服务未配置，验证码功能将降级（生成但不发送）
```

### 功能测试

1. **测试发送验证码**：
   - 登录系统，进入「账号中心」→「绑定/更换邮箱」
   - 输入邮箱，点击「发送验证码」
   - 检查邮箱是否收到验证码邮件

2. **测试验证码登录**：
   - 在登录页切换到「验证码登录」Tab
   - 输入已绑定的邮箱，发送验证码
   - 输入验证码并登录

---

## 验证码规则

| 规则项 | 说明 |
|--------|------|
| **验证码位数** | 6 位数字 |
| **有效期** | 5 分钟 |
| **每日发送限制** | 每邮箱最多 5 次/天 |
| **重复发送间隔** | 60 秒 |
| **验证次数** | 一次性使用，验证后立即失效 |

---

## 降级行为

当邮件服务**未配置**或**配置错误**时，系统自动降级：

| 功能 | 降级行为 |
|------|----------|
| 验证码生成 | 正常生成，记录到日志 |
| 验证码发送 | **不发送邮件**，仅记录警告日志 |
| 登录页「验证码登录」 | Tab 保留，但发送按钮点击后提示"邮件服务未配置" |
| 账号中心「绑定邮箱」 | 功能保留，但发送按钮点击后提示"邮件服务未配置" |
| 其他功能 | **不受影响**，考试、评分、统计等核心功能正常 |

**降级日志示例**：

```
2025-01-15 12:30:00.123  WARN --- [http-nio-8080-exec-5] c.g.e.service.EmailService : 邮件服务未配置，验证码生成但未发送：123456
```

---

## 常见问题

### 1. 验证码发送失败

**问题**：点击「发送验证码」后提示"发送失败"

**排查步骤**：
1. 检查环境变量是否正确配置
2. 检查授权码是否正确（常见错误：使用邮箱密码而非授权码）
3. 检查服务器是否能访问 SMTP 服务器（防火墙/网络限制）
4. 查看应用日志，搜索关键词 `JavaMailSender` 或 `EmailService`

**常见错误日志**：

```bash
# 授权码错误
javax.mail.AuthenticationFailedException: 535 Login Fail. Please enter your authorization code to login.

# 网络连接失败
javax.mail.MessagingException: Could not connect to SMTP host: smtp.qq.com, port: 465
```

### 2. QQ 邮箱授权码无效

**原因**：QQ 邮箱授权码生成后只显示一次，关闭页面后无法查看

**解决**：
1. 登录 QQ 邮箱，进入「设置」→「账户」
2. 关闭「IMAP/SMTP服务」，再重新开启
3. 重新生成授权码并保存

### 3. 163 邮箱发送频率限制

**问题**：短时间内发送大量邮件被限制

**解决**：
- 163 邮箱免费版有发送频率限制（约 50 封/小时）
- 建议使用 QQ 邮箱或企业邮箱
- 或配置验证码有效期和频率限制

### 4. Gmail 连接超时

**问题**：国内服务器无法连接 Gmail SMTP

**解决**：
- Gmail 需要海外服务器或代理
- 国内项目建议使用 QQ 邮箱或 163 邮箱

### 5. Docker 容器内无法发送邮件

**问题**：本地测试正常，Docker 部署后无法发送

**排查**：
1. 检查 Docker 容器网络配置
2. 确认环境变量已传入容器（`docker exec <container> env | grep MAIL`）
3. 检查容器是否能访问外网（`docker exec <container> ping smtp.qq.com`）

---

## 安全建议

1. **授权码保护**：
   - 授权码等同于密码，切勿泄露或提交到版本控制系统
   - 使用环境变量管理，避免硬编码到配置文件

2. **使用专用邮箱**：
   - 建议使用独立邮箱作为系统发件邮箱
   - 不要使用个人主要邮箱

3. **限制发送频率**：
   - 系统已内置频率限制（60秒/次，5次/天）
   - 避免被邮件服务商标记为垃圾邮件

4. **HTTPS 传输**：
   - 生产环境建议启用 HTTPS
   - 保护用户邮箱地址和验证码传输安全

---

## 生产环境建议

1. **使用企业邮箱**：
   - 更高的发送配额和稳定性
   - 更专业的发件人域名（如 `noreply@your-domain.com`）

2. **配置 SPF/DKIM**：
   - 减少邮件被判定为垃圾邮件的概率
   - 提升邮件送达率

3. **监控邮件发送**：
   - 定期检查邮件服务状态
   - 记录发送失败日志，及时发现问题

4. **备用邮件服务**：
   - 配置备用 SMTP 服务
   - 主服务不可用时自动切换

---

## 相关文档

- [Railway 部署指南](./deploy-railway.md) - Railway 平台邮件配置
- [Docker 部署指南](./deploy-docker.md) - Docker 环境邮件配置
- [系统管理员手册](./admin-guide.md) - 邮件功能管理

---

**最后更新**：2025-01-15  
**维护者**：广理考试中心开发团队
