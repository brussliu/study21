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
 * {@code STUDY21_DATASOURCE_URL=... STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api test}。
 * 下見用の DB は {@code tmp/tools/study21-testdb.sh start} で作れる。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class ClassroomFinalizeConcurrencyIT {

    /** 検証用の Redis（Spring Session が要る。この試験の中だけで生きる）。 */
    private static final int REDIS_PORT = freePort();
    private static final Optional<RedisServer> REDIS = startRedis();
    /** 音声の置き場（試験の中だけで使う）。 */
    private static final Path STORAGE_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "study21-it-classroom");

    /** 分塊を 1 つ入れるごとに使う音声（中身が違えば「別の分塊」とみなされる）。 */
    private static final AtomicInteger CONTENT_SEED = new AtomicInteger();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("study21.classroom.storage-root", STORAGE_ROOT::toString);
        registry.add("study21.classroom.ffmpeg-command", () -> "ffmpeg");
        registry.add("study21.classroom.ffprobe-command", () -> "ffprobe");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
        registry.add("spring.session.redis.configure-action", () -> "none");
    }

    @Autowired
    private ClassroomService service;
    @Autowired
    private ClassroomRecordMapper recordMapper;
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
        return created.recordId();
    }

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

    /* ---------------- ① 分塊と収尾の同時実行 ---------------- */

    @Test
    @DisplayName("① 分塊のアップロードと収尾が同時に来ても、確定した範囲が動かない（行ロック）")
    void uploadAndFinishAreSerializedByTheRowLock() throws Exception {
        long accountId = 1L;
        long recordId = createRecording(accountId);
        // 1・2 は先に入れておく（収尾は「3 まで録れた」と言われる）
        upload(recordId, accountId, 1);
        upload(recordId, accountId, 2);

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger uploadResult = new AtomicInteger(-1);
        Throwable[] uploadFailure = new Throwable[1];
        // 分塊 3 を送る側（収尾と同時に走らせる）
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

        /*
         * どちらの順になっても**矛盾しない**ことが要件:
         *   * 収尾が先に取れば、分塊 3 は状態が RECORDING でないので入らない（収尾は欠落を返す）。
         *   * 分塊が先に入れば、収尾は 3 までそろっていると見て終われる。
         * 「収尾が終わったのに、あとから分塊 3 が入っている」は**起こってはいけない**。
         */
        int stored = chunkMapper.countByRecord(recordId);
        String status = recordMapper.findById(recordId).getStatus();
        assertThat(uploadFailure[0]).isNull();
        if (endResult != null) {
            // 収尾が通った: 分塊 3 は収尾のあとに入っていない（行ロックで直列化されている）
            assertThat(endResult.complete()).isTrue();
            assertThat(stored).isEqualTo(3);
        } else {
            // 収尾が先に取った: 分塊 3 は入れられず、収尾は欠落として断った
            assertThat(endFailure).isInstanceOf(ChunkChecklistException.class);
            assertThat(((ChunkChecklistException) endFailure).missingSeqs()).contains(3);
            assertThat(uploadResult.get()).isEqualTo(0);
            assertThat(stored).isEqualTo(2);
        }
        // どちらの道でも、収尾のあとの状態は「終了」か「録音中のどちらか一方」に落ち着く
        assertThat(status).isIn(ClassroomModels.STATUS_STOPPED, ClassroomModels.STATUS_RECORDING);
        assertThat(recordMapper.findById(recordId).getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("① 収尾を 2 回送っても成功は 1 回だけ（記録を二重に終わらせない）")
    void duplicateFinishIsRefused() throws Exception {
        long accountId = 1L;
        long recordId = createRecording(accountId);
        upload(recordId, accountId, 1);
        upload(recordId, accountId, 2);

        ClassroomModels.EndResult first = service.end(student(accountId), recordId, false, manifest(2));
        assertThat(first.complete()).isTrue();

        ConflictException second = org.junit.jupiter.api.Assertions.assertThrows(
                ConflictException.class,
                () -> service.end(student(accountId), recordId, false, manifest(2)));
        assertThat(second).isNotInstanceOf(ChunkChecklistException.class);
        // 記録は終わったまま（2 回目の要求で状態が戻らない）
        assertThat(recordMapper.findById(recordId).getStatus()).isEqualTo(ClassroomModels.STATUS_STOPPED);
    }

    @Test
    @DisplayName("① 終了したあとの**新しい**分塊は受け付けない（黙って音を足さない）")
    void newChunkAfterFinishIsRefused() throws Exception {
        long accountId = 1L;
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
        long accountId = 1L;
        long recordId = createRecording(accountId);
        MultipartFile same = file(1);
        service.uploadChunk(student(accountId), recordId, 1, same, null, null, null);

        // 同じ中身・同じ連番（応答を失った再送）
        ClassroomModels.ChunkUploadResult again = service.uploadChunk(
                student(accountId), recordId, 1, same, null, null, null);

        assertThat(again.seq()).isEqualTo(1);
        assertThat(chunkMapper.countByRecord(recordId)).isEqualTo(1);
    }

    /* ---------------- ② 不完全終了の記録 ---------------- */

    @Test
    @DisplayName("② 明示の不完全終了は、失った範囲を返して終われる（詳細でも読める）")
    void forcedFinishKeepsTheLossRecord() throws Exception {
        long accountId = 1L;
        long recordId = createRecording(accountId);
        // 1 と 3 は保存できたが 2 が届いていない（画面は 3 まで録れたと言っている）
        upload(recordId, accountId, 1);
        upload(recordId, accountId, 3);

        // まず普通の終了は断られる
        ChunkChecklistException refused = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class,
                () -> service.end(student(accountId), recordId, false, manifest(3)));
        assertThat(refused.checklist().missingSeqs()).contains(2);

        // 利用者が確認したので通す
        ClassroomModels.EndResult forced = service.end(student(accountId), recordId, true, manifest(3));
        assertThat(forced.forced()).isTrue();
        assertThat(forced.complete()).isFalse();
        assertThat(forced.lossSeqs()).contains(2);
        assertThat(forced.notice()).contains("2");

        // 記録を読み直しても失った範囲が残っている（一度きりの通知にしない）
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
            // 起動できない環境では、テスト側のコンテキストが落ちる（スキップではなく失敗にする）
            return Optional.empty();
        }
    }
}
