# 199. 监考规则确认筛选与导出

## 目标

教师端监考会话列表已经展示学生规则确认时间，本批次继续补齐筛选和导出能力，让教师能快速定位未完成规则确认的异常会话。

## 范围

- 教师端监考面板新增 `Rules` 筛选项。
- 支持按 `CONFIRMED`、`MISSING` 过滤监考会话。
- 监考会话 CSV 导出接受同一筛选条件，避免页面结果和导出结果不一致。
- 导出文件名追加 `rules_<status>` 片段，便于审计归档。

## 三端协同

- 学生端：首次进入考试前完成规则确认，后端写入 `exam_attempt.rules_confirmed_at`，这是唯一可信审计来源。
- 教师端：监考列表展示规则确认时间，并可筛选 `Missing` 会话进行提醒、记录处置或后续核查。
- 管理员端：后续可复用同一审计字段做全局异常统计和考试准入策略抽查。

## 接口约定

`GET /api/monitor/exams/{examId}/sessions/export`

新增可选参数：

- `rulesConfirmationStatus=CONFIRMED`：仅导出已确认规则的会话。
- `rulesConfirmationStatus=MISSING`：仅导出未确认规则的会话。
- 空值或 `ALL`：不按规则确认状态过滤。

非法值返回参数错误，避免静默导出错误审计数据。

## 验收要点

- 选择 `Confirmed` 时，列表仅展示 `rulesConfirmedAt` 有值的会话。
- 选择 `Missing` 时，列表仅展示 `rulesConfirmedAt` 为空的会话。
- 点击导出时，CSV 服务端过滤条件与当前面板筛选一致。
- 导出文件名包含 `rules_CONFIRMED` 或 `rules_MISSING`。
- 重置筛选后，规则确认状态回到 `All rules`。
