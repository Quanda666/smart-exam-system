# Render 部署指南

本文档详细介绍如何将 Smart Exam System 部署到 Render 平台。Render 提供免费额度，部署流程简单，适合中小型应用。

## 目录

- [前置准备](#前置准备)
- [第一步：准备外部 MySQL 数据库](#第一步准备外部-mysql-数据库)
- [第二步：部署应用到 Render](#第二步部署应用到-render)
- [第三步：配置环境变量](#第三步配置环境变量)
- [第四步：初始化数据库](#第四步初始化数据库)
- [第五步：访问应用](#第五步访问应用)
- [常见问题](#常见问题)

---

## 前置准备

1. **GitHub 账号**：用于连接代码仓库
2. **Render 账号**：访问 [render.com](https://render.com) 注册（可使用 GitHub/GitLab 登录）
3. **外部 MySQL 数据库**：Render 免费计划不包含数据库，需使用外部服务

### Render 免费额度说明

- **免费 Web 服务**：每月 750 小时（约 31 天）
- **限制**：
  - 服务闲置 15 分钟后自动休眠
  - 首次访问需要 30-60 秒唤醒
  - 每月 100GB 出站流量
- **适用场景**：开发、测试、演示项目
- **升级选项**：Starter 计划 $7/月（始终在线，无休眠）

---

## 第一步：准备外部 MySQL 数据库

由于 Render 免费计划不提供 MySQL，需要使用外部数据库服务。推荐以下免费服务：

### 选项 1：PlanetScale（推荐）

**特点**：
- 免费 5GB 存储
- 10 亿行读取/月
- 1000 万行写入/月
- 基于 MySQL 8.0
- 支持 SSL 连接

**注册步骤**：
1. 访问 [planetscale.com](https://planetscale.com/)
2. 使用 GitHub 登录
3. 创建新数据库：`smart-exam-db`
4. 在 **"Connect"** 页面获取连接信息

**连接字符串格式**：
```
jdbc:mysql://aws.connect.psdb.cloud/smart_exam_system?sslMode=VERIFY_IDENTITY&useSSL=true
```

### 选项 2：Aiven MySQL

**特点**：
- 免费计划提供 1个数据库实例
- 支持 MySQL 8.0
- 多区域部署

**注册步骤**：
1. 访问 [aiven.io](https://aiven.io/)
2. 注册并创建免费 MySQL 服务
3. 获取连接信息

### 选项 3：Railway MySQL

如果使用 Railway 数据库但部署在 Render：
1. 在 Railway 创建独立的 MySQL 服务
2. 将其连接信息配置到 Render

详细配置见 [外部数据库服务配置指南](./external-database.md)。

---

## 第二步：部署应用到 Render

### 2.1 创建 Web Service

1. 登录 [Render Dashboard](https://dashboard.render.com/)
2. 点击 **"New +"** → **"Web Service"**
3. 选择 **"Build and deploy from a Git repository"**
4. 点击 **"Next"**

### 2.2 连接 GitHub 仓库

1. 选择 **"Connect GitHub"** 或 **"Connect GitLab"**
2. 授权 Render 访问你的仓库
3. 搜索并选择 `smart-exam-system` 仓库

### 2.3 配置服务

在配置页面填写以下信息：

| 字段 | 值 | 说明 |
|------|-----|------|
| **Name** | `smart-exam-system` | 服务名称（会成为域名一部分） |
| **Region** | `Oregon (US West)` 或最近的区域 | 选择最近的区域以降低延迟 |
| **Branch** | `main` 或 `master` | 部署分支 |
| **Root Directory** | 留空 | 使用仓库根目录 |
| **Runtime** | `Docker` | **必须选择 Docker** |
| **Instance Type** | `Free` | 免费计划 |

**重要**：Render 会自动检测根目录的 `Dockerfile` 并使用 Docker 构建。

### 2.4 确认配置

点击 **"Create Web Service"**，Render 开始构建应用。

---

## 第三步：配置环境变量

在服务创建后，需要配置环境变量。

### 3.1 进入环境变量配置

1. 在服务页面，点击左侧菜单的 **"Environment"**
2. 点击 **"Add Environment Variable"**

### 3.2 添加必需的环境变量

#### 数据库配置（必需）

```bash
# 使用外部 MySQL 服务的连接信息
MYSQL_URL=jdbc:mysql://aws.connect.psdb.cloud/smart_exam_system?sslMode=VERIFY_IDENTITY&useSSL=true
MYSQL_USERNAME=your_database_username
MYSQL_PASSWORD=your_database_password
```

#### 数据库初始化

```bash
# 首次部署设置为 always，之后改为 never
DB_INIT_MODE=always
```

#### CORS 配置（必需）

```bash
# 添加你的 Render 域名
CORS_ALLOWED_ORIGIN_PATTERNS=https://*.onrender.com,https://smart-exam-system.onrender.com
```

#### Spring Profile

```bash
SPRING_PROFILES_ACTIVE=prod
```

#### 日志级别

```bash
LOG_LEVEL=INFO
APP_LOG_LEVEL=INFO
```

#### AI 服务配置（可选）

```bash
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_API_KEY=sk-your-api-key
OPENAI_MODEL=gpt-4o-mini
AI_MOCK_ENABLED=false
AI_TIMEOUT_SECONDS=30
```

如果不配置 AI，系统会使用模拟模式。

#### 邮件服务配置（可选，推荐）

系统当前使用 Resend HTTP API 发送验证码邮件，不再使用 SMTP。Render 环境变量示例：

```bash
RESEND_API_KEY=re_your_api_key
RESEND_FROM_EMAIL=onboarding@resend.dev
```

正式环境建议在 Resend 中验证自己的域名，然后将 `RESEND_FROM_EMAIL` 改为 `noreply@你的域名.com`。

**注意事项**：
- 邮件服务为可选配置，不配置不影响核心考试流程。
- 未配置时，验证码会生成并写入数据库，但不会发送邮件。
- Resend 测试域名只能发送到已验证的收件邮箱。
- 详细步骤见 [邮件服务配置指南](./email-setup.md)。

### 3.3 保存并重新部署

1. 点击 **"Save Changes"**
2. Render 会自动触发重新部署

---

## 第四步：初始化数据库

### 4.1 自动初始化（推荐）

如果设置了 `DB_INIT_MODE=always`，应用首次启动时会自动：
- 执行 `schema.sql`：创建表结构
- 执行 `data.sql`：插入初始数据

### 4.2 手动初始化

如果需要手动初始化：

#### 使用本地 MySQL 客户端

```bash
# 设置环境变量（从外部数据库服务获取）
export MYSQL_URL="jdbc:mysql://..."
export MYSQL_USERNAME="your_username"
export MYSQL_PASSWORD="your_password"

# 运行迁移脚本
./scripts/migrate-database.sh
```

#### 直接连接数据库

```bash
# PlanetScale 示例
mysql -h aws.connect.psdb.cloud -u your_username -p your_database_name < backend/src/main/resources/db/schema.sql
mysql -h aws.connect.psdb.cloud -u your_username -p your_database_name < backend/src/main/resources/db/data.sql
```

---

## 第五步：访问应用

### 5.1 获取应用 URL

Render 会自动生成一个域名：

```
https://smart-exam-system.onrender.com
```

在服务页面顶部可以看到完整 URL。

### 5.2 等待首次构建

首次部署需要：
- 构建时间：约 10-15 分钟
- 部署时间：约 1-2 分钟

在 **"Logs"** 标签可以查看构建和部署日志。

### 5.3 测试应用

访问以下端点测试：

```bash
# 健康检查
https://your-app.onrender.com/api/health

# AI 状态
https://your-app.onrender.com/api/ai/status

# 前端页面
https://your-app.onrender.com/
```

### 5.4 登录系统

使用默认管理员账号：

- **用户名**：`admin`
- **密码**：`admin123`

**重要**：首次登录后，请立即修改管理员密码！

---

## 常见问题

### Q1: 应用构建超时或失败

**原因**：Render 免费计划构建时间限制

**解决方案**：
1. 检查构建日志，查看具体错误
2. 优化 Dockerfile，减少构建步骤
3. 考虑升级到付费计划

### Q2: 应用启动后访问很慢

**原因**：Render 免费服务会在 15 分钟无活动后休眠

**解决方案**：
1. 首次访问需要 30-60 秒唤醒时间，这是正常现象
2. 升级到 Starter 计划（$7/月）保持服务始终在线
3. 使用 UptimeRobot 等服务定期 ping 应用保持唤醒

### Q3: 数据库连接失败

**原因**：数据库连接字符串配置错误

**解决方案**：
1. 检查 `MYSQL_URL` 格式是否正确
2. 确保外部数据库服务正常运行
3. 验证用户名和密码
4. 检查 SSL 配置：
   - PlanetScale 需要：`sslMode=VERIFY_IDENTITY&useSSL=true`
   - Aiven 需要：`useSSL=true&requireSSL=true`

### Q4: CORS 错误

**原因**：CORS 配置未包含 Render 域名

**解决方案**：
```bash
CORS_ALLOWED_ORIGIN_PATTERNS=https://*.onrender.com,https://your-specific-app.onrender.com
```

### Q5: 应用内存不足

**原因**：Render 免费计划限制 512MB 内存

**解决方案**：
1. 优化 JVM 内存设置：
   ```bash
   JAVA_OPTS=-Xmx400m -Xms200m -XX:MaxMetaspaceSize=128m
   ```
2. 升级到付费计划获取更多内存

### Q6: 如何查看日志？

1. 在服务页面点击 **"Logs"** 标签
2. 选择 **"Logs"** 查看应用运行日志
3. 选择 **"Events"** 查看部署事件

### Q7: 如何重新部署？

方法 1：手动触发
1. 点击右上角 **"Manual Deploy"**
2. 选择 **"Deploy latest commit"**

方法 2：自动部署
- 每次推送到 GitHub 分支，Render 自动部署

### Q8: 如何绑定自定义域名？

1. 在服务页面点击 **"Settings"**
2. 找到 **"Custom Domains"** 部分
3. 点击 **"Add Custom Domain"**
4. 按照说明配置 DNS 记录

---

## 性能优化建议

### 1. 防止服务休眠

使用 cron 服务定期访问应用：

```yaml
# 创建 Render Cron Job
Type: Cron Job
Schedule: */10 * * * * (每 10 分钟)
Command: curl https://your-app.onrender.com/api/health
```

或使用外部服务：
- [UptimeRobot](https://uptimerobot.com/)：免费监控服务
- [Cron-job.org](https://cron-job.org/)：免费 cron 任务

### 2. 数据库连接池优化

```bash
DB_POOL_MIN_IDLE=1
DB_POOL_MAX_SIZE=3
DB_CONNECTION_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
```

### 3. JVM 内存优化

```bash
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Xms256m -Xmx400m
```

### 4. 禁用不必要的日志

```bash
LOG_LEVEL=WARN
SPRING_WEB_LOG_LEVEL=WARN
SPRING_JDBC_LOG_LEVEL=WARN
```

---

## 自动部署配置

Render 默认启用自动部署：

### 配置自动部署分支

1. 进入 **"Settings"**
2. 找到 **"Build & Deploy"** 部分
3. 在 **"Branch"** 设置部署分支（如 `main`）
4. 启用 **"Auto-Deploy"**

### 配置部署钩子

可以在特定事件触发部署：
- Push to branch：推送到分支
- Pull request：合并 PR 时

---

## 监控与维护

### 查看服务状态

在 Dashboard 可以看到：
- CPU 使用率
- 内存使用率
- 请求数量
- 响应时间

### 设置告警

1. 进入 **"Settings"**
2. 找到 **"Notifications"** 部分
3. 配置邮件或 Slack 通知

### 查看部署历史

在 **"Events"** 标签查看：
- 部署时间
- 部署状态
- 部署触发方式

---

## 安全建议

1. ✅ **使用环境变量**：所有敏感信息通过环境变量配置
2. ✅ **启用 HTTPS**：Render 自动提供 HTTPS
3. ✅ **修改默认密码**：首次登录后修改 admin 密码
4. ✅ **限制 CORS**：仅允许信任的域名
5. ✅ **定期备份**：定期备份外部数据库
6. ✅ **使用 SSL 连接数据库**：外部数据库必须启用 SSL

---

## 升级到付费计划

如需更好的性能和稳定性，可升级到：

### Starter Plan ($7/月)
- 始终在线（无休眠）
- 更多内存和 CPU
- 更快的构建速度
- 优先级支持

### Standard Plan ($25/月)
- 更高性能
- 自动扩展
- 更多资源

---

## 相关文档

- [Railway 部署指南](./deploy-railway.md)
- [外部数据库服务配置](./external-database.md)
- [部署故障排查](./troubleshooting.md)
- [Render 官方文档](https://render.com/docs)

---

## 支持与反馈

如遇到问题：
1. 查看 [部署故障排查文档](./troubleshooting.md)
2. 查看 Render 应用日志
3. 提交 GitHub Issue

祝部署顺利！🚀
