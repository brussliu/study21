package com.study21.admin.japanesewordai;

import com.study21.common.core.japanese.JpnWordDetailAssembler;
import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 語の<b>有効版</b>の詳細を、旧 {@code 詳細JSON} と同じ形のオブジェクトに組み立てる。
 *
 * <p>何のためか: AI に渡す入力（プロンプトの {@code word_json}）に「今の詳細」を載せる。
 * 画面が読む形（学習画面の detail）と同じ形にしておくと、AI のプロンプトと画面の
 * 見え方が食い違わない。</p>
 *
 * <p><strong>組み立ては common-core の {@link JpnWordDetailAssembler} と同じものを使う</strong>
 * （純関数。Spring にも MyBatis にも依存しない）。ここが受け持つのは:</p>
 * <ol>
 *   <li>11 の子テーブルを {@code 詳細ID} で読む（SQL は {@code JapaneseWordAiMapper.xml}。
 *       user-api の {@code JpnWordDetailMapper.xml} と同じ列・同じ並び）</li>
 *   <li>会話（2 層）を {@code 会話ID} で組み直す</li>
 *   <li>{@code Map} にする（Jackson でプロンプトへ埋め込む）</li>
 * </ol>
 *
 * <p>user-api 側の対応: {@code JapaneseServiceImpl.childrenOf} / {@code dialogsOf} が
 * 同じことをしている（あちらは画面へ返すため、こちらは AI の入力のため）。</p>
 */
@Component
public class JapaneseWordAiDetailReader {

    private static final Logger log = LoggerFactory.getLogger(JapaneseWordAiDetailReader.class);

    private final JapaneseWordAiMapper mapper;

    public JapaneseWordAiDetailReader(JapaneseWordAiMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 有効版の詳細（組み立て済み）。まだ詳細が無ければ null。
     *
     * @param wordId 語の ID
     */
    public Map<String, Object> readAssembledDetail(long wordId) {
        JpnWordDetailEntity header = mapper.findActiveDetail(wordId);
        if (header == null) {
            return null;
        }
        long detailId = header.getDetailId() == null ? 0L : header.getDetailId();
        try {
            return JpnWordDetailAssembler.assemble(header, childrenOf(detailId));
        } catch (Exception cause) {
            // 詳細の形が壊れていても AI の取得は続ける（入力が無いのと同じ扱い）
            log.warn("日本語単語の詳細を組み立てられませんでした。wordId={} detailId={}", wordId, detailId, cause);
            return null;
        }
    }

    /** 版に属する 11 の子テーブルの行（並びは SQL が決める）。 */
    private JpnWordDetailChildren.Rows childrenOf(long detailId) {
        return new JpnWordDetailChildren.Rows(
                mapper.listDetailSenses(detailId),
                mapper.listDetailExamples(detailId),
                mapper.listDetailPatterns(detailId),
                dialogsOf(detailId),
                mapper.listDetailSynonyms(detailId),
                mapper.listDetailCautions(detailId),
                mapper.listDetailCollocations(detailId),
                mapper.listDetailRelatedWords(detailId),
                mapper.listDetailUsageNotes(detailId),
                mapper.listDetailPractices(detailId));
    }

    /** 会話は 2 層（{@code dialogs[].lines[]}）。発言を {@code 会話ID} で親へぶら下げ直す。 */
    private List<JpnWordDetailChildren.Dialog> dialogsOf(long detailId) {
        List<JpnWordDetailChildren.Dialog> dialogs = mapper.listDetailDialogs(detailId);
        if (dialogs == null || dialogs.isEmpty()) {
            return List.of();
        }
        Map<Long, List<JpnWordDetailChildren.DialogLine>> linesByDialog = new LinkedHashMap<>();
        for (JpnWordDetailChildren.DialogLine line : mapper.listDetailDialogLines(detailId)) {
            linesByDialog.computeIfAbsent(line.getDialogId(), key -> new ArrayList<>()).add(line);
        }
        for (JpnWordDetailChildren.Dialog dialog : dialogs) {
            dialog.setLines(linesByDialog.getOrDefault(dialog.getDialogId(), List.of()));
        }
        return dialogs;
    }
}
