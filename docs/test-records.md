# 测试记录

本文件用于记录每个阶段的验证方法、测试用例、执行结果和问题修复。

## 阶段 1 测试计划

| 编号 | 测试对象 | 操作 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| T1-001 | 后端健康检查 | 访问 `/api/health` | 返回 `success` 为 `true` | 已通过；后端可启动，`/api/health` 返回 `success=true`、`status=UP`。数据库当前未连接，接口已如实返回 `connected=false` |
| T1-002 | AI 状态检查 | 访问 `/api/ai/status` | 返回 AI 配置状态，不暴露 API Key | 已通过；接口返回 `mode=MOCK`、`available=true`、`apiKeyConfigured=false`，未暴露明文 API Key |
| T1-003 | 前端首页 | 启动前端并访问首页 | 页面正常展示项目基础信息 | 已通过；`127.0.0.1:3000` 返回 HTTP 200。`5173` 端口绑定被 Windows 拒绝，已将默认开发端口调整为 `3000` |
| T1-004 | 数据库脚本 | 执行 `schema.sql` 与 `seed.sql` | 基础表和演示数据创建成功 | GitHub Actions 云端 MySQL 服务容器已验证通过；本地因未安装 MySQL 不作为唯一验证路径 |
| T1-005 | 项目结构检查 | 递归查看目录结构 | 基础目录和文件存在 | 已确认 `backend`、`frontend`、`database`、`docs`、`scripts`、`README.md`、`.gitignore`、`LICENSE` 等基础结构存在 |
| T1-006 | 前端构建 | 在 `frontend` 目录执行 `npm run build` | 类型检查通过并生成生产构建产物 | 构建通过；出现 Vite chunk 体积警告，阶段 1 可接受，后续按路由拆包优化 |
| T1-007 | 本地辅助脚本 | 检查 `scripts` 目录 | 存在环境检查、前端启动、后端启动脚本 | 已创建 `check-env.cmd`、`run-frontend.cmd`、`run-backend.cmd` 和 `scripts/README.md` |
| T1-008 | Git 提交与远程推送 | 初始化 Git 仓库并推送到 GitHub | 本地提交成功，远程仓库存在阶段 1 成果 | 已提交 `4774fbe chore: 初始化阶段1项目骨架`，并推送到 `https://github.com/Quanda666/smart-exam-system.git` 的 `main` 分支 |
| T1-009 | 云端自动化验证方案 | 编写 GitHub Actions MySQL 服务容器验证流程 | 不依赖本地 MySQL 和 Docker Desktop，也能验证数据库脚本、后端接口与前端构建 | 已新增 `.github/workflows/cloud-verify.yml`，并由用户确认 GitHub Actions 已通过 |
| T1-010 | 容器化部署配置 | 编写 Dockerfile、Nginx 配置与 Compose 编排 | 支持 Docker 环境可用时启动 MySQL、后端、前端 | 已新增 `backend/Dockerfile`、`frontend/Dockerfile`、`frontend/nginx.conf`、`docker-compose.yml` |

## 阶段 1 环境检查记录

| 工具 | 检查结果 | 说明 |
|---|---|---|
| Node.js | 已找到 | 路径为 `C:\Program Files\nodejs\node.exe` |
| npm | 已找到 | 路径为 `C:\Program Files\nodejs\npm.cmd` |
| Java | 已找到 | 本机存在 JDK 25 和 JDK 11 |
| javac | 已找到 | 本机存在 JDK 编译器 |
| Maven | 已找到但未加入 PATH | 可用路径为 `C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd`，脚本已支持自动识别该路径 |
| Maven Daemon | 未找到 | `where mvnd` 未找到可执行文件 |
| Gradle | 未找到 | `where gradle` 未找到可执行文件 |
| 前端依赖 | 已安装 | 已生成 `frontend/package-lock.json` 与 `frontend/node_modules` |
| 前端开发服务 | 已启动 | `http://127.0.0.1:3000/` 返回 HTTP 200 |
| Docker | 客户端存在，Engine 未就绪 | Docker CLI 已找到，但 Docker Desktop 长时间处于 `starting`，本地暂不作为主要验证路径 |
| MySQL | 未找到 | 未找到 `mysql.exe`、MySQL 服务或常见安装目录 |
| Git | 已找到 | Git 已安装，且全局用户名和邮箱已配置 |

## 阶段 2 测试计划

| 编号 | 测试对象 | 操作 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| T2-001 | 演示账号接口 | 访问 `/api/auth/demo-users` | 未登录也能返回管理员、教师、学生三类账号 | 已通过；后端单元测试覆盖，返回 `admin` 等演示账号 |
| T2-002 | 未登录拦截 | 未携带 Token 访问 `/api/auth/me` | 返回 `401` 与 `UNAUTHORIZED` | 已通过；后端单元测试覆盖 |
| T2-003 | 错误密码 | 使用错误密码登录 `admin` | 返回明确失败提示 | 已通过；后端单元测试覆盖，返回 `BAD_REQUEST` |
| T2-004 | 管理员登录 | 使用 `admin/admin123` 登录 | 返回 Token、用户信息、ADMIN 角色和管理员菜单 | 已通过；后端单元测试覆盖，前端登录页已接入 |
| T2-005 | 后端角色隔离 | 使用管理员 Token 访问 `/api/student/overview` | 返回 `403` 与 `FORBIDDEN` | 已通过；后端单元测试覆盖 |
| T2-006 | 前端构建 | 执行 `npm run build` | 类型检查通过并生成生产构建产物 | 已通过；构建成功，仍有 Element Plus 相关 chunk 体积警告，后续按路由拆包优化 |
| T2-007 | 云端自动化验证 | GitHub Actions 启动后端并用 curl 登录和访问角色接口 | 数据库脚本、后端测试、健康接口、登录接口、管理员权限接口和前端构建全部通过 | 已更新 `.github/workflows/cloud-verify.yml`，待推送后由 Actions 执行 |

## 阶段 2 本地命令记录

| 命令 | 结果 | 说明 |
|---|---|---|
| `powershell -NoProfile -ExecutionPolicy Bypass -Command "& { if (Test-Path 'C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd') { & 'C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd' -f backend/pom.xml test } else { mvn -f backend/pom.xml test } }"` | 通过 | 后端 5 个测试全部通过，覆盖上下文加载、演示账号、未登录拦截、错误密码、管理员接口和跨角色拒绝 |
| `npm run build` | 通过 | 前端类型检查和生产构建通过；Vite 输出 chunk 体积警告，当前不阻断阶段 2 |

## 阶段 2 问题与处理

1. 本地首次计算密码摘要命令被默认终端按 `cmd` 解析，导致 PowerShell 变量语法不兼容；已改为显式使用 `powershell -NoProfile -ExecutionPolicy Bypass -Command`。
2. 本地未安装 MySQL，后端登录服务提供数据库不可用时的演示账号回退能力，保证阶段 2 登录权限可验证；云端验证仍会执行真实 MySQL 初始化脚本。
3. 当前 Token 为内存会话，适合阶段 2 演示；后续如需要分布式或持久会话，可替换为 Spring Security、Sa Token 或 JWT。

## 阶段 3 测试计划

| 编号 | 测试对象 | 操作 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| T3-001 | 班级管理接口 | 管理员新增、修改、删除班级 | 返回成功，数据可变更 | 已通过；后端单元测试覆盖 |
| T3-002 | 科目管理接口 | 教师新增科目 | 返回成功，教师具备科目维护权限 | 已通过；后端单元测试覆盖 |
| T3-003 | 知识点管理接口 | 教师基于科目新增知识点 | 返回成功，知识点关联科目 | 已通过；后端单元测试覆盖 |
| T3-004 | 公告查询接口 | 学生查询公告 | 返回成功，学生可读公告 | 已通过；后端单元测试覆盖 |
| T3-005 | 权限隔离 | 学生尝试新增班级 | 返回 `403 FORBIDDEN` | 已通过；后端单元测试覆盖 |
| T3-006 | 前端基础资料页面 | 执行 `npm run build` | 类型检查通过并生成生产构建产物 | 已通过；构建成功，仍有 Vite chunk 体积警告 |
| T3-007 | 云端自动化验证 | GitHub Actions 中执行数据库脚本、登录、基础资料接口 curl 校验 | 数据库、后端接口和前端构建均通过 | 已更新 `.github/workflows/cloud-verify.yml`，待推送后由 Actions 执行 |

## 阶段 3 本地命令记录

| 命令 | 结果 | 说明 |
|---|---|---|
| `powershell -NoProfile -ExecutionPolicy Bypass -Command "& { if (Test-Path 'C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd') { & 'C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd' -f backend/pom.xml test } else { mvn -f backend/pom.xml test } }"` | 通过 | 后端 8 个测试全部通过，新增覆盖班级、科目、知识点、公告和学生越权拒绝 |
| `npm run build` | 通过 | 前端类型检查和生产构建通过；Vite 输出 chunk 体积警告，当前不阻断阶段 3 |

## 阶段 3 问题与处理

1. 新增基础资料页面后，Element Plus 全局组件类型在 Vue 类型检查中提示缺失；已在 `frontend/tsconfig.json` 增加 `element-plus/global` 类型。
2. Element Plus 表格插槽默认行类型较宽，编辑按钮传参触发 TypeScript 类型错误；已在模板中对行数据做显式类型断言。
3. 本地仍未安装 MySQL，基础资料服务继续提供内存演示数据回退；GitHub Actions 仍执行真实 MySQL 脚本验证。

## 阶段 4 测试计划

| 编号 | 测试对象 | 操作 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| T4-001 | 题库新增接口 | 教师新增单选题并填写选项 | 返回成功，题型、题干、选项和正确答案保存正确 | 已通过；后端单元测试覆盖 |
| T4-002 | 题库筛选接口 | 按题型、难度、状态查询题目 | 返回符合条件的题目列表 | 已通过；后端单元测试覆盖 |
| T4-003 | 题目状态管理 | 教师将草稿题发布 | 返回成功，状态变为已发布 | 已通过；后端单元测试覆盖 |
| T4-004 | 题目编辑接口 | 将题目修改为填空题并填写参考答案 | 返回成功，题型和参考答案更新正确 | 已通过；后端单元测试覆盖 |
| T4-005 | 题目删除接口 | 教师删除测试题目 | 返回逻辑删除成功 | 已通过；后端单元测试覆盖 |
| T4-006 | 权限隔离 | 学生访问题库管理接口 | 返回 `403 FORBIDDEN` | 已通过；后端单元测试覆盖 |
| T4-007 | 前端题库页面 | 执行 `npm run build` | 类型检查通过并生成生产构建产物 | 已通过；构建成功，仍有 Vite chunk 体积警告 |
| T4-008 | 云端自动化验证 | GitHub Actions 中执行数据库脚本、登录、题库接口 curl 校验 | 数据库、后端接口和前端构建均通过 | 已更新 `.github/workflows/cloud-verify.yml`，待推送后由 Actions 执行 |

## 阶段 4 本地命令记录

| 命令 | 结果 | 说明 |
|---|---|---|
| `powershell -NoProfile -ExecutionPolicy Bypass -Command "& { if (Test-Path 'C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd') { & 'C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd' -f backend/pom.xml test } else { mvn -f backend/pom.xml test } }"` | 通过 | 后端 10 个测试全部通过，新增覆盖题库新增、筛选、发布、编辑、删除和学生越权拒绝 |
| `npm run build` | 通过 | 前端类型检查和生产构建通过；Vite 输出 chunk 体积警告，当前不阻断阶段 4 |

## 阶段 4 问题与处理

1. 题库页面涉及客观题和非客观题两类表单，前端已按题型动态切换选项编辑区和参考答案输入区。
2. 本地仍未安装 MySQL，题库服务继续提供内存演示数据回退；GitHub Actions 仍执行真实 MySQL 脚本验证。
3. 前端构建仍出现 Element Plus 与依赖包体积警告，当前不影响验收，后续可按路由拆包优化。

## 阶段 5 测试计划

| 编号 | 测试对象 | 操作 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| T5-001 | 手动组卷接口 | 教师手动选择两道已发布题目，创建试卷 | 返回成功，试卷包含两道题，总分计算正确 | 已通过；后端单元测试覆盖 |
| T5-002 | 规则组卷接口 | 教师按题型、难度、数量、分值配置规则组卷 | 返回成功，自动抽取题目并创建试卷，题量和总分符合预期 | 已通过；后端单元测试覆盖 |
| T5-003 | 试卷详情接口 | 获取创建的试卷 | 返回试卷基础信息和题目列表 | 已通过；后端单元测试覆盖 |
| T5-004 | 试卷状态管理 | 教师发布草稿试卷 | 返回成功，状态变为已发布 | 已通过；后端单元测试覆盖 |
| T5-005 | 权限隔离 | 学生访问题库管理接口 | 返回 `403 FORBIDDEN` | 已通过；后端单元测试覆盖 |
| T5-006 | 前端试卷页面 | 执行 `npm run build` | 类型检查通过并生成生产构建产物 | 已通过；构建成功，仍有 Vite chunk 体积警告 |
| T5-007 | 云端自动化验证 | GitHub Actions 中执行数据库脚本、登录、试卷接口 curl 校验 | 数据库、后端接口和前端构建均通过 | 已更新 `.github/workflows/cloud-verify.yml`，待推送后由 Actions 执行 |

## 阶段 5 本地命令记录

| 命令 | 结果 | 说明 |
|---|---|---|
| `powershell -NoProfile -ExecutionPolicy Bypass -Command "& { if (Test-Path 'C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd') { & 'C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd' -f backend/pom.xml test } else { mvn -f backend/pom.xml test } }"` | 通过 | 后端 12 个测试全部通过，新增覆盖手动组卷、规则组卷、试卷详情、发布和学生越权拒绝 |
| `npm run build` | 通过 | 前端类型检查和生产构建通过；Vite 输出 chunk 体积警告，当前不阻断阶段 5 |

## 阶段 5 问题与处理

1. 规则组卷时，需要避免重复抽题，已在后端服务中增加已选题目 ID 集合进行去重。
2. 规则组卷时，题库题量不足可能导致组卷失败，已在后端增加明确的题量不足提示。
3. 手动组卷时，需要提供已发布且属于当前科目的题目列表，已在前端增加题目筛选和加载逻辑。

## 阶段 6 测试计划

| 编号 | 测试对象 | 操作 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| T6-001 | 考试任务创建接口 | 教师为班级创建考试任务 | 返回成功，学生考试记录已生成 | 待执行 |
| T6-002 | 学生考试列表 | 学生登录后查询个人考试列表 | 返回包含待开始的考试 | 待执行 |
| T6-003 | 学生开始考试 | 学生点击开始考试 | 返回试卷题目，考试记录状态变为进行中 | 待执行 |
| T6-004 | 学生提交答案 | 学生作答后提交 | 答案记录保存，客观题自动判分，主观题待批阅 | 待执行 |
| T6-005 | 权限隔离 | 学生尝试创建考试 | 返回 `403 FORBIDDEN` | 待执行 |
| T6-006 | 前端考试页面 | 执行 `npm run build` | 类型检查通过并生成生产构建产物 | 待执行 |
| T6-007 | 云端自动化验证 | GitHub Actions 中执行 curl 校验 | 考试相关接口通过 | 待执行 |

## 阶段 10 测试计划

| 编号 | 测试对象 | 操作 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| T10-001 | AI 辅助出题接口 | 教师调用 API 生成题目 | 返回 AI 生成的题目内容 | 待执行 |
| T10-002 | AI 解释接口 | 调用 API 解释指定内容 | 返回 AI 解释结果 | 待执行 |
| T10-003 | AI 评分建议接口 | 调用 API 获取评分建议 | 返回 AI 评分建议 | 待执行 |
| T10-004 | 前端 AI 出题交互 | 教师在题目页面触发 AI 出题 | 前端正确调用 API 并显示结果 | 待执行 |
| T10-005 | 前端 AI 讲解交互 | 学生在错题本触发 AI 讲解 | 前端正确调用 API 并显示结果 | 待执行 |
| T10-006 | 前端构建 | 执行 `npm run build` | 类型检查通过并生成生产构建产物 | 已通过 |
