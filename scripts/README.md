# 本地运行脚本

本目录保存 Windows 环境下的辅助脚本，用于降低团队成员首次运行项目的门槛。

## 脚本说明

| 脚本 | 用途 |
|---|---|
| [`check-env.cmd`](check-env.cmd) | 检查 Java、javac、Maven、Node.js、npm、前端依赖等环境状态 |
| [`run-backend.cmd`](run-backend.cmd) | 启动后端 Spring Boot 服务；若 Maven 未配置，会提示安装 Maven |
| [`run-frontend.cmd`](run-frontend.cmd) | 安装前端依赖并启动 Vite 开发服务器 |

## 推荐执行顺序

```cmd
scripts\check-env.cmd
scripts\run-backend.cmd
scripts\run-frontend.cmd
```

## 当前注意事项

当前环境已经检测到 Java、javac、Node.js、npm 和本地 Maven。Maven 未加入系统 PATH，但脚本会自动识别 `C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd`。

阶段 1 中，Windows 对 `5173` 端口绑定返回权限拒绝，因此前端开发服务默认使用 `127.0.0.1:3000`。
