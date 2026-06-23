# 93. 管理员端成绩申诉全局审计

## 背景

第 91 批写入 `score_appeal_log`，第 92 批让教师能查看单条申诉记录。但管理员还缺少跨考试、跨教师、跨学生的全局申诉审计视图，无法快速回答“谁提交了申诉、谁处理了申诉、哪些申诉进入过复核、什么时候关闭”。

## 本次改动

- 管理员端新增接口：
  - `GET /api/monitor/score-appeal-logs`
- 接口支持分页和过滤：
  - `keyword`：考试、学生、处理人、题干或说明。
  - `action`：`SUBMIT`、`REPLY`、`CLOSE_RECHECK`。
  - `handlingResult`：`MAINTAINED`、`RECHECK_REQUIRED`、`ADJUSTED_OFFLINE`。
  - `startFrom` / `startTo`：动作时间范围。
- 前端 `admin.ts` 新增：
  - `ScoreAppealAuditLog`
  - `ScoreAppealAuditQuery`
  - `listScoreAppealAuditLogs`
- 管理员“系统日志”页新增“成绩申诉审计”标签。
- 审计表展示：
  - 时间
  - 动作
  - 考试
  - 学生
  - 申诉对象
  - 状态流转
  - 处理结果
  - 操作人
  - 说明

## 验收点

- 管理员能按动作筛选申诉日志。
- 管理员能按处理结果筛选申诉日志。
- 管理员能按关键词和时间范围检索申诉日志。
- 页面分页与其它系统日志标签一致。
- 本地质量门禁通过。
