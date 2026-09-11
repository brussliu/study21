package com.study21.common.core.time;

import java.time.Instant;

/**
 * 时间工具。统一使用 ISO-8601（UTC）。
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static String nowIso8601() {
        return Instant.now().toString();
    }
}
