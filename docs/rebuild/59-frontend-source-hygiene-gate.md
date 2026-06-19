# 59. Frontend Source Hygiene Gate

## 目标

第 58 批已经把后端 Java 源码卫生升级为全量门禁。本批把同类保护推进到前端源码，覆盖管理员端、教师端、学生端的 Vue/TypeScript 页面、API 封装、样式和关键前端配置。

在线考试系统的三端体验高度依赖前端文案和状态提示：候考、作答、保存、交卷、阅卷、成绩发布、申诉、监考告警等页面一旦出现乱码，学生和教师会直接失去信任。因此前端也需要和后端一样具备基础编码卫生门禁。

## 新增脚本

新增：

```powershell
scripts\check-frontend-source-hygiene.ps1
```

默认检查：

- `frontend/src`
- `frontend/index.html`
- `frontend/package.json`
- `frontend/tsconfig.json`
- `frontend/tsconfig.node.json`
- `frontend/vite.config.ts`

覆盖扩展名：

- `.vue`
- `.ts`
- `.js`
- `.css`
- `.html`
- `.json`

## 检查内容

当前检查项：

- Unicode replacement character `U+FFFD`。
- 常见历史乱码和 mojibake 码点。
- 行首 Git 冲突标记：`<<<<<<<`、`>>>>>>>`。

CSS 中常见的分隔注释不会被误判为冲突标记，因为脚本只检查行首的真实冲突标记形态。

脚本内的待检测字符使用 Unicode 码点声明，避免 PowerShell 文件编码或控制台编码污染检查规则。

## 质量门禁变化

`scripts/run-quality-gates.ps1` 增加两个动作：

- PowerShell 语法解析包含 `check-frontend-source-hygiene.ps1`。
- 完整质量门禁在前端构建前执行前端源码卫生检查。

当前检查结果覆盖 52 个前端源码和配置文件。后续新增 Vue 页面、API 文件、样式文件或配置文件，会自动被纳入检查。

## 对三端重建的影响

管理员端：

- 用户、角色、系统配置、操作日志、考试监控等页面文案不会再静默退化为乱码。

教师端：

- 题库、AI 出题、组卷、发布考试、阅卷、成绩分析等工作台页面可以在构建前发现明显编码污染。

学生端：

- 候考、作答、草稿恢复、交卷、成绩、错题本、申诉等关键提示会被基础源码门禁兜住。

三端协同：

- 当前端路由、接口封装和页面提示继续演进时，文案层质量可以和后端接口层一起进入统一质量门禁。

## 验收标准

- `check-frontend-source-hygiene.ps1` PowerShell 语法解析通过。
- `check-frontend-source-hygiene.ps1 -SourceRoot frontend/src` 通过。
- `run-quality-gates.ps1` 已接入前端源码卫生检查。
- 完整本地质量门禁通过。
- 本批脚本和文档无尾随空白。

## 后续增强

- 将前端 hygiene 输出加入夜间验收摘要，记录覆盖文件数量。
- 增加对前端硬编码失败文案、空提示、占位符残留的专项检查。
- 结合端到端测试验证三端关键页面在登录失效、越权、考试过期、重复交卷、成绩未发布等场景下的提示一致性。
