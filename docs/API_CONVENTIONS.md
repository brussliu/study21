# API 规范

## 1. URL 规范

- admin-api：`/api/admin/**`
- user-api：`/api/user/**`
- 健康检查：`/api/{admin|user}/health`
- 系统信息：`/api/{admin|user}/system/info`
- Actuator：`/actuator/health`

未来业务 API 建议使用「领域/动作」命名（如 `/api/user/students/...`），待功能迁移时确定。

## 2. 统一响应

```json
{
  "success": true,
  "code": "OK",
  "message": "OK",
  "data": {},
  "traceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "timestamp": "2026-08-23T08:20:50.123Z"
}
```

错误响应：

```json
{
  "success": false,
  "code": "FORBIDDEN",
  "message": "アクセス権限がありません",
  "data": null,
  "traceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "timestamp": "2026-08-23T08:20:50.123Z"
}
```

## 3. 错误码

| code | 含义 |
|---|---|
| `VALIDATION_ERROR` | 参数校验失败 |
| `UNAUTHENTICATED` | 未认证 |
| `FORBIDDEN` | 无权限 |
| `NOT_FOUND` | 资源不存在 |
| `CONFLICT` | 冲突 |
| `INTERNAL_ERROR` | 服务器内部错误 |
| `NOT_IMPLEMENTED` | 未实现 |

前端客户端额外使用：`NETWORK_ERROR`、`TIMEOUT`、`CANCELED`（仅客户端本地错误）。

## 4. Trace ID

- 请求头 `X-Trace-Id` 透传；缺省时由后端生成 UUID。
- 响应头 `X-Trace-Id` 回写，响应体 `traceId` 与之一致。
- 后端日志通过 MDC 输出 traceId。

## 5. 日期格式

统一 ISO-8601（UTC），例如 `2026-08-23T08:20:50.123Z`。

## 6. 未来分页格式预留

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

本阶段无分页实现，仅预留类型定义（`PageResponse`）。
