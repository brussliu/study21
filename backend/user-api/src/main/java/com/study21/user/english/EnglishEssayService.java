package com.study21.user.english;

import com.study21.user.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

/**
 * 英作文AI添削の「作文データの層」（`ENG_英作文情報` / `ENG_英作文画像情報`）。
 *
 * <p><strong>見え方</strong>は「自分の作文 ＋ 自分の子どもの作文」（保護者）。他人の家庭の作文・
 * 存在しない作文は 404（存在も隠す）。**書き込みは本人だけ**で、保護者は子どもの作文を閲覧しても
 * 代理で提出・更新・削除・画像アップはできない（404）。家族の紐付けは
 * `ACC_アカウント.保護者ID`（生徒 → 保護者）で、SQL（`EnglishEssayMapper.xml` の `EssayScope`）の中で
 * 解決する（新しい関係表は作らない）。</p>
 *
 * <p>`ENG_AI添削履歴情報` は**参照するだけ**（書き込むのは admin-api の働き手）。</p>
 */
public interface EnglishEssayService {

    /** 一覧（`登録日時 DESC, 英作文ID DESC`。`状態コード='A'` だけ。自分の分＋子どもの分）。 */
    EnglishEssayModels.EssayListResult search(long accountId, String keyword, String level,
                                              String dateFrom, String dateTo, int page, int size);

    /** 作文 1 件（自分の分＋子どもの分。画像と添削の历次つき。添削は回数の昇順＝古い順）。 */
    EnglishEssayModels.EssayDetail detail(long accountId, long essayId);

    /** 作文を作る（**自分の作文として**。語数は本文から数える。本文が空なら 0）。 */
    EnglishEssayModels.EssayDetail create(UserPrincipal user, EnglishEssayModels.CreateRequest request);

    /**
     * 作文を更新する（本文は語数を数え直し、`バージョン` を +1）。**自分の作文だけ**（保護者は
     * 子どもの作文を更新できない＝404）。
     *
     * <p>`images` はこの作文の画像の一覧そのもの。**含まれない画像の行は削除**し、含まれる行は
     * 区分と表示順を更新する（`null` は画像を触らない。`[]` は全部消す）。</p>
     */
    EnglishEssayModels.EssayDetail update(UserPrincipal user, long essayId,
                                          EnglishEssayModels.UpdateRequest request);

    /** 論理削除（`状態コード='X'`。行と添削の履歴は残す）。**自分の作文だけ**。 */
    void delete(UserPrincipal user, long essayId);

    /** 画像の実体（自分の作文と自分の子どもの作文の画像だけ。無ければ 404）。 */
    EnglishEssayModels.ImageFile image(long accountId, long essayId, long imageId);

    /**
     * 画像 1 枚を上げる（**自分の作文だけ**。枚数・大きさは設定 `ENGLISH_ESSAY_MAX_IMAGES` / `_MAX_IMAGE_MB`）。
     *
     * <p>OCR の生の結果（`recognizedText` / `confidence`）は**任意**。null の欄は書かない
     * （`ENG_英作文画像情報` の `認識テキスト` / `認識信頼度` は NULL のまま）。</p>
     */
    EnglishEssayModels.ImageUploadResult uploadImage(UserPrincipal user, long essayId, MultipartFile file,
                                                     String category, Integer order,
                                                     String recognizedText, Integer confidence);

    /** 画像 1 枚を上げる（OCR の生の結果を送らない回）。 */
    default EnglishEssayModels.ImageUploadResult uploadImage(UserPrincipal user, long essayId,
                                                             MultipartFile file, String category, Integer order) {
        return uploadImage(user, essayId, file, category, order, null, null);
    }

    /**
     * AI 添削の**受付**（`POST /api/user/english-essays/{essayId}/gradings` の中身）。
     *
     * <p>**自分の作文だけ**（保護者は子どもの作文で受付できない＝代理で AI 費用を使わせない）。
     * 他人・存在しない・削除済みは 404（存在も隠す）で、admin-api を呼ばない。
     * 通ったら admin-api の内部入口（合言葉つき）へ転調し、受付けた回を返す。</p>
     *
     * @param round 何回目か（null なら admin-api が決める「次の回」）
     * @throws com.study21.common.core.exception.NotFoundException 自分の有効な作文でないとき
     * @throws com.study21.common.core.exception.ConflictException  内部入口が断った・答えられないとき（日本語の理由）
     */
    EnglishEssayModels.GradingAccepted acceptGrading(UserPrincipal user, long essayId, Integer round);

    /** 画像の上限（設定 `ENGLISH_ESSAY_MAX_IMAGES` / `_MAX_IMAGE_MB`。読めないときは既定 8 / 10）。 */
    EnglishEssayModels.ImageLimits limits();
}
