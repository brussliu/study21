package com.study21.user.geometry;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * AI 生図・AI 画図助手の実装。
 *
 * <p>流れ（画面 = `GeometryAiView.vue` / `GeometryDrawView.vue`）:</p>
 * <pre>
 *   POST /requests/images（元画像を保存）
 *     → POST /requests（QUEUED。crop / kind / subKind / note を保存）
 *     → 画面が admin-api の run を 1 回呼ぶ（batC51 → 52 → 53）
 *     → GET /requests/{id} を 2 秒間隔でポーリング
 *     → READY なら作図画面（?geometryAiRequestId=）で確認 → POST /requests/{id}/confirm
 * </pre>
 *
 * <p>AI の成果物は**GeoGebra のコマンド列まで**。XML とサムネイルは作図画面の applet が作る
 * （サーバーに GeoGebra は無い）。保存は既存の `GeometryService.create` を通し、
 * `登録元コード = 'AI'` を付ける。</p>
 */
@Service
public class GeometryAiServiceImpl implements GeometryAiService {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiServiceImpl.class);

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    /** 指示前 XML として残す上限（2,000,000 文字。図形の作図データと同じ上限）。 */
    private static final int BEFORE_XML_LIMIT = 2_000_000;
    /** 画面が送るオブジェクト一覧の上限（長すぎるプロンプトを防ぐ）。 */
    private static final int OBJECTS_SUMMARY_LIMIT = 4_000;
    /** 失敗内容（実行できなかった行と理由）の上限。 */
    private static final int FAILURE_DETAIL_LIMIT = 2_000;
    /** 助手の履歴で返す既定の件数（画面の会話ログの上限に合わせる）。 */
    private static final int ASSIST_HISTORY_DEFAULT_LIMIT = 20;
    /** 助手の履歴で返す最大の件数（画面から大きく指定されても守る）。 */
    private static final int ASSIST_HISTORY_MAX_LIMIT = 50;
    /** 反映できなかった理由として残す上限（長文のコマンド列をそのまま入れない）。 */
    private static final int ASSIST_REASON_MAX = 500;
    /**
     * 助手の反映方法（記録用）。**新しい依頼は追加だけ**（利用者の指示）。
     *
     * <p>作り直したいときは利用者が作図画面で【全消去】してから指示する。REPLACE は過去の履歴の
     * 表示・互換のためにだけ残す（`GEO_AI画図指示情報.反映方法` の CHECK 制約もそのまま）。</p>
     */
    private static final String ASSIST_MODE_APPEND = "APPEND";
    /** 提案 JSON から取り出す最大タグ数。 */
    private static final int PROPOSAL_TAG_MAX = 20;

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private final GeometryAiRequestMapper requestMapper;
    private final GeometryAiAssistMapper assistMapper;
    private final GeometryAiStorage storage;
    private final GeometryAiSettings settings;
    private final GeometryService geometryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeometryAiServiceImpl(GeometryAiRequestMapper requestMapper,
                                 GeometryAiAssistMapper assistMapper,
                                 GeometryAiStorage storage,
                                 GeometryAiSettings settings,
                                 GeometryService geometryService) {
        this.requestMapper = requestMapper;
        this.assistMapper = assistMapper;
        this.storage = storage;
        this.settings = settings;
        this.geometryService = geometryService;
    }

    // ------------------------------------------------------------------ 設定

    @Override
    @Transactional(readOnly = true)
    public GeometryAiModels.OptionsResult options(UserPrincipal user) {
        GeometryAiSettings.Snapshot snapshot = settings.load();
        boolean enabled = settings.enabled(snapshot);
        boolean assistEnabled = settings.assistEnabled(snapshot);
        int dailyLimit = settings.dailyLimit(snapshot);
        long usedToday = user == null ? 0L : requestMapper.countTodayByAccount(user.accountId());
        // 有効／無効は「未設定なら有効」。無効にする項目は画面から外したため、
        // 未設定を理由にする案内は出さない（明示的に false のときだけ無効）
        String notice = "";
        if (!enabled) {
            notice = "「AI 生図」はシステム設定で無効になっています。";
        } else if (dailyLimit > 0 && usedToday >= dailyLimit) {
            notice = "本日の AI 生図は上限（" + dailyLimit + " 回）に達しました。明日またお試しください。";
        }
        return new GeometryAiModels.OptionsResult(enabled, assistEnabled,
                settings.maxImageMb(snapshot), settings.maxImagePixels(snapshot),
                settings.defaultCrop(snapshot), settings.defaultKind(snapshot), settings.approval(snapshot),
                dailyLimit, usedToday, notice);
    }

    // ------------------------------------------------------------ アップロード

    @Override
    @Transactional
    public GeometryAiModels.UploadResult upload(UserPrincipal user, MultipartFile file) {
        GeometryAiSettings.Snapshot snapshot = settings.load();
        requireEnabled(snapshot);
        GeometryAiStorage.StoredImage stored = storage.storeOriginal(user.accountId(), file,
                settings.maxImageMb(snapshot), settings.maxImagePixels(snapshot));
        log.info("geometry ai image uploaded. accountId={} bytes={} width={} height={}",
                user.accountId(), stored.size(), stored.width(), stored.height());
        return new GeometryAiModels.UploadResult(stored.token(), stored.fileName(), stored.mime(),
                stored.size(), stored.width(), stored.height());
    }

    // ------------------------------------------------------------------ 作成

    @Override
    @Transactional
    public GeometryAiModels.RequestStatus create(UserPrincipal user, GeometryAiModels.CreateRequest request) {
        GeometryAiSettings.Snapshot snapshot = settings.load();
        requireEnabled(snapshot);
        requireDailyLimit(user, snapshot);

        String imagePath = storage.resolveToken(user.accountId(), request.imageToken());
        GeometryAiStorage.StoredImage original = storage.load(imagePath);

        GeometryAiRequestEntity entity = new GeometryAiRequestEntity();
        entity.setRequestNo(nextRequestNo());
        entity.setStatusCode(GeometryAiModels.STATUS_QUEUED);
        entity.setOriginalPath(original.relativePath());
        entity.setOriginalName(original.fileName());
        entity.setOriginalMime(original.mime());
        entity.setOriginalSize(original.size());
        entity.setOriginalWidth(original.width());
        entity.setOriginalHeight(original.height());

        BigDecimal[] crop = normalizeCrop(request.crop(), settings.defaultCrop(snapshot));
        entity.setCropX(crop[0]);
        entity.setCropY(crop[1]);
        entity.setCropW(crop[2]);
        entity.setCropH(crop[3]);

        // 作図モード（A〜D）と結果種別。モードが無い（歴史的な画面）要求は今までどおり受ける
        String mode = normalizeMode(request.mode());
        String resultType = normalizeResultType(request.resultType(), mode, request.kind());
        entity.setMode(mode);
        entity.setRequestedOutputType(resultType);
        // 補充パラメータは**そのモードと種類に当てはまる項目だけ**を保存する
        // （画面に無い項目・切り替え前の項目を AI へ渡さない）
        entity.setSupplementsJson(GeometryAiSupplements.toJson(mode, resultType, request.supplements()));

        // 歴史的な列（利用者区分）は、モードがあるときは**結果種別から埋める**
        // （画面はもう分類を選ばない。古い画面・古い bundle のときだけ入力を使う）
        String kind = legacyKindOf(request.kind(), mode, resultType, settings.defaultKind(snapshot));
        entity.setUserKind(kind);
        entity.setUserSubKind(GeometryAiModels.KIND_FIGURE.equals(kind)
                ? normalizeSubKind(request.subKind()) : null);
        // モードがある要求では、古い分類が送られてきても使わない（DB の CHECK に合わせて NULL にする）
        if (mode != null && !GeometryAiModels.KIND_FIGURE.equals(kind)) {
            entity.setUserSubKind(null);
        }
        entity.setFigureType(figureTypeOf(resultType, kind));
        entity.setNote(trimToNull(request.note(), GeometryAiModels.NOTE_MAX));
        entity.setCreatedBy(user.accountId());
        entity.setSourceCode("APP");
        // 受付時に**そのときの有効な設定**を固定する（待ち行列に並んでいる間に設定を変えても、
        // この要求は受付時のプロンプト・モデル・上限で実行される）
        entity.setSettingsSnapshotJson(settings.pinnedConfigJson(mode, 1));
        requestMapper.insert(entity);

        GeometryAiRequestEntity saved = requireOwned(user, entity.getRequestId());
        log.info("geometry ai request created. requestId={} requestNo={} accountId={} mode={} resultType={}"
                        + " kind={} figureType={}",
                saved.getRequestId(), saved.getRequestNo(), user.accountId(), mode, resultType, kind,
                saved.getFigureType());
        return toStatus(saved);
    }

    // ------------------------------------------------------------------ 参照

    @Override
    @Transactional(readOnly = true)
    public GeometryAiModels.RequestListResult list(UserPrincipal user, String status, int page, int size) {
        int safeSize = size <= 0 ? GeometryAiModels.DEFAULT_SIZE : Math.min(size, GeometryAiModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String statusFilter = normalizeStatusFilter(status);
        long total = requestMapper.count(user.accountId(), statusFilter);
        List<GeometryAiModels.RequestRow> items = requestMapper
                .search(user.accountId(), statusFilter, safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(GeometryAiServiceImpl::toRow)
                .toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new GeometryAiModels.RequestListResult(items, total, safePage, safeSize, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public GeometryAiModels.RequestDetail detail(UserPrincipal user, long requestId) {
        return toDetail(requireOwned(user, requestId));
    }

    @Override
    @Transactional(readOnly = true)
    public GeometryAiModels.ImageData image(UserPrincipal user, long requestId, String kind) {
        GeometryAiRequestEntity entity = requireOwned(user, requestId);
        boolean cropped = GeometryAiModels.IMAGE_KIND_CROPPED.equalsIgnoreCase(kind);
        String dir = cropped ? entity.getCroppedPath() : entity.getOriginalPath();
        String name = cropped ? entity.getCroppedName() : entity.getOriginalName();
        byte[] bytes = storage.read(dir, name);
        if (bytes == null) {
            return null;
        }
        String mime = cropped ? storage.mimeOf(name) : valueOr(entity.getOriginalMime(), storage.mimeOf(name));
        return new GeometryAiModels.ImageData(bytes, mime, name);
    }

    // ------------------------------------------------------------ 再試行・取消

    @Override
    @Transactional
    public GeometryAiModels.RequestStatus retry(UserPrincipal user, long requestId, Integer version) {
        GeometryAiRequestEntity entity = requireOwned(user, requestId);
        GeometryAiSettings.Snapshot snapshot = settings.load();
        requireEnabled(snapshot);
        if (!isRetryable(entity)) {
            throw new ConflictException("この状態では【もう一度生成】できません（"
                    + statusLabel(entity.getStatusCode()) + "）。");
        }
        requireDailyLimit(user, snapshot);
        // もう一度生成も**利用者が明示的に頼んだ再実行**なので、そのときの有効な設定で固定し直す
        // （設定を直したのに古い版のままだと、いつまでも同じ失敗を繰り返す）。実行版を +1 する
        entity.setSettingsSnapshotJson(settings.pinnedConfigJson(entity.getMode(),
                settings.nextRevision(entity.getSettingsSnapshotJson())));
        entity.setUpdatedBy(user.accountId());
        entity.setUpdateSourceCode("APP");
        entity.setVersion(requireVersion(entity, version));
        if (requestMapper.updateRetried(entity) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        log.info("geometry ai request retried. requestId={} accountId={}", requestId, user.accountId());
        return toStatus(requireOwned(user, requestId));
    }

    /**
     * 追加入力待ち・失敗した要求を**条件を直して送り直す**（同じ要求行を使い回す）。
     *
     * <p>画像はもうサーバーにあるので送り直さない。読み取る範囲・作図方法・結果種別・補充・
     * 補足要求を更新して `QUEUED` に戻す（実行はバックエンドの働き手が行う）。**行を増やさない**ので、
     * 一覧に古いタスクと新しいタスクが二重に並ばず、図形も 1 つしかできない。</p>
     */
    @Override
    @Transactional
    public GeometryAiModels.RequestStatus resubmit(UserPrincipal user, long requestId,
                                                   GeometryAiModels.ResubmitRequest request) {
        GeometryAiRequestEntity entity = requireOwned(user, requestId);
        GeometryAiSettings.Snapshot snapshot = settings.load();
        requireEnabled(snapshot);
        if (!isResubmittable(entity)) {
            throw new ConflictException("この状態では送り直せません（" + statusLabel(entity.getStatusCode()) + "）。"
                    + "内容を確かめたいときは一覧から作図を開いてください。");
        }
        // AI をもう一度呼ぶので、日次の上限を数え直す
        requireDailyLimit(user, snapshot);

        // 送り直しは新しい画面（作図方法を選ぶ）からだけ行う。方法が無い要求は受け付けない
        String mode = normalizeMode(request.mode());
        if (mode == null) {
            throw new ValidationException("作図方法（A / B / C / D）を指定してください。");
        }
        String resultType = normalizeResultType(request.resultType(), mode, null);
        BigDecimal[] crop = normalizeCrop(request.crop(), settings.defaultCrop(snapshot));
        // 分類は結果種別から読み替える（画面はもう分類を選ばない）
        String kind = legacyKindOf(null, mode, resultType, settings.defaultKind(snapshot));

        entity.setMode(mode);
        entity.setRequestedOutputType(resultType);
        entity.setSupplementsJson(GeometryAiSupplements.toJson(mode, resultType, request.supplements()));
        entity.setUserKind(kind);
        // 図形の種類（subKind）は新しい画面では選ばないので使わない（DB の CHECK に合わせて NULL）
        entity.setUserSubKind(null);
        entity.setFigureType(figureTypeOf(resultType, kind));
        entity.setNote(trimToNull(request.note(), GeometryAiModels.NOTE_MAX));
        entity.setCropX(crop[0]);
        entity.setCropY(crop[1]);
        entity.setCropW(crop[2]);
        entity.setCropH(crop[3]);
        entity.setStatusCode(GeometryAiModels.STATUS_QUEUED);
        entity.setUpdatedBy(user.accountId());
        entity.setUpdateSourceCode("APP");
        // 送り直しも**そのときの有効な設定で固定し直す**（利用者が条件を直して頼んでいるので、
        // 直した設定を拾わせる。並んでいる間は変わらない）。
        // **実行版を +1** して、どの版で作ったかを追えるようにする
        entity.setSettingsSnapshotJson(
                settings.pinnedConfigJson(mode, settings.nextRevision(entity.getSettingsSnapshotJson())));
        if (requestMapper.updateResubmitted(entity) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        log.info("geometry ai request resubmitted. requestId={} accountId={} mode={} resultType={}",
                requestId, user.accountId(), mode, resultType);
        return toStatus(requireOwned(user, requestId));
    }

    @Override
    @Transactional
    public GeometryAiModels.RequestStatus cancel(UserPrincipal user, long requestId, Integer version) {
        GeometryAiRequestEntity entity = requireOwned(user, requestId);
        String status = entity.getStatusCode();
        if (!GeometryAiModels.STATUS_QUEUED.equals(status)
                && !GeometryAiModels.STATUS_PREPROCESSED.equals(status)) {
            // 生成中・生成後は取り消せない（お金を使ったあと。設計 §4.1）
            throw new ConflictException(GeometryAiModels.STATUS_GENERATING.equals(status)
                    ? "生成中のため取り消せません。終わるまでお待ちください。"
                    : "この状態では取り消せません（" + statusLabel(status) + "）。");
        }
        if (requestMapper.updateCancelled(requestId, user.accountId(), "APP", requireVersion(entity, version)) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        log.info("geometry ai request cancelled. requestId={} accountId={}", requestId, user.accountId());
        return toStatus(requireOwned(user, requestId));
    }

    /**
     * **タスクを一覧から消す**（状態を取消にする。行は監査のため残す）。
     *
     * <p>【取消】は「お金を使ったあとは取り消せない」という規則だが、こちらは
     * **利用者が自分のタスクカードを片付ける**ための入口なので、どの状態でも消せる
     * （生成中のものを消したときは、走っている働き手の書き込みが版数で弾かれ、
     * 取消のままになる）。図形として保存済みのものは図形を消す話なので受け付けない。</p>
     */
    @Override
    @Transactional
    public GeometryAiModels.RequestStatus discard(UserPrincipal user, long requestId, Integer version) {
        GeometryAiRequestEntity entity = requireOwned(user, requestId);
        if (GeometryAiModels.STATUS_REGISTERED.equals(entity.getStatusCode())
                || entity.getFigureId() != null) {
            throw new ConflictException("この AI 生図は図形として保存済みです。"
                    + "図形一覧から削除してください。");
        }
        if (GeometryAiModels.STATUS_CANCELLED.equals(entity.getStatusCode())) {
            // 既に消えている（別の端末で消した等）。画面は一覧を取り直せばよい
            throw new ConflictException("この AI 生図は既に一覧から消えています。");
        }
        if (requestMapper.updateDiscarded(requestId, user.accountId(), GeometryAiModels.SOURCE_BATCH,
                requireVersion(entity, version)) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        log.info("geometry ai request discarded. requestId={} accountId={} status={}",
                requestId, user.accountId(), entity.getStatusCode());
        return toStatus(requireOwned(user, requestId));
    }

    // ------------------------------------------------------------------ 確定

    @Override
    @Transactional
    public GeometryAiModels.ConfirmResult confirm(UserPrincipal user, long requestId,
                                                  GeometryAiModels.ConfirmRequest request) {
        GeometryAiRequestEntity entity = requireOwned(user, requestId);
        if (GeometryAiModels.STATUS_REGISTERED.equals(entity.getStatusCode())) {
            throw new ConflictException("この AI 生図は既に図形として保存されています。");
        }
        if (!GeometryAiModels.STATUS_READY.equals(entity.getStatusCode())) {
            throw new ConflictException("この AI 生図はまだ確認できる状態ではありません（"
                    + statusLabel(entity.getStatusCode()) + "）。");
        }

        // 保存は既存の図形保存をそのまま使う（採番・タグ整形・表示順・検証を二重実装しない）。
        // 登録元コードだけ 'AI' にする（GEO_図形情報 に列は足さない。設計 §3.3）。
        GeometryModels.FigureSaveRequest save = new GeometryModels.FigureSaveRequest(
                request.title(),
                request.figureType() == null || request.figureType().isBlank()
                        ? entity.getFigureType() : request.figureType(),
                request.memo(), request.tags(), request.construction(), request.thumbnail(), null);
        GeometryModels.FigureMutationResult created =
                geometryService.create(user, save, GeometryAiModels.SOURCE_AI);

        if (requestMapper.updateRegistered(requestId, created.figure().figureId(), user.accountId(),
                GeometryAiModels.SOURCE_AI, requireVersion(entity, request.version())) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        log.info("geometry ai request registered. requestId={} figureId={} figureNo={}",
                requestId, created.figure().figureId(), created.figure().figureNo());
        return new GeometryAiModels.ConfirmResult(toRow(requireOwned(user, requestId)), created.figure(),
                "AI 生図から図形を保存しました。（" + created.figure().figureNo() + "）");
    }

    // ------------------------------------------------------------------ タスク一覧

    /**
     * 図形管理の一覧に出す AI 生図のタスク（自分の分だけ・新しい順）。
     *
     * <p>**図形として保存済（REGISTERED）は返さない**。保存できたものは図形一覧のカードとして出る
     * ので、同じものが 2 つ並ばない（利用者の指示）。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public GeometryAiModels.TaskListResult tasks(UserPrincipal user, Integer limit) {
        int safeLimit = limit == null || limit <= 0
                ? GeometryAiModels.DEFAULT_TASK_LIMIT
                : Math.min(limit, GeometryAiModels.MAX_TASK_LIMIT);
        List<GeometryAiRequestEntity> rows = requestMapper.findTasks(user.accountId(), safeLimit);
        List<GeometryAiModels.TaskRow> items = new ArrayList<>(rows.size());
        for (GeometryAiRequestEntity entity : rows) {
            GeometryAiModels.Proposal proposal = proposalOf(entity.getProposalJson());
            items.add(new GeometryAiModels.TaskRow(
                    entity.getRequestId(), entity.getRequestNo(),
                    entity.getStatusCode(), statusLabel(entity.getStatusCode()),
                    GeometryAiModels.cardStatusOf(entity.getStatusCode()),
                    cardStatusLabel(entity.getStatusCode()),
                    entity.getMode(), modeLabel(entity.getMode()),
                    valueOr(entity.getRequestedOutputType(), "AUTO"), entity.getResolvedOutputType(),
                    proposal == null ? null : proposal.title(),
                    proposal == null ? null : proposal.memo(),
                    questionsOf(entity.getQuestionsJson()).size(),
                    entity.getFigureId(),
                    entity.getFailedStage(), entity.getErrorCode(), entity.getErrorMessage(),
                    notBlank(entity.getCroppedName()),
                    entity.getRetryCount() == null ? 0 : entity.getRetryCount(),
                    entity.getVersion() == null ? 1 : entity.getVersion(),
                    iso(entity.getCreatedAt()), iso(entity.getUpdatedAt())));
        }
        return new GeometryAiModels.TaskListResult(items, items.size(), safeLimit);
    }

    // ------------------------------------------------------------------ 助手

    /**
     * AI 画図助手への**依頼を作る**（AI は呼ばない）。
     *
     * <p>AI の実行は admin-api のバッチ batC52。`生成状態=PENDING` の行を作り、画面へ
     * `runPath`（薄い入口）を返す。画面はその URL を 1 回だけ叩き、`getAssist` を
     * 短い間隔でポーリングする（送信 → ポーリングの契約）。</p>
     */
    @Override
    @Transactional
    public GeometryAiModels.AssistStatus assist(UserPrincipal user, GeometryAiModels.AssistRequest request) {
        GeometryAiSettings.Snapshot snapshot = settings.load();
        requireEnabled(snapshot);
        if (!settings.assistEnabled(snapshot)) {
            throw new ConflictException("AI 画図助手は現在ご利用いただけません（システム設定で無効です）。");
        }
        int limit = settings.assistDailyLimit(snapshot);
        long used = assistMapper.countTodayByAccount(user.accountId());
        if (limit > 0 && used >= limit) {
            throw new ConflictException("本日の AI 画図助手は上限（" + limit + " 回）に達しました。"
                    + "明日またお試しください。");
        }

        // 反映方法は**追加だけ**（利用者の指示）。作り直したいときは利用者が【全消去】してから指示する。
        // 古い画面（キャッシュされた bundle）が REPLACE を送ってきても、新しい依頼は APPEND で記録する
        // （既にある履歴の値はそのまま＝表示・互換のために残す）。
        String mode = ASSIST_MODE_APPEND;
        GeometryAiAssistEntity entity = new GeometryAiAssistEntity();
        entity.setFigureId(request.figureId());
        entity.setInstruction(trimToNull(request.instruction(), GeometryAiModels.INSTRUCTION_MAX));
        entity.setBeforeXml(limit(request.construction(), BEFORE_XML_LIMIT));
        // 画面が作ったオブジェクト一覧（AI が円の中心・半径などを推測しなくて済む）
        entity.setObjectsSummary(limit(request.objects(), OBJECTS_SUMMARY_LIMIT));
        // 前の案が失敗した内容（自動修正のときに AI へ渡す）
        entity.setFailureDetail(limit(request.failure(), FAILURE_DETAIL_LIMIT));
        entity.setApplyKind("SUGGESTED");
        entity.setMode(mode);
        entity.setStatus("PENDING");
        entity.setRetryCount(0);
        entity.setCreatedBy(user.accountId());
        entity.setSourceCode("APP");
        assistMapper.insert(entity);

        log.info("geometry ai assist requested. assistId={} accountId={} mode={}",
                entity.getAssistId(), user.accountId(), mode);
        return new GeometryAiModels.AssistStatus(entity.getAssistId(), "PENDING", assistStatusLabel("PENDING"),
                GeometryAiModels.assistRunPath(entity.getAssistId()));
    }

    @Override
    @Transactional(readOnly = true)
    public GeometryAiModels.AssistDetail getAssist(UserPrincipal user, long assistId) {
        GeometryAiAssistEntity entity = requireOwnedAssist(user, assistId);
        return new GeometryAiModels.AssistDetail(entity.getAssistId(), entity.getStatus(),
                assistStatusLabel(entity.getStatus()), commandsOf(entity.getCommands()), entity.getDescription(),
                entity.getErrorCode(), entity.getErrorMessage(), entity.getCommandCount(),
                entity.getBeforeXml());
    }

    /**
     * 図形ごとの指示履歴（古い順。自分が作った行だけ）。
     *
     * <p>会話ログを端末をまたいで見せるための入口。`指示前XML` は一覧では返さない
     * （1 行が大きくなり得るため。必要になったら `getAssist` で 1 件だけ取る）。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public GeometryAiModels.AssistHistoryResult assistHistory(UserPrincipal user, Long figureId, Integer limit) {
        if (figureId == null) {
            throw new ValidationException("図形を指定してください。");
        }
        int safeLimit = limit == null || limit <= 0
                ? ASSIST_HISTORY_DEFAULT_LIMIT
                : Math.min(limit, ASSIST_HISTORY_MAX_LIMIT);
        List<GeometryAiAssistEntity> rows = assistMapper.findByFigure(figureId, user.accountId(), safeLimit);
        List<GeometryAiModels.AssistHistoryItem> items = new ArrayList<>(rows.size());
        for (GeometryAiAssistEntity entity : rows) {
            items.add(new GeometryAiModels.AssistHistoryItem(
                    entity.getAssistId(),
                    entity.getInstruction(),
                    entity.getMode(),
                    entity.getStatus(),
                    assistStatusLabel(entity.getStatus()),
                    entity.getApplyKind(),
                    commandsOf(entity.getCommands()),
                    entity.getDescription(),
                    entity.getErrorCode(),
                    entity.getErrorMessage(),
                    entity.getCommandCount(),
                    Boolean.TRUE.equals(entity.getBeforeXmlAvailable()),
                    iso(entity.getCreatedAt()),
                    iso(entity.getUpdatedAt())));
        }
        // 画面は古い順に積む（クエリは新しい順に LIMIT している）
        Collections.reverse(items);
        return new GeometryAiModels.AssistHistoryResult(items, items.size(), safeLimit);
    }

    @Override
    @Transactional
    public GeometryAiModels.AssistResult markAssistApplied(UserPrincipal user, long assistId, String mode) {
        GeometryAiAssistEntity entity = requireOwnedAssist(user, assistId);
        requireReady(entity);
        String normalized = normalizeAssistMode(mode);
        if (assistMapper.updateApplied(assistId, normalized, user.accountId()) == 0) {
            throw new NotFoundException("AI 画図助手の記録が見つかりません。");
        }
        log.info("geometry ai assist applied. assistId={} accountId={} mode={}", assistId, user.accountId(), normalized);
        return new GeometryAiModels.AssistResult(assistId, commandsOf(entity.getCommands()),
                entity.getDescription(), entity.getAiCallId());
    }

    @Override
    @Transactional
    public GeometryAiModels.AssistResult markAssistRejected(UserPrincipal user, long assistId, String reason) {
        GeometryAiAssistEntity entity = requireOwnedAssist(user, assistId);
        requireReady(entity);
        String kept = limit(trimToNull(reason, ASSIST_REASON_MAX), ASSIST_REASON_MAX);
        if (assistMapper.updateRejected(assistId, user.accountId(), kept) == 0) {
            throw new NotFoundException("AI 画図助手の記録が見つかりません。");
        }
        log.info("geometry ai assist rejected. assistId={} accountId={} reason={}",
                assistId, user.accountId(), kept == null ? "-" : "あり");
        return new GeometryAiModels.AssistResult(assistId, commandsOf(entity.getCommands()),
                entity.getDescription(), entity.getAiCallId());
    }

    // -------------------------------------------------------------------- 内部

    /** 生成状態 = READY の行だけ反映・破棄できる（未生成のものは 409）。 */
    private static void requireReady(GeometryAiAssistEntity entity) {
        if (!"READY".equals(entity.getStatus())) {
            throw new ConflictException("AI の変更案がまだ生成されていません（"
                    + assistStatusLabel(entity.getStatus()) + "）。もうしばらくお待ちください。");
        }
    }

    private static String assistStatusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "依頼受付（AI 実行待ち）";
            case "GENERATING" -> "AI が生成中";
            case "READY" -> "できました";
            case "FAILED" -> "失敗";
            default -> status == null ? "" : status;
        };
    }

    private void requireEnabled(GeometryAiSettings.Snapshot snapshot) {
        if (!settings.enabled(snapshot)) {
            throw new ConflictException("AI 生図は現在ご利用いただけません（システム設定で無効です）。");
        }
    }

    private void requireDailyLimit(UserPrincipal user, GeometryAiSettings.Snapshot snapshot) {
        int limit = settings.dailyLimit(snapshot);
        if (limit <= 0) {
            return;
        }
        if (requestMapper.countTodayByAccount(user.accountId()) >= limit) {
            throw new ConflictException("本日の AI 生図は上限（" + limit + " 回）に達しました。"
                    + "明日またお試しください。");
        }
    }

    /** 自分の行だけを返す（他人の要求は「見つからない」にする。存在も漏らさない）。 */
    private GeometryAiRequestEntity requireOwned(UserPrincipal user, long requestId) {
        GeometryAiRequestEntity entity = requestMapper.findById(requestId);
        if (entity == null || entity.getCreatedBy() == null
                || !entity.getCreatedBy().equals(user.accountId())) {
            throw new NotFoundException("AI 生図の要求が見つかりません。");
        }
        return entity;
    }

    private GeometryAiAssistEntity requireOwnedAssist(UserPrincipal user, long assistId) {
        GeometryAiAssistEntity entity = assistMapper.findById(assistId);
        if (entity == null || entity.getCreatedBy() == null
                || !entity.getCreatedBy().equals(user.accountId())) {
            throw new NotFoundException("AI 画図助手の記録が見つかりません。");
        }
        return entity;
    }

    private static int requireVersion(GeometryAiRequestEntity entity, Integer version) {
        if (version == null) {
            throw new ValidationException("バージョンを指定してください。");
        }
        if (entity.getVersion() != null && !entity.getVersion().equals(version)) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        return version;
    }

    private static boolean isRetryable(GeometryAiRequestEntity entity) {
        String status = entity.getStatusCode();
        if (GeometryAiModels.STATUS_READY.equals(status)) {
            return true;
        }
        return GeometryAiModels.STATUS_FAILED.equals(status)
                && (GeometryAiModels.STAGE_GENERATE.equals(entity.getFailedStage())
                    || GeometryAiModels.STAGE_VALIDATE.equals(entity.getFailedStage()));
    }

    /**
     * 条件を直して送り直せる状態か。
     *
     * <p>「追加入力待ち」（AI が質問を返した）と「失敗」が対象。**処理中のもの・完了したものは
     * 対象外**（二重に実行させない・できた作図を消させない）。</p>
     */
    private static boolean isResubmittable(GeometryAiRequestEntity entity) {
        String status = entity.getStatusCode();
        return GeometryAiModels.STATUS_NEEDS_INPUT.equals(status)
                || GeometryAiModels.STATUS_FAILED.equals(status);
    }

    /** 切り抜き範囲（0..1・最小 5%・はみ出し防止）。画面の `clampCrop` と同じ結果にする。 */
    private static BigDecimal[] normalizeCrop(GeometryAiModels.CropInput crop, String defaultCrop) {
        double x = 0;
        double y = 0;
        double w = 1;
        double h = 1;
        if (crop != null) {
            x = value(crop.x(), 0);
            y = value(crop.y(), 0);
            w = value(crop.w(), 1);
            h = value(crop.h(), 1);
        } else if ("center".equals(defaultCrop)) {
            // 設定「既定の切り抜き」= center（画面の【中央を優先】と同じ 70%）
            x = 0.15;
            y = 0.15;
            w = 0.7;
            h = 0.7;
        }
        w = clamp(w, GeometryAiModels.CROP_MIN, 1);
        h = clamp(h, GeometryAiModels.CROP_MIN, 1);
        x = clamp(x, 0, 1 - w);
        y = clamp(y, 0, 1 - h);
        return new BigDecimal[]{decimal(x), decimal(y), decimal(w), decimal(h)};
    }

    /**
     * 歴史的な列（利用者区分）の値。
     *
     * <p>モード（A〜D）があるときは**結果種別から読み替える**（画面は分類を選ばないため）。
     * モードが無い（古い画面・キャッシュされた bundle）ときだけ、送られてきた分類を使う。</p>
     */
    private static String legacyKindOf(String kind, String mode, String resultType, String defaultKind) {
        if (mode == null) {
            return normalizeKind(kind, defaultKind);
        }
        return switch (resultType == null ? "AUTO" : resultType) {
            case "GEOMETRY" -> GeometryAiModels.KIND_FIGURE;
            case "GRAPH" -> GeometryAiModels.KIND_FUNCTION;
            case "MIXED" -> GeometryAiModels.KIND_MIXED;
            // 自動判定のときは分類を決めない（NULL = 未指定。AI の判定で決まる）
            default -> null;
        };
    }

    /** 画面の kind（小文字）→ DB の 利用者区分（大文字）。 */
    private static String normalizeKind(String kind, String defaultKind) {
        String value = kind == null || kind.isBlank() ? defaultKind : kind.trim().toUpperCase(Locale.ROOT);
        if (!GeometryAiModels.USER_KINDS.contains(value)) {
            throw new ValidationException("分類は figure / function / mixed のいずれかを指定してください。");
        }
        return value;
    }

    private static String normalizeSubKind(String subKind) {
        if (subKind == null || subKind.isBlank()) {
            return null;
        }
        String value = subKind.trim().toUpperCase(Locale.ROOT);
        if (!GeometryAiModels.USER_SUB_KINDS.contains(value)) {
            throw new ValidationException("図形の種類は triangle / circle / quad / other のいずれかを指定してください。");
        }
        return value;
    }

    /** 作図種別（GeoGebra の appName を決める最終値。結果種別 → 無ければ利用者区分）。 */
    private static String figureTypeOf(String resultType, String kind) {
        String normalized = resultType == null ? "AUTO" : resultType;
        switch (normalized) {
            case "GRAPH", "MIXED" -> {
                // 混在は関数も図形も描ける Graphing にする
                return GeometryModels.TYPE_FUNCTION;
            }
            case "GEOMETRY" -> {
                return GeometryModels.TYPE_GEOMETRY;
            }
            default -> {
                // AUTO: 歴史的な分類（利用者区分）で決める。決まらなければ幾何図形
                return GeometryAiModels.KIND_FUNCTION.equals(kind)
                        ? GeometryModels.TYPE_FUNCTION : GeometryModels.TYPE_GEOMETRY;
            }
        }
    }

    /** 画面のモード（A〜D。小文字も受ける）。無い（歴史的な画面）ときは null のままにする。 */
    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        String value = mode.trim().toUpperCase(Locale.ROOT);
        if (!GeometryAiModels.MODES.contains(value)) {
            throw new ValidationException("作図方法は A / B / C / D のいずれかを指定してください。");
        }
        return value;
    }

    /**
     * 作成する図の種類（AUTO / GEOMETRY / GRAPH / MIXED）。
     *
     * <p>B（数式からグラフ）は画面で選ばせないので**サーバーで GRAPH に固定**する。
     * 指定が無い（歴史的な画面）ときは、当時の分類から読み替える。</p>
     */
    private static String normalizeResultType(String resultType, String mode, String legacyKind) {
        if ("B".equals(mode)) {
            return "GRAPH";
        }
        if (resultType == null || resultType.isBlank()) {
            if (legacyKind == null || legacyKind.isBlank()) {
                return "AUTO";
            }
            return switch (legacyKind.trim().toUpperCase(Locale.ROOT)) {
                case GeometryAiModels.KIND_FIGURE -> "GEOMETRY";
                case GeometryAiModels.KIND_FUNCTION -> "GRAPH";
                case GeometryAiModels.KIND_MIXED -> "MIXED";
                default -> "AUTO";
            };
        }
        String value = resultType.trim().toUpperCase(Locale.ROOT);
        if (!GeometryAiModels.OUTPUT_TYPES.contains(value)) {
            throw new ValidationException("作成する図の種類は AUTO / GEOMETRY / GRAPH / MIXED のいずれかを指定してください。");
        }
        return value;
    }

    private static String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!GeometryAiModels.STATUSES.contains(value)) {
            throw new ValidationException("状態の指定が正しくありません: " + status);
        }
        return value;
    }

    /**
     * 記録する反映方法をそろえる（`markAssistApplied` 用）。
     *
     * <p><b>新しい依頼は常に {@link #ASSIST_MODE_APPEND}</b>（作り直しは画面から無くなった）。
     * ここへ来るのは「反映した」記録だけで、古い画面・古いステップとの互換のために
     * REPLACE も受け取れるようにしてある（値そのものは記録用で、作図を消すかどうかは画面が決める）。</p>
     */
    private static String normalizeAssistMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return ASSIST_MODE_APPEND;
        }
        String value = mode.trim().toUpperCase(Locale.ROOT);
        if ("ADD".equals(value) || ASSIST_MODE_APPEND.equals(value)) {
            return ASSIST_MODE_APPEND;
        }
        if ("REPLACE".equals(value) || "WHOLE".equals(value)) {
            return "REPLACE";
        }
        throw new ValidationException("反映方法は ADD（追加）/ REPLACE（全体置き換え）のいずれかを指定してください。");
    }

    private String nextRequestNo() {
        for (int attempt = 0; attempt < 20; attempt += 1) {
            String candidate = "AIG" + LocalDateTime.now().format(NO_FORMAT) + (1000 + RANDOM.nextInt(9000));
            if (requestMapper.findByNo(candidate) == null) {
                return candidate;
            }
        }
        throw new IllegalStateException("AI 生図の要求番号を採番できませんでした。");
    }

    private static String statusLabel(String status) {
        return GeometryAiModels.STATUS_LABELS.getOrDefault(status, status == null ? "" : status);
    }

    private static GeometryAiModels.RequestStatus toStatus(GeometryAiRequestEntity entity) {
        return new GeometryAiModels.RequestStatus(entity.getRequestId(), entity.getRequestNo(),
                entity.getStatusCode(), statusLabel(entity.getStatusCode()),
                entity.getVersion() == null ? 1 : entity.getVersion(),
                // 画面が 1 回だけ呼ぶ入口（admin-api の薄い起動 API）。パスだけを返す
                "/api/admin/batch/geometry-ai/requests/" + entity.getRequestId() + "/run");
    }

    private static GeometryAiModels.RequestRow toRow(GeometryAiRequestEntity entity) {
        return new GeometryAiModels.RequestRow(entity.getRequestId(), entity.getRequestNo(),
                entity.getStatusCode(), statusLabel(entity.getStatusCode()),
                GeometryAiModels.cardStatusOf(entity.getStatusCode()),
                cardStatusLabel(entity.getStatusCode()),
                entity.getMode(), modeLabel(entity.getMode()),
                valueOr(entity.getRequestedOutputType(), "AUTO"), entity.getResolvedOutputType(),
                entity.getUserKind(), entity.getUserSubKind(), entity.getFigureType(), entity.getNote(),
                entity.getFailedStage(), entity.getErrorCode(), entity.getErrorMessage(), entity.getFigureId(),
                entity.getRetryCount() == null ? 0 : entity.getRetryCount(),
                iso(entity.getCreatedAt()), iso(entity.getUpdatedAt()));
    }

    /** 作図モードの日本語（画面の選択肢と同じ呼び方）。 */
    static String modeLabel(String mode) {
        return switch (mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT)) {
            case "A" -> "画像をもとに再現";
            case "B" -> "数式からグラフを作成";
            case "C" -> "文章の条件から作図";
            case "D" -> "文章と図を合わせて作図";
            default -> null;
        };
    }

    /** カードの状態の日本語。 */
    static String cardStatusLabel(String status) {
        return GeometryAiModels.CARD_STATUS_LABELS
                .getOrDefault(GeometryAiModels.cardStatusOf(status), "失敗");
    }

    private GeometryAiModels.RequestDetail toDetail(GeometryAiRequestEntity entity) {
        String status = entity.getStatusCode();
        boolean showCommands = GeometryAiModels.STATUS_READY.equals(status)
                || GeometryAiModels.STATUS_REGISTERED.equals(status)
                // 検証で落ちたときは「参考」として出す（利用者が作図画面で手直しできる。設計 §2.3）
                || (GeometryAiModels.STATUS_FAILED.equals(status)
                    && GeometryAiModels.STAGE_VALIDATE.equals(entity.getFailedStage()));
        List<String> commands = showCommands ? commandsOf(entity.getCommands()) : List.of();
        GeometryAiModels.ErrorInfo error = GeometryAiModels.STATUS_FAILED.equals(status)
                ? new GeometryAiModels.ErrorInfo(entity.getFailedStage(),
                        GeometryAiModels.STAGE_LABELS.getOrDefault(entity.getFailedStage(), ""),
                        entity.getErrorCode(), entity.getErrorMessage())
                : null;
        return new GeometryAiModels.RequestDetail(entity.getRequestId(), entity.getRequestNo(),
                status, statusLabel(status),
                GeometryAiModels.cardStatusOf(status), cardStatusLabel(status),
                entity.getMode(), modeLabel(entity.getMode()),
                valueOr(entity.getRequestedOutputType(), "AUTO"), entity.getResolvedOutputType(),
                GeometryAiSupplements.rows(entity.getSupplementsJson()),
                entity.getOutcome(), questionsOf(entity.getQuestionsJson()),
                entity.getUserKind(), entity.getUserSubKind(), entity.getAiKind(),
                entity.getFigureType(), entity.getNote(), commands, proposalOf(entity.getProposalJson()),
                error, entity.getValidationError(),
                new GeometryAiModels.ExecutionIds(entity.getPreprocessExecutionId(), entity.getAiExecutionId(),
                        entity.getValidateExecutionId()),
                entity.getFigureId(), entity.getRetryCount() == null ? 0 : entity.getRetryCount(),
                entity.getVersion() == null ? 1 : entity.getVersion(),
                notBlank(entity.getOriginalPath()), notBlank(entity.getCroppedPath()),
                isRetryable(entity),
                GeometryAiModels.STATUS_QUEUED.equals(status) || GeometryAiModels.STATUS_PREPROCESSED.equals(status),
                iso(entity.getCreatedAt()), iso(entity.getUpdatedAt()));
    }

    /** 確認質問（JSON 配列）を読む。読めないときは空（画面は質問が無いものとして扱う）。 */
    private static List<GeometryAiModels.Question> questionsOf(String questionsJson) {
        if (questionsJson == null || questionsJson.isBlank()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = new ObjectMapper().readTree(questionsJson);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<GeometryAiModels.Question> questions = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode node : root) {
                String question = node.path("質問").isTextual() ? node.path("質問").asText()
                        : (node.path("question").isTextual() ? node.path("question").asText() : null);
                if (question == null || question.isBlank()) {
                    continue;
                }
                String id = node.path("ID").isTextual() ? node.path("ID").asText()
                        : (node.path("id").isTextual() ? node.path("id").asText() : null);
                String answerKind = node.path("回答の形").isTextual() ? node.path("回答の形").asText()
                        : (node.path("answerKind").isTextual() ? node.path("answerKind").asText() : "TEXT");
                List<String> options = new ArrayList<>();
                com.fasterxml.jackson.databind.JsonNode optionNode = node.has("選択肢") ? node.path("選択肢")
                        : node.path("options");
                if (optionNode != null && optionNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode option : optionNode) {
                        if (option.isTextual() && !option.asText().isBlank()) {
                            options.add(option.asText().trim());
                        }
                    }
                }
                questions.add(new GeometryAiModels.Question(id, question.trim(), options, answerKind));
            }
            return questions;
        } catch (Exception cause) {
            log.warn("AI 生図の質問 JSON を読めませんでした。", cause);
            return List.of();
        }
    }

    /** 生成コマンド（1 行 1 コマンドの TEXT）を行に分ける。 */
    private static List<String> commandsOf(String commands) {
        if (commands == null || commands.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : commands.split("\r?\n")) {
            String value = line.trim();
            if (!value.isEmpty()) {
                lines.add(value);
            }
        }
        return lines;
    }

    /** 提案 JSON（AI が返した図形名・タグ・メモ・認識テキスト）を読む。 */
    private GeometryAiModels.Proposal proposalOf(String proposalJson) {
        if (proposalJson == null || proposalJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(proposalJson);
            String title = firstText(root, "図形名", "title");
            String memo = firstText(root, "メモ", "memo");
            String recognized = firstText(root, "認識", "認識テキスト", "recognized");
            List<String> tags = new ArrayList<>();
            for (String field : List.of("タグ", "tags")) {
                JsonNode node = root.path(field);
                if (!node.isArray()) {
                    continue;
                }
                for (JsonNode tag : node) {
                    if (tag.isTextual() && !tag.asText().isBlank() && tags.size() < PROPOSAL_TAG_MAX) {
                        tags.add(tag.asText().trim());
                    }
                }
            }
            if (title == null && memo == null && recognized == null && tags.isEmpty()) {
                return null;
            }
            return new GeometryAiModels.Proposal(title, tags, memo, recognized);
        } catch (Exception cause) {
            log.warn("AI 生図の提案 JSON を読めませんでした。", cause);
            return null;
        }
    }

    private static String firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode node = root.path(field);
            if (node.isTextual() && !node.asText().isBlank()) {
                return node.asText().trim();
            }
        }
        return null;
    }

    private static double value(Double value, double fallback) {
        return value == null || value.isNaN() ? fallback : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    /** NUMERIC(6,5) に合わせる（0..1 なので 5 桁で足りる）。 */
    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String iso(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toString();
    }
}
