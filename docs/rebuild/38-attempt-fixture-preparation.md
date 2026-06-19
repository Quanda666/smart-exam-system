# 38. 作答可靠性测试夹具准备

## 目标

- 将作答可靠性验收从“发现候选 attempt”推进到“测试环境一键准备 attempt”。
- 自动创建学生、班级、科目、题目、试卷、考试、快照和未开始 attempt。
- 通过系统配置开关保护造数入口，避免生产环境误用。

## 后端接口

新增管理员接口：

- `POST /api/exams/attempt-resilience/fixture`

请求体可选：

```json
{
  "studentUsername": "verify_student",
  "studentPassword": "student123",
  "studentName": "Resilience Test Student",
  "subjectName": "Attempt Resilience Verification",
  "className": "Attempt Resilience Test Class",
  "classCode": "VERIFY-RESILIENCE",
  "durationMinutes": 120
}
```

返回：

- `studentUsername`
- `studentPassword`
- `studentUserId`
- `classId`
- `subjectId`
- `questionId`
- `paperId`
- `examId`
- `attemptId`
- `durationMinutes`

## 安全开关

新增默认配置：

- `system.testFixtureEnabled=false`

接口只有在该配置为 `true` 时可用。生产环境保持默认关闭，测试环境由管理员显式打开。

## 造数内容

- 复用或创建测试科目。
- 复用或创建测试班级。
- 复用或创建测试学生，并重置为指定密码。
- 创建一题已审核通过的单选题。
- 创建题目版本和选项版本。
- 创建已发布试卷。
- 创建当前可考的已发布考试。
- 写入考试目标、试卷快照、考生快照。
- 创建未开始 attempt。

## 新增脚本

- `scripts/prepare-attempt-resilience-fixture.ps1`

示例：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\prepare-attempt-resilience-fixture.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -EnableFixtureConfig
```

脚本会输出新建的 `attemptId`，并打印可继续执行的验收命令。

## 串联验收

准备夹具后运行非破坏验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-attempt-resilience.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AttemptId <attemptId> `
  -Username verify_student `
  -Password student123 `
  -AdminUsername admin `
  -AdminPassword admin123
```

完整提交回放验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-attempt-resilience.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AttemptId <attemptId> `
  -Username verify_student `
  -Password student123 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -Submit
```

## 下一批建议

- 增加清理测试夹具接口或脚本，支持按前缀清理测试考试、试卷、题目和用户。
- 将准备脚本、验收脚本组合成测试环境 smoke pipeline。
