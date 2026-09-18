# Docker 部署（飞牛 NAS / fnOS）

部署单位是 4 个容器（数据库是既有的 PostgreSQL，不由本项目创建）：

| 容器 | 镜像 | 端口（NAS 侧） | 说明 |
|---|---|---|---|
| `study21-web` | nginx（内含 pc-web + mobile-web） | 8090（PC）/ 8091（Mobile） | 静态前端 + 反向代理 |
| `study21-admin-api` | Spring Boot JAR | 8081 | 管理员后端 |
| （上に含む） | 同上（batS01 代理服务） | **7777** | 端末が使う代理ポート。2.0 の 8888 と衝突するため 7777 |
| `study21-user-api` | Spring Boot JAR | 8082 | 普通用户后端 |
| `study21-redis` | redis:7-alpine | （不公开） | **登录状态（セッション）の保存先**。user-api だけが使う。有効期限 60 分 |

架构（容器内）：nginx 将 `/api/admin/**` 转发到 `admin-api:8081`、`/api/user/**` 转发到 `user-api:8082`，前端使用相对路径，同源访问，无需 CORS。

前端镜像构建时还会从 `extension/` 打包浏览器扩展（`node extension/build-zip.mjs`），输出到
nginx 的 `/downloads/study21-extension.zip`。**每次部署都会重新打包**，学生在
「インターネット利用履歴 → Web閲覧履歴」画面下载的就是这个文件（`docs/BROWSER_EXTENSION.md`）。

## 三部分独立部署（互不影响）

三个部分是三个独立镜像、三个独立容器。修改并只更新其中一个，另外两个容器不受影响、不会重启：

```bash
docker compose up -d --build admin-api   # 只重建并重启 admin-api
docker compose up -d --build user-api    # 只重建并重启 user-api
docker compose up -d --build web         # 只重建并重启前端
docker compose up -d redis               # 只起/更新 Redis（无需构建）
```

> セッションの保存先（Redis）は `user-api` の `depends_on` に入っているので、
> `docker compose up -d --build user-api` だけでも一緒に起動する。
> データは名前つきボリューム `study21-redis-data` に残る（コンテナを作り直してもログイン状態は消えない）。

后端为**可执行 JAR（内嵌 Tomcat）**，`java -jar` 直接运行，不需要独立 Tomcat / WAR / JSP——与 study2.0 的「WAR + 独立 Tomcat」不同，但同样是跑在 NAS 上的独立 Docker 服务，两者可并存。

## 0. 与 study2 的端口对比

| 服务 | study2（现状） | study2.1 |
|---|---|---|
| Web / 前端 | 8080（Tomcat） | 8090 / 8091（nginx） |
| 数据库 | 54320（PostgreSQL） | 无（本阶段不接库） |
| 后端 API | 同 8080 | 8081 / 8082 |
| 代理（端末が使う） | 8888 | **7777** |

本项目**不会**创建任何数据库容器，只连接到既有的 PostgreSQL（`STUDY21_DATASOURCE_*`）。

## 1. 前置准备

- 飞牛 NAS 已安装 **Docker**（fnOS 的「Docker / Container Manager」）。
- 建议开启 SSH（fnOS 系统设置 → 终端/SSH），用 `docker compose` 命令行操作最稳。
- 确认 NAS 上 **8090 / 8091 / 8081 / 8082 / 7777** 端口未被占用。
  （Redis はホストにポートを公開しないので、空きポートを用意する必要はない）
  （7777 是代理服务的端口。旧 2.0 的代理占用 8888，若 7777 也被占用，
  可在 `setting/deploy.env` 里设 `STUDY21_PROXY_PORT` 换一个空闲端口。）

## 2. NAS 上のディレクトリ構造

发布位置（本项目为 `/vol5/1000/DATA0/tomcat/study21`）:

```
<发布位置>/
├── webapps/   ← 程序本体（docker compose 项目目录。源码会被复制到这里）
│   └── docker-compose.yml
├── files/     ← 用户数据（documents / legacy-documents / temp-files / test-files /
│                 reading / legacy-reading）
└── logs/      ← 日志（backend/ と frontend/。詳細は docs/LOGGING.md）
```

- 旧レイアウト（例 `/vol5/1000/DATA0/tomcat/study2.1`。直下にプログラム、`data/` にデータ）からの移行のうち、
  **`data/` → `files/` のコピーは手動で行う**（`cp -a /vol5/1000/DATA0/tomcat/study2.1/data/. /vol5/1000/DATA0/tomcat/study21/files/`）。
  コピー元は `setting/deploy.env` の `STUDY21_LEGACY_DATA_DIR` で指定する（新レイアウトの外でもよい）。
  `tools/deploy-to-nas.sh` は `files/` と `logs/{backend,frontend}` の作成だけを行い、
  未コピーのときは注意メッセージを出す（コピー後は消える）。旧ファイルは削除しない（手動で片付けてよい）。
- compose のプロジェクト名は `name: study21` に固定してあるため、ディレクトリを移動しても
  同じスタックとして扱われる。旧レイアウトで起動したコンテナが残っている場合は、
  デプロイ時に自動で削除してから起動する（イメージは残る）。

## 3. 把代码放到 NAS

任选一种：

- **A. git 克隆**（推荐）：把本仓库推送到私有 git（NAS 上的 Gitea，或 GitHub/Gitee），再在 NAS 上 `git clone`。
- **B. 直接拷贝**：用 SMB / SFTP 把整个源码目录上传到 NAS 的发布位置（本项目的 `<发布位置>/webapps`）。

> 上传时 `.dockerignore` 已排除 `node_modules`、`dist`、`target`、`tmp`、`.git`，体积很小。

## 4. 调整配置（按需）

编辑 `docker-compose.yml`：

1. 若 NAS 局域网 IP 不是 `192.168.0.100`，把两个后端里的
   `ALLOWED_ORIGINS` 改成实际地址（例：`http://192.168.1.5:8090,http://192.168.1.5:8091`）。
2. 若端口被占用，改左侧宿主机端口（如 `8092:80`），右侧容器端口不要动。
3. 时区固定为日本时间（3 个容器都是 `TZ=Asia/Tokyo`，来自 `setting/deploy.env` 的
   `STUDY21_TZ`）。要改就改这一处，不要直接改 `docker-compose.yml`。日志时刻的说明见
   `docs/LOGGING.md` 的「時刻（タイムゾーン）」。

## 5. 构建并启动

### 方式 A：命令行（推荐）

```bash
cd <发布位置>/webapps
docker compose up -d --build
```

首次构建会拉取 `maven` / `node` / `eclipse-temurin` / `nginx` / `redis:7-alpine` 基础镜像并编译，耗时较长（视网络而定）。

### 方式 B：fnOS 容器管理器（图形界面）

1. 打开「Docker / Container Manager」→「项目 / Compose」。
2. 新建项目，选择 `docker-compose.yml` 所在目录，或直接粘贴 compose 内容。
3. 构建并启动。

## 6. 验证

```bash
docker compose ps                 # 三个容器应为 running
docker compose logs -f admin-api  # 查看启动日志
```

浏览器访问：

- PC：`http://<NAS-IP>:8090`
- Mobile：`http://<NAS-IP>:8091`
- admin-api：`http://<NAS-IP>:8081/api/admin/health`
- user-api：`http://<NAS-IP>:8082/api/user/health`

健康检查应返回：

```json
{"success":true,"code":"OK","message":"OK","data":{"status":"UP","service":"admin-api"},...}
```

> 前端「系统状态」页会显示两个后端是否 UP，可作为连通性自检。

### 6.1 ログイン画面が 502 Bad Gateway になるとき

前端（nginx）は上流の名前を**起動時に一度だけ**名前解決し、その結果を保持し続ける。
そのため `docker compose up -d admin-api user-api` のように**後端だけ**作り直すと、
nginx は消えたコンテナの古い IP を掴んだままになり、`/api/admin/*`・`/api/user/*` が
すべて 502 になる（後端へ直接アクセスすると 200 のままなので原因が見えにくい）。

見分け方（後端は直に 200、nginx 経由だけ 502 ならこの症状）：

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://192.168.0.100:8082/api/user/health   # 200
curl -s -o /dev/null -w '%{http_code}\n' http://192.168.0.100:8090/api/user/health   # 502 なら該当
```

その場での復旧（nginx を起動し直して解決させ直す）：

```bash
cd <发布位置>/webapps
docker compose restart web
```

恒久対策は `deploy/docker/nginx.conf` 側で行っている：Docker 組み込み DNS（`127.0.0.11`）を
`resolver` に指定し、`proxy_pass` へは変数を渡すことで TTL（10 秒）ごとに上流を解決し直す。
この設定はイメージに焼き込まれるため、反映には `web` の再ビルド（通常の
`tools/deploy-to-nas.sh`）が必要。設定を変更したら `tmp/tools/verify-nginx-conf.sh` で
構文と「パス・クエリ文字列がそのまま転送されるか」を検証できる。

## 7. 更新与回滚

- 更新（一键）：在 NAS 上运行工作区的 `tools/deploy-to-nas.sh`（配置在 `setting/deploy.env`）。它会读取配置 → 增量复制源码到 `<发布位置>/webapps` → 按配置渲染 `docker-compose.yml` → `docker compose up -d --build`。默认重建 3 个服务（`web`＝pc-web 与 mobile-web 的前端镜像、`admin-api`・`user-api`＝后端 JAR 镜像），可只指定某一个（`tools/deploy-to-nas.sh web`）；`--dry-run` 只预览复制内容。
- 不用构建缓存（想彻底重编前端与后端时）：`tools/deploy-to-nas.sh --no-cache`。`docker compose up` 没有 `--no-cache` 选项，所以脚本分两步执行 `docker compose build --no-cache <服务>` → `docker compose up -d <服务>`（基础镜像不会重新拉取，只丢弃构建缓存）。`setting/deploy.env` 里写 `STUDY21_NO_CACHE=1` 也可以让它成为默认。なお、各 Dockerfile は「源码全部 COPY → 依赖安装 → 编译」の順なので、通常の `--build` でも変更したソースは必ず再コンパイルされる（缓存が効くのは無変更のときだけ）。
- 更新（手动）：拉取/上传新代码后，`docker compose up -d --build`（会重新编译并替换容器）。
- 後端だけを更新したときは、前端も `docker compose restart web` しておくと確実
  （上流の再解決は nginx.conf 側で対応済みだが、古いイメージは起動時の解決結果を
  持ち続けるため。詳細は「6.1 ログイン画面が 502 Bad Gateway になるとき」）。
- 回滚：`git checkout <旧提交>` 后重新 `docker compose up -d --build`。
- 停止：`docker compose down`（保留镜像）；彻底清理：`docker compose down --rmi local`。

## 8. 日志位置

主机侧（推荐入口）:

| 路径 | 内容 |
|---|---|
| `<发布位置>/logs/backend/admin-api-app.log` | admin-api の通常ログ |
| `<发布位置>/logs/backend/admin-api-sql.log` | admin-api の**全 DB 操作**（SQL・パラメータ・実行時間・件数） |
| `<发布位置>/logs/backend/admin-api-error.log` | admin-api の例外・ERROR（スタックトレース付き） |
| `<发布位置>/logs/backend/user-api-*.log` | user-api も同じ 3 ファイル |
| `<发布位置>/logs/frontend/study21-access.log` | 前端（nginx）のアクセスログ（IP・URL・Referer） |
| `<发布位置>/logs/frontend/study21-error.log` | 前端（nginx）のエラーログ |
| `<发布位置>/logs/backend/archive/` | ローテーション済み（gzip、既定 90 日保持） |

> ログの時刻は 3 コンテナとも**日本時間**（`TZ=Asia/Tokyo`）。DB サーバー（PostgreSQL）も
> `Asia/Tokyo` なので、ログと DB の時刻が一致する。詳細は `docs/LOGGING.md` の
> 「時刻（タイムゾーン）」を参照。

コンテナの標準出力（`docker compose logs <服务名>`）も残しているが、SQL は含めない
（SQL は専用ファイルのみ）。設計の詳細・運用手順は `docs/LOGGING.md` を参照。

## 9. 説明と制限

- 本阶段**无数据库**，因此 compose 中无任何数据库服务与连接配置。
- 前端/后端都在镜像内构建，NAS 上无需安装 Node / JDK / Maven。
- 真实认证尚未实现：PC 前端登录页使用 UI 确认专用假认证，不连接认证 API。
