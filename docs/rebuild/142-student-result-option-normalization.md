# 142. Student Result Option Normalization

## 背景

上一批已经让学生成绩详情和错题本优先使用考试发布时冻结的题目选项快照。
继续检查后发现两个学生端可信度问题：

- 成绩详情的答题明细没有显式排序，数据库返回顺序可能与试卷题序不一致。
- 快照选项的正确标记依赖 `FIND_IN_SET`，对 `AB`、`A B`、`A，B` 等多选答案格式不够稳定。

这会影响学生复盘结果、错题本和后续 AI 解释的共同基础数据。

## 本批改动

- `StudentService.getExamResult`
  - 答案明细按 `exam_question_snapshot.sort_order` 排序。
  - 旧数据缺少快照排序时回退到 `answer_record.id`，保证返回顺序稳定。
- `StudentService`
  - 新增 `snapshotQuestionOptions` 作为成绩详情和错题本的统一选项快照读取入口。
  - 选项快照不再依赖 SQL `FIND_IN_SET` 判断正确项。
  - 通过 `normalizeAnswerToken` 去除分隔符和空白，只保留字母/数字等有效字符后再匹配选项标签。
  - 支持 `A,B`、`A，B`、`A B`、`AB` 等常见多选答案写法。
- `scripts/run-quality-gates.ps1`
  - 锁定成绩详情必须按快照题序排序。
  - 锁定学生结果/错题选项必须走 `snapshotQuestionOptions`。
  - 锁定正确项匹配必须经过 `answerContainsOption` 和 `normalizeAnswerToken`。

## 验收重点

- 学生打开成绩详情时，题目顺序与考试发布时的试卷题序一致。
- 多选题正确答案无论保存为 `A,B`、`A，B`、`A B` 还是 `AB`，选项展示中的正确项标记都应一致。
- 错题本与成绩详情使用同一套快照选项逻辑，避免两个页面显示不一致。
- 未发布成绩、未完成复核的成绩仍不得进入成绩详情和错题本。

## 后续建议

- 清理历史遗留的旧选项 helper，减少维护者误用风险。
- 为选择题答案保存格式增加服务端规范化字段或统一工具类，后续自动判分、错题、AI 解释可共用。
- 如果后续引入题目快照中的知识点字段，学生知识掌握度也应从快照读取，避免题库编辑后影响历史考试统计。
