# 87. 成绩申诉处理结果结构化

## 背景

成绩申诉原先只有 `teacher_reply` 文本，教师处理后学生只能看到“已回复”和一段意见。真实系统需要结构化处理结果，方便学生理解处理结论，也方便后续做复核、仲裁、审计和统计。

## 本次改动

- `score_appeal` 新增 `handling_result` 字段。
- 新库 `schema.sql` 直接包含该字段。
- 旧库启动迁移自动补列，并将历史已处理申诉回填为 `MAINTAINED`。
- `ScoreAppealReplyRequest` 新增必填 `handlingResult`，限制为：
  - `MAINTAINED`
  - `RECHECK_REQUIRED`
  - `ADJUSTED_OFFLINE`
- 教师端处理申诉弹窗新增“处理结果”选择。
- 教师端申诉列表新增“处理结果”筛选，并展示处理结果、处理人和处理时间。
- 学生端申诉记录展示处理结果。
- 后端列表接口返回 `handlingResult`，并支持按 `handlingResult` 过滤。

## 状态语义

- `MAINTAINED`：维持原分。
- `RECHECK_REQUIRED`：需要复核，后续可衔接复评/仲裁流程。
- `ADJUSTED_OFFLINE`：已线下调整，用于当前阶段没有自动调分流程时的过渡记录。

## 验收点

- 教师处理申诉必须选择处理结果。
- 学生端可以看到处理结果和处理意见。
- 教师端可以按处理结果过滤已处理申诉，列表可直接看到处理结论和处理审计信息。
- 历史已回复申诉有默认处理结果。
- 后端拒绝非法处理结果。
- 本地质量门禁通过。
