package com.study21.admin.geometryai;

import com.study21.common.core.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AI 画図助手（batC52）が返したコマンドの**前段フィルタ**。
 *
 * <p>user-api の同期実装（{@code GeometryAiCommandPolicy}）から移植したもの。サーバーには
 * GeoGebra が無いので**意味の検証はできない**（最終判定は作図画面の applet）。ここで弾くのは
 * 「危険・巨大・方針違反」だけ（AI 生図の検証と同じ考え方。許可リストは使わない）。</p>
 */
public final class GeometryAiAssistCommandPolicy {

    private static final int LINE_MAX = 500;
    private static final int TOTAL_MAX = 20_000;

    private static final List<String> FORBIDDEN = List.of(
            "Delete", "Execute", "SetValue", "Import", "Export", "File", "Button", "Checkbox",
            "InputBox", "SetActiveView", "RunClickScript", "StartAnimation", "PlaySound", "SetAxesVisible");

    private static final List<String> UNSAFE = List.of(
            "<", ">", "\\", "javascript:", "http://", "https://", "../", "`", "$(");

    private GeometryAiAssistCommandPolicy() {
    }

    /**
     * 許可できるコマンドだけを返す（1 つでも違反があれば例外）。
     *
     * @param commands    AI が返したコマンド
     * @param maxCommands 設定 `GEOMETRY_AI_ASSIST_MAX_COMMANDS`
     */
    public static List<String> requireAllowed(List<String> commands, int maxCommands) {
        if (commands == null || commands.isEmpty()) {
            throw new ValidationException("AI が作図の変更案を返しませんでした。指示を変えてもう一度お試しください。");
        }
        if (commands.size() > maxCommands) {
            throw new ValidationException("AI の変更案が多すぎます（最大 " + maxCommands + " 件）。"
                    + "指示を分けてもう一度お試しください。");
        }
        List<String> accepted = new ArrayList<>();
        int total = 0;
        int lineNo = 0;
        for (String command : commands) {
            lineNo += 1;
            String line = command == null ? "" : command.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.length() > LINE_MAX) {
                throw new ValidationException(reason(lineNo, "1 行が長すぎます"));
            }
            total += line.length();
            if (total > TOTAL_MAX) {
                throw new ValidationException(reason(lineNo, "コマンド全体が長すぎます"));
            }
            for (String unsafe : UNSAFE) {
                if (line.contains(unsafe)) {
                    throw new ValidationException(reason(lineNo, "使えない文字が含まれています（" + unsafe + "）"));
                }
            }
            for (String forbidden : FORBIDDEN) {
                if (isCommandToken(line, forbidden)) {
                    throw new ValidationException(reason(lineNo, "許可されていないコマンドです（" + forbidden + "）"));
                }
            }
            if (!balanced(line)) {
                throw new ValidationException(reason(lineNo, "括弧が閉じていません"));
            }
            accepted.add(line);
        }
        if (accepted.isEmpty()) {
            throw new ValidationException("AI が作図の変更案を返しませんでした。指示を変えてもう一度お試しください。");
        }
        return accepted;
    }

    private static boolean isCommandToken(String line, String forbidden) {
        String lower = line.toLowerCase(Locale.ROOT);
        String target = forbidden.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int index = lower.indexOf(target, from);
            if (index < 0) {
                return false;
            }
            int after = index + target.length();
            boolean boundaryBefore = index == 0 || !Character.isLetterOrDigit(lower.charAt(index - 1));
            boolean commandAfter = after < lower.length()
                    && (lower.charAt(after) == '(' || lower.charAt(after) == '[');
            if (boundaryBefore && commandAfter) {
                return true;
            }
            from = after;
        }
    }

    private static boolean balanced(String line) {
        int round = 0;
        int square = 0;
        boolean inString = false;
        for (int index = 0; index < line.length(); index += 1) {
            char c = line.charAt(index);
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            switch (c) {
                case '(' -> round += 1;
                case ')' -> round -= 1;
                case '[' -> square += 1;
                case ']' -> square -= 1;
                default -> {
                }
            }
            if (round < 0 || square < 0) {
                return false;
            }
        }
        return round == 0 && square == 0 && !inString;
    }

    private static String reason(int lineNo, String detail) {
        return "AI が返した変更案をそのまま適用できません（" + lineNo + " 行目: " + detail + "）。"
                + "指示を変えてもう一度お試しください。";
    }
}
