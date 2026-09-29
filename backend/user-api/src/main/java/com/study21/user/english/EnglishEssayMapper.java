package com.study21.user.english;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 英作文のデータ（`ENG_英作文情報` / `ENG_英作文画像情報`）と、添削履歴（`ENG_AI添削履歴情報`）の
 * Mapper。SQL は `resources/mapper/EnglishEssayMapper.xml`。
 *
 * <p><strong>読み取り</strong>は「自分の作文 ＋ 自分の子どもの作文」（`EssayScope` が
 * `ACC_アカウント.保護者ID` から SQL の中で家族を解決する。`accountId` を必ず渡す）。
 * <strong>書き込み</strong>（`insert` / `update` / `logicalDelete` / `insertImage`）は
 * 「`利用者アカウントID` = 自分のアカウントID」の行だけを対象にする（保護者は閲覧のみ）。
 * 見えない行は返らないので、サービスは「返らなければ 404」にできる（存在も隠す）。</p>
 *
 * <p>添削履歴は**読むだけ**（書き込むのは admin-api の働き手）。</p>
 *
 * <p>JDBC を直接使わない（`SqlLoggingInterceptor` が DB 操作を記録する。docs/LOGGING.md）。</p>
 */
@Mapper
public interface EnglishEssayMapper {

    /** 一覧の件数（絞り込みは一覧と同じ。自分の分＋子どもの分）。 */
    long count(@Param("accountId") long accountId,
               @Param("keyword") String keyword,
               @Param("level") String level,
               @Param("dateFrom") String dateFrom,
               @Param("dateTo") String dateTo);

    /** 一覧（`登録日時 DESC, 英作文ID DESC`）。画像枚数・最新の添削・持ち主を 1 クエリで付ける。 */
    List<EnglishEssayListEntity> search(@Param("accountId") long accountId,
                                        @Param("keyword") String keyword,
                                        @Param("level") String level,
                                        @Param("dateFrom") String dateFrom,
                                        @Param("dateTo") String dateTo,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    /** 作文 1 件（**自分の分＋自分の子どもの分だけ**返る。他人・存在しないときは null）。 */
    EnglishEssayEntity findById(@Param("essayId") long essayId,
                                @Param("accountId") long accountId);

    /** 作文を作る（採番した `英作文ID` を書き戻す）。 */
    int insert(EnglishEssayEntity entity);

    /** 作文を更新する（語数・バージョン +1。**本人の行だけ**。保護者は子どもの作文を更新できない）。 */
    int update(EnglishEssayEntity entity);

    /** 論理削除（`状態コード='X'`。行と添削の履歴は残す）。更新行が 0 なら他人・存在しない。 */
    int logicalDelete(@Param("essayId") long essayId,
                      @Param("accountId") long accountId,
                      @Param("updatedBy") long updatedBy);

    /** その作文の画像（表示順の昇順）。 */
    List<EnglishEssayImageEntity> listImages(@Param("essayId") long essayId);

    /** 画像 1 枚（その作文の行だけ）。 */
    EnglishEssayImageEntity findImage(@Param("essayId") long essayId,
                                      @Param("imageId") long imageId);

    /** その作文の画像の枚数（上限の判定に使う）。 */
    long countImages(@Param("essayId") long essayId);

    /** その表示順が既に使われているか（UNIQUE 制約で落とす前に日本語で断るため）。 */
    long countImageOrder(@Param("essayId") long essayId,
                         @Param("orderNo") int orderNo);

    /** 画像の行を作る（採番した `英作文画像ID` を書き戻す）。 */
    int insertImage(EnglishEssayImageEntity entity);

    /**
     * 表示順を**いったん退避する**（`表示順 + offset`）。
     *
     * <p>`UNIQUE(英作文ID, 表示順)` があるので、入れ替えの途中で重複してしまう。
     * `CHECK(表示順 >= 1)` があるので負の値へは逃がせず、**増やす**方向へ逃がす。</p>
     */
    int shiftImageOrders(@Param("essayId") long essayId, @Param("offset") int offset);

    /** 要求に含まれない画像の行を消す（空なら全部消える）。 */
    int deleteImagesNotIn(@Param("essayId") long essayId, @Param("imageIds") List<Long> imageIds);

    /** 画像 1 枚の区分と表示順を更新する（ファイルの情報は変えない）。 */
    int updateImagePlacement(@Param("essayId") long essayId,
                             @Param("imageId") long imageId,
                             @Param("category") String category,
                             @Param("orderNo") int orderNo);

    /**
     * 画像 1 枚の **OCR の生の結果**（`認識テキスト` / `認識信頼度`）を更新する。
     *
     * <p>**渡された欄だけ**を更新する（`null` の欄は既存値のまま＝消さない）。どちらも `null` のときに
     * 呼ぶと SET が空になって SQL が壊れるので、**呼ぶ側が「1 つ以上ある」ことを確かめてから**呼ぶ。</p>
     */
    int updateImageRecognition(@Param("essayId") long essayId,
                               @Param("imageId") long imageId,
                               @Param("recognizedText") String recognizedText,
                               @Param("confidence") Integer confidence);

    /** 添削の履歴（回数の昇順＝古い順）。レポートの JSON も返す（**参照のみ**）。 */
    List<EnglishEssayGradingEntity> listGradings(@Param("essayId") long essayId);
}
