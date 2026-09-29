package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.english.EnglishEssayModels;
import com.study21.user.english.EnglishEssayService;
import com.study21.user.security.UserPrincipal;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 英作文AI添削の「作文データの層」（user-api）。画面【英作文AI添削】が使う。
 *
 * <ul>
 *   <li>`GET /` … 一覧（`keyword` / `level` / `dateFrom` / `dateTo` / `page` / `size`）。
 *       各行に画像枚数と**最新の添削**（回・状態・得点）、そして**持ち主**（`ownerAccountId` /
 *       `ownerName`。保護者が子どもの作文を見たときに「誰の作文か」を出す）</li>
 *   <li>`GET /{essayId}` … 詳細（画像と**添削の历次すべて**。`report` は JSON のまま）</li>
 *   <li>`POST /` … 新規（級・題 日/中・設問・本文。語数はサーバーが数える）</li>
 *   <li>`PUT /{essayId}` … 更新（本文は語数を数え直す。`images` の一覧で区分と表示順を直し、
 *       含まれない画像は消す）</li>
 *   <li>`DELETE /{essayId}` … 論理削除（204。行と添削の履歴は残す）</li>
 *   <li>`POST /{essayId}/images` … 画像 1 枚（multipart。`file` / `category` / `order`。
 *       OCR の生の結果 `recognizedText` / `confidence` も任意で受け取る）</li>
 *   <li>`GET /{essayId}/images/{imageId}` … 画像の実体（`Content-Type` は `MIMEタイプ`）</li>
 *   <li>`POST /{essayId}/gradings` … **AI 添削の受付**（`{"round"?:2}`。自分の作文だけ。実行は働き手）</li>
 *   <li>`GET /limits` … 画像の上限（`{maxImages, maxImageMb}`。設定が唯一の出所）</li>
 * </ul>
 *
 * <p>**見え方**は「自分の作文 ＋ 自分の子どもの作文」（保護者。家族は `ACC_アカウント.保護者ID` から
 * SQL の中で解決する）。他人の家庭の作文は 404。**書き込みは本人だけ**（保護者は子どもの作文を
 * 閲覧できても、代理で提出・更新・削除・画像アップ・添削の受付はできない＝404）。</p>
 *
 * <p>AI の呼び出し（OCR）は admin-api の `/api/admin/batch/english-essay/ocr` が行う（画面が直接叩く）。
 * **添削の受付は user-api の `POST /{essayId}/gradings` が唯一の入口**で、所有者を確かめてから
 * admin-api の内部入口（合言葉つき）へ転調する（他人の作文に添削を積めないようにするため）。</p>
 */
@RestController
@RequestMapping("/api/user/english-essays")
public class EnglishEssayController {

    private final EnglishEssayService englishEssayService;

    public EnglishEssayController(EnglishEssayService englishEssayService) {
        this.englishEssayService = englishEssayService;
    }

    @GetMapping
    public ApiResponse<EnglishEssayModels.EssayListResult> list(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(englishEssayService.search(user.accountId(), keyword, level,
                dateFrom, dateTo, page, size));
    }

    @GetMapping("/{essayId}")
    public ApiResponse<EnglishEssayModels.EssayDetail> detail(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long essayId) {
        return ApiResponse.ok(englishEssayService.detail(user.accountId(), essayId));
    }

    @PostMapping
    public ApiResponse<EnglishEssayModels.EssayDetail> create(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody EnglishEssayModels.CreateRequest request) {
        return ApiResponse.ok(englishEssayService.create(user, request), "英作文を登録しました。");
    }

    @PutMapping("/{essayId}")
    public ApiResponse<EnglishEssayModels.EssayDetail> update(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long essayId,
            @RequestBody EnglishEssayModels.UpdateRequest request) {
        return ApiResponse.ok(englishEssayService.update(user, essayId, request), "保存しました。");
    }

    /** 論理削除（行は残す。添削の履歴も残る）。 */
    @DeleteMapping("/{essayId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long essayId) {
        englishEssayService.delete(user, essayId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 画像の上限（**画面が事前チェックに使う**）。
     *
     * <p>値は設定（`ENGLISH_ESSAY_MAX_IMAGES` / `ENGLISH_ESSAY_MAX_IMAGE_MB`）から読む。
     * 画面が同じ値を二重に持つと、設定を変えたときに表示だけ古いままになる。</p>
     */
    @GetMapping("/limits")
    public ApiResponse<EnglishEssayModels.ImageLimits> limits() {
        return ApiResponse.ok(englishEssayService.limits(), "画像の上限を返しました。");
    }

    /** 画像 1 枚を上げる（区分と表示順は画面が指定する。`order` を省くと次の順になる）。 */
    @PostMapping(value = "/{essayId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EnglishEssayModels.ImageUploadResult> uploadImage(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long essayId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "order", required = false) Integer order,
            // OCR の生の結果（任意。画面は保存時にまとめて送る想定なので、ここでは受け取るだけ）
            @RequestParam(value = "recognizedText", required = false) String recognizedText,
            @RequestParam(value = "confidence", required = false) Integer confidence) {
        return ApiResponse.ok(englishEssayService.uploadImage(user, essayId, file, category, order,
                recognizedText, confidence), "画像を登録しました。");
    }

    /**
     * **AI 添削の受付**（実行はバックエンドの働き手。画面は詳細の再取得で結果を見る）。
     *
     * <p>本文は `{"round":2}`（`round` を省くと admin-api が次の回を決める）。
     * **自分の作文だけ**（他人・存在しない・削除済みは 404）。</p>
     */
    @PostMapping("/{essayId}/gradings")
    public ApiResponse<EnglishEssayModels.GradingAccepted> createGrading(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long essayId,
            @RequestBody(required = false) Map<String, Object> request) {
        Integer round = intOf(request == null ? null : request.get("round"));
        EnglishEssayModels.GradingAccepted accepted = englishEssayService.acceptGrading(user, essayId, round);
        return ApiResponse.ok(accepted, accepted.message());
    }

    /** 何回目か（省略・数値でないときは null＝admin-api が次の回を決める）。 */
    static Integer intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && text.trim().matches("\\d+")) {
            return Integer.parseInt(text.trim());
        }
        return null;
    }

    /** 画像の実体（自分の作文と自分の子どもの作文だけ。画面は `<img>` から直接開く）。 */
    @GetMapping("/{essayId}/images/{imageId}")
    public ResponseEntity<Resource> image(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long essayId,
            @PathVariable long imageId) {
        EnglishEssayModels.ImageFile image = englishEssayService.image(user.accountId(), essayId, imageId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(image.fileName() == null ? "image" : image.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(new FileSystemResource(image.path()));
    }
}
