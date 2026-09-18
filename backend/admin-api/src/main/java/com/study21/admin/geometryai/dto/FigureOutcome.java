package com.study21.admin.geometryai.dto;

import java.util.Locale;
import java.util.Optional;

/**
 * AI の出力の「判定」（{@code outcome}）。
 *
 * <p><strong>この判定は「AI が作図データを出せたか」だけを表す。</strong>実際にコマンドが実行できたか・
 * 検証に通ったか・保存できたかは**別**（サーバー側の実行・検証・保存の結果で決まる）。</p>
 */
public enum FigureOutcome {

    /** 作図データを作れた（{@code コマンド} が要る）。 */
    GENERATABLE(true),
    /** 情報が足りない。**利用者への質問**を返して確認を待つ（{@code コマンド} は空でよい）。 */
    NEEDS_INPUT(false),
    /** 対応できない（作図の対象外・入力が読めないなど）。理由を {@code 説明} に書く（{@code コマンド} は空でよい）。 */
    UNSUPPORTED(false);

    private final boolean needsCommands;

    FigureOutcome(boolean needsCommands) {
        this.needsCommands = needsCommands;
    }

    /** コマンドが要る判定か（NEEDS_INPUT / UNSUPPORTED では空でよい）。 */
    public boolean needsCommands() {
        return needsCommands;
    }

    /** AI の出力（大小文字・前後の空白を許す）。 */
    public static Optional<FigureOutcome> of(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (FigureOutcome outcome : values()) {
            if (outcome.name().equals(normalized)) {
                return Optional.of(outcome);
            }
        }
        return Optional.empty();
    }
}
