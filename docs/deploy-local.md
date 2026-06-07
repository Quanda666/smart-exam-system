# 智慧在线考试系统 — 本地验收部署指南

> 面向验收老师的「一键交付」方案：拿到 **源码 + jar + init.sql**，5 分钟即可跑通整套系统，无需配置开发环境。

---

## 一、环境要求

| 软件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 及以上 | 运行后端 jar 包，命令行输入 `java -version` 可检查 |
| MySQL | 8.0 及以上 | 存储数据，需能用 root 账号登录 |
| 浏览器 | Chrome / Edge 等现代浏览器 | 访问前端页面 |

> 前端为纯静态文件（已打包好的 `dist`），**无需安装 Node.js**。

---

## 二、交付物清单

老师收到的交付包应包含以下内容：

```
smart-exam-system/
├── backend/target/smart-exam-backend-0.1.0-SNAPSHOT.jar   ← 后端可执行包
├── frontend/dist/                                         ← 前端静态文件（打开即用）
├── docs/init.sql                                          ← 数据库一键初始化脚本
├── docs/deploy-local.md                                   ← 本文档
└── docs/一键启动.bat                                       ← Windows 一键启动脚本（可选）
```

> 若交付包中没有 jar 或 dist，请先按「附录 A：如何打包」自行生成。

---

## 三、部署步骤（5 分钟）

### 第 1 步：初始化数据库

打开命令行（cmd / PowerShell / 终端），执行：

```bash
mysql -u root -p < docs/init.sql
```

输入 MySQL 的 root 密码后，脚本会自动：
- 创建数据库 `smart_exam_system`（utf8mb4 编码）
- 建好全部 27 张表
- 写入初始数据（管理员账号、基础科目、知识点等）

> 该脚本可重复执行，不会报错、不会覆盖已有数据。

### 第 2 步：启动后端

```bash
java -jar backend/target/smart-exam-backend-0.1.0-SNAPSHOT.jar
```

看到日志输出 `Started SmartExamApplication ... ` 即表示启动成功，后端运行在 **8080 端口**。

> ⚠️ 如果你的 MySQL 密码不是默认的 `root`，请看「四、启动方式」中的自定义配置。

### 第 3 步：访问前端

任选一种方式打开前端：

- **方式 A（最简单）**：进入 `frontend/dist/` 目录，双击 `index.html` 用浏览器打开
- **方式 B（推荐）**：用任意静态服务器托管 `frontend/dist`，例如：
  ```bash
  cd frontend/dist
  python -m http.server 5173
  ```
  然后浏览器访问 `http://localhost:5173`

### 第 4 步：登录系统

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |

> 教师、学生账号可通过登录页的「注册」入口自行创建；管理员登录后请尽快修改默认密码。

---

## 四、启动方式（三种）

### 方式 1：默认配置（开箱即用）

适用于 MySQL 安装在本机、root 密码为 `root`、端口 3306 的标准环境：

```bash
java -jar smart-exam-backend-0.1.0-SNAPSHOT.jar
```

### 方式 2：自定义端口

8080 端口被占用时，改用其它端口（如 9090）：

```bash
java -jar smart-exam-backend-0.1.0-SNAPSHOT.jar --server.port=9090
```

### 方式 3：自定义数据库连接

MySQL 用户名/密码/地址与默认不同时，启动时覆盖配置：

```bash
java -jar smart-exam-backend-0.1.0-SNAPSHOT.jar \
  --MYSQL_URL="jdbc:mysql://localhost:3306/smart_exam_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
  --MYSQL_USERNAME=root \
  --MYSQL_PASSWORD=你的密码
```

> 后端通过环境变量/启动参数读取配置，默认值见 `backend/src/main/resources/application.yml`：
> - 数据库地址：`MYSQL_URL`（默认 `jdbc:mysql://localhost:3306/smart_exam_system?...`）
> - 用户名：`MYSQL_USERNAME`（默认 `root`）
> - 密码：`MYSQL_PASSWORD`（默认 `root`）
> - 端口：`SERVER_PORT` / `PORT`（默认 `8080`）

---

## 五、验收测试清单

启动成功后，可按以下流程走通核心业务，验证系统完整性：

- [ ] 用 `admin / admin123` 登录管理员后台
- [ ] 进入「科目管理」，查看已有科目（Java程序设计、数据库系统）
- [ ] 新建一道题目（单选/多选/判断/填空/主观题任一）
- [ ] 进入「试卷管理」，组一份试卷并发布
- [ ] 进入「考试管理」，基于试卷创建并发布一场考试
- [ ] 退出登录，用「注册」入口创建一个学生账号
- [ ] 学生登录 → 参加考试 → 作答 → 交卷
- [ ] 客观题自动判分，成绩可在「成绩/记录」中查看
- [ ] （如有主观题）教师/管理员进入阅卷，完成评分

---

## 六、常见问题排查

| 现象 | 原因 | 解决办法 |
| --- | --- | --- |
| 后端启动报 `Access denied for user 'root'` | MySQL 密码与默认值 `root` 不符 | 用「方式 3」指定 `--MYSQL_PASSWORD=你的密码` |
| 后端启动报 `Unknown database 'smart_exam_system'` | 没有先执行 init.sql 建库 | 回到第 1 步执行 `mysql -u root -p < docs/init.sql` |
| 后端启动报 `Communications link failure` | MySQL 服务未启动 | 启动 MySQL 服务（Windows 服务面板 / `net start mysql`） |
| 后端启动报端口被占用 `Port 8080 ... in use` | 8080 端口被其它程序占用 | 用「方式 2」改端口：`--server.port=9090` |
| 前端页面打开后无法登录、控制台报跨域/网络错误 | 后端未启动，或前端请求地址与后端端口不一致 | 确认后端已启动；前端默认请求 `http://localhost:8080`，若改了后端端口需对应调整 |
| 页面中文显示乱码 | 数据库非 utf8mb4 编码 | init.sql 已强制 utf8mb4；若是旧库请删库后重新执行 init.sql |

> 更多问题见 [troubleshooting.md](troubleshooting.md)。

---

## 七、技术亮点（交付说明）

这套交付方式对验收非常友好，核心设计如下：

1. **零环境变量配置** — `application.yml` 内置全部默认值，标准环境下 `java -jar` 直接可跑。
2. **数据库自动初始化** — 后端基于 Spring Boot `spring.sql.init.mode=always`，每次启动会自动执行建表/初始数据脚本（幂等，不重复写入）。即便不手动跑 init.sql，只要库已存在，后端也会自动补齐表结构。
3. **前端纯静态交付** — `dist` 为构建产物，浏览器直接打开即用，不依赖 Node.js 运行时。
4. **脚本幂等可重入** — `init.sql` 中所有建表为 `IF NOT EXISTS`、所有数据为 `INSERT IGNORE`，反复执行不会报错、不会破坏已有数据。
5. **优雅停机** — 后端启用 graceful shutdown，`Ctrl + C` 可安全停止，不丢正在处理的请求。

---

## 附录 A：如何打包（开发者）

若交付包中缺少 jar 或 dist，按下述命令生成。

### 打包后端 jar

```bash
cd backend
mvn clean package -DskipTests
```

产物：`backend/target/smart-exam-backend-0.1.0-SNAPSHOT.jar`

### 构建前端 dist

```bash
cd frontend
npm install
npm run build
```

产物：`frontend/dist/`

---

## 附录 B：停止与重启

- **停止后端**：在运行 jar 的命令行窗口按 `Ctrl + C`
- **重启后端**：重新执行 `java -jar ...` 即可，数据保留在 MySQL 中不会丢失
- **重置数据**：如需清空全部数据从头验收，删除数据库后重新执行 init.sql：
  ```sql
  DROP DATABASE smart_exam_system;
  ```
  然后 `mysql -u root -p < docs/init.sql` 重新初始化。
