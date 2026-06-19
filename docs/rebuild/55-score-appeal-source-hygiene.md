# 55. Score Appeal Source Hygiene

## 目标

本批次回到成绩发布后的学生申诉闭环，先修复会直接影响申诉接口可用性的源码问题：

- 修复申诉请求 DTO 的乱码校验提示和未闭合字符串。
- 修复申诉回复 DTO 的乱码校验提示和未闭合字符串。
- 修复学生提交申诉、教师处理申诉接口的成功消息。
- 增加 Java 源码卫生检查脚本，先把申诉闭环关键文件纳入本地质量门。

## 修复范围

涉及文件：

- `backend/src/main/java/com/smartexam/dto/student/ScoreAppealRequest.java`
- `backend/src/main/java/com/smartexam/dto/review/ScoreAppealReplyRequest.java`
- `backend/src/main/java/com/smartexam/controller/StudentController.java`
- `backend/src/main/java/com/smartexam/controller/ReviewController.java`

修复后：

- 学生提交申诉参数校验提示为干净中文。
- 教师回复申诉参数校验提示为干净中文。
- `POST /api/student/appeals` 返回 `申诉已提交`。
- `POST /api/reviews/appeals/{id}/reply` 返回 `申诉已处理`。

## 新增检查

新增脚本：

```powershell
scripts\check-java-source-hygiene.ps1
```

检查内容：

- 明显 mojibake 标记。
- 非 text block 行的未配对双引号。

本地质量门先定向检查申诉闭环关键文件：

```powershell
scripts\run-quality-gates.ps1
```

这样可以防止本批次修复过的申诉接口再次退化，同时避免一次性把历史全量乱码债务混进当前改动。

## 已发现的历史债务

全量运行 Java 源码卫生检查会暴露大量历史乱码和断裂字符串，典型位置包括：

- `OperationLogAspect`
- `AuthController`
- `ExamController`
- 部分 AI DTO
- 部分用户与系统配置 DTO

这些问题不属于第 55 步一次性清理范围。后续应按模块分批修复，并逐步扩大 `check-java-source-hygiene.ps1` 在质量门中的覆盖范围。

## 验收标准

- 申诉相关 4 个 Java 文件通过源码卫生检查。
- 本地质量门会执行该定向检查。
- 不改变申诉业务逻辑和接口路径。
- 全量历史乱码债务被记录，不在本批次静默忽略。

## 后续增强

- 第 56 步建议清理认证与操作日志模块的乱码字符串，因为它们会影响系统日志和错误提示可信度。
- 清理一批模块后，将对应文件加入质量门定向列表。
- 当全量后端 Java 文件通过检查后，把质量门切换为全量 `SourceRoot` 检查。
