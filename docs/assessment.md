# 项目审查与清理记录

审查日期：2026-06-09

## 结论

本次审查重点处理三类问题：旧文档和真实代码不一致、早期占位接口仍残留、AI 能力描述落后于当前实现。项目现在的文档、接口说明、数据库说明和部署说明已统一到当前代码状态。

## 已清理的死代码

| 类型 | 文件或内容 | 处理 |
|---|---|---|
| 旧概览接口 | `RoleOverviewController`, `RoleOverviewResponse` | 删除 |
| 诊断接口 | `DiagnosticController`, `/api/diagnostic/smtp-test` 放行规则 | 删除 |
| 旧 AI DTO | `GenerateQuestionRequest`, `ExplainRequest` | 删除 |
| 旧 AI 接口 | `/api/ai/generate-question`, `/api/ai/explain` | 删除 |
| 题库越权测试接口 | `/api/questions/student-deny-check` | 删除 |
| 旧邮件服务 | `EmailService` | 删除 |
| SMTP 死配置 | `spring-boot-starter-mail`, `app.mail` 配置 | 删除 |
| 前端旧概览占位 | `App.vue` 中旧角色 overview fallback | 删除 |
| 过期计划文档 | `plans/online-exam-refactor-plan.md` | 删除 |
| 过期初始化脚本 | `docs/init.sql` | 删除 |

## 本轮增强

| 能力 | 处理 |
|---|---|
| 认证安全 | 密码哈希升级为 PBKDF2，兼容历史 SHA-256 并在登录成功后自动迁移 |
| AI 审计 | AI 远程调用、本地模拟和规则兜底都会写入 `ai_usage_log`，系统日志页可筛选查看 |
| AI 来源追踪 | AI 草稿保存到题库时写入来源类型和来源说明 |
| 考试可靠性 | 在线答题页展示草稿保存状态，保存失败自动重试 |
| 回归测试 | 角色概况测试改为当前 `/api/overview/*` 接口 |

## 已更新的关键文档

- `README.md`
- `backend/README.md`
- `frontend/README.md`
- `database/README.md`
- `docs/ai-design.md`
- `docs/api-design.md`
- `docs/database-design.md`
- `docs/deploy-local.md`
- `docs/test-records.md`
- `第七组-在线考试系统项目主控文档.md`

云部署文档中的旧 SMTP 配置也已改为 Resend HTTP API 方案。

## 当前有效能力

- 三端菜单第一项均为“概况”。
- 基础数据页覆盖班级、课程、课程班、授课分配、学生归属、科目、知识点和公告。
- AI 出题台支持直接生成、上传题目文档识别、上传课程资料按题型数量生成题目。
- 题库列表和导出支持查看题目来源，区分手动题、AI 生成、文档识别和材料生成。
- 学生错题本支持 AI 讲解。
- 教师阅卷支持 AI 评分建议。
- 邮箱验证码发送使用 Resend HTTP API。
- 数据库初始化以 `schema.sql` 和 `data.sql` 为唯一来源。

## 保留风险

| 风险 | 说明 | 建议 |
|---|---|---|
| 文档解析边界 | 扫描版 PDF、图片型文档和复杂旧 Word/PPT 文档无法保证完整识别 | 后续可接入 OCR 或专业文档解析库 |
| Token 会话 | 当前 Token 由后端会话表/服务管理，适合单体部署 | 多实例部署时建议统一为 Redis/JWT/Sa Token |
| AI 输出稳定性 | 大模型输出仍可能格式漂移 | 保持人工确认流程，并增加 AI 结果保存前校验 |
| 前端路由 | 当前为轻量路径状态控制，尚未使用 Vue Router | 页面继续增长时建议引入正式路由 |
| 构建产物锁定 | Windows 下 `frontend/dist/logo.png` 可能被系统或浏览器占用 | 验证构建时可使用临时 outDir |

## 后续建议

- 为 AI 文档导入和保存草稿补充端到端测试用例。
- 为基础数据复杂关系增加更多权限和数据范围测试。
- 按页面拆分前端 chunk，降低首屏下载体积。
