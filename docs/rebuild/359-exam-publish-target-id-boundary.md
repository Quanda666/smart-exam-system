# Step 359 - Exam Publish Target ID Boundary

## 背景

考试发布是三端协同的起点：教师或管理员选择试卷和考生范围，系统生成考试目标、候选人快照、试卷快照和学生准入数据。此前发布请求已经校验试卷存在、试卷已发布、题目可用、考生范围存在、教师授课范围和最终候选人数，但 `paperId`、班级 ID、班课 ID、学生 ID 的正数边界还不够明确。

如果 `0`、负数或空目标项进入发布计划，错误会在数据库查询后表现为“试卷不可用”或“目标无效”，不利于接口契约、自动化测试和后续审计排查。正式考试系统需要在请求层和服务层都明确拒绝非法 ID。

## 本次改动

- `ExamRequest.paperId` 增加 `@Positive`。
- `ExamRequest.classIds` 增加元素级 `@Positive`。
- `ExamRequest.classCourseIds` 增加元素级 `@Positive`。
- `ExamRequest.studentUserIds` 增加元素级 `@Positive`。
- `ExamService.validateExamRequest` 在服务层拒绝空请求和非正数 `paperId`。
- `ExamService.validateExamPaper` 在查库前再次校验 `paperId`。
- `ExamService.normalizeExamTargets` 在生成 `TargetSpec` 前拒绝非正数目标 ID。
- 质量门禁新增结构断言，确保考试发布请求和发布计划服务层持续保留正数边界。

## 协同意义

- 管理员端：直接发布考试时，试卷和目标范围必须是有效业务 ID，避免产生异常发布计划。
- 教师端：提交审批前先得到明确参数错误，不会把非法目标混进授课范围校验。
- 学生端：候选人快照和准入记录只会基于有效目标生成，减少发布后无法准入或准入范围异常的风险。

## 验收点

- `paperId <= 0` 在考试预检和创建考试时被拒绝。
- `classIds`、`classCourseIds`、`studentUserIds` 中任一元素 `<= 0` 时被拒绝。
- 服务层内部调用即使绕过 Controller Bean Validation，也会在查库前拒绝非法 ID。
- 合法试卷、合法目标、教师授课范围、管理员直接发布和教师提交审批行为不变。
