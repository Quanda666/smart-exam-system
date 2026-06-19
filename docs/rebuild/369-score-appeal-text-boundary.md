# 369. Score Appeal Text Boundary

## 背景

成绩申诉理由、教师处理意见和复核关闭说明会同时写入业务表、申诉日志、通知和导出文件，是成绩争议处理的核心证据。DTO 已有限制，但服务层此前直接 `trim()` 后写入，内部调用或未来入口绕过 Controller 校验时会破坏文本边界。

## 本步改造

- `ScoreAppealService` 新增 `MAX_APPEAL_TEXT_LENGTH = 1000`。
- 新增 `normalizeRequiredAppealText`，统一校验非空和最大长度。
- 学生提交申诉、教师回复申诉、教师关闭复核全部使用规范化后的文本写入业务表和 `score_appeal_log`。
- 补充 `request == null` 的服务层错误语义，避免内部调用出现空指针。
- 质量门新增申诉生命周期文本边界检查。

## 三端影响

- 学生端：申诉理由必须非空且不超过 1000 字，写入日志的内容与提交内容一致。
- 教师端：处理意见和复核说明必须非空且不超过 1000 字，便于后续仲裁与导出。
- 管理员端：全局申诉审计日志不再依赖单一 Controller 校验，证据链更稳定。

## 验收点

- 超长申诉理由不能进入 `score_appeal.reason` 或 `score_appeal_log.note`。
- 超长教师回复不能进入 `teacher_reply` 或申诉回复日志。
- 超长复核说明不能进入 `recheck_note` 或复核关闭日志。
