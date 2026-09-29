package com.study21.admin.controller;

import com.study21.admin.batch.BatchService;
import com.study21.admin.japanesewordai.JapaneseWordAiQueue;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.api.ApiResponse;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日本語単語の AI 取得の**受付**（{@code POST /api/admin/batch/japanese-word-ai/run}）。
 *
 * <p>確かめる接縫はこの 1 点: <b>取得区分をバッチコードへ直し、語をそのまま待ち行列へ渡して
 * すぐ返す</b>（AI を呼ばない＝画面が待たない。2026-09-27 に同期実行から受付へ変更）。</p>
 */
class JapaneseWordAiBatchControllerTest {

    private JapaneseWordAiQueue queue;
    private BatchService batchService;
    private SettingsService settingsService;
    private JapaneseWordAiBatchController controller;

    @BeforeEach
    void setUp() {
        queue = mock(JapaneseWordAiQueue.class);
        settingsService = mock(SettingsService.class);
        batchService = mock(BatchService.class);
        controller = new JapaneseWordAiBatchController(queue, settingsService, batchService);
        when(queue.accept(anyString(), any())).thenReturn(new LinkedHashMap<>(Map.of(
                "accepted", 2, "reused", 0)));
        // 受付の上限は**設定ページの「1 回の最大単語数」**（コード側に既定値は無い）
        when(settingsService.requireGlobal(anyString(), anyString(), anyString())).thenReturn("20");
    }

    private static Map<String, Object> request(String kind, Object wordIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (kind != null) {
            body.put("kind", kind);
        }
        if (wordIds != null) {
            body.put("wordIds", wordIds);
        }
        return body;
    }

    @Test
    @DisplayName("4 つの取得区分が、それぞれのバッチコードで受付される")
    void mapsKindToBatchCode() {
        assertThat(controller.run(request("DETAIL", List.of(101L))).getData())
                .containsEntry("batchCode", "batC41").containsEntry("kind", "DETAIL");
        assertThat(controller.run(request("C", List.of(101L))).getData())
                .containsEntry("batchCode", "batC42");
        assertThat(controller.run(request("D", List.of(101L))).getData())
                .containsEntry("batchCode", "batC43");
        assertThat(controller.run(request("E", List.of(101L))).getData())
                .containsEntry("batchCode", "batC44");

        ArgumentCaptor<String> kind = ArgumentCaptor.forClass(String.class);
        verify(queue, org.mockito.Mockito.times(4)).accept(kind.capture(), any());
        assertThat(kind.getAllValues()).containsExactly("DETAIL", "C", "D", "E");
    }

    @Test
    @DisplayName("受付だけして、すぐ返す（AI の結果は待たない）")
    void acceptsAndReturnsImmediately() {
        ApiResponse<Map<String, Object>> response = controller.run(request("DETAIL", List.of(101L, 102L)));

        assertThat(response.isSuccess()).isTrue();
        // 受付したことと、あとで結果を見る場所（バッチ管理画面）を伝える
        assertThat(response.getMessage()).contains("受付");
        assertThat(response.getData()).containsEntry("accepted", 2).containsEntry("reused", 0);
        assertThat(response.getData()).containsEntry("wordIds", List.of(101L, 102L));
        // 画面は取得中を出すので、結果の要約（成功/失敗）は返さない
        assertThat(response.getData()).doesNotContainKey("succeeded");
    }

    @Test
    @DisplayName("選んだ語をそのまま待ち行列へ渡す（数字の文字列も受ける）")
    void passesWordIds() {
        controller.run(request("C", List.of(101L, "102", "x", 103)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> wordIds = ArgumentCaptor.forClass(List.class);
        verify(queue).accept(org.mockito.ArgumentMatchers.eq("C"), wordIds.capture());
        assertThat(wordIds.getValue()).containsExactly(101L, 102L, 103L);
    }

    @Test
    @DisplayName("語を指定しなければ、受付もしない（対象を選ぶのは画面の役目）")
    void rejectsEmptyWordIds() {
        assertThatThrownBy(() -> controller.run(request("DETAIL", null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("語");
        assertThatThrownBy(() -> controller.run(request("DETAIL", List.of())))
                .isInstanceOf(ValidationException.class);
        verify(queue, never()).accept(anyString(), any());
    }

    @Test
    @DisplayName("取得区分が無い・知らない区分は拒否する（受付ける前に止める）")
    void rejectsUnknownKind() {
        assertThatThrownBy(() -> controller.run(request(null, List.of(101L))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("取得区分");
        assertThatThrownBy(() -> controller.run(request("X", List.of(101L))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> controller.run(null))
                .isInstanceOf(ValidationException.class);
        verify(queue, never()).accept(anyString(), any());
    }

    @Test
    @DisplayName("1 回の受付数を超えたら拒否する（上限は設定値。語数を分けて受付ける）")
    void rejectsTooManyWords() {
        // 設定の「1 回の最大単語数」＝20 語
        when(settingsService.requireGlobal("batC41", "JAPANESE_WORD_AI", "BAT_C41_BATCH_MAX"))
                .thenReturn("20");
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 21).boxed().toList();

        assertThatThrownBy(() -> controller.run(request("DETAIL", tooMany)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("20 語まで");
        verify(queue, never()).accept(anyString(), any());
    }

    @Test
    @DisplayName("設定が読めないときは受付しない（コード側の既定値へは落とさない）")
    void rejectsWhenLimitSettingMissing() {
        // findGlobal は使わない。requireGlobal が「未設定」で例外にする
        when(settingsService.requireGlobal("batC41", "JAPANESE_WORD_AI", "BAT_C41_BATCH_MAX"))
                .thenThrow(new ValidationException(
                        "BAT_C41_BATCH_MAX を設定してください（設定ページの「1 回の最大単語数」）。"));

        assertThatThrownBy(() -> controller.run(request("DETAIL", List.of(101L))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BAT_C41_BATCH_MAX");
        verify(queue, never()).accept(anyString(), any());
    }

    @Test
    @DisplayName("取得区分ごとの「1 回の受付の上限」を、設定ページの値から返す（画面の窓に出す）")
    void returnsLimitsFromSettings() {
        // 設定は**設定キー**（BAT_C41_BATCH_MAX）で読む。画面のフィールドキー（c25BatchMax）ではない
        when(settingsService.requireGlobal("batC41", "JAPANESE_WORD_AI", "BAT_C41_BATCH_MAX")).thenReturn("10");
        when(settingsService.requireGlobal("batC42", "JAPANESE_WORD_AI", "BAT_C42_BATCH_MAX")).thenReturn("200");
        when(settingsService.requireGlobal("batC43", "JAPANESE_WORD_AI", "BAT_C43_BATCH_MAX")).thenReturn("30");
        when(settingsService.requireGlobal("batC44", "JAPANESE_WORD_AI", "BAT_C44_BATCH_MAX")).thenReturn("50");

        Map<String, Map<String, Object>> limits = controller.limits().getData();

        assertThat(limits).containsOnlyKeys("DETAIL", "C", "D", "E");
        // 設定の「1 回の最大単語数」を**そのまま**使う（コード側の頭打ちは無い）
        assertThat(limits.get("DETAIL")).containsEntry("batchCode", "batC41").containsEntry("limit", 10);
        assertThat(limits.get("C")).containsEntry("batchCode", "batC42").containsEntry("limit", 200);
        assertThat(limits.get("D")).containsEntry("batchCode", "batC43").containsEntry("limit", 30);
        assertThat(limits.get("E")).containsEntry("batchCode", "batC44").containsEntry("limit", 50);
    }

    @Test
    @DisplayName("設定が読めないときは上限も返せない（コード側の既定値に落とさない）")
    void failsLimitsWhenSettingMissing() {
        when(settingsService.requireGlobal("batC43", "JAPANESE_WORD_AI", "BAT_C43_BATCH_MAX"))
                .thenThrow(new ValidationException("BAT_C43_BATCH_MAX を設定してください。"));

        assertThatThrownBy(() -> controller.limits())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BAT_C43_BATCH_MAX");
    }

    @Test
    @DisplayName("バッチが無効なら受け付けない（バッチ一覧のスイッチをそのまま効かせる）")
    void rejectsDisabledBatch() {
        org.mockito.Mockito.doThrow(new ValidationException(
                "バッチが無効に設定されています: batC41（バッチ一覧で有効にしてください）"))
                .when(batchService).requireCallable("batC41");

        assertThatThrownBy(() -> controller.run(request("DETAIL", List.of(101L))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("batC41");
        // 無効なら積まない（AI の呼び出しも起きない）
        verify(queue, never()).accept(anyString(), any());
    }
}
