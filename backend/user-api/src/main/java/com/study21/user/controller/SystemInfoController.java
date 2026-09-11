package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.common.core.api.SystemInfoResponse;
import com.study21.user.service.SystemInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class SystemInfoController {

    private final SystemInfoService systemInfoService;

    public SystemInfoController(SystemInfoService systemInfoService) {
        this.systemInfoService = systemInfoService;
    }

    @GetMapping("/system/info")
    public ApiResponse<SystemInfoResponse> systemInfo() {
        return ApiResponse.ok(systemInfoService.getSystemInfo());
    }
}
