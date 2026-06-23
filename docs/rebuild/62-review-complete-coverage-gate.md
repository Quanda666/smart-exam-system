# 62. Review Complete Coverage Gate

## 目标

本批补强教师阅卷到成绩发布之间的闭环。

系统已有基础保护：阅卷详情和提交接口会校验授课范围，单题评分不能超过题目分值，成绩发布前会拒绝仍处于待批阅状态的答卷。但审计发现，阅卷提交接口允许直接调用 API 只提交部分待批阅题目。前端页面虽然要求所有题打分后才能提交，但服务端不应依赖页面约束。

本批将“待批阅答卷必须一次提交完整批阅结果”下沉到服务端。

## 后端调整

涉及文件：

- `backend/src/main/java/com/smartexam/service/ReviewService.java`

`submitReview(attemptId, reviews, reviewer)` 新增服务端校验：

- 加载当前答卷所有 `review_status = 0` 的待批阅答案记录。
- 请求体不能为空。
- 每个批阅项必须包含 `answerRecordId` 和 `score`。
- 同一个 `answerRecordId` 不能重复提交。
- 每个 `answerRecordId` 必须属于当前答卷且处于待批阅状态。
- 提交项必须完整覆盖当前所有待批阅答案记录。
- 分数不能超过该题 `maxScore`。

只有全部校验通过后，才写入：

- `review_record`
- `answer_record.score`
- `answer_record.review_status = 1`
- `exam_attempt.score`
- `exam_attempt.status`

提交结果新增返回：

- `reviewedCount`
- `pendingCount`
- `status`
- `score`

## 前端契约

涉及文件：

- `frontend/src/api/review.ts`

新增 `ReviewSubmitResult` 类型，替换原来的 `any` 返回值：

- `success`
- `message`
- `reviewedCount`
- `pendingCount`
- `status`
- `score`

当前教师端页面行为保持不变，仍然要求所有题目评分后提交；后续可以基于这些字段展示批阅完成状态或跳转提示。

## 与成绩发布的协同

本批增强后，阅卷闭环更加明确：

1. 学生交卷后，主观题或需人工复核题进入 `exam_attempt.status = 4`。
2. 教师只能提交完整覆盖所有待批阅题目的批阅结果。
3. 所有待批阅题完成后，答卷进入 `status = 5`。
4. 成绩发布接口继续拒绝存在 `status = 4` 的答卷。
5. 学生成绩、错题、掌握度和首页成绩指标继续只读取已发布成绩。

这样可以避免“半张卷已批完但状态被误推进”或“绕过前端提交部分批阅”的风险。

## 三端影响

管理员端：

- 成绩发布前置条件更加可信，发布动作不依赖教师端页面行为。

教师端：

- 批阅接口和页面规则保持一致。
- 直接调用接口部分提交会被拒绝，防止批阅进度和答卷状态不一致。

学生端：

- 成绩发布后看到的分数基于完整批阅结果。
- 未完整批阅的答卷不会被误发布。

## 验收标准

- Java 全量源码卫生检查通过。
- 前端源码卫生检查通过。
- PowerShell 脚本语法解析通过。
- 完整本地质量门禁通过。
- 服务端拒绝重复批阅项、非当前答卷待批阅项、缺少任一待批阅项、超出题目分值的批阅请求。

## 后续增强

- 增加真实后端集成测试：部分批阅失败，完整批阅成功，发布成绩成功。
- 为 `answer_record` 增加题型、题目分值和题目序号快照字段，减少阅卷查询对题库/试卷关系的依赖。
- 在教师端展示服务端返回的 `reviewedCount`、`pendingCount` 和最终答卷状态。
