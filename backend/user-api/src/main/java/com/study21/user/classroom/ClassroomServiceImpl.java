package com.study21.user.classroom;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.user.account.AccountEntity;
import com.study21.user.account.AccountMapper;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 授業録音 / AI 授業記録の実装。
 *
 * <p>流れ（画面 = `views/classroom/*`）:</p>
 * <pre>
 *   POST /classroom（作成・RECORDING） → POST /classroom/{id}/start（開始時刻）
 *   → POST /classroom/{id}/chunks（分塊 → STT → セグメント追記 → トリガー評価）
 *   → GET /classroom/{id}/segments?afterSeq=（ポーリング）
 *   → POST /classroom/{id}/end（STOPPED + 最終まとめ PENDING）
 *   → 画面が admin-api の薄い入口（/api/admin/batch/classroom/notes/{noteId}/run）を 1 回呼ぶ
 * </pre>
 *
 * <p>ノート/要約の AI 生成は admin-api バッチ（batC61/batC62）。user-api は PENDING 行を
 * 作るだけ（サービス間で API を呼ばず、DB の状態列だけで橋渡しする）。</p>
 */
@Service
public class ClassroomServiceImpl implements ClassroomService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomServiceImpl.class);

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    /** STT が空のときに埋める転写（無音・聞き取れなかった分塊。連番の不変を保つため）。 */
    private static final String EMPTY_TRANSCRIPT = "（聞き取れませんでした）";
    /** 話者分離なし（MVP）のときの表示ラベル。 */
    private static final String SPEAKER_LECTURE = "講義";

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** 【終了】のあとに届いた分塊を受け入れる猶予（この時間内なら音声だけ保存する）。 */
    private static final java.time.Duration LATE_CHUNK_GRACE = java.time.Duration.ofMinutes(10);

    private final ClassroomRecordMapper recordMapper;
    private final ClassroomSegmentMapper segmentMapper;
    /** 録音の分塊（1 塊 1 行 1 ファイル）。転写セグメントとは**別の連番**を持つ。 */
    private ClassroomRecordingChunkMapper chunkMapper;
    /** 保存した分塊から再生用の 1 本を組立てる（分塊が無ければ何もしない）。 */
    private final ClassroomRecordingAssembler assembler;
    private final ClassroomNoteMapper noteMapper;
    private final ClassroomPresetMapper presetMapper;
    private final ClassroomAiSettings settings;
    private final ClassroomSttClient sttClient;
    private final ClassroomSttStreamService streamService;
    /**
     * ストリーミング書き起こし（話しながら文字が出る）のセッション。
     *
     * <p>動いている間は**分塊ごとの STT をしない**（同じ音声を二度書き起こさないため）。
     * テストでは null（そのときは今までどおり分塊ごと）。</p>
     */
    private final ClassroomRecordingStorage storage;
    private final AccountMapper accountMapper;

    public ClassroomServiceImpl(ClassroomRecordMapper recordMapper,
                                ClassroomSegmentMapper segmentMapper,
                                ClassroomRecordingChunkMapper chunkMapper,
                                ClassroomRecordingAssembler assembler,
                                ClassroomNoteMapper noteMapper,
                                ClassroomPresetMapper presetMapper,
                                ClassroomAiSettings settings,
                                ClassroomSttClient sttClient,
                                ClassroomRecordingStorage storage,
                                AccountMapper accountMapper,
                               ClassroomSttStreamService streamService) {
        this.recordMapper = recordMapper;
        this.segmentMapper = segmentMapper;
        this.chunkMapper = chunkMapper;
        this.assembler = assembler;
        this.noteMapper = noteMapper;
        this.presetMapper = presetMapper;
        this.settings = settings;
        this.sttClient = sttClient;
        this.streamService = streamService;
        this.storage = storage;
        this.accountMapper = accountMapper;
    }

    // ------------------------------------------------------------------ 設定

    @Override
    @Transactional(readOnly = true)
    public ClassroomModels.OptionsResult options(UserPrincipal user) {
        ClassroomAiSettings.Snapshot snapshot = settings.load();
        boolean enabled = settings.enabled(snapshot);
        int dailyLimit = settings.dailyLimit(snapshot);
        long usedToday = user == null ? 0L : recordMapper.countTodayByAccount(user.accountId());
        String notice = "";
        if (!enabled) {
            notice = "「授業録音 / AI 授業記録」はシステム設定で無効になっています。";
        } else if (dailyLimit > 0 && usedToday >= dailyLimit) {
            notice = "本日の録音は上限（" + dailyLimit + " 回）に達しました。明日またお試しください。";
        }
        boolean browserStt = settings.browserStt(snapshot);
        // 言語モード → BCP-47。画面（Web Speech API）が同じ表を使うので、ここが唯一の定義
        Map<String, String> languageCodes = new LinkedHashMap<>();
        for (String mode : List.of("ja", "zh", "en", "ja-en", "zh-en")) {
            languageCodes.put(mode, settings.sttLanguageCode(snapshot, mode));
        }
        return new ClassroomModels.OptionsResult(enabled, settings.chunkSeconds(snapshot),
                settings.maxRecordingMinutes(snapshot), settings.retentionDays(snapshot),
                dailyLimit, usedToday, notice,
                browserStt ? ClassroomModels.STT_MODE_BROWSER : ClassroomModels.STT_MODE_SERVER,
                languageCodes, settings.noteEnabled(snapshot),
                // 阿里巴巴のリアルタイム認識のときだけ、ストリーミング書き起こしが使える
                !browserStt && ClassroomAiSettings.PROVIDER_ALIBABA.equals(snapshot.raw(ClassroomAiSettings.KEY_STT_PROVIDER)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomModels.PresetView> presets(UserPrincipal user) {
        return presetMapper.findVisible(user == null ? null : user.accountId()).stream()
                .map(ClassroomServiceImpl::toPresetView)
                .toList();
    }

    // ------------------------------------------------------------------ 作成

    @Override
    @Transactional
    public ClassroomModels.RecordStatus create(UserPrincipal user, ClassroomModels.CreateRequest request) {
        ClassroomAiSettings.Snapshot snapshot = settings.load();
        requireEnabled(snapshot);
        requireDailyLimit(user, snapshot);

        String languageMode = normalizeLanguageMode(request.languageMode());
        Long presetId = request.presetId();
        String presetText = null;
        if (presetId != null) {
            ClassroomPresetEntity preset = presetMapper.findById(presetId);
            if (preset == null) {
                throw ClassroomApiException.invalid("前置詞プリセットが見つかりません。");
            }
            presetText = preset.getText();
        }

        AccountEntity account = accountMapper.findById(user.accountId());
        Long familyId = null;
        if (account != null && account.getGuardianId() != null) {
            familyId = account.getGuardianId();
        } else if (user.accountType() == AccountType.GUARDIAN) {
            familyId = user.accountId();
        }

        ClassroomRecordEntity entity = new ClassroomRecordEntity();
        entity.setRecordNo(nextRecordNo());
        entity.setCreatedBy(user.accountId());
        entity.setStudentId(user.accountId());
        entity.setFamilyId(familyId);
        // 授業名は**任意**（未入力ならサーバーが既定の名前を入れる。利用者の指示）
        entity.setTitle(trimToNull(request.title(), ClassroomModels.TITLE_MAX) == null
                ? ClassroomModels.UNTITLED : trimToNull(request.title(), ClassroomModels.TITLE_MAX));
        // 科目は授業名とは別に持つ（一覧で独立して出す）
        entity.setSubject(trimToNull(request.subject(), ClassroomModels.SUBJECT_MAX));
        entity.setLanguageMode(languageMode);
        entity.setPresetId(presetId);
        entity.setPresetText(presetText);
        entity.setStatus(ClassroomModels.STATUS_RECORDING);
        entity.setSourceCode("APP");
        recordMapper.insert(entity);

        log.info("classroom record created. recordId={} recordNo={} accountId={} language={}",
                entity.getRecordId(), entity.getRecordNo(), user.accountId(), languageMode);
        return toStatus(entity);
    }

    @Override
    @Transactional
    public ClassroomModels.RecordStatus start(UserPrincipal user, long recordId) {
        ClassroomRecordEntity entity = requireOwner(user, recordId);
        if (recordMapper.updateStarted(recordId, user.accountId(), versionOf(entity)) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        log.info("classroom record started. recordId={} accountId={}", recordId, user.accountId());
        return toStatus(recordMapper.findById(recordId));
    }

    // ------------------------------------------------------------------ 分塊

    /**
     * 分塊を 1 つ受け取る（音声の保存 → 書き起こし → トリガー評価）。
     *
     * <p>音声は **1 塊 1 ファイル**（`chunk-{記録ID}-{連番}.webm`）として保存し、その行を
     * {@code CR_授業録音分塊情報} に持つ。再生用の 1 本は再生のときに連番順に組立てる
     * （{@link ClassroomRecordingAssembler}）。</p>
     *
     * <p>冪等の判定は**この表の (授業記録ID, 分塊連番)** で行う。以前は転写セグメントの
     * 最大連番を流用していたため、文の数と分塊の数が違う回に「3 個目の分塊が重複として
     * 捨てられる」「転写が無い回に二重に追記される」という不具合が出ていた。</p>
     */
    @Override
    @Transactional
    public ClassroomModels.ChunkUploadResult uploadChunk(UserPrincipal user, long recordId, int seq,
                                                         MultipartFile file, MultipartFile sttAudio,
                                                         java.math.BigDecimal startSeconds,
                                                         java.math.BigDecimal endSeconds) {
        // **記録の行を押さえてから**状態を読み直す（収尾と同じ行を押さえる＝後から来た側が新しい状態を見る）
        ClassroomRecordEntity record = lockOwner(user, recordId);
        ClassroomAiSettings.Snapshot snapshot = settings.load();
        requireEnabled(snapshot);
        /*
         * 終了直後に届いた最後の分塊は**受け入れる**（音声を残すため。isLateChunkAllowed を参照）。
         * 押さえたあとの状態で判断するので、「状態を確かめた直後に収尾が確定する」競合は起きない
         * （収尾の更新はこのトランザクションのコミットを待つ）。
         */
        boolean lateChunk = false;
        if (!ClassroomModels.STATUS_RECORDING.equals(record.getStatus())) {
            if (!isLateChunkAllowed(record)) {
                throw new ConflictException("この録音は録音中ではありません（"
                        + statusLabel(record.getStatus()) + "）。"
                        + (ClassroomModels.STATUS_TRANSCRIBING.equals(record.getStatus())
                                ? "いま終了処理中です。少し待ってからもう一度お試しください。" : ""));
            }
            lateChunk = true;
        }
        if (seq < 1) {
            throw ClassroomApiException.invalid("連番は 1 以上で指定してください。");
        }
        /*
         * **終わったあとの分塊は「宣言された範囲」だけ**受け入れる。
         *
         * <p>終了は<b>最終分塊一覧を確定させる</b>操作なので、そのあとに新しい連番が入ると
         * 確定した一覧と実体が食い違う（詳細画面が「全部そろっています」と言ったまま音が増える）。
         * 停止直後に届いた**宣言済みの分塊**（取りこぼしの救済）は受け入れるが、一覧に無い
         * 新しい連番は断る（利用者は録音をやり直すか、その回を別の録音として残す）。</p>
         */
        if (lateChunk && seq > declaredLastSeq(record)) {
            throw new ConflictException("この録音は終了しています。"
                    + "終了したあとに新しい音声（連番 " + seq + "）は追加できません。"
                    + "録音をもう一度行ってください。");
        }

        int chunkSeconds = settings.chunkSeconds(snapshot);
        int maxMinutes = settings.maxRecordingMinutes(snapshot);

        byte[] bytes = readBytes(file);
        String checksum = sha256(bytes);

        /*
         * 冪等: 同じ連番に**同じ中身**（バイト数 + チェックサム）が既にあれば、保存済みの結果を返す。
         * 応答を失った再送・ネットワークの波での再送・同時再送のどれでも、音も行も増えない。
         * 逆に、同じ連番で**中身が違う**ときは上書きしない（別の分塊を黙って捨てない）。明示のエラーにする。
         * また、大きい連番が既にあっても**小さい連番を捨てない**（順不同で届いた分塊も保存する）。
         */
        ClassroomRecordingChunkEntity existing = chunkMapper.findBySeq(recordId, seq);
        if (existing != null) {
            if (!existing.sameContent(bytes.length, checksum)) {
                throw new ConflictException(duplicateSeqMessage(seq));
            }
            log.info("classroom chunk re-sent (same content). recordId={} seq={} bytes={}",
                    recordId, seq, bytes.length);
            return chunkResult(record, seq, segmentsOfChunk(recordId, seq), null, null);
        }

        // この分塊が録音のどこか（画面が測った実際の経過秒。無ければ連番から計算する＝旧クライアント）
        BigDecimal start = startSeconds != null && startSeconds.signum() >= 0
                ? startSeconds : BigDecimal.valueOf((long) (seq - 1) * chunkSeconds);
        BigDecimal end = endSeconds != null && endSeconds.compareTo(start) > 0
                ? endSeconds : start.add(BigDecimal.valueOf(chunkSeconds));

        // 録音の最大時間は**実際の録音の位置（経過秒）**で見る。
        // 転写セグメントの連番（＝文の数）では測らない（無音の授業では 0 のままになる）
        BigDecimal position = recordingPositionOf(recordId, seq, chunkSeconds, start, end);
        if (position.compareTo(BigDecimal.valueOf((long) maxMinutes * 60)) > 0) {
            throw ClassroomApiException.invalid("録音は最大 " + maxMinutes + " 分までです。"
                    + "ここで録音を終了してください。");
        }

        /*
         * 音声は**再生用にも必ず保存**する（STT の結果が空でも履歴の元音声を残す）。
         *
         * <p><b>順序</b>: ①置き場を用意する → ②DB へ**引用だけ**を入れる（`ON CONFLICT
         * DO NOTHING` に勝った 1 本が引用を持つ） → ③勝った側だけが実体を書く。
         * 逆（書いてから引用を取る）にすると、同じ連番が同時に 2 本来たときに負けた側の
         * 実体が勝った側を上書きし得る（DB の行と実体の中身が食い違う）。</p>
         */
        ClassroomRecordingStorage.StoredRecording stored;
        if (record.getAudioPath() == null || record.getAudioName() == null) {
            // 記録 ID から置き場を決める（同時に届いた分塊が別々の置き場へ散らばらないように）
            stored = storage.newRecording(user.accountId(), recordId,
                    file == null ? null : file.getContentType());
        } else {
            stored = new ClassroomRecordingStorage.StoredRecording(record.getAudioPath(), record.getAudioName(),
                    record.getAudioMime());
        }
        // 初回の分塊がほぼ同時に届いても、置き場の初期化で落ちない（記録ごとに直列化する）
        storage.prepareRecordingDirectory(stored.relativeDir());
        // 書き込み先は**分塊ごとに固有**（同じ連番でも別の場所＝上書きが起こり得ない）
        ClassroomRecordingStorage.ChunkLocation location =
                storage.newChunkLocation(stored.relativeDir(), recordId, seq, stored.mime());

        ClassroomRecordingChunkEntity chunk = new ClassroomRecordingChunkEntity();
        chunk.setRecordId(recordId);
        chunk.setSeq(seq);
        chunk.setStartOffsetSeconds(start);
        chunk.setEndOffsetSeconds(end);
        chunk.setByteSize((long) bytes.length);
        chunk.setChecksum(checksum);
        chunk.setStorageDir(location.relativeDir());
        chunk.setFileName(location.fileName());
        chunk.setMime(stored.mime());
        // 新しいコンテナ（ヘッダ）から始まるか＝組立てでセッションの切れ目になるか
        chunk.setContainerHead(RecordingContainerCutter.isContainerHead(bytes, bytes.length));
        chunk.setProcessingStatus(ClassroomModels.CHUNK_STORED);
        chunk.setSegmentCount(0);

        boolean claimed = chunkMapper.insertIfAbsent(chunk) == 1;
        if (!claimed) {
            /*
             * 同時再送で別の要求が先に同じ連番を引用した。
             *
             * <p>中身が同じなら**同じ分塊**なので、保存済みの結果を返して終わる（実体を二重に
             * 作らない）。違うなら衝突にし、**勝った側の実体には触れない**（こちらは実体を書いて
             * いないので、消すものも無い）。</p>
             */
            ClassroomRecordingChunkEntity winner = chunkMapper.findBySeq(recordId, seq);
            if (winner == null) {
                // 挿入が 0 行なのに行が無い＝別の操作で消えた（記録の削除と競合した）
                throw new ConflictException("分塊を保存できませんでした。"
                        + "別の操作でこの録音が削除された可能性があります。");
            }
            if (!winner.sameContent(bytes.length, checksum)) {
                throw new ConflictException(duplicateSeqMessage(seq));
            }
            log.info("classroom chunk already stored by another request (concurrent retry)."
                    + " recordId={} seq={} bytes={}", recordId, seq, bytes.length);
            return chunkResult(record, seq, segmentsOfChunk(recordId, seq), null, null);
        }

        /*
         * 引用を取れた 1 本だけが実体を書く。
         *
         * <p>ここで失敗しても引用（DB の行）は残す: 行が「この分塊は欠けている」ことを示し、
         * 終了時の検証で欠落として見つかる（黙って無かったことにならない）。実体が無い行は
         * 結合でも欠落として扱う（{@link ClassroomRecordingSessionPlanner}）。</p>
         */
        try {
            storage.writeChunkLocation(location, bytes);
        } catch (RuntimeException cause) {
            log.error("分塊の実体を書けませんでした（引用は残します）。"
                    + "recordId={} seq={} name={}", recordId, seq, location.fileName(), cause);
            throw cause;
        }
        // 録音の置き場が決まっていなければ、ここで記録へ結び付ける（最初に引用を取れた分塊が入れる）
        if (record.getAudioPath() == null || record.getAudioName() == null) {
            recordMapper.updateAudio(recordId, stored.relativeDir(), stored.fileName(), stored.mime(),
                    bytes.length, user.accountId());
        }

        // 終了のあとに届いた分塊: **音声だけ保存**して書き起こしはしない
        // （録音は終わっていて最終まとめも作られている。音声を捨てると授業の記録が丸ごと消える）
        // STT の接続情報も要らないので、解決する前に返す（設定が不完全でも音声は残せる）
        if (lateChunk) {
            chunkMapper.updateStatus(recordId, seq, ClassroomModels.CHUNK_SKIPPED, 0);
            log.info("classroom late chunk stored after end. recordId={} seq={} bytes={}",
                    recordId, seq, bytes.length);
            return chunkResult(record, seq, List.of(), null, null);
        }

        // ストリーミング書き起こしが動いている間は、分塊ごとの STT をしない
        // （同じ音声を二度書き起こさない。音声は上で保存済み）
        if (streamService != null && streamService.isActive(recordId)) {
            chunkMapper.updateStatus(recordId, seq, ClassroomModels.CHUNK_SKIPPED, 0);
            log.info("classroom chunk stored without stt (streaming). recordId={} seq={} bytes={}",
                    recordId, seq, bytes.length);
            return chunkResult(record, seq, List.of(), null, null);
        }

        // ここから先は書き起こし（STT）。ブラウザ認識のときはサーバーは STT を呼ばない
        // （書き起こしは画面が認識したテキストを `appendTranscript` で送ってくる。音声は保存だけ）
        if (settings.browserStt(snapshot)) {
            chunkMapper.updateStatus(recordId, seq, ClassroomModels.CHUNK_SKIPPED, 0);
            log.info("classroom chunk stored without stt (browser recognition). recordId={} seq={} bytes={}",
                    recordId, seq, bytes.length);
            return chunkResult(record, seq, List.of(), null, null);
        }

        ClassroomAiSettings.SttConnection connection = settings.resolveStt(snapshot);
        String languageCode = settings.sttLanguageCode(snapshot, record.getLanguageMode());

        // STT へ渡す音声を選ぶ。**書き起こし用の音声（sttAudio）があればそちらを使う**:
        // MediaRecorder の timeslice で切った 2 つ目以降の分塊はコンテナのヘッダを持たず、
        // 認識エンジンがデコードできない（実測: 1 塊目だけ EBML ヘッダがある）。
        // 画面は分塊ごとに 16kHz モノラルの PCM（audio/L16）を並行して作って送ってくる。
        boolean hasSttAudio = sttAudio != null && !sttAudio.isEmpty();
        byte[] sttBytes = hasSttAudio ? readBytes(sttAudio) : bytes;
        String sttMime = hasSttAudio ? sttAudio.getContentType() : (file == null ? null : file.getContentType());

        // STT（分塊単位の batch transcription）。スタブは固定応答
        ClassroomSttClient.SttResponse response = sttClient.transcribe(new ClassroomSttClient.SttRequest(
                connection.provider(), connection.model(), connection.url(), connection.apiKey(),
                languageCode, settings.sttAlternativeLanguageCode(record.getLanguageMode()),
                sttBytes, sttMime,
                settings.sttTimeoutSeconds(snapshot)));
        if (hasSttAudio) {
            log.info("classroom chunk stt uses dedicated audio. recordId={} seq={} mime={} bytes={}",
                    recordId, seq, sttMime, sttBytes.length);
        }
        if (!response.isSuccess()) {
            /*
             * 書き起こしに失敗しても**分塊（音声）は捨てない**。
             *
             * <p>実測: 二音源の録音で画面が書き起こし用 PCM を送れない状態になり、保存した webm で
             * STT を呼ぶと阿里巴巴が「audio/webm;codecs=opus は認識できません」で失敗。ここで 409 を
             * 返していたため、その回の音声が**丸ごと残らなかった**（seq=8〜12 が全部 409）。
             * 音声は後から作り直せないので、保存して受け入れ、書き起こしだけを諦める
             * （理由はログに残す。画面には警告として出る）。</p>
             */
            chunkMapper.updateStatus(recordId, seq, ClassroomModels.CHUNK_SKIPPED, 0);
            log.warn("classroom chunk stt failed; audio kept. recordId={} seq={} mime={} reason={}",
                    recordId, seq, sttMime, response.errorMessage());
            return chunkResult(record, seq, List.of(), null, null);
        }

        String text = joinText(response.segments());
        if (text.isEmpty()) {
            // 認識結果が空: **埋め草を入れずに何も作らない**（文字数と AI まとめを汚さないため）。
            // 実測: 「（聞き取れませんでした）」が並び、まとめの材料を薄めていた
            chunkMapper.updateStatus(recordId, seq, ClassroomModels.CHUNK_SKIPPED, 0);
            log.info("classroom chunk transcribed empty. recordId={} seq={} bytes={}", recordId, seq,
                    sttBytes.length);
            return chunkResult(record, seq, List.of(), null, null);
        }
        String speaker = response.segments().stream()
                .map(ClassroomSttClient.Segment::speaker).filter(s -> s != null && !s.isBlank()).findFirst()
                .orElse(SPEAKER_LECTURE);
        String language = response.segments().stream()
                .map(ClassroomSttClient.Segment::language).filter(s -> s != null && !s.isBlank()).findFirst()
                .orElse(languageCode);

        ClassroomSegmentEntity segment = new ClassroomSegmentEntity();
        segment.setRecordId(recordId);
        segment.setSeq(seq);
        segment.setStartOffsetSeconds(start);
        segment.setEndOffsetSeconds(end);
        segment.setSpeaker(speaker);
        segment.setText(text);
        segment.setLanguage(language);
        segmentMapper.insert(segment);
        recordMapper.addTranscribedChars(recordId, text.length());
        chunkMapper.updateStatus(recordId, seq, ClassroomModels.CHUNK_TRANSCRIBED, 1);

        // トリガー評価 → 成立したら PENDING のフェーズノートを作る
        Long pendingNoteId = evaluateTrigger(recordId, user.accountId(), text, snapshot);

        List<ClassroomModels.SegmentView> appended = List.of(toSegmentView(segment));
        log.info("classroom chunk transcribed. recordId={} seq={} chars={} triggered={}",
                recordId, seq, text.length(), pendingNoteId != null);
        return chunkResult(record, seq, appended, pendingNoteId,
                pendingNoteId == null ? null : ClassroomModels.noteRunPath(pendingNoteId));
    }

    /** 同じ連番に違う中身が来たときの理由（画面にそのまま出す日本語）。 */
    private static String duplicateSeqMessage(int seq) {
        return "同じ連番（" + seq + "）に違う内容の音声が届きました。"
                + "保存済みの分塊は書き換えていません。画面を開き直して録音をやり直してください。";
    }

    /**
     * 分塊アップロードの結果を組み立てる。
     *
     * <p>`nextSeq`（書き起こしのポーリング用）は**取りこぼさない側**に倒して `seq + 1` を返す
     * （分塊は順不同で届き得るので、分塊表の最大連番 + 1 を返すと、まだ取っていない文を
     * 飛ばしてしまう）。次の**分塊**の連番は分塊表から取る（`nextChunkSeq`）。</p>
     */
    private ClassroomModels.ChunkUploadResult chunkResult(ClassroomRecordEntity record, int seq,
                                                          List<ClassroomModels.SegmentView> appended,
                                                          Long pendingNoteId, String runPath) {
        return new ClassroomModels.ChunkUploadResult(record.getRecordId(), seq,
                seq + 1, maxChunkSeqOf(record.getRecordId()) + 1,
                appended, pendingNoteId, pendingNoteId != null, record.getStatus(), runPath);
    }

    /** その分塊が作った転写セグメント（再送に応えるため。サーバー STT は分塊の連番＝セグメントの連番）。 */
    private List<ClassroomModels.SegmentView> segmentsOfChunk(long recordId, int seq) {
        return segmentMapper.findByRecordAfter(recordId, seq - 1).stream()
                .filter(segment -> segment.getSeq() != null && segment.getSeq() == seq)
                .map(ClassroomServiceImpl::toSegmentView).toList();
    }

    private int maxChunkSeqOf(long recordId) {
        Integer max = chunkMapper.maxSeq(recordId);
        return max == null ? 0 : max;
    }

    /**
     * この分塊が示す**録音の位置（秒）**。録音の最大時間の判定に使う。
     *
     * <p>画面が測った実際の経過秒を第一にする（分塊の長さの設定を録音中に変えても壊れない）。
     * 送られてこない旧クライアントだけ「連番 × 分塊の長さ」に落とす。保存済みの分塊より
     * 前には戻さない（遅れて届いた分塊で位置が巻き戻ると、上限の判定が緩くなる）。</p>
     */
    private BigDecimal recordingPositionOf(long recordId, int seq, int chunkSeconds,
                                           BigDecimal start, BigDecimal end) {
        BigDecimal position = end != null ? end : start;
        if (position == null) {
            position = BigDecimal.valueOf((long) seq * chunkSeconds);
        }
        BigDecimal stored = chunkMapper.maxEndOffsetSeconds(recordId);
        if (stored != null && stored.compareTo(position) > 0) {
            position = stored;
        }
        return position;
    }

    /** 分塊の長さ（秒。設定の現在値）。終了時の許容差のように**設定を引く 1 か所**で使う。 */
    private int chunkSecondsOf() {
        return settings.chunkSeconds(settings.load());
    }

    /**
     * 「録音の終わりの位置」を比べるときの許容差（秒）。
     *
     * <p>単位は統一時間軸のサンプル数（16kHz）。画面は最後の `dataavailable` のあとに止めるので、
     * 最後の分塊の終わりと**わずかに**ずれる。ただし分塊 1 つ（既定 20 秒）ぶんのずれは
     * 「録れた範囲の申告が間違っている」ということなので、端数だけを許す。</p>
     */
    private static final double END_SAMPLE_TOLERANCE_SECONDS = 2.0;

    /** 分塊のバイト列の照合に使う SHA-256（16 進 64 文字）。 */
    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(Character.forDigit((value >> 4) & 0x0F, 16));
                builder.append(Character.forDigit(value & 0x0F, 16));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException cause) {
            // SHA-256 は必ずある（無い環境では起動時に気づく）
            throw new IllegalStateException("チェックサムを計算できませんでした。", cause);
        }
    }


    /**
     * ブラウザ（Web Speech API）の認識結果を 1 件足す。
     *
     * <p>連番はサーバーが振る（ブラウザの切句は分塊と一致しない）。時間は「録音開始からの
     * 経過秒」で受け取り、始まりは前のセグメントの終わりにする（単調に増える）。
     * フェーズノートのトリガー評価は分塊のときと同じ。</p>
     */
    @Override
    @Transactional
    public ClassroomModels.ChunkUploadResult appendTranscript(UserPrincipal user, long recordId,
                                                              ClassroomModels.TranscriptRequest request) {
        ClassroomRecordEntity record = requireOwner(user, recordId);
        ClassroomAiSettings.Snapshot snapshot = settings.load();
        requireEnabled(snapshot);
        if (!ClassroomModels.STATUS_RECORDING.equals(record.getStatus())) {
            throw new ConflictException("この録音は録音中ではありません（"
                    + statusLabel(record.getStatus()) + "）。");
        }
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) {
            throw ClassroomApiException.invalid("書き起こしのテキストが空です。");
        }

        Integer existingMax = segmentMapper.maxSeq(recordId);
        int seq = (existingMax == null ? 0 : existingMax) + 1;

        BigDecimal start = lastSegmentEndOf(recordId);
        BigDecimal end = request.offsetSeconds() == null || request.offsetSeconds().compareTo(start) < 0
                ? start
                : request.offsetSeconds();

        ClassroomSegmentEntity segment = new ClassroomSegmentEntity();
        segment.setRecordId(recordId);
        segment.setSeq(seq);
        segment.setStartOffsetSeconds(start);
        segment.setEndOffsetSeconds(end);
        segment.setSpeaker(SPEAKER_LECTURE);
        segment.setText(text);
        segment.setLanguage(settings.sttLanguageCode(snapshot, record.getLanguageMode()));
        segmentMapper.insert(segment);
        recordMapper.addTranscribedChars(recordId, text.length());

        Long pendingNoteId = evaluateTrigger(recordId, user.accountId(), text, snapshot);
        log.info("classroom transcript appended. recordId={} seq={} chars={} triggered={}",
                recordId, seq, text.length(), pendingNoteId != null);
        return new ClassroomModels.ChunkUploadResult(recordId, seq, seq + 1,
                maxChunkSeqOf(recordId) + 1, List.of(toSegmentView(segment)), pendingNoteId,
                pendingNoteId != null, record.getStatus(),
                pendingNoteId == null ? null : ClassroomModels.noteRunPath(pendingNoteId));
    }

    /** その授業記録の最後のセグメントの終わり（無ければ 0）。 */
    private BigDecimal lastSegmentEndOf(long recordId) {
        Integer maxSeq = segmentMapper.maxSeq(recordId);
        if (maxSeq == null || maxSeq <= 0) {
            return BigDecimal.ZERO;
        }
        List<ClassroomSegmentEntity> tail = segmentMapper.findByRecordAfter(recordId, maxSeq - 1);
        for (ClassroomSegmentEntity segment : tail) {
            if (segment.getSeq() != null && segment.getSeq() == maxSeq) {
                return segment.getEndOffsetSeconds() == null ? BigDecimal.ZERO : segment.getEndOffsetSeconds();
            }
        }
        return BigDecimal.ZERO;
    }

    // ------------------------------------------------------------------ 参照

    @Override
    @Transactional(readOnly = true)
    public ClassroomModels.SegmentListResult segments(UserPrincipal user, long recordId, int afterSeq) {
        requireVisible(user, recordId);
        List<ClassroomModels.SegmentView> items = segmentMapper.findByRecordAfter(recordId, afterSeq).stream()
                .map(ClassroomServiceImpl::toSegmentView).toList();
        int maxSeq = items.isEmpty() ? afterSeq : items.get(items.size() - 1).seq();
        return new ClassroomModels.SegmentListResult(items, maxSeq);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomModels.RecordDetail detail(UserPrincipal user, long recordId) {
        ClassroomRecordEntity record = requireVisible(user, recordId);
        List<ClassroomModels.SegmentView> segments = segmentMapper.findByRecord(recordId).stream()
                .map(ClassroomServiceImpl::toSegmentView).toList();
        List<ClassroomModels.NoteView> notes = noteMapper.findByRecord(recordId).stream()
                .map(ClassroomServiceImpl::toNoteView).toList();
        String presetName = null;
        String presetText = record.getPresetText();
        if (record.getPresetId() != null) {
            ClassroomPresetEntity preset = presetMapper.findById(record.getPresetId());
            if (preset != null) {
                presetName = preset.getName();
                if (presetText == null) {
                    presetText = preset.getText();
                }
            }
        }
        boolean hasAudio = record.getAudioPath() != null && record.getAudioName() != null;
        return new ClassroomModels.RecordDetail(
                record.getRecordId(), record.getRecordNo(), record.getTitle(), record.getSubject(),
                record.getLanguageMode(),
                record.getPresetId(), presetName, presetText,
                record.getStatus(), statusLabel(record.getStatus()),
                iso(record.getStartTime()), iso(record.getEndTime()),
                record.getDurationSeconds(), record.getTranscribedChars(),
                record.getSummaryJson(), hasAudio, record.getAudioMime(),
                segments, notes, versionOf(record),
                iso(record.getCreatedAt()), iso(record.getUpdatedAt()),
                // 結合の状態（「音声は保存されている」と「再生できる」を分けて出す）
                assemblyViewOf(record),
                // 不完全なまま終えた回に失った連番（詳細画面が出し続ける）
                missingSeqsOf(record),
                // 書き起こし（認識）の収尾の結果（**音声の欠落とは別の軸**）
                transcribeViewOf(record));
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomModels.NoteStatusView noteStatus(UserPrincipal user, long recordId, long noteId) {
        // 見える記録か（他人の記録のノートを覗かせない）
        requireVisible(user, recordId);
        // その記録のノート一覧から**指定の 1 件**を探す（別の記録のノートは見せない）
        ClassroomNoteEntity note = noteMapper.findByRecord(recordId).stream()
                .filter(candidate -> candidate.getNoteId() != null && candidate.getNoteId() == noteId)
                .findFirst()
                .orElse(null);
        if (note == null) {
            // **別の記録のノートを指定された**（画面の取り違え）: 存在しない扱いにする
            throw new NotFoundException("授業ノートが見つかりません。");
        }
        String status = note.getStatus() == null ? "PENDING" : note.getStatus();
        boolean accepted = "GENERATING".equals(status) || "READY".equals(status);
        return new ClassroomModels.NoteStatusView(noteId, note.getKind(), status,
                noteStatusLabel(status), accepted,
                "PENDING".equals(status) || "FAILED".equals(status),
                note.getErrorCode(), note.getErrorMessage());
    }

    /** ノートの状態の表示名（日本語）。 */
    private static String noteStatusLabel(String status) {
        return switch (status == null ? "PENDING" : status) {
            case "GENERATING" -> "作成中";
            case "READY" -> "できました";
            case "FAILED" -> "失敗";
            default -> "まだ作成していません";
        };
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomModels.RecordListResult list(UserPrincipal user, String status, int page, int size) {
        int safeSize = size <= 0 ? ClassroomModels.DEFAULT_SIZE : Math.min(size, ClassroomModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        Long owner = null;
        Long familyId = null;
        if (user.accountType() == AccountType.STUDENT) {
            owner = user.accountId();
        } else if (user.accountType() == AccountType.GUARDIAN) {
            familyId = user.accountId();
        }
        // ADMIN は両方 null = 全件
        String statusFilter = normalizeStatus(status);
        long total = recordMapper.count(owner, familyId, statusFilter);
        List<ClassroomModels.RecordRow> items = recordMapper
                .search(owner, familyId, statusFilter, safeSize, (safePage - 1) * safeSize).stream()
                .map(ClassroomServiceImpl::toRow).toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new ClassroomModels.RecordListResult(items, total, safePage, safeSize, totalPages);
    }

    // ------------------------------------------------------------------ 終了

    /**
     * 録音を終える（**サーバー側で状態を検証してから**最終まとめの行を作る）。
     *
     * <p>画面の【授業を終了】を信じない。見るのは 2 つ:</p>
     * <ol>
     *   <li>**音源ごとの収尾（finish）が済んでいるか**。済んでいないまま最終まとめの範囲を
     *       固定すると、あとから保存された尾部の文がまとめに入らない（＝静かな取りこぼし）。
     *       まだやり直せる未完了は **409 で理由つきに断る**（画面は【続きをやり直す】を出す）</li>
     *   <li>**録音の分塊（音声の実体）が保存されているか**。書き起こしがあるのに分塊が 0 件なら、
     *       音声が 1 つも保存されていない（音は後から作り直せない）ので、これも断る</li>
     * </ol>
     * <p>やり直しても直らない終端（試行の上限・音声なし・発話なし）は**断らずに通し**、
     * {@code notice} で知らせる（終われない授業を作らない）。</p>
     */
    @Override
    @Transactional
    public ClassroomModels.EndResult end(UserPrincipal user, long recordId, boolean force,
                                         ClassroomModels.ChunkManifest manifest) {
        /*
         * **記録の行を押さえてから**状態を読む（分塊の受け入れと同じ行を押さえる）。
         *
         * <p>押さえる前の状態で「録音中」と判断すると、そのあとで届いた分塊が確定した範囲に
         * 入り込む（または入り損ねる）。押さえておけば、同時に走っているアップロードの
         * コミットを待ってから確かめられる（長い処理はこのトランザクションの中で走らせない）。</p>
         */
        ClassroomRecordEntity record = lockOwner(user, recordId);
        ClassroomAiSettings.Snapshot snapshot = settings.load();
        if (!ClassroomModels.STATUS_RECORDING.equals(record.getStatus())) {
            throw new ConflictException("この録音は録音中ではありません（" + statusLabel(record.getStatus()) + "）。");
        }
        int maxSeq = maxSeqOf(recordId);
        /*
         * **収尾の順序**（この 4 段で「確認したのに未調整のアップロードが入る」競合を作らない）:
         *
         * <ol>
         *   <li>**状態を確かめる**（読むだけ）: 音声の分塊が 1 から連続して全部そろっているか
         *       （実体の有無・大きさまで見る）と、音源ごとの書き起こしの収尾が済んでいるか。
         *       欠けていれば**どの連番か**を返して断る（画面はその分塊を送り直す）。</li>
         *   <li>**収尾の鍵を取る**（状態 = RECORDING → TRANSCRIBING を 1 文で）。状態そのものが
         *       排他になるので、収尾のあいだは分塊を受け付けない（`uploadChunk` が状態で断る）。</li>
         *   <li>**もう一度数える**: 直前の書き込みはこの要求だけなので、増えたら別の要求が
         *       先に収尾を始めている＝断る（確認を 2 回繰り返すのではなく、鍵とセットで確かめる）。</li>
         *   <li>完了（状態 = STOPPED）と、再生用の 1 本の生成（コミット後に背景）。</li>
         * </ol>
         */
        ClassroomModels.ChunkChecklist checklist = checkChunks(record, manifest);
        if (!checklist.complete() && !force) {
            throw new ChunkChecklistException(checklist,
                    "録音の音声（分塊）がそろっていません（足りない連番 "
                            + checklist.missingSeqs() + "）。【再試行】で送り直すか、"
                            + "音声を一部失うことを確認したうえで【不完全なまま終了】を押してください。");
        }
        String finalizeNotice = requireFinalizeComplete(record, maxSeq);
        List<ClassroomRecordingChunkEntity> beforeClaim = chunkMapper.findByRecord(recordId);
        int expectedChunks = beforeClaim == null ? 0 : beforeClaim.size();
        if (recordMapper.claimFinalize(recordId, user.accountId()) == 0) {
            throw new ChunkChecklistException(checklist,
                    "この録音はすでに終了処理に入っています（または終了済みです）。"
                            + "画面を開き直して状態を確かめてから、もう一度お試しください。");
        }
        List<ClassroomRecordingChunkEntity> afterClaim = chunkMapper.findByRecord(recordId);
        int countAfterClaim = afterClaim == null ? 0 : afterClaim.size();
        if (countAfterClaim != expectedChunks) {
            // 鍵を取る直前に別の要求が足した（状態は排他なので、通常は起きない＝起きたら断る）
            throw new ChunkChecklistException(checkChunks(record, manifest),
                    "音声の保存が終わったあとに、別の分塊が届きました。"
                            + "最新の状態を確かめて、もう一度終了してください。");
        }
        if (!checklist.complete() && force) {
            // 明示の不完全終了: **何を失うか**を残す（黙って完了にしない）
            finalizeNotice = join(finalizeNotice, "音声の一部が保存できていません（足りない連番 "
                    + checklist.missingSeqs() + "）。録音した音はその区間だけ失われています。");
        }
        int claimedVersion = versionOf(record) + 1;
        int durationSeconds = durationSecondsOf(record, snapshot);
        /*
         * **確定した「録れた範囲」と、失った範囲を残す**。
         *
         * <p>終了は最終分塊一覧を確定させる操作なので、そのあとに届いた分塊が一覧を動かさない
         * ように、確定した範囲を DB に書く。旧い画面（録れた範囲を送らない）は 0 のままにする
         * （0 = 「最後の分塊まで届いたことは証明していない」）。</p>
         */
        Integer recordedLastSeq = checklist.expectedLastSeq() > 0 ? checklist.expectedLastSeq() : null;
        Integer recordedCount = recordedLastSeq;
        boolean forcedLoss = !checklist.complete();
        String lostSeqs = forcedLoss && !checklist.missingSeqs().isEmpty()
                ? checklist.missingSeqs().stream().map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(","))
                : null;
        if (recordMapper.markFinalized(recordId, durationSeconds, settings.retentionDays(snapshot),
                user.accountId(), claimedVersion, recordedLastSeq, recordedCount,
                manifest == null ? null : manifest.expectedEndSample(),
                checklist.complete(), lostSeqs) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        ClassroomModels.ChunkChecklist latest = checklist;
        // 最終まとめ（FINAL）の PENDING 行を作る（batC62 が拾う）。
        // ただし転写セグメントが 1 件も無いとき（音声が 1 つも送られずに終了した場合）は作らない:
        // ノート行の対象範囲は DDL の CK_CR_授業ノート_範囲 で「終了連番 >= 開始連番」なので、
        // 0 件で作ると制約違反になり、@Transactional のため終了そのものが失敗して
        // 記録が RECORDING のまま残ってしまう。
        Long finalNoteId = null;
        String notice = finalizeNotice;
        if (!settings.noteEnabled(snapshot)) {
            // AI 解析（batC62）が無効: 最終まとめの行も作らない（書き起こしだけ残す）
            notice = join(notice, "AI 解析（フェーズノート・最終まとめ）は現在オフのため、"
                    + "最終まとめは作成しませんでした。");
            log.info("classroom record ended with ai notes disabled. recordId={} durationSeconds={}",
                    recordId, durationSeconds);
        } else if (maxSeq < 1) {
            notice = join(notice, "書き起こしが無かったため、最終まとめは作成しませんでした。");
            log.info("classroom record ended without transcript. recordId={} durationSeconds={}",
                    recordId, durationSeconds);
        } else {
            ClassroomNoteEntity finalNote = new ClassroomNoteEntity();
            finalNote.setRecordId(recordId);
            finalNote.setKind(ClassroomModels.NOTE_FINAL);
            finalNote.setPhaseNo(null);
            finalNote.setStartSeq(1);
            finalNote.setEndSeq(maxSeq);
            finalNote.setStatus(ClassroomModels.NOTE_PENDING);
            finalNote.setCreatedBy(user.accountId());
            finalNote.setSourceCode("APP");
            noteMapper.insert(finalNote);
            finalNoteId = finalNote.getNoteId();
        }

        log.info("classroom record ended. recordId={} finalNoteId={} durationSeconds={}",
                recordId, finalNoteId, durationSeconds);
        /*
         * 再生用の 1 本の生成は**コミット後に背景で**始める。
         *
         * <p>ここで待つと、長い録音ほど終了の応答と DB のトランザクションを長時間占有してしまう。
         * 状態は記録ごとに残すので、画面は「音声は保存されている／再生用を作成中／作成に失敗」
         * を後から読める（{@link #assembly}）。</p>
         */
        scheduleAssemblyAfterCommit(recordId);
        /*
         * **失った範囲を値として返す**（一度きりの通知にしない）。
         *
         * <p>不完全なまま終えた回は、どの連番の音が残っていないかを詳細画面に出し続ける。
         * 「一部が保存できていません」だけでは、後から見た人に何が失われたか分からない。</p>
         */
        List<Integer> lossSeqs = force && !latest.complete() ? List.copyOf(latest.missingSeqs()) : List.of();
        return new ClassroomModels.EndResult(recordId, ClassroomModels.STATUS_STOPPED,
                statusLabel(ClassroomModels.STATUS_STOPPED), finalNoteId,
                finalNoteId == null ? null : ClassroomModels.noteRunPath(finalNoteId), notice,
                latest.complete(), latest.missingSeqs(), !lossSeqs.isEmpty(),
                latest.expectedLastSeq(), lossSeqs, lossSeqs.isEmpty() ? null : latest.reasonCode(),
                transcribeViewOf(recordMapper.findById(recordId)));
    }

    /**
     * 終了してよいかを**サーバー側で**確かめる（収尾と分塊）。
     *
     * @return 通すが知らせを出すべきこと（日本語。無ければ null）
     * @throws ConflictException まだやり直せる未完了がある（理由つき）
     */
    private String requireFinalizeComplete(ClassroomRecordEntity record, int maxSeq) {
        long recordId = record.getRecordId();
        // ストリーミング書き起こしを使っていない構成（分塊ごとの STT だけ・テスト）では収尾の状態が無い
        ClassroomSttStreamService.FinalizeStatus status =
                streamService == null ? null : streamService.finalizeStatus(recordId);
        String notice = null;
        List<String> terminal = new ArrayList<>();
        if (status != null) {
            List<String> incomplete = new ArrayList<>();
            for (ClassroomSttStreamService.SourceFinalizeStatus source : status.sources()) {
                if (source.completed()) {
                    continue;
                }
                String reason = "【" + source.label() + "】" + (source.reason() == null
                        ? source.statusLabel() : source.reason());
                if (source.retryable()) {
                    incomplete.add(reason);
                } else {
                    terminal.add(reason);
                }
            }
            if (!incomplete.isEmpty()) {
                throw new ConflictException("書き起こしの収尾（最後の確定文の取り込みと保存）が済んでいません。"
                        + String.join(" ", incomplete)
                        + "【続きをやり直す】を押してから、もう一度終了してください。");
            }
            notice = terminal.isEmpty() ? null
                    : "書き起こしを完了できなかった音源があります。" + String.join(" ", terminal);
            if (notice == null && !status.completed() && status.notice() != null) {
                notice = status.notice();
            }
        }
        return notice;
    }

    /**
     * 音声の分塊が**1 から連続してそろっているか**を確かめる（{@link #end} の①）。
     *
     * <p>「1 つ以上あるか」では足りない: 途中が欠けていても、最後の分塊が届いていなくても
     * 区別できず、**音は後から作り直せない**のに完了にしてしまう。ここでは</p>
     * <ol>
     *   <li>分塊の行が 1 から連続しているか（欠けている連番を集める）</li>
     *   <li>画面が宣言した最後の連番まで届いているか（最後の分塊の取りこぼしを見つける）</li>
     * </ol>
     * <p>を確かめる。**取り込み音声（mp3）・貼り付けだけ**の記録は分塊を持たない作りなので、
     * 分塊を理由に断らない（今までできていた操作をできなくしない）。</p>
     */
    private ClassroomModels.ChunkChecklist checkChunks(ClassroomRecordEntity record,
                                                       ClassroomModels.ChunkManifest manifest) {
        long recordId = record.getRecordId();
        List<ClassroomRecordingChunkEntity> chunks = chunkMapper.findByRecord(recordId);
        List<ClassroomRecordingChunkEntity> rows = chunks == null ? List.of() : chunks;
        boolean imported = record.getAudioName() != null
                && record.getAudioName().toLowerCase(Locale.ROOT).endsWith(".mp3");
        if (imported) {
            // 取り込み音声は分塊を持たない（1 本のファイルをそのまま配信する）
            return ClassroomModels.ChunkChecklist.ready(rows.size());
        }
        /*
         * 一覧そのものが矛盾していないかを**調べる前に**見る。矛盾した一覧を受け取ると、
         * 最後の分塊が届いていないのに「そろっている」と見てしまう。
         */
        if (manifest != null) {
            String contradiction = manifest.mismatch();
            if (contradiction != null) {
                return refuse(ClassroomModels.CHECK_MANIFEST_CONTRADICTION,
                        "音声の一覧が正しくありません（" + contradiction + "）。"
                                + "画面を開き直して、もう一度送ってください。",
                        rows.size(), 0, List.of(), List.of(), List.of(), List.of(),
                        manifest.expectedEndSample());
            }
        }
        /*
         * 行の並びではなく**連番の対応表**を作る。行があるだけでは足りない:
         * 実体が無い・大きさが記録と違う行は「その連番の音が無い」ので欠落として扱う。
         */
        java.util.Map<Integer, ClassroomRecordingChunkEntity> bySeq = new java.util.TreeMap<>();
        for (ClassroomRecordingChunkEntity row : rows) {
            if (row.getSeq() != null) {
                bySeq.put(row.getSeq(), row);
            }
        }
        if (bySeq.isEmpty()) {
            if (manifest != null && manifest.declaresRecordedRange()) {
                // 画面は「録れた」と言っているのに 1 つも届いていない（全部送り直す）
                List<Integer> all = new ArrayList<>();
                for (int seq = 1; seq <= manifest.expectedLastSeq(); seq += 1) {
                    all.add(seq);
                }
                return refuse(ClassroomModels.CHECK_MISSING,
                        "録音の音声（分塊）が 1 つも保存されていません。"
                                + "通信の状態を確かめて、録音画面からもう一度送ってから終了してください。",
                        0, manifest.expectedLastSeq(), all, List.of(), List.of(), List.of(),
                        manifest.expectedEndSample());
            }
            return refuse(ClassroomModels.CHECK_NO_CHUNKS,
                    "録音の音声（分塊）が 1 つも保存されていません。"
                            + "通信の状態を確かめて、録音画面からもう一度送ってから終了してください。",
                    0, 0, List.of(), List.of(), List.of(), List.of(),
                    manifest == null ? null : manifest.expectedEndSample());
        }
        int savedLast = bySeq.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        /*
         * **調べる範囲**を決める。
         *
         * <p>画面が「実際に録れた範囲」を送ってきたら、それが正（録れたのに送れていない分塊を
         * 見つけられる唯一の情報）。送ってこない旧い画面では、保存済みの範囲でしか調べられない
         * （＝最後の分塊まで届いたことは**証明できない**。expectedLastSeq=0 で返し、
         * 画面は「保証された完了」と読み替えない）。</p>
         */
        int expectedLast = manifest != null && manifest.declaresRecordedRange()
                ? manifest.expectedLastSeq() : savedLast;
        List<Integer> unrecoverable = manifest == null ? List.of() : manifest.unrecoverable();
        List<Integer> missing = new ArrayList<>();
        List<Integer> broken = new ArrayList<>();
        List<Integer> saved = new ArrayList<>();
        for (int seq = 1; seq <= expectedLast; seq += 1) {
            ClassroomRecordingChunkEntity row = bySeq.get(seq);
            if (row == null) {
                missing.add(seq);
                continue;
            }
            // **実体を確かめる**（行がそろっていても、音が無い・壊れていることがある）
            if (!fileIsIntact(record, row)) {
                broken.add(seq);
                continue;
            }
            saved.add(seq);
        }
        /*
         * 録れた範囲の**外**に保存されている分塊（例: 3 番までしか録れていないのに 4 番が在る）。
         * 「在るから使う」と勝手に足さない（一覧と実体の食い違いは直してもらう）。
         */
        List<Integer> extra = new ArrayList<>();
        for (Integer seq : bySeq.keySet()) {
            if (seq > expectedLast) {
                extra.add(seq);
            }
        }
        // 終わりの位置（画面が申告した録音の終わり）が、実際の分塊の終わりと食い違っていないか
        String endSampleMismatch = endSampleMismatchOf(bySeq, expectedLast, manifest);
        /*
         * 画面に返す「足りない連番」は、**音が残っていない連番の全部**（行が無い・実体が無い・
         * 一覧の外）をまとめたもの。種類（{@code reasonCode}）で「送り直せば直るのか」を出す。
         */
        List<Integer> lost = new ArrayList<>();
        for (List<Integer> group : List.of(missing, broken, extra)) {
            for (Integer seq : group) {
                if (!lost.contains(seq)) {
                    lost.add(seq);
                }
            }
        }
        if (unrecoverable != null) {
            for (Integer seq : unrecoverable) {
                if (!lost.contains(seq)) {
                    lost.add(seq);
                }
            }
        }
        java.util.Collections.sort(lost);
        if (lost.isEmpty() && endSampleMismatch == null) {
            // どの連番が保存できているかを返す（画面が「送れた」の取りこぼしを照合できる）
            return new ClassroomModels.ChunkChecklist(true, List.of(), rows.size(), expectedLast, null,
                    List.of(), List.of(), ClassroomModels.CHECK_OK,
                    manifest != null && manifest.declaresRecordedRange() ? expectedLast : 0,
                    saved, manifest == null ? null : manifest.expectedEndSample());
        }
        List<String> reasons = new ArrayList<>();
        if (!missing.isEmpty()) {
            reasons.add("足りない連番 " + missing);
        }
        if (!broken.isEmpty()) {
            reasons.add("音声の実体が無い・壊れている連番 " + broken);
        }
        if (!extra.isEmpty()) {
            reasons.add("録れた範囲の外に保存されている連番 " + extra);
        }
        if (endSampleMismatch != null) {
            reasons.add(endSampleMismatch);
        }
        // **利用者ができること**が違うので、種類を分ける（送り直せるのか、諦めるしかないのか）
        String code;
        if (!missing.isEmpty()) {
            code = ClassroomModels.CHECK_MISSING;
        } else if (!broken.isEmpty()) {
            code = ClassroomModels.CHECK_BROKEN;
        } else if (!extra.isEmpty()) {
            code = ClassroomModels.CHECK_EXTRA;
        } else {
            code = ClassroomModels.CHECK_END_SAMPLE_MISMATCH;
        }
        return refuse(code,
                "録音の音声（分塊）がそろっていません（" + String.join("・", reasons) + "）。",
                rows.size(), expectedLast, lost, broken, extra, saved,
                manifest == null ? null : manifest.expectedEndSample());
    }

    /** 断りの形を 1 か所で作る（欄の詰め方を間違えないため）。 */
    private static ClassroomModels.ChunkChecklist refuse(String code, String reason, int storedChunks,
                                                         int expectedLastSeq, List<Integer> missing,
                                                         List<Integer> broken, List<Integer> extra,
                                                         List<Integer> saved, Long endSample) {
        return new ClassroomModels.ChunkChecklist(false, missing, storedChunks, expectedLastSeq, reason,
                broken, extra, code,
                // 「録れた範囲」は画面が申告した値だけを返す（保存済みの最大で代用しない）
                expectedLastSeq, saved, endSample);
    }

    /** 分塊の行が指す実体が、記録したとおりにあるか（大きさまで見る）。 */
    private boolean fileIsIntact(ClassroomRecordEntity record, ClassroomRecordingChunkEntity row) {
        java.nio.file.Path path = storage.resolve(
                row.getStorageDir() == null ? record.getAudioPath() : row.getStorageDir(),
                row.getFileName());
        if (path == null || !java.nio.file.Files.isRegularFile(path)) {
            return false;
        }
        try {
            long size = java.nio.file.Files.size(path);
            if (size <= 0) {
                return false;
            }
            return row.getByteSize() == null || row.getByteSize() <= 0 || size == row.getByteSize();
        } catch (java.io.IOException cause) {
            return false;
        }
    }

    /**
     * 画面が申告した**録音の終わりの位置**（16kHz のサンプル数）と、実際の分塊の終わりを比べる。
     *
     * <p>単位は**統一時間軸のサンプル数**（16kHz）。許容差は**分塊 1 つぶん**（端数のため。
     * 画面は最後の `dataavailable` のあとに止めるので、最後の分塊の終わりとわずかにずれる）。</p>
     *
     * @return 食い違っていれば理由（日本語）。比べられない・合っていれば null
     */
    private String endSampleMismatchOf(java.util.Map<Integer, ClassroomRecordingChunkEntity> bySeq,
                                       int expectedLast, ClassroomModels.ChunkManifest manifest) {
        if (manifest == null || manifest.expectedEndSample() == null || expectedLast < 1) {
            return null;
        }
        ClassroomRecordingChunkEntity last = bySeq.get(expectedLast);
        if (last == null || last.getEndOffsetSeconds() == null) {
            return null;
        }
        long declared = manifest.expectedEndSample();
        long actual = last.getEndOffsetSeconds()
                .multiply(java.math.BigDecimal.valueOf(ClassroomModels.TIMELINE_SAMPLE_RATE)).longValue();
        long tolerance = (long) (END_SAMPLE_TOLERANCE_SECONDS * ClassroomModels.TIMELINE_SAMPLE_RATE);
        if (Math.abs(declared - actual) > tolerance) {
            return "録音の終わりの位置（" + declared + " サンプル）が、最後の分塊の終わり（"
                    + actual + " サンプル）と合いません";
        }
        return null;
    }

    /** 知らせを足す（空は無視して連結する）。 */
    private static String join(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        return first + " " + second;
    }

    // ------------------------------------------------------------------ 分塊の状態

    /**
     * その記録に保存済みの分塊（**画面が「次に送る連番」を知るための入口**）。
     *
     * <p>画面は以前、開き直したときの続きの連番を**転写セグメントの最大連番**から作っていた。
     * 文の数と分塊の数は違うので、続きの番号がずれる（同じ連番を送り直す・飛ぶ）。
     * ここでは分塊表の連番だけを返す。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public ClassroomModels.ChunkListResult chunks(UserPrincipal user, long recordId, int afterSeq) {
        ClassroomRecordEntity record = requireVisible(user, recordId);
        List<ClassroomModels.ChunkView> items = chunkMapper.findByRecordAfter(recordId, afterSeq).stream()
                .map(ClassroomServiceImpl::toChunkView).toList();
        int maxSeq = maxChunkSeqOf(recordId);
        BigDecimal recorded = chunkMapper.maxEndOffsetSeconds(recordId);
        // 終了してよいかの下見も返す（画面を開き直してもサーバーから同じ状態が取れる）
        ClassroomModels.ChunkChecklist checklist = checkChunks(record, null);
        return new ClassroomModels.ChunkListResult(items, chunkMapper.countByRecord(recordId),
                maxSeq, maxSeq + 1, chunkMapper.totalBytes(recordId),
                recorded == null ? null : recorded.doubleValue(), checklist);
    }

    private static ClassroomModels.ChunkView toChunkView(ClassroomRecordingChunkEntity chunk) {
        return new ClassroomModels.ChunkView(chunk.getSeq(),
                chunk.getByteSize() == null ? 0L : chunk.getByteSize(),
                toDouble(chunk.getStartOffsetSeconds()), toDouble(chunk.getEndOffsetSeconds()),
                chunk.getMime(), Boolean.TRUE.equals(chunk.getContainerHead()),
                chunk.getProcessingStatus(),
                chunk.getSegmentCount() == null ? 0 : chunk.getSegmentCount(),
                iso(chunk.getCreatedAt()));
    }

    @Override
    public ClassroomModels.AssemblyView assembly(UserPrincipal user, long recordId) {
        ClassroomRecordEntity record = requireVisible(user, recordId);
        return assemblyViewOf(record);
    }

    @Override
    public ClassroomModels.AssemblyView retryAssembly(UserPrincipal user, long recordId) {
        ClassroomRecordEntity record = requireOwner(user, recordId);
        /*
         * 手で頼まれた回は**必ず作る**。忘れてから走らせるのは、前回の状態（READY・FAILED）が
         * 残っていると「作り直す必要が無い」と見て何もしないため。
         *
         * <p>ただし**HTTP の応答を長く待たせない**: 分塊が多い回は ffmpeg が数十秒かかる。
         * 受け付けだけして（QUEUED を残す）、実際の結合は背景で走らせ、画面は状態を見に来る。</p>
         */
        assembler.forget(recordId);
        if (!assembler.statusIsCurrent(record, chunkMapper.findByRecord(recordId))) {
            persistAssemblyState(recordId, ClassroomAssembly.of(ClassroomAssembly.State.QUEUED,
                    false, 0, null, List.of(), null), chunkDigestOf(chunkMapper.findByRecord(recordId)));
            runAssemblyInBackground(recordId);
            // 受け付けた直後の状態を返す（画面はポーリングで READY／FAILED を読む）
            return ClassroomModels.AssemblyView.of(ClassroomAssembly.State.QUEUED.name(), false,
                    chunkMapper.countByRecord(recordId), null, List.of(),
                    "再生用の音声の作成を受け付けました。");
        }
        assembler.restore(recordId, ClassroomAssembly.of(ClassroomAssembly.State.READY, true, 0,
                null, List.of(), null));
        return assemblyViewOf(record);
    }

    /** 結合の状態を画面の形にする（**分塊の数は必ず入れる**＝音が残っていることの根拠）。 */
    private ClassroomModels.AssemblyView assemblyViewOf(ClassroomRecordEntity record) {
        int stored = chunkMapper.countByRecord(record.getRecordId());
        if (assembler == null) {
            return ClassroomModels.AssemblyView.of(
                    ClassroomAssembly.State.NONE.name(), false, stored, null, List.of(), null);
        }
        ClassroomAssembly status = assembler.statusOf(record.getRecordId());
        if (status.state() == ClassroomAssembly.State.NONE) {
            // メモリに何も無い（起動直後・再起動後）: **DB の状態を復帰する**
            status = restoreAssemblyFromDb(record, stored);
        }
        return ClassroomModels.AssemblyView.of(status.stateCode(), status.complete(), stored,
                status.durationSeconds(), status.missingSeqs(), status.reason());
    }

    /**
     * 書き起こし（認識）の収尾の状態を画面の形にする。
     *
     * <p><b>音声の欠落とは別の軸</b>。見る順:</p>
     * <ol>
     *   <li>いまのストリーミングの状態（このプロセスが覚えている**最新**）。収尾が済んでいれば
     *       それを使う。</li>
     *   <li>済んでいなければ**記録に残した結果**（`認識収尾状態`。別のプロセスで終えた回・
     *       再起動した回）。</li>
     *   <li>どちらも無ければ `UNKNOWN`（**完全とは見なさない**。確認できないだけ）。</li>
     * </ol>
     */
    private ClassroomModels.TranscribeView transcribeViewOf(ClassroomRecordEntity record) {
        if (record == null) {
            return new ClassroomModels.TranscribeView("UNKNOWN", false, false,
                    "書き起こしの状態を確認できませんでした。", List.of());
        }
        ClassroomSttStreamService.FinalizeStatus live = streamService == null
                ? null : streamService.finalizeStatus(record.getRecordId());
        if (live != null && live.completed()) {
            return transcribeViewOf(live);
        }
        String stored = record.getTranscribeStatus();
        if (stored == null) {
            if (live != null && live.audioReceived() && live.retryable()) {
                // 収尾がまだ途中（やり直せる）。ここは「進行中」として出す
                return new ClassroomModels.TranscribeView("RUNNING", false, true,
                        live.notice(), List.of());
            }
            // 記録が無い（改修前の記録・音声を送っていない回）: **確認できない**として出す
            boolean audio = live != null && live.audioReceived();
            return new ClassroomModels.TranscribeView(audio ? "UNKNOWN" : "NO_AUDIO", !audio,
                    audio, audio ? "書き起こしの収尾の結果が残っていません（確認できません）。" : null,
                    List.of());
        }
        boolean complete = Boolean.TRUE.equals(record.getTranscribeComplete());
        // 「記録に残した状態」が完全と言っているときだけ完全として出す（確認できないときは false）
        return new ClassroomModels.TranscribeView(stored, complete,
                "RUNNING".equals(stored), record.getTranscribeReason(),
                transcribeSourcesOf(record.getTranscribeSources()));
    }

    /** いまのストリーミングの状態から画面の形にする（音源ごとの結果つき）。 */
    private static ClassroomModels.TranscribeView transcribeViewOf(
            ClassroomSttStreamService.FinalizeStatus status) {
        List<ClassroomModels.TranscribeSourceView> sources = status.sources().stream()
                .map(item -> new ClassroomModels.TranscribeSourceView(item.source(), item.label(),
                        item.status(), item.completed(), item.retryable(), item.savedCount(),
                        item.pendingCount(), item.reason()))
                .toList();
        boolean incomplete = status.sources().stream().anyMatch(item -> !item.completed() && !item.retryable());
        boolean complete = status.completed()
                && status.sources().stream().allMatch(ClassroomSttStreamService.SourceFinalizeStatus::completed);
        String overall = incomplete ? "INCOMPLETE"
                : complete ? "COMPLETE"
                : status.retryable() ? "RUNNING"
                : !status.audioReceived() ? "NO_AUDIO"
                : "UNKNOWN";
        return new ClassroomModels.TranscribeView(overall, complete, status.retryable(),
                status.notice(), sources);
    }

    /**
     * 記録に残した音源ごとの結果を読み直す（JSON を素朴に読む）。
     *
     * <p>読めないときは空を返す（画面は「音源ごとの内訳は残っていない」として出す）。
     * **完全とは見なさない**判断は `認識完備` の値で行うので、ここが空でも嘘にはならない。</p>
     */
    private static List<ClassroomModels.TranscribeSourceView> transcribeSourcesOf(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<ClassroomModels.TranscribeSourceView> out = new ArrayList<>();
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            for (com.fasterxml.jackson.databind.JsonNode node : root) {
                out.add(new ClassroomModels.TranscribeSourceView(
                        node.path("source").asText(""),
                        node.path("label").asText(""),
                        node.path("status").asText(""),
                        node.path("completed").asBoolean(false),
                        node.path("retryable").asBoolean(false),
                        node.path("savedCount").asInt(0),
                        node.path("pendingCount").asInt(0),
                        node.hasNonNull("reason") ? node.get("reason").asText(null) : null));
            }
        } catch (Exception cause) {
            return List.of();
        }
        return out;
    }

    /**
     * DB に残した結合の状態から、いまの状態を組み立て直す（**再起動後も「作成中」で止まらない**）。
     *
     * <p>見る順:</p>
     * <ol>
     *   <li>DB に状態が無い（この改修より前の記録）: 分塊から**作り直せるかだけ**を見て、
     *       作れるなら「まだ作っていない」、作れないなら「欠落」を返す。</li>
     *   <li>`READY` でも、**いまの分塊と出力が食い違っていれば** READY とは言わない
     *       （内容の要約＝ダイジェストを比べ、ファイルの実在と大きさも見る）。</li>
     *   <li>`PROCESSING` が残っていて、**このプロセスが始めたのではない**なら
     *       「途中で止まった」＝やり直せる失敗にする（永久に「作成中」を出さない）。</li>
     *   <li>作れるのに `NOT_STARTED`／`QUEUED` のままなら、**受け付け直す**（やり直しの入口を作る）。</li>
     * </ol>
     */
    private ClassroomAssembly restoreAssemblyFromDb(ClassroomRecordEntity record, int storedChunks) {
        List<ClassroomRecordingChunkEntity> rows = chunkMapper.findByRecord(record.getRecordId());
        String digest = chunkDigestOf(rows);
        boolean buildable = assembler.statusIsCurrent(record, rows)
                || ClassroomRecordingSessionPlanner.plan(storage, record.getRecordId(),
                        record.getAudioPath(), rows).complete();
        String state = record.getAssemblyState();
        if (state == null) {
            ClassroomAssembly none = ClassroomAssembly.none();
            assembler.restore(record.getRecordId(), none);
            return none;
        }
        ClassroomAssembly restored = switch (state) {
            case "READY" -> {
                boolean outputMatches = digest != null && digest.equals(record.getAssemblyDigest())
                        && outputFileIsIntact(record);
                yield outputMatches
                        ? ClassroomAssembly.of(ClassroomAssembly.State.READY, true, 0,
                                toDouble(record.getAssemblyDuration()), List.of(), null)
                        : ClassroomAssembly.of(ClassroomAssembly.State.FAILED, false, 0, null, List.of(),
                                "再生用の音声を作り直す必要があります（結合したあとに音声が変わっています）。"
                                        + "【再試行】で作り直せます。");
            }
            case "PROCESSING" -> assembler.isClaimedHere(record.getRecordId())
                    ? ClassroomAssembly.of(ClassroomAssembly.State.PROCESSING, false, 0, null, List.of(),
                            "再生用の音声を作成しています…")
                    : ClassroomAssembly.of(ClassroomAssembly.State.FAILED, false, 0, null, List.of(),
                            "再生用の音声の作成が途中で止まりました（サーバーが再起動しました）。"
                                    + "【再試行】で作り直せます。");
            case "QUEUED" -> ClassroomAssembly.of(ClassroomAssembly.State.QUEUED, false, 0, null, List.of(),
                    "再生用の音声の作成を受け付けました。");
            case "INCOMPLETE" -> ClassroomAssembly.of(ClassroomAssembly.State.INCOMPLETE, false,
                    storedChunks, null, missingSeqsOf(record),
                    record.getAssemblyReason() == null
                            ? "音声が欠けているため、再生用の音声を作りません（音は分塊として残っています）。"
                            : record.getAssemblyReason());
            case "FAILED" -> ClassroomAssembly.of(ClassroomAssembly.State.FAILED, false, 0, null, List.of(),
                    record.getAssemblyReason() == null
                            ? "再生用の音声を作れませんでした。【再試行】で作り直せます。"
                            : record.getAssemblyReason());
            default -> ClassroomAssembly.none();
        };
        if (restored.state() == ClassroomAssembly.State.NONE && buildable) {
            // 「まだ作っていない」だけ。作る入口は終了後の背景か【再試行】にある
            restored = ClassroomAssembly.none();
        }
        assembler.restore(record.getRecordId(), restored);
        return restored;
    }

    /** 記録が指す再生用の 1 本が実在し、大きさも記録どおりか。 */
    private boolean outputFileIsIntact(ClassroomRecordEntity record) {
        java.nio.file.Path path = storage.resolve(record.getAudioPath(), record.getAudioName());
        if (path == null || !java.nio.file.Files.isRegularFile(path)) {
            return false;
        }
        try {
            long size = java.nio.file.Files.size(path);
            return record.getAudioSize() == null || record.getAudioSize() <= 0 || size > 0;
        } catch (java.io.IOException cause) {
            return false;
        }
    }

    /** 欠落として残した連番（カンマ区切り）を読み直す。 */
    private static List<Integer> missingSeqsOf(ClassroomRecordEntity record) {
        String raw = record.getLostSeqs();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Integer> seqs = new ArrayList<>();
        for (String part : raw.split(",")) {
            try {
                seqs.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // 壊れた値は無視する（画面は「欠落がある」ことだけを出す）
            }
        }
        return seqs;
    }

    /**
     * 分塊の**内容の要約**（連番・バイト数・チェックサムから作る SHA-256）。
     *
     * <p>「この 1 本は、いまある分塊の全部から作られているか」を**時刻ではなく中身**で
     * 判断するために使う（ファイルの更新時刻だけでは、差し替えや巻き戻しを見分けられない）。</p>
     */
    private static String chunkDigestOf(List<ClassroomRecordingChunkEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        List<ClassroomRecordingChunkEntity> sorted = new ArrayList<>(rows);
        sorted.sort(java.util.Comparator.comparingInt(ClassroomRecordingChunkEntity::getSeq));
        StringBuilder builder = new StringBuilder();
        for (ClassroomRecordingChunkEntity row : sorted) {
            builder.append(row.getSeq()).append(':')
                    .append(row.getByteSize() == null ? 0 : row.getByteSize()).append(':')
                    .append(row.getChecksum() == null ? "" : row.getChecksum()).append('\n');
        }
        return sha256(builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 結合の状態を DB に書く（書けなくても処理は続ける。**画面の状態が少し古くなるだけ**）。 */
    private void persistAssemblyState(long recordId, ClassroomAssembly state, String digest) {
        try {
            recordMapper.updateAssemblyState(recordId, state.stateCode(), digest,
                    state.durationSeconds() == null ? null
                            : java.math.BigDecimal.valueOf(state.durationSeconds()),
                    state.reason() == null ? null
                            : state.reason().substring(0, Math.min(500, state.reason().length())));
        } catch (RuntimeException cause) {
            log.warn("結合の状態を保存できませんでした（処理は続けます）。recordId={}", recordId, cause);
        }
    }

    /**
     * 結合（再生用の 1 本）を**コミット後に背景で**走らせる。
     *
     * <p>トランザクションの途中で走らせると、長い録音ほど DB の接続と終了の応答を占有する。
     * コミット後に回し、記録ごとに直列化して走らせる（結果は {@link #assembly} で読める）。</p>
     */
    private void scheduleAssemblyAfterCommit(long recordId) {
        Runnable start = () -> runAssemblyInBackground(recordId);
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            start.run();
                        }
                    });
            return;
        }
        start.run();
    }

    /** 背景で 1 本を組立てる（同じ記録の組立ては直列。失敗しても状態に残す）。 */
    private void runAssemblyInBackground(long recordId) {
        Runnable task = () -> {
            ClassroomRecordEntity fresh = recordMapper.findById(recordId);
            if (fresh != null) {
                assembleQuietly(fresh);
            }
        };
        try {
            backgroundRunner.accept(task);
        } catch (RuntimeException cause) {
            // 背景の仕組みが使えない環境（古い JVM・停止中の実行器）: その場で走らせる（結果は同じ）
            log.debug("背景で走らせられないため、その場で組立てます。recordId={}", recordId);
            task.run();
        }
    }

    /**
     * 分塊表の入口を差し替える（**試験で待ち合わせるため**。本番は Spring が入れたもの）。
     *
     * <p>「分塊を受け入れているトランザクションのあいだ、収尾が待つ」ことを確かめるのに使う
     * （行ロックが効いているかの決定的な検証）。</p>
     */
    void setChunkMapperForTest(ClassroomRecordingChunkMapper mapper) {
        if (mapper != null) {
            this.chunkMapper = mapper;
        }
    }

    /**
     * 背景の実行器を差し替える（**試験で待ち合わせるため**。本番は仮想スレッド）。
     *
     * <p>既定は「仮想スレッドで走らせる」。試験では「その場で走らせる」に替えて、
     * 組立てが終わった状態から確かめる（別スレッドのまま確かめると、結果が出る前に検査して
     * しまう＝不安定な試験になる）。</p>
     */
    void setBackgroundRunner(java.util.function.Consumer<Runnable> runner) {
        this.backgroundRunner = runner == null ? DEFAULT_BACKGROUND_RUNNER : runner;
    }

    /** 仮想スレッドで走らせる（既定。使えなければその場で走らせる）。 */
    private static final java.util.function.Consumer<Runnable> DEFAULT_BACKGROUND_RUNNER = task -> {
        try {
            Thread.startVirtualThread(task);
        } catch (RuntimeException cause) {
            task.run();
        }
    };

    private volatile java.util.function.Consumer<Runnable> backgroundRunner = DEFAULT_BACKGROUND_RUNNER;

    /**
     * 分塊があれば再生用の 1 本を組立て直す（理由はログに残す。例外は投げない）。
     *
     * <p>**DB の行だけ**を正体にする（置き場のファイルを数えない＝トランザクションが失敗して
     * 残った孤立ファイルを音として混ぜない）。欠落があれば結合せず、状態を残す。</p>
     */
    private void assembleQuietly(ClassroomRecordEntity record) {
        if (assembler == null) {
            return;
        }
        long recordId = record.getRecordId();
        try {
            List<ClassroomRecordingChunkEntity> rows = chunkMapper.findByRecord(recordId);
            String digest = chunkDigestOf(rows);
            /*
             * **始める前に「受け付けた」を残す**。
             *
             * <p>プロセスが落ちても DB に痕跡が残り、次の起動が「途中で止まった」と判断できる
             * （メモリだけだと、落ちた瞬間に「まだ何もしていない」に見えて永久に待たされる）。
             * 実際に走り出したら {@link ClassroomAssembly.State#PROCESSING}、終わったら
             * READY／FAILED／INCOMPLETE を書く。</p>
             */
            persistAssemblyState(recordId, ClassroomAssembly.of(ClassroomAssembly.State.QUEUED,
                    false, 0, null, List.of(), null), digest);
            assembler.assembleIfNeeded(record, rows);
            /*
             * 走らなかった（既に新しい）ときは、**受け付けた印を戻す**。
             * 「作成中」のまま残すと、画面が永久に待ってしまう。
             */
            if (assembler.statusOf(recordId).state() == ClassroomAssembly.State.NONE) {
                persistAssemblyState(recordId, ClassroomAssembly.of(ClassroomAssembly.State.READY, true, 0,
                        null, List.of(), null), digest);
                return;
            }
            persistAssemblyState(recordId, assembler.statusOf(recordId), digest);
        } catch (RuntimeException cause) {
            log.warn("授業録音の組立てに失敗しました（分塊は残っています）。recordId={}",
                    recordId, cause);
            // 失敗を DB に残す（画面が「作成中」のままにならない）
            persistAssemblyState(recordId, ClassroomAssembly.of(ClassroomAssembly.State.FAILED, false,
                    0, null, List.of(), "再生用の音声を作れませんでした。【再試行】で作り直せます。"),
                    null);
        }
    }

    @Override
    public int collectOrphanChunks(long recordId) {
        ClassroomRecordEntity record = recordMapper.findById(recordId);
        if (record == null || record.getAudioPath() == null) {
            return 0;
        }
        java.util.Set<String> referenced = new java.util.HashSet<>();
        for (ClassroomRecordingChunkEntity chunk : chunkMapper.findByRecord(recordId)) {
            if (chunk.getFileName() != null) {
                referenced.add(chunk.getFileName());
            }
        }
        // 記録の再生用の 1 本も「引用されている」扱い（消してはいけない）
        if (record.getAudioName() != null) {
            referenced.add(record.getAudioName());
        }
        return storage.collectOrphanChunks(record.getAudioPath(), recordId, referenced,
                ORPHAN_MIN_AGE_MILLIS);
    }

    /** 孤立ファイルを消してよいと見なすまでの時間（書いた直後の実体を消さないため）。 */
    static final long ORPHAN_MIN_AGE_MILLIS = 60L * 60L * 1000L;

    // ------------------------------------------------------------------ 配信

    @Override
    @Transactional(readOnly = true)
    public AudioFile audio(UserPrincipal user, long recordId) {
        ClassroomRecordEntity record = requireVisible(user, recordId);
        if (record.getAudioPath() == null || record.getAudioName() == null) {
            throw ClassroomApiException.notFound("録音が見つかりません。");
        }
        /*
         * 保存は分塊ごとのファイルなので、再生のときに**連番順に 1 本へ組み立てる**。
         * 分塊が 1 つも無い（改修前の録音・取り込み）ときは何もしない＝今までどおり 1 本を配信する。
         * 既に新しい 1 本があれば作り直さない（毎回の再生で作り直さない）。
         */
        assembleQuietly(record);
        java.nio.file.Path path = storage.resolve(record.getAudioPath(), record.getAudioName());
        if (path == null || !java.nio.file.Files.isRegularFile(path)) {
            throw ClassroomApiException.notFound("録音が見つかりません。");
        }
        String contentType = record.getAudioMime() == null || record.getAudioMime().isBlank()
                ? storage.mimeOf(record.getAudioName()) : record.getAudioMime();
        return new AudioFile(path, contentType, record.getAudioName());
    }

    // ------------------------------------------------------------------ 削除

    @Override
    @Transactional
    public ClassroomModels.DeleteResult delete(UserPrincipal user, long recordId) {
        ClassroomRecordEntity record = requireVisible(user, recordId);
        if (record.getCreatedBy() == null || !record.getCreatedBy().equals(user.accountId())) {
            // 保護者・管理者は閲覧できるが、他人の録音は消せない（設計 §10）
            throw ClassroomApiException.forbidden("この録音は自分が作成したものではないため削除できません。");
        }
        boolean deletedAudio = record.getAudioPath() == null || record.getAudioName() == null
                ? false : storage.delete(record.getAudioPath(), record.getAudioName());
        /*
         * 分塊のファイルも消す。**行が指す実体だけ**を消す（置き場は複数の記録で共有し得るので、
         * ディレクトリごと消すと他の記録の音声まで巻き込む）。分塊の行は記録の削除（FK CASCADE）で消える。
         */
        for (ClassroomRecordingChunkEntity chunk : chunkMapper.findByRecord(recordId)) {
            deletedAudio |= storage.delete(chunk.getStorageDir(), chunk.getFileName());
        }
        if (record.getAudioPath() != null) {
            storage.removeDirectoryIfEmpty(record.getAudioPath());
        }
        recordMapper.delete(recordId, user.accountId());
        log.info("classroom record deleted. recordId={} deletedAudio={}", recordId, deletedAudio);
        return new ClassroomModels.DeleteResult(recordId, deletedAudio);
    }

    // -------------------------------------------------------------------- 内部

    /** トリガー評価。成立したら PENDING のフェーズノートを作り、その ID を返す（成立しなければ null）。 */
    private Long evaluateTrigger(long recordId, long accountId, String newText, ClassroomAiSettings.Snapshot snapshot) {
        // AI 解析（batC61）が無効ならフェーズノートを作らない＝バッチを呼ぶ入口を残さない
        if (!settings.noteEnabled(snapshot)) {
            return null;
        }
        ClassroomNoteEntity lastPhase = noteMapper.findLastPhase(recordId);
        LocalDateTime now = LocalDateTime.now();
        if (lastPhase == null) {
            return insertPhaseNote(recordId, accountId, 1, 1, maxSeqOf(recordId));
        }
        long elapsedMinutes = ChronoUnit.MINUTES.between(lastPhase.getCreatedAt().toLocalDateTime(), now);
        int cooldown = settings.triggerCooldownMinutes(snapshot);
        if (elapsedMinutes < cooldown) {
            return null;
        }
        boolean byInterval = elapsedMinutes >= settings.triggerIntervalMinutes(snapshot);
        boolean byChars = totalCharsSince(lastPhase) >= settings.triggerMinChars(snapshot);
        boolean byKeyword = containsKeyword(newText, settings.triggerKeywords(snapshot));
        if (byInterval || byChars || byKeyword) {
            int nextPhaseNo = lastPhase.getPhaseNo() == null ? 1 : lastPhase.getPhaseNo() + 1;
            int startSeq = lastPhase.getEndSeq() == null ? 1 : lastPhase.getEndSeq() + 1;
            return insertPhaseNote(recordId, accountId, nextPhaseNo, startSeq, maxSeqOf(recordId));
        }
        return null;
    }

    /** 前回フェーズ以降の転写文字数（対象範囲のセグメント文字数を足す）。 */
    private int totalCharsSince(ClassroomNoteEntity lastPhase) {
        int start = lastPhase.getEndSeq() == null ? 1 : lastPhase.getEndSeq() + 1;
        return segmentMapper.findByRecordAfter(lastPhase.getRecordId(), start - 1).stream()
                .mapToInt(segment -> segment.getText() == null ? 0 : segment.getText().length()).sum();
    }

    private Long insertPhaseNote(long recordId, long accountId, int phaseNo, int startSeq, int endSeq) {
        ClassroomNoteEntity note = new ClassroomNoteEntity();
        note.setRecordId(recordId);
        note.setKind(ClassroomModels.NOTE_PHASE);
        note.setPhaseNo(phaseNo);
        note.setStartSeq(startSeq);
        note.setEndSeq(endSeq);
        note.setStatus(ClassroomModels.NOTE_PENDING);
        note.setCreatedBy(accountId);
        note.setSourceCode("APP");
        noteMapper.insert(note);
        return note.getNoteId();
    }

    private int maxSeqOf(long recordId) {
        Integer max = segmentMapper.maxSeq(recordId);
        return max == null ? 0 : max;
    }

    /**
     * 録音時間（秒）。
     *
     * <p>第一に**保存した分塊が示す実際の位置**（画面が測った経過秒。無音の授業でも正しい）。
     * 分塊が無い（改修前の録音・取り込み）ときだけ、開始時刻からの経過と
     * 転写セグメントの連番（旧データの互換）に落とす。</p>
     */
    private int durationSecondsOf(ClassroomRecordEntity record, ClassroomAiSettings.Snapshot snapshot) {
        BigDecimal recorded = chunkMapper.maxEndOffsetSeconds(record.getRecordId());
        if (recorded != null && recorded.signum() > 0) {
            return recorded.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        }
        if (record.getStartTime() != null) {
            long seconds = ChronoUnit.SECONDS.between(record.getStartTime().toLocalDateTime(),
                    LocalDateTime.now());
            return (int) Math.max(0, seconds);
        }
        return maxSeqOf(record.getRecordId()) * settings.chunkSeconds(snapshot);
    }

    private static boolean containsKeyword(String text, List<String> keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String joinText(List<ClassroomSttClient.Segment> segments) {
        StringBuilder builder = new StringBuilder();
        for (ClassroomSttClient.Segment segment : segments) {
            if (segment.text() != null && !segment.text().isBlank()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(segment.text().trim());
            }
        }
        return builder.toString();
    }

    /** 取り込みの上限（利用者の指示: mp3・90 分・文字起こし 50 万字）。 */
    private static final int IMPORT_MAX_MINUTES = 90;
    private static final int IMPORT_TEXT_MAX = 500_000;

    @Override
    @Transactional(readOnly = true)
    public long requireSttAudioAccountId(UserPrincipal user, long recordId) {
        return requireSttAccountId(user, recordId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public long requireSttFinishAccountId(UserPrincipal user, long recordId) {
        return requireSttAccountId(user, recordId, true);
    }

    /**
     * ストリーミング書き起こしの入口の確認（**HTTP と常時接続で同じ 1 か所を使う**）。
     *
     * <p>所有は HTTP の STT 入口と同じ道（{@link #requireOwner}）で見る＝他人の記録は
     * 404（存在を漏らさない）、見えるが操作できない記録は 403。そのうえで**状態**を見る。
     * 状態を入口で見ないと、停止・完了した記録へ音を流し込み、確定済みの書き起こしが
     * あとから書き換わる（画面が待たずに送った場合に起きる）。</p>
     *
     * @param finishing 収尾（finish）かどうか。収尾だけは停止直後の猶予も受け付ける
     */
    private long requireSttAccountId(UserPrincipal user, long recordId, boolean finishing) {
        ClassroomRecordEntity record = requireOwner(user, recordId);
        if (!ClassroomModels.STATUS_RECORDING.equals(record.getStatus())
                && !(finishing && isLateChunkAllowed(record))) {
            throw new ConflictException("この録音は" + statusLabel(record.getStatus()) + "のため、"
                    + (finishing ? "書き起こしの終了" : "書き起こしの音声") + "を受け取れません。");
        }
        return record.getCreatedBy();
    }

    @Override
    @Transactional
    public ClassroomModels.ChunkUploadResult importSource(UserPrincipal user, long recordId,
                                                          MultipartFile audioFile, String text,
                                                          Integer durationSeconds) {
        ClassroomRecordEntity record = requireOwner(user, recordId);
        ClassroomAiSettings.Snapshot snapshot = settings.load();
        requireEnabled(snapshot);
        boolean hasAudio = audioFile != null && !audioFile.isEmpty();
        String pasted = text == null ? "" : text.trim();
        if (!hasAudio && pasted.isEmpty()) {
            throw ClassroomApiException.invalid("音声ファイルを選ぶか、文字起こしを貼り付けてください。");
        }
        if (pasted.length() > IMPORT_TEXT_MAX) {
            throw ClassroomApiException.invalid("文字起こしは " + IMPORT_TEXT_MAX + " 文字までです。");
        }
        byte[] audioBytes = null;
        if (hasAudio) {
            String mime = audioFile.getContentType() == null ? "" : audioFile.getContentType().toLowerCase();
            String name = audioFile.getOriginalFilename() == null ? "" : audioFile.getOriginalFilename().toLowerCase();
            if (!mime.contains("mpeg") && !mime.contains("mp3") && !name.endsWith(".mp3")) {
                throw ClassroomApiException.invalid("音声ファイルは mp3 を選んでください。");
            }
            int seconds = durationSeconds == null ? 0 : Math.max(0, durationSeconds);
            if (seconds > IMPORT_MAX_MINUTES * 60) {
                throw ClassroomApiException.invalid("音声ファイルは " + IMPORT_MAX_MINUTES + " 分までです。");
            }
            // 名前と種類だけでなく**中身**も見る（拡張子だけ mp3 のファイルを保存すると、
            // 書き起こしも再生もできない音声が残り、原因が分からない）
            audioBytes = readBytes(audioFile);
            if (!looksLikeMp3(audioBytes)) {
                throw ClassroomApiException.invalid(
                        "音声ファイルの中身が mp3 ではありません（別の形式か、壊れています）。");
            }
        }

        // 音声は保存する（再生できるように）。文字起こしは STT か貼り付けから作る
        String sttText = pasted;
        if (hasAudio) {
            byte[] bytes = audioBytes;
            ClassroomRecordingStorage.StoredRecording stored =
                    storage.newRecording(user.accountId(), audioFile.getContentType());
            recordMapper.updateAudio(recordId, stored.relativeDir(), stored.fileName(), stored.mime(),
                    bytes.length, user.accountId());
            storage.append(stored.relativeDir(), stored.fileName(), bytes);
            if (!settings.browserStt(snapshot)) {
                ClassroomAiSettings.SttConnection connection = settings.resolveStt(snapshot);
                ClassroomSttClient.SttResponse response = sttClient.transcribe(new ClassroomSttClient.SttRequest(
                        connection.provider(), connection.model(), connection.url(), connection.apiKey(),
                        settings.sttLanguageCode(snapshot, record.getLanguageMode()),
                        settings.sttAlternativeLanguageCode(record.getLanguageMode()),
                        bytes, audioFile.getContentType(), settings.sttTimeoutSeconds(snapshot) * 10));
                if (!response.isSuccess()) {
                    throw new ConflictException(response.errorMessage() == null
                            ? "音声ファイルの書き起こしに失敗しました。" : response.errorMessage());
                }
                sttText = joinText(response.segments());
            }
        }
        if (sttText.isBlank()) {
            return new ClassroomModels.ChunkUploadResult(recordId, 0, 1, maxChunkSeqOf(recordId) + 1,
                    List.of(), null, false, record.getStatus(), null);
        }

        // 貼り付け・認識結果を**文ごとに分けて**セグメントにする（時間は順番に割り当てる）
        int seq = maxSeqOf(recordId);
        int chunkSeconds = settings.chunkSeconds(snapshot);
        List<ClassroomModels.SegmentView> appended = new ArrayList<>();
        for (String part : splitTranscript(sttText)) {
            seq += 1;
            ClassroomSegmentEntity segment = new ClassroomSegmentEntity();
            segment.setRecordId(recordId);
            segment.setSeq(seq);
            segment.setStartOffsetSeconds(BigDecimal.valueOf((long) (seq - 1) * chunkSeconds));
            segment.setEndOffsetSeconds(BigDecimal.valueOf((long) seq * chunkSeconds));
            segment.setSpeaker(SPEAKER_LECTURE);
            segment.setText(part);
            segment.setLanguage(settings.sttLanguageCode(snapshot, record.getLanguageMode()));
            segmentMapper.insert(segment);
            recordMapper.addTranscribedChars(recordId, part.length());
            appended.add(toSegmentView(segment));
        }
        log.info("classroom source imported. recordId={} audio={} segments={} chars={}",
                recordId, hasAudio, appended.size(), sttText.length());
        return new ClassroomModels.ChunkUploadResult(recordId, seq, seq + 1,
                maxChunkSeqOf(recordId) + 1, appended, null, false, record.getStatus(), null);
    }

    /** 貼り付け・認識結果を文（行）ごとに分ける（空行は捨てる）。 */
    private static List<String> splitTranscript(String text) {
        List<String> parts = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String value = line.trim();
            if (!value.isEmpty()) parts.add(value);
        }
        if (parts.size() <= 1) {
            // 1 行しかないときは読点・句点でも分ける（長すぎる 1 行を作らない）
            List<String> sentences = new ArrayList<>();
            for (String piece : text.split("(?<=[。．.!?！？])")) {
                String value = piece.trim();
                if (!value.isEmpty()) sentences.add(value);
            }
            if (sentences.size() > 1) return sentences;
        }
        return parts;
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ClassroomApiException.invalid("音声が選ばれていません。");
        }
        try {
            return file.getBytes();
        } catch (IOException cause) {
            throw ClassroomApiException.invalid("音声を読み込めませんでした。もう一度お試しください。");
        }
    }

    /**
     * 中身が mp3 らしいか（先頭を見るだけの軽い確認）。
     *
     * <p>mp3 は「ID3 タグ」か「MPEG のフレーム同期（`0xFF` のあと上位 3 ビットが 1）」で始まる。
     * 文字のテキストを `.mp3` に変えたファイルを弾くためのもの（厳密な検証ではない）。</p>
     */
    static boolean looksLikeMp3(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        if (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') {
            return true;
        }
        return (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0;
    }

    /** 自分の行・家族の行・管理者の行だけを返す（他人の行は「見つからない」にする）。 */
    private ClassroomRecordEntity requireVisible(UserPrincipal user, long recordId) {
        ClassroomRecordEntity entity = recordMapper.findById(recordId);
        if (entity == null) {
            throw new NotFoundException("授業記録が見つかりません。");
        }
        if (user.accountType() == AccountType.ADMIN) {
            return entity;
        }
        if (entity.getCreatedBy() != null && entity.getCreatedBy().equals(user.accountId())) {
            return entity;
        }
        // 保護者は家族（自分の子どもの）記録を閲覧できる（決定 Q3）
        if (user.accountType() == AccountType.GUARDIAN && entity.getFamilyId() != null
                && entity.getFamilyId().equals(user.accountId())) {
            return entity;
        }
        throw new NotFoundException("授業記録が見つかりません。");
    }

    /** 所有者（録音した本人）だけを返す。見えないとき 404、見えても操作できないとき 403。 */
    private ClassroomRecordEntity requireOwner(UserPrincipal user, long recordId) {
        ClassroomRecordEntity entity = requireVisible(user, recordId);
        if (entity.getCreatedBy() == null || !entity.getCreatedBy().equals(user.accountId())) {
            throw ClassroomApiException.forbidden("この録音は自分が作成したものではありません。");
        }
        return entity;
    }

    /**
     * 所有者の記録を**排他で押さえて**返す（分塊の受け入れと収尾が同じ行を押さえる）。
     *
     * <p>権限の判定は**押さえる前**と同じ（見えない・自分のものでないなら、押さえずに断る）。
     * 押さえたあとに状態を読み直すので、収尾とアップロードが同時に来ても、後から来た側は
     * **新しい状態**を見る（押さえる前の状態で判断しない）。</p>
     */
    private ClassroomRecordEntity lockOwner(UserPrincipal user, long recordId) {
        ClassroomRecordEntity peeked = requireOwner(user, recordId);
        ClassroomRecordEntity locked = recordMapper.lockById(recordId);
        return locked == null ? peeked : locked;
    }

    private void requireEnabled(ClassroomAiSettings.Snapshot snapshot) {
        if (!settings.enabled(snapshot)) {
            throw new ConflictException("「授業録音 / AI 授業記録」は現在ご利用いただけません"
                    + "（システム設定で無効です）。");
        }
    }

    private void requireDailyLimit(UserPrincipal user, ClassroomAiSettings.Snapshot snapshot) {
        int limit = settings.dailyLimit(snapshot);
        if (limit <= 0) {
            return;
        }
        if (recordMapper.countTodayByAccount(user.accountId()) >= limit) {
            throw new ConflictException("本日の録音は上限（" + limit + " 回）に達しました。"
                    + "明日またお試しください。");
        }
    }

    private static String normalizeLanguageMode(String languageMode) {
        if (languageMode == null || languageMode.isBlank()) {
            // 「自動」は廃止した（言語コードはコード側で固定）。未指定は日本語として扱う
            return "ja";
        }
        String value = languageMode.trim().toLowerCase(Locale.ROOT);
        if (!ClassroomModels.LANGUAGE_MODES.contains(value)) {
            throw ClassroomApiException.invalid("言語モードは zh / ja / en / zh-en / ja-en の"
                    + "いずれかを指定してください。");
        }
        return value;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!ClassroomModels.STATUS_LABELS.containsKey(value)) {
            throw ClassroomApiException.invalid("状態の指定が正しくありません: " + status);
        }
        return value;
    }

    private String nextRecordNo() {
        for (int attempt = 0; attempt < 20; attempt += 1) {
            String candidate = "CR" + LocalDateTime.now().format(NO_FORMAT) + (1000 + RANDOM.nextInt(9000));
            if (recordMapper.findByNo(candidate) == null) {
                return candidate;
            }
        }
        throw new IllegalStateException("授業記録番号を採番できませんでした。");
    }

    private static int versionOf(ClassroomRecordEntity entity) {
        return entity.getVersion() == null ? 1 : entity.getVersion();
    }

    /**
     * 終了直後に届いた分塊を受け入れてよいか。
     *
     * <p>画面は【終了】の直前に最後の分塊を送る（`MediaRecorder` の最後の分塊は `stop()` の後に届く）。
     * 通信の遅れや画面の閉じ方によっては**終了のあとに届く**ことがあり、そこで 409 を返すと
     * **その回の音声が丸ごと残らない**（実測: 短い録音で音声 0 バイト。24 秒の録音も最初の分塊だけ）。
     * 受け入れるのは「停止直後（既定 10 分以内）」だけで、古い記録への誤った追記は防ぐ。</p>
     */
    /**
     * 収尾のときに**確定した「録れた分塊」の最後の連番**（分からなければ 0）。
     *
     * <p>終了後の遅れて届いた分塊を「受け入れてよいか」の境目に使う。旧い画面が送った回
     * （{@code recordedLastSeq} が無い）は、そのとき保存できていた最大の連番を使う
     * （新しい音を足さない、という目的は同じ）。</p>
     */
    private int declaredLastSeq(ClassroomRecordEntity record) {
        Integer recorded = record.getRecordedLastSeq();
        if (recorded != null && recorded > 0) {
            return recorded;
        }
        return maxSeqOf(record.getRecordId());
    }

    private boolean isLateChunkAllowed(ClassroomRecordEntity record) {
        if (!ClassroomModels.STATUS_STOPPED.equals(record.getStatus())) {
            return false;
        }
        Timestamp endedAt = record.getEndTime();
        if (endedAt == null) {
            return false;
        }
        return endedAt.toInstant().isAfter(Instant.now().minus(LATE_CHUNK_GRACE));
    }

    private static String statusLabel(String status) {
        return ClassroomModels.STATUS_LABELS.getOrDefault(status, status == null ? "" : status);
    }

    private static ClassroomModels.RecordStatus toStatus(ClassroomRecordEntity entity) {
        return new ClassroomModels.RecordStatus(entity.getRecordId(), entity.getRecordNo(),
                entity.getStatus(), statusLabel(entity.getStatus()), versionOf(entity));
    }

    private static ClassroomModels.RecordRow toRow(ClassroomRecordEntity entity) {
        boolean hasAudio = entity.getAudioPath() != null && entity.getAudioName() != null;
        return new ClassroomModels.RecordRow(entity.getRecordId(), entity.getRecordNo(), entity.getTitle(),
                entity.getSubject(), entity.getLanguageMode(), entity.getStatus(), statusLabel(entity.getStatus()),
                entity.getDurationSeconds(), entity.getTranscribedChars(), hasAudio,
                iso(entity.getCreatedAt()), iso(entity.getUpdatedAt()));
    }

    private static ClassroomModels.SegmentView toSegmentView(ClassroomSegmentEntity entity) {
        return new ClassroomModels.SegmentView(entity.getSegmentId(), entity.getSeq(),
                entity.getStartOffsetSeconds() == null ? null : entity.getStartOffsetSeconds().doubleValue(),
                entity.getEndOffsetSeconds() == null ? null : entity.getEndOffsetSeconds().doubleValue(),
                entity.getSpeaker() == null ? SPEAKER_LECTURE : entity.getSpeaker(),
                entity.getText(), entity.getLanguage(), iso(entity.getCreatedAt()));
    }

    private static ClassroomModels.NoteView toNoteView(ClassroomNoteEntity entity) {
        return new ClassroomModels.NoteView(entity.getNoteId(), entity.getKind(), entity.getPhaseNo(),
                entity.getStartSeq(), entity.getEndSeq(), entity.getStatus(),
                ClassroomModels.NOTE_STATUS_LABELS.getOrDefault(entity.getStatus(),
                        entity.getStatus() == null ? "" : entity.getStatus()),
                entity.getNoteJson(), entity.getErrorCode(), entity.getErrorMessage(),
                iso(entity.getCreatedAt()), iso(entity.getUpdatedAt()));
    }

    private static ClassroomModels.PresetView toPresetView(ClassroomPresetEntity entity) {
        return new ClassroomModels.PresetView(entity.getPresetId(), entity.getScope(), entity.getName(),
                entity.getText(), entity.getDisplayOrder() == null ? 0 : entity.getDisplayOrder());
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

    private static String iso(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toString();
    }

    /** BigDecimal を画面用の Double にする（null はそのまま null）。 */
    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
