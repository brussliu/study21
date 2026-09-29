package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.japanese.JapaneseModels;
import com.study21.user.japanese.JapaneseTestManagementService;
import com.study21.user.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/japanese/tests")
public class JapaneseTestManagementController {
    private final JapaneseTestManagementService service;
    public JapaneseTestManagementController(JapaneseTestManagementService service) { this.service = service; }

    @GetMapping("/conditions")
    public ApiResponse<Map<String, List<String>>> conditions(@RequestParam(required = false) String book) {
        return ApiResponse.ok(service.conditions(book));
    }

    @GetMapping("/search")
    public ApiResponse<JapaneseModels.TestListResult> search(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) String state, @RequestParam(required = false) String testType,
            @RequestParam(required = false) String book, @RequestParam(required = false) String categoryFrom,
            @RequestParam(required = false) String categoryTo, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        return ApiResponse.ok(service.search(user.accountId(), state, testType, book, categoryFrom, categoryTo, page, size));
    }

    public record MultipleRequest(@NotNull @Size(min = 1, max = 20) List<JapaneseModels.@NotNull @Valid TestCreateRequest> rows) { }

    @PostMapping("/multiple")
    public ApiResponse<JapaneseTestManagementService.MultipleResult> multiple(@AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody MultipleRequest request) {
        return ApiResponse.ok(service.create(user, request.rows()));
    }
}

