package com.study21.user.classroom;

import com.study21.common.core.exception.ConflictException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.multipart.MultipartFile;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 授業録音の**実 DB（PostgreSQL）に対する**同時実行の検証。
 *
 * <p>固定のモックでは「押さえる前の状態で判断していないか」を確かめられない。ここでは
 * **本物の DB の行ロック**（{@code SELECT ... FOR UPDATE}）と、本物のサービス・Mapper を使い、
 * 「分塊の受け入れ」と「収尾」が**同じ記録の行**を取り合う状況を作って確かめる:</p>
 * <ol>
 *   <li>分塊のアップロードと収尾が同時に来ても、**確定した範囲が動かない**
 *       （収尾が先なら分塊は届かず、分塊が先なら収尾がそれを見る）。</li>
 *   <li>収尾は 1 回だけ成功する（2 回目は 409。記録が二重に終わらない）。</li>
 *   <li>終わったあとの**新しい**分塊は受け付けない（黙って音を足さない）。</li>
 *   <li>保存済みの分塊の**同じ中身の再送**は冪等（応答を失った再送で壊れない）。</li>
 *   <li>明示の不完全終了は、**失った範囲**を返して終われる。</li>
 * </ol>
 *
 * <p>実行には DB とパスワードが要る（無いときはスキップする）:
 * {@code STUDY21_TEST_DATASOURCE_URL=... STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api test}。
 * 下見用の DB は {@code tmp/tools/study21-testdb.sh start} で作れる。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "STUDY21_TEST_DATASOURCE_URL", matches = ".+",
        disabledReason = "専用のテスト DB（STUDY21_TEST_DATASOURCE_URL）が未設定のためスキップ")
class ClassroomFinalizeConcurrencyIT {

    /** 検証用の Redis（Spring Session が要る。この試験の中だけで生きる）。 */
    private static final int REDIS_PORT = freePort();
    private static final Optional<RedisServer> REDIS = startRedis();
    /** 音声の置き場（試験の中だけで使う）。 */
    private static final Path STORAGE_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "study21-it-classroom");

    /** この検証で作った記録（**作った ID だけ**を後片付けする）。 */
    private static final java.util.List<Long> CREATED_RECORDS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** 分塊を 1 つ入れるごとに使う音声（中身が違えば「別の分塊」とみなされる）。 */
    private static final AtomicInteger CONTENT_SEED = new AtomicInteger();
    /** いま分塊を受け入れているスレッド（行ロックの検証で、待ち合わせの相手を見分ける）。 */
    private static final java.util.concurrent.atomic.AtomicReference<Thread> UPLOAD_THREAD =
            new java.util.concurrent.atomic.AtomicReference<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // 接続先は**専用のテスト DB に固定**（配備先の DB へは書かない）
        com.study21.user.testing.TestDatabase.override(registry);
        registry.add("study21.classroom.storage-root", STORAGE_ROOT::toString);
        registry.add("study21.classroom.ffmpeg-command", () -> "ffmpeg");
        registry.add("study21.classroom.ffprobe-command", () -> "ffprobe");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
        registry.add("spring.session.redis.configure-action", () -> "none");
    }

    /**
     * 実装の型で受ける。
     *
     * <p>{@link ClassroomServiceImpl} だけが持つ**試験用の入口**（分塊表の差し替え・背景実行器の
     * 差し替え）を使うため。本番の Spring もこの実装を入れている（{@code @Service} は 1 つ）。</p>
     */
    @Autowired
    private ClassroomServiceImpl service;
    /** 後片付け（`@AfterAll`）で使う。静的に控えるので Spring から代入する。 */
    private static ClassroomRecordMapper recordMapper;
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ClassroomFinalizeConcurrencyIT.class);

    /** 後片付け用に控える（`@Autowired` のメソッドは静的に代入できないので setter で受ける）。 */
    @Autowired
    void keepRecordMapperForCleanup(ClassroomRecordMapper mapper) {
        recordMapper = mapper;
    }

    /** 各テストでも使う口（控えたものと同じ。静的なので helper も静的）。 */
    private static ClassroomRecordMapper records() {
        return recordMapper;
    }

    @Autowired
    private ClassroomRecordingChunkMapper chunkMapper;

    @BeforeAll
    static void prepareStorage() throws IOException {
        Files.createDirectories(STORAGE_ROOT);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.ifPresent(server -> {
            try {
                server.stop();
            } catch (IOException ignored) {
                // 試験の後片付けなので、止められなくても結果は変えない
            }
        });
    }

    private static UserPrincipal student(long accountId) {
        return new UserPrincipal(accountId, "student" + accountId + "@example.com", "生徒",
                AccountType.STUDENT);
    }

    /** 新しい録音（状態 = RECORDING）を作る。 */
    private long createRecording(long accountId) {
        ClassroomModels.RecordStatus created = service.create(student(accountId),
                new ClassroomModels.CreateRequest("同時実行の検証", "数学", "ja", null));
        // **作った ID を控える**（後片付けで「最近の N 件」のような広い消し方をしない）
        CREATED_RECORDS.add(created.recordId());
        return created.recordId();
    }

    @AfterAll
    static void cleanUpCreatedRecords() {
        java.util.List<Long> ids = new java.util.ArrayList<>(CREATED_RECORDS);
        CREATED_RECORDS.clear();
        for (Long id : ids) {
            try {
                records().delete(id, ACCOUNT_ID);
            } catch (RuntimeException cause) {
                // 後片付けなので、消せなくても他の片付けは続ける（控えはログに残す）
                log.warn("検証データの後片付けに失敗しました。recordId={}", id, cause);
            }
        }
    }
    /** 新しい録音（状態 = RECORDING）を作る（**作った ID を控える**）。 */

    /** 1 回の検証で使うアカウント（下見用 DB の生徒）。 */
    private static final long ACCOUNT_ID = 1L;

    private void upload(long recordId, long accountId, int seq) throws IOException {
        service.uploadChunk(student(accountId), recordId, seq, file(seq), null,
                java.math.BigDecimal.valueOf((long) (seq - 1) * 20),
                java.math.BigDecimal.valueOf((long) seq * 20));
    }

    private static MultipartFile file(int seq) throws IOException {
        byte[] content = new byte[64 + CONTENT_SEED.incrementAndGet()];
        content[0] = (byte) seq;
        return new MockMultipartFile("file", "chunk-" + seq + ".webm", "audio/webm", content);
    }

    private static ClassroomModels.ChunkManifest manifest(int lastSeq) {
        return new ClassroomModels.ChunkManifest(lastSeq, lastSeq,
                java.util.stream.IntStream.rangeClosed(1, lastSeq).boxed().toList(),
                (long) lastSeq * 20 * 16_000L, List.of());
    }

    @Test
    @DisplayName("① 分塊のアップロードと収尾が同時に来ても、確定した範囲が動かない（行ロック）")
    void uploadAndFinishAreSerializedByTheRowLock() throws Exception {
        long accountId = ACCOUNT_ID;
        long recordId = createRecording(accountId);
        upload(recordId, accountId, 1);
        upload(recordId, accountId, 2);

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger uploadResult = new AtomicInteger(-1);
        Throwable[] uploadFailure = new Throwable[1];
        Thread uploader = new Thread(() -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                upload(recordId, accountId, 3);
                uploadResult.set(1);
            } catch (Throwable cause) {
                uploadFailure[0] = cause;
                uploadResult.set(0);
            }
        });

        ClassroomModels.EndResult endResult;
        Throwable endFailure = null;
        uploader.start();
        start.countDown();
        try {
            endResult = service.end(student(accountId), recordId, false, manifest(3));
        } catch (Throwable cause) {
            endResult = null;
            endFailure = cause;
        }
        uploader.join(20_000);

        int stored = chunkMapper.countByRecord(recordId);
        String status = records().findById(recordId).getStatus();
        assertThat(uploadFailure[0]).isNull();
        if (endResult != null && endResult.complete()) {
            assertThat(stored).isEqualTo(3);
            assertThat(status).isEqualTo(ClassroomModels.STATUS_STOPPED);
        } else if (endResult == null) {
            assertThat(endFailure).isInstanceOf(ChunkChecklistException.class);
            assertThat(((ChunkChecklistException) endFailure).missingSeqs()).contains(3);
            assertThat(stored).isEqualTo(3);
            assertThat(status).isEqualTo(ClassroomModels.STATUS_RECORDING);
        } else {
            org.junit.jupiter.api.Assertions.fail("収尾が通ったのに 3 件目が残っていません");
        }
    }

    /**
     * **行ロックが本当に効いているか**を決定的に確かめる。
     *
     * <p>①分塊の受け入れを、行を押さえたあとの INSERT で**3 秒止める**。②そのあいだに収尾を
     * 別スレッドから呼ぶ。行を押さえていない実装なら②はすぐ終わる。②が**押さえられている時間ぶん
     * 待つ**ことを、かかった時間で確かめる。</p>
     */
    @Test
    @DisplayName("① 収尾は、分塊を受け入れているトランザクションのコミットを待つ（行ロック）")
    void finishWaitsForTheUploadTransaction() throws Exception {
        long accountId = ACCOUNT_ID;
        long recordId = createRecording(accountId);
        long holdMillis = 3_000;
        CountDownLatch locked = new CountDownLatch(1);
        ClassroomRecordingChunkMapper real = chunkMapper;
        ClassroomRecordingChunkMapper slow = (ClassroomRecordingChunkMapper)
                java.lang.reflect.Proxy.newProxyInstance(
                        ClassroomRecordingChunkMapper.class.getClassLoader(),
                        new Class<?>[] { ClassroomRecordingChunkMapper.class },
                        (proxy, method, arguments) -> {
                            if ("insertIfAbsent".equals(method.getName())) {
                                locked.countDown();
                                Thread.sleep(holdMillis);
                            }
                            try {
                                return method.invoke(real, arguments);
                            } catch (java.lang.reflect.InvocationTargetException cause) {
                                throw cause.getCause();
                            }
                        });
        service.setChunkMapperForTest(slow);
        AtomicInteger uploadResult = new AtomicInteger(-1);
        Thread uploader = new Thread(() -> {
            try {
                service.uploadChunk(student(accountId), recordId, 1, file(1), null, null, null);
                uploadResult.set(1);
            } catch (Throwable cause) {
                uploadResult.set(0);
            }
        });
        AtomicInteger endResult = new AtomicInteger(-1);
        Throwable[] endFailure = new Throwable[1];
        java.util.concurrent.atomic.AtomicLong endMillis =
                new java.util.concurrent.atomic.AtomicLong(-1);
        Thread finisher = new Thread(() -> {
            long startedAt = System.nanoTime();
            try {
                service.end(student(accountId), recordId, false, manifest(1));
                endResult.set(1);
            } catch (Throwable cause) {
                endFailure[0] = cause;
                endResult.set(0);
            } finally {
                endMillis.set((System.nanoTime() - startedAt) / 1_000_000);
            }
        });
        try {
            uploader.start();
            assertThat(locked.await(20, TimeUnit.SECONDS)).isTrue();
            finisher.start();
            finisher.join(60_000);
            uploader.join(60_000);

            assertThat(uploadResult.get()).isEqualTo(1);
            assertThat(endResult.get()).isEqualTo(1);
            assertThat(endFailure[0]).isNull();
            assertThat(endMillis.get())
                    .as("収尾が、分塊を受け入れているトランザクションを待っていません（行ロックが効いていない）")
                    .isGreaterThan(holdMillis - 1_500);
            assertThat(chunkMapper.countByRecord(recordId)).isEqualTo(1);
            assertThat(records().findById(recordId).getStatus())
                    .isEqualTo(ClassroomModels.STATUS_STOPPED);
        } finally {
            service.setChunkMapperForTest(real);
        }
    }

    @Test
    @DisplayName("① 収尾を 2 回送っても成功は 1 回だけ（記録を二重に終わらせない）")
    void duplicateFinishIsRefused() throws Exception {
        long accountId = ACCOUNT_ID;
        long recordId = createRecording(accountId);
        upload(recordId, accountId, 1);
        upload(recordId, accountId, 2);

        ClassroomModels.EndResult first = service.end(student(accountId), recordId, false, manifest(2));
        assertThat(first.complete()).isTrue();

        ConflictException second = org.junit.jupiter.api.Assertions.assertThrows(
                ConflictException.class,
                () -> service.end(student(accountId), recordId, false, manifest(2)));
        assertThat(second).isNotInstanceOf(ChunkChecklistException.class);
        assertThat(records().findById(recordId).getStatus()).isEqualTo(ClassroomModels.STATUS_STOPPED);
    }

    @Test
    @DisplayName("① 終了したあとの**新しい**分塊は受け付けない（黙って音を足さない）")
    void newChunkAfterFinishIsRefused() throws Exception {
        long accountId = ACCOUNT_ID;
        long recordId = createRecording(accountId);
        upload(recordId, accountId, 1);
        service.end(student(accountId), recordId, false, manifest(1));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> upload(recordId, accountId, 2)))
                .isInstanceOf(ConflictException.class);
        assertThat(chunkMapper.countByRecord(recordId)).isEqualTo(1);
    }

    @Test
    @DisplayName("① 保存済みの分塊の**同じ中身の再送**は冪等（応答を失った再送で壊れない）")
    void resendOfSavedChunkIsIdempotent() throws Exception {
        long accountId = ACCOUNT_ID;
        long recordId = createRecording(accountId);
        MultipartFile same = file(1);
        service.uploadChunk(student(accountId), recordId, 1, same, null, null, null);

        ClassroomModels.ChunkUploadResult again = service.uploadChunk(
                student(accountId), recordId, 1, same, null, null, null);

        assertThat(again.seq()).isEqualTo(1);
        assertThat(chunkMapper.countByRecord(recordId)).isEqualTo(1);
    }

    @Test
    @DisplayName("② 明示の不完全終了は、失った範囲を返して終われる（詳細でも読める）")
    void forcedFinishKeepsTheLossRecord() throws Exception {
        long accountId = ACCOUNT_ID;
        long recordId = createRecording(accountId);
        upload(recordId, accountId, 1);
        upload(recordId, accountId, 3);

        ChunkChecklistException refused = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class,
                () -> service.end(student(accountId), recordId, false, manifest(3)));
        assertThat(refused.checklist().missingSeqs()).contains(2);

        ClassroomModels.EndResult forced = service.end(student(accountId), recordId, true, manifest(3));
        assertThat(forced.forced()).isTrue();
        assertThat(forced.complete()).isFalse();
        assertThat(forced.lossSeqs()).contains(2);
        assertThat(forced.notice()).contains("2");

        ClassroomModels.RecordDetail detail = service.detail(student(accountId), recordId);
        assertThat(detail.status()).isEqualTo(ClassroomModels.STATUS_STOPPED);
        assertThat(chunkMapper.countByRecord(recordId)).isEqualTo(2);
    }

    /* ---------------- 資材 ---------------- */

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException cause) {
            throw new IllegalStateException("空きポートを取れませんでした", cause);
        }
    }

    private static Optional<RedisServer> startRedis() {
        try {
            RedisServer server = RedisServer.newRedisServer().port(REDIS_PORT).build();
            server.start();
            return Optional.of(server);
        } catch (Exception cause) {
            return Optional.empty();
        }
    }
}
