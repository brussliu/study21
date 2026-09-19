package com.study21.admin.geometryai;

/**
 * 要求に固定した設定が**使えない**ときの失敗（壊れている・足りない・モードが食い違う）。
 *
 * <p>「スナップショットが無い（歴史的な要求）」とは別物として扱う。無い場合はいまの設定から
 * 作り直して固定してよいが、**あるのに読めない**ときにいまの設定で黙って走らせると、
 * 利用者が固定した条件（プロンプト・モデル・上限）と違う条件で AI を呼ぶことになる。
 * ここでは必ず失敗させ、理由を要求行へ書く。</p>
 */
public class AiFigureConfigException extends RuntimeException {

    public AiFigureConfigException(String message) {
        super(message);
    }
}
