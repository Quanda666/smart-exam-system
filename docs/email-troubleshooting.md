# 邮件验证码发送问题排查指南

## 当前问题：502 错误

502 Bad Gateway 错误表示后端服务超时或崩溃。可能原因：

### 1. SMTP 连接超时（最可能）

即使添加了超时配置，10秒超时后抛异常，如果异常处理不当仍会导致 502。

### 2. Railway 环境变量配置错误

检查以下环境变量是否正确：

```bash
railway variables
```

必须包含：
- `SPRING_MAIL_HOST=smtp.qq.com`
- `SPRING_MAIL_PORT=465`
- `SPRING_MAIL_USERNAME=完整邮箱地址@qq.com`
- `SPRING_MAIL_PASSWORD=16位授权码（无空格）`
- `SPRING_MAIL_PROTOCOL=smtps`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false`

### 3. 查看实时日志

**安装 Railway CLI**：
```bash
npm install -g @railway/cli
railway login
railway link
```

**查看日志**：
```bash
railway logs
```

**或通过 Railway 网页端查看**：
1. 访问 https://railway.app/dashboard
2. 选择你的项目
3. 点击 backend service
4. 查看 Deployments → 最新部署 → Logs

### 4. 关键日志关键词

修复后的代码会输出以下日志，请在 Railway 日志中搜索：

**成功发送**：
```
准备发送验证码邮件至 xxx@qq.com (SMTP: smtp.qq.com:465)
开始连接 SMTP 服务器并发送邮件...
验证码邮件已发送至 xxx@qq.com
```

**配置缺失**：
```
邮件服务未配置，验证码已生成但无法发送
```

**连接失败**（典型错误）：
```
邮件发送失败: Could not connect to SMTP host
邮件发送失败: Connection timed out
邮件发送失败: Authentication failed
```

### 5. 常见错误及解决方案

#### 错误 1：`Authentication failed`
**原因**：密码不是授权码，或用户名不是完整邮箱
**解决**：
1. 登录 https://mail.qq.com
2. 设置 → 账户 → 开启 IMAP/SMTP 服务
3. 生成授权码（16位，形如 `abcdefghijklmnop`）
4. 在 Railway 设置 `SPRING_MAIL_PASSWORD=授权码（去掉空格）`

#### 错误 2：`Connection timed out`
**原因**：Railway 服务器网络无法访问 smtp.qq.com
**解决**：
1. 尝试使用其他邮箱服务（Gmail、Outlook、SendGrid）
2. 或使用专业邮件服务（SendGrid、Mailgun、阿里云邮件推送）

#### 错误 3：`邮件服务未配置`
**原因**：环境变量未设置或被覆盖
**解决**：
```bash
railway variables set SPRING_MAIL_HOST=smtp.qq.com
railway variables set SPRING_MAIL_PORT=465
railway variables set SPRING_MAIL_USERNAME=your@qq.com
railway variables set SPRING_MAIL_PASSWORD=your_auth_code
railway variables set SPRING_MAIL_PROTOCOL=smtps
railway variables set SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
railway variables set SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
railway variables set SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

### 6. 启用详细调试日志

如果上述日志不够详细，临时启用 JavaMail 调试：

```bash
railway variables set SPRING_MAIL_PROPERTIES_MAIL_DEBUG=true
railway variables set APP_LOG_LEVEL=DEBUG
```

重新部署后会看到完整的 SMTP 会话日志。

**警告**：调试日志会暴露邮箱密码，排查完成后立即关闭：
```bash
railway variables set SPRING_MAIL_PROPERTIES_MAIL_DEBUG=false
railway variables set APP_LOG_LEVEL=INFO
```

### 7. 临时降级方案

如果邮件功能一直无法工作，可以先关闭邮件功能，让系统正常运行：

在 Railway 中删除所有 `SPRING_MAIL_*` 环境变量，系统会降级到"日志记录验证码"模式。

此时验证码会只在后端日志中输出，用户无法收到邮件，但不会导致 502 错误。

### 8. 替代方案：使用 SendGrid（推荐）

如果 QQ 邮箱一直有问题，建议使用 SendGrid（每月免费 100 封邮件）：

1. 注册 https://sendgrid.com/
2. 创建 API Key
3. 在 Railway 设置：
```bash
SPRING_MAIL_HOST=smtp.sendgrid.net
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=apikey
SPRING_MAIL_PASSWORD=your_sendgrid_api_key
SPRING_MAIL_PROTOCOL=smtp
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

---

## 立即排查步骤

1. **查看 Railway 日志**（网页端或 CLI）
2. **复制错误信息**（特别是 "邮件发送失败:" 后面的内容）
3. **检查环境变量**（`railway variables` 或网页端查看）
4. **提供以下信息**：
   - Railway 日志中的错误信息
   - 环境变量截图（密码部分打码）
   - QQ 邮箱是否已开启 SMTP 服务
