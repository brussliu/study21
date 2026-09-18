package com.study21.user.classroom;

import com.study21.user.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * 授業録音 / AI 授業記録のサービス。
 *
 * <p>録音・分塊アップロード・STT・転写追記・トリガー評価は user-api（所有者が記録ユーザー、
 * 低遅延）。ノート/要約の AI 生成は admin-api バッチ（batC61/batC62）で、両者は
 * CR_授業ノート情報.生成状態=PENDING の行だけで橋渡しする（docs/ARCHITECTURE.md §3）。</p>
 */
public interface ClassroomService {

    /** 配信する音声ファイル（Range/206 に使う。実体が見つからないときは null）。 */
    record AudioFile(Path path, String contentType, String fileName) {
    }

    /** 画面が使う設定（有効／無効・分塊長・録音最大時間・保存期間・日次上限）。 */
    ClassroomModels.OptionsResult options(UserPrincipal user);

    /** 前置詞プリセット一覧（GLOBAL + 自分のスコープ）。 */
    java.util.List<ClassroomModels.PresetView> presets(UserPrincipal user);

    /** 授業記録を作成（RECORDING）。 */
    ClassroomModels.RecordStatus create(UserPrincipal user, ClassroomModels.CreateRequest request);

    /** 録音開始（開始時刻を確定）。 */
    ClassroomModels.RecordStatus start(UserPrincipal user, long recordId);

    /** 分塊アップロード（multipart、連番つき）→ STT → セグメント保存 → トリガー評価。 */
    /**
     * 分塊アップロード（音声の保存 ＋ 書き起こし）。
     *
     * <p>`file` は**再生用**の音声（画面の `MediaRecorder` が作る webm）。`sttAudio` は
     * **書き起こし用**の音声で、あればこちらを STT へ送る。`MediaRecorder` の `timeslice` で切った
     * 2 つ目以降の分塊はコンテナのヘッダを持たず認識エンジンがデコードできないため、
     * 画面は分塊ごとに**ヘッダ無しの 16bit PCM（audio/L16）**を並行して作り、ここへ渡す。</p>
     */
    ClassroomModels.ChunkUploadResult uploadChunk(UserPrincipal user, long recordId, int seq, MultipartFile file,
                                                 MultipartFile sttAudio,
                                                 /** 画面が測った**実際の経過秒**（分塊の始まり・終わり）。null なら連番から計算する */
                                                 java.math.BigDecimal startSeconds,
                                                 java.math.BigDecimal endSeconds);

    /** 経過秒を渡さない場合（旧クライアント・テスト）。 */
    default ClassroomModels.ChunkUploadResult uploadChunk(UserPrincipal user, long recordId, int seq, MultipartFile file,
                                                          MultipartFile sttAudio) {
        return uploadChunk(user, recordId, seq, file, sttAudio, null, null);
    }

    /**
     * ストリーミング書き起こしに**音声を渡してよいか**確かめて、所有者（録音した本人）の
     * アカウント ID を返す。
     *
     * <p>セグメントの登録者に使うため、書き込みの前に所有を確かめる必要がある。加えて
     * **録音中の記録だけ**を受け付ける（停止・完了した記録へ音を流し込むと、確定した書き起こしが
     * あとから書き換わる）。HTTP の STT 入口と常時接続（WebSocket）の**両方がここを通る**ので、
     * 判定が 2 か所に分かれない。</p>
     */
    long requireSttAudioAccountId(UserPrincipal user, long recordId);

    /**
     * ストリーミング書き起こしの**収尾（finish）を渡してよいか**確かめて、所有者の
     * アカウント ID を返す。
     *
     * <p>終了（停止）の直後は、認識の尾部の確定文が遅れて届く。ここで断るとその音源の
     * 最後の文が丸ごと残らないので、**停止直後の猶予内だけ**は収尾を受け付ける
     * （分塊アップロードの遅延受け入れと同じ考え方）。</p>
     */
    long requireSttFinishAccountId(UserPrincipal user, long recordId);

    /**
     * 録音せずに取り込む（利用者の指示）: **音声ファイル（mp3）**または**貼り付けた文字起こし**。
     *
     * <p>どちらも「書き起こし（セグメント）」を作るところまで。AI まとめは今までどおり
     * `CLASSROOM_AI_NOTE_ENABLED` が有効なときだけ（`end` の最終まとめ／トリガー）動く。</p>
     */
    ClassroomModels.ChunkUploadResult importSource(UserPrincipal user, long recordId,
                                                   MultipartFile audioFile, String text,
                                                   Integer durationSeconds);

    /** 書き起こし用の音声を渡さない場合（テスト・旧クライアント）。 */
    default ClassroomModels.ChunkUploadResult uploadChunk(UserPrincipal user, long recordId, int seq,
                                                          MultipartFile file) {
        return uploadChunk(user, recordId, seq, file, null);
    }

    /**
     * ブラウザ（Web Speech API）の認識結果を 1 件足す（STT プロバイダー = browser のとき）。
     *
     * <p>認識は画面が行うので、サーバーは受け取ったテキストをセグメントとして保存し、
     * トリガー（フェーズノート）の評価だけを行う。連番はサーバーが振る。</p>
     */
    ClassroomModels.ChunkUploadResult appendTranscript(UserPrincipal user, long recordId,
                                                       ClassroomModels.TranscriptRequest request);

    /** 追記セグメントの取得（ポーリング用）。 */
    ClassroomModels.SegmentListResult segments(UserPrincipal user, long recordId, int afterSeq);

    /** 記録詳細（状態・転写全文・ノート一覧・前置詞）。 */
    ClassroomModels.RecordDetail detail(UserPrincipal user, long recordId);

    /** 一覧（学生=自分 / 保護者=家族 / 管理者=全体、ページング）。 */
    ClassroomModels.RecordListResult list(UserPrincipal user, String status, int page, int size);

    /** 終了（STOPPED → 最終まとめ PENDING を作る）。 */
    ClassroomModels.EndResult end(UserPrincipal user, long recordId);

    /** 元音声の配信（Range/206）。 */
    AudioFile audio(UserPrincipal user, long recordId);

    /** 削除（所有者のみ。音声実体も削除）。 */
    ClassroomModels.DeleteResult delete(UserPrincipal user, long recordId);
}
