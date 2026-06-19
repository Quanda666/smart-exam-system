# Step 354 - Review Answer ID Boundary

## 背景

教师阅卷提交是成绩闭环的核心写入点。现有 `ReviewService.submitReview` 已经校验待阅卷状态、教师数据范围、全部待阅卷答案覆盖、重复答案、非负分、不得超过题目分值，并且按考试快照范围重新计算总分。

但 `ReviewRequest.answerRecordId` 只做了非空校验。非正数 ID 会落入“不是当前待阅卷答案”的业务错误，参数错误语义不够稳定。

## 本次改动

- `ReviewRequest.answerRecordId` 增加 `@Positive`。
- `ReviewService.submitReview` 增加 service 层正数校验，避免内部调用绕过 Controller 校验。
- 质量门新增结构性断言，要求阅卷 DTO 和 service 层都拒绝非正数答案记录 ID。

## 协同意义

- 教师端：非法答案记录 ID 会被明确识别为参数错误。
- 后端：阅卷写入点继续保持待阅卷状态、完整覆盖和分值上限校验。
- 管理员端：阅卷审计日志只记录有效业务对象，降低排查噪声。

## 验收点

- `answerRecordId <= 0` 不能进入待阅卷答案匹配逻辑。
- 合法阅卷仍必须覆盖全部待阅卷答案。
- 分数仍不能为负，不能超过题目快照分值。
