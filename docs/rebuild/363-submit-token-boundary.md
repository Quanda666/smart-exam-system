# 363. Submit Token Boundary

## 背景

学生交卷接口使用 `submitToken` 支撑重复提交回放与幂等响应。此前服务端会把超过数据库字段长度的令牌静默截断到 80 个字符，这会让客户端传入的异常令牌被改写成另一个合法值，影响重复交卷、响应回放和审计判断的可信度。

## 本步改造

- `AnswerRequest.submitToken` 增加 `@Size(max = 80)`，在控制器入参阶段拒绝超长提交令牌。
- `ExamService.submitExam` 在读取考试尝试前调用 `normalizeSubmitToken`，统一处理空白、修剪和长度校验。
- `finalizeAttempt`、重复提交回放、`exam_submit_response` 写入统一使用规范化后的提交令牌。
- 移除 `submitToken` 场景下的静默 `trimToLength(..., 80)`，超长令牌直接返回业务错误。
- 质量门新增提交令牌边界检查，防止后续重新引入静默截断。

## 三端影响

- 学生端：异常超长 `submitToken` 会被明确拒绝，正常生成的令牌不受影响。
- 教师端：强制交卷等不传 `submitToken` 的内部流程仍保持 `null`，不改变监考处置语义。
- 管理员端：后续审计 `submit_token` 时不再出现被服务端截断后的不完整值。

## 验收点

- 超过 80 字符的提交令牌不能进入数据库写入流程。
- 空白提交令牌被归一化为 `null`。
- 重复提交响应回放和令牌不匹配判断使用同一套规范化逻辑。
- `scripts/run-quality-gates.ps1` 能检查该边界长期存在。
