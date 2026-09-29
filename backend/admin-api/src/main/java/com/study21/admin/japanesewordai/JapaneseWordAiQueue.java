package com.study21.admin.japanesewordai;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日本語単語の AI 取得の**待ち行列**（受付と取り出し）。
 *
 * <p>利用者の指示（2026-09-27「改成异步处理」）で、画面からの AI 取得は
 * <b>受付（{@code QUEUED} を積むだけ）</b>と<b>働き手（{@code JapaneseWordAiWorker}）</b>に分かれた。
 * 待ち行列は新しい表ではなく <b>{@code JPN_AI生成履歴情報} の行そのもの</b>を使う
 * （1 語 × 1 内容種別 = 1 行。状態は {@code QUEUED} → {@code RUNNING} → {@code SUCCEEDED} / {@code FAILED}。
 * 部分索引 {@code idx_jpn_ai_gen_active} が「実行中」の取り出しに効く）。</p>
 *
 * <p><b>二重に走らせない</b>ための規則は 2 つ:</p>
 * <ol>
 *   <li>受付: 同じ語 × 内容種別に実行中の行（{@code QUEUED} / {@code RUNNING}）があれば**積まない**
 *       （{@code findActiveGeneration}）。</li>
 *   <li>取り出し: {@code FOR UPDATE SKIP LOCKED} で 1 件選び、**状態を条件にした更新**で確定する。
 *       他の働き手が先に取っていれば 0 行になり、その件は実行しない。</li>
 * </ol>
 *
 * <p><b>成功済みを除かない</b>: 受付は「画面が選んだ語」をそのまま積む（{@code findTargets} は使わない）。
 * 除くのは実行中の行だけなので、「すべて再取得」は**新しい版で取り直す**という意味になる
 * （以前は SQL が成功済みを除いていたため、画面の「すべて再取得」が実際には取り直していなかった）。</p>
 */
@Component
public class JapaneseWordAiQueue {

    /** 働き手が拾う状態（待機中と、落ちたままの実行中）。{@code FAILED} は拾わない。 */
    static final List<String> CLAIMABLE_FROM = List.of("QUEUED", "RUNNING");

    private final JapaneseWordAiMapper mapper;

    public JapaneseWordAiQueue(JapaneseWordAiMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 受付: 選ばれた語 × 内容種別を {@code QUEUED} で積む（AI は呼ばない）。
     *
     * @param kind    取得区分（{@code DETAIL} / {@code C} / {@code D} / {@code E}）
     * @param wordIds 画面が選んだ語（空なら何も積まない。対象を選ぶのは画面の役目）
     * @return {@code {"accepted": 積んだ件数, "reused": 実行中で積まなかった件数,
     *         "generationIds": 受付けた行の生成 ID（実行中のものを含む）}}
     */
    @Transactional
    public Map<String, Object> accept(String kind, List<Long> wordIds) {
        List<String> contentTypes = JapaneseWordAiSettings.contentTypesOf(kind);
        int accepted = 0;
        int reused = 0;
        List<Long> generationIds = new ArrayList<>();
        for (Long wordId : wordIds) {
            if (wordId == null) {
                continue;
            }
            for (String contentType : contentTypes) {
                Long active = mapper.findActiveGeneration(wordId, contentType);
                if (active != null) {
                    // 実行中のものがある（働き手が処理中、または前の受付がまだ残っている）
                    reused += 1;
                    generationIds.add(active);
                    continue;
                }
                JapaneseWordAiMapper.GenerationRow row = new JapaneseWordAiMapper.GenerationRow();
                row.setWordId(wordId);
                row.setContentType(contentType);
                // 版は受付時に決める（問題の行と同じ版を入れる規則のため）
                row.setContentVersion(mapper.nextGenerationVersion(wordId, contentType));
                mapper.insertQueuedGeneration(row);
                if (row.getGenerationId() == null) {
                    // 同時に受付けた（入れる SQL が「実行中の行が無いときだけ」なので 0 行になった）。
                    // 相手が積んだ行を自分の結果にも入れて、reused として数える
                    reused += 1;
                    Long claimedByOther = mapper.findActiveGeneration(wordId, contentType);
                    if (claimedByOther != null) {
                        generationIds.add(claimedByOther);
                    }
                    continue;
                }
                accepted += 1;
                generationIds.add(row.getGenerationId());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", accepted);
        result.put("reused", reused);
        // 画面はこの ID で進み具合を見る（一覧の「取得状態」は取り直しの判定に使えない）
        result.put("generationIds", generationIds);
        return result;
    }

    /**
     * 受付けた取得の**進み具合**（画面の様子見が使う）。
     *
     * @param generationIds 受付が返した生成 ID
     * @return {@code {"pending":N,"succeeded":N,"failed":N}}（ID が空なら全て 0）
     */
    public Map<String, Object> progress(List<Long> generationIds) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", 0);
        result.put("succeeded", 0);
        result.put("failed", 0);
        if (generationIds == null || generationIds.isEmpty()) {
            return result;
        }
        Map<String, Object> counts = mapper.countGenerationsByState(generationIds);
        if (counts == null) {
            return result;
        }
        for (String key : List.of("pending", "succeeded", "failed")) {
            Object value = counts.get(key);
            result.put(key, value instanceof Number number ? number.intValue() : 0);
        }
        return result;
    }

    /**
     * 働き手: 実行する件を最大 {@code limit} 件確保して {@code RUNNING} にする。
     *
     * <p>1 件ずつ「選ぶ → 状態を条件に確定する」を繰り返す。確保できた件だけを返すので、
     * 呼び出し側（働き手）は**返ってきた件だけ**を実行する（AI を二重に呼ばない）。</p>
     *
     * @param limit        この周期で確保する最大件数（設定の {@code THREADS} を渡す想定）
     * @param staleMinutes 実行中のまま残った行を拾い直すまでの分数
     */
    @Transactional
    public List<JapaneseWordAiMapper.GenerationRow> claim(int limit, int staleMinutes) {
        List<JapaneseWordAiMapper.GenerationRow> claimed = new ArrayList<>();
        for (int index = 0; index < Math.max(0, limit); index += 1) {
            Long generationId = mapper.findClaimableGenerationId(staleMinutes);
            if (generationId == null) {
                break;
            }
            if (mapper.markGenerationClaimed(generationId, CLAIMABLE_FROM) == 0) {
                // 他の働き手が先に取った（SKIP LOCKED をすり抜けた場合の保険）。次の件へ
                continue;
            }
            JapaneseWordAiMapper.GenerationRow row = mapper.findGeneration(generationId);
            if (row != null) {
                claimed.add(row);
            }
        }
        return claimed;
    }
}
