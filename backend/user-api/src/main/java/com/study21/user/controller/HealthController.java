package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.common.core.api.HealthData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<HealthData> health() {
        return ApiResponse.ok(new HealthData("UP", "user-api"));
    }
}
