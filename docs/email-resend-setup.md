# Resend 邮件服务配置指南

## 为什么选择 Resend

- ✅ 现代化邮件服务，专为开发者设计
- ✅ HTTP API，不走 SMTP（绕过 Railway 端口限制）
- ✅ 每月免费 3000 封邮件
- ✅ 配置简单，API 清晰
- ✅ 官网：https://resend.com/

---

## 配置步骤（5 分钟）

### 1. 注册 Resend 并获取 API Key（2 分钟）

1. 访问 https://resend.com/
2. 点击 **Start Building** 注册账号
3. 验证邮箱后登录
4. 进入 **API Keys** 页面
5. 点击 **Create API Key**
   - Name: `smart-exam-railway`
   - Permission: **Full Access**
6. **复制生成的 API Key**（`re_xxxxxxxxx`）

### 2. 添加并验证发件域名（2 分钟）

**选项 A：使用自己的域名（推荐）**

1. 在 Resend 后台进入 **Domains**
2. 点击 **Add Domain**
3. 输入你的域名（如 `yourdomain.com`）
4. 按照提示在你的 DNS 服务商添加记录（SPF、DKIM）
5. 验证通过后，发件人地址可以用 `noreply@yourdomain.com`

**选项 B：使用 Resend 提供的测试域名（最快，但只能发到验证过的邮箱）**

1. 在 Resend 后台进入 **Domains**
2. 使用默认的 `onboarding` 域名
3. 发件人地址：`onboarding@resend.dev`
4. **注意**：测试域名只能发送到你在 Resend 后台验证过的邮箱地址
5. 要添加测试收件人：**Settings → Verified Emails** → 添加并验证

### 3. 配置 Railway 环境变量（1 分钟）

在 Railway 项目的 **Variables** 页面：

**删除所有旧的邮件相关变量**（`SPRING_MAIL_*`、`SENDGRID_*`），然后添加：

```bash
RESEND_API_KEY=re_你的API_Key
RESEND_FROM_EMAIL=noreply@yourdomain.com
```

或者使用测试域名：

```bash
RESEND_API_KEY=re_你的API_Key
RESEND_FROM_EMAIL=onboarding@resend.dev
```

保存后 Railway 会自动重启（30 秒）。

---

## 验证是否成功

1. **点击"发送验证码"**
2. **查看 Railway 日志**：
   ```
   准备通过 Resend HTTP API 发送验证码至: xxx@xxx.com
   验证码邮件已通过 Resend 发送至: xxx@xxx.com
   ```
3. **检查邮箱**（可能在垃圾箱）

---

## 常见问题

### Q1: 使用测试域名收不到邮件？

**A**: 测试域名 `onboarding@resend.dev` 只能发送到你在 Resend 后台验证过的邮箱。

解决方案：
1. 进入 Resend 后台 **Settings → Verified Emails**
2. 添加你要测试的邮箱地址
3. 去邮箱收验证邮件，点击验证
4. 验证后就能收到测试邮件了

### Q2: 想发给任意用户怎么办？

**A**: 必须使用自己的域名并完成 DNS 验证（选项 A）。添加域名后按照 Resend 提示配置 DNS 记录即可。

### Q3: API Key 在哪里？

**A**: Resend 后台 → **API Keys** → **Create API Key**，生成后立即复制（`re_` 开头）。

### Q4: 免费额度够用吗？

**A**: 每月 3000 封，对于考试系统完全够用。超出后每 1000 封 $1。

---

## 技术细节

### 改动内容

1. **依赖**: 使用 `spring-boot-starter-webflux`（内置 WebClient）
2. **服务**: `EmailServiceV3` 使用 Resend HTTP API
3. **注入**: `AuthService` 使用 `EmailServiceV3`

### API 请求示例

```http
POST https://api.resend.com/emails
Authorization: Bearer re_xxxxxxxxx
Content-Type: application/json

{
  "from": "noreply@yourdomain.com",
  "to": ["user@example.com"],
  "subject": "【广理考试中心】邮箱验证码",
  "html": "<div>...</div>"
}
```

### 为什么有效

| 方式 | 端口 | Railway 状态 |
|------|------|--------------|
| SMTP (旧) | 465/587 | ❌ 被封锁 |
| **Resend HTTP API (新)** | **443 (HTTPS)** | ✅ **必然通过** |

---

## 如果还不行

提供以下信息：

1. Railway 日志中的错误信息
2. Resend 后台是否显示邮件发送记录
3. 使用的是自己域名还是测试域名
4. 环境变量截图（API Key 打码）

我继续排查。
