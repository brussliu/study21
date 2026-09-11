# Docker 部署（飞牛 NAS / fnOS）

Study 2.1 本阶段**没有数据库**，部署只需 3 个容器：

| 容器 | 镜像 | 端口（NAS 侧） | 说明 |
|---|---|---|---|
| `study21-web` | nginx（内含 pc-web + mobile-web） | 8090（PC）/ 8091（Mobile） | 静态前端 + 反向代理 |
| `study21-admin-api` | Spring Boot JAR | 8081 | 管理员后端 |
| `study21-user-api` | Spring Boot JAR | 8082 | 普通用户后端 |

架构（容器内）：nginx 将 `/api/admin/**` 转发到 `admin-api:8081`、`/api/user/**` 转发到 `user-api:8082`，前端使用相对路径，同源访问，无需 CORS。

## 三部分独立部署（互不影响）

三个部分是三个独立镜像、三个独立容器。修改并只更新其中一个，另外两个容器不受影响、不会重启：

```bash
docker compose up -d --build admin-api   # 只重建并重启 admin-api
docker compose up -d --build user-api    # 只重建并重启 user-api
docker compose up -d --build web         # 只重建并重启前端
```

后端为**可执行 JAR（内嵌 Tomcat）**，`java -jar` 直接运行，不需要独立 Tomcat / WAR / JSP——与 study2.0 的「WAR + 独立 Tomcat」不同，但同样是跑在 NAS 上的独立 Docker 服务，两者可并存。

## 0. 与 study2 的端口对比

| 服务 | study2（现状） | study2.1 |
|---|---|---|
| Web / 前端 | 8080（Tomcat） | 8090 / 8091（nginx） |
| 数据库 | 54320（PostgreSQL） | 无（本阶段不接库） |
| 后端 API | 同 8080 | 8081 / 8082 |

本阶段**不会**创建/连接任何数据库容器。

## 1. 前置准备

- 飞牛 NAS 已安装 **Docker**（fnOS 的「Docker / Container Manager」）。
- 建议开启 SSH（fnOS 系统设置 → 终端/SSH），用 `docker compose` 命令行操作最稳。
- 确认 NAS 上 **8090 / 8091 / 8081 / 8082** 端口未被占用。

## 2. 把代码放到 NAS

任选一种：

- **A. git 克隆**（推荐）：把本仓库推送到私有 git（NAS 上的 Gitea，或 GitHub/Gitee），再在 NAS 上 `git clone`。
- **B. 直接拷贝**：用 SMB / SFTP 把整个 `study2.1` 目录上传到 NAS（例如 `/vol1/docker/study2.1`）。

> 上传时 `.dockerignore` 已排除 `node_modules`、`dist`、`target`、`tmp`、`.git`，体积很小。

## 3. 调整配置（按需）

编辑 `docker-compose.yml`：

1. 若 NAS 局域网 IP 不是 `192.168.0.100`，把两个后端里的
   `ALLOWED_ORIGINS` 改成实际地址（例：`http://192.168.1.5:8090,http://192.168.1.5:8091`）。
2. 若端口被占用，改左侧宿主机端口（如 `8092:80`），右侧容器端口不要动。

## 4. 构建并启动

### 方式 A：命令行（推荐）

```bash
cd /你的路径/study2.1
docker compose up -d --build
```

首次构建会拉取 `maven` / `node` / `eclipse-temurin` / `nginx` 基础镜像并编译，耗时较长（视网络而定）。

### 方式 B：fnOS 容器管理器（图形界面）

1. 打开「Docker / Container Manager」→「项目 / Compose」。
2. 新建项目，选择 `docker-compose.yml` 所在目录，或直接粘贴 compose 内容。
3. 构建并启动。

## 5. 验证

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

## 6. 更新与回滚

- 更新（一键）：在开发机 Windows 上运行 `deploy\deploy-to-nas.bat`（先填好脚本顶部的 `SSH_TARGET` 和 `NAS_PATH` 两行），它会自动执行「`deploy\copy-to-tomcat.bat` 拷贝源码 → SSH 到 NAS 执行 `docker compose up -d --build`」。默认只重建 `web`，也可运行 `deploy\deploy-to-nas.bat admin-api` / `user-api`；`deploy\deploy-to-nas.bat --dry-run` 只预览拷贝。
- 更新（手动）：拉取/上传新代码后，`docker compose up -d --build`（会重新编译并替换容器）。
- 回滚：`git checkout <旧提交>` 后重新 `docker compose up -d --build`。
- 停止：`docker compose down`（保留镜像）；彻底清理：`docker compose down --rmi local`。

## 7. 日志位置

容器日志走 stdout，用 `docker compose logs <服务名>` 查看，不落盘到旧项目。
如需落盘，可在 compose 中为两个后端追加 `logging` 或卷挂载（本阶段不默认配置）。

## 8. 说明与限制

- 本阶段**无数据库**，因此 compose 中无任何数据库服务与连接配置。
- 前端/后端都在镜像内构建，NAS 上无需安装 Node / JDK / Maven。
- 真实认证尚未实现：PC 前端登录页使用 UI 确认专用假认证，不连接认证 API。
