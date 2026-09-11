package com.study21.common.core.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.study21.common.core.time.TimeUtil;
import com.study21.common.core.trace.TraceIdContext;

/**
 * 统一 API 响应结构。
 */
@JsonPropertyOrder({"success", "code", "message", "data", "traceId", "timestamp"})
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final String traceId;
    private final String timestamp;

    private ApiResponse(boolean success, String code, String message, T data, String traceId, String timestamp) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ErrorCode.OK.code(), ErrorCode.OK.defaultMessage(), data,
                TraceIdContext.getTraceId(), TimeUtil.nowIso8601());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, ErrorCode.OK.code(), message, data,
                TraceIdContext.getTraceId(), TimeUtil.nowIso8601());
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null,
                TraceIdContext.getTraceId(), TimeUtil.nowIso8601());
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data,
                TraceIdContext.getTraceId(), TimeUtil.nowIso8601());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode.code(), errorCode.defaultMessage());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
