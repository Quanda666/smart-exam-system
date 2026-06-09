# 测试与验证记录

最近更新：2026-06-09

## 当前验证范围

| 模块 | 覆盖点 |
|---|---|
| 后端编译 | Controller、Service、DTO、数据库配置、AI 上传接口、AI 日志接口 |
| 前端类型检查 | Vue 组件、API 类型、AI 出题台、学生错题讲解、系统日志页 |
| 前端构建 | Vite 生产构建 |
| 文档一致性 | 旧接口、旧初始化脚本、旧 SMTP 配置、阶段占位文案 |

## 本轮验证命令

后端编译使用 JDK 17 对 `backend/src/main/java` 进行全量编译，依赖来自现有 Spring Boot jar 的 `BOOT-INF/lib`。

前端验证命令：

```bash
cd frontend
npx vue-tsc --noEmit
npx vite build --outDir dist-codex-verify --emptyOutDir
```

源码死代码复查命令：

```bash
rg -n "admin/overview|teacher/overview|student/overview|ai/generate-question|ai/explain|student-deny-check|RoleOverview|DiagnosticController|GenerateQuestionRequest|ExplainRequest" backend/src/main/java frontend/src
rg -n "SPRING_MAIL|spring-boot-starter-mail|app.mail|JavaMailSender" backend/src/main backend/pom.xml .env.example
```

文档中如出现旧接口或旧文件名，仅用于说明“已删除/已移除”，不再作为可用接口或交付物描述。

## 验证结果

| 项目 | 状态 | 备注 |
|---|---|---|
| 后端 Java 编译 | 通过 | 使用本机 Maven 路径执行 `mvn -q -DskipTests compile` |
| 前端类型检查 | 通过 | `npx vue-tsc --noEmit` |
| 前端生产构建 | 通过 | 使用 `dist-codex-verify` 临时目录构建，构建后已清理 |
| 旧接口搜索 | 通过 | 源码中未发现旧接口实现 |
| 文档旧配置搜索 | 通过 | SMTP 死配置和旧 mail 依赖已移除 |
| 后端集成测试 | 环境阻塞 | 已执行 `mvn -q test`，失败原因是本机 `localhost:3306` MySQL 连接被拒绝，Spring 测试上下文无法初始化 |

## 2026-06-09 增强验证

| 增强项 | 验证 |
|---|---|
| 角色概况接口测试 | 测试代码已从旧 `/api/admin/overview` 更新为 `/api/overview/admin` |
| 密码哈希升级 | 后端编译通过；历史 SHA-256 兼容逻辑保留，密码登录成功后自动迁移到 PBKDF2 |
| AI 调用审计 | 后端编译通过；远程调用、本地模拟和规则兜底都会写入 `ai_usage_log` |
| AI 日志可视化 | 后端编译与前端类型检查通过；系统日志页新增 AI 调用日志、场景筛选和成功状态筛选 |
| AI 题目来源追踪 | 后端编译与前端类型检查通过；AI 草稿保存后保留来源类型和来源说明 |
| 考试草稿保存状态 | 前端类型检查与生产构建通过；答题页新增保存状态、手动保存和失败重试 |

## 人工验收路径

1. 管理员登录 `admin / admin123`，确认菜单第一项为“概况”。
2. 进入基础数据，检查班级、课程、课程班、授课分配、学生归属、科目、知识点和公告。
3. 进入题库管理，使用 AI 出题台直接生成题目并保存草稿。
4. 上传题目文档，确认识别结果可预览、可微调、可保存。
5. 上传课程资料，分别设置题型数量，确认 AI 生成关联题目。
6. 创建试卷、发布考试，学生进入考试中心作答并交卷。
7. 教师阅卷主观题，调用 AI 评分建议后提交分数。
8. 学生查看成绩和错题本，调用错题 AI 讲解。
9. 管理员查看系统日志、AI 调用日志和异常事件。
