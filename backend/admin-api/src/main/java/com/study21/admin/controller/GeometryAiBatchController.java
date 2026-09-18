package com.study21.admin.controller;

import com.study21.admin.geometryai.AiFigurePipelineService;
import com.study21.admin.geometryai.GeometryAiImageStorage;
import com.study21.common.core.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 生図の起動と状況（admin-api）。
 *
 * <p>画面（user-api で要求を作ったあと）が**このエンドポイントを 1 回だけ**呼ぶ。
 * 内部で `batC51 → batC52 → batC53` を順に実行し、失敗した工程で止める（設計 §2.2）。
 * 各工程は独立した 1 実行として実行履歴に残る。</p>
 *
 * <p>**認証導入時の TODO**: 本スケルトンは認証未実装のため `/api/admin/batch/**` は許可されている。
 * このエンドポイントは「既存の要求行しか処理しない（新しい要求は作れない）」ので被害は限られるが、
 * 認証導入時は **ADMIN ロールまたは当該要求の所有者**に制限する（設計 §4.4）。</p>
 */
@RestController
@RequestMapping("/api/admin/batch/geometry-ai")
public class GeometryAiBatchController {

    private final AiFigurePipelineService pipelineService;
    private final GeometryAiImageStorage imageStorage;

    public GeometryAiBatchController(AiFigurePipelineService pipelineService,
                                     GeometryAiImageStorage imageStorage) {
        this.pipelineService = pipelineService;
        this.imageStorage = imageStorage;
    }

    /**
     * パイプライン起動（前処理 → AI 生成 → 検証）。**同期**で最後まで進める。
     *
     * <p>AI の応答に 60〜180 秒かかるので、画面はこの呼び出しを待たずに
     * `GET /api/user/geometry/ai/requests/{id}` をポーリングしてもよい（契約は「送信 → ポーリング」）。
     * 第 2 段階（有限ワーカー）では `?async=true` で即時 202 を返す予定（設計 §2.4）。</p>
     */
    @PostMapping("/requests/{aiRequestId}/run")
    public ApiResponse<Map<String, Object>> run(
            @PathVariable long aiRequestId,
            @RequestBody(required = false) Map<String, Object> request) {
        String operator = request == null || request.get("operator") == null
                ? null : String.valueOf(request.get("operator"));
        Map<String, Object> result = pipelineService.run(aiRequestId, operator);
        return ApiResponse.ok(result, Boolean.TRUE.equals(result.get("completed"))
                ? "AI 生図の処理が終わりました。" : "AI 生図の処理が途中で止まりました。");
    }

    /**
     * AI 生図の画像置き場（診断用）。
     *
     * <p>元画像は **user-api** が書き、切り抜きは **admin-api（batC51）** が読むので、両サービスが
     * 同じ置き場を指している必要がある。ずれていると `NO_IMAGE` になるだけでは原因が分からないので、
     * ここで自分の置き場を確認できるようにする（user-api 側は起動ログに出る）。</p>
     */
    @GetMapping("/storage")
    public ApiResponse<Map<String, Object>> storage() {
        Map<String, Object> data = new LinkedHashMap<>();
        Path root = imageStorage.getRoot();
        data.put("storageRoot", root.toString());
        data.put("exists", java.nio.file.Files.isDirectory(root));
        data.put("note", "user-api と同じ場所（STUDY21_GEOMETRY_AI_STORAGE_ROOT）を指している必要があります。");
        return ApiResponse.ok(data);
    }

    /** 工程ごとの実行 ID・状態・処理時間・エラー（実行履歴を `要求内容` で引く）。 */
    @GetMapping("/requests/{aiRequestId}")
    public ApiResponse<List<Map<String, Object>>> status(@PathVariable long aiRequestId) {
        return ApiResponse.ok(pipelineService.status(aiRequestId));
    }
}
