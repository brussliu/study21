package com.study21.user.classroom;

import com.study21.common.core.stt.DashScopeAsrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 授業録音の**ストリーミング書き起こし**（話しながら文字が出る）。
 *
 * <p>録音 1 回・**音源 1 つにつき WebSocket を 1 本**つなぎっぱなしにし、画面から届く
 * 16kHz モノラル 16bit の PCM をそのまま流し込む。阿里雲は `result-generated` で
 * **途中の文（interim）と確定した文（sentence_end）**、および `sentence_id` と
 * `begin_time` / `end_time`（ミリ秒）を返す。</p>
 *
 * <p><b>話者は音源で決める</b>（モデルが人物を判定するのではない）:
 * マイク＝学生／共有した音＝先生／マイクのみの録音＝講義。共有した音に混ざる他の発話も
 * 「先生」側として扱う（この制限は画面にも書く）。</p>
 *
 * <p><b>時間軸は音声クロック</b>: 画面はフレームごとに**録音の先頭からの絶対位置**（16kHz の
 * サンプル数）を送る。文の時刻は「**その認識セッションの開始位置**＋`begin_time`」から計算する
 * （阿里雲の `begin_time` はセッションへ送った音声の先頭からの位置なので、足すのは 1 回だけ）。
 * HTTP の往復時間や結果の到着順は使わない。位置を渡さない送り方（分塊ごとの HTTP など）では
 * 従来どおり「その音源へ送ったサンプル数」を積み上げて位置とする。どちらの経路でも位置は
 * 前へしか進めない（送り直し・順序の入れ替わりで時間軸を巻き戻さない）。したがって、
 * 張り直し・後端の再起動で非 0 の位置から続けても時刻は 0 に戻らない・詰まらない。</p>
 *
 * <p><b>フレームの識別は番号と絶対位置</b>（画面は常時接続と HTTP で同じ欄を送る）:
 * 番号は音源ごとに 1 から。すでに認識へ流した番号の音は流し直さない（冪等）。すでに処理した
 * 位置より古い位置のフレームも流し直さない（時間軸を巻き戻さない）。</p>
 *
 * <p><b>保存は冪等</b>: 発話キー（`音源#開始ミリ秒`）で 1 行に寄せる。
 * 同じ発話が再送されても INSERT せず UPDATE する（`連番` は記録内の表示順なので、
 * セッションを作り直しても一意制約に衝突しない）。</p>
 *
 * <p><b>受け取り確認（`processedFrames`）は保存できたところまでしか進めない</b>: 画面は
 * 確認できた番号までの音を捨てるので、保存に失敗した文を含むフレームを「処理済み」と返すと
 * その音は**二度と送られず、書き起こしが消える**。失敗した文は有界のやり直しを行い、
 * それでも駄目なら番号を進めずに理由を返す（セッションは殺さない）。</p>
 */
@Service
public class ClassroomSttStreamService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomSttStreamService.class);

    /** 1 セッションで受け取る PCM の上限（16kHz 16bit で約 6 時間ぶん。暴走を防ぐだけの歯止め）。 */
    private static final long MAX_PCM_BYTES = 16_000L * 2 * 60 * 60 * 6;

    /** 1 文の保存をやり直す回数（一時的な DB の失敗＝接続断・デッドロックをやり直す。無限には粘らない）。 */
    private static final int SAVE_ATTEMPTS = 3;
    /** やり直す前の待ち（ミリ秒）。待ちすぎない＝画面の送信を止めない。 */
    private static final long SAVE_RETRY_WAIT_MS = 50;

    /** 音源: マイク（学生）。 */
    public static final String SOURCE_MIC = "mic";
    /** 音源: 共有した音（先生）。 */
    public static final String SOURCE_SHARED = "shared";

    /**
     * フレームの絶対位置が分からないとき（位置を送らない送り方＝従来の分塊ごとの HTTP・
     * 見出しの無いフレーム）。このときは「その音源へ送ったサンプル数」を位置とする。
     */
    public static final long UNKNOWN_SAMPLE = -1L;

    /** 話者ラベル（音源から決める）。 */
    private static final String SPEAKER_LECTURE = "講義";
    private static final String SPEAKER_TEACHER = "先生";
    private static final String SPEAKER_STUDENT = "学生";

    /** 16kHz・モノラル 16bit（1 サンプル＝2 バイト）。 */
    private static final int SAMPLE_RATE = 16_000;
    private static final int BYTES_PER_SAMPLE = 2;
    /** 常時接続の 1 フレームの長さ（画面は 100ms＝3200 バイトずつ送る）。 */
    private static final int FRAME_MILLIS = 100;

    private final ClassroomAiSettings settings;
    private final ClassroomSegmentMapper segmentMapper;
    private final ClassroomRecordMapper recordMapper;
    private final DashScopeAsrClient dashScope;

    /** セッション（授業記録 ID ＋ 音源ごと）。 */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    /**
     * 音源ごとの**いちばん新しい音声の位置**（録音の先頭からの 16kHz サンプル数）。
     *
     * <p>認識セッションを作り直しても続く時間軸の基準であり、**前へしか進めない**
     * （送り直し・順序の入れ替わりで古い位置が届いても巻き戻さない）。位置を渡さない
     * 送り方では、ここに送ったサンプル数を積み上げる＝従来の「送ったサンプル数」と同じ値になる。</p>
     */
    private final Map<String, Long> newestSamples = new ConcurrentHashMap<>();
    /** 音源ごとのセッション番号（発話キーを安定させるために増やす）。 */
    private final Map<String, Integer> sessionNumbers = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public ClassroomSttStreamService(ClassroomAiSettings settings, ClassroomSegmentMapper segmentMapper,
                                     ClassroomRecordMapper recordMapper) {
        this(settings, segmentMapper, recordMapper, new DashScopeAsrClient());
    }

    /** テスト用（DashScope の接続を差し替える）。 */
    ClassroomSttStreamService(ClassroomAiSettings settings, ClassroomSegmentMapper segmentMapper,
                              ClassroomRecordMapper recordMapper, DashScopeAsrClient dashScope) {
        this.settings = settings;
        this.segmentMapper = segmentMapper;
        this.recordMapper = recordMapper;
        this.dashScope = dashScope;
    }

    /** 音源の正規化（知らない値はマイク扱い＝今までどおり）。 */
    public static String normalizeSource(String source) {
        return SOURCE_SHARED.equals(source) ? SOURCE_SHARED : SOURCE_MIC;
    }

    /** 話者ラベル（マイクのみの録音＝講義／二音源＝学生・先生）。 */
    public static String speakerOf(String source, boolean twoSources) {
        if (!twoSources) {
            return SPEAKER_LECTURE;
        }
        return SOURCE_SHARED.equals(normalizeSource(source)) ? SPEAKER_TEACHER : SPEAKER_STUDENT;
    }

    /** その録音で**どれかの音源**のストリーミングが動いているか（分塊ごとの STT を止める判断）。 */
    public boolean isActive(long recordId) {
        return sessions.keySet().stream().anyMatch(key -> key.startsWith(recordId + "#"));
    }

    /** その録音・その音源のストリーミングが動いているか。 */
    public boolean isActive(long recordId, String source) {
        return sessions.containsKey(keyOf(recordId, source));
    }

    /**
     * 画面へ返す 1 回ぶんの結果。
     *
     * <p>`processedFrames` は**この音源で処理し終えたフレーム番号**（受け取り確認）。
     * 断線して張り直したとき、画面はここから続きを送ればよい（すでに処理した番号は捨てる）。
     * **保存できた文までの番号しか進めない**（保存に失敗した文を含むフレームは進めないので、
     * 画面は同じ音を送り直し、認識へは流し直さずに保存だけをやり直せる）。</p>
     */
    public record StreamPush(
            /** まだ確定していない文（画面は 1 行だけ上書きして出す）。 */
            String interim,
            /** この回で確定して保存したセグメント。 */
            List<ClassroomModels.SegmentView> added,
            /** 失敗した理由（日本語。null なら正常）。 */
            String error,
            /** この音源で処理し終えたフレーム番号（0 はまだ無し）。 */
            int processedFrames) {
    }

    /** 1 回ぶんの取り出しと保存の結果。 */
    private record Drain(
            /** この回で確定して保存したセグメント。 */
            List<ClassroomModels.SegmentView> added,
            /** 取り出した文を**先頭から途切れず**保存できたか（false なら受け取り確認を進めない）。 */
            boolean allSaved,
            /** 認識側の失敗の理由（null なら正常）。 */
            String failure,
            /** まだ確定していない文。 */
            String interim) {
    }

    /**
     * 音声を 1 回ぶん受け取って認識へ流し、いまの状態を返す。
     *
     * @param recordId  授業記録 ID
     * @param accountId 所有者（セグメントの登録者に使う）
     * @param source    音源（`mic` / `shared`）
     * @param pcm       16kHz モノラル 16bit の PCM（ヘッダ無し）
     */
    public StreamPush push(long recordId, long accountId, String source, byte[] pcm) {
        // フレーム番号を渡さない呼び出し（HTTP の分塊ごとの送信）は**到着順**に 1 つずつ進める
        return push(recordId, accountId, source, pcm, 0, UNKNOWN_SAMPLE);
    }

    /**
     * 音声を 1 回ぶん受け取って認識へ流す（**フレーム番号つき**。位置は番号から推定できないので
     * 「送ったサンプル数」を使う＝位置を送らない古い画面との互換）。
     */
    public StreamPush push(long recordId, long accountId, String source, byte[] pcm, int frameNo) {
        return push(recordId, accountId, source, pcm, frameNo, UNKNOWN_SAMPLE);
    }

    /**
     * 音声を 1 回ぶん受け取って認識へ流す（**フレームの識別つき**＝画面の統一の欄）。
     *
     * <p>フレーム番号は音源ごとに 1 から。**すでに認識へ流した番号の音は流し直さない**
     * （断線して張り直したあとに画面が同じ音を送り直しても、二重に認識しない＝冪等）。
     * ただし**取り出しと保存はその回も行う**: 前の回に保存できなかった文はここでやり直され、
     * 成功すれば受け取り確認の番号が進む（そうしないと画面が同じ音を永久に送り続ける）。</p>
     *
     * @param frameNo     画面が数えたフレーム番号（0 以下なら到着順に採番する）
     * @param startSample このフレームが始まる**録音の先頭からの絶対位置**（16kHz のサンプル数）。
     *                    {@link #UNKNOWN_SAMPLE} なら「その音源へ送ったサンプル数」を使う
     */
    public StreamPush push(long recordId, long accountId, String source, byte[] pcm,
                           int frameNo, long startSample) {
        String normalized = normalizeSource(source);
        Session session = sessions.computeIfAbsent(keyOf(recordId, normalized),
                id -> openSession(recordId, normalized));
        // この回の番号（渡されないとき＝HTTP の分塊は到着順）
        int frame = frameNo > 0 ? frameNo : session.fedFrames() + 1;
        if (session.failure() != null) {
            return new StreamPush("", List.of(), session.failure(), session.ackedFrames());
        }
        int ackableFrame = frame;
        if (frame > session.fedFrames()) {
            // いままでに処理した**いちばん新しい位置**。時間軸はここから前へしか進めない
            long newest = newestSamples.getOrDefault(key(recordId, normalized), 0L);
            if (startSample >= 0 && startSample < newest) {
                /*
                 * **古い位置のフレーム**: 送り直し・順序の入れ替わりで、すでに処理した位置の音が
                 * もう一度届いた。流し直すと認識が二重になり、時間軸の基準をここへ戻すと確定済みの
                 * 文と同じ発話キーを作り直す（別の行を壊す）。番号も進めない＝画面は送り直せる。
                 */
                ackableFrame = session.ackedFrames();
                log.info("classroom stream frame is older than processed; skipped."
                                + " recordId={} source={} frame={} startSample={} newest={} confirmed={}",
                        recordId, normalized, frame, startSample, newest, session.ackedFrames());
            } else {
                if (session.bytes() + pcm.length > MAX_PCM_BYTES) {
                    return new StreamPush("", List.of(), "書き起こしの音声が長すぎます。録音を分けてください。",
                            session.ackedFrames());
                }
                long samples = pcm.length / BYTES_PER_SAMPLE;
                // 位置: 画面が送った絶対位置。送られないときは「その音源へ送ったサンプル数」
                long position = startSample >= 0 ? startSample : newest;
                if (session.originSamples() < 0) {
                    // この認識セッションの先頭＝阿里雲の begin_time の基準（あとから動かさない）
                    session.setOriginSamples(position);
                }
                session.addBytes(pcm.length);
                session.setFedFrames(frame);
                // 前へだけ進める（古い位置で巻き戻さない。位置は max で積む）
                newestSamples.merge(key(recordId, normalized), position + samples, Math::max);
                session.stream().send(pcm);
            }
        } else {
            // 送り直し（すでに認識へ流した音）: 流し直さない（同じ音を二度認識しない）
            log.info("classroom stream frame already sent. recordId={} source={} frame={} confirmed={}",
                    recordId, normalized, frame, session.ackedFrames());
        }

        List<String> warnings = new ArrayList<>();
        Drain drained = drain(session, recordId, normalized, ackableFrame, warnings);
        if (drained.failure() != null) {
            log.warn("classroom stream stt failed. recordId={} source={} reason={}",
                    recordId, normalized, drained.failure());
            close(recordId, normalized);
            return new StreamPush("", drained.added(), drained.failure(), session.ackedFrames());
        }
        return new StreamPush(drained.interim(), drained.added(),
                warnings.isEmpty() ? null : String.join(" ", warnings), session.ackedFrames());
    }

    /**
     * 確定した文を取り出して保存する（`push` と `finish` の共通）。
     *
     * <p><b>受け取り確認の正しさはここで決まる</b>: 保存できなかった文があると、その文より先へは
     * 進めない（`drained` を進めない）。同じ文は次の回にもう一度取り出され、発話キーで UPDATE に
     * 寄るので**行は増えない**。</p>
     *
     * @param frame この回で「処理済み」としてよいフレーム番号（`finish` は送り終えた番号を渡す）
     */
    private Drain drain(Session session, long recordId, String source, int frame, List<String> warnings) {
        DashScopeAsrClient.TimedSnapshot snapshot = session.stream().pollTimed(session.drained());
        boolean twoSources = twoSources(recordId);
        List<ClassroomModels.SegmentView> added = new ArrayList<>();
        boolean prefixSaved = true;
        int consumed = 0;
        for (DashScopeAsrClient.TimedSentence sentence : snapshot.finished()) {
            String text = sentence.text() == null ? "" : sentence.text().trim();
            if (text.isEmpty()) {
                // 空の確定は捨てる（埋め草を入れない）。捨てた文は保存済みと同じ扱いでよい
                if (prefixSaved) {
                    consumed += 1;
                }
                continue;
            }
            ClassroomModels.SegmentView saved =
                    saveWithRetry(session, recordId, source, twoSources, sentence, warnings);
            if (saved != null) {
                added.add(saved);
                if (prefixSaved) {
                    consumed += 1;
                }
            } else {
                // 1 文でも保存できなかったら、そこから先は「処理済み」にしない。残りの文の保存は続ける
                // （1 つの悪い文で、そのあとの良い文まで消える方が損）
                prefixSaved = false;
            }
        }
        session.setDrained(session.drained() + consumed);
        /*
         * **認識そのものが失敗した回は受け取り確認を進めない**（成功として返さないのと同じ理由）。
         * 進めると画面がその音を捨て、書き起こしに残らない区間が確定してしまう。送り直せば
         * 新しいセッションで認識できる。
         */
        if (prefixSaved && snapshot.failure() == null) {
            session.setAckedFrames(Math.max(session.ackedFrames(), frame));
        }
        return new Drain(added, prefixSaved, snapshot.failure(),
                snapshot.interim() == null ? "" : snapshot.interim().text());
    }

    /**
     * 終わりを伝えてセッションを閉じる（残りの確定文はこの呼び出しの戻りで取る）。
     *
     * <p><b>成功と言ってよいかをサーバー側で決める</b>（画面が待ったかどうかに頼らない）:
     * 尾部（停止のあとに確定する文）を取り切れなかったとき、保存できなかった文があるときは、
     * `error` に理由を入れて返す。</p>
     *
     * <p>**2 回呼んでもデータを増やさない・壊さない**（認識セッションは 1 回目の終了で閉じるので、
     * 2 回目は「受け付けるセッションが無い」ことを理由として返す＝黙って成功にしない）。</p>
     */
    public StreamPush finish(long recordId, long accountId, String source) {
        String normalized = normalizeSource(source);
        Session session = sessions.get(keyOf(recordId, normalized));
        if (session == null) {
            // すでに終えている（2 回目の終了）か、音を 1 つも送っていない。どちらも収尾は無い。
            // 画面は「終わった」と誤解するので、理由を返す（データは何も変えない）
            return new StreamPush("", List.of(),
                    "この音源の書き起こしは終了しています（受け付けている認識セッションがありません）。", 0);
        }
        session.stream().close();
        // 認識の終わりを時間内に受け取れたか（尾部の確定文を取り切れたかの判断材料）
        boolean tailCollected = session.stream().tailFinished();
        List<String> warnings = new ArrayList<>();
        // 送り終えた番号までを確認してよい（保存できなかった文があれば進めない）
        Drain drained = drain(session, recordId, normalized, session.fedFrames(), warnings);
        int processed = session.ackedFrames();
        close(recordId, normalized);
        log.info("classroom stream stt finished. recordId={} source={} saved={} confirmed={} tail={}",
                recordId, normalized, drained.added().size(), processed, tailCollected);

        String notice = drained.failure();
        if (notice == null && !drained.allSaved()) {
            // 保存できなかった文がある（やり直しても駄目だった）。受け取り確認も進めていない
            notice = "書き起こしの一部を保存できませんでした（受け取り確認は進めていません）。";
        }
        if (notice == null && !tailCollected) {
            // 実測: マイクの確定文は停止の 14〜20 秒後に届くことがある。取り切れなかったことを伝える
            notice = "認識の尾部（最後の確定文）を時間内に取り切れませんでした。"
                    + "遅れて確定した文は書き起こしに残らない可能性があります。";
        }
        if (notice == null && !warnings.isEmpty()) {
            notice = String.join(" ", warnings);
        }
        return new StreamPush("", drained.added(), notice, processed);
    }

    /**
     * 断線して張り直した接続から「次に送るフレーム番号」を受け取り、**音声クロックを合わせる**。
     *
     * <p>常時接続のフレームは 100ms 固定（画面は 3200 バイトずつ送る）なので、番号から
     * 「そのフレームが始まる音声の位置」が分かる。後端を再起動すると送ったサンプル数
     * （＝音声クロック）は 0 に戻るため、これが無いと**同じ発話が別の位置として数えられ、
     * 発話キーが変わって 2 行目ができる**（逆に、遅れて届いた重複が別の古い行を上書きし得る）。
     * 画面がフレームごとに絶対位置を送るようになった今は、位置が届く回はそちらが優先される
     * （この換算は位置を送らない古い画面・見出しの無いフレームのための互換）。</p>
     *
     * <p>前に進めるだけで戻さない（送り直しで時間が巻き戻ると、確定済みの文と同じキーを作り直す）。</p>
     */
    public void resume(long recordId, String source, int fromFrame) {
        if (fromFrame <= 1) {
            return;
        }
        String normalized = normalizeSource(source);
        long samples = (long) (fromFrame - 1) * SAMPLE_RATE * FRAME_MILLIS / 1000;
        newestSamples.merge(key(recordId, normalized), samples, Math::max);
    }

    /** 録音が終わった・失敗したときに片付ける（すべての音源）。 */
    public void close(long recordId) {
        close(recordId, SOURCE_MIC);
        close(recordId, SOURCE_SHARED);
        newestSamples.keySet().removeIf(key -> key.startsWith(recordId + "#"));
        sessionNumbers.keySet().removeIf(key -> key.startsWith(recordId + "#"));
    }

    /** 1 音源ぶんを片付ける（時間軸の基準は残す＝続きを録るときに戻らない）。 */
    public void close(long recordId, String source) {
        Session session = sessions.remove(keyOf(recordId, source));
        if (session == null) {
            return;
        }
        try {
            session.stream().close();
        } catch (RuntimeException ignored) {
            // すでに切れているときは何もしない
        }
    }

    /** 二音源（マイク＋共有の音）の録音か（＝話者を学生／先生に分ける）。 */
    private boolean twoSources(long recordId) {
        return newestSamples.getOrDefault(key(recordId, SOURCE_SHARED), 0L) > 0;
    }

    /** セッションを開く（設定から接続情報を解決し、失敗は理由として覚えておく）。 */
    private Session openSession(long recordId, String source) {
        Session session = new Session(source);
        session.setSessionNo(sessionNumbers.merge(key(recordId, source), 1, Integer::sum));
        try {
            ClassroomAiSettings.Snapshot snapshot = settings.load();
            ClassroomAiSettings.SttConnection connection = settings.resolveStt(snapshot);
            if (connection == null || connection.url() == null || connection.url().isBlank()) {
                session.setFailure("音声認識の接続情報が設定されていません。");
                return session;
            }
            // 言語は**その授業の言語モード**から決める（日本語の授業に zh を渡さない）
            ClassroomRecordEntity record = recordMapper.findById(recordId);
            String languageMode = record == null ? null : record.getLanguageMode();
            String languageCode = settings.sttLanguageCode(snapshot, languageMode);
            session.setLanguageCode(languageCode);
            session.setStream(dashScope.openStream(new DashScopeAsrClient.Request(
                    connection.url(), connection.apiKey(), connection.model(),
                    "pcm", SAMPLE_RATE, languageHintOf(languageCode),
                    null, settings.sttTimeoutSeconds(snapshot))));
            log.info("classroom stream stt opened. recordId={} source={} sessionNo={} provider={} model={}",
                    recordId, source, session.sessionNo(), connection.provider(), connection.model());
        } catch (RuntimeException cause) {
            session.setFailure(cause.getMessage() == null
                    ? "音声認識を開始できませんでした。" : cause.getMessage());
        }
        return session;
    }

    /**
     * 1 文を保存する（**有界のやり直しつき**）。**保存に失敗しても接続（セッション）を殺さない**。
     *
     * <p>実測で起きた不具合: 記録が消えている（利用者が削除した・終了と競合した）ときに
     * INSERT が外部キー違反になると、例外が接続まで抜けて WebSocket が **1011 で閉じ**、
     * そのあとに確定した文も**全部落ちていた**（尾部の文＝実質その音源の書き起こしが消える）。
     * 音そのものは録音に残っているので、ここでは警告を残して先へ進み、理由を画面にも返す。</p>
     *
     * <p>やり直すのは一時的な失敗（接続断・デッドロック・競合）のため。**保存できた文だけ**が
     * 「処理済み」になり（呼ぶ側が `drained` を進める）、ここで諦めた文は次の回にやり直される
     * ＝黙って捨てない。</p>
     *
     * @return 保存できた文（やり直しても駄目だったときは null）
     */
    private ClassroomModels.SegmentView saveWithRetry(Session session, long recordId, String source,
            boolean twoSources, DashScopeAsrClient.TimedSentence sentence, List<String> warnings) {
        for (int attempt = 1; attempt <= SAVE_ATTEMPTS; attempt += 1) {
            try {
                return save(session, recordId, source, twoSources, sentence);
            } catch (RuntimeException cause) {
                String reason = cause.getMessage() == null ? cause.getClass().getSimpleName()
                        : cause.getMessage().split("\n")[0];
                if (attempt < SAVE_ATTEMPTS && sleepBeforeRetry()) {
                    log.warn("classroom stream segment save retry. recordId={} source={} attempt={} reason={}",
                            recordId, source, attempt, reason);
                    continue;
                }
                warnings.add("書き起こしの一部を保存できませんでした。");
                log.warn("classroom stream segment save failed; session kept, position not confirmed."
                                + " recordId={} source={} attempts={} reason={}",
                        recordId, source, attempt, reason);
                return null;
            }
        }
        return null;
    }

    /** やり直す前の短い待ち（画面の送信を止めすぎない）。中断されたら false＝それ以上粘らない。 */
    private static boolean sleepBeforeRetry() {
        try {
            Thread.sleep(SAVE_RETRY_WAIT_MS);
            return true;
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ClassroomModels.SegmentView save(Session session, long recordId, String source, boolean twoSources,
                                             DashScopeAsrClient.TimedSentence sentence) {
        /*
         * この認識セッションを始めた**録音の位置**（サンプル数）。阿里雲の `begin_time` は
         * セッションへ送った音声の先頭からのミリ秒なので、これを足して録音の時間軸へ写す。
         *
         * セッションの先頭は「最初に流したフレームの絶対位置」（画面が送る値）。位置を送らない
         * 送り方では、そのときまでに送ったサンプル数（従来の基準）。
         */
        long sessionStartSamples = session.originSamples() >= 0 ? session.originSamples()
                : newestSamples.getOrDefault(key(recordId, source), 0L)
                        - session.bytes() / BYTES_PER_SAMPLE;
        BigDecimal start = offsetSeconds(sessionStartSamples, sentence.beginMs());
        BigDecimal end = offsetSeconds(sessionStartSamples, sentence.endMs());
        if (end.compareTo(start) < 0) {
            end = start;
        }
        String speaker = speakerOf(source, twoSources);
        /*
         * 発話キーは**音声の位置**から作る（`音源#開始ミリ秒`。`docs/DECISIONS.md` に理由）。
         *
         * 認識セッションの通し番号を使ってはいけない: 張り直し・後端の再起動でセッションを
         * 作り直すたびに同じ発話が別のキーになり、**同じ音が 2 行になる**（遅れて届いた重複が
         * 別の古い行を上書きすることもある）。音声の位置は音源ごとに積み上げたサンプル数から
         * 決まるので、セッションを作り直しても・プロセスを入れ替えても同じ発話には同じ値が出る。
         * 一意性は「同じ音源の中で文の開始位置が重ならない」ことと、音源ごとに別の空間
         * （`mic#` / `shared#`）であることで保証する。
         */
        long startMillis = start.multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.HALF_UP).longValue();
        String utteranceKey = source + "#" + startMillis;
        String language = session.languageCode();

        ClassroomSegmentEntity existing = segmentMapper.findByUtteranceKey(recordId, utteranceKey);
        if (existing != null) {
            return updateExisting(existing, recordId, start, end, sentence.text(), language, speaker, utteranceKey);
        }

        for (int attempt = 0; attempt < 5; attempt += 1) {
            Integer maxSeq = segmentMapper.maxSeq(recordId);
            int seq = (maxSeq == null ? 0 : maxSeq) + 1;
            ClassroomSegmentEntity segment = new ClassroomSegmentEntity();
            segment.setRecordId(recordId);
            segment.setSeq(seq);
            segment.setStartOffsetSeconds(start);
            segment.setEndOffsetSeconds(end);
            segment.setSpeaker(speaker);
            segment.setUtteranceKey(utteranceKey);
            segment.setSource(source);
            segment.setText(sentence.text());
            segment.setLanguage(language);
            try {
                segmentMapper.insert(segment);
                recordMapper.addTranscribedChars(recordId, sentence.text().length());
                log.info("classroom stream segment stored. recordId={} seq={} source={} speaker={} key={} start={}",
                        recordId, seq, source, speaker, utteranceKey, start);
                return new ClassroomModels.SegmentView(segment.getSegmentId(), seq,
                        start.doubleValue(), end.doubleValue(), speaker, sentence.text(), language, null);
            } catch (DuplicateKeyException conflict) {
                /*
                 * 一意制約に当たった。意味は 2 つある:
                 *  (1) 別の音源が同じ連番を先に取った（連番の一意制約。珍しい）→ 連番を採り直して再試行
                 *  (2) **同じ発話を別の要求が先に保存した**（発話キーの一意制約。HTTP の再送が重なった）
                 *      → 同じ発話なので INSERT を繰り返さず、その行を UPDATE する（**行を増やさない**）
                 */
                ClassroomSegmentEntity raced = segmentMapper.findByUtteranceKey(recordId, utteranceKey);
                if (raced != null) {
                    log.info("classroom stream segment raced; updated instead. recordId={} key={}",
                            recordId, utteranceKey);
                    return updateExisting(raced, recordId, start, end, sentence.text(), language, speaker,
                            utteranceKey);
                }
                log.info("classroom stream segment retry. recordId={} seq={} key={}", recordId, seq, utteranceKey);
            }
        }
        throw new IllegalStateException("転写セグメントを保存できませんでした（連番の競合）");
    }

    /**
     * すでにある同じ発話の行を、確定し直した文と時刻で置き換える。
     *
     * <p>文字数は**差分**だけ足す（同じ文をもう一度保存しても二重に数えない）。</p>
     */
    private ClassroomModels.SegmentView updateExisting(ClassroomSegmentEntity existing, long recordId,
            BigDecimal start, BigDecimal end, String text, String language, String speaker, String utteranceKey) {
        int previous = existing.getText() == null ? 0 : existing.getText().length();
        segmentMapper.updateByUtterance(existing.getSegmentId(), start, end, text, language);
        int delta = text.length() - previous;
        if (delta != 0) {
            recordMapper.addTranscribedChars(recordId, delta);
        }
        log.info("classroom stream segment updated. recordId={} key={} chars={}",
                recordId, utteranceKey, text.length());
        return new ClassroomModels.SegmentView(existing.getSegmentId(), existing.getSeq(),
                start.doubleValue(), end.doubleValue(), speaker, text, language, null);
    }

    /** サンプル位置＋ミリ秒 → 録音開始からの秒（音声クロック。HTTP の時刻は使わない）。 */
    private static BigDecimal offsetSeconds(long baseSamples, long milliseconds) {
        long samples = baseSamples + milliseconds * SAMPLE_RATE / 1000;
        return BigDecimal.valueOf(samples)
                .divide(BigDecimal.valueOf(SAMPLE_RATE), 3, RoundingMode.HALF_UP);
    }

    /** BCP-47（ja-JP）→ DashScope の言語ヒント（ja）。分からない言語は null（自動）。 */
    static String languageHintOf(String languageCode) {
        String value = languageCode == null ? "" : languageCode.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        int dash = value.indexOf('-');
        return dash > 0 ? value.substring(0, dash) : value;
    }

    private static String key(long recordId, String source) {
        return recordId + "#" + source;
    }

    private static String keyOf(long recordId, String source) {
        return recordId + "#" + normalizeSource(source);
    }

    /** 1 音源ぶんのセッション。 */
    private static final class Session {
        private final String source;
        private DashScopeAsrClient.Stream stream;
        private String failure;
        private String languageCode = "ja-JP";
        /** この音源で開いたセッションの通し番号（**発話キーには使わない**。ログの識別にだけ使う）。 */
        private int sessionNo;
        private int drained;
        /** この音源で**認識へ流した**フレーム番号（送り直しを二度流さないための目印）。 */
        private int fedFrames;
        /** この音源で**保存まで終えた**フレーム番号（画面へ返す受け取り確認）。 */
        private int ackedFrames;
        /**
         * この認識セッションが始まる**録音の位置**（16kHz のサンプル数。UNKNOWN＝まだ音を流していない）。
         * 阿里雲の `begin_time` を録音の時間軸へ写す基準。セッションの途中で動かさない。
         */
        private long originSamples = UNKNOWN_SAMPLE;
        private long bytes;

        Session(String source) { this.source = source; }

        String source() { return source; }
        DashScopeAsrClient.Stream stream() { return stream; }
        void setStream(DashScopeAsrClient.Stream stream) { this.stream = stream; }
        String failure() { return failure; }
        void setFailure(String failure) { this.failure = failure; }
        String languageCode() { return languageCode; }
        void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
        int sessionNo() { return sessionNo; }
        void setSessionNo(int sessionNo) { this.sessionNo = sessionNo; }
        int drained() { return drained; }
        void setDrained(int drained) { this.drained = drained; }
        int fedFrames() { return fedFrames; }
        void setFedFrames(int fedFrames) { this.fedFrames = fedFrames; }
        int ackedFrames() { return ackedFrames; }
        void setAckedFrames(int ackedFrames) { this.ackedFrames = ackedFrames; }
        long originSamples() { return originSamples; }
        void setOriginSamples(long originSamples) { this.originSamples = originSamples; }
        long bytes() { return bytes; }
        void addBytes(long bytes) { this.bytes += bytes; }
    }
}
