# 接口设计记录

本文件用于持续记录在线考试系统接口设计。当前已完成阶段 10 OpenAI 兼容 AI 辅助能力。

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
| GET | /api/auth/register-options | 获取注册所需选项，如班级列表 | 否 |
| POST | /api/auth/register | 注册新用户并直接登录 | 否 |
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

## 阶段 4 题库接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/questions/summary | 获取题目总数、发布数、草稿数、题型和难度统计 | ADMIN、TEACHER |
| GET | /api/questions | 查询题目列表，支持 keyword、subjectId、knowledgePointId、questionType、difficulty、status | ADMIN、TEACHER |
| POST | /api/questions | 新增题目，支持单选、多选、判断、填空、主观题 | ADMIN、TEACHER |
| PUT | /api/questions/{id} | 修改题目主体和选项 | ADMIN、TEACHER |
| PUT | /api/questions/{id}/status | 发布或撤回题目，status 为 1 或 0 | ADMIN、TEACHER |
| DELETE | /api/questions/{id} | 删除题目，当前为逻辑删除 | ADMIN、TEACHER |
| GET | /api/questions/student-deny-check | 学生越权验证接口 | ADMIN、TEACHER |

## 阶段 5 试卷接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/papers/summary | 获取试卷总数、发布数、草稿数和累计题量统计 | ADMIN、TEACHER |
| GET | /api/papers | 查询试卷列表，支持 keyword、subjectId、status | ADMIN、TEACHER |
| GET | /api/papers/{id} | 获取单份试卷详情，包含题目列表 | ADMIN、TEACHER |
| POST | /api/papers | 手动组卷创建试卷 | ADMIN、TEACHER |
| POST | /api/papers/generate | 按规则自动组卷并创建试卷 | ADMIN、TEACHER |
| PUT | /api/papers/{id} | 修改试卷题目、分值和基础信息 | ADMIN、TEACHER |
| PUT | /api/papers/{id}/status | 发布或撤回试卷，status 为 1 或 0 | ADMIN、TEACHER |
| DELETE | /api/papers/{id} | 删除试卷，当前为逻辑删除 | ADMIN、TEACHER |

## 阶段 5 请求示例

规则组卷：

```json
{
  "subjectId": 1,
  "paperName": "Java程序设计自动组卷",
  "description": "按题型和难度自动抽题",
  "status": 0,
  "rules": [
    {
      "questionType": "SINGLE_CHOICE",
      "difficulty": "EASY",
      "count": 1,
      "score": 5
    }
  ]
}
```

## 阶段 4 请求示例

新增单选题：

```json
{
  "subjectId": 1,
  "knowledgePointId": 1,
  "questionType": "SINGLE_CHOICE",
  "difficulty": "EASY",
  "stem": "Java 中用于存储键值对的数据结构通常是？",
  "correctAnswer": "B",
  "analysis": "Map 接口用于存储键值对数据。",
  "defaultScore": 5,
  "status": 1,
  "options": [
    { "optionLabel": "A", "optionContent": "List", "correct": false },
    { "optionLabel": "B", "optionContent": "Map", "correct": true },
    { "optionLabel": "C", "optionContent": "Set", "correct": false }
  ]
}
```

新增填空题：

```json
{
  "subjectId": 2,
  "knowledgePointId": 4,
  "questionType": "FILL_BLANK",
  "difficulty": "MEDIUM",
  "stem": "事务的四个特性简称为____。",
  "correctAnswer": "ACID",
  "analysis": "事务特性包含原子性、一致性、隔离性和持久性。",
  "defaultScore": 6,
  "status": 0,
  "options": []
}
```

## 阶段 6 考试接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/exams/teacher | 教师查询考试任务列表 | ADMIN、TEACHER |
| GET | /api/exams/student | 学生查询自己的考试列表 | STUDENT |
| POST | /api/exams | 教师创建考试任务 | ADMIN、TEACHER |
| POST | /api/exams/attempt/{attemptId}/start | 学生开始考试，返回试卷题目 | STUDENT |
| POST | /api/exams/attempt/{attemptId}/submit | 学生提交答案 | STUDENT |

## 阶段 6 请求示例

创建考试任务：

```json
{
  "paperId": 1,
  "examName": "Java程序设计期中测试",
  "description": "覆盖集合、线程、并发等知识点。",
  "startTime": "2026-07-01T09:00:00",
  "endTime": "2026-07-01T11:00:00",
  "durationMinutes": 120,
  "classIds": [1]
}
```

提交答案：

```json
{
  "answers": {
    "1": "B",
    "2": "A"
  }
}
```

## 阶段 7-9 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/reviews/pending | 获取待批阅的考试列表 | ADMIN、TEACHER |
| GET | /api/reviews/attempt/{id} | 获取待批阅的答卷详情 | ADMIN、TEACHER |
| POST | /api/reviews/attempt/{id} | 提交主观题批阅结果 | ADMIN、TEACHER |
| GET | /api/student/grades | 学生获取个人成绩列表 | STUDENT |
| GET | /api/student/exam-result/{id} | 学生获取单次考试结果详情 | STUDENT |
| GET | /api/student/wrong-questions | 学生获取错题本 | STUDENT |
| GET | /api/student/mastery | 学生获取知识点掌握度 | STUDENT |
| POST | /api/monitor/cheat-event | 记录切屏等异常事件 | STUDENT |
| GET | /api/monitor/cheat-events/{id} | 查看指定考试记录的异常事件 | ADMIN、TEACHER |
| GET | /api/monitor/logs | 查询系统操作日志 | ADMIN |

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
7. 题库管理接口仅管理员和教师可访问；学生端后续只能在考试或成绩发布后按业务规则查看必要题目信息，不能直接访问题库维护接口。
8. 客观题必须包含选项并设置正确答案：单选和判断只能有一个正确选项，多选至少两个正确选项；填空题和主观题必须填写参考答案。

## 后续接口分组

- 用户与权限接口
- 题库接口（阶段 4 已完成基础维护能力，后续组卷阶段继续复用）
- 试卷接口（阶段 5 已完成基础维护与组卷能力）
- 考试任务接口（阶段 6 已完成任务创建、学生查询、开始考试、提交答案）
- 学生答题接口（阶段 6 已完成基础实现）
- 阅卷接口（阶段 7 已完成）
- 成绩分析接口（阶段 8 已完成学生端）
- 日志接口（阶段 9 已完成）
- AI 辅助接口
