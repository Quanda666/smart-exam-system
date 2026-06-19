# 第 68 批：考试内监考提醒可见

## 背景

第 67 批已经让 `WARN` 和首次 `FORCE_SUBMIT` 监考处置写入学生站内通知。但学生作答页没有常驻通知铃铛，真实考试中学生可能一直停留在作答界面，直到交卷后才看到通知。这会削弱“提醒学生”的即时价值。

## 本批改动

- 学生作答页 `ExamTaking.vue` 接入通知 API。
- 进入考试后初始化监考通知基线，避免把历史监考通知误当成本次考试即时提醒。
- 作答过程中每 15 秒轮询最近通知，只识别：
  - `MONITOR_WARNING`
  - `MONITOR_FORCE_SUBMIT`
- 检测到新的监考通知后：
  - 在考试页 header 下方展示 `el-alert` 横幅。
  - 弹出轻量 `ElMessage.warning` 提示。
  - 不自动标记通知已读，保留通知中心未读状态供学生考后回看。
- 离开考试或组件卸载时清理监考通知轮询定时器，不影响草稿保存、心跳、监考事件批量上报。

## 三端协同影响

- 教师端：点击“提醒学生”后，学生在作答页也能看到提醒，不再只依赖顶部通知铃铛。
- 学生端：考试中可及时看到监考提醒和强制交卷通知，同时通知中心仍保留记录。
- 管理员端：通知记录、监考处置记录、答卷状态仍保持同一条审计链。

## 验收方式

自动质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

人工联调建议：

1. 学生进入考试作答页。
2. 教师打开实时监考页，对该学生执行“提醒学生”。
3. 学生作答页 15 秒内出现监考提醒横幅。
4. 学生顶部通知中心仍能看到对应未读通知。

可先用第 67 批接口验收确认后端通知已写入：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckMonitorWarnNotification `
  -SkipSubmit `
  -CleanupAfterRun
```

## 后续建议

- 后续可将轮询替换为 WebSocket/SSE，进一步缩短监考提醒延迟。
- 通知数据建议增加 `related_type`、`related_id`，作答页即可按当前 `attemptId` 精确过滤，而不是只依赖进入考试后的时间窗口。
