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
                                ClassroomNoteMapper noteMapper,
                                ClassroomPresetMapper presetMapper,
                                ClassroomAiSettings settings,
                                ClassroomSttClient sttClient,
                                ClassroomRecordingStorage storage,
                                AccountMapper accountMapper,
                               ClassroomSttStreamService streamService) {
        this.recordMapper = recordMapper;
        this.segmentMapper = segmentMapper;
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

    @Override
    @Transactional
    public ClassroomModels.ChunkUploadResult uploadChunk(UserPrincipal user, long recordId, int seq,
                                                         MultipartFile file, MultipartFile sttAudio,
                                                         java.math.BigDecimal startSeconds,
                                                         java.math.BigDecimal endSeconds) {
        ClassroomRecordEntity record = requireOwner(user, recordId);
        ClassroomAiSettings.Snapshot snapshot = settings.load();
        requireEnabled(snapshot);
        // 終了直後に届いた最後の分塊は**受け入れる**（音声を残すため。isLateChunkAllowed を参照）
        boolean lateChunk = false;
        if (!ClassroomModels.STATUS_RECORDING.equals(record.getStatus())) {
            if (!isLateChunkAllowed(record)) {
                throw new ConflictException("この録音は録音中ではありません（"
                        + statusLabel(record.getStatus()) + "）。");
            }
            lateChunk = true;
        }
        if (seq < 1) {
            throw ClassroomApiException.invalid("連番は 1 以上で指定してください。");
        }

        int chunkSeconds = settings.chunkSeconds(snapshot);
        int maxMinutes = settings.maxRecordingMinutes(snapshot);
        // 録音最大時間（分）を超える分塊は拒否する（コスト・レート制限。決定 Q7）
        if ((long) seq * chunkSeconds > (long) maxMinutes * 60) {
            throw ClassroomApiException.invalid("録音は最大 " + maxMinutes + " 分までです。"
                    + "ここで録音を終了してください。");
        }

        // 冪等: 同じ連番の再送はスキップ（音声も二重に追記しない）
        Integer existingMax = segmentMapper.maxSeq(recordId);
        int maxSeq = existingMax == null ? 0 : existingMax;
        if (seq <= maxSeq) {
            List<ClassroomModels.SegmentView> appended = segmentMapper
                    .findByRecordAfter(recordId, seq - 1).stream()
                    .map(ClassroomServiceImpl::toSegmentView).toList();
            return new ClassroomModels.ChunkUploadResult(recordId, seq, maxSeq + 1, appended, null, false,
                    record.getStatus(), null);
        }

        byte[] bytes = readBytes(file);

        // 音声は**再生用に必ず追記**する（STT の結果が空でも履歴の元音声を残す）
        ClassroomRecordingStorage.StoredRecording stored;
        if (record.getAudioPath() == null || record.getAudioName() == null) {
            stored = storage.newRecording(user.accountId(), file == null ? null : file.getContentType());
            recordMapper.updateAudio(recordId, stored.relativeDir(), stored.fileName(), stored.mime(),
                    bytes.length, user.accountId());
        } else {
            stored = new ClassroomRecordingStorage.StoredRecording(record.getAudioPath(), record.getAudioName(),
                    record.getAudioMime());
        }
        storage.append(stored.relativeDir(), stored.fileName(), bytes);

        // 終了のあとに届いた分塊: **音声だけ保存**して書き起こしはしない
        // （録音は終わっていて最終まとめも作られている。音声を捨てると授業の記録が丸ごと消える）
        // STT の接続情報も要らないので、解決する前に返す（設定が不完全でも音声は残せる）
        if (lateChunk) {
            log.info("classroom late chunk stored after end. recordId={} seq={} bytes={}",
                    recordId, seq, bytes.length);
            return new ClassroomModels.ChunkUploadResult(recordId, seq, seq + 1, List.of(), null, false,
                    record.getStatus(), null);
        }

        // ストリーミング書き起こしが動いている間は、分塊ごとの STT をしない
        // （同じ音声を二度書き起こさない。音声は上で保存済み）
        if (streamService != null && streamService.isActive(recordId)) {
            log.info("classroom chunk stored without stt (streaming). recordId={} seq={} bytes={}",
                    recordId, seq, bytes.length);
            return new ClassroomModels.ChunkUploadResult(recordId, seq, seq + 1, List.of(), null, false,
                    record.getStatus(), null);
        }

        // ここから先は書き起こし（STT）。ブラウザ認識のときはサーバーは STT を呼ばない
        // （書き起こしは画面が認識したテキストを `appendTranscript` で送ってくる。音声は保存だけ）
        if (settings.browserStt(snapshot)) {
            log.info("classroom chunk stored without stt (browser recognition). recordId={} seq={} bytes={}",
                    recordId, seq, bytes.length);
            return new ClassroomModels.ChunkUploadResult(recordId, seq, seq + 1, List.of(), null, false,
                    record.getStatus(), null);
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
            log.warn("classroom chunk stt failed; audio kept. recordId={} seq={} mime={} reason={}",
                    recordId, seq, sttMime, response.errorMessage());
            return new ClassroomModels.ChunkUploadResult(recordId, seq, seq + 1, List.of(), null, false,
                    record.getStatus(), null);
        }

        String text = joinText(response.segments());
        if (text.isEmpty()) {
            // 認識結果が空: **埋め草を入れずに何も作らない**（文字数と AI まとめを汚さないため）。
            // 実測: 「（聞き取れませんでした）」が並び、まとめの材料を薄めていた
            log.info("classroom chunk transcribed empty. recordId={} seq={} bytes={}", recordId, seq,
                    sttBytes.length);
            return new ClassroomModels.ChunkUploadResult(recordId, seq, seq + 1, List.of(), null, false,
                    record.getStatus(), null);
        }
        String speaker = response.segments().stream()
                .map(ClassroomSttClient.Segment::speaker).filter(s -> s != null && !s.isBlank()).findFirst()
                .orElse(SPEAKER_LECTURE);
        String language = response.segments().stream()
                .map(ClassroomSttClient.Segment::language).filter(s -> s != null && !s.isBlank()).findFirst()
                .orElse(languageCode);

        // 時間は**画面が測った実際の経過秒**を優先する（分塊の長さの設定を録音中に変えても時系列が壊れない）。
        // 送られてこないとき（旧クライアント）は連番×分塊の長さで計算する
        BigDecimal start = startSeconds != null && startSeconds.signum() >= 0
                ? startSeconds : BigDecimal.valueOf((long) (seq - 1) * chunkSeconds);
        BigDecimal end = endSeconds != null && endSeconds.compareTo(start) > 0
                ? endSeconds : start.add(BigDecimal.valueOf(chunkSeconds));

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

        // トリガー評価 → 成立したら PENDING のフェーズノートを作る
        Long pendingNoteId = evaluateTrigger(recordId, user.accountId(), text, snapshot);

        List<ClassroomModels.SegmentView> appended = List.of(toSegmentView(segment));
        log.info("classroom chunk transcribed. recordId={} seq={} chars={} triggered={}",
                recordId, seq, text.length(), pendingNoteId != null);
        return new ClassroomModels.ChunkUploadResult(recordId, seq, seq + 1, appended, pendingNoteId,
                pendingNoteId != null, record.getStatus(),
                pendingNoteId == null ? null : ClassroomModels.noteRunPath(pendingNoteId));
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
                List.of(toSegmentView(segment)), pendingNoteId, pendingNoteId != null, record.getStatus(),
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
                iso(record.getCreatedAt()), iso(record.getUpdatedAt()));
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

    @Override
    @Transactional
    public ClassroomModels.EndResult end(UserPrincipal user, long recordId) {
        ClassroomRecordEntity record = requireOwner(user, recordId);
        ClassroomAiSettings.Snapshot snapshot = settings.load();
        if (!ClassroomModels.STATUS_RECORDING.equals(record.getStatus())) {
            throw new ConflictException("この録音は録音中ではありません（" + statusLabel(record.getStatus()) + "）。");
        }
        int durationSeconds = durationSecondsOf(record, snapshot);
        if (recordMapper.updateEnded(recordId, durationSeconds, settings.retentionDays(snapshot),
                user.accountId(), versionOf(record)) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }

        // 最終まとめ（FINAL）の PENDING 行を作る（batC62 が拾う）。
        // ただし転写セグメントが 1 件も無いとき（音声が 1 つも送られずに終了した場合）は作らない:
        // ノート行の対象範囲は DDL の CK_CR_授業ノート_範囲 で「終了連番 >= 開始連番」なので、
        // 0 件で作ると制約違反になり、@Transactional のため終了そのものが失敗して
        // 記録が RECORDING のまま残ってしまう。
        int maxSeq = maxSeqOf(recordId);
        Long finalNoteId = null;
        String notice = null;
        if (!settings.noteEnabled(snapshot)) {
            // AI 解析（batC62）が無効: 最終まとめの行も作らない（書き起こしだけ残す）
            notice = "AI 解析（フェーズノート・最終まとめ）は現在オフのため、最終まとめは作成しませんでした。";
            log.info("classroom record ended with ai notes disabled. recordId={} durationSeconds={}",
                    recordId, durationSeconds);
        } else if (maxSeq < 1) {
            notice = "書き起こしが無かったため、最終まとめは作成しませんでした。";
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
        return new ClassroomModels.EndResult(recordId, ClassroomModels.STATUS_STOPPED,
                statusLabel(ClassroomModels.STATUS_STOPPED), finalNoteId,
                finalNoteId == null ? null : ClassroomModels.noteRunPath(finalNoteId), notice);
    }

    // ------------------------------------------------------------------ 配信

    @Override
    @Transactional(readOnly = true)
    public AudioFile audio(UserPrincipal user, long recordId) {
        ClassroomRecordEntity record = requireVisible(user, recordId);
        if (record.getAudioPath() == null || record.getAudioName() == null) {
            throw ClassroomApiException.notFound("録音が見つかりません。");
        }
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

    private int durationSecondsOf(ClassroomRecordEntity record, ClassroomAiSettings.Snapshot snapshot) {
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
            return new ClassroomModels.ChunkUploadResult(recordId, 0, 1, List.of(), null, false,
                    record.getStatus(), null);
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
        return new ClassroomModels.ChunkUploadResult(recordId, seq, seq + 1, appended, null, false,
                record.getStatus(), null);
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
}
