# Study 2.1

Study 2.1 是从 Study 2.0 逐步迁移而来的独立系统，包含 PC/Mobile 前端、管理员 API、普通用户 API、数据库 DDL 与数据迁移脚本。

## 应用结构

| 应用 | 目录 | 技术栈 | 默认端口 |
|---|---|---|---|
| PC 前端 | `frontend/pc-web` | Vue 3 + TypeScript + Vite | 5173 |
| Mobile 前端 | `frontend/mobile-web` | Vue 3 + TypeScript + Vite | 5174 |
| 管理员后端 | `backend/admin-api` | Java 21 + Spring Boot + MyBatis | 8081 |
| 普通用户后端 | `backend/user-api` | Java 21 + Spring Boot + MyBatis | 8082 |

公共模块位于 `frontend/packages/web-shared`、`backend/common-core` 和 `backend/common-security`。

当前仓库已包含认证与账户、系统设定、批处理管理、资料管理、临时文件、测试信息和链接剪贴等功能，以及相应的 DDL、初始化数据和从 2.0 迁移数据的脚本。

## 本地开发

前置条件与环境准备见 [docs/DEVELOPMENT_SETUP.md](docs/DEVELOPMENT_SETUP.md)。

```powershell
# 检查环境并安装前端依赖
powershell -ExecutionPolicy Bypass -File .\scripts\setup.ps1

# 启动全部开发服务
powershell -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1

# 停止全部开发服务
powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1
```

启动后可访问：

- PC：<http://localhost:5173>
- Mobile：<http://localhost:5174>
- admin-api health：<http://localhost:8081/api/admin/health>
- user-api health：<http://localhost:8082/api/user/health>

## 构建与测试

```powershell
# lint、类型检查、测试和构建
powershell -ExecutionPolicy Bypass -File .\scripts\build-all.ps1

# 仅运行测试
powershell -ExecutionPolicy Bypass -File .\scripts\test-all.ps1
```

依赖目录和构建产物不会纳入 Git；首次检出后需重新安装依赖并构建。

## 数据库与部署

- 数据库 DDL、初始化数据和迁移脚本位于 `database/`，按功能分类。
- Docker 部署说明见 [docs/DOCKER_DEPLOYMENT.md](docs/DOCKER_DEPLOYMENT.md)。
- 复制 `.env.example` 为 `.env` 后填写实际环境值；`.env` 已被 Git 忽略。
- `STUDY21_DATASOURCE_PASSWORD` 没有默认值，未设置时 Docker Compose 会直接报错。
- `data/` 是运行时文件存储目录，不提交到 Git。

## 文档

- [总体架构](docs/ARCHITECTURE.md)
- [开发环境](docs/DEVELOPMENT_SETUP.md)
- [前端指南](docs/FRONTEND_GUIDE.md)
- [后端指南](docs/BACKEND_GUIDE.md)
- [API 规范](docs/API_CONVENTIONS.md)
- [安全与角色](docs/SECURITY_AND_ROLES.md)
- [部署说明](docs/DEPLOYMENT.md)
- [Docker 部署](docs/DOCKER_DEPLOYMENT.md)
- [资料存储与迁移](docs/DOCUMENT_STORAGE_AND_MIGRATION.md)
- [迁移策略](docs/MIGRATION_STRATEGY.md)
- [角色功能矩阵](docs/ROLE_FUNCTION_MATRIX.md)
- [架构决策](docs/DECISIONS.md)

## 注意

旧系统目录只作为迁移参考，不应由本仓库脚本修改：

- `C:\work\Source\study2\study2`
- `C:\work\Source\study2\study2mobile`
- `C:\work\Source\study2\study2apk`

本项目为内部系统，未包含开源许可。
