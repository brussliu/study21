package com.study21.admin.japanesewordai;

import com.study21.common.core.japanese.JpnWordDetailEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * batC41 の詳細を<b>新しい版として</b>書く。
 *
 * <p>「版」の考え方（設計: {@code 日本語勉強_再設計案_中文.md} §1.4）:
 * <b>すべての取得が 1 版を作る</b>。新しい版は自動で {@code ACTIVE} になり、
 * 前の版は {@code ARCHIVED} になる（1 語に 1 版だけが有効。部分 UNIQUE 索引
 * {@code uq_jpn_detail_active} が守る）。</p>
 *
 * <p><strong>1 回の書き込みの順番</strong>（この順番が意味を持つ）:</p>
 * <ol>
 *   <li>今の有効版を読む（基にする版＝{@code 元詳細ID}。無ければ最初の版）</li>
 *   <li>AI の行の {@code 表示順} の開始番号を決める＝<b>人が入れた行の最大 + 1</b>
 *       （{@code maxKeptDetailOrderNo}。人が入れた行は {@code 登録元コード='APP'} または
 *       {@code 手修正フラグ=true}）</li>
 *   <li>今の有効版を {@code ARCHIVED} にする（1 語 1 版の ACTIVE を守る。<b>消さない</b>＝履歴）</li>
 *   <li>新しい版のヘッダを入れる（{@code ACTIVE}・{@code 生成ID}・
 *       {@code AIプロバイダ}／{@code AIモデル}／{@code 取得日時}・{@code 登録元コード='BATCH'}。
 *       採番された {@code 詳細ID} が戻る）</li>
 *   <li><b>人が入れた行を新しい版へ複製する</b>（内容・出所・{@code 表示順} はそのまま。
 *       会話 → 会話行の順で、会話行は新しい {@code 会話ID} に付け替える）</li>
 *   <li>11 の子テーブルへ AI の新しい行を入れる（{@code 表示順} は
 *       {@code #{startOrderNo} + 段落内の位置}。2 で数えた値から）</li>
 * </ol>
 *
 * <p><strong>人工行不许被 AI 覆盖</strong>: 5 で人の行を新しい版へ写すので、AI の取り直しで
 * 消えない。人が書いた内容に戻したいときは、AI の版ではなく人の版へ有効版を切り替える
 * （切片4の「有効版の切り替え」）。</p>
 *
 * <p>全体が <b>1 つのトランザクション</b>（{@code @Transactional}）。途中で失敗したら何も残らない
 * （中途半端な版を作らない）。</p>
 */
@Component
public class JapaneseWordAiDetailWriter {

    private static final Logger log = LoggerFactory.getLogger(JapaneseWordAiDetailWriter.class);

    private final JapaneseWordAiMapper mapper;

    public JapaneseWordAiDetailWriter(JapaneseWordAiMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 作った版。
     *
     * @param detailId       新しい版の {@code 詳細ID}
     * @param originDetailId 基にした版（無ければ null）
     * @param generationId   この版を作った AI 生成
     * @param maxKeptOrderNo 複製した人の行の {@code 表示順} の最大（AI の行はこの次から）
     */
    public record CreatedVersion(Long detailId, Long originDetailId, Long generationId, int maxKeptOrderNo) {
    }

    /**
     * 詳細の JSON（AI の応答から正規化したもの）で<b>新しい版</b>を作る。
     *
     * @param wordId       語の ID
     * @param detailJson   詳細（旧 {@code 詳細JSON} と同じ形の JSON 文字列）
     * @param generationId 今回の AI 生成（{@code JPN_AI生成履歴情報.生成ID}）
     * @param aiProvider   この版を作った AI
     * @param aiModel      この版を作ったモデル
     */
    @Transactional
    public CreatedVersion createVersion(long wordId, String detailJson, Long generationId,
                                        String aiProvider, String aiModel) {
        JpnWordDetailEntity originVersion = mapper.findActiveDetail(wordId);
        Long originDetailId = originVersion == null ? null : originVersion.getDetailId();

        // 人が入れた行の 表示順 の最大。AI の行はその次から振る（人が入れた行を上書きしない）
        int maxKeptOrderNo = originDetailId == null ? 0 : mapper.maxKeptDetailOrderNo(originDetailId);

        // 古い有効版を ARCHIVED にしてから、新しい版を ACTIVE で入れる
        // （部分 UNIQUE 索引が「1 語 1 版の ACTIVE」を守るので順番を入れ替えられない）
        if (originDetailId != null) {
            mapper.archiveActiveDetail(wordId);
        }

        JapaneseWordAiDetailComposer.NewVersion version = JapaneseWordAiDetailComposer.compose(
                detailJson, originVersion, generationId, aiProvider, aiModel, maxKeptOrderNo);
        version.header().setWordId(wordId);
        mapper.insertDetailVersion(version.header());
        Long detailId = version.header().getDetailId();
        if (detailId == null) {
            throw new IllegalStateException("新しい版の 詳細ID を採番できませんでした。wordId=" + wordId);
        }

        // 人が入れた行を新しい版へ複製する（新しい版の 詳細ID にぶら下げる）
        if (originDetailId != null) {
            mapper.copyDetailSenses(detailId, originDetailId);
            mapper.copyDetailExamples(detailId, originDetailId);
            mapper.copyDetailPatterns(detailId, originDetailId);
            mapper.copyDetailDialogs(detailId, originDetailId);
            // 会話行は新しい 会話ID が決まってから（会話の複製のあと）
            mapper.copyDetailDialogLines(detailId, originDetailId);
            mapper.copyDetailSynonyms(detailId, originDetailId);
            mapper.copyDetailCautions(detailId, originDetailId);
            mapper.copyDetailCollocations(detailId, originDetailId);
            mapper.copyDetailRelatedWords(detailId, originDetailId);
            mapper.copyDetailUsageNotes(detailId, originDetailId);
            mapper.copyDetailPractices(detailId, originDetailId);
        }

        // AI の新しい行（表示順は複製した行の後ろ）
        insertNewRows(detailId, version);

        log.debug("japanese word ai detail version created. wordId={} detailId={} originDetailId={} ai={}/{}",
                wordId, detailId, originDetailId, aiProvider, aiModel);
        return new CreatedVersion(detailId, originDetailId, generationId, maxKeptOrderNo);
    }

    /** 11 の子テーブルへ AI の新しい行を入れる（空の段落は呼ばない）。 */
    private void insertNewRows(Long detailId, JapaneseWordAiDetailComposer.NewVersion version) {
        version.children().senses().forEach(row -> mapper.insertDetailSense(row, detailId));
        version.children().examples().forEach(row -> mapper.insertDetailExample(row, detailId));
        version.children().patterns().forEach(row -> mapper.insertDetailPattern(row, detailId));
        version.children().dialogs().forEach(dialog -> {
            mapper.insertDetailDialog(dialog, detailId);
            // 会話の INSERT が採番した 会話ID が row.dialogId に入る
            for (com.study21.common.core.japanese.JpnWordDetailChildren.DialogLine line : dialog.getLines()) {
                line.setDialogId(dialog.getDialogId());
                mapper.insertDetailDialogLine(line, detailId);
            }
        });
        version.children().synonyms().forEach(row -> mapper.insertDetailSynonym(row, detailId));
        version.children().cautions().forEach(row -> mapper.insertDetailCaution(row, detailId));
        version.children().collocations().forEach(row -> mapper.insertDetailCollocation(row, detailId));
        version.children().relatedWords().forEach(row -> mapper.insertDetailRelatedWord(row, detailId));
        version.children().usageNotes().forEach(row -> mapper.insertDetailUsageNote(row, detailId));
        version.children().practices().forEach(row -> mapper.insertDetailPractice(row, detailId));
    }
}
