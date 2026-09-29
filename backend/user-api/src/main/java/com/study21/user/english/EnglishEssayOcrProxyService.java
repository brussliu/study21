package com.study21.user.english;

import com.fasterxml.jackson.databind.JsonNode;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 同期 OCR の**公開入口**（user-api）の中身。画面から受けた画像を、合言葉つきで admin-api の
 * 内部入口（`POST /api/admin/batch/english-essay/ocr`）へ**そのまま転送**し、応答の `data` を返す。
 *
 * <p><b>なぜ user-api を通すか</b>: admin-api の OCR は `/api/admin/batch/**` の `permitAll` の下にあり、
 * **URL を知っていれば誰でも AI を呼べた**（費用を使わせられる）。`/api/user/**` はログイン必須なので、
 * 入口をここに置けば**匿名は 401** で止まる。利用者の権限はここ（Spring Security）が確かめ、
 * 転送は {@link EnglishEssayAiAdminClient} が合言葉を付けて行う。</p>
 *
 * <p><b>作文IDは要らない</b>: 編集中の画像をその場で送る（まだ作文の行が無い）ため、
 * 所有者の確認は「ログインしていること」までにする。**画像は保存しない**（DB にもファイルにも
 * 残さない。保管は {@code EnglishEssayService} の担当で、ここは AI を呼ぶだけ）。</p>
 *
 * <p><b>失敗は日本語で返す</b>（黙って空を返さない）。入力の誤りは 400、admin-api が断った・
 * 答えられないときは 409（既存の添削の受付と同じ作法）。</p>
 */
@Component
public class EnglishEssayOcrProxyService {

    private static final Logger log = LoggerFactory.getLogger(EnglishEssayOcrProxyService.class);

    private final EnglishEssayAiAdminClient adminClient;

    public EnglishEssayOcrProxyService(EnglishEssayAiAdminClient adminClient) {
        this.adminClient = adminClient;
    }

    /**
     * OCR の結果（画面へそのまま返す形）。
     *
     * @param data    admin-api の応答 `data` をそのまま（`questionText` / `essayText` / `pages` / 信頼度）
     * @param message 画面に出す日本語の案内
     */
    public record OcrResult(JsonNode data, String message) {
    }

    /**
     * 画像を文字にする（同期）。
     *
     * @param level      英検級（`GRADE1` / `PRE1` / `GRADE2`。判定は admin-api が行う）
     * @param categories 各ファイルの区分をカンマ区切りで（省略時は先頭が question・残り answer）
     * @param files      画像（**ファイルの実体をそのまま転送する**）
     * @throws ValidationException 画像が無い・読めない（400）
     * @throws ConflictException   admin-api が断った・答えられない（409。日本語の理由つき）
     */
    public OcrResult recognize(String level, String categories, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ValidationException("画像を選んでください（設問画像と答案画像）。");
        }
        List<EnglishEssayAiAdminClient.OcrImage> images = new ArrayList<>();
        for (MultipartFile file : files) {
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (Exception cause) {
                throw new ValidationException("画像「" + file.getOriginalFilename() + "」を読み込めませんでした。");
            }
            images.add(new EnglishEssayAiAdminClient.OcrImage(file.getOriginalFilename(),
                    file.getContentType(), bytes));
        }
        log.info("english essay ocr requested. level={} images={}", level, images.size());
        try {
            JsonNode data = adminClient.recognize(level, categories, images);
            return new OcrResult(data, messageOf(data));
        } catch (EnglishEssayAiAdminClient.EnglishEssayCallException cause) {
            // 内部入口が断った・答えられない理由を**日本語のまま**画面へ返す（500 にしない）。
            // 文字にできていないので「成功」とは言わない
            log.warn("english essay ocr internal call failed. retryable={} images={}",
                    cause.retryable(), images.size());
            throw new ConflictException(cause.getMessage());
        }
    }

    /** 画面に出す案内（何枚を文字にしたかを admin-api の応答から数える）。 */
    private static String messageOf(JsonNode data) {
        JsonNode pages = data == null ? null : data.get("pages");
        int count = pages != null && pages.isArray() ? pages.size() : 0;
        return count > 0
                ? "英作文の画像を文字にしました（" + count + " 枚）。内容を確認してから添削を受付けてください。"
                : "英作文の画像を文字にしました。内容を確認してから添削を受付けてください。";
    }
}
