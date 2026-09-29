package com.study21.admin.englishessay;

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
 * 英作文のバッチ（{@code batC11} = OCR・{@code batC12} = 添削）の登録を Spring の起動で確かめる。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>ハンドラが 2 つとも登録されている（同じコードが二重に無い）</li>
 *   <li>種別は C（呼出）。<b>有効／無効は利用者がバッチ一覧で切り替える</b>ので、
 *       ここでは既定値（{@code false}）を変えない</li>
 *   <li>必須設定は設定ページ {@code ENGLISH_ESSAY} の、そのバッチのキーを指す</li>
 * </ol>
 */
@SpringBootTest
class EnglishEssayAiBatchRegistrationTest {

    private static final List<String> BATCH_CODES = List.of("batC11", "batC12");

    @Autowired
    private List<BatchTaskHandler> handlers;

    @Autowired
    private BatchTaskRegistry taskRegistry;

    @Test
    @DisplayName("2 つのハンドラが登録されている（入れ子の static クラスも走査される）")
    void registersHandlers() {
        List<String> codes = handlers.stream().map(BatchTaskHandler::taskCode).toList();

        assertThat(codes).contains("batC11", "batC12");
        assertThat(codes.stream().filter(BATCH_CODES::contains).toList()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("種別 C（呼出）で、定義の active は触らない（利用者が有効にする）")
    void keepsRegistryDefaults() {
        for (String code : BATCH_CODES) {
            var definition = taskRegistry.findByCode(code);
            assertThat(definition).as(code).isNotNull();
            assertThat(definition.taskType()).as(code).isEqualTo(BatchTaskType.C);
            assertThat(definition.active()).as(code).isFalse();
            assertThat(definition.canManualRerun()).as(code).isFalse();
            assertThat(definition.runsOnStartup()).as(code).isFalse();
        }
    }

    @Test
    @DisplayName("必須設定は設定ページ ENGLISH_ESSAY の、そのバッチのキーを指す")
    void declaresRequiredSettings() {
        var ocr = taskRegistry.findByCode("batC11");
        assertThat(ocr.pageCode()).isEqualTo("ENGLISH_ESSAY");
        assertThat(ocr.requiredSettings()).extracting(requirement -> requirement.settingKey())
                .contains("ENGLISH_ESSAY_OCR_PROMPT", "ENGLISH_ESSAY_TITLE_PROMPT",
                        "ENGLISH_ESSAY_MAX_IMAGES", "ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS",
                        "ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS", "ENGLISH_ESSAY_OCR_TEMPERATURE");

        var grading = taskRegistry.findByCode("batC12");
        assertThat(grading.pageCode()).isEqualTo("ENGLISH_ESSAY");
        assertThat(grading.requiredSettings()).extracting(requirement -> requirement.settingKey())
                .contains("ENGLISH_ESSAY_GRADING_PROMPT", "ENGLISH_ESSAY_GRADING_USER_PROMPT",
                        "ENGLISH_ESSAY_GRADING_RETRY_LIMIT",
                        "ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS", "ENGLISH_ESSAY_GRADING_TEMPERATURE");

        // ハンドラが宣言する設定と、レジストリの宣言が食い違わない
        assertThat(EnglishEssayAiBatchHandlers.requirements("batC11"))
                .extracting(requirement -> requirement.settingKey())
                .isSubsetOf(ocr.requiredSettings().stream()
                        .map(requirement -> requirement.settingKey()).toList());
        assertThat(EnglishEssayAiBatchHandlers.requirements("batC12"))
                .extracting(requirement -> requirement.settingKey())
                .isSubsetOf(grading.requiredSettings().stream()
                        .map(requirement -> requirement.settingKey()).toList());
    }
}
