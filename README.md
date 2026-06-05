# Smart Exam System 智慧在线考试与学习反馈系统

本仓库用于第七组《计算机工程综合能力实训》项目“在线考试系统”的开发、测试、答辩展示与实训报告沉淀。

## 项目定位

本系统面向高校课程考核、课堂测验和阶段训练场景，围绕题库管理、试卷管理、考试发布、在线答题、自动评分、教师阅卷、成绩分析、防作弊记录、错题反馈与 AI 辅助学习构建完整在线考试闭环。

## 团队信息

| 分工 | 学号 | 姓名 |
|---|---|---|
| 组长 | 2312402060134 | 陈绍杰 |
| 成员 | 2312402060135 | 张嘉豪 |
| 成员 | 2312402060133 | 黄权达 |
| 成员 | 2312402060128 | 黄浩然 |

## 技术栈规划

| 层次 | 技术 |
|---|---|
| 前端 | Vue 3、Vite、TypeScript、Element Plus、ECharts |
| 后端 | Java 17、Spring Boot 3、Spring Security 或 Sa Token、MyBatis Plus |
| 数据库 | MySQL 8 |
| AI 接入 | OpenAI 兼容接口适配层，可配置 Base URL、模型名、API Key 与模拟响应 |
| 协作 | Git、GitHub、阶段记录、测试记录 |

## 当前阶段

当前处于阶段 1：前后端基础骨架与数据库初始化准备。

本阶段目标：

1. 创建仓库基础目录和说明文档。
2. 创建后端 Spring Boot 最小骨架。
3. 创建前端 Vue 最小骨架。
4. 准备数据库初始化脚本。
5. 预留 AI 配置读取与模拟响应入口。

## 目录结构

```text
smart-exam-system
├─ backend                    # 后端 Spring Boot 项目
├─ frontend                   # 前端 Vue 项目
├─ database                   # 数据库脚本与说明
├─ docs                       # 接口、数据库、AI、测试等文档
├─ 第七组-在线考试系统项目主控文档.md
├─ README.md
├─ .gitignore
└─ LICENSE
```

## 后端快速启动

```bash
cd backend
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

健康检查接口：

```text
GET http://localhost:8080/api/health
```

AI 状态接口：

```text
GET http://localhost:8080/api/ai/status
```

## 前端快速启动

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://127.0.0.1:3000
```

## 数据库初始化

数据库脚本位于 database 目录：

- schema.sql：创建数据库和阶段 1 基础表。
- seed.sql：写入演示角色、账号、科目、班级和 AI 提示词模板。

建议数据库名：

```text
smart_exam_system
```

## 辅助脚本

Windows 环境可使用 [`scripts`](scripts) 目录下的脚本进行环境检查和启动：

```cmd
scripts\check-env.cmd
scripts\run-backend.cmd
scripts\run-frontend.cmd
```

其中 [`scripts/check-env.cmd`](scripts/check-env.cmd) 会检查 Java、Maven、Node.js、npm 和前端依赖状态；[`scripts/run-backend.cmd`](scripts/run-backend.cmd) 用于启动后端；[`scripts/run-frontend.cmd`](scripts/run-frontend.cmd) 用于安装依赖并启动前端。

## AI 接入说明

AI 模块当前只做可配置规划和状态接口预留，不影响系统核心运行。

建议通过环境变量配置：

```text
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_API_KEY=你的密钥
OPENAI_MODEL=gpt-4o-mini
AI_MOCK_ENABLED=true
```

若未配置 API Key，系统应返回未启用或模拟模式状态，核心考试流程不受影响。

## 主控文档

所有后续开发以《第七组-在线考试系统项目主控文档.md》为主线推进，每完成一个阶段都应同步记录进度、验证结果和可写入实训报告的内容。
