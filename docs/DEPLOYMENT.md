# 部署说明

## 1. PC Web 部署

- `npm run build`（在 `frontend/pc-web`）产出 `dist/` 静态文件。
- 将 `dist/` 部署到静态服务器 / CDN / Nginx。
- SPA 需要 history 路由回退到 `index.html`。

## 2. Mobile Web 部署

- 与 PC 相同，`frontend/mobile-web` 产出 `dist/`。
- `manifest.webmanifest` 与图标随 `dist/` 一起部署。

## 2.5 Redis（セッションの保存先）

ログイン状態（セッション）は Redis に置く（2026-09-13 以降）。`user-api` だけが使い、
admin-api は `STATELESS`（セッションを使わない）ため接続しない。

- コンテナ: `study21-redis`（`redis:7-alpine`、compose のネットワーク内のみ。ホストには公開しない）
- パスワード: `STUDY21_REDIS_PASSWORD`（`setting/deploy.env`。未設定だとデプロイが止まる）
- 有効期限: **60 分**（`spring.session.timeout`。最後のアクセスから数える）
- 中を見る: `docker exec -it study21-redis redis-cli -a <パスワード>` →
  `KEYS study21:session:user*`
- 消しても実害は「全員が再ログイン」だけ（`FLUSHDB` してよい）

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

## 7. 容器 / 反向代理配置

`deploy/` には**构建必需**の 4 ファイルだけを置いている（根目录 `docker-compose.yml` と
`deploy/docker/nginx.Dockerfile` が相互に参照する）：

- `deploy/docker/nginx.Dockerfile` — 前端镜像（pc-web / mobile-web をビルドして nginx で配信、PC:80 / Mobile:81）
- `deploy/docker/admin-api.Dockerfile` / `user-api.Dockerfile` — 各后端的可执行 JAR 镜像
- `deploy/docker/nginx.conf` — nginx 容器内の設定（`/api/admin`・`/api/user` の反向代理、访问日志定义）

旧版の `deploy/*.bat`・`deploy/nginx/study21.conf`・`deploy/env/.env.production.example` は
不要のため削除した（部署は下記のスクリプトに一本化）。反向代理の実体は `deploy/docker/nginx.conf`。

部署の実行：工作区の `tools/deploy-to-nas.sh`（設定は `setting/deploy.env`）。
前端（`web`＝pc-web + mobile-web）と后端（`admin-api` / `user-api`）の 3 サービスをまとめて
再コンパイル・再起動する。ビルドキャッシュを使わずに作り直したいときは `--no-cache` を付ける。
手順の詳細は `docs/DOCKER_DEPLOYMENT.md`。

## 8. 日志位置

- 本机开发日志・一时文件：`tmp/`（自动生成ツールやスクリーンショットもここ。gitignore 済み・デプロイ対象外）。
- 生产日志：`<发布位置>/logs/`（backend は `<service>-app.log` / `-sql.log` / `-error.log`、
  frontend は nginx 访问日志）。設計と運用手順は `docs/LOGGING.md`。
- 日志不写入旧项目目录。
