package com.study21.user.geometry;

import com.study21.user.account.AccountService;
import com.study21.user.account.AccountType;
import com.study21.user.account.RegisterRequest;
import com.study21.common.core.geometryai.AiFigureConfig;
import com.study21.common.core.geometryai.AiFigureSettingKeys;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する AI 生図の検証。
 *
 * ・要求行の insert / select（切り抜き範囲・状態・監査列）
 * ・部分索引が拾う「未処理」の形（状態コードで絞れる）
 * ・CHECK 制約（FAILED のときは失敗工程が必須）
 * ・楽観的ロック（版数が違えば更新しない）
 * ・確定で `GEO_図形情報` に **登録元コード='AI'** の図形ができること
 *
 * テストはロールバックするので DB は汚れない（既存の `GeometryRepositoryTest` と同じ作法）。
 *
 * 実行には DB のパスワードが要る（無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api test
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class GeometryAiRepositoryTest {

    @Autowired
    private GeometryAiService geometryAiService;

    @Autowired
    private GeometryAiRequestMapper requestMapper;

    @Autowired
    private GeometryAiAssistMapper assistMapper;

    @Autowired
    private GeometryService geometryService;

    @Autowired
    private GeometryMapper geometryMapper;

    @Autowired
    private AccountService accountService;

    @Autowired
    private GeometryAiSettings geometryAiSettings;

    /** 検証用のアカウントをこのテストの中で作る（実在のアカウントを汚さない）。 */
    private UserPrincipal createStudent() {
        String email = "e2e-geo-ai-" + System.nanoTime() + "@example.com";
        RegisterRequest request = new RegisterRequest();
        request.setParentEmail(email);
        request.setParentPassword("Parent1234");
        request.setSei("検証");
        request.setMei("保護者");
        request.setSeiKana("けんしょう");
        request.setMeiKana("ほごしゃ");
        request.setGrade("中学1年生");
        request.setStudentEmail("s-" + email);
        request.setStudentPassword("Student1234");
        request.setAgreed(true);
        long accountId = accountService.register(request).getStudentAccountId();
        return new UserPrincipal(accountId, "s-" + email, "検証 生徒", AccountType.STUDENT);
    }

    /** 要求行を 1 件作る（画像はファイルを持たずパスだけ。この検証では中身を使わない）。 */
    private GeometryAiRequestEntity insert(UserPrincipal user, String status) {
        return insert(user, status, null);
    }

    /** 作図モード・結果種別・補充など、insert が対応している列を仕込んで作りたいとき。 */
    private GeometryAiRequestEntity insert(UserPrincipal user, String status,
                                           java.util.function.Consumer<GeometryAiRequestEntity> seed) {
        GeometryAiRequestEntity entity = new GeometryAiRequestEntity();
        entity.setRequestNo("AIG-TEST-" + System.nanoTime());
        entity.setStatusCode(status);
        entity.setOriginalPath("geometry-ai/" + user.accountId() + "/202609");
        entity.setOriginalName("test.png");
        entity.setOriginalMime("image/png");
        entity.setOriginalSize(4096L);
        entity.setOriginalWidth(1000);
        entity.setOriginalHeight(800);
        entity.setCropX(new BigDecimal("0.10000"));
        entity.setCropY(new BigDecimal("0.20000"));
        entity.setCropW(new BigDecimal("0.70000"));
        entity.setCropH(new BigDecimal("0.70000"));
        entity.setUserKind("FIGURE");
        entity.setUserSubKind("TRIANGLE");
        entity.setFigureType("geometry");
        entity.setNote("検証用");
        entity.setCreatedBy(user.accountId());
        entity.setSourceCode("APP");
        if (seed != null) {
            seed.accept(entity);
        }
        requestMapper.insert(entity);
        // DB が入れた版数を読み直す（更新は楽観的ロックなので、手元の版数が違うと 0 行になる）
        return requestMapper.findById(entity.getRequestId());
    }

    @Test
    void requestRowRoundTripsWithCropAndStatus() {
        UserPrincipal student = createStudent();
        GeometryAiRequestEntity inserted = insert(student, "QUEUED");

        GeometryAiRequestEntity found = requestMapper.findById(inserted.getRequestId());
        assertThat(found).isNotNull();
        assertThat(found.getRequestNo()).isEqualTo(inserted.getRequestNo());
        assertThat(found.getStatusCode()).isEqualTo("QUEUED");
        assertThat(found.getCropX()).isEqualByComparingTo(new BigDecimal("0.10000"));
        assertThat(found.getCropW()).isEqualByComparingTo(new BigDecimal("0.70000"));
        assertThat(found.getUserKind()).isEqualTo("FIGURE");
        assertThat(found.getUserSubKind()).isEqualTo("TRIANGLE");
        assertThat(found.getVersion()).isEqualTo(1);
        assertThat(found.getRetryCount()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(requestMapper.findByNo(inserted.getRequestNo()).getRequestId())
                .isEqualTo(inserted.getRequestId());
    }

    @Test
    void dailyCountAndHistoryAreScopedToTheCreator() {
        UserPrincipal student = createStudent();
        long before = requestMapper.countTodayByAccount(student.accountId());
        insert(student, "QUEUED");
        assertThat(requestMapper.countTodayByAccount(student.accountId())).isEqualTo(before + 1);
        assertThat(requestMapper.countTodayByAccount(student.accountId() + 999L)).isZero();

        List<GeometryAiRequestEntity> rows = requestMapper.search(student.accountId(), null, 10, 0);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(row -> row.getCreatedBy().equals(student.accountId()));
        assertThat(requestMapper.count(student.accountId(), "QUEUED")).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void failedStatusRequiresAStage() {
        UserPrincipal student = createStudent();
        GeometryAiRequestEntity entity = new GeometryAiRequestEntity();
        entity.setRequestNo("AIG-TEST-BAD-" + System.nanoTime());
        entity.setStatusCode("FAILED");
        entity.setFigureType("geometry");
        entity.setCreatedBy(student.accountId());
        entity.setSourceCode("APP");

        // CK_GEO_AI生図_失敗工程必須（状態と原因の食い違いを防ぐ）
        assertThatThrownBy(() -> requestMapper.insert(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatesRespectTheOptimisticLock() {
        UserPrincipal student = createStudent();
        GeometryAiRequestEntity entity = insert(student, "QUEUED");

        // 版数が違えば何も書かない
        assertThat(requestMapper.updateCancelled(entity.getRequestId(), student.accountId(), "APP", 99)).isZero();
        assertThat(requestMapper.updateCancelled(entity.getRequestId(), student.accountId(), "APP", 1)).isEqualTo(1);
        assertThat(requestMapper.findById(entity.getRequestId()).getStatusCode()).isEqualTo("CANCELLED");
        assertThat(requestMapper.findById(entity.getRequestId()).getVersion()).isEqualTo(2);
    }

    @Test
    void confirmCreatesTheFigureWithSourceCodeAi() {
        UserPrincipal student = createStudent();
        GeometryAiRequestEntity entity = insert(student, "READY");
        entity.setCommands("A = (0, 0)\nB = (5, 0)\nPolygon(A, B, C)");
        entity.setCommandCount(3);

        GeometryAiModels.ConfirmResult result = geometryAiService.confirm(student, entity.getRequestId(),
                new GeometryAiModels.ConfirmRequest("検証の三角形", "geometry", "AI 生図から",
                        List.of("三角形"), "<construction/>", "AAAA", 1));

        assertThat(result.request().status()).isEqualTo("REGISTERED");
        assertThat(result.request().figureId()).isNotNull();
        assertThat(result.message()).contains("AI 生図から図形を保存しました");

        // GEO_図形情報 に列を足さず、登録元コード='AI' で來源を表す（設計 §3.3）
        GeometryEntity figure = geometryMapper.findById(result.figure().figureId());
        assertThat(figure.getSourceCode()).isEqualTo("AI");
        assertThat(figure.getTitle()).isEqualTo("検証の三角形");
        assertThat(figure.getStatusCode()).isEqualTo("ACTIVE");
        assertThat(figure.getConstruction()).isEqualTo("<construction/>");

        // 要求行は REGISTERED + 図形ID
        GeometryAiRequestEntity saved = requestMapper.findById(entity.getRequestId());
        assertThat(saved.getStatusCode()).isEqualTo("REGISTERED");
        assertThat(saved.getFigureId()).isEqualTo(result.figure().figureId());
        assertThat(saved.getVersion()).isEqualTo(2);

        // 2 回目の確定は 409（既に登録済み）
        assertThatThrownBy(() -> geometryAiService.confirm(student, entity.getRequestId(),
                new GeometryAiModels.ConfirmRequest("検証の三角形", "geometry", null, List.of(),
                        "<construction/>", null, 2)))
                .isInstanceOf(com.study21.common.core.exception.ConflictException.class);
    }

    @Test
    void othersRequestsAreInvisibleThroughTheService() {
        UserPrincipal owner = createStudent();
        UserPrincipal other = createStudent();
        GeometryAiRequestEntity entity = insert(owner, "READY");

        assertThatThrownBy(() -> geometryAiService.detail(other, entity.getRequestId()))
                .isInstanceOf(com.study21.common.core.exception.NotFoundException.class);
    }

    // -------------------------------------------------- 助手の履歴（端末をまたぐ）

    /** 検証用の図形を 1 つ作る（助手の履歴は図形に紐づくので実体が要る）。 */
    private long createFigure(UserPrincipal user) {
        return geometryService.create(user, new GeometryModels.FigureSaveRequest(
                "履歴の検証", "geometry", null, List.of(),
                "<construction><element type=\"point\"/></construction>", null, null)).figure().figureId();
    }

    /** 助手の指示を 1 件作る（画面と同じ列の使い方）。 */
    private GeometryAiAssistEntity insertAssist(UserPrincipal user, long figureId, String instruction) {
        return insertAssist(user, figureId, instruction, "PENDING", null);
    }

    /** 助手の指示を 1 件作る（生成状態とコマンドを指定する版）。 */
    private GeometryAiAssistEntity insertAssist(UserPrincipal user, long figureId, String instruction,
                                                String status, String commands) {
        GeometryAiAssistEntity entity = new GeometryAiAssistEntity();
        entity.setFigureId(figureId);
        entity.setInstruction(instruction);
        entity.setBeforeXml("<construction><element type=\"point\"/></construction>");
        entity.setApplyKind("SUGGESTED");
        entity.setMode("APPEND");
        entity.setStatus(status);
        entity.setCommands(commands);
        entity.setCommandCount(commands == null ? null : commands.split("\n").length);
        entity.setRetryCount(0);
        entity.setCreatedBy(user.accountId());
        entity.setSourceCode("APP");
        assistMapper.insert(entity);
        return entity;
    }

    /**
     * 履歴は**図形 + 自分の行**だけ（新しい順）で、件数の上限が効く。
     *
     * <p>画面はこれを古い順に並べて会話ログに出す（他の端末で開いても同じ履歴が見える）。</p>
     */
    @Test
    void assistHistoryIsScopedToTheFigureAndTheCreator() {
        UserPrincipal owner = createStudent();
        UserPrincipal other = createStudent();
        long figureId = createFigure(owner);
        long otherFigureId = createFigure(owner);

        GeometryAiAssistEntity first = insertAssist(owner, figureId, "1 つ目の指示");
        GeometryAiAssistEntity second = insertAssist(owner, figureId, "2 つ目の指示");
        insertAssist(owner, otherFigureId, "別の図形の指示");
        insertAssist(other, figureId, "他人の指示");

        List<GeometryAiAssistEntity> rows = assistMapper.findByFigure(figureId, owner.accountId(), 20);

        // 新しい順（クエリの契約）。サービスの履歴はこれを古い順に戻す
        assertThat(rows).extracting(GeometryAiAssistEntity::getAssistId)
                .containsExactly(second.getAssistId(), first.getAssistId());
        assertThat(rows).extracting(GeometryAiAssistEntity::getInstruction)
                .containsExactly("2 つ目の指示", "1 つ目の指示");
        assertThat(rows).allMatch(row -> row.getCreatedBy().equals(owner.accountId()));
        assertThat(rows).allMatch(row -> row.getFigureId().equals(figureId));

        // 件数の上限（新しい方から）
        assertThat(assistMapper.findByFigure(figureId, owner.accountId(), 1))
                .extracting(GeometryAiAssistEntity::getAssistId)
                .containsExactly(second.getAssistId());

        // 一覧では「指示前XML」を運ばない（重いので 1 件ずつ取る）。【戻す】に使えるかだけを返す
        assertThat(rows).allMatch(row -> row.getBeforeXml() == null);
        assertThat(rows).allMatch(row -> Boolean.TRUE.equals(row.getBeforeXmlAvailable()));
        assertThat(assistMapper.findById(first.getAssistId()).getBeforeXml()).contains("<element");

        // サーバー側で切られた（上限に達した）作図は【戻す】の戻り先にできない
        GeometryAiAssistEntity truncated = new GeometryAiAssistEntity();
        truncated.setFigureId(figureId);
        truncated.setInstruction("大きすぎる指示");
        truncated.setBeforeXml("x".repeat(2_000_000));
        truncated.setApplyKind("SUGGESTED");
        truncated.setMode("APPEND");
        truncated.setStatus("PENDING");
        truncated.setRetryCount(0);
        truncated.setCreatedBy(owner.accountId());
        truncated.setSourceCode("APP");
        assistMapper.insert(truncated);
        assertThat(assistMapper.findByFigure(figureId, owner.accountId(), 1).get(0).getBeforeXmlAvailable())
                .isFalse();
    }

    /** 履歴はサービス越しでも古い順に並ぶ（画面がそのまま積めるように）。 */
    @Test
    void assistHistoryThroughTheServiceIsChronological() {
        UserPrincipal owner = createStudent();
        long figureId = createFigure(owner);
        insertAssist(owner, figureId, "1 つ目の指示");
        insertAssist(owner, figureId, "2 つ目の指示");

        GeometryAiModels.AssistHistoryResult result = geometryAiService.assistHistory(owner, figureId, null);

        assertThat(result.items()).extracting(GeometryAiModels.AssistHistoryItem::instruction)
                .containsExactly("1 つ目の指示", "2 つ目の指示");
        assertThat(result.items().get(0).beforeXmlAvailable()).isTrue();
        // 図形を指定しないと引けない（新規作図中は履歴が無い）
        assertThatThrownBy(() -> geometryAiService.assistHistory(owner, null, null))
                .isInstanceOf(com.study21.common.core.exception.ValidationException.class);
    }

    /** 反映できなかった理由は実 DB に残る（他の端末でも同じ理由が見える）。 */
    @Test
    void assistRejectedReasonIsPersisted() {
        UserPrincipal owner = createStudent();
        long figureId = createFigure(owner);
        GeometryAiAssistEntity row = insertAssist(owner, figureId, "垂線を引いて", "READY", "D = (0, 0)\nSegment(C, D)");

        geometryAiService.markAssistRejected(owner, row.getAssistId(),
                "AI のコマンドを実行できませんでした：線分(F, G)");

        GeometryAiAssistEntity saved = assistMapper.findById(row.getAssistId());
        assertThat(saved.getApplyKind()).isEqualTo("REJECTED");
        assertThat(saved.getErrorMessage()).isEqualTo("AI のコマンドを実行できませんでした：線分(F, G)");
        // 端末をまたいだ履歴にも同じ理由が出る
        assertThat(geometryAiService.assistHistory(owner, figureId, null).items())
                .anyMatch(item -> "AI のコマンドを実行できませんでした：線分(F, G)".equals(item.errorMessage()));
    }

    // ------------------------------------------------------ 設定の固定（設計 §6）

    /**
     * 受付時に固定した設定（スナップショット）は要求行に残り、**そのまま読み返せる**。
     *
     * <p>ハッシュだけでは「どの文面で作ったか」が分からないので、プロンプトの本文を入れる。
     * 秘密（API Key・URL）は入れない。</p>
     */
    @Test
    void pinnedConfigSnapshotSurvivesTheJsonbRoundTrip() {
        UserPrincipal student = createStudent();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "受付時の共通ルール。\n2 行目。");
        values.put(AiFigureSettingKeys.systemPromptKey("C"), "受付時のモード C のルール。");
        values.put(AiFigureSettingKeys.taskTemplateKey("C"), "文章から作図してください。");
        values.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        values.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point,Segment");
        values.put(AiFigureSettingKeys.MAX_COMMANDS, "40");
        AiFigureConfig pinned = AiFigureConfig.resolve("C", "batC51-C", values, "2026-09-19T00:00:00");

        GeometryAiRequestEntity entity = insert(student, "QUEUED");
        entity.setMode("C");
        entity.setSettingsSnapshotJson(pinned.toSnapshotJson(null));
        assertThat(requestMapper.updateResubmitted(entity)).isEqualTo(1);

        GeometryAiRequestEntity found = requestMapper.findById(entity.getRequestId());
        AiFigureConfig restored = AiFigureConfig.fromSnapshotJson(found.getSettingsSnapshotJson())
                .orElseThrow();
        assertThat(restored).isEqualTo(pinned);
        assertThat(restored.systemPromptCommon()).isEqualTo("受付時の共通ルール。\n2 行目。");
        assertThat(restored.systemPromptMode()).isEqualTo("受付時のモード C のルール。");
        assertThat(restored.maxCommands()).isEqualTo(40);
        assertThat(found.getSettingsSnapshotJson()).doesNotContain("apiKey").doesNotContain("http");
    }

    /**
     * 追加入力待ちからの送り直しは、**同じ要求行**を使い回し、**元画像を失わない**。
     *
     * <p>利用者が直したのは「読み取る範囲・作図方法・種類・補充・補足」だけ。画像は
     * もうサーバーにあるので送り直させない。設定は**そのときの有効な設定で固定し直す**。</p>
     */
    @Test
    void resubmitKeepsTheOriginalImageAndRepinsTheConfig() {
        UserPrincipal student = createStudent();
        // 送り直す前の「利用者が選んだ条件」（AI が質問を返して待っている状態）
        GeometryAiRequestEntity entity = insert(student, "NEEDS_INPUT", row -> {
            row.setMode("A");
            row.setRequestedOutputType("AUTO");
            row.setSupplementsJson("{\"再現の重点\":\"数学的な関係を優先\"}");
        });

        GeometryAiRequestEntity before = requestMapper.findById(entity.getRequestId());
        assertThat(before.getStatusCode()).isEqualTo("NEEDS_INPUT");
        assertThat(before.getMode()).isEqualTo("A");
        String originalPath = before.getOriginalPath();
        String originalName = before.getOriginalName();
        String originalMime = before.getOriginalMime();
        Long originalSize = before.getOriginalSize();
        Integer originalWidth = before.getOriginalWidth();
        Integer originalHeight = before.getOriginalHeight();

        GeometryAiModels.RequestStatus status = geometryAiService.resubmit(student, entity.getRequestId(),
                new GeometryAiModels.ResubmitRequest(
                        new GeometryAiModels.CropInput(0.2, 0.2, 0.6, 0.6),
                        "C", "MIXED",
                        new GeometryAiModels.SupplementInput(null, "ALLOW_APPROXIMATE", null, null,
                                null, "a = 2", "x > 0", "x: -5..5", null, "COMPLETE_CONSTRUCTION",
                                "問題文の訂正あり", null, null, null, Boolean.TRUE),
                        "垂線も入れて", before.getVersion()));

        assertThat(status.status()).isEqualTo("QUEUED");
        GeometryAiRequestEntity after = requestMapper.findById(entity.getRequestId());

        // 元画像はそのまま（原图を失わない）
        assertThat(after.getOriginalPath()).isEqualTo(originalPath);
        assertThat(after.getOriginalName()).isEqualTo(originalName);
        assertThat(after.getOriginalMime()).isEqualTo(originalMime);
        assertThat(after.getOriginalSize()).isEqualTo(originalSize);
        assertThat(after.getOriginalWidth()).isEqualTo(originalWidth);
        assertThat(after.getOriginalHeight()).isEqualTo(originalHeight);

        // 利用者が直した条件と選択は反映される
        assertThat(after.getMode()).isEqualTo("C");
        assertThat(after.getRequestedOutputType()).isEqualTo("MIXED");
        assertThat(after.getNote()).isEqualTo("垂線も入れて");
        assertThat(after.getCropX()).isEqualByComparingTo(new BigDecimal("0.20000"));
        assertThat(GeometryAiSupplements.rows(after.getSupplementsJson()))
                .containsEntry(GeometryAiSupplements.LABEL_GOAL, "問題が求める作図を完成させる")
                .containsEntry(GeometryAiSupplements.LABEL_WHEN_INSUFFICIENT, "近似（明記する）で進めてよい")
                .containsEntry(GeometryAiSupplements.LABEL_PARAMETERS, "a = 2");

        // 古い AI の成果物は残さない（もう一度作るので、前の案を混ぜない）
        assertThat(after.getCommands()).isNull();
        assertThat(after.getProposalJson()).isNull();
        assertThat(after.getQuestionsJson()).isNull();
        assertThat(after.getRetryCount()).isEqualTo(1);

        // 設定は**そのときの有効な設定**で固定し直される（本文が入り、復元できる）
        AiFigureConfig repinned = AiFigureConfig.fromSnapshotJson(after.getSettingsSnapshotJson())
                .orElseThrow();
        assertThat(repinned.mode()).isEqualTo("C");
        assertThat(repinned.systemPromptCommon()).isNotBlank();
        assertThat(repinned.taskTemplate()).isNotBlank();
    }

    /**
     * 受付時に**モデル名**まで固定する（スロットだけだと別のモデルへ移ってしまう）。
     *
     * <p>実行版（{@code revision}）も入り、送り直しのたびに +1 される。</p>
     */
    @Test
    void createPinsTheModelNameAndRevision() {
        UserPrincipal student = createStudent();
        GeometryAiRequestEntity entity = insert(student, "QUEUED");
        entity.setMode("A");
        entity.setSettingsSnapshotJson(
                geometryAiSettings.pinnedConfigJson("A", 1));
        assertThat(requestMapper.updateResubmitted(entity)).isEqualTo(1);

        GeometryAiRequestEntity created = requestMapper.findById(entity.getRequestId());
        AiFigureConfig pinned = AiFigureConfig.fromSnapshotJson(created.getSettingsSnapshotJson())
                .orElseThrow();
        // モデルのスロットと（設定があれば）モデル名の両方を固定する
        assertThat(pinned.provider()).isNotBlank();
        assertThat(pinned.revision()).isEqualTo(1);
        if (pinned.hasPinnedModel()) {
            assertThat(pinned.model()).doesNotContain("http");
        }

        // 送り直しは実行版を +1 する（技術的な再試行では増えない）
        int next = geometryAiSettings.nextRevision(created.getSettingsSnapshotJson());
        assertThat(next).isEqualTo(2);
        created.setMode("A");
        created.setSettingsSnapshotJson(geometryAiSettings.pinnedConfigJson("A", next));
        assertThat(requestMapper.updateResubmitted(created)).isEqualTo(1);
        assertThat(AiFigureConfig.fromSnapshotJson(
                requestMapper.findById(created.getRequestId()).getSettingsSnapshotJson())
                .orElseThrow().revision()).isEqualTo(2);
    }

    /**
     * 【削除】は状態を取消にし、**タスク一覧から消える**（行は監査のため残る）。
     *
     * <p>図形として保存済みのものは消せない（図形一覧から削除する）。</p>
     */
    @Test
    void discardHidesTheTaskFromTheListButKeepsTheRow() {
        UserPrincipal student = createStudent();
        GeometryAiRequestEntity entity = insert(student, "READY");
        assertThat(requestMapper.findTasks(student.accountId(), 50))
                .extracting(GeometryAiRequestEntity::getRequestId)
                .contains(entity.getRequestId());

        GeometryAiModels.RequestStatus status = geometryAiService.discard(student, entity.getRequestId(),
                entity.getVersion());

        assertThat(status.status()).isEqualTo("CANCELLED");
        // 行は残る（監査・AI 呼出履歴との紐付けを消さない）
        assertThat(requestMapper.findById(entity.getRequestId()).getStatusCode()).isEqualTo("CANCELLED");
        // 一覧には出ない（カードが戻ってこない）
        assertThat(requestMapper.findTasks(student.accountId(), 50))
                .extracting(GeometryAiRequestEntity::getRequestId)
                .doesNotContain(entity.getRequestId());
        // 2 回目は断る（他の端末で先に消えたときと同じ）
        assertThatThrownBy(() -> geometryAiService.discard(student, entity.getRequestId(),
                requestMapper.findById(entity.getRequestId()).getVersion()))
                .isInstanceOf(com.study21.common.core.exception.ConflictException.class)
                .hasMessageContaining("既に一覧から消えています");
    }

    /** 図形として保存済みのものは【削除】できない（図形側の削除に任せる）。 */
    @Test
    void discardRejectsSavedTasks() {
        UserPrincipal student = createStudent();
        GeometryAiRequestEntity entity = insert(student, "READY");
        entity.setCommands("A = (0, 0)\nB = (5, 0)\nPolygon(A, B, C)");
        entity.setCommandCount(3);
        geometryAiService.confirm(student, entity.getRequestId(),
                new GeometryAiModels.ConfirmRequest("検証の三角形", "geometry", "AI 生図から",
                        List.of("三角形"), "<construction/>", "AAAA", entity.getVersion()));

        GeometryAiRequestEntity saved = requestMapper.findById(entity.getRequestId());
        assertThat(saved.getStatusCode()).isEqualTo("REGISTERED");

        assertThatThrownBy(() -> geometryAiService.discard(student, saved.getRequestId(), saved.getVersion()))
                .isInstanceOf(com.study21.common.core.exception.ConflictException.class)
                .hasMessageContaining("図形として保存済み");
        assertThat(requestMapper.findById(entity.getRequestId()).getStatusCode()).isEqualTo("REGISTERED");
    }

    /** 【もう一度生成】でも、そのときの有効な設定で固定し直す。 */
    @Test
    void retryRepinsTheConfig() {
        UserPrincipal student = createStudent();
        // 【もう一度生成】は READY（できた作図を作り直す）と失敗した要求が対象
        GeometryAiRequestEntity entity = insert(student, "READY");

        GeometryAiModels.RequestStatus status = geometryAiService.retry(student, entity.getRequestId(),
                entity.getVersion());

        assertThat(status.status()).isEqualTo("PREPROCESSED");
        GeometryAiRequestEntity after = requestMapper.findById(entity.getRequestId());
        assertThat(after.getStatusCode()).isEqualTo("PREPROCESSED");
        assertThat(AiFigureConfig.fromSnapshotJson(after.getSettingsSnapshotJson())).isPresent();
        // 画像はそのまま
        assertThat(after.getOriginalPath()).isEqualTo(entity.getOriginalPath());
    }
}
