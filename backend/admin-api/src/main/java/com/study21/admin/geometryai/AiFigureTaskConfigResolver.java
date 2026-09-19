package com.study21.admin.geometryai;

import com.study21.admin.geometryai.processor.FigureProcessor;
import com.study21.common.core.geometryai.AiFigureConfig;
import org.springframework.stereotype.Component;

/**
 * **1 件の要求の設定を解決する唯一の入口**。
 *
 * <p>AI を呼ぶ工程（生成）も、検証の工程も、実行前の設定検証も、ここだけを通る。
 * 呼び出し側が「先にいまの設定を見てから、スナップショットを読む」という順番を取らないので、
 * 提出後に設定を消してもタスクが止まらない。</p>
 *
 * <p>決め方:</p>
 * <ol>
 *   <li><b>スナップショットがある</b> → それを使う（提出時に固定した版。いまの設定は見ない）。
 *       モードが要求と食い違う場合は失敗する（取り違えたまま走らせない）。</li>
 *   <li><b>スナップショットが無い（歴史的な要求）</b> → いまの設定から作り、
 *       **AI を呼ぶ前に要求行へ固定する**（{@link AiFigureTaskConfig#needsPin()}）。
 *       2 回目以降はその固定を使う（技術的な再試行で条件が変わらない）。</li>
 *   <li><b>スナップショットが壊れている・足りない・版が違う</b> →
 *       {@link AiFigureConfigException} で失敗する（いまの設定へは切り替えない）。</li>
 * </ol>
 *
 * <p>モデルは**スロット（`qwen:4`）とモデル名の両方**を固定する。モデル名を固定できていない
 * とき（提出時に `AI_MODEL` が未設定だった・前の版が書いたスナップショット）は、ここで解決して
 * 固定し直す（{@code needsPin}）。API Key と URL はスナップショットに入れず、実行のたびに
 * `AI_MODEL` から安全に読む。</p>
 */
@Component
public class AiFigureTaskConfigResolver {

    private final FigureProcessorSettings processorSettings;
    private final GeometryAiConnectionResolver connectionResolver;

    public AiFigureTaskConfigResolver(FigureProcessorSettings processorSettings,
                                      GeometryAiConnectionResolver connectionResolver) {
        this.processorSettings = processorSettings;
        this.connectionResolver = connectionResolver;
    }

    /** 解決した設定と接続。 */
    public record AiFigureTaskConfig(AiFigureConfig config,
                                     GeometryAiConnectionResolver.AiConnection connection,
                                     /** 要求行へ固定を書き込む必要があるか（無い要求・モデル未固定）。 */
                                     boolean needsPin,
                                     /** スナップショットがあったか（歴史的な要求かどうか）。 */
                                     boolean fromSnapshot) {
    }

    /**
     * 要求行の設定だけを解決する（**接続は解決しない**）。
     *
     * <p>検証の工程と実行前の設定検証が使う。AI を呼ばないので、`AI_MODEL` が未設定でも
     * ここでは失敗させない（接続の失敗は AI を呼ぶ工程が理由つきで書き戻す）。</p>
     */
    public AiFigureTaskConfig resolveConfig(FigureProcessor processor, GeometryAiRequestEntity entity) {
        return doResolve(processor, entity, false);
    }

    /** 要求行の設定と接続を解決する（壊れていれば {@link AiFigureConfigException}）。 */
    public AiFigureTaskConfig resolve(FigureProcessor processor, GeometryAiRequestEntity entity) {
        return doResolve(processor, entity, true);
    }

    private AiFigureTaskConfig doResolve(FigureProcessor processor, GeometryAiRequestEntity entity,
                                         boolean withConnection) {
        AiFigureConfig.SnapshotState state = AiFigureConfig.parseSnapshot(entity.getSettingsSnapshotJson());
        if (state.isBroken()) {
            throw new AiFigureConfigException("この要求に固定した設定を使えません（要求番号 "
                    + entity.getRequestNo() + "）: " + state.problem()
                    + " 固定した条件を復元できないため、いまの設定では実行しません。"
                    + "内容を直して送り直してください。");
        }

        AiFigureConfig config;
        boolean needsPin;
        if (state.isValid()) {
            config = state.config();
            String mode = config.mode();
            if (mode != null && !processor.mode().name().equals(mode)) {
                throw new AiFigureConfigException("この要求に固定した作図モード（" + mode
                        + "）と、実行するモード（" + processor.mode().name() + "）が違います（要求番号 "
                        + entity.getRequestNo() + "）。固定した条件のままでは実行できません。");
            }
            config = config.withTaskCode(processor.taskCode());
            needsPin = false;
        } else {
            // 歴史的な要求: いまの設定から作り、AI を呼ぶ前に固定する（設計 §6）
            config = processorSettings.liveResolve(processor);
            needsPin = true;
        }

        GeometryAiConnectionResolver.AiConnection connection = withConnection
                ? connectionResolver.resolve(processor.taskCode(), config.provider(), config.model())
                : null;
        if (withConnection && !config.hasPinnedModel()) {
            // モデル名を固定できていなかった（提出時に AI_MODEL が未設定だった等）。
            // いま解決できたモデル名を固定してから呼ぶ（次回の再試行でも同じモデルになる）
            config = config.withModel(connection.model());
            needsPin = true;
        }
        return new AiFigureTaskConfig(config, connection, needsPin, state.isValid());
    }
}
