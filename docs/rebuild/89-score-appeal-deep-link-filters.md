# 89. 成绩申诉待办直达筛选

## 背景

第 88 批让教师首页能看到待处理申诉和需复核申诉数量，但点击后仍然只是进入阅卷页默认视图。真实工作台需要“看到待办 -> 直达对应列表”，否则数字和处理动作之间仍有额外搜索成本。

## 本次改动

- 前端导航支持保留 URL query：
  - 权限判断仍使用纯路径。
  - 实际路由跳转保留 `?appealStatus=...&appealHandlingResult=...`。
- 教师首页统计卡可点击：
  - “待批阅试卷”进入 `/reviews`。
  - “待处理申诉”进入 `/reviews?appealStatus=0&appealHandlingResult=ALL`。
  - “需复核申诉”进入 `/reviews?appealStatus=1&appealHandlingResult=RECHECK_REQUIRED`。
- 教师首页“成绩申诉”快捷入口直达待处理申诉筛选。
- 阅卷页读取并监听 URL query，自动切换申诉状态和处理结果筛选。

## Query 约定

- `appealStatus`
  - `0`：待处理。
  - `1`：已回复。
  - `-1`：全部。
- `appealHandlingResult`
  - `ALL`：全部处理结果。
  - `MAINTAINED`：维持原分。
  - `RECHECK_REQUIRED`：需要复核。
  - `ADJUSTED_OFFLINE`：已线下调整。

## 验收点

- 从教师首页点击“待处理申诉”，阅卷页展示待处理申诉，处理结果筛选重置为全部。
- 从教师首页点击“需复核申诉”，阅卷页展示已回复且需要复核的申诉。
- 刷新带 query 的 `/reviews` 页面后筛选仍然生效。
- URL query 不影响菜单权限判断。
- 本地质量门禁通过。
