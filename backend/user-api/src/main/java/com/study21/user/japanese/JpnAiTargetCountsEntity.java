package com.study21.user.japanese;

/**
 * AI 取得の対象を選ぶための件数（1 行）。
 *
 * <p>窓が「検索条件に一致する N 語（うち取得済み M 語）」と「今回受付ける K 語」を出すのに使う。
 * 一覧の「取得状態」と同じく {@code v_jpn_word_ai_state} を見るので、**画面と数え方が一致する**。</p>
 *
 * <ul>
 *   <li>{@code total} … 検索条件に一致する語の数</li>
 *   <li>{@code acquired} … そのうち「取得済み」（{@code SUCCEEDED}）の数</li>
 *   <li>{@code candidates} … そのうち「今回取得できる」語の数（取得済みと取得中を除いた数）
 *       ＝「取得済みをスキップ」を選んだときの対象数</li>
 * </ul>
 */
public class JpnAiTargetCountsEntity {

    private long total;
    private long acquired;
    private long candidates;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getAcquired() {
        return acquired;
    }

    public void setAcquired(long acquired) {
        this.acquired = acquired;
    }

    public long getCandidates() {
        return candidates;
    }

    public void setCandidates(long candidates) {
        this.candidates = candidates;
    }
}
