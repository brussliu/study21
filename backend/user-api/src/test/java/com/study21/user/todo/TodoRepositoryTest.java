package com.study21.user.todo;

import com.study21.common.core.exception.ConflictException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する TODO の検証。
 *
 * <p>2.0 から移行した 169 件が入っているアカウント（2）を使い、**このテストで作った行だけ**を見る。
 * テストはロールバックするので DB は汚れない。</p>
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class TodoRepositoryTest {

    /** 2.0 から移行した TODO の持ち主（'ljz' = 劉競澤）。 */
    private static final long ACCOUNT_ID = 2L;
    /** 検証用のタイトル（移行データとぶつからない名前にする）。 */
    private static final String TITLE = "E2E: リポジトリ検証";

    @Autowired
    private TodoService todoService;

    private UserPrincipal owner() {
        return new UserPrincipal(ACCOUNT_ID, "ljz@example.com", "劉競澤", AccountType.STUDENT);
    }

    private TodoModels.TodoSaveRequest save(String title, List<TodoModels.ChildRequest> children, Integer version) {
        return new TodoModels.TodoSaveRequest(title, "メモ", "HIGH", LocalDate.of(2026, 12, 31),
                children, null, version);
    }

    /** 検証用の TODO を 1 件作って id を返す。 */
    private long createWithChildren(String title) {
        TodoModels.TodoMutationResult created = todoService.create(owner(), save(title, List.of(
                new TodoModels.ChildRequest("子1", null, "NORMAL", null),
                new TodoModels.ChildRequest("子2", null, "LOW", null)), null));
        return created.todoId();
    }

    @Test
    void createSearchUpdateStatusAndDeleteRoundTrip() {
        long todoId = createWithChildren(TITLE);

        // 作った行は検索で見つかる（子タスクも入れ子で返る）
        TodoModels.TodoListResult found = todoService.search(owner(), TITLE, null, null, false, null, null, 1, 20);
        assertThat(found.items()).hasSize(1);
        assertThat(found.items().get(0).title()).isEqualTo(TITLE);
        assertThat(found.items().get(0).children()).hasSize(2);
        assertThat(found.items().get(0).childCount()).isEqualTo(2);
        assertThat(found.items().get(0).doneChildCount()).isZero();
        assertThat(found.items().get(0).status()).isEqualTo("TODO");
        assertThat(found.items().get(0).priority()).isEqualTo("HIGH");
        assertThat(found.items().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        int version = found.items().get(0).version();

        // 編集: バージョンを渡すと通り、子タスクは送った内容に入れ替わる
        todoService.update(owner(), todoId, save(TITLE + "（編集）", List.of(
                new TodoModels.ChildRequest("子1", null, "NORMAL", null)), version));

        TodoModels.TodoListResult edited =
                todoService.search(owner(), TITLE + "（編集）", null, null, false, null, null, 1, 20);
        assertThat(edited.items()).hasSize(1);
        assertThat(edited.items().get(0).children()).hasSize(1);
        version = edited.items().get(0).version();

        // 古いバージョンで編集すると 409（楽観ロック）
        int staleVersion = version - 1;
        assertThatThrownBy(() -> todoService.update(owner(), todoId,
                save(TITLE + "（古い画面）", List.of(), staleVersion)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("他の操作で先に更新されました");

        // 状態: 未着手 → 進行中（開始日時が入る）
        todoService.updateStatus(owner(), todoId, "DOING", version);
        TodoModels.TodoRow doing = findOne(TITLE + "（編集）");
        assertThat(doing.status()).isEqualTo("DOING");
        assertThat(doing.startedAt()).isNotNull();
        assertThat(doing.completedAt()).isNull();

        // 状態: 進行中 → 完了（完了日時が入り、子タスクも完了になる）
        todoService.updateStatus(owner(), todoId, "DONE", doing.version());
        TodoModels.TodoRow done = findOne(TITLE + "（編集）");
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.completedAt()).isNotNull();
        assertThat(done.children()).isNotEmpty();
        assertThat(done.children()).allSatisfy(child -> assertThat(child.status()).isEqualTo("DONE"));
        assertThat(done.doneChildCount()).isEqualTo(done.childCount());

        // 状態: 完了 → 未着手（開始・完了日時を消す）
        todoService.updateStatus(owner(), todoId, "TODO", done.version());
        TodoModels.TodoRow back = findOne(TITLE + "（編集）");
        assertThat(back.status()).isEqualTo("TODO");
        assertThat(back.startedAt()).isNull();
        assertThat(back.completedAt()).isNull();

        // 状態もバージョンを渡さないと（古い画面からだと）弾かれる
        assertThatThrownBy(() -> todoService.updateStatus(owner(), todoId, "DONE", back.version() - 5))
                .isInstanceOf(ConflictException.class);

        // 削除すると子タスクも消える（FK の自己参照）
        todoService.delete(owner(), todoId);
        assertThat(todoService.search(owner(), TITLE + "（編集）", null, null, false, null, null, 1, 20).items())
                .isEmpty();
    }

    @Test
    void searchCountsMatchTheCreatedRows() {
        long todoId = createWithChildren(TITLE + "（件数）");

        TodoModels.TodoListResult result = todoService.search(owner(), TITLE + "（件数）", null, null, false,
                null, null, 1, 20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);

        // 状態ごとの件数は、作った行を「進行中」にすると DOING 側が増える
        long doingBefore = result.doingCount();
        todoService.updateStatus(owner(), todoId, "DOING", result.items().get(0).version());
        TodoModels.TodoListResult after = todoService.search(owner(), TITLE + "（件数）", null, null, false,
                null, null, 1, 20);
        assertThat(after.doingCount()).isEqualTo(doingBefore + 1);
    }

    @Test
    void calendarShowsTheDueDateOfTheCreatedRow() {
        long todoId = createWithChildren(TITLE + "（カレンダー）");
        TodoModels.TodoListResult created = todoService.search(owner(), TITLE + "（カレンダー）", null, null,
                false, null, null, 1, 20);
        assertThat(created.items()).hasSize(1);
        assertThat(created.items().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 12, 31));

        TodoModels.CalendarResult calendar = todoService.calendar(owner(), java.time.YearMonth.of(2026, 12));
        assertThat(calendar.cells()).anySatisfy(cell -> {
            assertThat(cell.dueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(cell.openCount()).isGreaterThanOrEqualTo(1);
        });
        assertThat(todoId).isPositive();
    }

    /** 検証用タイトルで 1 件引く。 */
    private TodoModels.TodoRow findOne(String title) {
        return todoService.search(owner(), title, null, null, true, null, null, 1, 20)
                .items().stream()
                .filter(item -> item.title().equals(title))
                .findFirst()
                .orElseThrow();
    }
}
