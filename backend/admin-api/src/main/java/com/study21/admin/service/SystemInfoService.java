package com.study21.admin.service;

import com.study21.common.core.api.SystemInfoResponse;
import com.study21.common.core.time.TimeUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SystemInfoService {

    private final String systemName;
    private final String version;
    private final String serviceName;
    private final String environment;

    public SystemInfoService(
            @Value("${app.system-name:Study 2.1}") String systemName,
            @Value("${app.version:0.1.0}") String version,
            @Value("${app.service-name:admin-api}") String serviceName,
            @Value("${app.environment:development}") String environment) {
        this.systemName = systemName;
        this.version = version;
        this.serviceName = serviceName;
        this.environment = environment;
    }

    public SystemInfoResponse getSystemInfo() {
        return new SystemInfoResponse(systemName, version, serviceName, environment, TimeUtil.nowIso8601());
    }
}
