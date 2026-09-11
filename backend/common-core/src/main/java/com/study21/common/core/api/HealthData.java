package com.study21.common.core.api;

/**
 * health 端点数据（非敏感信息）。
 */
public record HealthData(String status, String service) {
}
