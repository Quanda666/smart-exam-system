# 304 基础数据操作审计证据

## 背景

基础数据是三端协同的根：管理员维护班级、课程、课程班、授课关系和公告范围，教师和学生后续的题库、考试发布、考试准入都会依赖这些数据。基础数据变更必须能在系统日志中精确追踪。

## 本步范围

- `BasicDataController` 所有写操作在业务成功后写入 `operation_log`。
- 写操作响应统一追加 `operationLogId`。
- 覆盖对象：
  - 班级 `edu_class`
  - 课程 `edu_course`
  - 课程班 `class_course`
  - 授课分配 `teacher_class_course`
  - 学生班级关系 `student_class_membership`
  - 科目 `edu_subject`
  - 知识点 `edu_knowledge_point`
  - 公告 `notice`
- `BasicDataPanel.vue` 在保存或删除成功后展示最新审计证据，并支持复制审计 ID 和系统日志深链。
- 本地质量门禁检查后端响应与前端展示，防止后续回归。

## 协同关系

- 管理员端：基础数据变更后立即获得可复核证据，适合排查考试范围、授课范围、公告范围异常。
- 教师端：教师创建科目、知识点、公告时同样留下操作者与动作证据。
- 系统日志：通过 `/monitor/logs?operationLogId=<id>` 精确定位基础数据变更记录。

## 验收点

- 基础数据写接口响应包含 `operationLogId`。
- `operation_log.action` 使用 `CREATE_BASIC_*`、`UPDATE_BASIC_*`、`DELETE_BASIC_*` 系列动作名。
- 基础数据页保存/删除后出现审计提示。
- “Copy audit ID” 复制日志 ID，“Copy audit link” 复制可跳转到系统日志的深链。
- `scripts/run-quality-gates.ps1` 覆盖后端和前端防回归检查。
