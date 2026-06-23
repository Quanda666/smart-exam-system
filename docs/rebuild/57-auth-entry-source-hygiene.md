# 57. Auth Entry Source Hygiene

## 目标

本批继续清理认证链路的历史乱码和断裂字符串，聚焦用户登录、注册、验证码登录、邮箱绑定、密码修改和个人资料更新这些入口级能力。

认证入口是三端协同的第一道门：管理员、教师、学生所有后续页面权限、接口权限、操作日志和审计记录，都依赖这里返回稳定、可读、可追踪的认证结果。因此本批不扩展新业务，而是先把入口源码恢复到可维护状态，并纳入质量门禁。

## 修复范围

本批清理以下文件：

- `backend/src/main/java/com/smartexam/controller/AuthController.java`
- `backend/src/main/java/com/smartexam/service/AuthService.java`
- `backend/src/main/java/com/smartexam/dto/auth/AuthUser.java`
- `backend/src/main/java/com/smartexam/dto/auth/LoginRequest.java`
- `backend/src/main/java/com/smartexam/dto/auth/RegisterRequest.java`
- `backend/src/main/java/com/smartexam/dto/auth/ChangePasswordRequest.java`
- `backend/src/main/java/com/smartexam/dto/auth/LoginByCodeRequest.java`
- `backend/src/main/java/com/smartexam/dto/auth/SendLoginCodeRequest.java`
- `backend/src/main/java/com/smartexam/dto/auth/SendBindCodeRequest.java`
- `backend/src/main/java/com/smartexam/dto/auth/BindEmailRequest.java`
- `backend/src/main/java/com/smartexam/dto/auth/UpdateProfileRequest.java`

## 已恢复的业务提示

认证控制器恢复为可读中文响应和操作日志：

- 登录成功：`登录成功`
- 注册成功：`注册成功`
- 退出成功：`退出成功`
- 修改密码：`密码修改成功`
- 发送验证码：`验证码已发送`
- 邮箱绑定：`邮箱绑定成功`
- 更新资料：`个人资料已更新`
- 操作日志动作：`登录系统`、`验证码登录`
- 操作日志目标：`认证`

认证服务恢复为可读中文异常和状态提示：

- `账号或密码错误`
- `注册失败，无法获取用户ID`
- `用户不存在`
- `当前密码不正确`
- `该邮箱未绑定任何账号`
- `该邮箱已被其他账号绑定`
- `验证码已发送，请 60 秒后再试`
- `今日发送次数已达上限（5次），请明天再试`
- `账号已被禁用，请联系管理员`
- `当前账号未分配有效角色，请联系管理员`
- `数据库连接不可用，请检查本地或云端数据源配置`

用户角色展示恢复为：

- `管理员`
- `教师`
- `学生`
- `访客`

## 质量门禁扩展

`scripts/run-quality-gates.ps1` 中的 Java hygiene 定向检查从 10 个文件扩展到 21 个文件：

- 第 55 批：成绩申诉闭环关键文件 4 个。
- 第 56 批：认证上下文、权限切面、Token 和操作日志文件 6 个。
- 第 57 批：认证入口、认证服务、认证 DTO 和 `AuthUser` 文件 11 个。

当前仍然采用定向文件清单，而不是直接扫描 `backend/src/main/java` 全量源码。原因是后端仍存在历史乱码债务，全量开启会把大量未治理模块一次性拉入本批范围。后续每清理一个模块，就应将该模块加入质量门禁，直到最终切换为全量扫描。

## 验收标准

- 本批 11 个认证相关 Java 文件通过 `check-java-source-hygiene.ps1`。
- 本地质量门禁覆盖 21 个已治理 Java 文件。
- 认证入口的主要中文提示、校验信息和操作日志文本不再出现明显乱码。
- 不改变现有认证接口路径、请求 DTO 名称和前端调用契约。
- 教师注册后等待审核的既有行为保持不变。

## 后续增强

- 继续清理考试、题库、AI、系统配置等模块中的历史乱码和断裂字符串。
- 为登录、验证码登录、邮箱绑定、密码修改补充后端集成测试。
- 将认证失败、验证码频控、账号禁用、角色缺失纳入端到端验收链路。
- 当所有后端源码通过 hygiene 检查后，将质量门禁从定向清单升级为全量 `SourceRoot` 检查。
