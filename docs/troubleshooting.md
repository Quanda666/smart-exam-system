# 部署故障排查指南

本文档收集了 Smart Exam System 在各种云平台部署过程中可能遇到的常见问题和解决方案。

## 目录

- [Docker 构建问题](#docker-构建问题)
- [数据库连接问题](#数据库连接问题)
- [应用启动问题](#应用启动问题)
- [前端页面问题](#前端页面问题)
- [API 请求问题](#api-请求问题)
- [平台特定问题](#平台特定问题)
- [性能问题](#性能问题)
- [日志查看方法](#日志查看方法)

---

## Docker 构建问题

### 前端构建失败

**症状**：`npm run build` 阶段失败，构建日志显示错误

**解决方案**：

1. **检查 Node.js 版本**：
   Dockerfile 使用 `node:20-alpine`，确认你的前端代码兼容 Node.js 20：
   ```json
   // frontend/package.json
   "engines": {
     "node": ">=18.0.0"
   }
   ```

2. **检查 TypeScript 类型错误**：
   ```bash
   # 本地运行类型检查
   cd frontend
   npx vue-tsc --noEmit
   ```

3. **修复构建命令**：
   在 `frontend/package.json` 中简化 build 脚本：
   ```json
   "build": "vite build"
   ```
   或保持类型检查但忽略错误：
   ```json
   "build": "vue-tsc --noEmit || vite build"
   ```

4. **清理 npm 缓存**：
   在 Dockerfile 中添加：
   ```dockerfile
   RUN npm cache clean --force
   RUN npm install --no-audit --no-fund
   ```

### Maven 构建失败

**症状**：`mvn package` 阶段失败，构建日志显示编译错误或依赖下载失败

**解决方案**：

1. **检查 Java 版本**：
   ```bash
   # 确认 pom.xml 中的 Java 版本
   # <java.version>17</java.version>
   ```

2. **Maven 依赖离线模式失败**：
   如果 `dependency:go-offline` 失败，修改 Dockerfile：
   ```dockerfile
   # 移除离线模式限制
   RUN mvn dependency:resolve -B
   ```

3. **内存不足**：
   在 Maven 构建阶段添加内存限制：
   ```dockerfile
   RUN mvn package -DskipTests -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
   ```

4. **清理缓存**：
   在 Dockerfile 中添加：
   ```dockerfile
   RUN mvn clean
   ```

### Docker 镜像过大

**症状**：镜像大小超过平台限制或部署耗时过长

**解决方案**：

1. **使用 Alpine 基础镜像**（已配置）：
   ```dockerfile
   FROM eclipse-temurin:17-jre-alpine AS runner
   ```

2. **添加 .dockerignore 文件**：
   已在 `backend/.dockerignore` 和 `frontend/.dockerignore` 中配置。

3. **使用多阶段构建**（已配置）：
   当前 Dockerfile 已使用三阶段构建。

4. **压缩构建产物**：
   在 Dockerfile 运行阶段添加：
   ```dockerfile
   RUN jar -cf app.jar -C target/classes . && jar -cf app.jar -C target/dependency .
   ```

---

## 数据库连接问题

### 连接超时 (Communications link failure)

**症状**：
```
com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
```

**解决方案**：

1. **检查连接字符串格式**：
   ```bash
   # 正确的格式
   MYSQL_URL=jdbc:mysql://host:port/database?parameters
   
   # PlanetScale 正确格式
   MYSQL_URL=jdbc:mysql://aws.connect.psdb.cloud/smart_exam_system?sslMode=VERIFY_IDENTITY&useSSL=true
   
   # Aiven 正确格式
   MYSQL_URL=jdbc:mysql://mysql-xxx.aivencloud.com:12345/smart_exam_system?useSSL=true&requireSSL=true
   ```

2. **增加连接超时**：
   ```bash
   # 在连接字符串中添加
   ?connectTimeout=60000&socketTimeout=60000
   
   # 或在环境变量中设置
   DB_CONNECTION_TIMEOUT=60000
   ```

3. **验证防火墙规则**：
   - 确认数据库服务的防火墙允许应用 IP 访问
   - 对于云数据库，检查是否需要将应用 IP 加入白名单

4. **检查 DNS 解析**：
   ```bash
   # 测试 DNS 解析
   nslookup aws.connect.psdb.cloud
   ```

### SSL 连接错误

**症状**：
```
javax.net.ssl.SSLHandshakeException: No appropriate protocol
```

**解决方案**：

1. **启用正确的 SSL 参数**：
   ```bash
   # PlanetScale
   ?sslMode=VERIFY_IDENTITY&useSSL=true
   
   # Aiven/其他
   ?useSSL=true&requireSSL=true
   
   # 或更宽松的选项（不推荐生产环境）
   ?useSSL=true&requireSSL=false&verifyServerCertificate=false
   ```

2. **添加 SSL 信任存储**（如有需要）：
   ```bash
   # 在启动脚本中
   JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStore=/path/to/truststore.jks"
   ```

### 认证失败 (Access denied)

**症状**：
```
java.sql.SQLException: Access denied for user 'username'@'host'
```

**解决方案**：

1. **验证凭据**：
   ```bash
   # 使用 MySQL 客户端测试
   mysql -h host -P port -u username -p database
   ```

2. **检查用户名格式**：
   - Azure MySQL 需要：`username@servername`
   - 其他服务通常只需：`username`

3. **重置密码**：
   在数据库服务管理界面重置密码，然后更新 `MYSQL_PASSWORD`。

### 数据库不存在

**症状**：
```
Unknown database 'smart_exam_system'
```

**解决方案**：

1. **设置自动创建**：
   ```bash
   # 设置初始化模式
   DB_INIT_MODE=always
   ```

2. **手动创建数据库**：
   ```sql
   CREATE DATABASE IF NOT EXISTS smart_exam_system 
     CHARACTER SET utf8mb4 
     COLLATE utf8mb4_unicode_ci;
   ```

3. **检查数据库名称**：
   - PlanetScale 使用创建时的数据库名
   - Railway 默认数据库名是 `railway`
   - Aiven 默认数据库名是 `defaultdb`

### 时区错误

**症状**：
```
The server time zone value 'XXX' is unrecognized
```

**解决方案**：

```bash
# 1. 设置明确的时区
MYSQL_URL=...?serverTimezone=UTC

# 2. 或在数据库中设置
SET GLOBAL time_zone = '+00:00';
```

---

## 应用启动问题

### 应用启动后立即退出

**症状**：容器启动后几秒就退出

**解决方案**：

1. **检查应用日志**：
   ```bash
   docker logs smart-exam-backend
   ```

2. **检查必需的环境变量**：
   ```bash
   # 必需变量
   MYSQL_URL
   MYSQL_USERNAME
   MYSQL_PASSWORD
   ```

3. **检查端口是否被占用**：
   ```bash
   # 容器内
   netstat -tlnp
   ```

### 端口绑定错误

**症状**：
```
java.net.BindException: Address already in use
```

**解决方案**：

1. **使用平台提供的 PORT 变量**：
   application.yml 已配置 `server.port: ${PORT:${SERVER_PORT:8080}}`

2. **设置正确的端口**：
   ```bash
   # Railway/Render/Heroku 自动提供
   PORT=8080
   
   # 或手动设置
   SERVER_PORT=8080
   ```

### JVM 内存不足

**症状**：
```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案**：

```bash
# 设置 JVM 内存限制
JAVA_OPTS="-Xms256m -Xmx512m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m"

# 或使用百分比
JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

### Spring 初始化失败

**症状**：大量的 Spring 初始化错误日志

**解决方案**：

1. **查看具体错误**：
   ```bash
   # 启用详细日志
   LOG_LEVEL=DEBUG
   APP_LOG_LEVEL=DEBUG
   ```

2. **常见错误**：
   - SQL 初始化失败：检查 `schema.sql` 和 `data.sql` 语法
   - Bean 创建失败：检查配置类
   - 资源文件缺失：检查 classpath 资源

---

## 前端页面问题

### 空白页面

**症状**：访问根路径显示空白页

**解决方案**：

1. **检查前端是否打包**：
   确认 Dockerfile 中前端构建产物被正确复制到 Spring Boot 的 static 目录。

2. **检查资源路径**：
   ```html
   <!-- frontend/index.html -->
   <!-- 确保资源路径正确 -->
   <script type="module" src="/src/main.ts"></script>
   ```

3. **检查 Vite 配置**：
   ```typescript
   // frontend/vite.config.ts
   export default defineConfig({
     base: '/',  // 确保 base 为 '/'
   });
   ```

### 页面刷新 404

**症状**：直接访问非根路由（如 `/admin`）返回 404

**解决方案**：

检查 [`SpaController.java`](../backend/src/main/java/com/smartexam/controller/SpaController.java) 是否正确配置了 SPA 路由转发。

### 静态资源 404

**症状**：CSS/JS 文件加载失败

**解决方案**：

1. **检查构建输出目录**：
   ```bash
   # 确认 frontend/dist/ 存在且有内容
   ls -la frontend/dist/
   ```

2. **检查 Dockerfile 复制路径**：
   ```dockerfile
   COPY --from=frontend-builder /app/frontend/dist/ /app/backend/src/main/resources/static/
   ```

### CORS 错误

**症状**：浏览器控制台显示 CORS 相关错误

**解决方案**：

```bash
# 添加应用域名到 CORS 配置
CORS_ALLOWED_ORIGIN_PATTERNS=https://your-app.railway.app,https://your-app.onrender.com,https://*.onrender.com
```

---

## API 请求问题

### 健康检查失败

**症状**：`GET /api/health` 返回错误

**解决方案**：

1. **检查数据库连接**：
   健康检查包含数据库连接测试，如果数据库不可用会失败。

2. **检查健康检查端点**：
   ```bash
   curl -v https://your-app.railway.app/api/health
   ```

3. **查看详细错误**：
   ```bash
   curl -v https://your-app.railway.app/actuator/health
   ```

### 请求返回 500

**症状**：API 请求返回 500 Internal Server Error

**解决方案**：

1. **查看应用日志**：
   检查完整的错误堆栈。

2. **常见原因**：
   - 数据库查询错误
   - 空指针异常
   - 参数验证失败
   - 依赖注入失败

3. **启用详细日志**：
   ```bash
   APP_LOG_LEVEL=DEBUG
   ```

### 认证失败

**症状**：登录返回 401 Unauthorized

**解决方案**：

1. **检查 Token 格式**：
   - 确认请求头包含 `Authorization: Bearer <token>`

2. **检查 Token 是否过期**：
   - 重新登录获取新 Token

3. **确认用户存在**：
   ```sql
   SELECT * FROM users WHERE username = 'admin';
   ```

---

## 平台特定问题

### Railway

**问题**：应用持续重启

**解决方案**：
- 检查环境变量是否完整配置
- 确认 MySQL 服务状态
- 查看 Railway 日志

**问题**：构建超时

**解决方案**：
- Railway 构建超时默认 20 分钟
- 优化 Dockerfile 减少构建时间
- 联系 Railway 支持增加超时时间

**问题**：端口绑定错误

**解决方案**：
- Railway 会自动设置 `PORT` 环境变量
- 不要覆盖 `PORT` 变量

### Render

**问题**：应用休眠后无法唤醒

**解决方案**：
- 等待 1-2 分钟（免费计划正常现象）
- 使用 UptimeRobot 定期 ping
- 升级到 Starter 计划

**问题**：构建超时

**解决方案**：
- Render 免费计划构建时间限制 1 小时
- 优化前端构建步骤
- 简化 Dockerfile

**问题**：磁盘空间不足

**解决方案**：
- 免费计划仅 1GB 磁盘
- 优化镜像大小
- 清理不需要的文件

### Fly.io

**问题**：部署命令失败

**解决方案**：
- 确认 `fly.toml` 配置正确
- 检查网络连接
- 使用 `fly doctor` 诊断

### Heroku

**问题**：slug 大小超限

**解决方案**：
- 使用多阶段构建
- 优化依赖包大小
- Heroku slug 限制 500MB

---

## 性能问题

### 应用响应缓慢

**解决方案**：

1. **检查数据库性能**：
   ```bash
   # 优化连接池
   DB_POOL_MIN_IDLE=2
   DB_POOL_MAX_SIZE=10
   DB_CONNECTION_TIMEOUT=30000
   ```

2. **优化 JVM**：
   ```bash
   JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
   ```

3. **启用 HTTP 压缩**：
   已在 `application.yml` 中配置。

### 数据库查询缓慢

**解决方案**：

1. **启用慢查询日志**：
   ```bash
   SPRING_JDBC_LOG_LEVEL=DEBUG
   ```

2. **添加数据库索引**：
   ```sql
   -- 常用查询字段添加索引
   CREATE INDEX idx_user_role ON users(role);
   CREATE INDEX idx_exam_status ON exams(status);
   ```

### 内存泄漏

**症状**：内存使用持续增长

**解决方案**：

1. **监控内存使用**：
   ```bash
   curl https://your-app.railway.app/actuator/metrics/jvm.memory.used
   ```

2. **检查连接泄漏**：
   ```bash
   # 添加连接泄漏检测
   DB_LEAK_DETECTION=30000
   ```

---

## 日志查看方法

### Railway

```bash
# 通过 Dashboard
1. 进入服务页面
2. 点击 "Logs" 标签

# 通过 CLI
railway logs
```

### Render

```bash
# 通过 Dashboard
1. 进入服务页面
2. 点击 "Logs" 标签
3. 选择 "Logs" 或 "Events"

# 通过 CLI
render logs --service smart-exam-system
```

### Docker (本地)

```bash
# 查看所有服务日志
docker-compose logs

# 查看特定服务日志
docker-compose logs backend
docker-compose logs mysql

# 实时日志
docker-compose logs -f backend

# 最近 100 条日志
docker-compose logs --tail=100 backend
```

### 应用日志配置

在环境变量中调整日志级别：

```bash
# 生产环境
LOG_LEVEL=WARN
APP_LOG_LEVEL=INFO

# 调试环境
LOG_LEVEL=DEBUG
APP_LOG_LEVEL=DEBUG
SPRING_WEB_LOG_LEVEL=DEBUG
```

---

## 紧急恢复

### 数据库恢复

```bash
# 从备份恢复
mysql -h host -u user -p database < backup.sql

# PlanetScale 恢复
pscale branch restore smart-exam-db main

# Aiven 恢复
# 在 Aiven Console 中选择 "Backups" → "Restore"
```

### 应用回滚

```bash
# Railway: 在 Deployments 页面选择历史部署重新部署
# Render: 在 Events 页面选择历史部署重新部署
# Docker: 使用之前的镜像版本
docker-compose up -d smart-exam-backend:previous-tag
```

### 快速重启

```bash
# Docker
docker-compose restart backend

# Railway
railway restart

# Render
# 在 Dashboard 中点击 Manual Deploy → Deploy latest commit
```

---

## 相关文档

- [Railway 部署指南](./deploy-railway.md)
- [Render 部署指南](./deploy-render.md)
- [外部数据库服务配置](./external-database.md)
- [项目主 README](../README.md)

---

## 获取帮助

如果以上方案不能解决你的问题：

1. **查看应用日志**获取详细错误信息
2. **搜索 GitHub Issues**查看类似问题
3. **提交新的 Issue**，包含：
   - 使用的平台（Railway/Render/其他）
   - 环境变量配置（隐藏敏感信息）
   - 完整的错误日志
   - 复现步骤

我们会在 24 小时内回复。
