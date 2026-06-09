# 本地验收部署指南

本文档用于从零在本地跑通智慧在线考试系统。当前项目不再维护 `docs/init.sql`，数据库以 `backend/src/main/resources/db/schema.sql` 和 `backend/src/main/resources/db/data.sql` 为准。

## 环境要求

| 软件 | 版本 | 用途 |
|---|---|---|
| JDK | 17+ | 运行后端 |
| MySQL | 8+ | 存储业务数据 |
| Maven | 3.8+ | 源码方式启动后端 |
| Node.js | 18+ | 构建或开发前端 |

如果使用已经打好的一体化 Jar，Node.js 和 Maven 不是运行时必需。

## 第一步：创建数据库

```sql
CREATE DATABASE IF NOT EXISTS smart_exam_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

后端启动时会自动建表并写入初始化数据。默认连接为：

```text
jdbc:mysql://localhost:3306/smart_exam_system
用户名：root
密码：root
```

如数据库账号不同，启动时覆盖：

```bash
java -jar backend/target/smart-exam-backend-0.1.0-SNAPSHOT.jar \
  --MYSQL_URL="jdbc:mysql://localhost:3306/smart_exam_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
  --MYSQL_USERNAME=root \
  --MYSQL_PASSWORD=你的密码
```

## 第二步：启动系统

### 推荐方式：源码一体化启动

在项目根目录运行：

```cmd
scripts\run-all.cmd
```

脚本会构建前端、复制到 Spring Boot 静态资源目录，并启动后端。启动后访问：

```text
http://localhost:8080
```

### 分离开发方式

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

访问：`http://127.0.0.1:3000`。

### 已打包 Jar 方式

如果交付包中的 Jar 已包含前端静态资源：

```bash
java -jar backend/target/smart-exam-backend-0.1.0-SNAPSHOT.jar
```

访问：`http://localhost:8080`。

## 初始登录

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |

教师和学生通过登录页注册入口创建。验收后建议立即修改管理员密码。

## 手动初始化外部数据库

一般不需要手动导入表结构。如果需要对外部数据库预先初始化，按顺序执行：

```bash
mysql -u root -p smart_exam_system < backend/src/main/resources/db/schema.sql
mysql -u root -p smart_exam_system < backend/src/main/resources/db/data.sql
```

## 验收清单

- 使用管理员账号登录，确认首页菜单为“概况”。
- 进入基础数据，检查班级、课程、课程班、授课分配、学生归属、科目、知识点和公告。
- 在题库管理中新增题目、发布题目、撤回题目。
- 使用 AI 出题台直接生成题目、上传题目文档识别、上传课程资料生成题目。
- 创建试卷并发布考试。
- 注册学生账号，参加考试并交卷。
- 教师批阅主观题，查看 AI 评分建议。
- 学生查看成绩、错题本和 AI 错题讲解。
- 管理员查看用户管理、角色管理、系统日志和异常事件。

## 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `Unknown database 'smart_exam_system'` | 未创建数据库 | 执行本文第一步的 `CREATE DATABASE` |
| `Access denied for user 'root'` | MySQL 用户或密码不匹配 | 覆盖 `MYSQL_USERNAME` 和 `MYSQL_PASSWORD` |
| 8080 端口占用 | 本地已有服务 | 启动时增加 `--server.port=9090` |
| 前端无法请求后端 | 后端未启动或 API 地址不对 | 开发模式检查 Vite proxy，分离部署配置 `VITE_API_BASE_URL` |
| AI 无真实输出 | 未配置 API Key 或启用模拟模式 | 配置 `OPENAI_API_KEY` 并设置 `AI_MOCK_ENABLED=false` |
| 邮箱验证码发不出 | 未配置 Resend | 配置 `RESEND_API_KEY` 和 `RESEND_FROM_EMAIL` |

## 重置数据

如需清空数据重新验收：

```sql
DROP DATABASE smart_exam_system;
CREATE DATABASE smart_exam_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

再次启动后端即可自动重建表并写入初始数据。
