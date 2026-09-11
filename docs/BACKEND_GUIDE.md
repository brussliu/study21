# 后端指南

## 1. admin-api 职责

管理员后端（端口 8081，前缀 `/api/admin`）。本阶段仅实现：

- `GET /api/admin/health`
- `GET /api/admin/system/info`
- `GET /actuator/health`

`system/info` 仅返回非敏感信息（systemName / version / serviceName / environment / timestamp）。

## 2. user-api 职责

普通用户后端（端口 8082，前缀 `/api/user`），供学生与家长使用。本阶段仅实现：

- `GET /api/user/health`
- `GET /api/user/system/info`
- `GET /actuator/health`

不实现具体学生或家长业务 API。

## 3. 公共模块边界

### common-core

只包含：API 统一响应、错误码、异常类型、全局异常处理基础、Trace ID、时间格式、通用常量、无业务含义的工具。

### common-security

只建立安全基础抽象：Role 枚举、CurrentUser、AuthContext 接口、PermissionChecker 接口、未认证/无权限异常、Security 基础配置工具。

**不实现**：用户/角色/权限数据库查询、真实登录、JWT 签发、Session 持久化、假账号登录。

## 4. Controller 规范

- 使用 `@RestController` + `@RequestMapping("/api/...")`。
- 返回 `ApiResponse<T>`（由 `common-core` 提供），不直接返回裸对象。
- 不编写 try/catch 异常响应，统一交给 `GlobalExceptionHandler`。
- 不在 Controller 中散落认证/权限逻辑。

## 5. Service 规范

- `@Service` 注解，构造器注入依赖。
- 不含数据库访问（本阶段无数据库）。
- 不打印敏感信息。

## 6. 未来 Repository 位置

数据库接入后，Repository 实现应放在各应用的 `repository` 包（如 `com.study21.admin.repository`），
或新建独立数据访问模块。**本阶段不创建任何 Repository 数据库实现。**

## 7. 当前无数据库

两个后端在没有数据库、没有数据库环境变量的情况下可正常启动。
不包含 Flyway、Liquibase、H2、Spring Data JPA、Spring JDBC 数据库实现。
