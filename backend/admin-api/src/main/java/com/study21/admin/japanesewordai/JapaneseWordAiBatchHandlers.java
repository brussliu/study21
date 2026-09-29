package com.study21.admin.japanesewordai;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import com.study21.admin.setting.SettingRequirement;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 日本語単語の AI 取得バッチ（{@code batC41}〜{@code batC44}）。
 *
 * <p>4 つは「取得区分」だけが違う同じ形なので、入れ子の static クラスで 4 つ登録する
 * （{@code FigureGenerateBatchHandlers} と同じ作り。Spring は入れ子の static クラスも拾う）。</p>
 *
 * <table>
 *   <caption>バッチと取得区分</caption>
 *   <tr><th>バッチ</th><th>取得区分</th><th>作る内容</th></tr>
 *   <tr><td>{@code batC41}</td><td>{@code DETAIL}</td><td>語の詳細（A・B 共通）→ {@code JPN_単語詳細情報}</td></tr>
 *   <tr><td>{@code batC42}</td><td>{@code C}</td><td>C1 読み・C2 漢字の問題</td></tr>
 *   <tr><td>{@code batC43}</td><td>{@code D}</td><td>D 文脈意味の問題</td></tr>
 *   <tr><td>{@code batC44}</td><td>{@code E}</td><td>E 漢字用法の問題</td></tr>
 * </table>
 *
 * <p>対象の語は {@code 要求内容}（{@code {"wordIds":[1,2]}}）で絞れる。無ければ
 * 「まだ成功していない語」を SQL が選ぶ（画面の【AI 一括取得】から呼ばれる形）。</p>
 */
public final class JapaneseWordAiBatchHandlers {

    private JapaneseWordAiBatchHandlers() {
    }

    /** 要求内容から対象の語を取り出す（{@code {"wordIds":[...]}}。無ければ空）。 */
    static List<Long> wordIdsOf(BatchExecutionEntity execution) {
        if (execution == null || execution.getRequestPayload() == null
                || execution.getRequestPayload().isBlank()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(execution.getRequestPayload());
            com.fasterxml.jackson.databind.JsonNode ids = root.path("wordIds");
            List<Long> list = new java.util.ArrayList<>();
            if (ids.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode id : ids) {
                    if (id.canConvertToLong()) {
                        list.add(id.asLong());
                    }
                }
            }
            return list;
        } catch (Exception cause) {
            return List.of();
        }
    }

    /** batC41（語の詳細。A・B 共通）。 */
    @Component
    public static class Detail implements BatchTaskHandler {

        public static final String BATCH_CODE = "batC41";

        private final JapaneseWordAiStep step;

        public Detail(JapaneseWordAiStep step) {
            this.step = step;
        }

        @Override
        public String taskCode() {
            return BATCH_CODE;
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            return String.valueOf(step.run(execution, BATCH_CODE, "DETAIL", wordIdsOf(execution)).get("message"));
        }
    }

    /** batC42（C 読み・漢字問題）。 */
    @Component
    public static class Reading implements BatchTaskHandler {

        public static final String BATCH_CODE = "batC42";

        private final JapaneseWordAiStep step;

        public Reading(JapaneseWordAiStep step) {
            this.step = step;
        }

        @Override
        public String taskCode() {
            return BATCH_CODE;
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            return String.valueOf(step.run(execution, BATCH_CODE, "C", wordIdsOf(execution)).get("message"));
        }
    }

    /** batC43（D 文脈意味問題）。 */
    @Component
    public static class Context implements BatchTaskHandler {

        public static final String BATCH_CODE = "batC43";

        private final JapaneseWordAiStep step;

        public Context(JapaneseWordAiStep step) {
            this.step = step;
        }

        @Override
        public String taskCode() {
            return BATCH_CODE;
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            return String.valueOf(step.run(execution, BATCH_CODE, "D", wordIdsOf(execution)).get("message"));
        }
    }

    /** batC44（E 漢字用法問題）。 */
    @Component
    public static class KanjiUsage implements BatchTaskHandler {

        public static final String BATCH_CODE = "batC44";

        private final JapaneseWordAiStep step;

        public KanjiUsage(JapaneseWordAiStep step) {
            this.step = step;
        }

        @Override
        public String taskCode() {
            return BATCH_CODE;
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            return String.valueOf(step.run(execution, BATCH_CODE, "E", wordIdsOf(execution)).get("message"));
        }
    }

    /** 4 バッチが要求する設定（{@code BatchTaskRegistry} と突き合わせるための一覧）。 */
    public static List<SettingRequirement> requirements(String batchCode) {
        return JapaneseWordAiSettings.requirements(batchCode);
    }
}
