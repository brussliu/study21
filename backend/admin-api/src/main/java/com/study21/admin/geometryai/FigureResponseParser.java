package com.study21.admin.geometryai;

import com.study21.admin.geometryai.dto.FigureOutcome;
import com.study21.admin.geometryai.dto.FigureOutputDto;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI の応答から**モード別の出力 DTO**を取り出す（AI 生図の全モード共通）。
 *
 * <p>出力データ構造の定義は DTO（{@code @Schema} と {@code @JsonProperty}）が唯一で、
 * ここは「本文から DTO へ読む」だけ。キー名や型の一覧は持たない。</p>
 *
 * <p><strong>NEEDS_INPUT / UNSUPPORTED は「コマンドが空」で正常</strong>なので、空を理由に
 * 再質問（＝無駄な課金）をしない。再質問するのは「JSON として読めない」「判定が無いのに
 * コマンドも無い」という**契約違反**のときだけにする。読めないときは従来どおり
 * コマンド行だけの応答も受け付ける（フォールバック）。</p>
 */
public final class FigureResponseParser {

    /** 解析の結果。`errorCode` が null なら成功（`output` は DTO）。 */
    public record ParseResult(FigureOutputDto output, String errorCode, String errorMessage) {

        public static ParseResult ok(FigureOutputDto output) {
            return new ParseResult(output, null, null);
        }

        public static ParseResult failure(String code, String message) {
            return new ParseResult(null, code, message);
        }

        public boolean isSuccess() {
            return errorCode == null;
        }
    }

    private FigureResponseParser() {
    }

    /**
     * 応答全体（HTTP 本文）を解析する。
     *
     * @param dtoClass     モードの出力 DTO（プロセッサが持つ）
     * @param outputFormat 設定 `GEOMETRY_AI_OUTPUT_FORMAT`（JSON / COMMAND）
     */
    public static ParseResult parse(String body, Class<? extends FigureOutputDto> dtoClass, String outputFormat) {
        String content = AiResponseDtoParser.assistantContent(body);
        if (content == null || content.isBlank()) {
            return ParseResult.failure("EMPTY_RESPONSE", "AI から内容が返りませんでした。");
        }

        // 1) DTO として読む（出力形式の定義は DTO が唯一）
        Optional<? extends FigureOutputDto> parsed = AiResponseDtoParser.parseContent(content, dtoClass);
        if (parsed.isPresent() && isComplete(parsed.get())) {
            return ParseResult.ok(parsed.get());
        }

        // 2) JSON が壊れていてもコマンド行として読めれば進む（歴史的な COMMAND 形式も同じ道）
        List<String> lines = parseLines(AiResponseDtoParser.stripFence(content).trim());
        if (!lines.isEmpty()) {
            FigureOutputDto fallback = newInstance(dtoClass);
            fallback.setCommands(lines);
            if (fallback.getOutcome() == null) {
                fallback.setOutcome(FigureOutcome.GENERATABLE);
            }
            return ParseResult.ok(fallback);
        }

        if ("JSON".equalsIgnoreCase(outputFormat)) {
            return ParseResult.failure("INVALID_JSON", "AI の応答を JSON として読めませんでした。");
        }
        return ParseResult.failure("EMPTY_RESPONSE", "AI からコマンドが返りませんでした。");
    }

    /**
     * 契約を満たしているか。
     *
     * <p>コマンドが要る判定（GENERATABLE・判定が無い）でコマンドが空なら、契約違反として
     * 再質問の対象にする。NEEDS_INPUT / UNSUPPORTED は空でよい。</p>
     */
    private static boolean isComplete(FigureOutputDto dto) {
        if (!commandsOf(dto).isEmpty()) {
            return true;
        }
        FigureOutcome outcome = dto.getOutcome();
        return outcome != null && !outcome.needsCommands();
    }

    /** DTO のコマンド（null を空リストとして扱い、空行を落とす）。 */
    public static List<String> commandsOf(FigureOutputDto dto) {
        if (dto == null || dto.getCommands() == null) {
            return List.of();
        }
        List<String> commands = new ArrayList<>();
        for (String command : dto.getCommands()) {
            if (command != null && !command.isBlank()) {
                commands.add(command.trim());
            }
        }
        return commands;
    }

    /** モードの DTO を作る（フォールバック用。DTO は既定コンストラクタを持つ）。 */
    private static FigureOutputDto newInstance(Class<? extends FigureOutputDto> dtoClass) {
        try {
            return dtoClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException
                 | InvocationTargetException cause) {
            throw new IllegalStateException("出力 DTO を作れませんでした: " + dtoClass.getName(), cause);
        }
    }

    /** 行ごとの解釈（`COMMAND` 形式）。`#` の行と JSON の断片は捨てる。 */
    private static List<String> parseLines(String text) {
        List<String> commands = new ArrayList<>();
        for (String rawLine : text.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("説明:") || line.startsWith("説明：")) {
                continue;
            }
            if (line.startsWith("{") || line.startsWith("}") || line.startsWith("[")) {
                continue;
            }
            commands.add(line);
        }
        return commands;
    }
}
