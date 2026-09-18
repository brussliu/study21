package com.study21.admin.geometryai.dto;

import java.util.Locale;
import java.util.Optional;

/**
 * AI 生図の**作図モード**（画面の A〜D。利用者が 1 つ選ぶ）。
 *
 * <p>モードは「入力をどう読むか」を決める。**何を作るか**は {@link FigureOutputType}（結果種別）が決める。
 * この 2 つを混ぜないこと（設計 §2）:</p>
 * <ul>
 *   <li>A: 画像をもとに再現 … 画像から読み取った図形・式・文章をもとに同じ作図を作り直す</li>
 *   <li>B: 数式からグラフを作成 … 式・方程式・定義域からグラフを作る（結果種別は GRAPH に固定）</li>
 *   <li>C: 文章の条件から作図 … 問題文などの文章の条件だけから作図する（図は渡さない）</li>
 *   <li>D: 文章と図を合わせて作図 … 文章の条件と参考図の両方を使う</li>
 * </ul>
 *
 * <p><strong>A・C・D は幾何図形だけに限定されない</strong>（関数・方程式のグラフ、混在も作れる）。
 * DTO も {@code 作図オブジェクト} に点・線・円・多角形・関数・曲線を共通の語彙で持つ。</p>
 *
 * <p>モードごとに**独立したバッチ**（batC51-A〜D）として登録し、プロンプトと出力 DTO も別に持つ。
 * 処理（画像・モデル呼び出し・コマンド検証・保存）は共通の部品を使う。</p>
 *
 * <p>{@code 作図モード} が NULL の時代の要求（モード欄が無い行）は **A として扱う**
 * （利用者の指示。結果種別は当時の分類から読み替える）。モードが無い時代の**裸の batC51**
 * （バッチコード）は 2026-09-19 に削除したので、{@link #of(String)} は解決しない（空を返す）。</p>
 */
public enum FigureMode {

    A("画像をもとに再現",
            "画像から読み取った図形・式・文章をもとに、同じ作図を作り直します。"),
    B("数式からグラフを作成",
            "入力した式・方程式・定義域から、関数や方程式のグラフを作ります。"),
    C("文章の条件から作図",
            "問題文などの文章にある条件だけから作図します。図は使いません。"),
    D("文章と図を合わせて作図",
            "文章の条件と参考図の両方を使って作図します。");

    /** モード別バッチのコードの接頭辞（{@code batC51-A}〜{@code batC51-D} の {@code batC51} の部分）。 */
    public static final String TASK_CODE_PREFIX = "batC51";

    private final String label;
    private final String description;

    FigureMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** 画面に出す名前。 */
    public String label() {
        return label;
    }

    /** 画面に出す説明（1 文）。 */
    public String description() {
        return description;
    }

    /** このモードのバッチコード（batC51-A〜D。**連字符つきの接尾辞**を使う）。 */
    public String taskCode() {
        return TASK_CODE_PREFIX + "-" + name();
    }

    /** このモードの出力 DTO（モードごとに独立。共通項目は {@link FigureOutputDto}）。 */
    public Class<? extends FigureOutputDto> dtoClass() {
        return switch (this) {
            case A -> BatC51AResultDto.class;
            case B -> BatC51BResultDto.class;
            case C -> BatC51CResultDto.class;
            case D -> BatC51DResultDto.class;
        };
    }

    /** 画面で「作成する図の種類」を選ばせるか（B は GRAPH に固定なので選ばせない）。 */
    public boolean asksOutputType() {
        return this != B;
    }

    /** 選ばせないモードの固定の結果種別（B だけ GRAPH。他のモードは空）。 */
    public Optional<FigureOutputType> fixedOutputType() {
        return this == B ? Optional.of(FigureOutputType.GRAPH) : Optional.empty();
    }

    /**
     * 画面の値（{@code A}〜{@code D}。小文字・前後の空白も許す）と、バッチコード（{@code batC51-A}）の
     * どちらからでも解決する。モード欄が無い時代の値（{@code null} / 空 / 未知）は空を返すので、
     * 呼ぶ側が「A として扱う」（`orElse(A)`）を決める。
     */
    public static Optional<FigureMode> of(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith(TASK_CODE_PREFIX.toUpperCase(Locale.ROOT) + "-")) {
            normalized = normalized.substring(TASK_CODE_PREFIX.length() + 1);
        }
        for (FigureMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    /** バッチコードから解決する（未登録なら空）。 */
    public static Optional<FigureMode> ofTaskCode(String taskCode) {
        return of(taskCode);
    }
}
