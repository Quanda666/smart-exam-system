# 本地运行脚本

**课程**：Web程序设计课程设计（S3048I）  
**项目**：第二组 - 在线考试系统

本目录保存 Windows 环境下的辅助脚本，用于降低团队成员首次运行项目的门槛。

## 脚本说明

| 脚本 | 用途 |
|---|---|
| [`run-all.cmd`](run-all.cmd) | **【强烈推荐】一键极速本地构建与启动脚本**：自动构建前端代码并将资源打包同步到 Spring Boot 托管目录，最终一键以本地单容器一体化托管模式运行，无需手动分别起前端和后端！ |
| [`check-env.cmd`](check-env.cmd) | 检查 Java、javac、Maven、Node.js、npm、前端依赖等环境状态 |
| [`run-backend.cmd`](run-backend.cmd) | 【分离开发模式】启动后端 Spring Boot 服务；若 Maven 未配置，会提示安装 Maven |
| [`run-frontend.cmd`](run-frontend.cmd) | 【分离开发模式】安装前端依赖并启动 Vite 开发服务器 |

## 推荐运行方式（一键运行托管模式）

双击运行或在终端执行：
```cmd
scripts\run-all.cmd
```
运行成功后，访问 `http://localhost:8080` 即可直接使用完整的前后端系统！

## 传统运行方式（前后端分离模式）

如果你想进行前端热更新开发：

```cmd
scripts\check-env.cmd
scripts\run-backend.cmd
scripts\run-frontend.cmd
```
然后访问前端开发服务器：`http://127.0.0.1:3000`。

