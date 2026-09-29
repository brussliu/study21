package com.study21.admin.ai;

/**
 * AI 呼び出しの失敗を、画面・実行履歴・AI呼出履歴へ返す文言に変える。
 *
 * <p><b>プロバイダーが返した理由を必ず後ろに付ける。</b>「API Key とモデル名を確認してください。」だけでは、
 * 残高不足（DashScope の {@code Arrearage}）・利用停止・内容フィルタのように、
 * <b>設定を直しても解決しない原因</b>が分からない。実際に日本語単語の AI 取得（batC41）が
 * 全語 400 で失敗したとき、理由はバックエンドのログ（WARN）にしか残っておらず、
 * 画面と {@code BAT_AI呼出履歴情報} には定型文しか無かったため、切り分けに時間がかかった
 * （2026-09-25。原因は DashScope のアカウント残高不足）。</p>
 *
 * <p>AI を呼ぶ実装（{@code GeometryAiLangChain4jClient} / {@code GeometryAiHttpClient} /
 * {@code ClassroomAiHttpClient}）が共有する。**どの機能からも同じ形で原因が読める**ようにするため、
 * ここ 1 か所に置く（特定の機能のパッケージへ依存させない）。</p>
 */
public final class AiErrorMessages {

    /** 添える「プロバイダーが返した理由」の上限（画面のトーストと履歴が読める長さに保つ）。 */
    public static final int PROVIDER_DETAIL_LIMIT = 500;

    private AiErrorMessages() {
    }

    /**
     * 4xx（キー・モデル不正・残高不足など）のときに返す文言。
     *
     * @param status         HTTP ステータス（400 / 401 / 403 など）
     * @param providerDetail プロバイダーが返した本文（null / 空なら定型文だけ。
     *                       改行は空白へつぶし、{@link #PROVIDER_DETAIL_LIMIT} を超える分は切る）
     */
    public static String clientError(int status, String providerDetail) {
        String message = "AI がリクエストを受け付けませんでした（HTTP " + status
                + "）。API Key とモデル名を確認してください。";
        String detail = providerDetail == null ? "" : providerDetail.replaceAll("\\s+", " ").trim();
        if (detail.isEmpty()) {
            return message;
        }
        if (detail.length() > PROVIDER_DETAIL_LIMIT) {
            detail = detail.substring(0, PROVIDER_DETAIL_LIMIT) + "…";
        }
        return message + " 詳細: " + detail;
    }
}
