package com.study21.user.todo;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.common.security.exception.ForbiddenException;
import com.study21.user.account.AccountEntity;
import com.study21.user.account.AccountMapper;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TODO の業務処理（状態の進み方・親子・楽観ロック・保護者の代理作成）。
 *
 * <p>一覧とカレンダーは実機（`tmp/e2e/e2e-todo-*.mjs`）で通しで確かめているので、
 * ここでは「画面からは見えにくい決まりごと」を固定する。</p>
 */
class TodoServiceTest {

    private static final long STUDENT_ID = 2L;
    private static final long GUARDIAN_ID = 10L;
    private static final long TODO_ID = 100L;

    private TodoMapper todoMapper;
    private AccountMapper accountMapper;
    private TodoServiceImpl service;

    @BeforeEach
    void setUp() {
        todoMapper = mock(TodoMapper.class);
        accountMapper = mock(AccountMapper.class);
        service = new TodoServiceImpl(todoMapper, accountMapper);
    }

    private UserPrincipal student() {
        return new UserPrincipal(STUDENT_ID, "ljz@example.com", "劉競澤", AccountType.STUDENT);
    }

    private UserPrincipal guardian() {
        return new UserPrincipal(GUARDIAN_ID, "parent@example.com", "保護者", AccountType.GUARDIAN);
    }

    private TodoEntity entity(long todoId, long accountId, String status) {
        TodoEntity row = new TodoEntity();
        row.setTodoId(todoId);
        row.setAccountId(accountId);
        row.setStatus(status);
        row.setPriority("NORMAL");
        row.setTitle("数学の宿題");
        row.setVersion(1);
        return row;
    }

    private TodoModels.TodoSaveRequest save(String title, List<TodoModels.ChildRequest> children,
                                            Boolean alsoForStudent, Integer version) {
        return new TodoModels.TodoSaveRequest(title, "p.20-22", "NORMAL", LocalDate.of(2026, 9, 20),
                children, alsoForStudent, version);
    }

    /* ---------- 新規登録 ---------- */

    @Test
    void createInsertsParentAndChildren() {
        when(todoMapper.insert(any(TodoEntity.class))).thenAnswer(invocation -> {
            TodoEntity inserted = invocation.getArgument(0);
            if (inserted.getTodoId() == null) {
                inserted.setTodoId(inserted.getParentTodoId() == null ? TODO_ID : TODO_ID + 1);
            }
            return 1;
        });

        TodoModels.TodoMutationResult result = service.create(student(), save("ハリポタ",
                List.of(new TodoModels.ChildRequest("1章", null, "HIGH", null),
                        // タイトルが空の行は入れない（Excel 風グリッドの空行）
                        new TodoModels.ChildRequest("   ", null, "LOW", null)),
                null, null));

        assertThat(result.todoId()).isEqualTo(TODO_ID);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.message()).contains("登録しました");
        // 親 1 件 ＋ 子 1 件（空行は捨てる）
        verify(todoMapper, times(2)).insert(any(TodoEntity.class));
    }

    @Test
    void createRejectsABlankTitle() {
        assertThatThrownBy(() -> service.create(student(), save("   ", List.of(), null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("タイトルを入力してください");
        verify(todoMapper, never()).insert(any(TodoEntity.class));
    }

    @Test
    void guardianCanAlsoCreateTheSameTodoForTheStudent() {
        when(todoMapper.insert(any(TodoEntity.class))).thenAnswer(invocation -> {
            TodoEntity inserted = invocation.getArgument(0);
            if (inserted.getTodoId() == null) {
                inserted.setTodoId(inserted.getParentTodoId() == null ? TODO_ID : TODO_ID + 1);
            }
            return 1;
        });
        AccountEntity child = new AccountEntity();
        child.setAccountId(STUDENT_ID);
        when(accountMapper.findStudentByGuardianId(GUARDIAN_ID)).thenReturn(child);

        TodoModels.TodoMutationResult result = service.create(guardian(),
                save("ハリポタ", List.of(new TodoModels.ChildRequest("1章", null, "HIGH", null)), true, null));

        // 自分（保護者）と子どもの 2 件を作る
        assertThat(result.updatedCount()).isEqualTo(2);
        verify(todoMapper, times(4)).insert(any(TodoEntity.class));
    }

    @Test
    void alsoForStudentIsRejectedForAStudentAndForAGuardianWithoutAChild() {
        // 画面の順番どおり、まず自分の TODO を作ってから「お子さまにも」を判定する
        when(todoMapper.insert(any(TodoEntity.class))).thenAnswer(invocation -> {
            TodoEntity inserted = invocation.getArgument(0);
            inserted.setTodoId(TODO_ID);
            return 1;
        });

        assertThatThrownBy(() -> service.create(student(), save("数学", List.of(), true, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("保護者アカウント");

        when(accountMapper.findStudentByGuardianId(GUARDIAN_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.create(guardian(), save("数学", List.of(), true, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("お子さまのアカウントが見つからない");
    }

    /* ---------- 編集・削除 ---------- */

    @Test
    void updateReplacesChildrenAndUsesTheVersion() {
        when(todoMapper.findById(TODO_ID)).thenReturn(entity(TODO_ID, STUDENT_ID, "TODO"));
        when(todoMapper.update(eq(TODO_ID), eq("英語の宿題"), eq("p.20-22"), eq("NORMAL"),
                eq(LocalDate.of(2026, 9, 20)), eq(3), eq(STUDENT_ID))).thenReturn(1);

        service.update(student(), TODO_ID, save("英語の宿題", List.of(), null, 3));

        // バージョンを渡して更新し、子タスクは「送られた内容で入れ替える」
        verify(todoMapper).update(eq(TODO_ID), eq("英語の宿題"), eq("p.20-22"), eq("NORMAL"),
                eq(LocalDate.of(2026, 9, 20)), eq(3), eq(STUDENT_ID));
        verify(todoMapper).deleteChildren(TODO_ID);
    }

    @Test
    void updateWithAStaleVersionIsAConflict() {
        when(todoMapper.findById(TODO_ID)).thenReturn(entity(TODO_ID, STUDENT_ID, "TODO"));
        when(todoMapper.update(anyLong(), any(), any(), any(), any(), any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> service.update(student(), TODO_ID, save("英語", List.of(), null, 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("他の操作で先に更新されました");
        verify(todoMapper, never()).deleteChildren(anyLong());
    }

    @Test
    void deleteRejectsAnotherAccountsTodo() {
        when(todoMapper.findById(TODO_ID)).thenReturn(entity(TODO_ID, 999L, "TODO"));

        assertThatThrownBy(() -> service.delete(student(), TODO_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("TODOが見つかりません");
        verify(todoMapper, never()).delete(anyLong());
    }

    /* ---------- 状態の変更 ---------- */

    @Test
    void statusGoesTodoDoingDoneAndBack() {
        when(todoMapper.findById(TODO_ID)).thenReturn(entity(TODO_ID, STUDENT_ID, "TODO"));
        when(todoMapper.updateStatus(eq(TODO_ID), any(), any(), any(), any(), eq(STUDENT_ID))).thenReturn(1);

        // 未着手 → 進行中（開始日時が入る・完了日時は入らない）
        assertThat(service.updateStatus(student(), TODO_ID, "DOING", 1).message()).contains("進行中");
        verify(todoMapper).updateStatus(eq(TODO_ID), eq("DOING"), any(), isNull(), eq(1), eq(STUDENT_ID));

        // 進行中 → 完了（完了日時が入る）
        when(todoMapper.findChildren(List.of(TODO_ID))).thenReturn(List.of());
        assertThat(service.updateStatus(student(), TODO_ID, "DONE", 1).message()).contains("完了");
        verify(todoMapper).updateStatus(eq(TODO_ID), eq("DONE"), any(), any(), eq(1), eq(STUDENT_ID));

        // 完了 → 未着手（開始・完了日時を消す）
        assertThat(service.updateStatus(student(), TODO_ID, "TODO", 1).message()).contains("未着手");
        verify(todoMapper).updateStatus(eq(TODO_ID), eq("TODO"), isNull(), isNull(), eq(1), eq(STUDENT_ID));
    }

    @Test
    void completingAParentCompletesItsChildren() {
        when(todoMapper.findById(TODO_ID)).thenReturn(entity(TODO_ID, STUDENT_ID, "DOING"));
        when(todoMapper.updateStatus(eq(TODO_ID), any(), any(), any(), any(), eq(STUDENT_ID))).thenReturn(1);
        TodoEntity openChild = entity(TODO_ID + 1, STUDENT_ID, "TODO");
        TodoEntity doneChild = entity(TODO_ID + 2, STUDENT_ID, "DONE");
        when(todoMapper.findChildren(List.of(TODO_ID))).thenReturn(List.of(openChild, doneChild));

        service.updateStatus(student(), TODO_ID, "DONE", 1);

        // まだ終わっていない子だけ完了にする（すでに完了の子は触らない）
        verify(todoMapper, times(1)).updateStatus(eq(TODO_ID + 1), eq("DONE"), any(), any(), isNull(), eq(STUDENT_ID));
        verify(todoMapper, never()).updateStatus(eq(TODO_ID + 2), any(), any(), any(), any(), anyLong());
    }

    @Test
    void statusRejectsAnUnknownCode() {
        when(todoMapper.findById(TODO_ID)).thenReturn(entity(TODO_ID, STUDENT_ID, "TODO"));

        assertThatThrownBy(() -> service.updateStatus(student(), TODO_ID, "FINISHED", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("状態");
    }

    @Test
    void statusWithAStaleVersionIsAConflict() {
        when(todoMapper.findById(TODO_ID)).thenReturn(entity(TODO_ID, STUDENT_ID, "TODO"));
        when(todoMapper.updateStatus(anyLong(), any(), any(), any(), any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> service.updateStatus(student(), TODO_ID, "DOING", 1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("他の操作で先に更新されました");
    }

    /* ---------- 一覧 ---------- */

    @Test
    void searchNestsChildrenUnderTheirParentsAndCountsStatuses() {
        when(todoMapper.count(eq(STUDENT_ID), any(), any(), any(), eq(true), any(), any())).thenReturn(1L);
        when(todoMapper.search(eq(STUDENT_ID), any(), any(), any(), eq(true), any(), any(), eq(100), eq(0)))
                .thenReturn(List.of(entity(TODO_ID, STUDENT_ID, "TODO")));
        TodoEntity child = entity(TODO_ID + 1, STUDENT_ID, "DONE");
        child.setParentTodoId(TODO_ID);
        when(todoMapper.findChildren(List.of(TODO_ID))).thenReturn(List.of(child));
        when(todoMapper.countByStatus(STUDENT_ID, "TODO")).thenReturn(1L);
        when(todoMapper.countByStatus(STUDENT_ID, "DOING")).thenReturn(2L);
        when(todoMapper.countByStatus(STUDENT_ID, "DONE")).thenReturn(3L);

        TodoModels.TodoListResult result = service.search(student(), null, null, null, true, null, null, 1, 200);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).children()).hasSize(1);
        // 子タスクは完了/全体の数を親に集める（画面は「ハリポタ 1/1」と出す）
        assertThat(result.items().get(0).childCount()).isEqualTo(1);
        assertThat(result.items().get(0).doneChildCount()).isEqualTo(1);
        assertThat(result.openCount()).isEqualTo(1);
        assertThat(result.doingCount()).isEqualTo(2);
        assertThat(result.doneCount()).isEqualTo(3);
        // size の上限は 100
        assertThat(result.size()).isEqualTo(100);
    }
}
