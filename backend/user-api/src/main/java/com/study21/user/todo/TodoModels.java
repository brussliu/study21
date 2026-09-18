package com.study21.user.todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * TODO のモデル。
 *
 * 親タスクと子タスクは同じ表（親TODOID の自己参照）。画面は一覧（親＋子を入れ子）と
 * カレンダー（期限日ごとの件数）の 2 つの見方を持つ。
 */
public final class TodoModels {

    private TodoModels() {
    }

    public static final List<String> STATUSES = List.of("TODO", "DOING", "DONE");
    public static final List<String> PRIORITIES = List.of("HIGH", "NORMAL", "LOW");

    /** 1 件（子タスクは children に入る）。 */
    public record TodoRow(
            long todoId,
            Long parentTodoId,
            int order,
            String title,
            String memo,
            String status,
            String priority,
            LocalDate dueDate,
            String startedAt,
            String completedAt,
            /** 作った人（保護者が作った場合は保護者の表示名） */
            String createdByName,
            /** 子タスクの数（親タスクのみ） */
            int childCount,
            /** 完了した子タスクの数 */
            int doneChildCount,
            int version,
            List<TodoRow> children) {
    }

    public record TodoListResult(
            List<TodoRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages,
            /** 状態ごとの件数（画面のカウンタ） */
            long openCount,
            long doingCount,
            long doneCount) {
    }

    /** 期限日ごとの件数（カレンダー）。 */
    /** カレンダー用の 1 行（SQL の結果。親子は問わない）。 */
    public record CalendarTaskRow(
            LocalDate dueDate,
            long todoId,
            String title,
            String status,
            String priority,
            Long parentTodoId) {
    }

    /** カレンダーのマスに出す 1 件（親子は問わない。child は親 TODO の子かどうか）。 */
    public record CalendarTask(long todoId, String title, String status, String priority, boolean child) {
    }

    /**
     * カレンダーのマス（1 日 = 1 セル）。
     * 件数だけでなく**その日の TODO の中身**も返す（画面のマスに並べるため）。
     */
    public record CalendarCell(LocalDate dueDate, long openCount, long doneCount, List<CalendarTask> tasks) {
    }

    public record CalendarResult(int year, int month, List<CalendarCell> cells) {
    }

    /** 子タスク 1 行（Excel 風グリッドの 1 行）。 */
    public record ChildRequest(
            @Size(max = 200, message = "タイトルは200文字以内で入力してください。") String title,
            @Size(max = 2000, message = "メモは2000文字以内で入力してください。") String memo,
            String priority,
            LocalDate dueDate) {
    }

    /** 新規／編集の内容。 */
    public record TodoSaveRequest(
            @NotBlank(message = "タイトルを入力してください。")
            @Size(max = 200, message = "タイトルは200文字以内で入力してください。") String title,
            @Size(max = 2000, message = "メモは2000文字以内で入力してください。") String memo,
            String priority,
            LocalDate dueDate,
            /** 子タスク（Excel 風グリッドの行）。空行は無視する */
            List<ChildRequest> children,
            /** 保護者のときだけ: お子さまの TODO にも同じ内容を登録する */
            Boolean alsoForStudent,
            /** 楽観的ロック用（編集時） */
            Integer version) {
    }

    public record TodoStatusRequest(
            @NotBlank(message = "状態を指定してください。") String status,
            /** 一覧が持っているバージョン。渡すと楽観ロックが効く（古い画面からの更新を弾く） */
            Integer version) {
    }

    public record TodoMutationResult(String message, Long todoId, int updatedCount) {
    }
}
