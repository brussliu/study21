package com.study21.user.todo;

import com.study21.user.security.UserPrincipal;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * TODO の業務処理。
 *
 * 親タスクと子タスクは同じ表（親TODOID の自己参照）。持ち主はあくまで子どもで、
 * 保護者が作った場合は 作成者アカウントID に保護者を残す（リンククリップと同じ考え方）。
 */
public interface TodoService {

    /** 一覧（親タスク＋子タスク。状態ごとの件数も返す）。 */
    TodoModels.TodoListResult search(UserPrincipal user, String keyword, String status, String priority,
                                     boolean includeDone, LocalDate dueFrom, LocalDate dueTo, int page, int size);

    /** 期限日ごとの件数（カレンダー）。 */
    TodoModels.CalendarResult calendar(UserPrincipal user, YearMonth month);

    /** 新規（子タスクも同じリクエストで作る）。 */
    TodoModels.TodoMutationResult create(UserPrincipal user, TodoModels.TodoSaveRequest request);

    /** 編集（子タスクは入れ替える）。 */
    TodoModels.TodoMutationResult update(UserPrincipal user, long todoId, TodoModels.TodoSaveRequest request);

    /** 削除（子タスクも一緒に消える）。 */
    TodoModels.TodoMutationResult delete(UserPrincipal user, long todoId);

    /** 状態を変える（未着手 / 進行中 / 完了）。 */
    TodoModels.TodoMutationResult updateStatus(UserPrincipal user, long todoId, String status, Integer version);
}
