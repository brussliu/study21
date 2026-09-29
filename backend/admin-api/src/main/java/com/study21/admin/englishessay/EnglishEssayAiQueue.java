package com.study21.admin.englishessay;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 英作文の AI 添削の<b>待ち行列</b>（受付と取り出し）。
 *
 * <p>待ち行列は新しい表ではなく <b>{@code ENG_AI添削履歴情報} の行そのもの</b>を使う
 * （1 回の添削 = 1 行。状態は {@code QUEUED} → {@code RUNNING} → {@code SUCCEEDED} / {@code FAILED}。
 * 部分索引 {@code idx_eng_essay_grading_active} が実行中の取り出しに効く）。</p>
 *
 * <p><b>受付は AI を呼ばない</b>。そのときの写し（級・題・設問・本文・語数）を積んで即座に返すので、
 * 画面を閉じても処理は続く。実行は働き手（{@link EnglishEssayAiWorker}）が行う。</p>
 *
 * <p><b>二重に走らせない</b>規則は 2 つ:</p>
 * <ol>
 *   <li>受付: 同じ作文に実行中（{@code QUEUED} / {@code RUNNING}）があれば<b>積まない</b></li>
 *   <li>取り出し: {@code FOR UPDATE SKIP LOCKED} で 1 件選び、<b>状態を条件にした更新</b>で確定する</li>
 * </ol>
 */
@Component
public class EnglishEssayAiQueue {

    /** 働き手が拾う状態（待機中と、落ちたままの実行中）。{@code FAILED} は拾わない。 */
    static final List<String> CLAIMABLE_FROM = List.of("QUEUED", "RUNNING");

    private static final int ROUND_MAX = 1000;

    private final EnglishEssayAiMapper mapper;

    public EnglishEssayAiQueue(EnglishEssayAiMapper mapper) {
        this.mapper = mapper;
    }

    /** 受付の結果（画面へ返す）。 */
    public record Accepted(long gradingId, int round) {
    }

    /**
     * 受付: 添削を 1 件積む（AI は呼ばない）。
     *
     * @param essayId 英作文ID
     * @param round   何回目か（null なら次の回＝既存の最大 + 1）
     * @throws ValidationException 作文が無い・削除済み・本文が未確定・前の添削が終わっていないとき
     * @throws ConflictException   同じ回数が既にあるとき（{@code UNIQUE(英作文ID, 回数)}）
     */
    @Transactional
    public Accepted accept(Long essayId, Integer round) {
        if (essayId == null) {
            throw new ValidationException("英作文を指定してください。");
        }
        EnglishEssayAiMapper.EssayRow essay = mapper.findEssay(essayId);
        if (essay == null) {
            throw new ValidationException("英作文が見つかりません（削除された可能性があります）: " + essayId);
        }
        if (!"A".equals(essay.getStateCode())) {
            throw new ValidationException("この英作文は削除されています。添削を受け付けられません。");
        }
        if (isBlank(essay.getQuestionText()) || isBlank(essay.getEssayText())) {
            throw new ValidationException("設問文と作文本文を先に確定してください"
                    + "（画像の OCR の結果を確認・修正してから受付けてください）。");
        }
        Long active = mapper.findActiveGrading(essayId);
        if (active != null) {
            throw new ValidationException("前の添削が終わっていません（添削ID " + active
                    + "）。終わってからもう一度受付けてください。");
        }
        int nextRound = round == null ? mapper.nextRound(essayId) : round;
        if (nextRound < 1 || nextRound > ROUND_MAX) {
            throw new ValidationException("回数は 1〜" + ROUND_MAX + " で指定してください。");
        }

        EnglishEssayAiMapper.GradingRow row = new EnglishEssayAiMapper.GradingRow();
        row.setEssayId(essayId);
        row.setRound(nextRound);
        row.setStatusCode("QUEUED");
        row.setLevel(essay.getLevel());
        row.setTitleJa(essay.getTitleJa());
        row.setTitleZh(essay.getTitleZh());
        row.setQuestionText(essay.getQuestionText());
        row.setEssayText(essay.getEssayText());
        row.setWordCount(essay.getWordCount());
        row.setUserAccountId(essay.getUserAccountId());
        try {
            mapper.insertGrading(row);
        } catch (DuplicateKeyException cause) {
            // UNIQUE(英作文ID, 回数)。同時に受付けたときはこちらへ来る
            throw new ConflictException("第 " + nextRound + " 回の添削は既にあります。"
                    + "回数を指定せずに受付けると、次の回になります。");
        }
        if (row.getGradingId() == null) {
            throw new ValidationException("添削の受付に失敗しました。もう一度お試しください。");
        }
        return new Accepted(row.getGradingId(), nextRound);
    }

    /**
     * 画面のポーリング用に 1 件の状態を返す。
     *
     * @throws NotFoundException 添削が見つからないとき
     */
    public Map<String, Object> status(long gradingId) {
        EnglishEssayAiMapper.GradingStatusRow row = mapper.findGradingStatus(gradingId);
        if (row == null) {
            throw new NotFoundException("添削が見つかりません: " + gradingId);
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("gradingId", row.getGradingId());
        status.put("round", row.getRound());
        status.put("statusCode", row.getStatusCode());
        status.put("score", row.getScore());
        status.put("maxScore", row.getMaxScore());
        status.put("failureReason", row.getFailureReason());
        return status;
    }

    /**
     * 働き手: 実行する件を最大 {@code limit} 件確保して {@code RUNNING} にする。
     *
     * <p>1 件ずつ「選ぶ → 状態を条件に確定する」を繰り返す。確保できた件だけを返すので、
     * 呼び出し側は<b>返ってきた件だけ</b>を実行する（AI を二重に呼ばない）。</p>
     *
     * @param limit        この周期で確保する最大件数
     * @param staleMinutes 実行中のまま残った行を拾い直すまでの分数
     */
    @Transactional
    public List<EnglishEssayAiMapper.GradingRow> claim(int limit, int staleMinutes) {
        List<EnglishEssayAiMapper.GradingRow> claimed = new ArrayList<>();
        for (int index = 0; index < Math.max(0, limit); index += 1) {
            Long gradingId = mapper.findClaimableGradingId(staleMinutes);
            if (gradingId == null) {
                break;
            }
            if (mapper.markGradingClaimed(gradingId, CLAIMABLE_FROM) == 0) {
                // 他の働き手が先に取った。次の件へ
                continue;
            }
            EnglishEssayAiMapper.GradingRow row = mapper.findGrading(gradingId);
            if (row != null) {
                claimed.add(row);
            }
        }
        return claimed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
