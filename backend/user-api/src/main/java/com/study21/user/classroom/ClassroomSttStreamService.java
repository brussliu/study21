package com.study21.user.classroom;

import com.study21.common.core.stt.DashScopeAsrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
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
 *
 * <p><b>収尾（finish）は段階に分ける</b>（利用者の指示。以前は 1 回の呼び出しで
 * セッションを消していたので、尾部を取り切れなかった・保存できなかったときに
 * **やり直す手段が無かった**）:</p>
 * <ol>
 *   <li>新しい音声の受け付けを止める</li>
 *   <li>STT の最終結果（尾部の確定文）を集める</li>
 *   <li>最終的な書き起こしを保存する</li>
 *   <li>資源（接続・控えた音声）を解放する</li>
 * </ol>
 * <p><b>2 と 3 が済むまで完了にしない</b>。済まなかったときに何を残すかは理由で分ける:</p>
 * <ul>
 *   <li>{@link #RECOVERY_AWAIT_RESULTS} … **元の要求をまだ待てる**（接続は閉じずに残す）。
 *       やり直すと同じ要求の尾部をもう一度待つ（**認識し直さない**）</li>
 *   <li>{@link #RECOVERY_RESAVE_PENDING} … 取れた文を**保存できなかった**。
 *       取れた文（{@link PendingSegment}）を持ち越し、やり直しは**保存だけ**行う</li>
 *   <li>{@link #RECOVERY_RETRANSCRIBE} … 接続が復帰しない（`task-failed`・通信断）。
 *       死んだ接続は持たず、**控えた音声**（有界）から新しいセッションで認識し直す</li>
 * </ul>
 * <p>控えた状態は {@link #RETENTION_MILLIS} だけ保持し、過ぎたら片付ける（無限には増やさない）。
 * 完了した収尾の控えも同じ期限で片付けるが、そのあいだは `finish` を**何度呼んでも同じ結果**を
 * 返す（画面が応答を失って問い合わせ直しても「セッションが無い」と言わない）。</p>
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

    /* ---------------- 収尾（finalize）の段階 ---------------- */

    /** まだ音声を 1 つも受け取っていない（この音源は使われていない）。 */
    public static final String FINALIZE_NOT_STARTED = "NOT_STARTED";
    /** 音声を受け付けている（収尾はまだ）。 */
    public static final String FINALIZE_AUDIO_ACCEPTING = "AUDIO_ACCEPTING";
    /** 収尾の途中（新しい音声は受け付けない。最終結果の取り出しと保存の最中）。 */
    public static final String FINALIZE_FINALIZING = "FINALIZING";
    /** 収尾が完了した（必要な結果は全部保存できた）。 */
    public static final String FINALIZE_SAVED = "SAVED";
    /** 終端: この音源の音声は 1 つも受け取っていない（書き起こしは空で確定）。 */
    public static final String FINALIZE_NO_AUDIO = "NO_AUDIO";
    /** 終端: 音声は受け取ったが確定した文が 1 つも無い（無音・聞き取れず。書き起こしは空で確定）。 */
    public static final String FINALIZE_NO_UTTERANCE = "NO_UTTERANCE";
    /** 収尾が済んでいない（理由つき。{@link SourceFinalizeStatus#retryable()} が false なら終端）。 */
    public static final String FINALIZE_FAILED = "FAILED";

    /** 復帰の仕方: 元の要求をまだ待てる（接続は閉じずに残す。やり直しは**認識し直さない**）。 */
    public static final String RECOVERY_AWAIT_RESULTS = "AWAIT_RESULTS";
    /** 復帰の仕方: 取れた文の保存だけをやり直す（**認識し直さない**）。 */
    public static final String RECOVERY_RESAVE_PENDING = "RESAVE_PENDING";
    /** 復帰の仕方: 接続が復帰しない。控えた音声から**認識し直す**。 */
    public static final String RECOVERY_RETRANSCRIBE = "RETRANSCRIBE";

    /**
     * 収尾の状態（保存待ちの文・控えた音声・完了の控え）を保持する時間。
     *
     * <p>応答を失った画面が問い合わせ直し、やり直すのに十分な長さ。過ぎたら片付ける
     * （{@link #evictExpired(long)}。控えた音声は録音のファイルにも残っているので、
     * 音そのものは失われない）。</p>
     */
    static final long RETENTION_MILLIS = 60L * 60 * 1000;

    /** 尾部を待ち直す上限。超えたら「元の要求は待てない」＝控えた音声から認識し直す。 */
    static final int MAX_TAIL_AWAITS = 3;

    /**
     * 収尾のやり直しの上限。これを超えたら**終端**（`retryable=false`）にする。
     *
     * <p>際限なくやり直すと、画面が永久に再試行を出し続けて授業を終えられなくなる。終端にしたら
     * `finish` は**失敗として返さない**（`notice` で知らせる）ので、画面は先へ進める。
     * `/end` も、終端の音源があれば通して知らせを出す（書き起こしが不完全であることは残す）。</p>
     */
    static final int MAX_FINALIZE_ATTEMPTS = 4;

    /** 再認識のために控える音声の上限（最後の 2 分ぶん。それより前はすでにセッションへ流れている）。 */
    static final long MAX_RETAINED_PCM_BYTES = 16_000L * 2 * 120;

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

    /** 動いている認識セッション（授業記録 ID ＋ 音源ごと。収尾が済んだ音源は入っていない）。 */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    /** 音源ごとの**収尾の状態**（保存待ち・控えた音声・完了の控え。{@link #RETENTION_MILLIS} で片付ける）。 */
    private final Map<String, FinalizeState> finalizes = new ConcurrentHashMap<>();
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

    /** その録音・その音源のストリーミングが動いているか（＝音声を受け付けているか）。 */
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
     *
     * <p>`finalizeStatus` / `finalizeCompleted` / `recovery` / `savedCount` / `pendingCount` は
     * **収尾の状態**（`finish` の戻りにもそのまま入る）。画面はこれで「終わったと言ってよいか」と
     * 「やり直しで何が起きるか」を判断できる。`notice` は**失敗ではない知らせ**
     * （音声なし・発話なしの終端、やり直しても直らない終端など）。</p>
     */
    public record StreamPush(
            /** まだ確定していない文（画面は 1 行だけ上書きして出す）。 */
            String interim,
            /** この回で確定して保存したセグメント。 */
            List<ClassroomModels.SegmentView> added,
            /** やり直せば直る失敗の理由（日本語。null なら失敗していない）。 */
            String error,
            /** この音源で処理し終えたフレーム番号（0 はまだ無し）。 */
            int processedFrames,
            /** この音源の収尾の段階（{@link #FINALIZE_AUDIO_ACCEPTING} など）。 */
            String finalizeStatus,
            /** 収尾が完了したか（必要な結果を全部保存できたか）。 */
            boolean finalizeCompleted,
            /** 失敗したときの復帰の仕方（{@link #RECOVERY_AWAIT_RESULTS} など）。 */
            String recovery,
            /** この音源で保存できた文の数。 */
            int savedCount,
            /** 保存待ちで残している文の数。 */
            int pendingCount,
            /** 失敗ではない知らせ（日本語。null なら無し）。 */
            String notice) {

        /** 収尾の状態を載せない呼び出し（テスト・旧クライアント互換）。 */
        public StreamPush(String interim, List<ClassroomModels.SegmentView> added, String error,
                          int processedFrames) {
            this(interim, added, error, processedFrames, FINALIZE_AUDIO_ACCEPTING, false, null, 0, 0, null);
        }
    }

    /** 音源ごとの収尾の状態（画面の問い合わせ・`/end` の検証が見る）。 */
    public record SourceFinalizeStatus(
            /** 音源（`mic` / `shared`）。 */
            String source,
            /** 音源の表示名（日本語）。 */
            String label,
            /** 段階（{@link #FINALIZE_NOT_STARTED} など）。 */
            String status,
            /** 段階の表示名（日本語）。 */
            String statusLabel,
            /** この音源の収尾が**済んだ**か（音声なし・発話なしの終端も true）。 */
            boolean completed,
            /** まだやり直せるか（false なら終端。画面は再試行を出し続けない）。 */
            boolean retryable,
            /** 済んでいない理由（日本語。null なら無し）。 */
            String reason,
            /** 復帰の仕方（{@link #RECOVERY_AWAIT_RESULTS} など。null なら無し）。 */
            String recovery,
            /** 保存できた文の数。 */
            int savedCount,
            /** 保存待ちで残している文の数。 */
            int pendingCount,
            /** 受け取ったフレーム数。 */
            int audioFrames,
            /** 音声を受け取っているか（`/end` の分塊の検証に使う）。 */
            boolean audioReceived,
            /** 再認識のために音声を控えてあるか。 */
            boolean audioRetained,
            /** 控えた音声の在り処（`memory:…` / `recording:…`）。 */
            String retainedAudioRef,
            /** 認識の尾部（`task-finished`）を取り切れたか。 */
            boolean tailCollected,
            /** 収尾の試行回数。 */
            int attempts,
            /** 最後に状態が変わった時刻（ISO-8601）。 */
            String updatedAt) {
    }

    /** その録音の収尾の状態（両方の音源。画面が応答を失ったあとに問い合わせる）。 */
    public record FinalizeStatus(
            long recordId,
            /** 全体の段階（`COMPLETE` / `FAILED` / `FINALIZING` / `AUDIO_ACCEPTING` / `NOT_STARTED`）。 */
            String status,
            /** 全体の段階の表示名（日本語）。 */
            String statusLabel,
            /** **両方の音源の収尾が済んでいるか**（`/end` を通してよいかの材料）。 */
            boolean completed,
            /** どれかの音源で音声を受け取っているか。 */
            boolean audioReceived,
            /** まだやり直せる音源が残っているか（画面はこれで再試行を出す）。 */
            boolean retryable,
            /** 画面に出す知らせ（日本語。null なら無し）。 */
            String notice,
            List<SourceFinalizeStatus> sources) {
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
     * 保存する 1 文（**認識した時点で**位置・発話キー・話者・言語を確定させたもの）。
     *
     * <p>保存に失敗した文はこの形で持ち越す。やり直しでは**認識し直さず**これを保存するだけなので、
     * 位置も発話キーも動かない（同じ行に寄る＝行が増えない）。</p>
     */
    private record PendingSegment(String source, String utteranceKey, String speaker, String language,
                                  String text, BigDecimal start, BigDecimal end) {
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
        evictExpired(nowMillis());
        String mapKey = keyOf(recordId, normalized);
        FinalizeState state = finalizes.computeIfAbsent(mapKey, id -> new FinalizeState(normalized));
        Session session = sessions.get(mapKey);
        if (session == null) {
            /*
             * 収尾が済んだあとに音が届いた。**番号では「送り直し」と「続きの録音」を区別できない**
             * （画面は録音を再開すると番号を 1 から数え直す）ので、番号では弾かない。同じ音の
             * 送り直しは下の**位置の判定**（すでに処理した位置より古い）で落ちるので、
             * 二重に認識しない。続きの録音なら新しいセッションを開いて書き起こす。
             */
            if (state.awaiting != null) {
                // 前の収尾が残した接続は、新しい音が来た時点で用済み（閉じて控えだけ残す）
                closeStream(state.awaiting);
                state.awaiting = null;
            }
            session = openSession(recordId, normalized);
            sessions.put(mapKey, session);
        }
        // この回の番号（渡されないとき＝HTTP の分塊は到着順）
        int frame = frameNo > 0 ? frameNo : session.fedFrames() + 1;
        if (session.failure() != null) {
            return pushOf(recordId, normalized, session, "", List.of(), session.failure());
        }
        int ackableFrame = frame;
        boolean accepted = false;
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
                    return pushOf(recordId, normalized, session, "",
                            List.of(), "書き起こしの音声が長すぎます。録音を分けてください。");
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
                accepted = true;
                // 断線したときのために控えておく（有界。最後の 2 分ぶんだけ）
                retain(state, recordId, pcm, position);
            }
        } else {
            // 送り直し（すでに認識へ流した音）: 流し直さない（同じ音を二度認識しない）
            log.info("classroom stream frame already sent. recordId={} source={} frame={} confirmed={}",
                    recordId, normalized, frame, session.ackedFrames());
        }

        List<String> warnings = new ArrayList<>();
        List<ClassroomModels.SegmentView> added = new ArrayList<>();
        flushPending(state, recordId, added, warnings);
        Drain drained = drain(state, session, recordId, normalized, ackableFrame, warnings);
        added.addAll(drained.added());
        if (drained.failure() != null) {
            log.warn("classroom stream stt failed. recordId={} source={} reason={}",
                    recordId, normalized, drained.failure());
            /*
             * **認識そのものが失敗した**。以前はここでセッションを消していたので、控えも無ければ
             * やり直す手段が無かった（画面は送り直せるが、認識し直せない音は消える）。いまは
             * 死んだ接続は閉じるが、**控えた音声と保存待ちの文は残す**（`RETRANSCRIBE` で復帰）。
             */
            markUnrecoverable(state, session, drained.failure());
            closeSession(recordId, normalized);
            state.updatedAt = nowMillis();
            return pushOf(recordId, normalized, session, "", added, drained.failure());
        }
        if (accepted) {
            /*
             * 新しい音が届いた＝**書き起こしは「完了」ではない**（続きを録っている）。
             * 収尾の段階を戻すが、**保存待ちの文と控えた音声は捨てない**（やり直しに使う）。
             */
            state.stage = FINALIZE_AUDIO_ACCEPTING;
            state.reason = null;
            state.notice = null;
            state.recovery = null;
            state.tailCollected = false;
            state.attempts = 0;
            state.saved = List.of();
            state.updatedAt = nowMillis();
        }
        return pushOf(recordId, normalized, session, drained.interim(), added,
                warnings.isEmpty() ? null : String.join(" ", warnings));
    }

    /**
     * その録音の収尾の状態（画面の問い合わせ・`/end` の検証）。
     *
     * <p>応答を失った画面はこれで「どこまで済んだか」「やり直せるか」を確かめてから、安全に
     * やり直せる（`finish` は何度呼んでも同じ結果に落ち着く）。</p>
     */
    public FinalizeStatus finalizeStatus(long recordId) {
        evictExpired(nowMillis());
        List<SourceFinalizeStatus> sources = new ArrayList<>();
        for (String source : List.of(SOURCE_MIC, SOURCE_SHARED)) {
            sources.add(sourceStatus(recordId, source));
        }
        boolean completed = sources.stream().allMatch(SourceFinalizeStatus::completed);
        boolean retryable = sources.stream().anyMatch(SourceFinalizeStatus::retryable);
        boolean failed = sources.stream().anyMatch(item -> FINALIZE_FAILED.equals(item.status()));
        boolean finalizing = sources.stream().anyMatch(item -> FINALIZE_FINALIZING.equals(item.status()));
        boolean audio = sources.stream().anyMatch(SourceFinalizeStatus::audioReceived);
        boolean waiting = sources.stream().anyMatch(item -> FINALIZE_AUDIO_ACCEPTING.equals(item.status()));
        String status = completed ? "COMPLETE"
                : finalizing ? FINALIZE_FINALIZING
                : waiting ? FINALIZE_AUDIO_ACCEPTING
                : failed ? FINALIZE_FAILED
                : FINALIZE_NOT_STARTED;
        String notice = null;
        if (!completed) {
            List<String> reasons = sources.stream()
                    .filter(item -> !item.completed())
                    .map(item -> "【" + item.label() + "】" + (item.reason() == null
                            ? item.statusLabel() : item.reason()))
                    .toList();
            notice = "書き起こしの収尾が済んでいない音源があります。" + String.join(" ", reasons);
        } else if (!audio) {
            notice = "この録音には音声が送られていません（書き起こしは空です）。";
        }
        return new FinalizeStatus(recordId, status, overallLabel(status, retryable), completed,
                audio, retryable, notice, sources);
    }

    /**
     * 終わりを伝えて収尾を進める（**何度呼んでも同じ結果に落ち着く**）。
     *
     * <p><b>段階</b>: (1) 新しい音声の受け付けを止める → (2) STT の最終結果を集める →
     * (3) 最終的な書き起こしを保存する → (4) 資源を解放する。(2)(3) が済むまで完了にしない。</p>
     *
     * <p>済まなかったときは状態を**残す**: 取れた文は保存待ちとして持ち越し（やり直しは保存だけ＝
     * 認識し直さない）、接続が死んでいる場合は控えた音声から認識し直す。成功したあとの 2 回目は
     * **保存済みの文をそのまま返す**（「セッションが無い」とは言わない）。やり直しても直らない
     * 終端に達したら、`error` ではなく `notice` で知らせる（画面が永久に再試行しないため）。</p>
     */
    public StreamPush finish(long recordId, long accountId, String source) {
        String normalized = normalizeSource(source);
        long now = nowMillis();
        evictExpired(now);
        String mapKey = keyOf(recordId, normalized);
        FinalizeState state = finalizes.computeIfAbsent(mapKey, id -> new FinalizeState(normalized));
        // すでに決着している（完了・やり直しても直らない終端）: 同じ結果を返す＝冪等
        if (isCompleted(state.stage) || (FINALIZE_FAILED.equals(state.stage) && !isRetryable(state))) {
            return settledPush(state);
        }

        // ---- 段階 1: 新しい音声の受け付けを止める
        Session session = sessions.remove(mapKey);
        if (session == null) {
            session = state.awaiting;
        }
        if (session == null && RECOVERY_RETRANSCRIBE.equals(state.recovery) && state.retainedBytes > 0) {
            // 前の収尾で「接続は復帰しない」と判断した。控えた音声から認識し直す
            session = retranscribe(recordId, normalized, state);
        }
        if (session == null) {
            if (!state.pending.isEmpty()) {
                // 接続はもう無いが、**保存待ちの文が残っている**（認識し直さずに保存だけやり直す）
                return resaveWithoutConnection(recordId, normalized, state);
            }
            // 音を 1 つも送っていない（この音源は使われていない）＝明示の終端
            state.stage = FINALIZE_NO_AUDIO;
            state.reason = "この音源の音声は送られていません（書き起こしは空です）。";
            state.notice = state.reason;
            state.recovery = null;
            state.saved = List.of();
            state.updatedAt = now;
            log.info("classroom stream stt finished without audio. recordId={} source={}",
                    recordId, normalized);
            return settledPush(state);
        }
        state.awaiting = session;
        state.stage = FINALIZE_FINALIZING;
        state.updatedAt = now;
        if (session.fedFrames() <= 0 && session.bytes() <= 0) {
            // セッションは開いたが音声が 1 バイトも来ていない＝音声なしの終端
            releaseQuietly(state, session);
            state.stage = FINALIZE_NO_AUDIO;
            state.reason = "この音源の音声は送られていません（書き起こしは空です）。";
            state.notice = state.reason;
            state.recovery = null;
            state.saved = List.of();
            state.updatedAt = nowMillis();
            return settledPush(state);
        }

        // ---- 段階 2: STT の最終結果を集める
        boolean connectionDead = session.failure() != null;
        if (!connectionDead && state.tailAwaits >= MAX_TAIL_AWAITS && state.retainedBytes > 0) {
            /*
             * 尾部を何度も待ち直した。**元の要求はもう待てない**ものとして扱い、控えた音声から
             * 認識し直す（ここが (a)「まだ待てる」との分かれ目）。
             */
            log.info("classroom stream tail awaits exhausted; retranscribing."
                    + " recordId={} source={} awaits={}", recordId, normalized, state.tailAwaits);
            session = retranscribe(recordId, normalized, state);
            connectionDead = session.failure() != null;
        }
        if (connectionDead) {
            // 死んだ接続は「保留の状態」として持たない（控えた音声と保存待ちだけを残す）
            closeStream(session);
            state.awaiting = null;
        } else {
            session.stream().requestFinish();
        }

        List<String> warnings = new ArrayList<>();
        List<ClassroomModels.SegmentView> added = new ArrayList<>();
        // ---- 段階 3: 保存する（保存待ちを先に片付ける＝認識し直さない）
        flushPending(state, recordId, added, warnings);
        Drain drained = drain(state, session, recordId, normalized, session.fedFrames(), warnings);
        added.addAll(drained.added());
        boolean tailCollected = false;
        if (!connectionDead) {
            tailCollected = session.stream().awaitTail(session.timeoutSeconds());
            if (tailCollected) {
                // 遅れて確定した文を取り出す（この回だけの取り出し。同じ文は発話キーで UPDATE に寄る）
                Drain late = drain(state, session, recordId, normalized, session.fedFrames(), warnings);
                added.addAll(late.added());
                drained = new Drain(added, drained.allSaved() && late.allSaved(),
                        late.failure() != null ? late.failure() : drained.failure(), late.interim());
            }
        }
        state.tailCollected = tailCollected;
        state.attempts += 1;
        // 受け取り確認の番号は状態にも写す（完了の控え・状態照会が同じ値を返すように）
        state.ackedFrames = Math.max(state.ackedFrames, session.ackedFrames());
        state.fedFrames = Math.max(state.fedFrames, session.fedFrames());
        state.updatedAt = nowMillis();
        log.info("classroom stream stt finished. recordId={} source={} attempt={} saved={} pending={}"
                        + " confirmed={} tail={} failure={}",
                recordId, normalized, state.attempts, added.size(), state.pending.size(),
                session.ackedFrames(), tailCollected, session.failure());

        // ---- 段階 4: 判定して資源を解放する
        boolean received = state.savedCount > 0;
        String recovery = null;
        if (!state.pending.isEmpty() || !drained.allSaved()) {
            // 取れた文を保存できなかった（やり直しは**保存だけ**。認識し直さない）
            recovery = RECOVERY_RESAVE_PENDING;
        } else if (connectionDead) {
            recovery = state.retainedBytes > 0 ? RECOVERY_RETRANSCRIBE : null;
        } else if (!tailCollected) {
            recovery = RECOVERY_AWAIT_RESULTS;
        }
        state.recovery = recovery;
        if (recovery == null && received) {
            // **必要な結果は全部保存できた**。ここで初めて完了にする
            state.stage = FINALIZE_SAVED;
            state.reason = null;
            state.notice = null;
            state.saved = List.copyOf(added);
            state.pending.clear();
            releaseQuietly(state, session);
            log.info("classroom stream stt finalize complete. recordId={} source={} saved={}",
                    recordId, normalized, state.savedCount);
        } else if (recovery == null) {
            // 音声は受け取ったが発話が 1 つも無い＝明示の終端（無音・聞き取れず）
            state.stage = FINALIZE_NO_UTTERANCE;
            state.reason = "この音源では発話が認識されませんでした（書き起こしは空です）。";
            state.notice = state.reason;
            state.saved = List.of();
            releaseQuietly(state, session);
            log.info("classroom stream stt finalize without utterance. recordId={} source={}",
                    recordId, normalized);
        } else {
            state.stage = FINALIZE_FAILED;
            state.reason = reasonOf(state, recovery, session, drained, tailCollected, warnings);
            boolean canAwait = !connectionDead && !tailCollected;
            if (canAwait) {
                // (a) **元の要求はまだ待てる**。接続は残す（やり直しは認識し直さない）
                state.awaiting = session;
                state.tailAwaits += 1;
            } else {
                releaseQuietly(state, session);
                if (RECOVERY_RETRANSCRIBE.equals(recovery) && state.retainedBytes <= 0) {
                    // 認識し直す材料が無い＝これ以上やり直しても直らない（終端）
                    state.reason = state.reason + "（音声の控えが無いため、この音源はこれ以上やり直せません。）";
                }
            }
            if (!isRetryable(state)) {
                // 終端: **失敗として返さない**（画面が永久に再試行しないように知らせで伝える）
                ensureTerminalNotice(state);
            }
            log.warn("classroom stream stt finalize incomplete. recordId={} source={} recovery={}"
                            + " attempts={} pending={} retryable={} reason={}",
                    recordId, normalized, recovery, state.attempts, state.pending.size(),
                    isRetryable(state), state.reason);
        }
        return settledPush(state, added);
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

    /** 録音が終わった・失敗したときに片付ける（すべての音源。収尾の控えも解放する）。 */
    public void close(long recordId) {
        close(recordId, SOURCE_MIC);
        close(recordId, SOURCE_SHARED);
        for (String source : List.of(SOURCE_MIC, SOURCE_SHARED)) {
            FinalizeState state = finalizes.remove(keyOf(recordId, source));
            if (state != null && state.awaiting != null) {
                closeStream(state.awaiting);
                state.awaiting = null;
            }
        }
        newestSamples.keySet().removeIf(key -> key.startsWith(recordId + "#"));
        sessionNumbers.keySet().removeIf(key -> key.startsWith(recordId + "#"));
    }

    /**
     * 1 音源ぶんの認識セッションを閉じる（**収尾の控えと時間軸の基準は残す**）。
     *
     * <p>時間軸の基準を残すのは、続きを録るときに時刻が 0 に戻らないため。収尾の控えを残すのは、
     * 保存待ちの文・控えた音声をやり直しに使うため（{@link #RETENTION_MILLIS} で片付ける）。</p>
     */
    public void close(long recordId, String source) {
        Session session = sessions.remove(keyOf(recordId, source));
        if (session == null) {
            return;
        }
        closeStream(session);
    }

    /**
     * 保持期限を過ぎた収尾の状態を片付ける（**資源を無限に増やさない**）。
     *
     * <p>完了した控え（`finish` の冪等のため）も、保存待ちの文・控えた音声も、最後に状態が
     * 変わってから {@link #RETENTION_MILLIS} で解放する。開いたまま残した接続
     * （{@link #RECOVERY_AWAIT_RESULTS}）も一緒に閉じる。期限は呼ぶ側が渡す
     * （テストで時計を進められるように）。</p>
     *
     * @return 片付けた件数
     */
    int evictExpired(long nowMillis) {
        int before = finalizes.size();
        finalizes.values().removeIf(state -> {
            boolean expired = nowMillis - state.updatedAt > RETENTION_MILLIS;
            if (expired && state.awaiting != null) {
                closeStream(state.awaiting);
                state.awaiting = null;
            }
            return expired;
        });
        int removed = before - finalizes.size();
        if (removed > 0) {
            log.info("classroom stream finalize state evicted. removed={} remaining={}",
                    removed, finalizes.size());
        }
        return removed;
    }

    /** 保持期限を過ぎた収尾の状態を片付ける（いまの時刻で）。 */
    public int evictExpired() {
        return evictExpired(nowMillis());
    }

    /* ---------------- 保存（取り出し・やり直しの共通） ---------------- */

    /**
     * 確定した文を取り出して保存する（`push` と `finish` の共通）。
     *
     * <p><b>受け取り確認の正しさはここで決まる</b>: 保存できなかった文があると、その文より先へは
     * 進めない（`drained` を進めない）。同じ文は次の回にもう一度取り出され、発話キーで UPDATE に
     * 寄るので**行は増えない**。保存できなかった文は {@link PendingSegment} として状態に残す
     * （やり直しでは**認識し直さず**それを保存する）。</p>
     *
     * @param frame この回で「処理済み」としてよいフレーム番号（`finish` は送り終えた番号を渡す）
     */
    private Drain drain(FinalizeState state, Session session, long recordId, String source, int frame,
                        List<String> warnings) {
        DashScopeAsrClient.TimedSnapshot snapshot = session.stream().pollTimed(session.drained());
        boolean twoSources = twoSources(recordId);
        List<ClassroomModels.SegmentView> added = new ArrayList<>();
        boolean prefixSaved = true;
        /*
         * 取り出した文は**すべて**この回で引き取る（保存できた文は行になり、できなかった文は
         * 保存待ちになる）。引き取った位置（`drained`）を進めておかないと、次の回に同じ文が
         * もう一度取り出され、保存待ちと二重に数える（画面へ同じ文を 2 回返す）。
         * **受け取り確認（`ackedFrames`）は別**: 保存できなかった文があるうちは進めない。
         */
        int consumed = snapshot.finished().size();
        for (DashScopeAsrClient.TimedSentence sentence : snapshot.finished()) {
            String text = sentence.text() == null ? "" : sentence.text().trim();
            if (text.isEmpty()) {
                // 空の確定は捨てる（埋め草を入れない）。捨てた文は保存済みと同じ扱いでよい
                continue;
            }
            PendingSegment prepared = prepare(session, recordId, source, twoSources, sentence);
            ClassroomModels.SegmentView saved = persist(recordId, prepared, warnings);
            if (saved != null) {
                added.add(saved);
                state.savedCount += 1;
            } else {
                // **保存待ちとして残す**（やり直しはこの文を保存するだけ＝認識し直さない）。
                // 1 文でも保存できなかったら、そこから先は「処理済み」にしない（受け取り確認は
                // 進めない）。残りの文の保存は続ける（1 つの悪い文で、そのあとの良い文まで消える方が損）
                state.pending.add(prepared);
                prefixSaved = false;
            }
        }
        session.setDrained(session.drained() + consumed);
        /*
         * **認識そのものが失敗した回は受け取り確認を進めない**（成功として返さないのと同じ理由）。
         * 進めると画面がその音を捨て、書き起こしに残らない区間が確定してしまう。送り直せば
         * 新しいセッションで認識できる。
         */
        if (prefixSaved && state.pending.isEmpty() && snapshot.failure() == null) {
            session.setAckedFrames(Math.max(session.ackedFrames(), frame));
        }
        return new Drain(added, prefixSaved, snapshot.failure(),
                snapshot.interim() == null ? "" : snapshot.interim().text());
    }

    /** 保存待ちの文を保存し直す（**認識し直さない**）。保存できた文は待ち行列から外す。 */
    private void flushPending(FinalizeState state, long recordId,
                              List<ClassroomModels.SegmentView> added, List<String> warnings) {
        if (state.pending.isEmpty()) {
            return;
        }
        List<PendingSegment> remaining = new ArrayList<>();
        for (PendingSegment pending : state.pending) {
            ClassroomModels.SegmentView saved = persist(recordId, pending, warnings);
            if (saved == null) {
                remaining.add(pending);
            } else {
                added.add(saved);
                state.savedCount += 1;
            }
        }
        state.pending.clear();
        state.pending.addAll(remaining);
    }

    /**
     * 認識した 1 文を**保存できる形**にする（位置・発話キー・話者・言語をここで確定させる）。
     *
     * <p>発話キーをここで固定するので、保存に失敗してやり直しても**同じキー**になり、
     * INSERT ではなく UPDATE に寄る（行が増えない）。</p>
     */
    private PendingSegment prepare(Session session, long recordId, String source, boolean twoSources,
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
        return new PendingSegment(source, utteranceKey, speakerOf(source, twoSources),
                session.languageCode(), sentence.text(), start, end);
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
     * 「処理済み」になり（呼ぶ側が `drained` を進める）、ここで諦めた文は呼ぶ側が保存待ちとして
     * 残す（＝黙って捨てない。やり直しでは認識し直さずに保存する）。</p>
     *
     * @return 保存できた文（やり直しても駄目だったときは null）
     */
    private ClassroomModels.SegmentView persist(long recordId, PendingSegment pending,
                                                List<String> warnings) {
        for (int attempt = 1; attempt <= SAVE_ATTEMPTS; attempt += 1) {
            try {
                return save(recordId, pending);
            } catch (RuntimeException cause) {
                String reason = cause.getMessage() == null ? cause.getClass().getSimpleName()
                        : cause.getMessage().split("\n")[0];
                if (attempt < SAVE_ATTEMPTS && sleepBeforeRetry()) {
                    log.warn("classroom stream segment save retry. recordId={} source={} attempt={} reason={}",
                            recordId, pending.source(), attempt, reason);
                    continue;
                }
                warnings.add("書き起こしの一部を保存できませんでした。");
                log.warn("classroom stream segment save failed; kept as pending, position not confirmed."
                                + " recordId={} source={} attempts={} key={} reason={}",
                        recordId, pending.source(), attempt, pending.utteranceKey(), reason);
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

    private ClassroomModels.SegmentView save(long recordId, PendingSegment pending) {
        BigDecimal start = pending.start();
        BigDecimal end = pending.end();
        String text = pending.text();
        String utteranceKey = pending.utteranceKey();

        ClassroomSegmentEntity existing = segmentMapper.findByUtteranceKey(recordId, utteranceKey);
        if (existing != null) {
            return updateExisting(existing, recordId, start, end, text, pending.language(),
                    pending.speaker(), utteranceKey);
        }

        for (int attempt = 0; attempt < 5; attempt += 1) {
            Integer maxSeq = segmentMapper.maxSeq(recordId);
            int seq = (maxSeq == null ? 0 : maxSeq) + 1;
            ClassroomSegmentEntity segment = new ClassroomSegmentEntity();
            segment.setRecordId(recordId);
            segment.setSeq(seq);
            segment.setStartOffsetSeconds(start);
            segment.setEndOffsetSeconds(end);
            segment.setSpeaker(pending.speaker());
            segment.setUtteranceKey(utteranceKey);
            segment.setSource(pending.source());
            segment.setText(text);
            segment.setLanguage(pending.language());
            try {
                segmentMapper.insert(segment);
                recordMapper.addTranscribedChars(recordId, text.length());
                log.info("classroom stream segment stored. recordId={} seq={} source={} speaker={} key={} start={}",
                        recordId, seq, pending.source(), pending.speaker(), utteranceKey, start);
                return new ClassroomModels.SegmentView(segment.getSegmentId(), seq,
                        start.doubleValue(), end.doubleValue(), pending.speaker(), text,
                        pending.language(), null);
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
                    return updateExisting(raced, recordId, start, end, text, pending.language(),
                            pending.speaker(), utteranceKey);
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

    /* ---------------- 収尾の内部 ---------------- */

    /**
     * 接続はもう無いが保存待ちの文が残っているときのやり直し（**保存だけ**。認識し直さない）。
     *
     * <p>保存が済んだら、残りの尾部は取得できない（接続が無く、控えた音声も無い）。それでも
     * **完了とは言わない**: 理由つきの終端にして、`error` ではなく `notice` で知らせる
     * （画面が永久に再試行しないため）。`/end` は終端の音源があれば通し、知らせを出す。</p>
     */
    private StreamPush resaveWithoutConnection(long recordId, String source, FinalizeState state) {
        state.stage = FINALIZE_FINALIZING;
        List<String> warnings = new ArrayList<>();
        List<ClassroomModels.SegmentView> added = new ArrayList<>();
        flushPending(state, recordId, added, warnings);
        state.attempts += 1;
        state.updatedAt = nowMillis();
        if (!state.pending.isEmpty()) {
            state.stage = FINALIZE_FAILED;
            state.recovery = RECOVERY_RESAVE_PENDING;
            state.reason = "書き起こしの一部を保存できませんでした（" + state.pending.size()
                    + " 文を残しています）。";
        } else if (state.tailCollected) {
            // 保存待ちが片付き、尾部も取れている＝**ここで完了**（未完の理由が無くなった）
            state.stage = state.savedCount > 0 ? FINALIZE_SAVED : FINALIZE_NO_UTTERANCE;
            state.reason = state.savedCount > 0 ? null
                    : "この音源では発話が認識されませんでした（書き起こしは空です）。";
            state.notice = state.savedCount > 0 ? null : state.reason;
            state.recovery = null;
            state.saved = List.copyOf(added);
        } else {
            // 保存待ちは片付いたが、尾部は取れず接続ももう無い＝ここが終端
            state.stage = FINALIZE_FAILED;
            state.recovery = RECOVERY_RETRANSCRIBE;
            state.reason = "認識の尾部（最後の確定文）を取り切れませんでした（接続が復帰せず、"
                    + "認識し直す音声の控えもありません）。";
        }
        ensureTerminalNotice(state);
        log.warn("classroom stream stt resaved without connection. recordId={} source={} saved={}"
                        + " pending={} retryable={}", recordId, source, added.size(),
                state.pending.size(), isRetryable(state));
        return settledPush(state, added);
    }

    /**
     * 死んだ接続の代わりに、**控えてある音声を新しいセッションで認識し直す**（復帰の仕方 (b)）。
     *
     * <p>控えは最後の {@link #MAX_RETAINED_PCM_BYTES} ぶんだけ。控えた先頭の位置（{@code retainedStartSample}）
     * を起点にするので、文の時刻（＝発話キー）は元の時間軸のままで、保存済みの行には UPDATE で寄る。</p>
     */
    private Session retranscribe(long recordId, String source, FinalizeState state) {
        List<byte[]> frames = new ArrayList<>(state.retained);
        Session fresh = openSession(recordId, source);
        if (fresh.failure() != null || frames.isEmpty()) {
            return fresh;
        }
        fresh.setOriginSamples(state.retainedStartSample);
        fresh.setFedFrames(state.ackedFrames);
        fresh.setAckedFrames(state.ackedFrames);
        for (byte[] frame : frames) {
            fresh.stream().send(frame);
            fresh.addBytes(frame.length);
        }
        log.info("classroom stream retranscribing retained audio. recordId={} source={} frames={} bytes={}",
                recordId, source, state.retainedFrames, state.retainedBytes);
        return fresh;
    }

    /** 認識そのものが失敗した（接続が復帰しない）ときの状態（控えた音声でやり直せるようにする）。 */
    private void markUnrecoverable(FinalizeState state, Session session, String reason) {
        state.stage = FINALIZE_FAILED;
        state.recovery = RECOVERY_RETRANSCRIBE;
        state.reason = reason + "（控えた音声から認識し直します）。";
        state.awaiting = null;
        if (session != null && session.ackedFrames() > 0) {
            state.ackedFrames = Math.max(state.ackedFrames, session.ackedFrames());
        }
    }

    /** 収尾が済んでいない理由（日本語）。**どの復帰の仕方かを言葉にも出す**。 */
    private String reasonOf(FinalizeState state, String recovery, Session session, Drain drained,
                            boolean tailCollected, List<String> warnings) {
        if (RECOVERY_RESAVE_PENDING.equals(recovery)) {
            return "書き起こしの一部を保存できませんでした（" + state.pending.size()
                    + " 文を残しています。受け取り確認は進めていません。やり直すと、認識し直さずに保存します）。";
        }
        if (session.failure() != null) {
            return session.failure() + "（控えた音声から認識し直します）。";
        }
        if (!tailCollected) {
            return "認識の尾部（最後の確定文）を時間内に取り切れませんでした。"
                    + "遅れて確定した文は書き起こしに残らない可能性があります（やり直すと、"
                    + "同じ要求の尾部をもう一度待ちます）。";
        }
        if (!warnings.isEmpty()) {
            return String.join(" ", warnings);
        }
        if (drained.failure() != null) {
            return drained.failure();
        }
        return "書き起こしの収尾を完了できませんでした。";
    }

    /** 完了している段階か（音声なし・発話なしの終端も「済んだ」）。 */
    private static boolean isCompleted(String stage) {
        return FINALIZE_SAVED.equals(stage) || FINALIZE_NO_AUDIO.equals(stage)
                || FINALIZE_NO_UTTERANCE.equals(stage);
    }

    /** まだやり直せるか（終端なら画面は再試行を出し続けない）。 */
    private static boolean isRetryable(FinalizeState state) {
        if (isCompleted(state.stage) || FINALIZE_NOT_STARTED.equals(state.stage)) {
            return false;
        }
        if (FINALIZE_FAILED.equals(state.stage)) {
            // 上限まで試したら終端。何度でもやり直せると、画面が永久に再試行を出し続けて終われない
            return state.attempts < MAX_FINALIZE_ATTEMPTS;
        }
        return true;
    }

    /**
     * 終端なら「失敗として返さない」ための知らせを用意する（利用者の指示 9）。
     *
     * <p>`error` にすると画面は永久に【続きをやり直す】を出し続け、授業を終えられない。終端は
     * `error` ではなく `notice` に載せ、`/end` も通す（書き起こしが不完全であることは残す）。</p>
     */
    private static void ensureTerminalNotice(FinalizeState state) {
        if (FINALIZE_FAILED.equals(state.stage) && !isRetryable(state)) {
            state.notice = state.reason + "（これ以上やり直しても直りません。音声は録音に残っています。"
                    + "最終まとめには、この音源の書き起こしが入らないことがあります。）";
        }
    }

    /** その音源の収尾の状態（状態が無ければ「まだ使われていない」）。 */
    private SourceFinalizeStatus sourceStatus(long recordId, String source) {
        String normalized = normalizeSource(source);
        FinalizeState state = finalizes.get(keyOf(recordId, normalized));
        Session session = sessions.get(keyOf(recordId, normalized));
        if (state == null && session == null) {
            return new SourceFinalizeStatus(normalized, labelOf(normalized), FINALIZE_NOT_STARTED,
                    labelOf(FINALIZE_NOT_STARTED), true, false, null, null, 0, 0, 0, false, false,
                    null, false, 0, null);
        }
        String stage = state == null || FINALIZE_NOT_STARTED.equals(state.stage)
                ? FINALIZE_AUDIO_ACCEPTING : state.stage;
        int frames = session != null ? session.fedFrames() : (state == null ? 0 : state.fedFrames);
        boolean audio = frames > 0 || (session != null && session.bytes() > 0);
        return new SourceFinalizeStatus(normalized, labelOf(normalized), stage, labelOf(stage),
                isCompleted(state == null ? stage : state.stage),
                state != null && isRetryable(state),
                state == null ? null : state.reason, state == null ? null : state.recovery,
                state == null ? 0 : state.savedCount, state == null ? 0 : state.pending.size(),
                frames, audio, state != null && state.retainedBytes > 0,
                state == null ? null : state.retainedRef, state != null && state.tailCollected,
                state == null ? 0 : state.attempts,
                Instant.ofEpochMilli(state == null ? nowMillis() : state.updatedAt).toString());
    }

    /**
     * 決着した収尾の結果を返す（**完了の控え・やり直しても直らない終端**の両方）。
     *
     * <p>終端の失敗は `error` にせず `notice` に載せる（画面は「収尾成功」として先へ進み、
     * 利用者には知らせが出る。永久に再試行させない）。</p>
     */
    private StreamPush settledPush(FinalizeState state) {
        return settledPush(state, state.saved);
    }

    /** 決着した（または今回の）収尾の結果を返す。 */
    private StreamPush settledPush(FinalizeState state, List<ClassroomModels.SegmentView> added) {
        boolean completed = isCompleted(state.stage);
        boolean retryable = isRetryable(state);
        String error = completed || !retryable ? null : state.reason;
        String notice = state.notice;
        if (!completed && !retryable && notice == null) {
            notice = state.reason;
        }
        return new StreamPush("", added, error, state.ackedFrames, state.stage, completed,
                state.recovery, state.savedCount, state.pending.size(), notice);
    }

    /** いまの状態つきの結果（`push` の戻り）。 */
    private StreamPush pushOf(long recordId, String source, Session session, String interim,
                              List<ClassroomModels.SegmentView> added, String error) {
        FinalizeState state = finalizes.get(keyOf(recordId, source));
        String stage = state == null || FINALIZE_NOT_STARTED.equals(state.stage)
                ? FINALIZE_AUDIO_ACCEPTING : state.stage;
        return new StreamPush(interim, added, error, session == null ? 0 : session.ackedFrames(),
                stage, state != null && isCompleted(state.stage),
                state == null ? null : state.recovery, state == null ? 0 : state.savedCount,
                state == null ? 0 : state.pending.size(), state == null ? null : state.notice);
    }

    /** 段階の表示名（日本語）。 */
    static String labelOf(String stage) {
        return switch (stage == null ? "" : stage) {
            case FINALIZE_NOT_STARTED -> "音声は送られていません";
            case FINALIZE_AUDIO_ACCEPTING -> "書き起こし中（収尾はまだ）";
            case FINALIZE_FINALIZING -> "収尾の途中（最後の確定文を待っています）";
            case FINALIZE_SAVED -> "保存済み";
            case FINALIZE_NO_AUDIO -> "音声なし（終端）";
            case FINALIZE_NO_UTTERANCE -> "発話なし（終端）";
            case FINALIZE_FAILED -> "収尾が済んでいません";
            case SOURCE_MIC -> "マイク";
            case SOURCE_SHARED -> "共有した音";
            default -> stage == null ? "" : stage;
        };
    }

    /** 全体の段階の表示名（日本語）。 */
    private static String overallLabel(String status, boolean retryable) {
        return switch (status == null ? "" : status) {
            case "COMPLETE" -> "書き起こしの収尾は完了しています";
            case FINALIZE_FAILED -> retryable
                    ? "書き起こしの収尾が済んでいません（やり直せます）"
                    : "書き起こしの収尾が済んでいません（これ以上やり直しても直りません）";
            case FINALIZE_FINALIZING -> "書き起こしの収尾の途中です";
            case FINALIZE_AUDIO_ACCEPTING -> "書き起こし中です（収尾はまだ）";
            default -> "音声は送られていません";
        };
    }

    /** 控えた音声を足す（**有界**。上限を超えたら古い方から落とす＝資源を増やし続けない）。 */
    private void retain(FinalizeState state, long recordId, byte[] pcm, long position) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        if (state.retained.isEmpty()) {
            state.retainedStartSample = position;
        }
        state.retained.addLast(pcm.clone());
        state.retainedBytes += pcm.length;
        state.retainedFrames += 1;
        state.retainedRecordId = recordId;
        while (state.retainedBytes > MAX_RETAINED_PCM_BYTES && !state.retained.isEmpty()) {
            byte[] dropped = state.retained.removeFirst();
            state.retainedBytes -= dropped.length;
            state.retainedStartSample += dropped.length / BYTES_PER_SAMPLE;
        }
        state.retainedRef = "memory:" + (state.retainedBytes / (SAMPLE_RATE * BYTES_PER_SAMPLE)) + "s";
    }

    /** 収尾の最後の状態を書いて接続を解放する（控えた音声の在り処も残す）。 */
    private void releaseQuietly(FinalizeState state, Session session) {
        if (session != null && session.ackedFrames() > 0) {
            state.ackedFrames = Math.max(state.ackedFrames, session.ackedFrames());
        }
        if (session != null && session.fedFrames() > 0) {
            state.fedFrames = Math.max(state.fedFrames, session.fedFrames());
        }
        closeStream(session);
        state.awaiting = null;
        // 控えた音声は「録音の音声」にも残っている（音そのものは失われない）＝在り処を残す
        if (state.retainedBytes > 0 && state.retainedRef != null
                && !state.retainedRef.contains("recording:")) {
            ClassroomRecordEntity record = recordMapper.findById(state.retainedRecordId);
            if (record != null && record.getAudioPath() != null && record.getAudioName() != null) {
                state.retainedRef = state.retainedRef + " / recording:"
                        + record.getAudioPath() + "/" + record.getAudioName();
            }
        }
    }

    /** 接続を閉じる（すでに切れているときは何もしない）。 */
    private void closeStream(Session session) {
        if (session == null) {
            return;
        }
        try {
            session.stream().close();
        } catch (RuntimeException ignored) {
            // すでに切れているときは何もしない
        }
    }

    /** その音源の認識セッションを地図から外して閉じる（収尾の控えは残す）。 */
    private void closeSession(long recordId, String source) {
        closeStream(sessions.remove(keyOf(recordId, source)));
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
            session.setTimeoutSeconds(settings.sttTimeoutSeconds(snapshot));
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

    /** いまの時刻（ミリ秒）。保持期限の判定に使う（テストは {@link #evictExpired(long)} で進める）。 */
    long nowMillis() {
        return System.currentTimeMillis();
    }

    private static String key(long recordId, String source) {
        return recordId + "#" + source;
    }

    private static String keyOf(long recordId, String source) {
        return recordId + "#" + normalizeSource(source);
    }

    /** 1 音源ぶんの**認識セッション**（動いている接続とその進行）。 */
    private static final class Session {
        private final String source;
        private DashScopeAsrClient.Stream stream;
        private String failure;
        private String languageCode = "ja-JP";
        /** 尾部を待つ上限（秒。設定から解決した値）。 */
        private int timeoutSeconds = 60;
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
        int timeoutSeconds() { return timeoutSeconds; }
        void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
        }
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

    /**
     * 音源ごとの**収尾の状態**（保存待ちの文・控えた音声・完了の控え）。
     *
     * <p>{@link #RETENTION_MILLIS} を過ぎたら {@link #evictExpired(long)} が片付ける。</p>
     */
    private static final class FinalizeState {
        private final String source;
        /** 収尾の段階。 */
        private String stage = FINALIZE_NOT_STARTED;
        /** 済んでいない理由（日本語）。 */
        private String reason;
        /** 失敗ではない知らせ（日本語）。 */
        private String notice;
        /** 復帰の仕方（{@link #RECOVERY_AWAIT_RESULTS} など）。 */
        private String recovery;
        /** この収尾で保存できた文の数。 */
        private int savedCount;
        /** 収尾の試行回数（上限で終端にする）。 */
        private int attempts;
        /** 尾部を待ち直した回数（上限で認識し直しに切り替える）。 */
        private int tailAwaits;
        /** 認識の尾部（`task-finished`）を取り切れたか。 */
        private boolean tailCollected;
        /** **保存できなかった確定文**（やり直しはこれを保存するだけ＝認識し直さない）。 */
        private final List<PendingSegment> pending = new ArrayList<>();
        /** 完了したときに返す文（2 回目の `finish` はこれを返す）。 */
        private List<ClassroomModels.SegmentView> saved = List.of();
        /** 再認識のために控えた音声（有界。古い方から落とす）。 */
        private final ArrayDeque<byte[]> retained = new ArrayDeque<>();
        private long retainedBytes;
        private int retainedFrames;
        private long retainedStartSample;
        /** 控えた音声の在り処（画面に見せる）。 */
        private String retainedRef;
        /** 控えた音声の記録 ID（在り処に録音ファイルを添えるため）。 */
        private long retainedRecordId;
        /** **元の要求をまだ待てる接続**（{@link #RECOVERY_AWAIT_RESULTS} のときだけ残す）。 */
        private Session awaiting;
        /** 最後に返した受け取り確認の番号（収尾後に送り直された音を弾く判断に使う）。 */
        private int ackedFrames;
        private int fedFrames;
        private long updatedAt = System.currentTimeMillis();

        FinalizeState(String source) {
            this.source = source;
        }
    }
}
