package com.study21.admin.controller;

import com.study21.admin.batch.BatchService;
import com.study21.admin.englishessay.EnglishEssayAiQueue;
import com.study21.admin.englishessay.EnglishEssayAiSettings;
import com.study21.admin.englishessay.EnglishEssayLevel;
import com.study21.admin.englishessay.EnglishEssayOcrStep;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.api.ApiResponse;
import com.study21.common.core.exception.ValidationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 英作文 AI 添削の入口（{@code /api/admin/batch/english-essay}）。
 *
 * <p><b>2.0 と同じ役割分担</b>: OCR は<b>同期</b>（画面が待って、結果を確認・修正する）、
 * 添削は<b>非同期</b>（受付だけして {@code QUEUED} を積み、働き手が実行する）。</p>
 *
 * <ul>
 *   <li>{@code POST /ocr}: 画像（multipart）を 1 枚ずつ AI に渡し、設問文・作文本文・信頼度を返す</li>
 *   <li>{@code POST /gradings}: 添削を受付ける（{@code {essayId, round?}}）</li>
 *   <li>{@code GET /gradings/{id}}: 画面のポーリング用の状態</li>
 * </ul>
 *
 * <p><b>理由は日本語で返す</b>（黙って空を返さない）。入力の誤りは 400、AI の失敗は 502、
 * 見つからないものは 404（{@code common-core} の {@code GlobalExceptionHandler} が形を揃える）。</p>
 *
 * <p><b>認証（2026-09-27 改修）</b>: この入口は**画面から直接叩かない**。利用者の権限（ログイン）は
 * user-api（OCR は {@code POST /api/user/english-essays/ocr}、添削の受付は
 * {@code POST /api/user/english-essays/{essayId}/gradings}）が確かめ、**そこから**サービス間の合言葉
 * （{@code X-Internal-Token}）つきで呼ばれる（{@code SecurityConfig} の
 * {@link com.study21.admin.internal.InternalServiceAuthorizer} の規則。**匿名・利用者の session では
 * 通らない**）。以前は同期 OCR が {@code permitAll} で、URL を知っていれば**誰でも AI を呼べた**
 * （費用を使わせられる）。</p>
 */
@RestController
@RequestMapping("/api/admin/batch/english-essay")
public class EnglishEssayAiBatchController {

    /** 各画像の区分（DDL の {@code 画像区分} と同じ 2 つ）。 */
    private static final List<String> CATEGORIES = List.of("question", "answer");

    private final EnglishEssayOcrStep ocrStep;
    private final EnglishEssayAiQueue queue;
    private final SettingsService settingsService;
    private final BatchService batchService;

    public EnglishEssayAiBatchController(EnglishEssayOcrStep ocrStep,
                                         EnglishEssayAiQueue queue,
                                         SettingsService settingsService,
                                         BatchService batchService) {
        this.ocrStep = ocrStep;
        this.queue = queue;
        this.settingsService = settingsService;
        this.batchService = batchService;
    }

    /**
     * 画像を文字にする（同期）。
     *
     * @param level      英検級（{@code GRADE1} / {@code PRE1} / {@code GRADE2}）
     * @param categories 各ファイルの区分をカンマ区切りで（省略時は先頭が question・残り answer）
     * @param files      画像（上限は設定の {@code _MAX_IMAGES} と {@code _MAX_IMAGE_MB}）
     */
    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> ocr(
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "categories", required = false) String categories,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        EnglishEssayAiSettings.requireEnabled(settingsService);
        // 同期でも batC11 の Step を使うので、バッチ一覧の有効／無効をそのまま効かせる
        // （日語単語の AI 取得と同じ。バッチが無効なら画面からも実行できない）
        batchService.requireCallable(EnglishEssayAiSettings.OCR_BATCH_CODE);
        EnglishEssayLevel essayLevel = EnglishEssayLevel.parse(level);
        List<EnglishEssayOcrStep.ImageInput> images = imageInputsOf(files, categories);

        EnglishEssayOcrStep.OcrResult result = ocrStep.recognizeRequest(null, essayLevel, images);

        List<Map<String, Object>> pages = new ArrayList<>();
        for (EnglishEssayOcrStep.PageResult page : result.pages()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", page.category());
            item.put("text", page.text());
            item.put("confidence", page.confidence());
            pages.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionText", result.questionText());
        data.put("essayText", result.essayText());
        data.put("pages", pages);
        data.put("questionConfidence", result.questionConfidence());
        data.put("essayConfidence", result.essayConfidence());
        return ApiResponse.ok(data, "英作文の画像を文字にしました（"
                + pages.size() + " 枚）。内容を確認してから添削を受付けてください。");
    }

    /**
     * 添削を<b>受付ける</b>（実行はバックエンドの働き手）。
     *
     * <p>本文は {@code {"essayId":900001,"round":2}}。{@code round} を省くと次の回になる。
     * 級・題・設問・本文・語数は<b>そのときの写し</b>を履歴へ入れる。</p>
     */
    @PostMapping("/gradings")
    public ApiResponse<Map<String, Object>> createGrading(
            @RequestBody(required = false) Map<String, Object> request) {
        EnglishEssayAiSettings.requireEnabled(settingsService);
        // バッチ一覧で無効にされているときは受け付けない（一覧のスイッチをそのまま効かせる）
        batchService.requireCallable(EnglishEssayAiSettings.GRADING_BATCH_CODE);

        Map<String, Object> body = request == null ? Map.of() : request;
        Long essayId = longOf(body.get("essayId"));
        if (essayId == null) {
            throw new ValidationException("英作文（essayId）を指定してください。");
        }
        EnglishEssayAiQueue.Accepted accepted = queue.accept(essayId, intOf(body.get("round")));

        String message = "英検基準AI添削を受付けました（第 " + accepted.round()
                + " 回）。バックグラウンドで処理します。";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gradingId", accepted.gradingId());
        data.put("round", accepted.round());
        data.put("message", message);
        return ApiResponse.ok(data, message);
    }

    /** 添削の状態（画面のポーリング用）。 */
    @GetMapping("/gradings/{gradingId}")
    public ApiResponse<Map<String, Object>> grading(@PathVariable("gradingId") long gradingId) {
        return ApiResponse.ok(queue.status(gradingId), "添削の状態を返しました。");
    }

    /** multipart のファイルと区分を組にする（区分の数が多すぎる・未知の区分は 400）。 */
    static List<EnglishEssayOcrStep.ImageInput> imageInputsOf(List<MultipartFile> files, String categories) {
        if (files == null || files.isEmpty()) {
            throw new ValidationException("画像を選んでください（設問画像と答案画像）。");
        }
        List<String> requested = new ArrayList<>();
        if (categories != null && !categories.isBlank()) {
            for (String part : categories.split(",")) {
                String value = part.trim();
                if (!value.isEmpty()) {
                    requested.add(value);
                }
            }
        }
        if (requested.size() > files.size()) {
            throw new ValidationException("画像の区分（categories）の数が、画像の枚数より多いです。");
        }
        List<EnglishEssayOcrStep.ImageInput> images = new ArrayList<>();
        for (int index = 0; index < files.size(); index += 1) {
            MultipartFile file = files.get(index);
            String category = index < requested.size()
                    ? requested.get(index)
                    : (index == 0 ? "question" : "answer");
            if (!CATEGORIES.contains(category)) {
                throw new ValidationException("画像の区分は question / answer のいずれかです: " + category);
            }
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (Exception cause) {
                throw new ValidationException("画像「" + file.getOriginalFilename() + "」を読み込めませんでした。");
            }
            images.add(new EnglishEssayOcrStep.ImageInput(category,
                    file.getOriginalFilename() == null ? ("image-" + (index + 1)) : file.getOriginalFilename(),
                    bytes,
                    file.getContentType() == null ? "image/jpeg" : file.getContentType()));
        }
        return images;
    }

    private static Long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && text.trim().matches("\\d+")) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    private static Integer intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && text.trim().matches("\\d+")) {
            return Integer.parseInt(text.trim());
        }
        return null;
    }
}
