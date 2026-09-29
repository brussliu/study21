package com.study21.user.english;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 英作文AI添削の「作文データの層」の実装。
 *
 * <p>規則（この層が守ること）:</p>
 * <ul>
 *   <li>**見え方は 2 つ**: 自分の作文 ＋ **自分の子どもの作文**（`EssayScope` が `ACC_アカウント.保護者ID`
 *       から SQL の中で家族を解決する。`docs/PERMISSION_MATRIX.md` の「英作文AI添削＝保護者は子の結果を
 *       閲覧」）。返らなければ 404 にする（他人の家庭の作文は存在も隠す）</li>
 *   <li>**書き込みは本人だけ**: 登録・更新・削除・画像アップ・添削の受付は
 *       {@code requireWritableOwn}（`利用者アカウントID = 自分`）を通す。保護者は子どもの作文を
 *       閲覧できても、代理で提出・修正・削除はできない（見えても書けないときは 404）</li>
 *   <li>一覧は `状態コード='A'` だけ（論理削除は出さない）。並びは `登録日時 DESC, 英作文ID DESC`</li>
 *   <li>画像の枚数・大きさは設定（`ENGLISH_ESSAY_MAX_IMAGES` / `_MAX_IMAGE_MB`）。超えたら
 *       **日本語の理由**で 400（1 枚も保存しない）</li>
 *   <li>添削（`ENG_AI添削履歴情報`）は**参照だけ**。`添削結果JSON` はそのまま JSON にして返す。
 *       **受付（`acceptGrading`）だけは admin-api の内部入口を合言葉つきで呼ぶ**（受付の行を書くのは
 *       admin-api。所有者の確認はここで済ませてから呼ぶ）</li>
 *   <li>画像の実体は DB の外（{@link EnglishEssayStorage}）。所有者はディレクトリの規約で守る</li>
 *   <li>OCR の生の結果（`認識テキスト` / `認識信頼度`）は、画面が保存時に送ってきたときだけ書く
 *       （省略された欄の既存値を消さない）</li>
 * </ul>
 *
 * <p>語数の数え方は画面（`frontend/pc-web/src/features/english-essay/grading.ts` の
 * {@code countWords}）と同じ「空白で区切った語の数」にする（画面の表示と食い違わせない）。</p>
 */
@Service
public class EnglishEssayServiceImpl implements EnglishEssayService {

    private static final Logger log = LoggerFactory.getLogger(EnglishEssayServiceImpl.class);

    /**
     * 表示順の入れ替えで**いったん退避する**ときのずらし幅。
     *
     * <p>`UNIQUE(英作文ID, 表示順)` があるので、1↔2 の入れ替えを直接やると途中で重複する。
     * `CHECK(表示順 >= 1)` があるので負の値へは逃がせず、この幅だけ**増やして**から確定する
     * （1 トランザクションの中なので、途中の値は外から見えない）。</p>
     */
    public static final int ORDER_SHIFT = 1_000_000;

    private final EnglishEssayMapper mapper;
    private final EnglishEssayStorage storage;
    private final EnglishEssaySettings settings;
    private final EnglishEssayAiAdminClient aiAdminClient;
    private final ObjectMapper objectMapper;

    public EnglishEssayServiceImpl(EnglishEssayMapper mapper, EnglishEssayStorage storage,
                                   EnglishEssaySettings settings, EnglishEssayAiAdminClient aiAdminClient,
                                   ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.storage = storage;
        this.settings = settings;
        this.aiAdminClient = aiAdminClient;
        this.objectMapper = objectMapper;
    }

    // ================================================================ 一覧

    @Override
    @Transactional(readOnly = true)
    public EnglishEssayModels.EssayListResult search(long accountId, String keyword, String level,
                                                     String dateFrom, String dateTo, int page, int size) {
        int safeSize = size <= 0 ? EnglishEssayModels.DEFAULT_SIZE
                : Math.min(size, EnglishEssayModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String safeLevel = normalizeChoice(level, EnglishEssayModels.LEVELS, "英検級");
        String safeFrom = normalizeDate(dateFrom, "開始日");
        String safeTo = normalizeDate(dateTo, "終了日");
        String safeKeyword = blankToNull(keyword);

        long total = mapper.count(accountId, safeKeyword, safeLevel, safeFrom, safeTo);
        List<EnglishEssayModels.EssayRow> items = total == 0
                ? List.of()
                : mapper.search(accountId, safeKeyword, safeLevel, safeFrom, safeTo,
                        safeSize, (safePage - 1) * safeSize).stream().map(this::toRow).toList();
        int totalPages = (int) ((total + safeSize - 1) / safeSize);
        return new EnglishEssayModels.EssayListResult(items, total, safePage, safeSize, totalPages);
    }

    // ================================================================ 詳細

    @Override
    @Transactional(readOnly = true)
    public EnglishEssayModels.EssayDetail detail(long accountId, long essayId) {
        EnglishEssayEntity essay = requireVisible(accountId, essayId);
        List<EnglishEssayModels.ImageRow> images = mapper.listImages(essayId).stream()
                .map(EnglishEssayServiceImpl::toImageRow)
                .toList();
        List<EnglishEssayModels.GradingRow> gradings = mapper.listGradings(essayId).stream()
                .map(this::toGradingRow)
                .toList();
        return toDetail(essay, images, gradings);
    }

    // ================================================================ 作成

    @Override
    @Transactional
    public EnglishEssayModels.EssayDetail create(UserPrincipal user, EnglishEssayModels.CreateRequest request) {
        if (request == null) {
            throw new ValidationException("作成する内容を指定してください。");
        }
        EnglishEssayEntity essay = new EnglishEssayEntity();
        essay.setAccountId(user.accountId());
        essay.setLevel(requireLevel(request.level()));
        essay.setTitle(requireTitle(request.title()));
        essay.setTitleZh(normalizeTitle(request.titleZh(), "題（中国語）"));
        essay.setQuestionText(blankToNull(request.questionText()));
        essay.setEssayText(blankToNull(request.essayText()));
        essay.setWordCount(countWords(request.essayText()));
        essay.setStateCode("A");
        essay.setVersion(1);
        essay.setCreatedBy(user.accountId());

        mapper.insert(essay);
        return detail(user.accountId(), essay.getEssayId());
    }

    // ================================================================ 更新

    @Override
    @Transactional
    public EnglishEssayModels.EssayDetail update(UserPrincipal user, long essayId,
                                                 EnglishEssayModels.UpdateRequest request) {
        if (request == null) {
            throw new ValidationException("更新する内容を指定してください。");
        }
        EnglishEssayEntity essay = requireWritableOwn(user.accountId(), essayId);
        essay.setLevel(requireLevel(request.level()));
        essay.setTitle(requireTitle(request.title()));
        essay.setTitleZh(normalizeTitle(request.titleZh(), "題（中国語）"));
        essay.setQuestionText(blankToNull(request.questionText()));
        essay.setEssayText(blankToNull(request.essayText()));
        essay.setWordCount(countWords(request.essayText()));
        essay.setUpdatedBy(user.accountId());

        // 画像の指定は**書き込む前に**検査する（1 つでも不正なら作文も画像も変えない）
        List<Placement> placements = request.images() == null ? null : planImages(essayId, request.images());
        if (mapper.update(essay) == 0) {
            // 直前に読めているので通常は起きない（他人へ移った・消えた等）。存在を隠して 404
            throw new NotFoundException("英作文が見つかりません。");
        }
        if (placements != null) {
            applyImages(essayId, placements);
        }
        return detail(user.accountId(), essayId);
    }

    /**
     * 画像の一覧を要求どおりに直す（**含まれない行は消し、含まれる行は区分と表示順を更新**）。
     *
     * <p>`UNIQUE(英作文ID, 表示順)` に当たらないよう、いったん全部を退避してから確定する。
     * OCR の生の結果（`認識テキスト` / `認識信頼度`）は**送られてきたときだけ**書く
     * （省略された欄の既存値を消さない）。</p>
     */
    private void applyImages(long essayId, List<Placement> placements) {
        if (placements.isEmpty()) {
            // 空の配列＝全部消す（画像のファイルは残るが、DB から消えるので配信されない）
            mapper.deleteImagesNotIn(essayId, List.of());
            return;
        }
        List<Long> keep = placements.stream().map(Placement::imageId).toList();
        mapper.shiftImageOrders(essayId, ORDER_SHIFT);
        mapper.deleteImagesNotIn(essayId, keep);
        for (Placement placement : placements) {
            mapper.updateImagePlacement(essayId, placement.imageId(), placement.category(), placement.order());
            if (placement.recognizedText() != null || placement.confidence() != null) {
                // どちらか 1 つ以上が指定されたときだけ（SET が空にならないように）
                mapper.updateImageRecognition(essayId, placement.imageId(),
                        placement.recognizedText(), placement.confidence());
            }
        }
    }

    /** 要求の画像の一覧を検査して、適用する形（区分と表示順）にする。 */
    private List<Placement> planImages(long essayId, List<EnglishEssayModels.ImageRef> refs) {
        if (refs.isEmpty()) {
            return List.of();
        }
        Set<Long> owned = new LinkedHashSet<>();
        for (EnglishEssayImageEntity image : mapper.listImages(essayId)) {
            owned.add(image.getImageId());
        }
        Set<Integer> orders = new HashSet<>();
        List<Placement> placements = new ArrayList<>(refs.size());
        for (EnglishEssayModels.ImageRef ref : refs) {
            if (ref == null || ref.imageId() == null) {
                throw new ValidationException("画像の指定が正しくありません。");
            }
            if (!owned.contains(ref.imageId())) {
                throw new ValidationException("指定された画像がこの作文にありません。");
            }
            String category = normalizeChoice(ref.category(), EnglishEssayModels.IMAGE_CATEGORIES, "画像区分");
            if (category == null) {
                throw new ValidationException("画像区分（question / answer）を指定してください。");
            }
            int order = ref.order() == null ? 0 : ref.order();
            if (order < 1 || order > EnglishEssayModels.MAX_IMAGE_ORDER) {
                throw new ValidationException("表示順は 1〜" + EnglishEssayModels.MAX_IMAGE_ORDER
                        + " の範囲で指定してください。");
            }
            if (!orders.add(order)) {
                throw new ValidationException("表示順が重複しています。");
            }
            // OCR の生の結果（任意）。**空白は「指定なし」**として扱う（既存値を消さない）
            String recognizedText = blankToNull(ref.recognizedText());
            Integer confidence = requireConfidence(ref.confidence());
            placements.add(new Placement(ref.imageId(), category, order, recognizedText, confidence));
        }
        return placements;
    }

    /** 認識信頼度（0〜100。`ENG_英作文画像情報` の CHECK と同じ）。null は「指定なし」。 */
    private static Integer requireConfidence(Integer confidence) {
        if (confidence == null) {
            return null;
        }
        if (confidence < 0 || confidence > 100) {
            throw new ValidationException("認識信頼度は 0〜100 の範囲で指定してください。");
        }
        return confidence;
    }

    /** 検証済みの画像の置き方（区分・表示順と、任意の OCR の生の結果）。 */
    private record Placement(long imageId, String category, int order,
                             String recognizedText, Integer confidence) {
    }

    // ================================================================ 削除

    @Override
    @Transactional
    public void delete(UserPrincipal user, long essayId) {
        requireWritableOwn(user.accountId(), essayId);
        if (mapper.logicalDelete(essayId, user.accountId(), user.accountId()) == 0) {
            throw new NotFoundException("英作文が見つかりません。");
        }
    }

    // ================================================================ 画像

    @Override
    @Transactional
    public EnglishEssayModels.ImageUploadResult uploadImage(UserPrincipal user, long essayId, MultipartFile file,
                                                            String category, Integer order,
                                                            String recognizedText, Integer confidence) {
        long accountId = user.accountId();
        requireWritableOwn(accountId, essayId);
        String safeCategory = normalizeChoice(category, EnglishEssayModels.IMAGE_CATEGORIES, "画像区分");
        if (safeCategory == null) {
            throw new ValidationException("画像区分（question / answer）を指定してください。");
        }
        // OCR の生の結果（任意）。**空白は「指定なし」**（既存値を消さない）
        String safeRecognizedText = blankToNull(recognizedText);
        Integer safeConfidence = requireConfidence(confidence);

        EnglishEssaySettings.Snapshot snapshot = settings.load();
        int maxImages = settings.maxImages(snapshot);
        long current = mapper.countImages(essayId);
        if (current >= maxImages) {
            throw new ValidationException("画像は " + maxImages + " 枚までです。");
        }
        int orderNo = order == null ? (int) current + 1 : order;
        if (orderNo < 1 || orderNo > EnglishEssayModels.MAX_IMAGE_ORDER) {
            throw new ValidationException("表示順は 1〜" + EnglishEssayModels.MAX_IMAGE_ORDER
                    + " の範囲で指定してください。");
        }
        if (mapper.countImageOrder(essayId, orderNo) > 0) {
            throw new ValidationException("表示順 " + orderNo + " は既に使われています。");
        }

        EnglishEssayStorage.StoredImage stored = storage.store(accountId, file, settings.maxImageMb(snapshot));

        EnglishEssayImageEntity entity = new EnglishEssayImageEntity();
        entity.setEssayId(essayId);
        entity.setOrderNo(orderNo);
        entity.setCategory(safeCategory);
        entity.setOriginalFileName(stored.originalFileName());
        entity.setStoredFileName(stored.storedFileName());
        entity.setRelativePath(stored.relativePath());
        entity.setMimeType(stored.mimeType());
        entity.setFileSize(stored.size());
        entity.setRecognizedText(safeRecognizedText);
        entity.setConfidence(safeConfidence);
        entity.setCreatedBy(accountId);
        mapper.insertImage(entity);

        return new EnglishEssayModels.ImageUploadResult(entity.getImageId(), orderNo, safeCategory,
                stored.originalFileName(), stored.mimeType(), stored.size());
    }

    @Override
    @Transactional(readOnly = true)
    public EnglishEssayModels.ImageFile image(long accountId, long essayId, long imageId) {
        // 他人の作文の画像は 404（作文の見え方＝自分の作文と自分の子どもの作文、を見てから画像の行を引く）
        requireVisible(accountId, essayId);
        EnglishEssayImageEntity image = mapper.findImage(essayId, imageId);
        if (image == null) {
            throw new NotFoundException("画像が見つかりません。");
        }
        Path path = storage.resolve(image.getRelativePath(), image.getStoredFileName());
        if (!Files.isReadable(path)) {
            log.warn("英作文の画像の実体がありません。essayId={} imageId={} path={}", essayId, imageId, path);
            throw new NotFoundException("画像が見つかりません。");
        }
        String contentType = image.getMimeType() == null || image.getMimeType().isBlank()
                ? "application/octet-stream" : image.getMimeType();
        return new EnglishEssayModels.ImageFile(path, contentType, image.getOriginalFileName());
    }

    // ================================================================ 添削の受付

    /**
     * 添削の受付（`POST /api/user/english-essays/{essayId}/gradings`）。
     *
     * <p><b>ここが認可の要</b>: 画面から admin-api を直接叩かせると `essayId` を差し替えるだけで
     * **他人の作文に添削を積めた**（AI 費用を使わせられる）。**自分の有効な作文**であることを
     * SQL の条件（`利用者アカウントID` と `状態コード='A'`）で確かめ、返らなければ 404 にして
     * **admin-api を呼ばない**。通ったときだけ、合言葉つきの内部入口へ転調する。</p>
     *
     * <p>`round` を省略したときは**送らない**（次の回を決める規則は admin-api に 1 つだけ置く）。</p>
     *
     * <p>`@Transactional` を付けない: 受付は AI を待たないが、**HTTP の間ずっと DB の接続を
     * 握らない**ため（所有の確認は 1 回の SELECT で完結する）。</p>
     */
    @Override
    public EnglishEssayModels.GradingAccepted acceptGrading(UserPrincipal user, long essayId, Integer round) {
        requireWritableOwn(user.accountId(), essayId);
        log.info("english essay grading requested. accountId={} essayId={} round={}",
                user.accountId(), essayId, round);
        try {
            EnglishEssayAiAdminClient.Accepted accepted = aiAdminClient.acceptGrading(essayId, round);
            return new EnglishEssayModels.GradingAccepted(accepted.gradingId(), accepted.round(),
                    accepted.message());
        } catch (EnglishEssayAiAdminClient.EnglishEssayCallException cause) {
            // 内部入口が断った・答えられない理由を**日本語のまま**画面へ返す（500 にしない）。
            // 受理できていないので「成功」とは言わない
            log.warn("english essay grading internal call failed. essayId={} retryable={}",
                    essayId, cause.retryable());
            throw new ConflictException(cause.getMessage());
        }
    }

    // ================================================================ 上限（画面へ配る）

    /**
     * 画像の上限（`GET /api/user/english-essays/limits`）。
     *
     * <p>値は**設定が唯一の出所**（`ENGLISH_ESSAY_MAX_IMAGES` / `ENGLISH_ESSAY_MAX_IMAGE_MB`）。
     * 画面が同じ値を二重に持つと、設定を変えたときに事前チェックだけ古いままになる。
     * **設定を読めないとき（DB が落ちている等）だけ**、seed と同じ既定（8 枚 / 10MB）へ落とす
     * （画面の事前チェックを止めない。保存そのものは設定を読めないと失敗するので、嘘は出さない）。</p>
     */
    @Override
    public EnglishEssayModels.ImageLimits limits() {
        try {
            EnglishEssaySettings.Snapshot snapshot = settings.load();
            return new EnglishEssayModels.ImageLimits(settings.maxImages(snapshot),
                    settings.maxImageMb(snapshot));
        } catch (RuntimeException cause) {
            log.warn("英作文AI添削の上限設定を読めませんでした。既定値を使います。cause={}",
                    cause.getClass().getSimpleName());
            return new EnglishEssayModels.ImageLimits(EnglishEssaySettings.DEFAULT_MAX_IMAGES,
                    EnglishEssaySettings.DEFAULT_MAX_IMAGE_MB);
        }
    }

    // -------------------------------------------------------------------- 内部

    /** **読み取り**の見え方: 自分の作文と、自分の子どもの作文を取る（それ以外は 404 で存在も隠す）。 */
    private EnglishEssayEntity requireVisible(long accountId, long essayId) {
        EnglishEssayEntity essay = mapper.findById(essayId, accountId);
        if (essay == null) {
            throw new NotFoundException("英作文が見つかりません。");
        }
        return essay;
    }

    /**
     * **書き込み**の所有判定: ログイン中のアカウント自身の作文だけを返す。
     *
     * <p>見え方は {@link #requireVisible}（家族を含む）と分ける。保護者は子どもの作文を**閲覧だけ**
     * でき、代理で提出・更新・削除・画像アップ・添削の受付はしない（見えても書けないときは 404 で
     * 存在を隠す。`docs/SECURITY_AND_ROLES.md`）。</p>
     */
    private EnglishEssayEntity requireWritableOwn(long accountId, long essayId) {
        EnglishEssayEntity essay = requireVisible(accountId, essayId);
        if (!Long.valueOf(accountId).equals(essay.getAccountId())) {
            throw new NotFoundException("英作文が見つかりません。");
        }
        return essay;
    }

    private EnglishEssayModels.EssayRow toRow(EnglishEssayListEntity entity) {
        EnglishEssayModels.LatestGrading latest = entity.getLatestGradingId() == null ? null
                : new EnglishEssayModels.LatestGrading(
                        entity.getLatestGradingId(),
                        intValue(entity.getLatestGradingRound()),
                        entity.getLatestGradingStatus(),
                        entity.getLatestGradingScore(),
                        entity.getLatestGradingMaxScore(),
                        iso(entity.getLatestGradingCreatedAt()));
        return new EnglishEssayModels.EssayRow(
                longValue(entity.getEssayId()),
                longValue(entity.getOwnerAccountId()),
                entity.getOwnerName(),
                entity.getLevel(),
                entity.getTitle(),
                entity.getTitleZh(),
                entity.getQuestionText(),
                entity.getEssayText(),
                intValue(entity.getWordCount()),
                intValue(entity.getImageCount()),
                intValue(entity.getQuestionImageCount()),
                intValue(entity.getAnswerImageCount()),
                iso(entity.getCreatedAt()),
                iso(entity.getUpdatedAt()),
                latest);
    }

    private EnglishEssayModels.EssayDetail toDetail(EnglishEssayEntity entity,
                                                    List<EnglishEssayModels.ImageRow> images,
                                                    List<EnglishEssayModels.GradingRow> gradings) {
        return new EnglishEssayModels.EssayDetail(
                longValue(entity.getEssayId()),
                longValue(entity.getOwnerAccountId() == null ? entity.getAccountId() : entity.getOwnerAccountId()),
                entity.getOwnerName(),
                entity.getLevel(),
                entity.getTitle(),
                entity.getTitleZh(),
                entity.getQuestionText(),
                entity.getEssayText(),
                intValue(entity.getWordCount()),
                entity.getStateCode(),
                iso(entity.getCreatedAt()),
                iso(entity.getUpdatedAt()),
                images,
                gradings);
    }

    private static EnglishEssayModels.ImageRow toImageRow(EnglishEssayImageEntity entity) {
        return new EnglishEssayModels.ImageRow(
                longValue(entity.getImageId()),
                intValue(entity.getOrderNo()),
                entity.getCategory(),
                entity.getOriginalFileName(),
                entity.getMimeType(),
                entity.getFileSize() == null ? 0L : entity.getFileSize(),
                entity.getRecognizedText(),
                entity.getConfidence());
    }

    private EnglishEssayModels.GradingRow toGradingRow(EnglishEssayGradingEntity entity) {
        return new EnglishEssayModels.GradingRow(
                longValue(entity.getGradingId()),
                intValue(entity.getRoundNo()),
                entity.getStatusCode(),
                entity.getLevel(),
                entity.getTitleJa(),
                entity.getTitleZh(),
                entity.getQuestionText(),
                entity.getEssayText(),
                intValue(entity.getWordCount()),
                entity.getScore(),
                entity.getMaxScore(),
                parseReport(entity.getReport()),
                entity.getFailureReason(),
                iso(entity.getStartedAt()),
                iso(entity.getFinishedAt()),
                iso(entity.getCreatedAt()));
    }

    /** `添削結果JSON`（JSONB）をそのまま JSON にする（壊れていても 500 にしない）。 */
    private JsonNode parseReport(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException cause) {
            log.warn("添削結果JSON を読めませんでした（先頭 60 文字）: {}",
                    json.substring(0, Math.min(60, json.length())));
            return null;
        }
    }

    /** 英単語の数（画面の `countWords` と同じ数え方。空白で区切る）。 */
    static int countWords(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return 0;
        }
        return value.split("\\s+").length;
    }

    private static String requireLevel(String level) {
        String value = normalizeChoice(level, EnglishEssayModels.LEVELS, "英検級");
        if (value == null) {
            throw new ValidationException("英検級を指定してください。");
        }
        return value;
    }

    private static String requireTitle(String title) {
        String value = normalizeTitle(title, "題");
        if (value == null) {
            throw new ValidationException("題を入力してください。");
        }
        return value;
    }

    private static String normalizeTitle(String title, String label) {
        String value = blankToNull(title);
        if (value != null && value.length() > EnglishEssayModels.TITLE_MAX) {
            throw new ValidationException(label + "は " + EnglishEssayModels.TITLE_MAX + " 文字以内で入力してください。");
        }
        return value;
    }

    /** 期間の指定（画面は `yyyy-MM-dd` を送る）。空は絞り込み無し。 */
    private static String normalizeDate(String value, String label) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text).toString();
        } catch (DateTimeParseException cause) {
            throw new ValidationException(label + "の日付が正しくありません（yyyy-MM-dd で指定してください）。");
        }
    }

    private static String normalizeChoice(String value, List<String> allowed, String label) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        if (!allowed.contains(text)) {
            throw new ValidationException(label + "は " + String.join(" / ", allowed) + " のいずれかを指定してください。");
        }
        return text;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long longValue(Long number) {
        return number == null ? 0L : number;
    }

    private static int intValue(Integer number) {
        return number == null ? 0 : number;
    }

    private static String iso(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toString();
    }
}
