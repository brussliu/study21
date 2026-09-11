# 部署说明

## 1. PC Web 部署

- `npm run build`（在 `frontend/pc-web`）产出 `dist/` 静态文件。
- 将 `dist/` 部署到静态服务器 / CDN / Nginx。
- SPA 需要 history 路由回退到 `index.html`。

## 2. Mobile Web 部署

- 与 PC 相同，`frontend/mobile-web` 产出 `dist/`。
- `manifest.webmanifest` 与图标随 `dist/` 一起部署。

## 3. admin-api 部署

- `mvn package`（在 `backend`）产出 `backend/admin-api/target/admin-api-0.1.0.jar`。
- 运行：`java -jar admin-api-0.1.0.jar`
- 端口：`8081`（可通过 `ADMIN_API_PORT` 覆盖）。

## 4. user-api 部署

- 产出 `backend/user-api/target/user-api-0.1.0.jar`。
- 运行：`java -jar user-api-0.1.0.jar`
- 端口：`8082`（可通过 `USER_API_PORT` 覆盖）。

## 5. 端口

| 服务 | 默认端口 |
|---|---|
| PC Web | 5173（开发）/ 生产由反向代理或静态服务器决定 |
| Mobile Web | 5174（开发）/ 同上 |
| admin-api | 8081 |
| user-api | 8082 |

## 6. 环境变量

| 变量 | 说明 |
|---|---|
| `ADMIN_API_PORT` | admin-api 端口 |
| `USER_API_PORT` | user-api 端口 |
| `APP_ENV` | 环境标识（development / staging / production） |
| `ALLOWED_ORIGINS` | CORS 允许来源（逗号分隔） |
| `VITE_ADMIN_API_BASE_URL` | 前端构建时指定的 admin-api 地址（可选） |
| `VITE_USER_API_BASE_URL` | 前端构建时指定的 user-api 地址（可选） |

> 不含数据库 URL / 用户名 / 密码。

## 7. 反向代理示例

见 `deploy/`：

- `deploy/nginx/study21.conf`：同域反向代理 `/api/admin/**`、`/api/user/**`。
- `deploy/env/.env.production.example`：生产环境变量模板。
- `deploy/README.md`：部署说明。

## 8. 日志位置原则

- 本机开发日志：`tmp/`（由启动脚本写入，已 gitignore）。
- 生产日志：建议由部署平台或进程管理器（systemd / 容器日志）统一收集。
- 日志不写入旧项目目录。
