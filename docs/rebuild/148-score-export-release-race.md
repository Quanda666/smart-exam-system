# 148. Score Export Release Race

## 背景

单场成绩导出已经在开始时调用 `requireScoresReleased`。
但导出流程是两步：先检查发布状态，再查询答卷记录。
如果成绩在两步之间被撤回，理论上存在很短的竞态窗口。

正式考试系统中，导出这类数据出口需要在最终数据查询处再次绑定发布状态。

## 本批改动

- `ExportService.examScoreSheet`
  - 保留 `requireScoresReleased` 的早期失败提示。
  - 在实际成绩记录查询中增加 `JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1`。
  - 成绩被撤回后，即使前置检查刚通过，最终查询也不会继续导出撤回后的成绩。
- `scripts/run-quality-gates.ps1`
  - 增加静态约束，确保单场成绩导出记录查询本身必须重新校验 `score_release.status = 1`。

## 验收重点

- 未发布成绩不能导出。
- 已撤回成绩不能导出。
- 导出记录查询本身必须绑定发布状态，而不是只依赖前置检查。
- 开放复核申诉中的答卷仍不得进入导出。
