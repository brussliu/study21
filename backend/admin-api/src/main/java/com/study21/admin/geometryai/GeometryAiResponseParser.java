package com.study21.admin.geometryai;

import com.study21.admin.geometryai.dto.BatC51ResultDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI の応答から **DTO（{@link BatC51ResultDto}）** を取り出す（batC51 = AI 生図の AI 生成）。
 *
 * <p>出力データ構造の定義は DTO が唯一（{@code @Schema} と {@code @JsonProperty}）。
 * ここは「本文から DTO へ読む」だけで、キー名や型の一覧は持たない。
 * プロンプトへ渡す出力形式も DTO から生成する（{@link com.study21.admin.geometryai.dto.AiResponseSchemaService}）。</p>
 *
 * <p>モデルが JSON を崩したときのために、**コマンド行だけ**の応答も受け付ける（設計 §6.3 のフォールバック）。
 * その場合も戻り値は同じ DTO（{@code コマンド} だけ入った状態）にする。</p>
 */
public final class GeometryAiResponseParser {

    private GeometryAiResponseParser() {
    }

    /** 解析結果。`errorCode` が null なら成功（`output` は DTO）。 */
    public record ParseResult(BatC51ResultDto output, String errorCode, String errorMessage) {

        public static ParseResult ok(BatC51ResultDto output) {
            return new ParseResult(output, null, null);
        }

        public static ParseResult failure(String code, String message) {
            return new ParseResult(null, code, message);
        }

        public boolean isSuccess() {
            return errorCode == null;
        }
    }

    /**
     * 応答全体（HTTP 本文）を解析する。
     *
     * @param outputFormat 設定 `GEOMETRY_AI_OUTPUT_FORMAT`（JSON / COMMAND）
     */
    public static ParseResult parse(String body, String outputFormat) {
        String content = AiResponseDtoParser.assistantContent(body);
        if (content == null || content.isBlank()) {
            return ParseResult.failure("EMPTY_RESPONSE", "AI から内容が返りませんでした。");
        }

        // 1) DTO として読む（出力形式の定義は DTO が唯一）
        Optional<BatC51ResultDto> parsed = AiResponseDtoParser.parseContent(content, BatC51ResultDto.class);
        if (parsed.isPresent() && !commandsOf(parsed.get()).isEmpty()) {
            return ParseResult.ok(parsed.get());
        }

        // 2) JSON が壊れていてもコマンド行として読めれば進む
        List<String> lines = parseLines(AiResponseDtoParser.stripFence(content).trim());
        if (!lines.isEmpty()) {
            BatC51ResultDto fallback = new BatC51ResultDto();
            fallback.setCommands(lines);
            return ParseResult.ok(fallback);
        }

        if ("JSON".equalsIgnoreCase(outputFormat)) {
            return ParseResult.failure("INVALID_JSON", "AI の応答を JSON として読めませんでした。");
        }
        return ParseResult.failure("EMPTY_RESPONSE", "AI からコマンドが返りませんでした。");
    }

    /** DTO のコマンド（null を空リストとして扱う）。 */
    public static List<String> commandsOf(BatC51ResultDto dto) {
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
