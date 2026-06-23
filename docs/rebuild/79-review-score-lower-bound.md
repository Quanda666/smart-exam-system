# 第 79 批：阅卷评分服务端下限兜底

## 背景

阅卷闭环已经具备几个关键约束：

- 批阅提交必须覆盖当前答卷全部 `review_status = 0` 的待批阅答案。
- 批阅分数不能超过题目分值。
- 批阅完成后重新汇总 `answer_record.score`，并更新 `exam_attempt.score/status`。
- 成绩发布接口会拒绝仍存在待批阅答卷的考试。

继续审计发现，前端阅卷输入框设置了 `min=0`，但服务端 `ReviewService.submitReview` 没有显式拒绝负分。`ReviewRequest` 虽有 `@DecimalMin`，但接口接收的是 `List<ReviewRequest>`，集合元素校验在不同框架配置下容易失效；真实项目里不能只依赖前端或注解兜底。

## 本批改动

- `ReviewService.submitReview`
  - 在服务层增加 `review.getScore().compareTo(BigDecimal.ZERO) < 0` 校验。
  - 负分直接拒绝，错误信息为 `Review score cannot be negative`。
  - 保留原有“必须覆盖全部待批阅答案”和“不能超过题目满分”的校验。
- `scripts/run-quality-gates.ps1`
  - 增加 `ReviewService` 源码守护：
    - 必须有负分校验。
    - 必须保留全量覆盖校验。
    - 必须保留上限校验。

## 三端协同影响

- 教师端：正常页面操作无变化，输入框原本就限制了非负分。
- 管理员端：管理员代批或全局批阅时同样受服务端规则保护。
- 学生端：避免恶意或错误请求产生负分成绩，成绩发布后的可见结果更可信。

## 验收方式

本地质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

人工接口验收建议：

1. 准备一份含主观题、进入 `status=4` 的答卷。
2. 直接调用 `POST /api/reviews/attempt/{attemptId}`，给某个 `answerRecordId` 提交 `score=-1`。
3. 接口应拒绝，并返回 `Review score cannot be negative`。
4. 改为 `0 <= score <= maxScore` 且覆盖所有待批阅答案，应能批阅成功。
5. 批阅成功后考试成绩仍需发布后学生才可见。

## 后续建议

- 将阅卷提交响应也拆成显式 DTO，避免 `Map<String, Object>` 随意扩展敏感字段。
- 后续增加复评/仲裁时，应沿用同一套分值下限、上限和覆盖规则。
- 可以在真实接口验收脚本中增加主观题 fixture，自动验证负分、超分、漏批三类失败路径。
