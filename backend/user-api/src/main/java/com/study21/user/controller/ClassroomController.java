package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.classroom.ClassroomApiException;
import com.study21.user.classroom.ClassroomModels;
import com.study21.user.classroom.ClassroomService;
import com.study21.user.classroom.ClassroomSttStreamService;
import com.study21.user.reading.ReadingApiException;
import com.study21.user.reading.ReadingRange;
import com.study21.user.security.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 授業録音 / AI 授業記録の API（user-api）。
 *
 * <p>録音・分塊アップロード・STT・転写追記・トリガー評価はここ（user-api）。ノート/要約の
 * AI 生成は admin-api バッチ（batC61/batC62）で、画面は終了時に {@code runPath} の薄い入口を
 * 1 回だけ呼ぶ（AI 生図と同じ「送信 → ポーリング」の契約）。</p>
 *
 * <ul>
 *   <li>`GET /options` … 画面が使う設定（有効／無効・分塊長・録音最大時間・保存期間・日次上限）</li>
 *   <li>`GET /presets` … 前置詞プリセット一覧（GLOBAL + 自分のスコープ）</li>
 *   <li>`POST /classroom` … 作成（RECORDING）/ `POST /classroom/{id}/start` … 開始</li>
 *   <li>`POST /classroom/{id}/chunks` … 分塊アップロード（multipart、連番つき）。
 *       `file` は再生用の音声、`stt`（任意）は書き起こし用の 16kHz PCM（`audio/L16`）</li>
 *   <li>`GET /classroom/{id}/segments?afterSeq=` … 追記セグメント（ポーリング）</li>
 *   <li>`GET /classroom` / `GET /classroom/{id}` … 一覧・詳細</li>
 *   <li>`POST /classroom/{id}/end` … 終了（最終まとめ PENDING）</li>
 *   <li>`GET /classroom/{id}/audio` … 元音声の配信（Range/206）</li>
 *   <li>`DELETE /classroom/{id}` … 削除（所有者のみ）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user/classroom")
public class ClassroomController {

    private final ClassroomService classroomService;
    private final ClassroomSttStreamService streamService;

    public ClassroomController(ClassroomService classroomService,
                               ClassroomSttStreamService streamService) {
        this.classroomService = classroomService;
        this.streamService = streamService;
    }

    /**
     * **ストリーミング書き起こし**に音声を 1 回ぶん送る（話しながら文字が出る）。
     *
     * <p>本文は 16kHz モノラル 16bit の PCM（ヘッダ無し）。画面は 250〜500ms ごとに呼び、
     * 返ってきた `interim`（まだ確定していない文）を 1 行だけ出し、`added`（確定した文）を
     * 書き起こしへ足す。最初の呼び出しでセッションを開く。</p>
     *
     * <p>`source` は**音源**（`mic`＝マイク／`shared`＝共有した音）。音源ごとに別のセッションを
     * 持ち、話者ラベルも音源から決める（マイクのみの録音は「講義」）。文の時刻は
     * その音源へ送った音声の位置（音声クロック）から計算するので、`endSeconds` は使わない。</p>
     *
     * <p><b>フレームの識別は常時接続と同じ欄で受ける</b>（`frameNo` ＝ その音源で 1 から数えた番号、
     * `startSample` ＝ 録音の先頭からの絶対位置＝16kHz のサンプル数）。常時接続はバイナリの前に
     * 見出しのテキストで同じ値を送る。これがあるので、経路が常時接続と HTTP のあいだで変わっても
     * 後端が「同じ音」を識別でき（二重に認識しない）、文の時刻は録音の時間軸のままになる
     * （張り直し・後端の再起動で 0 に戻らない）。**渡さないときは今までどおり**: 番号は到着順、
     * 位置は送ったサンプル数から積む。</p>
     *
     * <p>入口の確認（所有者と録音の状態）は常時接続（WebSocket）と**同じ実装**を使う
     * （{@link ClassroomService#requireSttAudioAccountId}）。</p>
     */
    @PostMapping(value = "/{recordId}/stt/stream", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResponse<ClassroomSttStreamService.StreamPush> streamStt(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId,
            @RequestParam(value = "source", required = false, defaultValue = "mic") String source,
            @RequestParam(value = "frameNo", required = false) Integer frameNo,
            @RequestParam(value = "startSample", required = false) Long startSample,
            @RequestBody byte[] pcm) {
        long accountId = classroomService.requireSttAudioAccountId(user, recordId);
        return ApiResponse.ok(streamService.push(recordId, accountId, source, pcm,
                frameNo == null ? 0 : frameNo,
                startSample == null ? ClassroomSttStreamService.UNKNOWN_SAMPLE : startSample));
    }

    /**
     * ストリーミング書き起こしの終わり（最後の確定文を取り出してセッションを閉じる）。
     *
     * <p>停止直後の猶予内は収尾を受け付ける（尾部の確定文が遅れて届くため）。返す
     * `error` に「収尾を取り切れなかった」等の理由が入ることがある＝黙って成功にしない。</p>
     */
    @PostMapping("/{recordId}/stt/stream/finish")
    public ApiResponse<ClassroomSttStreamService.StreamPush> finishStreamStt(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId,
            @RequestParam(value = "source", required = false, defaultValue = "mic") String source) {
        long accountId = classroomService.requireSttFinishAccountId(user, recordId);
        return ApiResponse.ok(streamService.finish(recordId, accountId, source));
    }

    @GetMapping("/options")
    public ApiResponse<ClassroomModels.OptionsResult> options(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(classroomService.options(user));
    }

    @GetMapping("/presets")
    public ApiResponse<java.util.List<ClassroomModels.PresetView>> presets(
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(classroomService.presets(user));
    }

    @PostMapping
    public ApiResponse<ClassroomModels.RecordStatus> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody ClassroomModels.CreateRequest request) {
        return ApiResponse.ok(classroomService.create(user, request), "授業記録を作成しました。");
    }

    @PostMapping("/{recordId}/start")
    public ApiResponse<ClassroomModels.RecordStatus> start(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId) {
        return ApiResponse.ok(classroomService.start(user, recordId), "録音を開始しました。");
    }

    @PostMapping(value = "/{recordId}/chunks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ClassroomModels.ChunkUploadResult> uploadChunk(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId,
            @RequestParam("seq") int seq,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "stt", required = false) MultipartFile sttAudio,
            // 画面が測った実際の経過秒（分塊の始まり・終わり）。無ければ連番から計算する
            @RequestParam(value = "startSeconds", required = false) java.math.BigDecimal startSeconds,
            @RequestParam(value = "endSeconds", required = false) java.math.BigDecimal endSeconds) {
        return ApiResponse.ok(
                classroomService.uploadChunk(user, recordId, seq, file, sttAudio, startSeconds, endSeconds),
                "分塊を受け取りました。");
    }

    /**
     * 録音せずに取り込む（利用者の指示）: **音声ファイル（mp3）**または**貼り付けた文字起こし**。
     *
     * <p>`file` を送れば STT で書き起こし、`text` を送ればそのまま書き起こしにし、
     * どちらもセグメント（文ごと）として保存する。AI まとめは今までどおり設定
     * `CLASSROOM_AI_NOTE_ENABLED` が有効なときだけ動く。</p>
     */
    @PostMapping(value = "/{recordId}/source", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ClassroomModels.ChunkUploadResult> importSource(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) {
        return ApiResponse.ok(classroomService.importSource(user, recordId, file, text, durationSeconds),
                "取り込みました。");
    }

    /**
     * ブラウザ（Web Speech API）の認識結果を 1 件足す（STT プロバイダー = browser のとき）。
     *
     * <p>認識は画面が行う（Chrome / Edge）。サーバーはテキストをセグメントとして保存し、
     * フェーズノートのトリガーだけ評価する。</p>
     */
    @PostMapping("/{recordId}/transcripts")
    public ApiResponse<ClassroomModels.ChunkUploadResult> appendTranscript(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId,
            @Valid @RequestBody ClassroomModels.TranscriptRequest request) {
        return ApiResponse.ok(classroomService.appendTranscript(user, recordId, request),
                "書き起こしを追記しました。");
    }

    @GetMapping("/{recordId}/segments")
    public ApiResponse<ClassroomModels.SegmentListResult> segments(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId,
            @RequestParam(value = "afterSeq", defaultValue = "0") int afterSeq) {
        return ApiResponse.ok(classroomService.segments(user, recordId, afterSeq));
    }

    @GetMapping("/{recordId}")
    public ApiResponse<ClassroomModels.RecordDetail> detail(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId) {
        return ApiResponse.ok(classroomService.detail(user, recordId));
    }

    @GetMapping
    public ApiResponse<ClassroomModels.RecordListResult> list(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(classroomService.list(user, status, page, size));
    }

    @PostMapping("/{recordId}/end")
    public ApiResponse<ClassroomModels.EndResult> end(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId) {
        return ApiResponse.ok(classroomService.end(user, recordId), "録音を終了しました。");
    }

    /**
     * 元音声の配信（`&lt;audio&gt;` が読む）。
     *
     * <p>`Range` があれば 206（`Content-Range` つき）、不正・範囲外は 416（`ReadingRange` を流用）。
     * 実体が無い・行が無い・他人の記録は 404。</p>
     */
    @GetMapping("/{recordId}/audio")
    public void audio(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            HttpServletResponse response) {
        writeFile(classroomService.audio(user, recordId), range, response);
    }

    @DeleteMapping("/{recordId}")
    public ApiResponse<ClassroomModels.DeleteResult> delete(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId) {
        return ApiResponse.ok(classroomService.delete(user, recordId), "授業記録を削除しました。");
    }

    /** 音声配信の共通処理（Range/206。読書管理の `writeFile` と同じ作り）。 */
    private void writeFile(ClassroomService.AudioFile file, String rangeHeader, HttpServletResponse response) {
        Path path = file.path();
        long length;
        try {
            length = Files.size(path);
        } catch (IOException ex) {
            throw new IllegalStateException("録音ファイルの読み込みに失敗しました。", ex);
        }
        ReadingRange.Resolved resolved;
        try {
            resolved = ReadingRange.resolve(rangeHeader, length);
        } catch (ReadingApiException cause) {
            throw ClassroomApiException.rangeNotSatisfiable();
        }

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setContentType(file.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        long count = resolved == null ? length : resolved.count();
        if (resolved != null) {
            response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
            response.setHeader(HttpHeaders.CONTENT_RANGE, resolved.contentRange());
        }
        response.setContentLengthLong(count);

        try (InputStream in = Files.newInputStream(path);
             OutputStream out = response.getOutputStream()) {
            if (resolved == null) {
                in.transferTo(out);
            } else {
                StreamUtils.copyRange(in, out, resolved.start(), resolved.end());
            }
            out.flush();
        } catch (IOException ex) {
            throw new IllegalStateException("録音ファイルの配信に失敗しました。", ex);
        }
    }
}
