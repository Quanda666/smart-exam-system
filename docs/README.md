# docs 目录导航

**课程**：Web程序设计课程设计（S3048I）  
**项目**：第二组 - 在线考试系统

本目录存放项目设计与运维文档。按”是否反映当前代码状态”分为两类。

## 当前活文档（权威来源）

### [rebuild/](rebuild/README.md) — 重建开发文档集

从课程设计级项目升级为学院级平台的建设文档，按迭代批次记录能力落地，**是当前进度的权威来源**。

- [阅读顺序与总体目标](rebuild/README.md)
- `01`–`10`：现状审计、领域模型与状态机、权限与三端设计、各端开发、接口与库迁移、测试验收、部署运维、迭代计划。
- `16`–`49`：考试 / 试卷 / 题库 / 审批 / 草稿 / 阅卷 / 成绩 / 监考等领域主线。
- `100`–`441`：按特性拆分的规格与验收条目（监考事件、成绩发布闭环、快照审计、统一审计、资料库 RAG 等）。

## 设计与运维文档

| 文档 | 内容 |
|---|---|
| [ai-design.md](ai-design.md) | AI 功能设计（直接生成、Excel 模板导入、课程资料生成、RAG、错题讲解、阅卷建议） |
| [api-design.md](api-design.md) | 当前 API 分组 |
| [database-design.md](database-design.md) | 当前数据库结构说明 |
| [deliver-integrated-jar.md](deliver-integrated-jar.md) | 交付老师验收说明（Docker Compose 一键启动 + 纯 Jar） |
| [deploy-local.md](deploy-local.md) | 本地验收部署 |
| [deploy-railway.md](deploy-railway.md) | Railway 云部署 |
| [deploy-render.md](deploy-render.md) | Render 云部署 |
| [email-setup.md](email-setup.md) | Resend 邮件配置 |
| [external-database.md](external-database.md) | 外部数据库接入 |
| [test-records.md](test-records.md) | 验证记录 |
| [troubleshooting.md](troubleshooting.md) | 常见问题排查 |

## 历史快照

| 文档 | 说明 |
|---|---|
| [assessment.md](assessment.md) | 2026-06-09 重建前的点时审查记录，仅供回溯；结论已部分被 `rebuild/` 覆盖 |

## 其他

- [一键启动.bat](一键启动.bat)：Windows 一键启动脚本（亦可使用 `scripts/run-all.cmd`）。
- [report-assets/](report-assets/)：报告资源占位目录。
