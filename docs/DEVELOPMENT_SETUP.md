# 开发环境搭建

## 1. 前置要求

| 工具 | 版本要求 | 说明 |
|---|---|---|
| JDK | 21（目标字节码版本） | 本机可使用更高版本 JDK（如 25）编译 Java 21 字节码 |
| Maven | 3.9+ | 多模块构建 |
| Node.js | ≥ 20 | 前端构建 |
| npm | ≥ 10 | 随 Node.js 安装 |
| Windows PowerShell | 5.1+ 或 PowerShell 7 | （`scripts/*.ps1` は削除済み。必要な場合は git 履歴から復元） |
| Git | 2.x | 独立仓库 |
| Redis | 6.x / 7.x | **登录（セッション）の保存先**。user-api は起動時と認証時に Redis へ繋ぐ（未起動だとログインが 500 になる） |

> 本阶段**不要求数据库**。两个后端在没有数据库、没有数据库环境变量的情况下即可启动。
> ただし **Redis だけは起動しておく**こと（`user-api` のセッションの保存先。2026-09-13 以降）。
> ローカルで手っ取り早く起動する例（ホストに投入しない場合は `docker run -d -p 6379:6379 redis:7-alpine`）:
>
> ```bash
> docker run -d --name study21-redis-dev -p 6379:6379 redis:7-alpine \
>   redis-server --requirepass study21dev
> # アプリ側（パスワード無しで起動した Redis なら STUDY21_REDIS_PASSWORD は不要）
> STUDY21_REDIS_HOST=127.0.0.1 STUDY21_REDIS_PASSWORD=study21dev <user-api の起動コマンド>
> ```

## 2. 环境变量

参考根目录 `.env.example`。所有变量都有安全默认值，本地开发无需 `.env` 文件。

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PC_WEB_PORT` | 5173 | PC 前端开发端口 |
| `MOBILE_WEB_PORT` | 5174 | Mobile 前端开发端口 |
| `ADMIN_API_PORT` | 8081 | admin-api 端口 |
| `USER_API_PORT` | 8082 | user-api 端口 |
| `ADMIN_API_BASE_URL` | `http://localhost:8081` | 前端调用 admin-api 的地址 |
| `USER_API_BASE_URL` | `http://localhost:8082` | 前端调用 user-api 的地址 |
| `ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:5174` | CORS 允许来源（逗号分隔，禁止 `*`） |
| `APP_ENV` | `development` | 运行环境标识 |
| `STUDY21_REDIS_HOST` | `192.168.0.100` | Redis のホスト（セッションの保存先） |
| `STUDY21_REDIS_PORT` | `6379` | Redis のポート |
| `STUDY21_REDIS_PASSWORD` | （空） | Redis のパスワード（未設定なら AUTH しない） |

## 3. 开发端口

| 服务 | 端口 |
|---|---|
| PC Web（Vite） | 5173 |
| Mobile Web（Vite） | 5174 |
| admin-api | 8081 |
| user-api | 8082 |

Vite 开发代理（PC 与 Mobile 相同）：

- `/api/admin` → `http://localhost:8081`
- `/api/user` → `http://localhost:8082`

## 4. 启动步骤

```powershell
# 进入项目根目录
cd C:\work\Source\study2.1

# 1. 初始化（检查环境 + 安装前端依赖）
powershell -ExecutionPolicy Bypass -File .\scripts\setup.ps1

# 2. 启动全部服务
powershell -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1

# 3. 停止全部服务
powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1
```

也可单独启动：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-pc.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start-mobile.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start-admin-api.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start-user-api.ps1
```

## 5. 构建与测试

```powershell
# 构建
powershell -ExecutionPolicy Bypass -File .\scripts\build-all.ps1

# 测试
powershell -ExecutionPolicy Bypass -File .\scripts\test-all.ps1

# 验证无硬编码数据库密码
powershell -ExecutionPolicy Bypass -File .\scripts\verify-no-sql.ps1
```

## 6. Maven 路径

若 `mvn` 不在 PATH，脚本会回退到 `C:\work\Tools\apache-maven-3.9.15\bin\mvn.cmd`。
也可手动将 Maven 的 `bin` 目录加入 PATH。
