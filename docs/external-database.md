# 外部数据库服务配置指南

本文档介绍如何配置和使用外部 MySQL 数据库服务，适用于 Render、Fly.io、Heroku 等不提供内置数据库的云平台。

## 目录

- [为什么使用外部数据库](#为什么使用外部数据库)
- [推荐的免费 MySQL 服务](#推荐的免费-mysql-服务)
- [PlanetScale 配置指南](#planetscale-配置指南)
- [Aiven MySQL 配置指南](#aiven-mysql-配置指南)
- [Railway MySQL 配置指南](#railway-mysql-配置指南)
- [Azure MySQL 配置指南](#azure-mysql-配置指南)
- [AWS RDS MySQL 配置指南](#aws-rds-mysql-配置指南)
- [数据库连接最佳实践](#数据库连接最佳实践)
- [故障排查](#故障排查)

---

## 为什么使用外部数据库

### 优势

1. **数据持久化**：独立于应用部署，数据不会丢失
2. **高可用性**：托管服务提供自动备份和恢复
3. **可扩展性**：可根据需求升级数据库资源
4. **跨平台**：同一个数据库可被多个应用访问
5. **专业管理**：自动更新、安全补丁、监控

### 适用场景

- 使用 Render、Fly.io、Heroku 免费计划（不包含数据库）
- 需要独立管理数据库
- 多环境共享数据库（开发、测试、生产）
- 需要高级数据库功能（复制、备份、监控）

---

## 推荐的免费 MySQL 服务

| 服务 | 免费额度 | 优点 | 缺点 | 推荐度 |
|------|---------|------|------|--------|
| **PlanetScale** | 5GB 存储<br>10亿行读/月<br>1000万行写/月 | MySQL 8.0<br>无服务器架构<br>全球 CDN<br>分支功能 | 需要信用卡验证 | ⭐⭐⭐⭐⭐ |
| **Aiven** | 1个免费实例<br>有限资源 | 多种数据库<br>多区域<br>易于使用 | 资源限制较多 | ⭐⭐⭐⭐ |
| **Railway MySQL** | $5 免费额度/月 | 易于集成<br>自动配置 | 需要用完免费额度 | ⭐⭐⭐⭐ |
| **FreeSQLDatabase** | 5MB 存储 | 完全免费<br>即时创建 | 存储太小 | ⭐⭐ |

---

## PlanetScale 配置指南

PlanetScale 是基于 Vitess 的无服务器 MySQL 平台，提供慷慨的免费额度。

### 1. 注册账号

1. 访问 [planetscale.com](https://planetscale.com/)
2. 点击 **"Get started"**
3. 使用 GitHub/Google 账号登录
4. 完成邮箱验证

### 2. 创建数据库

1. 点击 **"Create a database"**
2. 输入数据库名称：`smart-exam-db`
3. 选择区域（推荐选择离用户最近的区域）：
   - **AWS us-east-1** (美国东部)
   - **AWS eu-west-1** (欧洲)
   - **AWS ap-southeast-1** (亚太)
4. 选择 **"Free"** 计划
5. 点击 **"Create database"**

### 3. 获取连接信息

#### 方法 1：使用连接向导

1. 进入数据库页面
2. 点击 **"Connect"** 按钮
3. 选择 **"Connect with"** → **"Java"**
4. 复制显示的连接信息

#### 方法 2：手动构建

PlanetScale 提供以下变量：

```bash
PLANETSCALE_HOST=aws.connect.psdb.cloud
PLANETSCALE_PORT=3306
PLANETSCALE_DATABASE=smart-exam-db
PLANETSCALE_USERNAME=your_generated_username
PLANETSCALE_PASSWORD=your_generated_password
```

### 4. 构建 JDBC 连接字符串

```bash
MYSQL_URL=jdbc:mysql://aws.connect.psdb.cloud:3306/smart-exam-db?sslMode=VERIFY_IDENTITY&useSSL=true
MYSQL_USERNAME=your_generated_username
MYSQL_PASSWORD=your_generated_password
```

**重要配置**：
- PlanetScale 要求 SSL：`sslMode=VERIFY_IDENTITY&useSSL=true`
- 不需要 `allowPublicKeyRetrieval=true`

### 5. 初始化数据库

#### 使用 PlanetScale CLI

```bash
# 安装 CLI
brew install planetscale/tap/pscale   # macOS
# 或
scoop install pscale                  # Windows

# 登录
pscale auth login

# 连接到数据库
pscale shell smart-exam-db main

# 执行 SQL
SOURCE backend/src/main/resources/db/schema.sql;
SOURCE backend/src/main/resources/db/data.sql;
```

#### 使用本地 MySQL 客户端

```bash
mysql -h aws.connect.psdb.cloud \
  -u your_username \
  -p \
  smart-exam-db \
  --ssl-mode=VERIFY_IDENTITY \
  < backend/src/main/resources/db/schema.sql
```

### 6. PlanetScale 特殊注意事项

#### 不支持外键约束

PlanetScale 不支持外键约束（FOREIGN KEY）。如果你的 schema.sql 包含外键，需要：

1. **移除外键定义**：
   ```sql
   -- 移除类似这样的约束
   -- FOREIGN KEY (user_id) REFERENCES users(id)
   ```

2. **在应用层实现引用完整性**

#### 使用分支功能

PlanetScale 支持数据库分支（类似 Git）：

```bash
# 创建开发分支
pscale branch create smart-exam-db dev

# 在分支上测试 schema 更改
pscale shell smart-exam-db dev

# 创建部署请求
pscale deploy-request create smart-exam-db dev

# 合并到主分支
pscale deploy-request deploy smart-exam-db 1
```

---

## Aiven MySQL 配置指南

Aiven 提供托管的 MySQL 服务，易于设置。

### 1. 注册账号

1. 访问 [aiven.io](https://aiven.io/)
2. 点击 **"Get started for free"**
3. 注册账号并验证邮箱

### 2. 创建 MySQL 服务

1. 登录后点击 **"Create service"**
2. 选择 **"MySQL"**
3. 选择云提供商和区域：
   - **AWS** / **Google Cloud** / **Azure**
   - 选择离用户最近的区域
4. 选择 **"Free plan"** (Hobbyist)
5. 输入服务名称：`smart-exam-mysql`
6. 点击 **"Create service"**

### 3. 等待服务启动

服务创建需要 5-10 分钟。状态变为 **"Running"** 后可以使用。

### 4. 获取连接信息

1. 进入服务详情页
2. 在 **"Overview"** 标签查看连接信息：
   - **Host**: `mysql-xxx.aivencloud.com`
   - **Port**: `12345`
   - **User**: `avnadmin`
   - **Password**: 自动生成的密码
   - **Database**: `defaultdb`

### 5. 构建 JDBC 连接字符串

```bash
MYSQL_URL=jdbc:mysql://mysql-xxx.aivencloud.com:12345/defaultdb?useSSL=true&requireSSL=true
MYSQL_USERNAME=avnadmin
MYSQL_PASSWORD=your_generated_password
```

**重要配置**：
- Aiven 要求 SSL：`useSSL=true&requireSSL=true`

### 6. 创建应用数据库

```bash
# 连接到 Aiven MySQL
mysql -h mysql-xxx.aivencloud.com \
  -P 12345 \
  -u avnadmin \
  -p \
  --ssl-mode=REQUIRED

# 创建数据库
CREATE DATABASE smart_exam_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 使用新数据库
USE smart_exam_system;

# 执行初始化脚本
SOURCE backend/src/main/resources/db/schema.sql;
SOURCE backend/src/main/resources/db/data.sql;
```

更新连接字符串：

```bash
MYSQL_URL=jdbc:mysql://mysql-xxx.aivencloud.com:12345/smart_exam_system?useSSL=true&requireSSL=true
```

---

## Railway MySQL 配置指南

Railway 提供内置的 MySQL 插件，非常易于使用。

### 1. 创建 MySQL 服务

1. 登录 [Railway Dashboard](https://railway.app/dashboard)
2. 创建新项目或进入现有项目
3. 点击 **"New"** → **"Database"** → **"Add MySQL"**
4. Railway 自动创建 MySQL 实例

### 2. 获取连接信息

Railway 自动提供以下环境变量：

```bash
MYSQLHOST=containers-us-west-xxx.railway.app
MYSQLPORT=1234
MYSQLDATABASE=railway
MYSQLUSER=root
MYSQLPASSWORD=your_password
MYSQL_URL=mysql://root:password@host:port/railway
```

### 3. 构建 JDBC 连接字符串

```bash
MYSQL_URL=jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE}?useSSL=true&requireSSL=true
MYSQL_USERNAME=${MYSQLUSER}
MYSQL_PASSWORD=${MYSQLPASSWORD}
```

或使用变量引用（在 Railway 中）：

```bash
MYSQL_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=true&requireSSL=true
MYSQL_USERNAME=${{MySQL.MYSQLUSER}}
MYSQL_PASSWORD=${{MySQL.MYSQLPASSWORD}}
```

### 4. 初始化数据库

Railway MySQL 会自动创建数据库。使用迁移脚本初始化：

```bash
# 使用 Railway CLI
railway connect MySQL

# 或使用本地客户端
mysql -h containers-us-west-xxx.railway.app \
  -P 1234 \
  -u root \
  -p \
  railway < backend/src/main/resources/db/schema.sql
```

---

## Azure MySQL 配置指南

Azure Database for MySQL 适合企业级应用。

### 1. 创建 Azure MySQL 服务器

```bash
# 使用 Azure CLI
az mysql flexible-server create \
  --resource-group myResourceGroup \
  --name smart-exam-mysql \
  --location eastus \
  --admin-user myadmin \
  --admin-password <your_password> \
  --sku-name Standard_B1ms \
  --tier Burstable \
  --storage-size 32
```

### 2. 配置防火墙规则

```bash
# 允许 Azure 服务访问
az mysql flexible-server firewall-rule create \
  --resource-group myResourceGroup \
  --name smart-exam-mysql \
  --rule-name AllowAzureServices \
  --start-ip-address 0.0.0.0 \
  --end-ip-address 0.0.0.0

# 允许你的 IP 访问
az mysql flexible-server firewall-rule create \
  --resource-group myResourceGroup \
  --name smart-exam-mysql \
  --rule-name AllowMyIP \
  --start-ip-address <your_ip> \
  --end-ip-address <your_ip>
```

### 3. 构建连接字符串

```bash
MYSQL_URL=jdbc:mysql://smart-exam-mysql.mysql.database.azure.com:3306/smart_exam_system?useSSL=true&requireSSL=true&serverTimezone=UTC
MYSQL_USERNAME=myadmin
MYSQL_PASSWORD=your_password
```

---

## AWS RDS MySQL 配置指南

AWS RDS 是 AWS 的托管数据库服务。

### 1. 创建 RDS 实例

通过 AWS Console 或 CLI：

```bash
aws rds create-db-instance \
  --db-instance-identifier smart-exam-db \
  --db-instance-class db.t3.micro \
  --engine mysql \
  --engine-version 8.0.35 \
  --master-username admin \
  --master-user-password your_password \
  --allocated-storage 20 \
  --publicly-accessible
```

### 2. 配置安全组

允许应用访问数据库（端口 3306）。

### 3. 构建连接字符串

```bash
MYSQL_URL=jdbc:mysql://smart-exam-db.xxx.rds.amazonaws.com:3306/smart_exam_system?useSSL=true
MYSQL_USERNAME=admin
MYSQL_PASSWORD=your_password
```

---

## 数据库连接最佳实践

### 1. 连接池配置

针对云数据库优化连接池：

```bash
# 环境变量
DB_POOL_MIN_IDLE=2
DB_POOL_MAX_SIZE=10
DB_CONNECTION_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
DB_MAX_LIFETIME=1800000
```

### 2. SSL/TLS 连接

始终启用 SSL 连接：

```bash
# PlanetScale
?sslMode=VERIFY_IDENTITY&useSSL=true

# 其他服务
?useSSL=true&requireSSL=true
```

### 3. 连接重试

配置自动重试机制处理临时连接失败。

### 4. 连接验证

```bash
# 在 application.yml 中配置
hikari:
  connection-test-query: SELECT 1
  validation-timeout: 5000
  keepalive-time: 300000
```

### 5. 时区设置

建议使用 UTC：

```bash
?serverTimezone=UTC
```

### 6. 字符编码

确保使用 UTF-8：

```bash
?useUnicode=true&characterEncoding=utf8mb4
```

---

## 故障排查

### 连接超时

**问题**：`Communications link failure`

**解决方案**：
1. 检查数据库服务是否运行
2. 验证防火墙规则
3. 检查网络连接
4. 增加连接超时：`connectTimeout=30000`

### SSL 错误

**问题**：`SSL connection required`

**解决方案**：
```bash
# 添加 SSL 参数
?useSSL=true&requireSSL=true
```

### 认证失败

**问题**：`Access denied for user`

**解决方案**：
1. 验证用户名和密码
2. 检查用户权限
3. 对于 Azure，使用：`username@servername`

### 字符集问题

**问题**：中文显示乱码

**解决方案**：
```bash
?characterEncoding=utf8mb4&useUnicode=true
```

---

## 数据库备份

### PlanetScale

自动备份，支持恢复点功能。

### Aiven

```bash
# 创建备份
curl -X POST https://api.aiven.io/v1/project/<project>/service/<service>/backup
```

### 手动备份

```bash
# 导出数据
mysqldump -h host -u user -p database > backup.sql

# 恢复数据
mysql -h host -u user -p database < backup.sql
```

---

## 相关文档

- [Railway 部署指南](./deploy-railway.md)
- [Render 部署指南](./deploy-render.md)
- [部署故障排查](./troubleshooting.md)

---

## 支持

如遇到数据库配置问题，请提交 GitHub Issue。
