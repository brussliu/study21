package com.study21.admin.classroomai;

import com.study21.admin.batch.BatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最終まとめの**起動の受理**の検証（実 DB）。
 *
 * <p>固定のモックでは「条件つき更新が本当に 1 本しか通らないか」を確かめられない。ここでは
 * 本物の PostgreSQL と本物の Mapper を使い、**同じ要求を同時に何本も投げても**</p>
 * <ol>
 *   <li>受理されるのは 1 回だけ（2 つ目のタスクを作らない）、</li>
 *   <li>すでに作成済み（`READY`）なら AI をもう一度呼ばない、</li>
 *   <li>前回の起動のまま落ちた `GENERATING` は**やり直せる**（永久に「作成中」で止めない）、</li>
 * </ol>
 * <p>を確かめる。</p>
 *
 * <p>実行には DB とパスワードが要る（無いときはスキップする）:
 * {@code STUDY21_TEST_DATASOURCE_URL=... STUDY21_DATASOURCE_PASSWORD=... mvn -pl admin-api test}。
 * 下見用の DB は {@code tmp/tools/study21-testdb.sh start} で作れる。</p>
 */
@SpringBootTest(properties = {
    // 起動時の自動バッチ（本物の業務）を**すべて**止める
    "study21.batch.auto-run.startup-enabled=false",
    "study21.batch.auto-run.schedule-enabled=false",
    "study21.batch.auto-run.recovery-enabled=false"
})
@EnabledIfEnvironmentVariable(named = "STUDY21_TEST_DATASOURCE_URL", matches = ".+",
        disabledReason = "専用のテスト DB（STUDY21_TEST_DATASOURCE_URL）が未設定のためスキップ")
class ClassroomNoteGenerationClaimIT {

    /** 検証用の記録（作った行だけを見る）。 */
    private static final long ACCOUNT_ID = 1L;

    /** **専用のテスト DB 以外では動かさない**（設定漏れのまま配備先の DB へ書かない）。 */
    @org.junit.jupiter.api.BeforeAll
    static void requireDedicatedTestDatabase(@org.springframework.beans.factory.annotation.Value(
            "${spring.datasource.url:}") String datasourceUrl) {
        org.assertj.core.api.Assertions.assertThat(datasourceUrl)
                .as("検証は専用のテスト DB でのみ動かします（STUDY21_TEST_DATASOURCE_URL）")
                .isNotBlank();
        org.assertj.core.api.Assertions.assertThat(datasourceUrl)
                .as("配備先の DB を指していたら検証を止めます")
                .doesNotContain("192.168.0.100");
    }

    /** 接続先を**専用のテスト DB に固定**する（`STUDY21_DATASOURCE_*` へは落とさない）。 */
    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
        com.study21.admin.testing.TestDatabase.override(registry);
    }

    @Autowired
    private ClassroomNoteMapper noteMapper;
    @Autowired
    private ClassroomAiPipelineService pipelineService;
    @Autowired
    private com.study21.admin.batch.ProcessRunId processRunId;
    /** バッチの代役（忙しい・例外・失敗を差し込む）。 */
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private BatchService batchService;
    @Autowired
    private JdbcTemplate jdbc;

    /** 検証用のノート行を作る（呼び側が片付ける）。 */
    private long createNote(String status) {
        Long recordId = jdbc.queryForObject("""
                INSERT INTO public."CR_授業記録情報"
                    ("授業記録番号", "登録者アカウントID", "学生ID", "状態", "バージョン", "登録元コード")
                VALUES (?, ?, ?, 'STOPPED', 1, 'APP')
                RETURNING "授業記録ID"
                """, Long.class, "IT-NOTE-" + System.nanoTime(), ACCOUNT_ID, ACCOUNT_ID);
        Long noteId = jdbc.queryForObject("""
                INSERT INTO public."CR_授業ノート情報"
                    ("授業記録ID", "種別", "対象開始連番", "対象終了連番", "生成状態",
                     "エラーコード", "再試行回数", "バージョン", "登録者アカウントID", "登録元コード")
                VALUES (?, 'FINAL', 1, 3, ?, CASE WHEN ? = 'FAILED' THEN 'AI_ERROR' ELSE NULL END,
                        0, 1, ?, 'APP')
                RETURNING "授業ノートID"
                """, Long.class, recordId, status, status, ACCOUNT_ID);
        return noteId == null ? 0L : noteId;
    }

    private void deleteNote(long noteId) {
        // 記録ごと消す（FK の CASCADE でノートも消える）
        jdbc.update("""
                DELETE FROM public."CR_授業記録情報"
                 WHERE "授業記録ID" = (SELECT "授業記録ID" FROM public."CR_授業ノート情報"
                                        WHERE "授業ノートID" = ?)
                """, noteId);
    }

    private String statusOf(long noteId) {
        return jdbc.queryForObject(
                "SELECT \"生成状態\" FROM public.\"CR_授業ノート情報\" WHERE \"授業ノートID\" = ?",
                String.class, noteId);
    }

    @Test
    @DisplayName("① 同じ要求を同時に投げても、受理されるのは 1 回だけ（2 つ目のタスクを作らない）")
    void concurrentClaimsOnlyOneSucceeds() throws Exception {
        long noteId = createNote("PENDING");
        try {
            int threads = 8;
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> workers = new ArrayList<>();
            AtomicInteger claimed = new AtomicInteger();
            for (int index = 0; index < threads; index += 1) {
                Thread worker = new Thread(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException cause) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (noteMapper.claimGeneration(noteId, token()) == 1) {
                        claimed.incrementAndGet();
                    }
                });
                workers.add(worker);
                worker.start();
            }
            start.countDown();
            for (Thread worker : workers) {
                worker.join(20_000);
            }

            // 条件つき更新なので、勝つのは必ず 1 本
            assertThat(claimed.get()).isEqualTo(1);
            assertThat(statusOf(noteId)).isEqualTo("GENERATING");
        } finally {
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("② すでに作成済み（READY）の行は受理しない（AI をもう一度呼ばない）")
    void readyNoteIsNotClaimedAgain() {
        long noteId = createNote("READY");
        try {
            assertThat(noteMapper.claimGeneration(noteId, token())).isZero();
            assertThat(statusOf(noteId)).isEqualTo("READY");
        } finally {
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("② 失敗（FAILED）の行はもう一度受理できる（やり直しの入口を残す）")
    void failedNoteCanBeClaimedAgain() {
        long noteId = createNote("FAILED");
        try {
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            assertThat(statusOf(noteId)).isEqualTo("GENERATING");
        } finally {
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("③ 前回の起動のまま落ちた GENERATING は、一定時間たてばやり直せる")
    void staleGeneratingIsReclaimed() {
        long noteId = createNote("PENDING");
        try {
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            // 走っている最中（いま始まったばかり）は**取り上げない**
            assertThat(noteMapper.reclaimStaleGeneration(noteId, staleBefore(30), token()))
                    .as("始まったばかりの生成を取り上げてはいけない")
                    .isZero();

            // 前回の起動が**40 分前**のまま残っている（プロセスが落ちた回）とみなす
            jdbc.update("""
                    UPDATE public."CR_授業ノート情報"
                       SET "生成開始日時" = ?
                     WHERE "授業ノートID" = ?
                    """, Timestamp.from(Instant.now().minusSeconds(40 * 60)), noteId);
            // 落ちたとみなせる古い開始は取り上げてやり直せる
            assertThat(noteMapper.reclaimStaleGeneration(noteId, staleBefore(30), token())).isEqualTo(1);
            assertThat(statusOf(noteId)).isEqualTo("GENERATING");
        } finally {
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("③ 開始時刻が無い（改修前の）GENERATING もやり直せる")
    void generatingWithoutStartTimeIsReclaimed() {
        long noteId = createNote("GENERATING");
        try {
            jdbc.update("""
                    UPDATE public."CR_授業ノート情報" SET "生成開始日時" = NULL
                     WHERE "授業ノートID" = ?
                    """, noteId);
            assertThat(noteMapper.reclaimStaleGeneration(noteId, staleBefore(30), token())).isEqualTo(1);
        } finally {
            deleteNote(noteId);
        }
    }

    private String tokenOf(long noteId) {
        return jdbc.queryForObject(
                "SELECT \"生成トークン\" FROM public.\"CR_授業ノート情報\" WHERE \"授業ノートID\" = ?",
                String.class, noteId);
    }

    @Test
    @DisplayName("④ バッチが忙しくて断られても、GENERATING のまま残らず「やり直せる失敗」になる")
    void busyBatchLeavesRetryableFailure() throws Exception {
        long noteId = createNote("PENDING");
        try {
            // 別の授業の batC62 が走っている（BatchServiceImpl の防重が ConflictException を投げる）
            doThrow(new com.study21.common.core.exception.ConflictException(
                    "バッチは既に実行中です: batC62")).when(batchService)
                    .rerunStep(anyString(), anyString(), anyString());

            ClassroomAiPipelineService.Acceptance acceptance =
                    pipelineService.accept(noteId, "it-busy");
            // 受理はしている（実行は背景）＝応答を待たせない
            assertThat(acceptance.accepted()).isTrue();

            // 背景の失敗が**記録に残る**（GENERATING のまま放置しない）
            awaitStatus(noteId, "FAILED");
            assertThat(jdbc.queryForObject(
                    "SELECT \"エラーコード\" FROM public.\"CR_授業ノート情報\" WHERE \"授業ノートID\" = ?",
                    String.class, noteId)).isEqualTo(ClassroomAiPipelineService.CODE_BUSY);
            // やり直せる
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
        } finally {
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("④ 実行はされたが失敗・スキップで終わったときも、GENERATING のまま残らない")
    void failedExecutionLeavesFailure() throws Exception {
        long noteId = createNote("PENDING");
        try {
            // 実行記録は作られたが、結果は失敗（success=false）
            java.util.Map<String, Object> step = new java.util.LinkedHashMap<>();
            step.put("success", false);
            step.put("status", "FAILED");
            step.put("message", "設定が足りません。");
            doReturn(step).when(batchService).rerunStep(anyString(), anyString(), anyString());

            ClassroomAiPipelineService.Acceptance acceptance =
                    pipelineService.accept(noteId, "it-failed");
            assertThat(acceptance.accepted()).isTrue();
            awaitStatus(noteId, "FAILED");
            assertThat(jdbc.queryForObject(
                    "SELECT \"エラーコード\" FROM public.\"CR_授業ノート情報\" WHERE \"授業ノートID\" = ?",
                    String.class, noteId)).isEqualTo(ClassroomAiPipelineService.CODE_FAILED);
        } finally {
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("④ 既に READY のノートは受理しない（AI をもう一度呼ばない・結果を壊さない）")
    void readyNoteIsNotRestartedByAccept() {
        long noteId = createNote("READY");
        try {
            ClassroomAiPipelineService.Acceptance acceptance =
                    pipelineService.accept(noteId, "it-ready");
            assertThat(acceptance.accepted()).isFalse();
            assertThat(acceptance.status()).isEqualTo("READY");
            assertThat(statusOf(noteId)).isEqualTo("READY");
        } finally {
            deleteNote(noteId);
        }
    }

    /** 実行記録を 1 行作って ID を返す（ノートに紐づける）。 */
    private long createExecution(long noteId, String batchCode, String status, String runId) {
        Long executionId = jdbc.queryForObject("""
                INSERT INTO public."BAT_バッチ実行履歴情報"
                    ("バッチコード", "バッチ種別", "起動種別", "状態", "起動識別子", "開始時刻")
                -- 起動種別は 1 文字（'C'=他の処理から / 'L'=ログイン / 'R'=復旧 / 'S'=予約）
                VALUES (?, 'C', 'R', ?, ?, CURRENT_TIMESTAMP)
                RETURNING "実行ID"
                """, Long.class, batchCode, status, runId);
        return executionId == null ? 0L : executionId;
    }

    private void deleteExecution(long executionId) {
        jdbc.update("DELETE FROM public.\"BAT_バッチ実行履歴情報\" WHERE \"実行ID\" = ?", executionId);
    }

    /**
     * 「受理された回」の状態を作る（**トークンが入り、実行record にも紐づいた** `GENERATING`）。
     *
     * <p>本番の順序（受理 → 実行記録の作成 → `markGenerating`）と同じ形にする。</p>
     */
    private void bindExecution(long noteId, long executionId) {
        jdbc.update("""
                UPDATE public."CR_授業ノート情報" SET "生成実行ID" = ?
                 WHERE "授業ノートID" = ?
                """, executionId, noteId);
    }

    private ClassroomAiPipelineService.Liveness livenessOf(long noteId) {
        return pipelineService.livenessOf(noteMapper.findById(noteId));
    }

    @Test
    @DisplayName("A: 別の授業の batC62 が走っていても、本まとめの失联判定は変わらない")
    void anotherLessonExecutionDoesNotAffectThisNote() {
        long noteId = createNote("PENDING");
        long otherNoteId = createNote("PENDING");
        long otherExecution = createExecution(otherNoteId, "batC62", "RUNNING", processRunId.value());
        try {
            // 本まとめは受理だけ（実行記録はまだ無い）＝**別の授業の実行は見ない**
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            assertThat(livenessOf(noteId))
                    .as("別の授業の実行を、このまとめの実行と取り違えてはいけない")
                    .isEqualTo(ClassroomAiPipelineService.Liveness.BINDING);

            // 本まとめの実行が**別プロセスの**記録として残っている（サーバー再起動後）＝失联
            long ownExecution = createExecution(noteId, "batC62", "RUNNING", "20260101T000000-deadbeef");
            bindExecution(noteId, ownExecution);
            assertThat(livenessOf(noteId)).isEqualTo(ClassroomAiPipelineService.Liveness.LOST);
            // 回復の入口でやり直せる失敗に戻る
            ClassroomAiPipelineService.RecoveryView view = pipelineService.recoverOne(noteId);
            assertThat(view.recovered()).isTrue();
            assertThat(view.status()).isEqualTo("FAILED");
            deleteExecution(ownExecution);
        } finally {
            deleteExecution(otherExecution);
            deleteNote(otherNoteId);
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("B: 本まとめの実行が生きているあいだは、回復も再起動もしない（長く走っても同じ）")
    void liveExecutionIsNotRecovered() {
        long noteId = createNote("PENDING");
        long executionId = createExecution(noteId, "batC62", "RUNNING", processRunId.value());
        try {
            // 受理済み（GENERATING）にして、実行記録を結び付ける
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            bindExecution(noteId, executionId);
            // **1 時間前**に始まっていても、実行記録が生きていれば失联ではない
            jdbc.update("""
                    UPDATE public."CR_授業ノート情報" SET "生成開始日時" = ?
                     WHERE "授業ノートID" = ?
                    """, Timestamp.from(Instant.now().minusSeconds(60 * 60)), noteId);

            assertThat(livenessOf(noteId)).isEqualTo(ClassroomAiPipelineService.Liveness.RUNNING);
            ClassroomAiPipelineService.RecoveryView view = pipelineService.recoverOne(noteId);
            assertThat(view.recovered()).isFalse();
            assertThat(view.recoverable()).isFalse();
            assertThat(statusOf(noteId)).isEqualTo("GENERATING");

            // 受理もしない（**二重に走らせない**）
            ClassroomAiPipelineService.Acceptance acceptance = pipelineService.accept(noteId, "it-live");
            assertThat(acceptance.accepted()).isFalse();
            assertThat(acceptance.status()).isEqualTo("GENERATING");
            assertThat(statusOf(noteId)).isEqualTo("GENERATING");
        } finally {
            deleteExecution(executionId);
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("C: サーバー再起動で残った GENERATING は、記録を確かめたうえで回復できる")
    void leftoverGenerationIsRecoveredThroughTheEntryPoint() {
        long noteId = createNote("PENDING");
        // 実行記録は**別プロセスの起動識別子**で RUNNING のまま残っている（そのプロセスはもう居ない）
        long executionId = createExecution(noteId, "batC62", "RUNNING", "20260101T000000-cafebabe");
        try {
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            bindExecution(noteId, executionId);
            ClassroomAiPipelineService.RecoveryView view = pipelineService.recoverOne(noteId);
            assertThat(view.recovered()).isTrue();
            assertThat(view.liveness()).isEqualTo(ClassroomAiPipelineService.Liveness.LOST.name());
            assertThat(statusOf(noteId)).isEqualTo("FAILED");
            // やり直せる（FAILED は受理できる）
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            // **帰属は新しい試行のために外れる**（前の実行IDを引きずらない）
            assertThat(noteMapper.findById(noteId).getGenerationExecutionId()).isNull();
        } finally {
            deleteExecution(executionId);
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("D: 受理したばかりで実行記録がまだ無い窓は、回収しない")
    void freshClaimWithoutExecutionIsNotRecovered() {
        long noteId = createNote("PENDING");
        try {
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            // 実行記録はまだ無いが、始まったばかり（＝準備中）
            assertThat(livenessOf(noteId)).isEqualTo(ClassroomAiPipelineService.Liveness.BINDING);
            ClassroomAiPipelineService.RecoveryView view = pipelineService.recoverOne(noteId);
            assertThat(view.recovered()).isFalse();
            assertThat(statusOf(noteId)).isEqualTo("GENERATING");
        } finally {
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("E: 同じまとめを同時に回復しても、有効な状態変更は 1 回だけ")
    void concurrentRecoveryChangesStateOnce() throws Exception {
        long noteId = createNote("PENDING");
        long executionId = createExecution(noteId, "batC62", "RUNNING", "20260101T000000-abcdef01");
        try {
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            bindExecution(noteId, executionId);
            int threads = 6;
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.List<Thread> workers = new java.util.ArrayList<>();
            AtomicInteger recovered = new AtomicInteger();
            for (int index = 0; index < threads; index += 1) {
                Thread worker = new Thread(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException cause) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (pipelineService.recoverOne(noteId).recovered()) {
                        recovered.incrementAndGet();
                    }
                });
                workers.add(worker);
                worker.start();
            }
            start.countDown();
            for (Thread worker : workers) {
                worker.join(20_000);
            }

            assertThat(recovered.get()).isEqualTo(1);
            assertThat(statusOf(noteId)).isEqualTo("FAILED");
        } finally {
            deleteExecution(executionId);
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("F: 回復したあとに旧スレッドが遅れて返っても、回復した状態を上書きしない")
    void lateOldThreadCannotOverwriteRecoveredState() {
        long noteId = createNote("PENDING");
        long executionId = createExecution(noteId, "batC62", "RUNNING", "20260101T000000-abcdef02");
        try {
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            bindExecution(noteId, executionId);
            String oldToken = tokenOf(noteId);
            ClassroomNoteEntity beforeRecovery = noteMapper.findById(noteId);
            assertThat(pipelineService.recoverOne(noteId).recovered()).isTrue();
            assertThat(statusOf(noteId)).isEqualTo("FAILED");

            // 旧スレッドが遅れて成功を書き込もうとする（**トークンは回復前のまま**）
            assertThat(noteMapper.updateReady(noteId, "{\"テーマ\":\"古い\"}", 999L,
                    beforeRecovery.getVersion(), oldToken))
                    .as("回復した状態を旧スレッドが上書きしてはいけない")
                    .isZero();
            assertThat(noteMapper.updateFailed(noteId, "AI_ERROR", "旧スレッドの失敗",
                    beforeRecovery.getVersion(), oldToken)).isZero();
            assertThat(statusOf(noteId)).isEqualTo("FAILED");
        } finally {
            deleteExecution(executionId);
            deleteNote(noteId);
        }
    }

    /** 状態が変わるまで待つ（背景の実行を待ち合わせる）。 */
    private void awaitStatus(long noteId, String expected) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt += 1) {
            if (expected.equals(statusOf(noteId))) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(statusOf(noteId)).as("背景の実行が状態を更新しませんでした").isEqualTo(expected);
    }

    @Test
    @DisplayName("④ 実行できなかった試行は「やり直せる失敗」として残る（GENERATING のまま放置しない）")
    void failedGenerationIsRecordedAsRetryableFailure() {
        long noteId = createNote("PENDING");
        try {
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            assertThat(statusOf(noteId)).isEqualTo("GENERATING");

            // バッチが忙しい／実行器の起動に失敗した、と同じ処理
            assertThat(noteMapper.markGenerationFailed(noteId, "NOTE_ENGINE_BUSY",
                    "バッチは既に実行中です: batC62", tokenOf(noteId))).isEqualTo(1);
            assertThat(statusOf(noteId)).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject(
                    "SELECT \"エラーコード\" FROM public.\"CR_授業ノート情報\" WHERE \"授業ノートID\" = ?",
                    String.class, noteId)).isEqualTo("NOTE_ENGINE_BUSY");
            // やり直せる（FAILED はもう一度受理できる）
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
        } finally {
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("④ 古い試行の遅い書き込みは、新しい試行の状態を上書きしない（トークンの照合）")
    void lateAttemptCannotOverwriteNewerAttempt() {
        long noteId = createNote("PENDING");
        try {
            String oldToken = token();
            assertThat(noteMapper.claimGeneration(noteId, oldToken)).isEqualTo(1);
            ClassroomNoteEntity entity = noteMapper.findById(noteId);

            // 途中で失われたとみなされ、**新しい試行**が引き取った
            jdbc.update("""
                    UPDATE public."CR_授業ノート情報" SET "生成開始日時" = ?
                     WHERE "授業ノートID" = ?
                    """, Timestamp.from(Instant.now().minusSeconds(40 * 60)), noteId);
            String newToken = token();
            assertThat(noteMapper.reclaimStaleGeneration(noteId, staleBefore(30), newToken))
                    .isEqualTo(1);

            // 古い試行が**あとから結果を書こうとする**が、通らない（トークンが違う）
            assertThat(noteMapper.updateReady(noteId, "{\"テーマ\":\"古い結果\"}", 111L,
                    entity.getVersion(), oldToken))
                    .as("古い試行の結果で上書きしてはいけない")
                    .isZero();
            assertThat(noteMapper.updateFailed(noteId, "AI_ERROR", "古い試行の失敗",
                    entity.getVersion(), oldToken)).isZero();
            assertThat(statusOf(noteId)).isEqualTo("GENERATING");

            // 新しい試行の結果は通る
            ClassroomNoteEntity fresh = noteMapper.findById(noteId);
            assertThat(noteMapper.updateReady(noteId, "{\"テーマ\":\"新しい結果\"}", 222L,
                    fresh.getVersion(), newToken)).isEqualTo(1);
            assertThat(statusOf(noteId)).isEqualTo("READY");
        } finally {
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("⑤ 失われた実行（実行記録が無い GENERATING）は回復の入口で拾える")
    void lostGenerationIsFoundByTheRecoveryQuery() {
        long noteId = createNote("PENDING");
        try {
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            // 40 分前のまま残っている（プロセスが落ちた回）
            jdbc.update("""
                    UPDATE public."CR_授業ノート情報" SET "生成開始日時" = ?
                     WHERE "授業ノートID" = ?
                    """, Timestamp.from(Instant.now().minusSeconds(40 * 60)), noteId);

            List<ClassroomNoteEntity> stale = noteMapper.findStaleGenerating(staleBefore(30), 50);
            assertThat(stale).extracting(ClassroomNoteEntity::getNoteId).contains(noteId);

            // 回復（実行記録が無いので失われている）→ やり直せる失敗になる
            assertThat(noteMapper.markGenerationFailed(noteId, "NOTE_ENGINE_BUSY",
                    "生成の実行が失われていました。", tokenOf(noteId))).isEqualTo(1);
            assertThat(statusOf(noteId)).isEqualTo("FAILED");
        } finally {
            deleteNote(noteId);
        }
    }

    @Test
    @DisplayName("⑤ 始まったばかりの GENERATING は回復の対象に入らない（長い実行を止めない）")
    void freshGenerationIsNotRecovered() {
        long noteId = createNote("PENDING");
        try {
            assertThat(noteMapper.claimGeneration(noteId, token())).isEqualTo(1);
            List<ClassroomNoteEntity> stale = noteMapper.findStaleGenerating(staleBefore(30), 50);
            assertThat(stale).extracting(ClassroomNoteEntity::getNoteId).doesNotContain(noteId);
        } finally {
            deleteNote(noteId);
        }
    }

    /** この試行のトークン（同じ形の一意な文字列）。 */
    private static String token() {
        return "it-" + Long.toString(System.nanoTime(), 36);
    }

    /** 「この時刻より前に始まったものは落ちた」の境目を作る。 */
    private static Timestamp staleBefore(int minutesAgo) {
        return Timestamp.from(Instant.now().minusSeconds(minutesAgo * 60L));
    }
}

