# 58. Full Java Source Hygiene Gate

## 目标

前几批已经分模块清理了成绩申诉、认证上下文、操作日志、认证入口和认证 DTO 中的历史乱码与断裂字符串。本批把这条治理线从定向文件清单升级为后端 Java 全量源码门禁。

这一步的意义是：后续考试、题库、阅卷、监考、成绩、AI 等模块继续重构时，不能再把明显乱码、替换字符或未闭合字符串带回主链路。质量门禁开始保护整个后端源码树，而不是只保护已经列入白名单的文件。

## 调整范围

涉及文件：

- `scripts/check-java-source-hygiene.ps1`
- `scripts/run-quality-gates.ps1`

## 检查能力

`check-java-source-hygiene.ps1` 保留两种运行模式：

- `-Files`：用于临时检查指定文件，便于排查单个模块。
- `-SourceRoot`：用于扫描整个 Java 源码目录，当前质量门禁使用该模式。

当前检查内容：

- UTF-8 读取 Java 源码。
- 识别常见乱码标记码点。
- 识别 Unicode replacement character `U+FFFD`。
- 识别常见 mojibake 残留字符。
- 检查非 text block 行中未配对的双引号。

脚本本身使用 ASCII 形式记录待检查码点，避免 PowerShell 文件编码或控制台编码再次污染检查规则。

## 质量门禁变化

本批前：

- `scripts/run-quality-gates.ps1` 只对 21 个已治理 Java 文件运行 hygiene 检查。
- 新增或未列入清单的后端文件可能绕过乱码检查。

本批后：

- `scripts/run-quality-gates.ps1` 对 `backend/src/main/java` 全量运行 hygiene 检查。
- 当前覆盖 124 个 Java 文件。
- 后续新增 Java 文件会自动进入质量门禁。

## 对重建计划的影响

这一步属于底座治理，不直接新增三端页面，但会提升后续大规模重构的可靠性：

- 管理员端：用户、角色、系统配置、日志等接口提示不再允许回退为乱码。
- 教师端：题库、组卷、发布、阅卷、成绩分析等模块继续改造时，接口文本和校验提示会被全量兜底。
- 学生端：候考、作答、交卷、成绩、错题、申诉等链路的错误提示和响应文本会被同一门禁保护。
- 运维验收：本地和夜间质量门禁可以更早发现编码污染，避免上线后才在页面或日志里暴露不可读文本。

## 验收标准

- PowerShell 语法解析通过。
- `check-java-source-hygiene.ps1 -SourceRoot backend/src/main/java` 通过。
- `run-quality-gates.ps1` 使用全量 SourceRoot 检查，而不是定向文件清单。
- 完整本地质量门禁通过。
- 本批脚本和文档无尾随空白。

## 后续增强

- 继续为核心业务状态机补集成测试，避免只停留在源码卫生层面。
- 将前端 Vue/TypeScript 源码也纳入类似的乱码和断裂文案检查。
- 在 CI 或夜间验收中输出 hygiene 覆盖数量，便于发现异常的扫描范围变化。
