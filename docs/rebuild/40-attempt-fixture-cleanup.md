# 40. 作答可靠性测试夹具清理

## 目标

- 给第 38-39 批的作答可靠性测试夹具补上可控清理能力。
- 避免测试环境 nightly/CI 长期堆积 disposable 学生、考试、答卷、监考事件和快照数据。
- 清理入口继续受 `system.testFixtureEnabled` 保护，生产环境默认不可用。

## 新增接口

- `DELETE /api/exams/attempt-resilience/fixtures`

权限：

- 仅管理员可调用。
- `system.testFixtureEnabled=true` 时才允许执行或预览。

查询参数：

- `olderThanHours`：只处理早于当前时间指定小时数的夹具数据，默认 24。
- `studentPrefix`：只处理用户名匹配该前缀的测试学生及其考试，默认 `verify_student`。
- `dryRun`：是否只预览不执行，默认 `true`。

示例：

```http
DELETE /api/exams/attempt-resilience/fixtures?olderThanHours=24&studentPrefix=verify_student&dryRun=true
```

## 清理范围

入口先按以下条件圈定考试：

- `exam.exam_name LIKE 'Attempt Resilience Fixture Exam %'`
- `exam.description = 'Fixture exam for attempt resilience acceptance tests'`
- 考试创建时间早于 `olderThanHours` 截止时间
- 考生快照中的学生用户名匹配 `studentPrefix`

随后只处理这些考试关联的数据：

- 监考处置、监考事件、监考会话
- 成绩申诉、成绩发布
- 答题记录、草稿、提交响应快照、attempt
- 考试目标、考生快照、题目快照、审批日志
- 考试逻辑关闭并删除
- 试卷题目关联、试卷逻辑删除
- 题目版本、版本选项、题目选项、题目逻辑删除
- 测试学生档案、班级关系、选课关系、角色关系、用户逻辑删除

基础科目和测试班级暂不删除，因为它们是稳定复用的夹具底座，不会随每次验收线性增长。

## 新增脚本

- `scripts/cleanup-attempt-resilience-fixtures.ps1`

默认只预览：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\cleanup-attempt-resilience-fixtures.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123
```

执行清理：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\cleanup-attempt-resilience-fixtures.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -OlderThanHours 24 `
  -Execute
```

清理某次一键验收创建的学生：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\cleanup-attempt-resilience-fixtures.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -StudentPrefix verify_student_20260617120000_abcd1234 `
  -OlderThanHours 0 `
  -Execute
```

## 与一键验收管线协同

`scripts/run-attempt-resilience-acceptance.ps1` 新增：

- `-CleanupAfterRun`
- `-CleanupOlderThanHours`

CI/nightly 可使用：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -CleanupAfterRun
```

默认不自动清理，保留失败现场；只有显式传入 `-CleanupAfterRun` 才会在验收成功后清理本次 disposable 数据。

## 验收点

- 未开启 `system.testFixtureEnabled` 时，清理接口拒绝执行。
- `dryRun=true` 只返回匹配 ID 和数量，不修改数据。
- `studentPrefix` 精确到本次自动生成学生名时，只清理该学生关联的夹具考试。
- 执行后，考试、试卷、题目、测试学生不再出现在正常业务列表。
- 监考事件、草稿、提交响应、快照等派生数据不再持续堆积。
