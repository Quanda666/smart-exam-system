# 接口设计记录

本文件用于持续记录在线考试系统接口设计。当前已完成阶段 3 基础资料管理接口。

## 通用响应结构

```json
{
  "success": true,
  "code": "OK",
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-06-06T01:00:00"
}
```

## 阶段 1 接口

| 方法 | 路径 | 说明 | 是否需要登录 |
|---|---|---|---|
| GET | /api/health | 后端健康检查 | 否 |
| GET | /api/ai/status | AI 配置状态检查 | 否 |

## 阶段 2 接口

| 方法 | 路径 | 说明 | 是否需要登录 |
|---|---|---|---|
| GET | /api/auth/demo-users | 获取三类演示账号 | 否 |
| POST | /api/auth/login | 账号密码登录，返回 Token、用户信息、角色菜单和默认入口 | 否 |
| GET | /api/auth/me | 获取当前登录用户、菜单和默认入口 | 是 |
| GET | /api/auth/menus | 获取当前登录用户菜单 | 是 |
| POST | /api/auth/logout | 退出登录并注销 Token | 是 |
| GET | /api/auth/access-matrix | 查看角色与页面入口映射 | 是 |
| GET | /api/admin/overview | 管理员工作台数据 | 是，仅 ADMIN |
| GET | /api/teacher/overview | 教师工作台数据 | 是，仅 TEACHER |
| GET | /api/student/overview | 学生首页数据 | 是，仅 STUDENT |

## 阶段 3 基础资料接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/basic/summary | 获取班级、科目、知识点、公告数量统计 | 已登录用户 |
| GET | /api/basic/classes | 查询班级列表，支持 keyword、status | ADMIN、TEACHER |
| POST | /api/basic/classes | 新增班级 | ADMIN |
| PUT | /api/basic/classes/{id} | 修改班级 | ADMIN |
| DELETE | /api/basic/classes/{id} | 删除班级 | ADMIN |
| GET | /api/basic/subjects | 查询科目列表，支持 keyword、status | ADMIN、TEACHER、STUDENT |
| POST | /api/basic/subjects | 新增科目 | ADMIN、TEACHER |
| PUT | /api/basic/subjects/{id} | 修改科目 | ADMIN、TEACHER |
| DELETE | /api/basic/subjects/{id} | 删除科目 | ADMIN、TEACHER |
| GET | /api/basic/knowledge-points | 查询知识点列表，支持 subjectId、keyword、status | ADMIN、TEACHER、STUDENT |
| POST | /api/basic/knowledge-points | 新增知识点 | ADMIN、TEACHER |
| PUT | /api/basic/knowledge-points/{id} | 修改知识点 | ADMIN、TEACHER |
| DELETE | /api/basic/knowledge-points/{id} | 删除知识点 | ADMIN、TEACHER |
| GET | /api/basic/notices | 查询公告列表，支持 keyword、status | ADMIN、TEACHER、STUDENT |
| POST | /api/basic/notices | 新增或发布公告 | ADMIN、TEACHER |
| PUT | /api/basic/notices/{id} | 修改公告 | ADMIN、TEACHER |
| DELETE | /api/basic/notices/{id} | 删除公告 | ADMIN、TEACHER |

## 阶段 3 请求示例

新增班级：

```json
{
  "className": "23本科计科1班",
  "major": "计算机科学与技术",
  "grade": "2023级",
  "status": 1
}
```

新增科目：

```json
{
  "subjectName": "Java程序设计",
  "description": "用于演示 Java 基础、集合、线程、面向对象等题目。",
  "status": 1
}
```

新增知识点：

```json
{
  "subjectId": 1,
  "parentId": null,
  "pointName": "集合框架",
  "sortOrder": 1,
  "status": 1
}
```

新增公告：

```json
{
  "title": "在线考试系统阶段3公告",
  "content": "基础资料管理已启用。",
  "status": 1
}
```

## 阶段 2 登录响应数据要点

| 字段 | 说明 |
|---|---|
| token | 后端生成的登录令牌，前端后续放入 Authorization Bearer 请求头 |
| expiresAt | Token 过期时间，当前演示实现为 8 小时 |
| user | 当前用户基础信息、角色、默认入口和档案信息 |
| menus | 当前角色可访问菜单 |
| defaultPath | 登录后默认跳转路径 |

## 权限约束

1. 未登录访问受保护接口返回 `401 UNAUTHORIZED`。
2. 登录后访问非当前角色接口返回 `403 FORBIDDEN`。
3. 前端菜单只展示当前角色可访问页面，但后端仍进行角色校验，避免只依赖前端判断。
4. 班级新增、修改、删除仅管理员可操作；教师可查看班级用于考试任务发布前准备。
5. 科目、知识点和公告由管理员或教师维护；学生只读科目、知识点和公告。
6. 演示密码不再以明文写入数据库，种子数据使用带盐 SHA-256 摘要保存。

## 后续接口分组

- 用户与权限接口
- 题库接口
- 试卷接口
- 考试任务接口
- 学生答题接口
- 阅卷接口
- 成绩分析接口
- 日志接口
- AI 辅助接口
