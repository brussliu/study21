package com.study21.common.core.trace;

import org.slf4j.MDC;

/**
 * Trace ID 上下文。通过 SLF4J MDC 贯穿请求与日志。
 */
public final class TraceIdContext {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private TraceIdContext() {
    }

    public static void setTraceId(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static String getTraceId() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "" : value;
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
