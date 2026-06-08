# SendGrid HTTP API 邮件服务配置指南

## 问题根因

Railway 平台**封锁了所有 SMTP 端口**（465、587），诊断测试显示连接 QQ、Gmail、SendGrid、163 的 SMTP 全部超时。

这不是 QQ 邮箱的问题，是 Railway 的网络策略限制。

---

## 解决方案：改用 HTTP API

**已重新设计邮件系统**，使用 SendGrid HTTP API 而不是 SMTP：
- ✅ 通过 HTTPS (443端口) 发送邮件
- ✅ Railway 不会封锁 HTTP/HTTPS
- ✅ 代码已更新为 EmailServiceV2

---

## 配置步骤（5 分钟）

### 1. 注册 SendGrid 并获取 API Key

1. 访问 https://signup.sendgrid.com/
2. 注册免费账号（需验证邮箱）
3. 登录后，进入 **Settings → API Keys**
4. 点击 **Create API Key**
   - Name: `smart-exam-railway`
   - Permissions: **Full Access**
5. **立即复制生成的 API Key**（`SG.xxxxxxxxx`，只显示一次！）

### 2. 验证发件人邮箱

1. **Settings → Sender Authentication**
2. 选择 **Single Sender Verification**
3. 填写表单：
   - **From Name**: `广理考试中心`
   - **From Email Address**: 你的真实邮箱（Gmail、QQ、163 都可以）
   - **Reply To**: 同上
   - 其他随便填
4. 提交后，去邮箱收验证邮件，**点击链接验证**

### 3. 配置 Railway 环境变量

在 Railway 项目的 **Variables** 页面：

**删除所有旧的 `SPRING_MAIL_*` 变量**，然后添加：

```bash
SENDGRID_API_KEY=SG.你的API_Key
SENDGRID_FROM_EMAIL=你验证过的邮箱地址
SENDGRID_FROM_NAME=广理考试中心
```

就这三个变量，不需要其他任何配置！

---

## 验证

Railway 自动部署完成后（约 1-2 分钟）：

1. **点击"发送验证码"**
2. **查看 Railway 日志**：
   ```
   准备通过 SendGrid HTTP API 发送验证码至: xxx@xxx.com
   验证码邮件已通过 SendGrid 发送至: xxx@xxx.com, HTTP 状态: 202
   ```
3. **检查邮箱**（可能在垃圾箱）

---

## 技术细节

### 改动内容

1. **新增依赖**：`sendgrid-java` SDK
2. **新建服务**：`EmailServiceV2`（使用 HTTP API）
3. **修改注入**：`AuthService` 使用 `EmailServiceV2`

### 为什么有效

| 方式 | 端口 | Railway 状态 |
|------|------|--------------|
| SMTP (旧) | 465/587 | ❌ 被封锁 |
| HTTP API (新) | 443 (HTTPS) | ✅ 正常 |

SendGrid HTTP API 走的是标准 HTTPS 请求，Railway 不可能封锁 443 端口（否则所有 HTTP 服务都不能用了）。

---

## 费用

SendGrid 免费额度：**100 封/天**，对你的系统完全够用。

---

## 如果还不行

如果配置后仍然失败，可能的原因：

1. **API Key 错误**：检查是否完整复制（`SG.` 开头）
2. **发件人未验证**：必须在 SendGrid 后台完成邮箱验证
3. **环境变量名错误**：严格使用 `SENDGRID_API_KEY`、`SENDGRID_FROM_EMAIL`、`SENDGRID_FROM_NAME`
4. **Railway 部署失败**：查看 Deployments 是否有错误日志

提供具体错误信息，我继续排查。
