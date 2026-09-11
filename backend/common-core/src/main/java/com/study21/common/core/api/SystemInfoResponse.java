package com.study21.common.core.api;

/**
 * system/info 端点数据（仅非敏感信息）。
 */
public record SystemInfoResponse(
        String systemName,
        String version,
        String serviceName,
        String environment,
        String timestamp) {
}
