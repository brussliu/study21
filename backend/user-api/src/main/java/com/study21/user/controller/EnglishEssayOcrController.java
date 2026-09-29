package com.study21.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.study21.common.core.api.ApiResponse;
import com.study21.user.english.EnglishEssayOcrProxyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 英作文の**同期 OCR の公開入口**（user-api）。画面【英作文AI添削】の【AIで画像を読み取る】が使う。
 *
 * <ul>
 *   <li>`POST /api/user/english-essays/ocr` … 画像（multipart。`level` / `categories` / `files`）を
 *       admin-api へ転送し、`data = {questionText, essayText, pages[], questionConfidence,
 *       essayConfidence}` を**そのまま**返す</li>
 * </ul>
 *
 * <p><b>ログイン必須</b>（`SecurityConfig` の `/api/user/english-essays/**`）。**作文IDは要らない**
 * （編集中の画像をそのまま送る。まだ作文の行が無い）。</p>
 *
 * <p><b>なぜ admin-api を画面から直接叩かせないか</b>: admin-api の OCR は `/api/admin/batch/**` の
 * `permitAll` の下にあり、**URL を知っていれば誰でも AI を呼べた**（費用を使わせられる）。
 * 入口を user-api に置くことで、匿名はここで 401 になる。中身は
 * {@link EnglishEssayOcrProxyService}（合言葉つきの転送）。</p>
 */
@RestController
@RequestMapping("/api/user/english-essays/ocr")
public class EnglishEssayOcrController {

    private final EnglishEssayOcrProxyService ocrProxyService;

    public EnglishEssayOcrController(EnglishEssayOcrProxyService ocrProxyService) {
        this.ocrProxyService = ocrProxyService;
    }

    /**
     * 画像を文字にする（同期）。
     *
     * @param level      英検級（`GRADE1` / `PRE1` / `GRADE2`）
     * @param categories 各ファイルの区分をカンマ区切りで（省略時は先頭が question・残り answer）
     * @param files      画像（上限は設定の `_MAX_IMAGES` と `_MAX_IMAGE_MB`）
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JsonNode> ocr(
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "categories", required = false) String categories,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        EnglishEssayOcrProxyService.OcrResult result = ocrProxyService.recognize(level, categories, files);
        return ApiResponse.ok(result.data(), result.message());
    }
}
