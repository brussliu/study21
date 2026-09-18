package com.study21.admin.geometryai;

import com.study21.admin.geometryai.dto.FigureParts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * モードごとの出力 DTO を**共用の作図構造**（{@link FigureParts.MathObject}）へ写す。
 *
 * <p>A〜D は DTO が別々でも、作図そのものは同じ構造（名前・種類・定義・出典・精度・依存）で扱う。
 * ここがその写像の唯一の場所で、検証・保存・（第 2 段階の）実行が同じ構造を見る。</p>
 *
 * <p>AI が {@code 作図オブジェクト} を書かなかったときは、**コマンドから名前と定義だけを拾って**
 * 構造を作る（名前が分からないままでは検証も保存もできないため）。そのときは種類を断定せず
 * {@link FigureParts.ObjectKind#OTHER} にし、警告を残す。</p>
 */
public final class FigureObjectMapper {

    /** `名前 = 定義` の形（コマンドから名前と定義を拾う）。 */
    private static final Pattern DEFINITION = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*(.+)$");
    /** `f(x) = …` の形（関数の定義）。 */
    private static final Pattern FUNCTION_DEFINITION =
            Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)\\s*\\(\\s*([A-Za-z]+)\\s*\\)\\s*=\\s*(.+)$");

    private FigureObjectMapper() {
    }

    /**
     * DTO の作図オブジェクトを正規化する。
     *
     * <p>名前の前後の空白を落とし、同じ名前は 1 つだけ残す（後から出たものを無視）。
     * 名前が無い・空のものは落として**警告に残す**（黙って消さない）。</p>
     */
    public static List<FigureParts.MathObject> normalize(List<FigureParts.MathObject> objects,
                                                         List<String> warnings) {
        List<FigureParts.MathObject> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (FigureParts.MathObject object : objects == null ? List.<FigureParts.MathObject>of() : objects) {
            if (object == null) {
                continue;
            }
            String name = object.name() == null ? "" : object.name().trim();
            if (name.isEmpty()) {
                if (warnings != null) {
                    warnings.add("名前の無い作図オブジェクトがあったため落としました（"
                            + textOr(object.definition(), "定義なし") + "）。");
                }
                continue;
            }
            if (!seen.add(name)) {
                if (warnings != null) {
                    warnings.add("作図オブジェクトの名前が重複しています: " + name + "（最初の 1 つを使います）。");
                }
                continue;
            }
            result.add(new FigureParts.MathObject(name, object.kind(),
                    textOr(object.definition(), null), object.source(), object.precision(),
                    object.basis(), object.dependsOn() == null ? List.of() : object.dependsOn()));
        }
        return result;
    }

    /**
     * 作図オブジェクトが書かれていないとき、コマンドから名前と定義を拾って構造を作る。
     *
     * <p>種類は断定できないので {@link FigureParts.ObjectKind#OTHER}・出典は
     * {@link FigureParts.Source#DERIVED} にする（AI が宣言していないことを表す）。</p>
     */
    public static List<FigureParts.MathObject> fromCommands(List<String> commands) {
        Map<String, FigureParts.MathObject> byName = new LinkedHashMap<>();
        for (String command : commands == null ? List.<String>of() : commands) {
            String line = command == null ? "" : command.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher function = FUNCTION_DEFINITION.matcher(line);
            if (function.matches()) {
                String name = function.group(1);
                byName.putIfAbsent(name, new FigureParts.MathObject(name, FigureParts.ObjectKind.FUNCTION,
                        line, FigureParts.Source.DERIVED, FigureParts.Precision.EXACT, null, List.of()));
                continue;
            }
            Matcher definition = DEFINITION.matcher(line);
            if (definition.matches()) {
                String name = definition.group(1).trim();
                byName.putIfAbsent(name, new FigureParts.MathObject(name, FigureParts.ObjectKind.OTHER,
                        line, FigureParts.Source.DERIVED, FigureParts.Precision.EXACT, null, List.of()));
            }
        }
        return List.copyOf(byName.values());
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
