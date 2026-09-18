package com.study21.admin.geometryai.dto;

import java.util.Locale;
import java.util.Optional;

/**
 * 「作成する図の種類」（要求＝利用者の指定 と 確定＝AI の判定）。
 *
 * <p>モード（{@link FigureMode}）が「入力をどう読むか」を決めるのに対し、こちらは「何を作るか」を決める。
 * 利用者が具体的な種類を指定したら **AI はそれに従う**（合わない・作れないときは黙って切り替えず、
 * 説明して確認を求める）。{@link #AUTO} のときだけ AI が実際の種類を判定する。</p>
 */
public enum FigureOutputType {

    /** 自動判定（画面の既定）。AI が {@code 確定図種} を決める。 */
    AUTO("自動判定",
            "AI が内容を見て決めます。"),
    /** 幾何図形。 */
    GEOMETRY("幾何図形",
            "点・線・三角形・円などの図形を作ります。"),
    /** 関数・方程式のグラフ。 */
    GRAPH("関数・方程式のグラフ",
            "関数や方程式のグラフを作ります。座標軸・目盛・定義域も扱います。"),
    /** 図形とグラフの組み合わせ。 */
    MIXED("図形とグラフの組み合わせ",
            "図形とグラフの両方を作ります。");

    private final String label;
    private final String description;

    FigureOutputType(String label, String description) {
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

    /** 画面の既定（利用者が何も選ばなければこれ）。 */
    public static FigureOutputType defaultType() {
        return AUTO;
    }

    /** AI が「確定図種」として書ける値か（AUTO は書けない）。 */
    public boolean isResolved() {
        return this != AUTO;
    }

    /**
     * GeoGebra の作図タイプ（`GEO_図形情報.図形種別` と同じ {@code geometry} / {@code function}）。
     *
     * <p>混在（{@link #MIXED}）は関数も図形も描ける Graphing にする（Geometry では関数を描けない）。
     * {@link #AUTO} は確定してから決まるので null。</p>
     */
    public String figureType() {
        return switch (this) {
            case GEOMETRY -> "geometry";
            case GRAPH, MIXED -> "function";
            case AUTO -> null;
        };
    }

    /** 画面の値（大小文字・前後の空白を許す）。 */
    public static Optional<FigureOutputType> of(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (FigureOutputType type : values()) {
            if (type.name().equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
