# 邮件服务配置指南

## 当前方案：Resend HTTP API

由于 Railway 平台封锁了所有 SMTP 端口（465/587），系统使用 **Resend HTTP API** 通过 HTTPS 发送邮件。

**特点：**
- ✅ HTTP API，通过 HTTPS (443端口) 发送
- ✅ 绕过 Railway 的 SMTP 端口限制
- ✅ 每月免费 3000 封邮件
- ✅ 配置简单，稳定可靠
- ✅ 邮件包含项目 Logo
- ✅ 官网：https://resend.com/

---

## 快速配置（5 分钟）

### 步骤 1：注册 Resend（2 分钟）

1. 访问 https://resend.com/
2. 点击 **Start Building** 注册账号
3. 验证邮箱后登录
4. 进入 **API Keys** → **Create API Key**
   - Name: `smart-exam-railway`
   - Permission: **Full Access**
5. **复制 API Key**（`re_xxxxxxxxx`）

### 步骤 2：配置发件人（选一个方案）

**方案 A：测试域名（最快，推荐先用这个）**

1. 使用 Resend 提供的默认域名 `onboarding@resend.dev`
2. 在 Resend 后台进入 **Settings → Verified Emails**
3. 添加你要测试的邮箱地址并验证
4. **限制**：只能发送到验证过的邮箱

**方案 B：自己的域名（正式环境）**

1. 在 Resend 后台 **Domains** → **Add Domain**
2. 输入你的域名（如 `yourdomain.com`）
3. 按提示在 DNS 服务商添加记录（SPF、DKIM）
4. 验证通过后可以发送到任意邮箱
5. 发件人地址：`noreply@yourdomain.com`

### 步骤 3：配置 Railway 环境变量（1 分钟）

在 Railway 项目的 **Variables** 页面添加：

**测试方案：**
```
RESEND_API_KEY=re_你的API_Key
RESEND_FROM_EMAIL=onboarding@resend.dev
```

**正式方案：**
```
RESEND_API_KEY=re_你的API_Key
RESEND_FROM_EMAIL=noreply@你的域名.com
```

保存后 Railway 会自动重启（30 秒）。

---

## 邮件样式

邮件包含以下内容：

- **项目 Logo**（自动从 GitHub 加载）
- **广理考试中心** 标题
- **6 位验证码**（大字号居中显示）
- 有效期提示（5 分钟）
- 自动发送提示

---

## 验证

1. 点击"发送验证码"
2. 查看 Railway 日志：
   ```
   准备通过 Resend HTTP API 发送验证码至: xxx@xxx.com
   验证码邮件已通过 Resend 发送至: xxx@xxx.com
   ```
3. 检查邮箱（可能在垃圾箱）

---

## 常见问题

### Q: 使用测试域名收不到邮件？

**A**: 测试域名只能发送到 Resend 后台验证过的邮箱。

解决：进入 **Settings → Verified Emails** → 添加并验证目标邮箱。

### Q: 想发给任意用户怎么办？

**A**: 必须使用自己的域名（方案 B）并完成 DNS 验证。

### Q: 免费额度够用吗？

**A**: 每月 3000 封，对考试系统完全够用。超出后每 1000 封 $1。

### Q: Logo 显示不出来？

**A**: Logo 从 GitHub 仓库加载，确保：
1. 仓库是公开的
2. `frontend/public/logo.png` 文件存在
3. 或修改 `EmailServiceV3.java` 中的图片 URL

---

## 技术架构

### 代码结构

- **EmailServiceV3.java**: Resend HTTP API 集成
- **AuthService.java**: 异步调用邮件服务
- **WebClient**: Spring WebFlux 的 HTTP 客户端

### API 调用示例

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
| **Resend HTTP API** | **443 (HTTPS)** | ✅ **正常** |

---

## 故障排查

如果发送失败，检查：

1. **API Key 是否正确**：必须以 `re_` 开头
2. **发件人邮箱是否已验证**：测试域名需要验证收件人，自有域名需要 DNS 验证
3. **Railway 日志中的错误信息**：查看具体失败原因
4. **Resend 后台 Logs**：查看发送记录和状态

---

## 费用

- **免费额度**：3000 封/月
- **超额费用**：$1 / 1000 封
- **无月费**：只为实际使用付费

对于学校考试系统，免费额度完全够用。
