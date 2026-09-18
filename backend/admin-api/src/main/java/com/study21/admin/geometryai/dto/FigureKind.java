package com.study21.admin.geometryai.dto;

/**
 * AI が判定した作図の分類（batC51）。画面の作図種別（{@code kind}）と同じ語彙にして、
 * 対応表を作らせない（設計 §6.2）。
 *
 * <p>この Enum の値が JSON Schema の {@code enum} にそのまま出る（DTO が唯一の定義。
 * 値の説明は {@link BatC51ResultDto#getKind()} の {@code @Schema(description)} に書く）。</p>
 */
public enum FigureKind {

    /** 図形（三角形・円・四角形などの幾何図形）。 */
    FIGURE,

    /** 関数グラフ。 */
    FUNCTION,

    /** 図形と関数が混ざった複合図形。 */
    MIXED,

    /** 判別できない。 */
    UNKNOWN
}
