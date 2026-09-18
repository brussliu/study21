package com.study21.admin.geometryai;

import com.study21.admin.batch.BatchTaskHandler;
import com.study21.admin.batch.BatchTaskRegistry;
import com.study21.admin.geometryai.dto.FigureMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * モード別バッチの登録（A〜D が独立して登録されていること）を Spring の起動で確かめる。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>4 つのモード別バッチ（batC51-A〜D）が**別々に**登録されている（入れ子のハンドラも走査される）</li>
 *   <li>歴史的な batC51 の入口も残っている（要求行が無いときの再実行・互換）</li>
 *   <li>バッチ定義の設定ページは GEOMETRY_AI（必須設定はモード共通の分）</li>
 * </ol>
 */
@SpringBootTest
class FigureBatchRegistrationTest {

    @Autowired
    private List<BatchTaskHandler> handlers;

    @Autowired
    private BatchTaskRegistry taskRegistry;

    @Test
    @DisplayName("4 つのモード別バッチと歴史的な batC51 が別々に登録されている")
    void registersFourModeBatches() {
        List<String> codes = handlers.stream().map(BatchTaskHandler::taskCode).toList();
        assertThat(codes).contains("batC51-A", "batC51-B", "batC51-C", "batC51-D");
        // 同じコードが 2 つ登録されていない（どちらが使われるか分からない状態を作らない）
        assertThat(codes).doesNotHaveDuplicates();
        // 歴史的なコードの入口も残す
        assertThat(codes).contains(FigureMode.LEGACY_TASK_CODE);
    }

    @Test
    @DisplayName("バッチ定義はモードごとに 1 つで、設定ページと必須設定を持つ")
    void batchDefinitionsCoverEveryMode() {
        for (FigureMode mode : FigureMode.values()) {
            var definition = taskRegistry.findByCode(mode.taskCode());
            assertThat(definition).as(mode.taskCode()).isNotNull();
            assertThat(definition.pageCode()).isEqualTo("GEOMETRY_AI");
            // モード別の項目は「未設定なら共通を継承」するので必須にしない
            assertThat(definition.requiredSettings())
                    .allMatch(requirement -> !requirement.settingKey().startsWith("GEOMETRY_AI_" + mode.name() + "_"));
            assertThat(definition.requiredSettings())
                    .anyMatch(requirement -> "GEOMETRY_AI_SYSTEM_PROMPT".equals(requirement.settingKey()));
        }
        assertThat(taskRegistry.findByCode(FigureMode.LEGACY_TASK_CODE)).isNotNull();
    }
}
