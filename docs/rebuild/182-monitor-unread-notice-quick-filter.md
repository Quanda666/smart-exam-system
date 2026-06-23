# 182. 未读监考提醒快捷筛选

## 背景

第 181 批把 `Unread notices` 提升为监考面板指标。教师看到未读提醒数量后，下一步通常就是定位这些会话。此前还需要再手动打开 `Latest notification` 下拉选择 `Unread`，操作不够直接。

## 本批改动

- `Unread notices` 指标卡改为可点击按钮卡片。
- 点击后设置 `sessionFilter.latestNotificationStatus = 'UNREAD'`。
- 保留其它筛选条件，只收窄最近通知状态。
- 增加按钮卡片 hover 和 focus-visible 样式，保持键盘可达。
- 质量门增加快捷筛选函数、筛选赋值和可聚焦样式断言。

## 三端协同影响

- 教师端：从“看到未读数量”到“定位未读会话”一步完成。
- 学生端：通知已读状态仍由学生查看通知更新，不改变作答流程。
- 管理员端：无接口变化，导出仍沿用第 180 批的筛选参数。

## 验收点

- 点击 `Unread notices` 后，`Latest notification` 筛选切换为 `Unread`。
- 已有状态、风险等筛选条件不被清空。
- 键盘聚焦该指标卡时有可见焦点样式。
