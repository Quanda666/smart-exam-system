# Railway 邮件发送修复方案

## 问题诊断

Railway 网络日志显示到 `smtp.qq.com:465` 的连接被丢弃：
```
dropCause: "ICMP_CSUM"
dstPort: 465
```

这是 Railway 平台网络策略导致的，与代码无关。

---

## 解决方案 1：改用 587 端口（推荐先尝试）

QQ 邮箱支持两种 SMTP 端口：
- **465**：SMTPS（隐式 SSL）← Railway 阻止
- **587**：SMTP + STARTTLS（显式 TLS）← 试试这个

### 修改 Railway 环境变量

```bash
SPRING_MAIL_PORT=587
SPRING_MAIL_PROTOCOL=smtp
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true
```

保持其他变量不变：
```bash
SPRING_MAIL_HOST=smtp.qq.com
SPRING_MAIL_USERNAME=your@qq.com
SPRING_MAIL_PASSWORD=your_16_char_auth_code
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
```

---

## 解决方案 2：使用 SendGrid（如果 587 也不行）

SendGrid 是专业邮件服务，Railway 不会阻止，且免费额度 100 封/天。

### 注册 SendGrid
1. 访问 https://sendgrid.com/
2. 注册免费账号
3. 创建 API Key（Settings → API Keys）
4. Sender 验证（发件人邮箱验证）

### 配置 Railway 环境变量

```bash
SPRING_MAIL_HOST=smtp.sendgrid.net
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=apikey
SPRING_MAIL_PASSWORD=<your_sendgrid_api_key>
SPRING_MAIL_PROTOCOL=smtp
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true
```

**注意**：`SPRING_MAIL_USERNAME` 必须是字面量 `apikey`，不是你的邮箱！

---

## 解决方案 3：阿里云邮件推送（国内推荐）

如果你有阿里云账号，邮件推送服务更稳定：

1. 开通服务：https://www.aliyun.com/product/directmail
2. 配置发信域名和发信地址
3. 获取 SMTP 密码

```bash
SPRING_MAIL_HOST=smtpdm.aliyun.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=your_sender@your_domain.com
SPRING_MAIL_PASSWORD=<smtp_password>
SPRING_MAIL_PROTOCOL=smtps
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

---

## 快速测试

修改环境变量后：
1. Railway 会自动重启服务
2. 点击"发送验证码"
3. 查看 Railway 日志：
   - 成功：`验证码邮件已发送至 xxx`
   - 失败：`邮件发送失败: <具体错误>`

---

## 为什么 465 不行？

Railway 平台的出站流量策略可能：
- 限制了 465 端口（防止垃圾邮件）
- 或 QQ 邮箱屏蔽了 Railway 的 IP 段
- ICMP_CSUM 错误表明数据包被网络层丢弃

这不是代码问题，是基础设施限制。587 端口通常更宽松。
