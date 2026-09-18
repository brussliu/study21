package com.study21.admin.geometryai;

import com.study21.admin.geometryai.dto.FigureMode;
import com.study21.admin.geometryai.dto.FigureOutputType;
import com.study21.admin.geometryai.processor.FigureProcessor;
import com.study21.common.core.geometryai.AiFigureConfig;
import com.study21.common.core.geometryai.AiFigureSettingKeys;
import com.study21.common.core.geometryai.AiFigureSupplements;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * モード別プロセッサのプロンプトを組み立てる（AI 生図の全モード共通の部品）。
 *
 * <p><strong>System Prompt</strong> は、次の順に**連結**する（どれか 1 つを選ぶのではない）:</p>
 * <ol>
 *   <li>**共通の System Prompt**（`GEOMETRY_AI_SYSTEM_PROMPT`。必須。禁止事項・出力の約束はここ）</li>
 *   <li>**モードの System Prompt**（`GEOMETRY_AI_&lt;A〜D&gt;_SYSTEM_PROMPT`。未設定なら無し）</li>
 *   <li>**モードの役割**（A〜D の違い。コードが持つ＝設定が空でも効く）</li>
 *   <li>**結果種別の守り方**（利用者の指定に従う・黙って変えない。コードが持つ）</li>
 *   <li>**出力形式（JSON Schema）**（DTO から生成。呼び出し側が `{outputSchema}` を渡して足す）</li>
 * </ol>
 *
 * <p>モード別のプロンプトで共通を**上書きしない**。モード側が禁止事項を書き漏らしても、
 * 共通の規則は必ずモデルへ届く。</p>
 *
 * <p><strong>User Prompt</strong>（タスクテンプレート）は任意。モード別 → 共通の順に採り、
 * どちらも無ければ「テンプレート無し」で実行する（**共用の User Prompt は必須ではない**）。
 * 空の任意項目は明示的な既定値（「なし」「指定なし」）にし、未展開の {@code {...}} を
 * モデルへ送らない（{@link FigurePromptTemplate}）。</p>
 */
@Component
public class FigurePromptBuilder {

    /** user プロンプトに渡す材料。 */
    public record PromptInput(
            FigureMode mode,
            FigureOutputType requestedOutputType,
            String note,
            /** モードと結果種別ごとの補充項目（「項目名: 値」の行。空なら「指定なし」になる） */
            List<String> supplements,
            /** 元の名前・ラベルを残すか（null は既定＝残す） */
            Boolean keepLabels,
            /** 歴史的な要求の分類（画面の kind。無ければ null） */
            String legacyKind,
            String legacySubKind,
            String legacyFigureType,
            int maxCommands,
            String allowedCommands,
            String outputFormat) {
    }

    /**
     * System Prompt を組み立てる（共通 → モード別 → 役割 → 結果種別。出力形式は呼び出し側が足す）。
     *
     * @param outputSchema DTO から生成した出力形式（{@code {outputSchema}} に入る。null 可）
     */
    public String buildSystemPrompt(FigureProcessor processor, AiFigureConfig config,
                                    FigureOutputType requestedOutputType, String outputSchema) {
        FigureOutputType type = effectiveType(processor, requestedOutputType);
        Map<String, String> variables = baseVariables(processor, config, type,
                FigurePromptTemplate.DEFAULT_NONE, FigurePromptTemplate.DEFAULT_UNSPECIFIED, null);
        variables.put("outputSchema", outputSchema == null ? "" : outputSchema);

        StringBuilder builder = new StringBuilder();
        // 1. 共通の System Prompt（必須）
        builder.append(FigurePromptTemplate.render(config.systemPromptCommon(), variables,
                AiFigureSettingKeys.SYSTEM_PROMPT).strip());
        // 2. モード別の System Prompt（未設定なら何も足さない）
        if (config.systemFromMode()) {
            builder.append("\n\n").append(FigurePromptTemplate.render(config.systemPromptMode(), variables,
                    processor.systemPromptKey()).strip());
        }
        // 3. モードの役割（コードが持つ）
        builder.append("\n\n").append(processor.modeGuide().strip());
        // 4. 結果種別の守り方（コードが持つ）
        builder.append("\n\n").append(processor.resultTypeRule(type).strip());
        return builder.toString();
    }

    /** user プロンプト（タスクテンプレート）を組み立てる。テンプレートが無くても成り立つ。 */
    public String buildUserPrompt(FigureProcessor processor, AiFigureConfig config, PromptInput input) {
        FigureOutputType type = effectiveType(processor, input.requestedOutputType());
        Map<String, String> variables = baseVariables(processor, config, type, input.note(),
                supplementText(input.supplements()), input.keepLabels());
        // 補充項目は**項目ごとの変数**でも渡す（テンプレートが個別に置けるように）
        variables.putAll(supplementVariables(input.supplements()));
        variables.put("outputSchema", "");
        // 歴史的な変数（モードが無い時代のテンプレート）も展開できるようにする
        variables.put("kind", legacyKindLabel(input.legacyKind(), input.legacySubKind()));
        variables.put("subKind", input.legacySubKind() == null ? "" : input.legacySubKind());
        variables.put("figureType", legacyFigureTypeLabel(input.legacyFigureType(), type));

        String rendered = FigurePromptTemplate.renderOptional(config.taskTemplate(), variables,
                processor.taskTemplateKey());
        String prompt = rendered == null ? "" : rendered;
        // テンプレートが触れていない重要事項は、**利用者の指定が落ちないように**後ろへ足す
        // （テンプレートの文面に依存せず、モード・結果種別・補充・名前の方針が必ずモデルへ届く）
        return appendMissingBlocks(prompt, config.taskTemplate(), variables, type);
    }

    /** テンプレートに書かれていない重要事項を足す（書かれていれば二重にしない）。 */
    private static String appendMissingBlocks(String prompt, String template, Map<String, String> variables,
                                              FigureOutputType type) {
        Set<String> used = FigurePromptTemplate.placeholders(template);
        StringBuilder builder = new StringBuilder(prompt.stripTrailing());
        if (!used.contains("resultType")) {
            builder.append("\n- 作成する図の種類: ").append(type.name()).append("（").append(type.label()).append("）");
        }
        String supplements = variables.get("supplements");
        boolean individual = usesAnySupplementVariable(used);
        if (!used.contains("supplements") && !individual && supplements != null && !supplements.isBlank()) {
            builder.append("\n- 補充の指定:\n").append(indent(supplements));
        }
        if (!used.contains("note")) {
            builder.append("\n- 補足要求: ").append(variables.get("note"));
        }
        if (!used.contains("keepLabels")) {
            builder.append("\n- 名前とラベル: ").append(variables.get("keepLabels"));
        }
        return builder.toString();
    }

    /** 補充項目を個別の変数で受け取っているか（一覧を二重に足さないため）。 */
    private static boolean usesAnySupplementVariable(Set<String> used) {
        for (AiFigureSupplements.Item item : AiFigureSupplements.all()) {
            if (used.contains(item.variable())) {
                return true;
            }
        }
        return false;
    }

    /** 複数行の値をぶら下げる（読めるように 1 段下げる）。 */
    private static String indent(String text) {
        return "  " + text.replace("\n", "\n  ");
    }

    /** 補充項目の行を 1 つのテキストにする（空なら「指定なし」＝既定値が入る）。 */
    static String supplementText(List<String> supplements) {
        if (supplements == null || supplements.isEmpty()) {
            return "";
        }
        List<String> lines = supplements.stream()
                .filter(line -> line != null && !line.isBlank())
                .map(String::strip)
                .toList();
        return lines.isEmpty() ? "" : String.join("\n", lines);
    }

    /**
     * 補充項目の行（「項目名: 値」）を**項目ごとの変数**へ写す。
     *
     * <p>項目名と変数の対応は {@link AiFigureSupplements}（common-core）が唯一の定義なので、
     * 画面が保存する名前を変えてもここは直さなくてよい。「元の名前とラベル」だけは
     * {@code keepLabels}（はい／いいえの文）として別に組み立てるので、ここでは扱わない。</p>
     */
    static Map<String, String> supplementVariables(List<String> rows) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rows == null) {
            return values;
        }
        for (String row : rows) {
            if (row == null) {
                continue;
            }
            int separator = row.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String label = row.substring(0, separator).strip();
            String value = row.substring(separator + 1).strip();
            if (value.isEmpty() || AiFigureSupplements.LABEL_KEEP_LABELS.equals(label)) {
                continue;
            }
            AiFigureSupplements.variableOf(label).ifPresent(variable -> values.put(variable, value));
        }
        return values;
    }

    /** モードが結果種別を固定しているときはそちらを使う（B は GRAPH）。 */
    static FigureOutputType effectiveType(FigureProcessor processor, FigureOutputType requested) {
        return processor.mode().fixedOutputType()
                .orElse(requested == null ? FigureOutputType.defaultType() : requested);
    }

    private static Map<String, String> baseVariables(FigureProcessor processor, AiFigureConfig config,
                                                     FigureOutputType type, String note, String supplements,
                                                     Boolean keepLabels) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("mode", processor.mode().name());
        variables.put("modeLabel", processor.mode().label());
        variables.put("modeGuide", processor.modeGuide());
        variables.put("resultType", type.name());
        variables.put("resultTypeLabel", type.label());
        variables.put("resultTypeRule", processor.resultTypeRule(type));
        variables.put("taskCode", config == null || config.taskCode() == null
                ? processor.taskCode() : config.taskCode());
        variables.put("note", note);
        variables.put("supplements", supplements);
        variables.put("keepLabels", keepLabels != null && !keepLabels
                ? FigurePromptTemplate.DEFAULT_DROP_LABELS : FigurePromptTemplate.DEFAULT_KEEP_LABELS);
        variables.put("maxCommands", String.valueOf(config == null
                ? AiFigureConfig.DEFAULT_MAX_COMMANDS : config.maxCommands()));
        variables.put("allowedCommands", config == null || config.allowedCommands() == null
                ? "" : config.allowedCommands());
        variables.put("outputFormat", config == null || config.outputFormat() == null
                ? AiFigureConfig.DEFAULT_OUTPUT_FORMAT : config.outputFormat());
        return variables;
    }

    /** 歴史的な分類の日本語（画面の kind / subKind と同じ呼び方）。 */
    static String legacyKindLabel(String kind, String subKind) {
        String label = switch (kind == null ? "" : kind.toUpperCase(Locale.ROOT)) {
            case "FIGURE" -> "図形";
            case "FUNCTION" -> "関数グラフ";
            case "MIXED" -> "判別が難しい複合図形";
            default -> "図形";
        };
        String sub = switch (subKind == null ? "" : subKind.toUpperCase(Locale.ROOT)) {
            case "TRIANGLE" -> "三角形";
            case "CIRCLE" -> "円";
            case "QUAD" -> "四角形";
            case "OTHER" -> "その他・複合";
            default -> null;
        };
        return sub == null ? label : label + "（" + sub + "）";
    }

    /** 歴史的な作図タイプの日本語。 */
    static String legacyFigureTypeLabel(String figureType, FigureOutputType type) {
        if (figureType != null && !figureType.isBlank()) {
            return "function".equalsIgnoreCase(figureType) ? "関数グラフ" : "幾何図形";
        }
        String mapped = type.figureType();
        return "function".equals(mapped) ? "関数グラフ" : "幾何図形";
    }
}
