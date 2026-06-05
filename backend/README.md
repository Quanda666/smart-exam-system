# 后端服务

本目录为智慧在线考试与学习反馈系统后端服务，阶段 1 先提供最小 Spring Boot 骨架。

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8，可选，阶段 1 未启动数据库时后端仍应能启动，健康接口会返回数据库未连接状态。

## 启动命令

```bash
mvn spring-boot:run
```

## 默认接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/health | 健康检查，包含应用、时间和数据库连通状态 |
| GET | /api/ai/status | AI 配置状态，返回模拟模式、模型名、Base URL 和密钥配置状态，不返回明文密钥 |

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| SERVER_PORT | 8080 | 后端端口 |
| MYSQL_URL | jdbc:mysql://localhost:3306/smart_exam_system | 数据库连接地址 |
| MYSQL_USERNAME | root | 数据库用户名 |
| MYSQL_PASSWORD | root | 数据库密码 |
| OPENAI_BASE_URL | https://api.openai.com/v1 | OpenAI 兼容接口地址 |
| OPENAI_API_KEY | 空 | AI 密钥，不配置时 AI 处于未配置或模拟状态 |
| OPENAI_MODEL | gpt-4o-mini | 默认模型 |
| AI_MOCK_ENABLED | true | 是否启用 AI 模拟响应 |

## 阶段说明

阶段 1 不实现完整业务，只完成项目可启动、接口可访问、数据库连接可检查、AI 配置可读取。
