package com.study21.admin.geometryai;

import com.study21.admin.geometryai.dto.FigureMode;
import com.study21.admin.geometryai.dto.FigureOutcome;
import com.study21.admin.geometryai.dto.FigureOutputDto;
import com.study21.admin.geometryai.dto.FigureOutputType;
import com.study21.admin.geometryai.processor.FigureProcessor;
import com.study21.admin.geometryai.processor.FigureProcessorRegistry;
import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import com.study21.common.core.geometryai.AiFigureConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 生図の**検証工程**（**AI を呼ばない**・決定的・ミリ秒。バッチではない）。
 *
 * <ol>
 *   <li>AI の出力（モード別 DTO）を読み直す</li>
 *   <li>{@link FigureOutputValidator} で判定する（結果種別の尊重・種類ごとの必要項目・コマンドの検証）</li>
 *   <li>結果で状態を分ける:
 *     <ul>
 *       <li>作図できる → `READY`（**生成済み・確認待ち**。作図画面で確認できる）</li>
 *       <li>情報が足りない → `NEEDS_INPUT`（**追加入力待ち**。質問を保存し、利用者が答えて送り直す）</li>
 *       <li>対応できない → `FAILED(UNSUPPORTED)`（理由を画面に出す）</li>
 *       <li>コマンドが方針に合わない → `FAILED(VALIDATE)`（検証エラー内容を残す）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><strong>ここで確かめているのは「コマンドの規則」（許可リスト・長さ・個数・文字種）だけ</strong>で、
 * 図形が数学的に正しいかは確かめていない（GeoGebra は作図画面のブラウザにしか無い）。
 * そのため「数学的な条件まで検証した」と言える文言を画面へ出さない。</p>
 *
 * <p>検証に使う許可リストと上限は、**要求行に固定した設定**（受付時のスナップショット）を使う。
 * 待ち行列に並んでいる間に設定を変えても、その要求の検証条件は変わらない。スナップショットが無い
 * （歴史的な）行だけ、いまの設定を読む。</p>
 *
 * <p><strong>冪等</strong>: `READY` 以降・`NEEDS_INPUT`・`UNSUPPORTED` なら何もしない。
 * `GENERATED`（検証待ち）と、落ちたままの `VALIDATING`（**AI を呼び直さない**）を拾う。</p>
 */
@Component
public class AiFigureValidateStep {

    private static final Logger log = LoggerFactory.getLogger(AiFigureValidateStep.class);

    /** バッチの必須設定（スナップショットが無い行だけが使う）。 */
    public static final List<SettingRequirement> REQUIRED_SETTINGS = List.of(
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_ENABLED"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_ALLOWED_COMMANDS"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_MAX_COMMANDS"));

    private final GeometryAiRequestMapper requestMapper;
    private final GeometryAiRequestRecorder recorder;
    private final FigureOutputValidator validator;
    private final FigureProcessorRegistry processorRegistry;
    private final SettingsService settingsService;

    public AiFigureValidateStep(GeometryAiRequestMapper requestMapper,
                                GeometryAiRequestRecorder recorder,
                                FigureOutputValidator validator,
                                FigureProcessorRegistry processorRegistry,
                                SettingsService settingsService) {
        this.requestMapper = requestMapper;
        this.recorder = recorder;
        this.validator = validator;
        this.processorRegistry = processorRegistry;
        this.settingsService = settingsService;
    }

    /** 1 件を検証して確定する。 */
    public Map<String, Object> run(long aiRequestId) {
        GeometryAiRequestEntity entity = requestMapper.findById(aiRequestId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (entity == null) {
            result.put("skipped", true);
            result.put("message", "対象がありませんでした（AI 生図が見つかりません）。");
            return result;
        }
        result.put("aiRequestId", entity.getRequestId());
        result.put("requestNo", entity.getRequestNo());

        if (!isValidateTarget(entity)) {
            result.put("skipped", true);
            result.put("message", "既に確定済みのためスキップしました。（" + entity.getRequestNo() + "）");
            return result;
        }

        FigureMode mode = FigureMode.of(entity.getMode()).orElse(FigureMode.A);
        FigureProcessor processor = processorRegistry.of(mode);

        try {
            // 検証中にする（段階が画面に見えるように。ここで落ちても下で失敗として書く）
            entity.setValidateExecutionId(null);
            recorder.markValidating(entity);
            return validate(entity, processor, result);
        } catch (AiFigurePreprocessStep.GeometryAiStepException cause) {
            // この中で既に理由を書いてある（二重に書かない）
            throw cause;
        } catch (SettingsValidationException cause) {
            fail(entity, "CONFIG", "AI の設定が不足しています。検証の条件（許可コマンド・上限）を"
                    + "確認してください。" + cause.getMessage());
            return result;
        } catch (Exception cause) {
            fail(entity, "UNEXPECTED", "検証の途中で想定外の失敗がありました（"
                    + cause.getClass().getSimpleName() + "）: " + messageOf(cause));
            return result;
        }
    }

    /** 検証の本体（設定の読み出し → 判定 → 状態の確定）。 */
    private Map<String, Object> validate(GeometryAiRequestEntity entity, FigureProcessor processor,
                                         Map<String, Object> result) {
        Limits limits = limitsOf(entity, processor);

        FigureOutputDto output = AiResponseDtoParser
                .parseContent(entity.getProposalJson(), processor.dtoClass())
                .orElse(null);
        if (output == null) {
            // AI の出力（提案 JSON）が読めない＝生成のやり直しが要る（**検証の失敗ではない**）
            String message = "AI の出力を読み直せませんでした。もう一度生成してください。";
            entity.setFailedStage("GENERATE");
            entity.setErrorCode("NO_PROPOSAL");
            entity.setErrorMessage(message);
            entity.setValidationError(message);
            recorder.updateFailed(entity);
            log.warn("geometry-ai proposal is unreadable. requestId={}", entity.getRequestId());
            throw new AiFigurePreprocessStep.GeometryAiStepException(message);
        }

        FigureOutputType requested = AiFigureGenerateStep.requestedOutputTypeOf(entity, processor);
        FigureOutputValidator.Verdict verdict = validator.validate(output, requested,
                limits.allowedCommands(), limits.maxCommands());
        result.put("outcome", verdict.outcome().name());
        result.put("resolvedOutputType", verdict.resolvedOutputType() == null
                ? null : verdict.resolvedOutputType().name());

        // 追加入力待ち（AI が質問を返した。**失敗ではない**）
        if (verdict.needsInput()) {
            entity.setOutcome(FigureOutcome.NEEDS_INPUT.name());
            entity.setQuestionsJson(AiResponseDtoParser.toJson(verdict.questions()));
            entity.setResolvedOutputType(verdict.resolvedOutputType() == null
                    ? null : verdict.resolvedOutputType().name());
            entity.setFigureType(verdict.figureType());
            recorder.updateNeedsInput(entity);
            result.put("success", true);
            result.put("message", "AI が確認を求めています。（" + entity.getRequestNo() + "）");
            log.info("geometry-ai needs input. requestId={} questions={}",
                    entity.getRequestId(), verdict.questions().size());
            return result;
        }

        // 対応できない（失敗として理由を残す。もう一度生成でも直らないので画面は説明を出す）
        if (verdict.isUnsupported()) {
            entity.setOutcome(FigureOutcome.UNSUPPORTED.name());
            entity.setFailedStage("VALIDATE");
            entity.setErrorCode("UNSUPPORTED");
            entity.setErrorMessage(verdict.reason());
            entity.setValidationError(verdict.reason());
            recorder.updateFailed(entity);
            log.info("geometry-ai unsupported. requestId={} reason={}", entity.getRequestId(), verdict.reason());
            throw new AiFigurePreprocessStep.GeometryAiStepException(verdict.reason());
        }

        // コマンドが方針に合わない（検証で失敗。もう一度生成の対象）
        if (verdict.errorCode() != null) {
            entity.setOutcome(FigureOutcome.GENERATABLE.name());
            entity.setFailedStage("VALIDATE");
            entity.setErrorCode(verdict.errorCode());
            entity.setErrorMessage(verdict.reason());
            entity.setValidationError(verdict.reason());
            recorder.updateFailed(entity);
            log.warn("geometry-ai validation failed. requestId={} code={}",
                    entity.getRequestId(), verdict.errorCode());
            throw new AiFigurePreprocessStep.GeometryAiStepException(verdict.reason());
        }

        entity.setOutcome(FigureOutcome.GENERATABLE.name());
        entity.setResolvedOutputType(verdict.resolvedOutputType() == null
                ? null : verdict.resolvedOutputType().name());
        entity.setFigureType(verdict.figureType());
        entity.setValidationError(null);
        recorder.updateReady(entity);

        result.put("success", true);
        result.put("commandCount", verdict.commands().size());
        result.put("figureType", entity.getFigureType());
        // 「検証に通った」は**コマンドの規則**の話。数学的な条件まで確かめたとは言わない
        // （実際に図が成り立つかは作図画面のブラウザで実行して初めて分かる）
        result.put("message", "コマンドの規則チェックに通りました。（" + entity.getRequestNo() + " "
                + verdict.commands().size() + " コマンド / " + entity.getFigureType()
                + "）数学的な条件の検証は行っていません。作図画面で確認してください。");
        log.info("geometry-ai ready. requestId={} requestNo={} commands={} figureType={} resolvedType={}",
                entity.getRequestId(), entity.getRequestNo(), verdict.commands().size(),
                entity.getFigureType(), entity.getResolvedOutputType());
        return result;
    }

    /** 検証に使う条件（許可コマンドと上限）。 */
    private record Limits(String allowedCommands, int maxCommands) {
    }

    /**
     * 検証に使う条件を決める。
     *
     * <p>要求行に固定した設定（受付時のスナップショット）があればそれを、無ければいまの設定を使う。
     * 待ち行列に並んでいる間の設定変更で検証条件が変わらないようにするため。いまの設定を読むのは
     * **1 回**にまとめる（設定の読み出しはキーごとに DB を見るので、何度も呼ばない）。</p>
     */
    private Limits limitsOf(GeometryAiRequestEntity entity, FigureProcessor processor) {
        AiFigureConfig pinned = AiFigureConfig.fromSnapshotJson(entity.getSettingsSnapshotJson()).orElse(null);
        if (pinned != null && pinned.allowedCommands() != null && !pinned.allowedCommands().isBlank()
                && pinned.maxCommands() > 0) {
            return new Limits(pinned.allowedCommands(), pinned.maxCommands());
        }
        Map<String, String> values = settingsService.requireSettings(processor.taskCode(), REQUIRED_SETTINGS);
        String allowed = pinned != null && pinned.allowedCommands() != null && !pinned.allowedCommands().isBlank()
                ? pinned.allowedCommands()
                : values.get("GEOMETRY_AI_ALLOWED_COMMANDS");
        int max = pinned != null && pinned.maxCommands() > 0
                ? pinned.maxCommands()
                : intValue(values.get("GEOMETRY_AI_MAX_COMMANDS"), 80);
        return new Limits(allowed, max);
    }

    /**
     * 検証の対象か。
     *
     * <p>`GENERATED`（生成済み・検証待ち）と、**落ちたままの `VALIDATING`**（働き手が確保した直後に
     * 止まった／以前の版が残した行）、そして `FAILED(VALIDATE)`（もう一度生成で戻ってきた行）。
     * **ここで `VALIDATING` を受けないと、確保しただけで何もせず終わり、同じ行を拾い続けてしまう。**</p>
     */
    static boolean isValidateTarget(GeometryAiRequestEntity entity) {
        String status = entity.getStatusCode();
        return "GENERATED".equals(status) || "VALIDATING".equals(status)
                || ("FAILED".equals(status) && "VALIDATE".equals(entity.getFailedStage()));
    }

    /** 失敗を記録して実行を失敗させる（要求は FAILED(VALIDATE) になる）。 */
    private void fail(GeometryAiRequestEntity entity, String errorCode, String message) {
        entity.setFailedStage("VALIDATE");
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(message);
        entity.setValidationError(message);
        recorder.updateFailed(entity);
        log.warn("geometry-ai validate failed. requestId={} code={} message={}",
                entity.getRequestId(), errorCode, message);
        throw new AiFigurePreprocessStep.GeometryAiStepException(message);
    }

    private static String messageOf(Throwable cause) {
        if (cause == null) {
            return "原因が分かりません。";
        }
        String message = cause.getMessage();
        if (message != null && !message.isBlank()) {
            return message.strip();
        }
        Throwable root = cause.getCause();
        return root != null && root.getMessage() != null && !root.getMessage().isBlank()
                ? root.getMessage().strip() : cause.getClass().getSimpleName();
    }

    private static int intValue(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException cause) {
            return fallback;
        }
    }
}
