# 第 80 批：成绩发布必须等待已开始答卷全部最终完成

## 背景

成绩发布是学生可见成绩、错题、画像、导出和分析指标的统一开关。此前后端已经要求：

- 考试已关闭或结束时间已过。
- 没有进行中答卷。
- 没有待批阅答卷。
- 至少存在一份已完成答卷。

继续审计发现，发布前置条件仍可更严：如果历史或异常数据中存在 `status=2` 这类“已提交但未最终判分”的中间态，原逻辑只检查 `status=1` 和 `status=4`，理论上可能在仍有非最终答卷时发布成绩。

## 本批改动

- `ExamService.requireExamReadyForScoreRelease`
  - 新增 `nonFinalStartedAttemptCount` 校验。
  - 除未开始的缺考答卷 `status=0` 外，所有已开始答卷必须为最终完成态 `status=5`。
  - 如果仍存在非最终已开始答卷，拒绝发布并返回 `Scores cannot be published while started attempts are not finalized`。
- `ExamService.listTeacherExams`
  - 新增列表字段：
    - `completedAttemptCount`
    - `pendingReviewAttemptCount`
    - `nonFinalStartedAttemptCount`
  - 前端可据此准确判断成绩是否达到发布条件。
- `ExamManagement.vue`
  - “发布成绩”按钮增加禁用和原因提示。
  - 考试未结束、仍有作答中、仍有待批阅、仍有非最终答卷、没有完成答卷时均不可点击。
- `frontend/src/api/exam.ts`
  - `ExamInfo` 补充上述状态统计字段。
- `scripts/run-quality-gates.ps1`
  - 增加源码守护，确保后端发布成绩校验和前端按钮约束不被移除。

## 状态口径

- `status=0`：未开始/缺考，可以不阻塞成绩发布。
- `status=1`：作答中，必须阻塞成绩发布。
- `status=2`：提交中/中间态，必须阻塞成绩发布。
- `status=4`：待批阅，必须阻塞成绩发布。
- `status=5`：已完成判分，可以进入成绩发布。

## 三端协同影响

- 教师端：发布按钮不再盲点触发，列表上可提前看到发布条件是否满足。
- 管理员端：管理员代管考试时同样受后端硬约束，不会发布半成品成绩。
- 学生端：只会在所有已开始答卷完成判分后，通过正式发布动作看到成绩。

## 验收方式

本地质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

人工验收建议：

1. 创建一场考试并让学生进入作答中，尝试发布成绩，应被拒绝。
2. 学生交卷但存在待批阅主观题时，发布按钮应禁用，直接调接口也应拒绝。
3. 全部待批阅题批完后，且至少存在一份 `status=5` 答卷，发布按钮可用。
4. 发布后学生端成绩、错题、画像和导出路径可见。
5. 撤回后学生端再次隐藏。

## 后续建议

- 将 `AttemptStatus` 正式抽成枚举，减少 `0/1/2/4/5` 魔法数字散落。
- 增加接口级验收：构造 `status=2` 异常答卷，验证发布成绩被拒绝。
- 在考试列表增加“成绩发布准备度”列，直接显示完成、待阅、进行中、异常中间态数量。
