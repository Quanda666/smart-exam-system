# 56. Auth And Operation Log Source Hygiene

## 目标

本批次继续清理历史乱码债务，优先修复认证与操作日志模块，因为它们直接影响：

- 用户未登录、无权限、登录锁定等关键错误提示。
- 管理端操作日志的动作名称和模块名称。
- Token 会话恢复失败时的后端日志可读性。
- 后续排查越权访问、重复提交、系统异常时的审计可信度。

## 修复范围

本批次清理：

- `backend/src/main/java/com/smartexam/aspect/OperationLogAspect.java`
- `backend/src/main/java/com/smartexam/aspect/RequireRolesAspect.java`
- `backend/src/main/java/com/smartexam/auth/AuthContext.java`
- `backend/src/main/java/com/smartexam/auth/AuthFilter.java`
- `backend/src/main/java/com/smartexam/auth/LoginAttemptGuard.java`
- `backend/src/main/java/com/smartexam/auth/TokenStore.java`

## 业务效果

认证与权限提示恢复为可读中文：

- `当前请求未携带有效登录态`
- `请先登录后再访问该接口`
- `当前账号角色为 ...，不能访问该接口`
- `登录失败次数过多，账号已临时锁定，请 15 分钟后再试`

操作日志动作和模块恢复为可读中文：

- 动作：`新增`、`更新`、`删除`、`操作`
- 模块：`班级`、`科目`、`知识点`、`公告`、`题目`、`试卷`、`考试`、`阅卷`、`监控`、`数据`

Token 校验异常日志恢复为：

```text
Token 校验失败，按未登录处理: ...
```

## 质量门扩展

`scripts/run-quality-gates.ps1` 中的 `check-java-source-hygiene.ps1` 覆盖范围从申诉闭环 4 个文件扩展到 10 个文件：

- 第 55 步申诉闭环文件。
- 第 56 步认证与操作日志文件。

当前仍采用定向覆盖，而不是全量覆盖，因为全量后端源码仍存在历史乱码债务。每清理一个模块，就应把该模块文件加入定向列表，直到最终切换为全量检查。

## 验收标准

- 本批 6 个 Java 文件无明显 mojibake 标记。
- 本批 6 个 Java 文件无单行未配对双引号。
- 定向 Java hygiene 检查覆盖 10 个文件。
- 本地质量门包含该定向检查。

## 后续增强

- 清理 `AuthController`、用户 DTO 和 AI DTO 中的历史乱码提示。
- 修复 `OperationLogAspect` 后可在端到端验收中检查操作日志动作名称。
- 最终将 Java hygiene 检查从定向文件列表切换为 `backend/src/main/java` 全量检查。
