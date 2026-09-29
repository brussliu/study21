package com.study21.admin.japanesewordai;

import com.study21.admin.batch.BatchTaskHandler;
import com.study21.admin.batch.BatchTaskRegistry;
import com.study21.admin.batch.BatchTaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日本語単語の AI 取得バッチ（batC41〜batC44）の登録を Spring の起動で確かめる。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>4 つのバッチが**別々に**登録されている（入れ子のハンドラも走査される）</li>
 *   <li>種別は C（呼出）。日本語単語の画面から {@code POST /api/admin/batch/japanese-word-ai/run}
 *       で呼ばれる（{@code rerunStep}）。画面の【再実行】ボタンは出さない</li>
 *   <li><b>種別 C の有効は「いま使っているか」の目印</b>（{@code docs/DECISIONS.md}）。
 *       実装済みで使っている C は**既定で有効**にする。無効のままだと一覧で「止まっている」ように
 *       読めてしまう（{@code batC41}〜{@code batC44} で実際に起きた）</li>
 *   <li>有効／無効は画面から切り替えられる（ハンドラがある＝実装済みの C だけ。
 *       {@code BatchControlServiceImplTest} が固定する）。無効にすると呼出が拒否される</li>
 *   <li>必須設定は設定ページ JAPANESE_WORD_AI の 4 バッチ分を持つ</li>
 * </ol>
 */
@SpringBootTest
class JapaneseWordAiBatchRegistrationTest {

    private static final List<String> BATCH_CODES = List.of("batC41", "batC42", "batC43", "batC44");

    @Autowired
    private List<BatchTaskHandler> handlers;

    @Autowired
    private BatchTaskRegistry taskRegistry;

    @Test
    @DisplayName("4 つのバッチのハンドラが登録されている（入れ子の static クラスも走査される）")
    void registersFourHandlers() {
        List<String> codes = handlers.stream().map(BatchTaskHandler::taskCode).toList();

        assertThat(codes).contains("batC41", "batC42", "batC43", "batC44");
        // 同じコードが 2 つ登録されていない（どちらが使われるか分からない状態を作らない）
        assertThat(codes.stream().filter(BATCH_CODES::contains).toList()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("4 つのバッチは種別 C（呼出）で、既定で有効（使っている C の目印）")
    void registersAsActiveCallBatches() {
        for (String code : BATCH_CODES) {
            var definition = taskRegistry.findByCode(code);
            assertThat(definition).as(code).isNotNull();
            assertThat(definition.taskType()).as(code).isEqualTo(BatchTaskType.C);
            // 「いま使っているか」の目印。使っている C は既定で有効（2026-09-19 の決定）
            assertThat(definition.active()).as("%s は既定で有効", code).isTrue();
            // 種別 C は画面から起動しない。有効／無効は**定義だけでは**切り替え不可だが、
            // 実装済み（ハンドラがある。このテストが上で確かめる）なので、画面からは切り替えられる
            assertThat(definition.canManualRerun()).as(code).isFalse();
            assertThat(definition.canToggleActive()).as(code).isFalse();
            // 起動時に AI を呼ばない（種別 C は S ではない）
            assertThat(definition.runsOnStartup()).as(code).isFalse();
        }
    }

    @Test
    @DisplayName("必須設定は設定ページ JAPANESE_WORD_AI の 4 バッチ分を持つ")
    void declaresRequiredSettings() {
        for (String code : BATCH_CODES) {
            var definition = taskRegistry.findByCode(code);
            assertThat(definition).as(code).isNotNull();
            assertThat(definition.pageCode()).as(code).isEqualTo("JAPANESE_WORD_AI");
            assertThat(definition.requiredSettings())
                    .as("%s の必須設定", code)
                    .anyMatch(requirement -> (JapaneseWordAiSettings.prefixOf(code) + "_SYSTEM_PROMPT")
                            .equals(requirement.settingKey()));
        }
    }
}
