package com.study21.admin.geometryai;

import com.study21.admin.geometryai.dto.BatC52ResultDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI の応答から **DTO（{@link BatC52ResultDto}）** を取り出す（batC52 = AI 画図助手）。
 *
 * <p>出力データ構造の定義は DTO が唯一（{@code @Schema} と {@code @JsonProperty}）。
 * ここは「本文から DTO へ読む」だけで、キー名や型の一覧は持たない。
 * プロンプトへ渡す出力形式も DTO から生成する（{@link com.study21.admin.geometryai.dto.AiResponseSchemaService}）。</p>
 *
 * <p>コマンド行だけの応答（{@code 説明:} 行つき）も受け付ける（設計 §6.3 のフォールバック）。
 * その場合も戻り値は同じ DTO にする。</p>
 */
public final class GeometryAiAssistResponseParser {

    private GeometryAiAssistResponseParser() {
    }

    /** 解析結果。`errorCode` が null なら成功（`result` は DTO）。 */
    public record Parsed(BatC52ResultDto result, String errorCode, String errorMessage) {

        public static Parsed ok(BatC52ResultDto result) {
            return new Parsed(result, null, null);
        }

        public static Parsed failure(String code, String message) {
            return new Parsed(null, code, message);
        }

        public boolean isSuccess() {
            return errorCode == null;
        }
    }

    /** 応答全体（HTTP 本文）を受け取って DTO へ読み込む。 */
    public static Parsed parse(String body) {
        String content = AiResponseDtoParser.assistantContent(body);
        if (content == null || content.isBlank()) {
            return Parsed.failure("EMPTY_RESPONSE", "AI から内容が返りませんでした。");
        }

        // 1) DTO として読む（出力形式の定義は DTO が唯一）
        Optional<BatC52ResultDto> parsed = AiResponseDtoParser.parseContent(content, BatC52ResultDto.class);
        if (parsed.isPresent() && !commandsOf(parsed.get()).isEmpty()) {
            return Parsed.ok(parsed.get());
        }

        // 2) コマンド行だけの応答（`説明:` の行は説明として拾う）
        return parseLines(AiResponseDtoParser.stripFence(content).trim());
    }

    /** DTO のコマンド（null を空リストとして扱う）。 */
    public static List<String> commandsOf(BatC52ResultDto dto) {
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

    /** 行ごとの解釈。`説明:` / `説明：` で始まる行は説明、それ以外はコマンド。 */
    private static Parsed parseLines(String text) {
        List<String> commands = new ArrayList<>();
        String description = null;
        for (String rawLine : text.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("説明:") || line.startsWith("説明：")) {
                description = line.substring(line.indexOf(':') + 1).trim();
                if (description.isEmpty()) {
                    description = line.substring(line.indexOf('：') + 1).trim();
                }
                continue;
            }
            if (line.startsWith("{") || line.startsWith("}") || line.startsWith("[")) {
                continue;
            }
            commands.add(line);
        }
        if (commands.isEmpty()) {
            return Parsed.failure("EMPTY_RESPONSE", "AI からコマンドが返りませんでした。");
        }
        BatC52ResultDto result = new BatC52ResultDto();
        result.setCommands(commands);
        result.setDescription(description);
        return Parsed.ok(result);
    }
}
