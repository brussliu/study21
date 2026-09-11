# 部署配置

本目录提供部署示例，**不修改**本机 Nginx / IIS / Tomcat / 系统服务。

## 内容

- `docker/` — 飞牛 NAS Docker 部署文件（Dockerfile + nginx.conf），配合根目录 `docker-compose.yml`。
- `nginx/study21.conf` — 同域反向代理示例（`/api/admin/**`、`/api/user/**`）。
- `env/.env.production.example` — 生产环境变量模板（无数据库配置）。
- 本 README — 部署说明（详见 `docs/DEPLOYMENT.md`；Docker 部署详见 `docs/DOCKER_DEPLOYMENT.md`）。

## 使用

1. 前端：`npm run build` 产出 `dist/`，部署到静态服务器 / CDN / Nginx。
2. 后端：`mvn package` 产出可执行 JAR，`java -jar admin-api-0.1.0.jar` 等运行。
3. 反向代理：参考 `nginx/study21.conf` 将 `/api/admin` 与 `/api/user` 分别转发到两个后端。
