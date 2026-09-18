package com.study21.admin.geometryai.dto;

/**
 * 図形の種類（batC51。分類が {@link FigureKind#FIGURE} のときだけ意味を持つ）。
 * 画面の作図タイプ（{@code subKind}）と同じ語彙。
 *
 * <p>この Enum の値が JSON Schema の {@code enum} にそのまま出る（DTO が唯一の定義）。
 * 値の説明は {@link BatC51ResultDto#getSubKind()} の {@code @Schema(description)} に書く。</p>
 */
public enum FigureSubKind {

    /** 三角形。 */
    TRIANGLE,

    /** 円。 */
    CIRCLE,

    /** 四角形。 */
    QUAD,

    /** その他の図形・判別できない図形。 */
    OTHER
}
