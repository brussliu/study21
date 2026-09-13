package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.security.UserPrincipal;
import com.study21.user.todo.TodoModels;
import com.study21.user.todo.TodoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * TODO API（user-api）。
 *
 * <ul>
 *   <li>`GET /todos` … 一覧（キーワード・状態・優先度・期限・完了も表示・ページング）</li>
 *   <li>`GET /todos/calendar?year=&amp;month=` … 期限日ごとの件数</li>
 *   <li>`POST /todos` … 新規（子タスクも同時に。保護者は「お子さまにも登録」可）</li>
 *   <li>`PUT /todos/{todoId}` … 編集（子タスクは入れ替え）／`DELETE` 削除</li>
 *   <li>`POST /todos/{todoId}/status` … 未着手・進行中・完了の切り替え</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public ApiResponse<TodoModels.TodoListResult> search(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "includeDone", defaultValue = "false") boolean includeDone,
            @RequestParam(value = "dueFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(value = "dueTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(todoService.search(user, keyword, status, priority, includeDone,
                dueFrom, dueTo, page, size));
    }

    @GetMapping("/calendar")
    public ApiResponse<TodoModels.CalendarResult> calendar(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        return ApiResponse.ok(todoService.calendar(user, YearMonth.of(year, month)));
    }

    @PostMapping
    public ApiResponse<TodoModels.TodoMutationResult> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody TodoModels.TodoSaveRequest request) {
        TodoModels.TodoMutationResult result = todoService.create(user, request);
        return ApiResponse.ok(result, result.message());
    }

    @PutMapping("/{todoId}")
    public ApiResponse<TodoModels.TodoMutationResult> update(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long todoId,
            @Valid @RequestBody TodoModels.TodoSaveRequest request) {
        TodoModels.TodoMutationResult result = todoService.update(user, todoId, request);
        return ApiResponse.ok(result, result.message());
    }

    @DeleteMapping("/{todoId}")
    public ApiResponse<TodoModels.TodoMutationResult> delete(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long todoId) {
        TodoModels.TodoMutationResult result = todoService.delete(user, todoId);
        return ApiResponse.ok(result, result.message());
    }

    @PostMapping("/{todoId}/status")
    public ApiResponse<TodoModels.TodoMutationResult> updateStatus(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long todoId,
            @Valid @RequestBody TodoModels.TodoStatusRequest request) {
        TodoModels.TodoMutationResult result =
                todoService.updateStatus(user, todoId, request.status(), request.version());
        return ApiResponse.ok(result, result.message());
    }
}
