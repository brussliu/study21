package com.study21.admin.englishessay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import com.study21.admin.setting.SettingRequirement;
import com.study21.common.core.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 英作文のバッチ（{@code batC11} = 画像分類・OCR・主題タイトル生成／{@code batC12} = 英検基準 AI 添削）。
 *
 * <p><b>有効／無効は利用者がバッチ一覧で切り替える</b>（{@code BatchTaskRegistry} の定義は
 * {@code false} のまま。ここでは触らない）。</p>
 *
 * <p>要求内容（{@code BAT_バッチ実行履歴情報.要求内容}）:</p>
 * <ul>
 *   <li>{@code batC11}: {@code {"essayId":900001}}（作文の画像を DB から読んで OCR する）</li>
 *   <li>{@code batC12}: {@code {"gradingId":501}}（積んである 1 件を実行）または
 *       {@code {"essayId":900001,"round":2}}（受付けてからその 1 件を実行）</li>
 * </ul>
 *
 * <p>{@code batC11} は同期の {@code POST /ocr} と<b>同じ {@link EnglishEssayOcrStep}</b>、
 * {@code batC12} は働き手と<b>同じ {@link EnglishEssayGradingStep}</b> を通る（道を分けない）。</p>
 */
public final class EnglishEssayAiBatchHandlers {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EnglishEssayAiBatchHandlers() {
    }

    /** 要求内容から英作文ID を取り出す（数値のときだけ。無ければ null）。 */
    static Long essayIdOf(BatchExecutionEntity execution) {
        return longOf(execution, "essayId");
    }

    /** 要求内容から添削ID を取り出す（数値のときだけ。無ければ null）。 */
    static Long gradingIdOf(BatchExecutionEntity execution) {
        return longOf(execution, "gradingId");
    }

    /** 要求内容から回数を取り出す（無ければ null＝受付が次の回を決める）。 */
    static Integer roundOf(BatchExecutionEntity execution) {
        JsonNode value = valueOf(execution, "round");
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private static Long longOf(BatchExecutionEntity execution, String key) {
        JsonNode value = valueOf(execution, key);
        return value != null && value.canConvertToLong() ? value.asLong() : null;
    }

    /** 要求内容の 1 項目（壊れた JSON は null）。 */
    private static JsonNode valueOf(BatchExecutionEntity execution, String key) {
        if (execution == null || execution.getRequestPayload() == null
                || execution.getRequestPayload().isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(execution.getRequestPayload()).path(key);
        } catch (Exception cause) {
            return null;
        }
    }

    /** {@code batC11}: 英作文 画像分類・OCR・主題タイトル生成。 */
    @Component
    public static class Ocr implements BatchTaskHandler {

        public static final String BATCH_CODE = EnglishEssayAiSettings.OCR_BATCH_CODE;

        private final EnglishEssayOcrStep step;

        public Ocr(EnglishEssayOcrStep step) {
            this.step = step;
        }

        @Override
        public String taskCode() {
            return BATCH_CODE;
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            Long essayId = essayIdOf(execution);
            if (essayId == null) {
                throw new ValidationException("要求内容に essayId を指定してください"
                        + "（例: {\"essayId\":900001}）。");
            }
            return String.valueOf(step.run(execution, essayId).get("message"));
        }
    }

    /** {@code batC12}: 英作文 英検基準 AI 添削。 */
    @Component
    public static class Grading implements BatchTaskHandler {

        public static final String BATCH_CODE = EnglishEssayAiSettings.GRADING_BATCH_CODE;

        private final EnglishEssayGradingStep step;
        private final EnglishEssayAiQueue queue;
        private final EnglishEssayAiMapper mapper;

        public Grading(EnglishEssayGradingStep step, EnglishEssayAiQueue queue, EnglishEssayAiMapper mapper) {
            this.step = step;
            this.queue = queue;
            this.mapper = mapper;
        }

        @Override
        public String taskCode() {
            return BATCH_CODE;
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            EnglishEssayAiMapper.GradingRow row = rowOf(execution);
            boolean succeeded = step.runItem(row, execution == null ? null : execution.getExecutionId());
            String head = "英作文 AI添削（第 " + row.getRound() + " 回）";
            return succeeded
                    ? head + "が完了しました。"
                    : head + "に失敗しました（理由は添削履歴の 失敗理由 を見てください）。";
        }

        /** 実行する 1 件を決める（gradingId 指定か、essayId で受付けてから）。 */
        private EnglishEssayAiMapper.GradingRow rowOf(BatchExecutionEntity execution) {
            Long gradingId = gradingIdOf(execution);
            if (gradingId == null) {
                Long essayId = essayIdOf(execution);
                if (essayId == null) {
                    throw new ValidationException("要求内容に gradingId か essayId を指定してください"
                            + "（例: {\"gradingId\":501} / {\"essayId\":900001,\"round\":2}）。");
                }
                gradingId = queue.accept(essayId, roundOf(execution)).gradingId();
            }
            EnglishEssayAiMapper.GradingRow row = mapper.findGrading(gradingId);
            if (row == null) {
                throw new ValidationException("添削が見つかりません: " + gradingId);
            }
            return row;
        }
    }

    /** 2 バッチが要求する設定（{@code BatchTaskRegistry} と突き合わせるための一覧）。 */
    public static List<SettingRequirement> requirements(String batchCode) {
        return new ArrayList<>(EnglishEssayAiSettings.requirements(batchCode));
    }
}
