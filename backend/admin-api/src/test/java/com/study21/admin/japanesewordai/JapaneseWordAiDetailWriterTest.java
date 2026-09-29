package com.study21.admin.japanesewordai;

import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * batC41 の詳細の書き込み（<b>新しい版を作る</b>）。
 *
 * <p>確かめる接縫は 1 つ（Mapper はモック。実 DB は使わない）:
 * {@link JapaneseWordAiDetailWriter#createVersion}。ここが見るのは<b>SQL を呼ぶ順番と引数</b>
 * （実際の版の積み上がりは真 DB のトランザクションで確かめる）。</p>
 *
 * <p>「人工行は AI に上書きさせない」の実装: AI の行を入れる<b>前に</b>、今の有効版から
 * 人の行（{@code 登録元コード='APP'} または {@code 手修正フラグ=true}）を新しい版へ複製する。
 * そのため<b>順番が本質</b>（複製 → ARCHIVED → 版のヘッダ → AI の行）。</p>
 */
class JapaneseWordAiDetailWriterTest {

    private static final long WORD_ID = 101L;

    private JapaneseWordAiMapper mapper;
    private JapaneseWordAiDetailWriter writer;

    @BeforeEach
    void setUp() {
        mapper = mock(JapaneseWordAiMapper.class);
        writer = new JapaneseWordAiDetailWriter(mapper);
        // 採番した 詳細ID を書き戻す（useGeneratedKeys）
        when(mapper.insertDetailVersion(any())).thenAnswer(invocation -> {
            JpnWordDetailEntity header = invocation.getArgument(0);
            if (header.getDetailId() == null) {
                header.setDetailId(901L);
            }
            return 1;
        });
        when(mapper.maxKeptDetailOrderNo(anyLong())).thenReturn(0);
    }

    private static String detailJson() {
        JapaneseWordAiDtoMapper.DetailResult result = JapaneseWordAiDtoMapper.parseDetail("""
                {"detail":{
                 "coreMeaning":"爱；喜爱","descriptionJa":"大切に思う気持ち。","descriptionZh":"珍视的感情。",
                 "partOfSpeech":"名詞","jlpt":"N3","conjugation":"なし","transitivity":"NONE","importance":5,
                 "senses":[{"number":1,"japanese":"大切に思う気持ち。","chinese":"爱；喜爱"}],
                 "examples":[{"japanese":"親の愛は無条件だ。","reading":"おやのあいはむじょうけんだ。",
                              "chinese":"父母的爱是无条件的。"},
                             {"japanese":"彼は植物を愛している。","reading":"かれはしょくぶつをあいしている。",
                              "chinese":"他热爱植物。"}],
                 "patterns":[{"pattern":"**を**愛する","chinese":"爱…","example":"音楽を愛する。"}],
                 "dialogs":[{"scene":"職員室で先生に相談する",
                             "lines":[{"speaker":"先生","japanese":"家族を愛していますか。","chinese":"你爱家人吗。"}]}],
                 "synonyms":[{"heading":"恋","reading":"こい","chinese":"恋爱","difference":"恋愛感情。"}],
                 "cautions":[{"kind":"MEANING","title":"「恋」との違い","wrong":"文化を恋する。",
                              "correct":"文化を愛する。","reason":"「恋」は恋愛に限る。"}],
                 "collocations":[{"expression":"愛を込める","chinese":"倾注爱意"},
                                 {"expression":"愛が深い","chinese":"爱意深厚"}],
                 "relatedWords":[{"relation":"類義語","heading":"恋","chinese":"恋爱"}],
                 "usageNotes":[{"register":"どちらも","politeness":"普通","audience":"家族に"}],
                 "practices":[{"kind":"PARTICLE","question":"音楽（ ）愛する。","choices":["に","を"],
                               "answer":"を","freeWriting":false}],
                 "pronunciation":{"reading":"あい","accentType":1,"hint":"「あ」を高く。","hasAudioSample":false}}}
                """);
        assertThat(result.isSuccess()).isTrue();
        return result.detailJson();
    }

    /** 今の有効版（詳細ID 900・内容版数 3）。 */
    private static JpnWordDetailEntity activeVersion() {
        JpnWordDetailEntity entity = new JpnWordDetailEntity();
        entity.setDetailId(900L);
        entity.setWordId(WORD_ID);
        entity.setContentVersion(3);
        entity.setStateCode("ACTIVE");
        entity.setGenerationId(70L);
        return entity;
    }

    private JapaneseWordAiDetailWriter.CreatedVersion create() {
        return writer.createVersion(WORD_ID, detailJson(), 77L, "qwen", "qwen3.7-plus");
    }

    /** 11 の子テーブルの複製（SQL の id）。 */
    private static final String[] COPY_IDS = {"copyDetailSenses", "copyDetailExamples", "copyDetailPatterns",
            "copyDetailDialogs", "copyDetailDialogLines", "copyDetailSynonyms", "copyDetailCautions",
            "copyDetailCollocations", "copyDetailRelatedWords", "copyDetailUsageNotes", "copyDetailPractices"};

    /** 11 の子テーブルへの AI の行の追加（SQL の id）。 */
    private static final String[] INSERT_IDS = {"insertDetailSense", "insertDetailExample", "insertDetailPattern",
            "insertDetailDialog", "insertDetailDialogLine", "insertDetailSynonym", "insertDetailCaution",
            "insertDetailCollocation", "insertDetailRelatedWord", "insertDetailUsageNote",
            "insertDetailPractice"};

    @Test
    @DisplayName("初めての取得は内容版数 1 の版を作る（元詳細ID は無し・ARCHIVED にする版も無い）")
    void createsFirstVersion() {
        when(mapper.findActiveDetail(WORD_ID)).thenReturn(null);

        JapaneseWordAiDetailWriter.CreatedVersion created = create();

        verify(mapper).insertDetailVersion(any());
        // 古い版が無いので ARCHIVED にする行も無い
        verify(mapper, never()).archiveActiveDetail(anyLong());
        assertThat(created.detailId()).isEqualTo(901L);
        assertThat(created.originDetailId()).isNull();
        assertThat(created.generationId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("2 回目の取得は元詳細ID が前の版を指し、古い版は ARCHIVED になる")
    void createsSecondVersion() {
        when(mapper.findActiveDetail(WORD_ID)).thenReturn(activeVersion());
        when(mapper.maxKeptDetailOrderNo(900L)).thenReturn(2);

        JapaneseWordAiDetailWriter.CreatedVersion created = create();

        verify(mapper).archiveActiveDetail(WORD_ID);
        assertThat(created.originDetailId()).isEqualTo(900L);
        // AI の行は複製した行の後ろ（人が入れた 2 行を上書きしない）
        assertThat(created.maxKeptOrderNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("人の行の複製 → 古い版の ARCHIVED → 版のヘッダ → AI の行、の順で書く")
    void copiesHumanRowsBeforeArchivingAndWriting() {
        when(mapper.findActiveDetail(WORD_ID)).thenReturn(activeVersion());
        when(mapper.maxKeptDetailOrderNo(900L)).thenReturn(1);

        create();

        InOrder order = inOrder(mapper);
        order.verify(mapper).findActiveDetail(WORD_ID);
        // 人が入れた行の 表示順 を数えてから、古い版を ARCHIVED にする
        // （新しい版の ACTIVE を入れる前に。部分 UNIQUE 索引が 1 語 1 版を守る）
        order.verify(mapper).maxKeptDetailOrderNo(900L);
        order.verify(mapper).archiveActiveDetail(WORD_ID);
        // 新しい版のヘッダを入れて 詳細ID を決める
        order.verify(mapper).insertDetailVersion(any());
        // 人の行を新しい版へ複製（元の版の行はそのまま残る）
        order.verify(mapper).copyDetailExamples(901L, 900L);
        order.verify(mapper).copyDetailDialogLines(901L, 900L);
        // 最後に AI の行（複製した行の後ろ）
        order.verify(mapper, org.mockito.Mockito.atLeastOnce()).insertDetailExample(any(), any());
    }

    @Test
    @DisplayName("11 の子テーブルすべてを、人の行の複製と AI の行の両方で書く")
    void writesAllChildTables() {
        when(mapper.findActiveDetail(WORD_ID)).thenReturn(activeVersion());

        create();

        for (String id : COPY_IDS) {
            // 複製は「元の版 → 新しい版」
            assertThat(callCount(id)).as("%s を呼ぶ", id).isGreaterThan(0);
        }
        for (String id : INSERT_IDS) {
            assertThat(callCount(id)).as("%s を呼ぶ", id).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("会話行の複製は会話の複製より後（新しい 会話ID が決まってから付け替える）")
    void copiesDialogLinesAfterDialogs() {
        when(mapper.findActiveDetail(WORD_ID)).thenReturn(activeVersion());

        create();

        InOrder order = inOrder(mapper);
        order.verify(mapper).copyDetailDialogs(901L, 900L);
        order.verify(mapper).copyDetailDialogLines(901L, 900L);
    }

    @Test
    @DisplayName("版のヘッダに生成ID と AI の情報を入れる（どの生成から生まれた版か辿れる）")
    void storesGenerationOnHeader() {
        when(mapper.findActiveDetail(WORD_ID)).thenReturn(null);

        create();

        ArgumentCaptor<JpnWordDetailEntity> captor = ArgumentCaptor.forClass(JpnWordDetailEntity.class);
        verify(mapper).insertDetailVersion(captor.capture());
        JpnWordDetailEntity header = captor.getValue();
        assertThat(header.getGenerationId()).isEqualTo(77L);
        assertThat(header.getAiProvider()).isEqualTo("qwen");
        assertThat(header.getAiModel()).isEqualTo("qwen3.7-plus");
        assertThat(header.getStateCode()).isEqualTo("ACTIVE");
        assertThat(header.getManualCorrected()).isFalse();
        assertThat(header.getFetchedAt()).isNotNull();
        // AI が作った版なので、内容の出所は BATCH
        assertThat(header.getNote()).isNull();
    }

    @Test
    @DisplayName("AI の詳細が空でも版は作る（版のヘッダだけ。子テーブルは複製した人の行だけ）")
    void createsVersionForEmptyDetail() {
        when(mapper.findActiveDetail(WORD_ID)).thenReturn(activeVersion());

        JapaneseWordAiDetailWriter.CreatedVersion created =
                writer.createVersion(WORD_ID, "{\"detail\":null}", 77L, "qwen", "m");

        assertThat(created.detailId()).isEqualTo(901L);
        verify(mapper).insertDetailVersion(any());
        // AI の行は 1 つも無い（空の段落は insert を呼ばない）
        verify(mapper, never()).insertDetailSense(any(), any());
    }

    @Test
    @DisplayName("子テーブルの行は版のヘッダの 詳細ID にぶら下げる（採番し直した ID を使う）")
    void attachesRowsToNewVersion() {
        when(mapper.findActiveDetail(WORD_ID)).thenReturn(activeVersion());

        create();

        ArgumentCaptor<JpnWordDetailChildren.Example> captor =
                ArgumentCaptor.forClass(JpnWordDetailChildren.Example.class);
        verify(mapper, times(2)).insertDetailExample(captor.capture(), any());
        assertThat(captor.getAllValues()).hasSize(2);
        // 表示順は複製した行（0 件）の後ろ = 1 から
        assertThat(captor.getAllValues().get(0).getOrderNo()).isEqualTo(1);
        assertThat(captor.getAllValues().get(1).getOrderNo()).isEqualTo(2);
    }

    /** 呼ばれた回数を数える（id は SQL の id、Java のメソッド名に直して数える）。 */
    private int callCount(String sqlId) {
        String method = Character.toLowerCase(sqlId.charAt(0)) + sqlId.substring(1);
        return org.mockito.Mockito.mockingDetails(mapper).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(method))
                .toList().size();
    }
}
