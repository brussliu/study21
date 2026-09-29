package com.study21.user.japanese;

import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 単語の詳細（JPN_単語詳細情報 ＝ 版のヘッダ）と、その 11 の子テーブルの読み書き。
 *
 * <p><strong>版の考え方</strong>（設計: {@code 日本語勉強_再設計案_中文.md}）:
 * 1 行 = その語の詳細の 1 版。有効版は {@code 状態コード='ACTIVE'} の 1 行だけで、
 * 部分 UNIQUE 索引 {@code uq_jpn_detail_active} がそれを保証する。段落は子テーブルが持ち、
 * 版が変わると行は新しい版へ複製される。</p>
 *
 * <p>ここが受け持つのは<b>読むこと</b>（有効版と子テーブル）と、切片4で足した<b>書くこと</b>
 * （画面の編集で新しい版を作る・有効版を切り替える・版の一覧）。AI 取得の新しい版を作るのは
 * admin-api の {@code JapaneseWordAiMapper} で、規則（古い版を ARCHIVED にしてから ACTIVE を
 * 入れる・人が作った行は複製する）はそちらと揃えてある。</p>
 *
 * <p>SQL は {@code resources/mapper/JpnWordDetailMapper.xml}。MyBatis が
 * {@code SqlLoggingInterceptor} で自動的に SQL ログへ記録するので、ここでログを書かない
 * （{@code docs/LOGGING.md}）。</p>
 */
@Mapper
public interface JpnWordDetailMapper {

    /**
     * その語の有効版（{@code 状態コード='ACTIVE'}）。まだ詳細が無ければ null。
     *
     * <p>部分 UNIQUE 索引で 1 語に 1 行しか無いので {@code LIMIT 1} で足りる。</p>
     */
    JpnWordDetailEntity findActiveDetail(@Param("wordId") long wordId);

    /** 語義（senses）。 */
    List<JpnWordDetailChildren.Sense> listSenses(@Param("detailId") long detailId);

    /** 例文（examples）。 */
    List<JpnWordDetailChildren.Example> listExamples(@Param("detailId") long detailId);

    /** 文型（patterns）。 */
    List<JpnWordDetailChildren.Pattern> listPatterns(@Param("detailId") long detailId);

    /** 会話の枠（dialogs）。発言は {@link #listDialogLines(long)}。 */
    List<JpnWordDetailChildren.Dialog> listDialogs(@Param("detailId") long detailId);

    /** 会話の発言（dialogs[].lines）。親は {@code 会話ID}。 */
    List<JpnWordDetailChildren.DialogLine> listDialogLines(@Param("detailId") long detailId);

    /** 類義語（synonyms）。 */
    List<JpnWordDetailChildren.Synonym> listSynonyms(@Param("detailId") long detailId);

    /** 間違えやすいポイント（cautions）。 */
    List<JpnWordDetailChildren.Caution> listCautions(@Param("detailId") long detailId);

    /** コロケーション（collocations）。 */
    List<JpnWordDetailChildren.Collocation> listCollocations(@Param("detailId") long detailId);

    /** 関連語（relatedWords）。 */
    List<JpnWordDetailChildren.RelatedWord> listRelatedWords(@Param("detailId") long detailId);

    /** 使用場面（usageNotes）。 */
    List<JpnWordDetailChildren.UsageNote> listUsageNotes(@Param("detailId") long detailId);

    /** ミニ練習（practices）。 */
    List<JpnWordDetailChildren.Practice> listPractices(@Param("detailId") long detailId);

    /**
     * 指定した語の**有効版**の段落の件数（一覧の「詳細情報件数」列）。
     *
     * <p>一覧の 1 ページぶんだけをまとめて数える（1 語ずつ引くと N+1 になる）。
     * 有効版が無い語は<b>行が返らない</b>（画面は「—」を出す）。</p>
     */
    List<JpnDetailCountEntity> listActiveSectionCounts(@Param("wordIds") List<Long> wordIds);

    /* ================================================= 版の書き込み（切片4） */

    /**
     * 新しい版のヘッダを入れる（{@code 状態コード='ACTIVE'}）。
     *
     * <p>内容版数は INSERT の中で {@code MAX(内容版数) + 1} を数える（同じ語を並行に触っても
     * 番号がぶつからない）。採番された {@code 詳細ID} が {@code header.detailId} に入る。
     * <b>古い有効版を ARCHIVED にしたあとに呼ぶこと</b>（部分 UNIQUE 索引
     * {@code uq_jpn_detail_active} が「1 語 1 版の ACTIVE」を守っているため）。</p>
     */
    int insertDetailVersion(JpnWordDetailEntity header);

    /** 今の有効版を {@code ARCHIVED} にする（消さない＝履歴として残す）。 */
    int archiveActiveDetail(@Param("wordId") long wordId, @Param("accountId") long accountId);

    /* ----- 段落の行（新しい版へ入れる。出所は行ごとに決まる） ----- */

    /**
     * 語義を入れる（まとめて。1 行ずつ呼ばない）。
     *
     * <p>行の出所（{@code 登録元コード}・{@code 手修正フラグ}）は<b>行が持っている値</b>をそのまま
     * 書く（{@code JpnWordDetailEditorComposer} が元の版と突き合わせて決めた値。AI の行は BATCH、
     * 人が触った行は APP）。</p>
     */
    int insertSenses(@Param("detailId") long detailId,
                     @Param("rows") List<JpnWordDetailChildren.Sense> rows);

    int insertExamples(@Param("detailId") long detailId,
                       @Param("rows") List<JpnWordDetailChildren.Example> rows);

    int insertPatterns(@Param("detailId") long detailId,
                       @Param("rows") List<JpnWordDetailChildren.Pattern> rows);

    int insertDialogs(@Param("detailId") long detailId,
                      @Param("rows") List<JpnWordDetailChildren.Dialog> rows);

    /** 会話の発言を入れる（親の {@code 会話ID} は {@code row.dialogId}）。 */
    int insertDialogLines(@Param("detailId") long detailId,
                          @Param("rows") List<JpnWordDetailChildren.DialogLine> rows);

    int insertSynonyms(@Param("detailId") long detailId,
                       @Param("rows") List<JpnWordDetailChildren.Synonym> rows);

    int insertCautions(@Param("detailId") long detailId,
                       @Param("rows") List<JpnWordDetailChildren.Caution> rows);

    int insertCollocations(@Param("detailId") long detailId,
                           @Param("rows") List<JpnWordDetailChildren.Collocation> rows);

    int insertRelatedWords(@Param("detailId") long detailId,
                           @Param("rows") List<JpnWordDetailChildren.RelatedWord> rows);

    int insertUsageNotes(@Param("detailId") long detailId,
                         @Param("rows") List<JpnWordDetailChildren.UsageNote> rows);

    int insertPractices(@Param("detailId") long detailId,
                        @Param("rows") List<JpnWordDetailChildren.Practice> rows);

    /* ============================================ 版の一覧と切り替え（切片4） */

    /**
     * その語の版の一覧（<b>新しい順</b>）。段落の行数も一緒に数える（1 回の SQL。N+1 にしない）。
     *
     * <p>{@code manualRows} は段落のいずれかに人の行があるか（版のヘッダの 手修正フラグだけでは
     * 「段落だけ直した版」が分からないため）。</p>
     */
    List<JpnWordDetailVersionEntity> listDetailVersions(@Param("wordId") long wordId);

    /** その語の版 1 つ（一覧と同じ形）。その語の版でなければ null。 */
    JpnWordDetailVersionEntity findDetailVersion(@Param("wordId") long wordId,
                                                 @Param("detailId") long detailId);

    /**
     * 指定した版を {@code ACTIVE} にする（楽観的ロック）。
     *
     * <p>{@code バージョン} が一致しなければ 0 行（ほかの操作が先に更新した）。
     * <b>元の有効版を ARCHIVED にしたあとに呼ぶこと</b>。</p>
     */
    int activateDetailVersion(@Param("wordId") long wordId, @Param("detailId") long detailId,
                              @Param("version") int version, @Param("accountId") long accountId);
}
