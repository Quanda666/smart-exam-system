# 第 78 批：交卷响应不再泄露未发布成绩

## 背景

前几批已经让学生成绩页、错题、画像、导出、分析看板等查询路径遵守 `score_release.status = 1`。继续审计发现，交卷接口本身仍存在响应层泄露：

- 学生正常交卷后，`/api/exams/attempt/{attemptId}/submit` 会直接返回 `score`。
- 重复交卷时，幂等响应从 `exam_submit_response.response_json` 回放，旧响应可能继续携带 `score`。
- 心跳超时自动交卷、教师/管理员强制交卷复用同一提交结果，也可能把未发布成绩带出。

这会绕过“成绩必须发布后学生可见”的规则。即使前端不展示分数，抓包或直接调用接口仍能拿到。

## 本批改动

- `ExamService.finalizeAttempt`
  - 继续计算并持久化 `exam_attempt.score`，供阅卷、发布、统计使用。
  - 提交响应中移除 `score` 字段。
  - 增加 `scoreVisible=false` 与 `scoreVisibility=PENDING_RELEASE`，明确告诉前端成绩暂不可见。
- `ExamService.submittedAttemptResult`
  - 重复提交、心跳发现已提交时，不再查询和返回 `exam_attempt.score`。
- `ExamService.loadSubmitResponse`
  - 对历史 `exam_submit_response.response_json` 做服务端脱敏，读取后强制移除 `score`。
- `ExamService.storeSubmitResponse`
  - 存储幂等响应前也做脱敏，避免新写入记录带入分数。
- 前端 API 类型
  - `frontend/src/api/exam.ts` 的 `submitExam` 和 `forceSubmitAttempt` 响应类型移除 `score`。
  - `frontend/src/api/monitor.ts` 的监考强制交卷响应类型移除 `score`。
- 质量门与验收脚本
  - `run-quality-gates.ps1` 增加源码守护，禁止提交响应重新暴露 `score`。
  - `verify-attempt-resilience.ps1` 增加真实接口断言：首次提交和重复提交均不得包含 `score`。

## 三端协同影响

- 学生端：交卷后只知道“提交成功/已自动交卷/待发布”，不会提前看到客观题自动判分结果。
- 教师端：仍可完成阅卷、发布、撤回等管理动作；强制交卷动作返回流程状态，不返回未发布分数。
- 管理员端：监考强制交卷和审计链路保持可追踪，但成绩可见性继续由发布动作统一控制。

## 验收方式

本地质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

接口验收建议：

1. 学生开始考试并提交答卷。
2. 检查提交响应 JSON，不应存在 `score` 字段，应存在 `scoreVisible=false`。
3. 使用相同 `submitToken` 重复提交，回放响应仍不应存在 `score`。
4. 发布成绩前访问学生成绩页、错题、画像、导出，均不应看到该成绩。
5. 发布成绩后，通过成绩页等正式发布路径查看成绩。

## 后续建议

- 继续审计 `ReviewService`、教师阅卷详情、成绩申诉详情等教师侧内部评分接口，区分“教师批阅所需分数”和“学生可见成绩”。
- 可以把提交响应 DTO 固化成显式类型，替代 `Map<String, Object>`，减少敏感字段误放回响应的概率。
- 后续对 `exam_submit_response` 可加一次迁移清理，批量删除历史 JSON 中的 `score` 字段。
