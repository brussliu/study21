package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.geometry.GeometryAiModels;
import com.study21.user.geometry.GeometryAiService;
import com.study21.user.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

/**
 * AI 生図・AI 画図助手の API（user-api）。
 *
 * <p>画面（生徒・保護者・管理者）が使うので user-api に置く。**AI を呼ぶ本体（batC51/52/53）は
 * admin-api** で、両者は DB の状態列だけで橋渡しする（`docs/ARCHITECTURE.md` §3 がサービス間の
 * 相互呼び出しを禁じているため）。</p>
 *
 * <ul>
 *   <li>`GET /options` … 画面が使う設定（有効／無効・上限・既定値・今日の使用回数）</li>
 *   <li>`POST /requests/images` … 元画像のアップロード（AI へはまだ送らない）</li>
 *   <li>`POST /requests` … リクエスト作成（QUEUED）</li>
 *   <li>`GET /requests` / `GET /requests/{id}` … 履歴とポーリング</li>
 *   <li>`GET /requests/{id}/image?kind=original|cropped` … 画像の配信（所有者だけ）</li>
 *   <li>`POST /requests/{id}/retry` / `cancel` / `confirm` … 再生成・取消・図形として保存</li>
 *   <li>`POST /assist` … AI 画図助手（同期）。`POST /assist/{id}/applied|discarded` は記録だけ</li>
 * </ul>
 *
 * <p>**AI の起動（admin-api の `POST /api/admin/batch/geometry-ai/requests/{id}/run`）は画面が
 * 1 回だけ呼ぶ。** 応答の `runPath` がその URL。フロントの HttpClient は既定 10 秒で
 * タイムアウトするため、契約は「送信 → ポーリング」にしてある（設計 §2.4）。</p>
 */
@RestController
@RequestMapping("/api/user/geometry/ai")
public class GeometryAiController {

    private final GeometryAiService geometryAiService;

    public GeometryAiController(GeometryAiService geometryAiService) {
        this.geometryAiService = geometryAiService;
    }

    /** 画面が使う設定（設定画面の値をそのまま返す。**API Key は返さない**）。 */
    @GetMapping("/options")
    public ApiResponse<GeometryAiModels.OptionsResult> options(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(geometryAiService.options(user));
    }

    /** 元画像のアップロード。`imageToken` を `/requests` に渡す。 */
    @PostMapping(value = "/requests/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<GeometryAiModels.UploadResult> upload(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(geometryAiService.upload(user, file), "画像を受け取りました。");
    }

    /** リクエスト作成（画面の送信前の確認と同じ内容）。 */
    @PostMapping("/requests")
    public ApiResponse<GeometryAiModels.RequestStatus> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody GeometryAiModels.CreateRequest request) {
        return ApiResponse.ok(geometryAiService.create(user, request), "AI 生図を受け付けました。");
    }

    /** 自分の履歴（画像と生成コマンドは返さない）。 */
    @GetMapping("/requests")
    public ApiResponse<GeometryAiModels.RequestListResult> requests(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(geometryAiService.list(user, status, page, size));
    }

    /**
     * 図形管理の一覧に出す**タスクのカード**（処理中のものと最近のもの）。
     *
     * <p>図形として保存済のものは返さない（図形一覧のカードとして出るので重複させない）。
     * 処理中の段階（待機中・読み取り中・生成中・検証保存中）と、追加入力待ちの質問件数を返す。</p>
     */
    @GetMapping("/tasks")
    public ApiResponse<GeometryAiModels.TaskListResult> tasks(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResponse.ok(geometryAiService.tasks(user, limit));
    }

    /** 1 件（ポーリングの主対象）。 */
    @GetMapping("/requests/{requestId}")
    public ApiResponse<GeometryAiModels.RequestDetail> request(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long requestId) {
        return ApiResponse.ok(geometryAiService.detail(user, requestId));
    }

    /** 画像の配信（所有者だけ）。一覧に出す切り抜きプレビューもここから取る。 */
    @GetMapping("/requests/{requestId}/image")
    public ResponseEntity<byte[]> image(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long requestId,
            @RequestParam(value = "kind", defaultValue = GeometryAiModels.IMAGE_KIND_ORIGINAL) String kind) {
        GeometryAiModels.ImageData image = geometryAiService.image(user, requestId, kind);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mime()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(image.bytes());
    }

    /** 【もう一度生成】（AI の成果物を消して前処理済みへ戻す。回数分だけ課金される）。 */
    @PostMapping("/requests/{requestId}/retry")
    public ApiResponse<GeometryAiModels.RequestStatus> retry(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long requestId,
            @RequestBody GeometryAiModels.VersionRequest request) {
        return ApiResponse.ok(geometryAiService.retry(user, requestId, request == null ? null : request.version()),
                "もう一度生成します。");
    }

    /**
     * 追加入力待ち・失敗した要求を**条件を直して送り直す**（同じ要求行を使い回す）。
     *
     * <p>画像はもうサーバーにあるので送り直さない。実行はバックエンドの働き手が行うため、
     * 画面はこの応答を受けたら一覧へ戻ってよい。</p>
     */
    @PostMapping("/requests/{requestId}/resubmit")
    public ApiResponse<GeometryAiModels.RequestStatus> resubmit(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long requestId,
            @Valid @RequestBody GeometryAiModels.ResubmitRequest request) {
        return ApiResponse.ok(geometryAiService.resubmit(user, requestId, request),
                "内容を直して送り直しました。");
    }

    /** 取消（QUEUED / PREPROCESSED のみ）。 */
    @PostMapping("/requests/{requestId}/cancel")
    public ApiResponse<GeometryAiModels.RequestStatus> cancel(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long requestId,
            @RequestBody GeometryAiModels.VersionRequest request) {
        return ApiResponse.ok(geometryAiService.cancel(user, requestId, request == null ? null : request.version()),
                "AI 生図を取り消しました。");
    }

    /** 図形として保存（作図画面で確認・調整したあと。登録元コード = 'AI'）。 */
    @PostMapping("/requests/{requestId}/confirm")
    public ApiResponse<GeometryAiModels.ConfirmResult> confirm(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long requestId,
            @Valid @RequestBody GeometryAiModels.ConfirmRequest request) {
        GeometryAiModels.ConfirmResult result = geometryAiService.confirm(user, requestId, request);
        return ApiResponse.ok(result, result.message());
    }

    /** AI 画図助手への依頼を作る（AI はまだ呼ばない。batC52 のキューに入るだけ）。 */
    @PostMapping("/assist")
    public ApiResponse<GeometryAiModels.AssistStatus> assist(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody GeometryAiModels.AssistRequest request) {
        return ApiResponse.ok(geometryAiService.assist(user, request), "AI 画図助手の依頼を受け付けました。");
    }

    /** 依頼の現在の状態（ポーリングの主対象）。`指示前XML`（【戻す】の戻り先）も返す。 */
    @GetMapping("/assist/{assistId}")
    public ApiResponse<GeometryAiModels.AssistDetail> assistDetail(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long assistId) {
        return ApiResponse.ok(geometryAiService.getAssist(user, assistId));
    }

    /**
     * 図形ごとの指示履歴（古い順。自分が作った行だけ）。
     *
     * <p>会話ログを端末をまたいで見せるための入口（`figureId` は必須）。
     * `指示前XML` は重いので含めない（必要になったら `GET /assist/{id}` で 1 件取る）。</p>
     */
    @GetMapping("/assist")
    public ApiResponse<GeometryAiModels.AssistHistoryResult> assistHistory(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(name = "figureId", required = false) Long figureId,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ApiResponse.ok(geometryAiService.assistHistory(user, figureId, limit));
    }

    /** 【反映】（記録だけ。作図への適用はブラウザの applet が行う）。 */
    @PostMapping("/assist/{assistId}/applied")
    public ApiResponse<GeometryAiModels.AssistResult> assistApplied(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long assistId,
            @RequestBody(required = false) Map<String, Object> request) {
        String mode = request == null || request.get("mode") == null ? null : String.valueOf(request.get("mode"));
        return ApiResponse.ok(geometryAiService.markAssistApplied(user, assistId, mode),
                "AI の変更案を反映しました。");
    }

    /**
     * 変更案を**作図に反映しなかった**（記録だけ）。
     *
     * <p>利用者の【破棄】だけでなく、案は返ったが作図に反映できなかったときにも呼ぶ。
     * そのときは `reason`（どの行で失敗したか）を残す＝端末をまたいでも理由が見える。</p>
     */
    @PostMapping("/assist/{assistId}/discarded")
    public ApiResponse<GeometryAiModels.AssistResult> assistDiscarded(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long assistId,
            @RequestBody(required = false) Map<String, Object> request) {
        String reason = request == null || request.get("reason") == null
                ? null : String.valueOf(request.get("reason"));
        return ApiResponse.ok(geometryAiService.markAssistRejected(user, assistId, reason),
                "AI の変更案を破棄しました。");
    }
}
