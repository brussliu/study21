package com.study21.user.geometry;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 生図の業務ルール（作成・状態遷移・確定・助手）。
 *
 * 設計 `tmp/geometry-ai-design.md` の §4.1 / §6 / §9.1 を固定する:
 * ・画面の語彙（crop / kind / subKind / note）をそのまま受ける
 * ・切り抜きは 0..1・最小 5%・はみ出し防止（画面の clampCrop と同じ）
 * ・他人の要求は見えない（404。存在も漏らさない）
 * ・取消は QUEUED / PREPROCESSED のみ
 * ・確定は READY のみ。**登録元コード = 'AI'** で図形を作る
 */
class GeometryAiServiceImplTest {

    private static final long ACCOUNT_ID = 7L;
    private static final long REQUEST_ID = 31L;
    /** 受付時に固定した設定（要求行の設定スナップショット。秘密は入らない）。 */
    private static final String PINNED_CONFIG =
            "{\"version\":1,\"config\":{\"systemPromptCommon\":\"共通のルール。\","
            + "\"provider\":\"qwen:4\",\"mode\":\"A\"}}";

    private GeometryAiRequestMapper requestMapper;
    private GeometryAiAssistMapper assistMapper;
    private GeometryAiStorage storage;
    private GeometryAiSettings settings;
    private GeometryService geometryService;
    private GeometryAiServiceImpl service;

    @BeforeEach
    void setUp() {
        requestMapper = mock(GeometryAiRequestMapper.class);
        assistMapper = mock(GeometryAiAssistMapper.class);
        storage = mock(GeometryAiStorage.class);
        settings = mock(GeometryAiSettings.class);
        geometryService = mock(GeometryService.class);
        service = new GeometryAiServiceImpl(requestMapper, assistMapper, storage, settings, geometryService);
        when(settings.load()).thenReturn(snapshot(Map.of(
                GeometryAiSettings.KEY_ENABLED, "true",
                GeometryAiSettings.KEY_MAX_IMAGE_MB, "10",
                GeometryAiSettings.KEY_MAX_IMAGE_PIXELS, "1536",
                GeometryAiSettings.KEY_DEFAULT_CROP, "manual",
                GeometryAiSettings.KEY_DEFAULT_KIND, "figure",
                GeometryAiSettings.KEY_APPROVAL, "manual",
                GeometryAiSettings.KEY_DAILY_LIMIT, "20")));
        // 注: thenAnswer は「引数 null」でも落ちないようにする（Mockito は同じ引数マッチャで
        // 再スタブするとき、直前の答えを評価するため null が渡ってくる）
        when(settings.enabled(any())).thenAnswer(invocation -> {
            GeometryAiSettings.Snapshot argument = invocation.getArgument(0);
            return argument != null && "true".equals(argument.raw(GeometryAiSettings.KEY_ENABLED));
        });
        when(settings.maxImageMb(any())).thenReturn(10);
        when(settings.maxImagePixels(any())).thenReturn(1536);
        when(settings.defaultCrop(any())).thenReturn("manual");
        when(settings.defaultKind(any())).thenReturn("figure");
        when(settings.approval(any())).thenReturn("manual");
        when(settings.dailyLimit(any())).thenReturn(20);
        when(settings.missingKeys(any())).thenReturn(List.of());
        // 受付時に固定する「有効な設定」（要求行の設定スナップショット）
        when(settings.pinnedConfigJson(any(), anyInt())).thenReturn(PINNED_CONFIG);
        when(settings.nextRevision(any())).thenReturn(1);
    }

    private UserPrincipal student() {
        return new UserPrincipal(ACCOUNT_ID, "student@example.com", "試験 生徒", AccountType.STUDENT);
    }

    private static GeometryAiSettings.Snapshot snapshot(Map<String, String> values) {
        return new GeometryAiSettings.Snapshot(values);
    }

    private GeometryAiRequestEntity request(String status, int version) {
        GeometryAiRequestEntity entity = new GeometryAiRequestEntity();
        entity.setRequestId(REQUEST_ID);
        entity.setRequestNo("AIG202609141200001234");
        entity.setStatusCode(status);
        entity.setFigureType("geometry");
        entity.setUserKind("FIGURE");
        entity.setVersion(version);
        entity.setCreatedBy(ACCOUNT_ID);
        entity.setOriginalPath("geometry-ai/7/202609");
        entity.setOriginalName("abc.png");
        entity.setCommands("A = (0, 0)\nB = (5, 0)");
        entity.setCommandCount(2);
        return entity;
    }

    // ------------------------------------------------------------------ 作成

    @Test
    void createStoresTheScreenVocabularyAndClampsTheCrop() {
        when(storage.resolveToken(eq(ACCOUNT_ID), eq("token"))).thenReturn("geometry-ai/7/202609/abc.png");
        when(storage.load(anyString())).thenReturn(new GeometryAiStorage.StoredImage(
                "geometry-ai/7/202609", "abc.png", "image/png", 4096, 1000, 800, null));
        when(requestMapper.findByNo(anyString())).thenReturn(null);
        when(requestMapper.insert(any())).thenAnswer(invocation -> {
            GeometryAiRequestEntity entity = invocation.getArgument(0);
            entity.setRequestId(REQUEST_ID);
            return 1;
        });
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("QUEUED", 1));
        when(requestMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(0L);

        GeometryAiModels.RequestStatus result = service.create(student(), new GeometryAiModels.CreateRequest(
                "token",
                // はみ出す切り抜き（右下へ寄せる）
                new GeometryAiModels.CropInput(0.9, 0.9, 0.5, 0.5),
                // 作図方法 A（画像をもとに再現）＋ 種類は自動判定。グラフ用の項目は当てはまらないので捨てられる
                "A", "GEOMETRY",
                new GeometryAiModels.SupplementInput("MATH_FIRST", "ASK_FIRST", "AB = 5", "x: -5..5",
                        "訂正なし", "a = 2", "すべての実数", "x: -10..10", Boolean.TRUE,
                        null, null, null, null, null, Boolean.TRUE),
                "figure", "triangle", "  垂線も入れてください  "));

        assertThat(result.requestId()).isEqualTo(REQUEST_ID);
        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.runPath()).isEqualTo("/api/admin/batch/geometry-ai/requests/31/run");

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(requestMapper).insert(captor.capture());
        GeometryAiRequestEntity saved = captor.getValue();
        // 切り抜きは 0..1 に収まり、最小 5% を確保する（画面の clampCrop と同じ）
        assertThat(saved.getCropX()).isEqualByComparingTo(new BigDecimal("0.50000"));
        assertThat(saved.getCropY()).isEqualByComparingTo(new BigDecimal("0.50000"));
        assertThat(saved.getCropW()).isEqualByComparingTo(new BigDecimal("0.50000"));
        assertThat(saved.getCropH()).isEqualByComparingTo(new BigDecimal("0.50000"));
        // 画面の語彙をそのまま大文字にして保存する
        assertThat(saved.getUserKind()).isEqualTo("FIGURE");
        assertThat(saved.getUserSubKind()).isEqualTo("TRIANGLE");
        assertThat(saved.getFigureType()).isEqualTo("geometry");
        assertThat(saved.getNote()).isEqualTo("垂線も入れてください");
        assertThat(saved.getStatusCode()).isEqualTo("QUEUED");
        assertThat(saved.getCreatedBy()).isEqualTo(ACCOUNT_ID);
        // 作図方法と種類、当てはまる補充だけを保存する（幾何図形なので座標の範囲は入らない）
        assertThat(saved.getMode()).isEqualTo("A");
        assertThat(saved.getRequestedOutputType()).isEqualTo("GEOMETRY");
        assertThat(saved.getSupplementsJson())
                .contains("再現の重点").contains("情報が足りないとき").contains("既知の値")
                .doesNotContain("座標の範囲").doesNotContain("定義域");
        // 受付時に**有効な設定を固定**する（待ち行列に並んでいる間に設定を変えても、
        // この要求は受付時のプロンプト・モデル・上限で実行される）
        assertThat(saved.getSettingsSnapshotJson()).isEqualTo(PINNED_CONFIG);
    }

    @Test
    void createRejectsAnUnknownKind() {
        when(requestMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(0L);
        when(storage.resolveToken(anyLong(), anyString())).thenReturn("geometry-ai/7/202609/abc.png");
        when(storage.load(anyString())).thenReturn(new GeometryAiStorage.StoredImage(
                "geometry-ai/7/202609", "abc.png", "image/png", 4096, 1000, 800, null));

        // モードが無い（古い画面）ときだけ分類を使う。知らない分類は今までどおり拒否する
        assertThatThrownBy(() -> service.create(student(), new GeometryAiModels.CreateRequest(
                "token", null, null, null, null, "triangle", null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("figure / function / mixed");
    }

    @Test
    void createIsRejectedWhenTheDailyLimitIsReached() {
        when(requestMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(20L);

        assertThatThrownBy(() -> service.create(student(), new GeometryAiModels.CreateRequest(
                "token", null, "A", "AUTO", null, "figure", "triangle", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("上限（20 回）");
        verify(requestMapper, never()).insert(any());
    }

    @Test
    void createIsRejectedWhenTheFeatureIsDisabled() {
        when(settings.enabled(any())).thenReturn(false);

        assertThatThrownBy(() -> service.create(student(), new GeometryAiModels.CreateRequest(
                "token", null, "A", "AUTO", null, "figure", "triangle", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ご利用いただけません");
    }

    @Test
    void createFixesTheOutputTypeToGraphForModeB() {
        when(storage.resolveToken(eq(ACCOUNT_ID), eq("token"))).thenReturn("geometry-ai/7/202609/abc.png");
        when(storage.load(anyString())).thenReturn(new GeometryAiStorage.StoredImage(
                "geometry-ai/7/202609", "abc.png", "image/png", 4096, 1000, 800, null));
        when(requestMapper.findByNo(anyString())).thenReturn(null);
        when(requestMapper.insert(any())).thenAnswer(invocation -> {
            GeometryAiRequestEntity entity = invocation.getArgument(0);
            entity.setRequestId(REQUEST_ID);
            return 1;
        });
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("QUEUED", 1));
        when(requestMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(0L);

        // 画面は B で種類を選ばせない。サーバーでも AUTO を GRAPH に固定する
        service.create(student(), new GeometryAiModels.CreateRequest(
                "token", null, "B", "AUTO",
                new GeometryAiModels.SupplementInput(null, null, null, null, null, "a = 2", "x > 0", "x: -5..5",
                        Boolean.FALSE, null, null, null, null, null, null),
                null, null, null));

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(requestMapper).insert(captor.capture());
        GeometryAiRequestEntity saved = captor.getValue();
        assertThat(saved.getMode()).isEqualTo("B");
        assertThat(saved.getRequestedOutputType()).isEqualTo("GRAPH");
        // グラフなので Graph 用の作図タイプ（function）になる
        assertThat(saved.getFigureType()).isEqualTo("function");
        assertThat(saved.getSupplementsJson())
                .contains("パラメータの値").contains("定義域").contains("表示範囲")
                .doesNotContain("再現の重点");
    }

    @Test
    void createRejectsAnUnknownModeOrOutputType() {
        when(requestMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(0L);
        when(storage.resolveToken(anyLong(), anyString())).thenReturn("geometry-ai/7/202609/abc.png");
        when(storage.load(anyString())).thenReturn(new GeometryAiStorage.StoredImage(
                "geometry-ai/7/202609", "abc.png", "image/png", 4096, 1000, 800, null));

        assertThatThrownBy(() -> service.create(student(), new GeometryAiModels.CreateRequest(
                "token", null, "E", "AUTO", null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("A / B / C / D");
        assertThatThrownBy(() -> service.create(student(), new GeometryAiModels.CreateRequest(
                "token", null, "A", "CIRCLE", null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("AUTO / GEOMETRY / GRAPH / MIXED");
    }

    @Test
    void createKeepsLegacyRequestsWithoutMode() {
        when(storage.resolveToken(eq(ACCOUNT_ID), eq("token"))).thenReturn("geometry-ai/7/202609/abc.png");
        when(storage.load(anyString())).thenReturn(new GeometryAiStorage.StoredImage(
                "geometry-ai/7/202609", "abc.png", "image/png", 4096, 1000, 800, null));
        when(requestMapper.findByNo(anyString())).thenReturn(null);
        when(requestMapper.insert(any())).thenAnswer(invocation -> {
            GeometryAiRequestEntity entity = invocation.getArgument(0);
            entity.setRequestId(REQUEST_ID);
            return 1;
        });
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("QUEUED", 1));
        when(requestMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(0L);

        // 古い画面（モードを送らない）は今までどおり受けて、分類から結果種別を読み替える
        service.create(student(), new GeometryAiModels.CreateRequest(
                "token", null, null, null, null, "function", null, null));

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(requestMapper).insert(captor.capture());
        GeometryAiRequestEntity saved = captor.getValue();
        assertThat(saved.getMode()).isNull();
        assertThat(saved.getRequestedOutputType()).isEqualTo("GRAPH");
        assertThat(saved.getFigureType()).isEqualTo("function");
    }

    @Test
    void tasksExcludeRegisteredOnes() {
        when(requestMapper.findTasks(eq(ACCOUNT_ID), anyInt())).thenReturn(List.of(request("GENERATING", 1)));

        GeometryAiModels.TaskListResult result = service.tasks(student(), null);

        assertThat(result.items()).hasSize(1);
        GeometryAiModels.TaskRow row = result.items().get(0);
        assertThat(row.cardStatus()).isEqualTo("GENERATING");
        assertThat(row.cardStatusLabel()).isEqualTo("AI が生成中");
        // 段階そのものも「実際に何をしているか」が分かる文言にする
        assertThat(row.statusLabel()).isEqualTo("AI が生成中");
    }

    @Test
    void resubmitUpdatesTheOptionsAndReturnsToQueued() {
        GeometryAiRequestEntity existing = request("NEEDS_INPUT", 3);
        existing.setMode("A");
        existing.setRequestedOutputType("AUTO");
        when(requestMapper.findById(REQUEST_ID)).thenReturn(existing, request("QUEUED", 1));
        when(requestMapper.updateResubmitted(any())).thenReturn(1);
        when(requestMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(0L);

        GeometryAiModels.RequestStatus result = service.resubmit(student(), REQUEST_ID,
                new GeometryAiModels.ResubmitRequest(
                        new GeometryAiModels.CropInput(0.1, 0.1, 0.8, 0.8),
                        "C", "MIXED",
                        new GeometryAiModels.SupplementInput(null, "ALLOW_APPROXIMATE", null, null,
                                null, "a = 2", "x > 0", "x: -5..5", null, "COMPLETE_CONSTRUCTION",
                                null, null, null, null, Boolean.TRUE),
                        "垂線も入れて", 3));

        assertThat(result.status()).isEqualTo("QUEUED");
        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(requestMapper).updateResubmitted(captor.capture());
        GeometryAiRequestEntity saved = captor.getValue();
        // **同じ要求行**を使い回す（行を増やさない＝一覧に二重に並ばない・図形も 1 つ）
        assertThat(saved.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(saved.getStatusCode()).isEqualTo("QUEUED");
        assertThat(saved.getMode()).isEqualTo("C");
        assertThat(saved.getRequestedOutputType()).isEqualTo("MIXED");
        assertThat(saved.getFigureType()).isEqualTo("function");
        assertThat(saved.getNote()).isEqualTo("垂線も入れて");
        // 当てはまらない補充（A の項目）は入らない
        assertThat(saved.getSupplementsJson())
                .contains("作図の目標").contains("パラメータの値").contains("定義域")
                .doesNotContain("再現の重点");
        // 読み取る範囲も更新する
        assertThat(saved.getCropX()).isEqualByComparingTo(new BigDecimal("0.10000"));
        // 送り直しも、そのときの有効な設定で固定し直す（直した設定を拾わせる）
        assertThat(saved.getSettingsSnapshotJson()).isEqualTo(PINNED_CONFIG);
        // **AI をもう一度呼ぶので行を消す**（画像は使い回す）
        verify(requestMapper, never()).insert(any());
    }

    @Test
    void resubmitRejectsNonResubmittableStatus() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("GENERATING", 3));

        assertThatThrownBy(() -> service.resubmit(student(), REQUEST_ID,
                new GeometryAiModels.ResubmitRequest(null, "A", "AUTO", null, null, 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("送り直せません");
        verify(requestMapper, never()).updateResubmitted(any());
    }

    @Test
    void resubmitRequiresMode() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("FAILED", 3));
        when(requestMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.resubmit(student(), REQUEST_ID,
                new GeometryAiModels.ResubmitRequest(null, null, null, null, null, 1)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("作図方法");
    }

    // ------------------------------------------------------------------ 参照

    @Test
    void othersRequestsAreInvisible() {
        GeometryAiRequestEntity other = request("READY", 3);
        other.setCreatedBy(999L);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(other);

        assertThatThrownBy(() -> service.detail(student(), REQUEST_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("見つかりません");
    }

    @Test
    void detailReturnsCommandsOnlyWhenTheyAreReadyToUse() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("GENERATED", 3));
        assertThat(service.detail(student(), REQUEST_ID).commands()).isEmpty();

        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("READY", 4));
        GeometryAiModels.RequestDetail ready = service.detail(student(), REQUEST_ID);
        assertThat(ready.commands()).containsExactly("A = (0, 0)", "B = (5, 0)");
        assertThat(ready.retryable()).isTrue();
        assertThat(ready.cancellable()).isFalse();

        // 検証で落ちたときは参考として出す（作図画面で手直しできる）
        GeometryAiRequestEntity failed = request("FAILED", 5);
        failed.setFailedStage("VALIDATE");
        failed.setErrorCode("COMMAND_NOT_ALLOWED");
        failed.setErrorMessage("3 行目: 許可されていないコマンドです");
        when(requestMapper.findById(REQUEST_ID)).thenReturn(failed);
        GeometryAiModels.RequestDetail detail = service.detail(student(), REQUEST_ID);
        assertThat(detail.commands()).hasSize(2);
        assertThat(detail.error().stage()).isEqualTo("VALIDATE");
        assertThat(detail.error().stageLabel()).isEqualTo("生成結果の検証");
        assertThat(detail.retryable()).isTrue();
    }

    // ------------------------------------------------------------ 再試行・取消

    @Test
    void retryIsAllowedOnlyForGenerateAndValidateFailures() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("FAILED", 2));
        assertThatThrownBy(() -> service.retry(student(), REQUEST_ID, 2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("もう一度生成");

        GeometryAiRequestEntity failed = request("FAILED", 2);
        failed.setFailedStage("GENERATE");
        when(requestMapper.findById(REQUEST_ID)).thenReturn(failed);
        when(requestMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(1L);
        when(requestMapper.updateRetried(any())).thenReturn(1);
        GeometryAiRequestEntity retried = request("PREPROCESSED", 3);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(failed, retried);

        GeometryAiModels.RequestStatus status = service.retry(student(), REQUEST_ID, 2);
        assertThat(status.status()).isEqualTo("PREPROCESSED");
        // もう一度生成でも、そのときの有効な設定で固定し直す
        ArgumentCaptor<GeometryAiRequestEntity> retryCaptor =
                ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(requestMapper).updateRetried(retryCaptor.capture());
        assertThat(retryCaptor.getValue().getSettingsSnapshotJson()).isEqualTo(PINNED_CONFIG);
    }

    @Test
    @DisplayName("【削除】は一覧から消す（生成中でも消せる。記録はサーバーに残る）")
    void discardWorksInAnyStateExceptRegistered() {
        // 生成中でも消せる（走っている働き手の書き込みは版数で弾かれる）
        GeometryAiRequestEntity generating = request("GENERATING", 4);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(generating, request("CANCELLED", 5));
        when(requestMapper.updateDiscarded(REQUEST_ID, ACCOUNT_ID, "BATCH", 4)).thenReturn(1);

        GeometryAiModels.RequestStatus status = service.discard(student(), REQUEST_ID, 4);

        assertThat(status.status()).isEqualTo("CANCELLED");
        verify(requestMapper).updateDiscarded(REQUEST_ID, ACCOUNT_ID, "BATCH", 4);
    }

    @Test
    @DisplayName("【削除】は図形として保存済みのものを断る（図形一覧から消す話）")
    void discardRejectsRegisteredRequests() {
        GeometryAiRequestEntity registered = request("REGISTERED", 5);
        registered.setFigureId(900L);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(registered);

        assertThatThrownBy(() -> service.discard(student(), REQUEST_ID, 5))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("図形として保存済み");
        verify(requestMapper, never()).updateDiscarded(anyLong(), anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("【削除】は版数が違えば何も書かない（他の端末で先に消えた場合）")
    void discardRespectsTheVersion() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("READY", 3));

        assertThatThrownBy(() -> service.discard(student(), REQUEST_ID, 2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("他の操作で先に更新されました");
        verify(requestMapper, never()).updateDiscarded(anyLong(), anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("【削除】は既に消えているものを断る（画面は一覧を取り直す）")
    void discardRejectsAlreadyCancelled() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("CANCELLED", 6));

        assertThatThrownBy(() -> service.discard(student(), REQUEST_ID, 6))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("既に一覧から消えています");
    }

    @Test
    void cancelIsRejectedWhileGenerating() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("GENERATING", 4));

        assertThatThrownBy(() -> service.cancel(student(), REQUEST_ID, 4))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("生成中のため取り消せません");

        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("QUEUED", 1));
        when(requestMapper.updateCancelled(REQUEST_ID, ACCOUNT_ID, "APP", 1)).thenReturn(1);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("QUEUED", 1), request("CANCELLED", 2));
        assertThat(service.cancel(student(), REQUEST_ID, 1).status()).isEqualTo("CANCELLED");
    }

    // ------------------------------------------------------------------ 確定

    @Test
    void confirmCreatesTheFigureWithSourceCodeAi() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("READY", 6));
        GeometryModels.FigureRow figure = new GeometryModels.FigureRow(88L, "GEO202609141200001234",
                "数学", "geometry", "saved", "三角形ABC", null, List.of(), 1, "ACTIVE", true, 100, 1,
                "2026-09-14T12:00", "2026-09-14T12:00");
        when(geometryService.create(any(), any(), eq("AI")))
                .thenReturn(new GeometryModels.FigureMutationResult(figure, "図形を保存しました。"));
        when(requestMapper.updateRegistered(REQUEST_ID, 88L, ACCOUNT_ID, "AI", 6)).thenReturn(1);
        GeometryAiRequestEntity registered = request("REGISTERED", 7);
        registered.setFigureId(88L);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("READY", 6), registered);

        GeometryAiModels.ConfirmResult result = service.confirm(student(), REQUEST_ID,
                new GeometryAiModels.ConfirmRequest("三角形ABC", "geometry", "メモ", List.of("三角形"),
                        "<xml/>", "AAAA", 6));

        assertThat(result.figure().figureId()).isEqualTo(88L);
        assertThat(result.request().status()).isEqualTo("REGISTERED");
        assertThat(result.message()).contains("GEO202609141200001234");
        // 保存は既存の図形保存をそのまま使い、登録元コードだけ 'AI' にする
        ArgumentCaptor<GeometryModels.FigureSaveRequest> captor =
                ArgumentCaptor.forClass(GeometryModels.FigureSaveRequest.class);
        verify(geometryService).create(any(), captor.capture(), eq("AI"));
        assertThat(captor.getValue().construction()).isEqualTo("<xml/>");
        assertThat(captor.getValue().thumbnail()).isEqualTo("AAAA");
    }

    @Test
    void confirmIsRejectedWhenTheRequestIsNotReadyOrAlreadyRegistered() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("GENERATED", 3));
        assertThatThrownBy(() -> service.confirm(student(), REQUEST_ID,
                new GeometryAiModels.ConfirmRequest("三角形ABC", "geometry", null, List.of(), "<xml/>", null, 3)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("まだ確認できる状態ではありません");

        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("REGISTERED", 9));
        assertThatThrownBy(() -> service.confirm(student(), REQUEST_ID,
                new GeometryAiModels.ConfirmRequest("三角形ABC", "geometry", null, List.of(), "<xml/>", null, 9)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("既に図形として保存されています");
        verify(geometryService, never()).create(any(), any(), anyString());
    }

    // ------------------------------------------------------------------ 設定

    @Test
    void optionsExplainWhyTheFeatureIsUnavailable() {
        when(requestMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(3L);
        GeometryAiModels.OptionsResult available = service.options(student());
        assertThat(available.enabled()).isTrue();
        assertThat(available.notice()).isEmpty();
        assertThat(available.usedToday()).isEqualTo(3L);
        assertThat(available.dailyLimit()).isEqualTo(20);

        when(settings.enabled(any())).thenReturn(false);
        assertThat(service.options(student()).notice()).contains("無効になっています");

        // 「有効／無効」の設定項目は画面から外し、未設定＝有効にしたので、
        // 未設定を理由にする案内は出さない（明示的に false のときだけ無効）
        when(settings.missingKeys(any())).thenReturn(List.of(GeometryAiSettings.KEY_ENABLED));
        assertThat(service.options(student()).notice()).contains("無効になっています");
    }

    // ------------------------------------------------------------------ 助手

    @Test
    void assistCreatesPendingRowWithoutCallingAi() {
        when(settings.assistEnabled(any())).thenReturn(true);
        when(settings.assistDailyLimit(any())).thenReturn(50);
        when(assistMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(0L);
        when(assistMapper.insert(any())).thenAnswer(invocation -> {
            GeometryAiAssistEntity entity = invocation.getArgument(0);
            entity.setAssistId(12L);
            return 1;
        });

        // 古い画面（キャッシュされた bundle）が REPLACE を送ってきても、依頼は**追加**で記録する
        GeometryAiModels.AssistStatus result = service.assist(student(), new GeometryAiModels.AssistRequest(
                1L, "<construction><element label=\"A\"/></construction>", null, "垂線を引いて", "replace", null));

        assertThat(result.assistId()).isEqualTo(12L);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.runPath()).isEqualTo("/api/admin/batch/geometry-assist/12/run");

        ArgumentCaptor<GeometryAiAssistEntity> captor = ArgumentCaptor.forClass(GeometryAiAssistEntity.class);
        verify(assistMapper).insert(captor.capture());
        // AI を呼ばず、依頼（PENDING）の行だけを作る
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getApplyKind()).isEqualTo("SUGGESTED");
        // 反映方法は追加だけ（作図全体を作り直す依頼は作らない）
        assertThat(captor.getValue().getMode()).isEqualTo("APPEND");
        assertThat(captor.getValue().getCommands()).isNull();
        assertThat(captor.getValue().getDescription()).isNull();
    }

    @Test
    void assistIsRejectedWhenAssistDisabled() {
        when(settings.assistEnabled(any())).thenReturn(false);

        assertThatThrownBy(() -> service.assist(student(), new GeometryAiModels.AssistRequest(
                null, "", null, "垂線を引いて", "append", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ご利用いただけません");
        verify(assistMapper, never()).insert(any());
    }

    @Test
    void assistIsRejectedWhenDailyLimitReached() {
        when(settings.assistEnabled(any())).thenReturn(true);
        when(settings.assistDailyLimit(any())).thenReturn(50);
        when(assistMapper.countTodayByAccount(ACCOUNT_ID)).thenReturn(50L);

        assertThatThrownBy(() -> service.assist(student(), new GeometryAiModels.AssistRequest(
                null, "", null, "垂線を引いて", "append", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("上限（50 回）");
        verify(assistMapper, never()).insert(any());
    }

    @Test
    void getAssistReturnsOwnRowAndHidesOthers() {
        GeometryAiAssistEntity ready = new GeometryAiAssistEntity();
        ready.setAssistId(12L);
        ready.setCreatedBy(ACCOUNT_ID);
        ready.setStatus("READY");
        ready.setCommands("D = (0, 0)\nSegment(C, D)");
        ready.setCommandCount(2);
        ready.setDescription("垂線を引きます。");
        when(assistMapper.findById(12L)).thenReturn(ready);

        GeometryAiModels.AssistDetail detail = service.getAssist(student(), 12L);
        assertThat(detail.status()).isEqualTo("READY");
        assertThat(detail.commands()).containsExactly("D = (0, 0)", "Segment(C, D)");
        assertThat(detail.description()).isEqualTo("垂線を引きます。");
        assertThat(detail.commandCount()).isEqualTo(2);

        GeometryAiAssistEntity other = new GeometryAiAssistEntity();
        other.setAssistId(13L);
        other.setCreatedBy(999L);
        when(assistMapper.findById(13L)).thenReturn(other);
        assertThatThrownBy(() -> service.getAssist(student(), 13L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("見つかりません");
    }

    @Test
    void markAssistAppliedRejectsNonReady() {
        GeometryAiAssistEntity pending = new GeometryAiAssistEntity();
        pending.setAssistId(12L);
        pending.setCreatedBy(ACCOUNT_ID);
        pending.setStatus("PENDING");
        when(assistMapper.findById(12L)).thenReturn(pending);

        assertThatThrownBy(() -> service.markAssistApplied(student(), 12L, "append"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("まだ生成されていません");
        verify(assistMapper, never()).updateApplied(anyLong(), anyString(), anyLong());
    }

    // ---------------------------------------------------- 履歴（端末をまたぐ）

    private static GeometryAiAssistEntity assistRow(long assistId, long figureId, String status,
                                                    String applyKind, String instruction) {
        GeometryAiAssistEntity entity = new GeometryAiAssistEntity();
        entity.setAssistId(assistId);
        entity.setFigureId(figureId);
        entity.setCreatedBy(ACCOUNT_ID);
        entity.setStatus(status);
        entity.setApplyKind(applyKind);
        entity.setInstruction(instruction);
        entity.setMode("APPEND");
        entity.setCommands("D = (0, 0)\nSegment(C, D)");
        entity.setCommandCount(2);
        // 一覧クエリは XML の中身ではなく「持っているか」だけを返す
        entity.setBeforeXmlAvailable(true);
        return entity;
    }

    /** 履歴は**古い順**で返す（クエリは新しい順に LIMIT するので、サービスで並べ直す）。 */
    @Test
    void assistHistoryReturnsOwnRowsInChronologicalOrder() {
        when(assistMapper.findByFigure(5L, ACCOUNT_ID, 20)).thenReturn(List.of(
                assistRow(14L, 5L, "READY", "APPLIED", "垂線を引いて"),
                assistRow(12L, 5L, "READY", "APPLIED", "三角形を書いて")));

        GeometryAiModels.AssistHistoryResult result = service.assistHistory(student(), 5L, null);

        assertThat(result.items()).extracting(GeometryAiModels.AssistHistoryItem::assistId)
                .containsExactly(12L, 14L);
        assertThat(result.items()).extracting(GeometryAiModels.AssistHistoryItem::instruction)
                .containsExactly("三角形を書いて", "垂線を引いて");
        GeometryAiModels.AssistHistoryItem first = result.items().get(0);
        assertThat(first.commands()).containsExactly("D = (0, 0)", "Segment(C, D)");
        assertThat(first.applyKind()).isEqualTo("APPLIED");
        assertThat(first.statusLabel()).isEqualTo("できました");
        // 【戻す】で使う指示前XMLを持っているかだけを伝える（中身は一覧では返さない）
        assertThat(first.beforeXmlAvailable()).isTrue();
        assertThat(result.limit()).isEqualTo(20);
        assertThat(result.count()).isEqualTo(2);
        // 自分の行だけを取る（他人の履歴を混ぜない）
        verify(assistMapper).findByFigure(5L, ACCOUNT_ID, 20);
    }

    /** 図形が決まっていない（新規作図中）は履歴を引けない（400）。 */
    @Test
    void assistHistoryRequiresFigureId() {
        assertThatThrownBy(() -> service.assistHistory(student(), null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("図形を指定してください");
        verify(assistMapper, never()).findByFigure(anyLong(), anyLong(), anyInt());
    }

    /** 件数は既定 20・上限 50 に丸める（画面から大きく指定されても守る）。 */
    @Test
    void assistHistoryClampsLimit() {
        when(assistMapper.findByFigure(eq(5L), eq(ACCOUNT_ID), anyInt())).thenReturn(List.of());
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);

        assertThat(service.assistHistory(student(), 5L, null).limit()).isEqualTo(20);
        assertThat(service.assistHistory(student(), 5L, 0).limit()).isEqualTo(20);
        assertThat(service.assistHistory(student(), 5L, 999).limit()).isEqualTo(50);

        verify(assistMapper, org.mockito.Mockito.times(3)).findByFigure(eq(5L), eq(ACCOUNT_ID), captor.capture());
        assertThat(captor.getAllValues()).containsExactly(20, 20, 50);
    }

    /** 詳細は【戻す】の戻り先（指示前XML）を返す。 */
    @Test
    void getAssistReturnsBeforeXmlForRestore() {
        GeometryAiAssistEntity ready = assistRow(12L, 5L, "READY", "APPLIED", "垂線を引いて");
        ready.setBeforeXml("<construction><element type=\"point\"/></construction>");
        when(assistMapper.findById(12L)).thenReturn(ready);

        GeometryAiModels.AssistDetail detail = service.getAssist(student(), 12L);

        assertThat(detail.beforeXml()).contains("<element");
    }

    /** 反映できなかった理由は DB に残す（端末をまたいでも同じ理由が見える）。 */
    @Test
    void markAssistRejectedStoresReason() {
        GeometryAiAssistEntity ready = assistRow(12L, 5L, "READY", "SUGGESTED", "垂線を引いて");
        when(assistMapper.findById(12L)).thenReturn(ready);
        when(assistMapper.updateRejected(eq(12L), eq(ACCOUNT_ID), any())).thenReturn(1);

        service.markAssistRejected(student(), 12L, "AI のコマンドを実行できませんでした：線分(F, G)");

        verify(assistMapper).updateRejected(12L, ACCOUNT_ID, "AI のコマンドを実行できませんでした：線分(F, G)");
    }

    /** 理由なし（利用者の【破棄】）は今までどおり null で更新する（既存の理由を消さない）。 */
    @Test
    void markAssistRejectedWithoutReasonKeepsExistingMessage() {
        GeometryAiAssistEntity ready = assistRow(12L, 5L, "READY", "SUGGESTED", "垂線を引いて");
        when(assistMapper.findById(12L)).thenReturn(ready);
        when(assistMapper.updateRejected(eq(12L), eq(ACCOUNT_ID), any())).thenReturn(1);

        service.markAssistRejected(student(), 12L, null);

        verify(assistMapper).updateRejected(12L, ACCOUNT_ID, null);
    }

    /** 長すぎる理由は切る（コマンド列をそのまま入れない）。 */
    @Test
    void markAssistRejectedTrimsLongReason() {
        GeometryAiAssistEntity ready = assistRow(12L, 5L, "READY", "SUGGESTED", "垂線を引いて");
        when(assistMapper.findById(12L)).thenReturn(ready);
        when(assistMapper.updateRejected(eq(12L), eq(ACCOUNT_ID), any())).thenReturn(1);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        service.markAssistRejected(student(), 12L, "x".repeat(600));

        verify(assistMapper).updateRejected(eq(12L), eq(ACCOUNT_ID), captor.capture());
        assertThat(captor.getValue()).hasSize(500);
    }
}
