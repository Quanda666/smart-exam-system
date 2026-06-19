# 144. Objective Answer Normalization

## 背景

学生成绩详情和错题本已经支持把 `A,B`、`A，B`、`A B`、`AB` 等答案格式识别为同一组选项。
继续审查交卷链路后发现，自动判分也必须使用同一类规范化规则，否则会出现：

- 学生作答 `AB`，正确答案保存为 `A,B`，复盘页面显示正确选项一致，但自动判分可能不一致。
- 历史数据或 AI/导入题目使用不同分隔符，导致客观题误判。

真实考试系统中，判分逻辑必须比展示逻辑更严格可控，不能依赖带编码风险的正则。

## 本批改动

- `ExamService.finalizeAttempt`
  - 客观题自动判分改为调用 `normalizeObjectiveAnswer`。
  - 学生答案和正确答案都先规范化，再做完全相等比较。
- `ExamService.normalizeObjectiveAnswer`
  - 只保留字母/数字等有效答案字符。
  - 统一转为大写。
  - 对字符排序，保证多选题答案顺序无关。
  - 不再依赖包含中文标点的正则表达式。
- `StudentService`
  - 删除旧的 `wrongQuestionOptions`/`FIND_IN_SET` 选项判断路径。
  - 学生成绩详情和错题本只保留 `snapshotQuestionOptions` 统一入口。
- `scripts/run-quality-gates.ps1`
  - 锁定客观题自动判分必须调用 `normalizeObjectiveAnswer`。
  - 锁定该方法必须使用字符级规范化和排序。
  - 防止旧 `normalizeObjective`、`wrongQuestionOptions`、`FIND_IN_SET` 路径回流。

## 验收重点

- `A,B`、`A，B`、`A B`、`AB`、`BA` 对同一道多选题应得到一致自动判分结果。
- 单选、判断题仍按规范化后的完全匹配判分。
- 学生成绩详情、错题本、自动判分对正确选项的理解保持一致。
- 未发布成绩仍不会因为复盘逻辑增强而提前暴露。

## 后续建议

- 将答案规范化抽成共享工具类，供判分、导入、AI 解释、错题复练共同使用。
- 在后续测试批次中增加一组客观题判分集成测试，覆盖中英文分隔符、空格和多选乱序。
