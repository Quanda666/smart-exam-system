# Railway 部署指南

本文档详细介绍如何将 Smart Exam System 部署到 Railway 平台。Railway 提供免费额度，非常适合开发和小规模生产环境。

## 目录

- [前置准备](#前置准备)
- [第一步：准备 GitHub 仓库](#第一步准备-github-仓库)
- [第二步：创建 MySQL 数据库](#第二步创建-mysql-数据库)
- [第三步：部署应用](#第三步部署应用)
- [第四步：配置环境变量](#第四步配置环境变量)
- [第五步：初始化数据库](#第五步初始化数据库)
- [第六步：访问应用](#第六步访问应用)
- [常见问题](#常见问题)

---

## 前置准备

1. **GitHub 账号**：用于连接代码仓库
2. **Railway 账号**：访问 [railway.app](https://railway.app) 注册（可使用 GitHub 登录）
3. **项目代码**：确保代码已推送到 GitHub 仓库

### Railway 免费额度说明

- **免费额度**：每月 $5 的免费使用额度
- **使用时长**：大约可运行 500 小时/月（约 20 天）
- **适用场景**：开发、测试、小规模生产环境
- **升级选项**：需要更多资源可升级为 Developer 计划（$5/月）

---

## 第一步：准备 GitHub 仓库

### 1.1 确认项目结构

确保你的 GitHub 仓库根目录包含以下文件：

```
smart-exam-system/
├── Dockerfile                # 根目录多阶段构建文件
├── docker-compose.yml        # 本地开发配置（Railway 不使用）
├── .env.example              # 环境变量模板
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
└── frontend/
    ├── package.json
    ├── Dockerfile
    └── src/
```

### 1.2 验证 Dockerfile

Railway 会自动检测并使用根目录的 [`Dockerfile`](../Dockerfile)。确认该文件存在且包含多阶段构建配置。

---

## 第二步：创建 MySQL 数据库

Railway 提供托管的 MySQL 数据库服务。

### 2.1 创建新项目

1. 登录 [Railway Dashboard](https://railway.app/dashboard)
2. 点击 **"New Project"**
3. 选择 **"Provision MySQL"**

### 2.2 获取数据库连接信息

1. 点击创建的 MySQL 服务
2. 进入 **"Variables"** 标签
3. Railway 会自动提供以下变量：
   - `MYSQLHOST`：数据库主机地址
   - `MYSQLPORT`：数据库端口（默认 3306）
   - `MYSQLDATABASE`：数据库名称
   - `MYSQLUSER`：数据库用户名
   - `MYSQLPASSWORD`：数据库密码

### 2.3 构建 JDBC 连接字符串

根据 Railway 提供的变量，构建 JDBC 连接字符串：

```
jdbc:mysql://[MYSQLHOST]:[MYSQLPORT]/[MYSQLDATABASE]?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=true&requireSSL=true
```

示例：
```
jdbc:mysql://containers-us-west-123.railway.app:3306/railway?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=true&requireSSL=true
```

**注意**：
- Railway MySQL 使用 SSL 连接，需要设置 `useSSL=true&requireSSL=true`
- 时区建议使用 `UTC` 而非 `Asia/Shanghai`，避免连接问题

---

## 第三步：部署应用

### 3.1 从 GitHub 部署

1. 在 Railway Dashboard 中，点击 **"New Project"**
2. 选择 **"Deploy from GitHub repo"**
3. 授权 Railway 访问你的 GitHub 账号
4. 选择 `smart-exam-system` 仓库
5. Railway 会自动检测 Dockerfile 并开始构建

### 3.2 监控构建过程

1. 点击新创建的服务
2. 进入 **"Deployments"** 标签
3. 查看构建日志，确保构建成功

构建大约需要 5-10 分钟（包括前端和后端编译）。

---

## 第四步：配置环境变量

### 4.1 添加必需的环境变量

在应用服务中，进入 **"Variables"** 标签，添加以下环境变量：

#### 数据库配置

```bash
# 使用第二步获取的数据库信息
MYSQL_URL=jdbc:mysql://containers-us-west-123.railway.app:3306/railway?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=true&requireSSL=true
MYSQL_USERNAME=root
MYSQL_PASSWORD=your_railway_mysql_password
```

#### 数据库初始化模式

```bash
# 首次部署设置为 always，后续可改为 never
DB_INIT_MODE=always
```

#### CORS 配置

```bash
# 添加你的 Railway 域名
CORS_ALLOWED_ORIGIN_PATTERNS=https://*.railway.app,https://your-app.railway.app
```

#### AI 服务配置（可选）

```bash
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_API_KEY=sk-your-api-key
OPENAI_MODEL=gpt-4o-mini
AI_MOCK_ENABLED=false
AI_TIMEOUT_SECONDS=30
```

如果不配置 AI，系统会使用模拟模式，不影响核心功能。

#### 邮件服务配置（可选，推荐配置）

系统支持邮箱验证码登录和邮箱绑定功能。配置后用户可以：
- 使用邮箱验证码登录（已绑定邮箱的用户）
- 在账号中心绑定/更换邮箱
- 提升账号安全性

**使用 QQ 邮箱 SMTP**（推荐，免费且稳定）：

```bash
# QQ 邮箱 SMTP 配置
SPRING_MAIL_HOST=smtp.qq.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=your_qq_email@qq.com
SPRING_MAIL_PASSWORD=your_authorization_code
SPRING_MAIL_PROTOCOL=smtps
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

**获取 QQ 邮箱授权码**：
1. 登录 QQ 邮箱网页版：https://mail.qq.com
2. 进入「设置」→「账户」
3. 找到「POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV服务」
4. 开启「IMAP/SMTP服务」或「POP3/SMTP服务」
5. 生成授权码（16位，如 `abcd efgh ijkl mnop`）
6. 将授权码填入 `SPRING_MAIL_PASSWORD`（去除空格）

**使用其他邮箱**：

```bash
# 163 邮箱
SPRING_MAIL_HOST=smtp.163.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=your_email@163.com
SPRING_MAIL_PASSWORD=your_authorization_code

# Gmail（需海外服务器）
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com
SPRING_MAIL_PASSWORD=your_app_password
SPRING_MAIL_PROTOCOL=smtp
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

**注意事项**：
- 邮件服务为**可选配置**，不配置不影响系统核心功能
- 未配置时，系统会降级：验证码生成但无法发送（仅记录日志）
- 登录页的「验证码登录」Tab 在未配置邮件时不可用
- 推荐在生产环境配置邮件服务以提升用户体验

### 4.3 使用 Railway 变量引用（推荐）

Railway 支持变量引用，可以直接使用 MySQL 服务的变量：

```bash
MYSQL_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=true&requireSSL=true
MYSQL_USERNAME=${{MySQL.MYSQLUSER}}
MYSQL_PASSWORD=${{MySQL.MYSQLPASSWORD}}
```

这样当 MySQL 凭据更新时，应用会自动使用新值。

### 4.4 保存并重新部署

添加完环境变量后，点击 **"Deploy"** 重新部署应用。

---

## 第五步：初始化数据库

### 5.1 自动初始化（推荐）

如果设置了 `DB_INIT_MODE=always`，应用启动时会自动执行：
- `backend/src/main/resources/db/schema.sql`：创建表结构
- `backend/src/main/resources/db/data.sql`：插入初始数据

### 5.2 手动初始化

如果需要手动初始化数据库：

#### 方法 1：使用本地 MySQL 客户端

```bash
# 设置环境变量（从 Railway 获取）
export MYSQL_URL="jdbc:mysql://..."
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="..."

# 运行迁移脚本
./scripts/migrate-database.sh
```

#### 方法 2：使用 Railway CLI

```bash
# 安装 Railway CLI
npm install -g @railway/cli

# 登录
railway login

# 连接到项目
railway link

# 进入 MySQL Shell
railway connect MySQL

# 执行 SQL 脚本
SOURCE backend/src/main/resources/db/schema.sql;
SOURCE backend/src/main/resources/db/data.sql;
```

---

## 第六步：访问应用

### 6.1 生成公共域名

1. 在应用服务中，进入 **"Settings"** 标签
2. 找到 **"Networking"** 部分
3. 点击 **"Generate Domain"**
4. Railway 会自动生成一个域名，如：`smart-exam-system.railway.app`

### 6.2 测试应用

访问生成的域名，测试以下端点：

```bash
# 健康检查
https://your-app.railway.app/api/health

# AI 状态
https://your-app.railway.app/api/ai/status

# 注册选项
https://your-app.railway.app/api/auth/register-options
```

### 6.3 登录系统

使用默认管理员账号登录：

- **用户名**：`admin`
- **密码**：`admin123`

**重要**：首次登录后，请立即修改管理员密码！

---

## 常见问题

### Q1: 应用启动失败，日志显示数据库连接超时

**原因**：数据库连接字符串配置错误或数据库未启动

**解决方案**：
1. 检查 `MYSQL_URL` 格式是否正确
2. 确保使用 `useSSL=true&requireSSL=true`
3. 验证 MySQL 服务是否正常运行
4. 检查时区设置，建议使用 `UTC`

### Q2: 前端页面显示空白

**原因**：前端构建失败或静态资源未正确打包

**解决方案**：
1. 查看构建日志，确认前端构建成功
2. 检查 Dockerfile 中的前端构建步骤
3. 验证 `frontend/dist` 目录已正确复制到 Spring Boot 静态资源目录

### Q3: API 请求返回 CORS 错误

**原因**：CORS 配置未包含 Railway 域名

**解决方案**：
```bash
CORS_ALLOWED_ORIGIN_PATTERNS=https://*.railway.app,https://your-specific-domain.railway.app
```

### Q4: 数据库表未创建

**原因**：`DB_INIT_MODE` 未设置或数据库初始化脚本执行失败

**解决方案**：
1. 设置 `DB_INIT_MODE=always`
2. 重新部署应用
3. 查看应用日志，检查数据库初始化错误

### Q5: 应用内存不足或构建超时

**原因**：Railway 免费额度限制

**解决方案**：
1. 升级到 Developer 计划
2. 优化 Dockerfile，减少构建时间和镜像大小
3. 调整 JVM 内存配置：
   ```bash
   JAVA_OPTS=-Xmx512m -Xms256m
   ```

### Q6: 如何查看应用日志？

在 Railway Dashboard 中：
1. 点击应用服务
2. 进入 **"Logs"** 标签
3. 实时查看应用日志输出

### Q7: 如何重启应用？

方法 1：通过 Dashboard
1. 进入 **"Deployments"** 标签
2. 点击最新部署右侧的 **"..."** 菜单
3. 选择 **"Restart"**

方法 2：通过 CLI
```bash
railway restart
```

### Q8: 数据库数据会丢失吗？

不会。Railway 的 MySQL 服务使用持久化存储，数据会保留。但建议：
1. 定期备份数据库
2. 使用 `mysqldump` 导出数据
3. 在生产环境使用外部托管数据库服务

---

## 性能优化建议

### 1. 数据库连接池优化

在环境变量中添加：

```bash
DB_POOL_MIN_IDLE=2
DB_POOL_MAX_SIZE=5
DB_CONNECTION_TIMEOUT=30000
```

### 2. JVM 优化

```bash
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError
```

### 3. 启用 HTTP 压缩

已在 `application.yml` 中配置，无需额外设置。

### 4. 禁用开发模式日志

```bash
LOG_LEVEL=WARN
APP_LOG_LEVEL=INFO
```

---

## 后续维护

### 自动部署

Railway 默认启用了自动部署功能：
- 每次推送到 GitHub 主分支，Railway 会自动构建并部署
- 在 Settings > GitHub App 中可以配置部署分支

### 手动部署

如需手动触发部署：
1. 进入 **"Deployments"** 标签
2. 点击 **"Deploy"** 按钮

### 回滚部署

如果新版本有问题：
1. 进入 **"Deployments"** 标签
2. 找到稳定的历史部署
3. 点击 **"Redeploy"**

---

## 邮件功能使用指南

如果已配置邮件服务，用户可使用以下功能：

### 邮箱验证码登录

**适用场景**：用户忘记密码，但已绑定邮箱

1. 访问登录页，切换到「验证码登录」Tab
2. 输入已绑定的邮箱地址
3. 点击「发送验证码」，系统会发送 6 位验证码到邮箱
4. 输入验证码并登录

**验证码规则**：
- 有效期 5 分钟
- 每天每邮箱最多发送 5 次
- 60 秒内不可重复发送
- 验证码一次性使用

### 邮箱绑定/更换

**操作步骤**：

1. 登录后，点击右上角用户名下拉菜单
2. 选择「绑定/更换邮箱」
3. 输入要绑定的邮箱地址
4. 点击「发送验证码」
5. 输入收到的 6 位验证码
6. 完成绑定

**注意事项**：
- 每个邮箱只能绑定一个账号
- 绑定成功后即可使用验证码登录
- 更换邮箱需要重新验证新邮箱

### 验证码邮件模板

系统发送的邮件格式如下：

```
主题：【广理考试中心】邮箱验证码

您的验证码是：123456
验证码 5 分钟内有效，请勿泄露给他人。
此邮件由系统自动发送，请勿回复。
```

### 故障排查

**用户无法收到验证码**：

1. **检查垃圾邮件箱**：某些邮箱可能将验证码邮件识别为垃圾邮件
2. **检查邮箱拼写**：确认输入的邮箱地址正确
3. **检查发送频率**：60 秒内不可重复发送
4. **联系管理员**：确认服务器邮件配置是否正确

**管理员检查邮件配置**：

1. 访问 `/api/health` 端点，检查邮件配置状态
2. 查看应用日志中的邮件发送记录：
   ```
   邮件服务未配置，验证码已生成但无法发送: user@example.com -> 123456
   ```
3. 确认 Railway 环境变量中邮件配置正确
4. 测试 SMTP 连接（使用邮件客户端或在线工具）

---

## 安全建议

1. ✅ **修改默认密码**：首次登录后立即修改 admin 密码
2. ✅ **使用环境变量**：所有敏感信息通过环境变量配置
3. ✅ **启用 HTTPS**：Railway 自动提供 HTTPS
4. ✅ **限制 CORS**：仅允许信任的域名
5. ✅ **定期备份**：定期备份数据库数据
6. ✅ **监控日志**：定期检查应用和数据库日志

---

## 相关文档

- [Render 部署指南](./deploy-render.md)
- [外部数据库服务配置](./external-database.md)
- [部署故障排查](./troubleshooting.md)
- [Railway 官方文档](https://docs.railway.app/)

---

## 支持与反馈

如遇到问题：
1. 查看 [部署故障排查文档](./troubleshooting.md)
2. 查看 Railway 应用日志
3. 提交 GitHub Issue

祝部署顺利！🚀
