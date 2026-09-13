package com.study21.admin.batch;

/**
 * バッチタスク種別。
 *
 * <p>2.0 は R（定時）／ L（循環）／ C（呼出）の 3 種別だった。2.1 は
 * 「admin-api の起動時に 1 回だけ実行し、あとは画面から再実行する」バッチ
 * （プロキシサービス = batS01）を表す S を追加した。2.0 の batL01 がこれに当たる
 * （2.0 は 6 時間ごとのスケジュールで動かしていたが、2.1 では起動時と再実行のみ）。</p>
 */
public enum BatchTaskType {
    /** 定時実行 */
    R,
    /** 循環実行 */
    L,
    /** 呼出（コマンド）実行 */
    C,
    /** システム起動時（admin-api の起動時に 1 回だけ実行し、あとは画面から再実行） */
    S;

    public static BatchTaskType from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return BatchTaskType.valueOf(value.trim().toUpperCase());
    }
}
