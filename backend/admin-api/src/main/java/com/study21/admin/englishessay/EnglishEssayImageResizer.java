package com.study21.admin.englishessay;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * AI へ送る 1 枚を作る（設定 {@code ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS} を超える長辺を縮小する）。
 *
 * <p>2.0 と同じ考え方: <b>縦横比は保つ</b>（長辺だけを上限に合わせる）。上限以内の画像は
 * <b>元のバイト列をそのまま返す</b>（43,000 枚規模で再エンコードの画質劣化と CPU を避ける）。</p>
 *
 * <p>縮小した画像は JPEG にする（OCR の対象は紙の写真・スキャンなのでアルファは要らない。
 * 接縫 {@code GeometryAiClient} は MIME をそのままプロバイダへ渡す）。</p>
 */
@Component
public class EnglishEssayImageResizer {

    /** AI に渡す画像（バイト列と MIME）。 */
    public record ResizedImage(byte[] bytes, String mime) {
    }

    /**
     * 長辺を {@code maxEdge} に収める。
     *
     * @throws IllegalArgumentException 画像として読み込めないとき（日本語の理由）
     */
    public ResizedImage resize(byte[] original, int maxEdge) {
        if (original == null || original.length == 0) {
            throw new IllegalArgumentException("AI送信用の画像を読み込めません（ファイルが空です）。");
        }
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(original));
        } catch (Exception cause) {
            throw new IllegalArgumentException("AI送信用の画像を読み込めません。", cause);
        }
        if (source == null) {
            throw new IllegalArgumentException("AI送信用の画像を読み込めません（対応していない形式です）。");
        }
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxEdge) {
            return new ResizedImage(original, mimeOf(original));
        }
        double scale = (double) maxEdge / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(resized, "jpg", output)) {
                throw new IllegalStateException("AI送信用画像の変換に失敗しました。");
            }
        } catch (IllegalStateException cause) {
            throw cause;
        } catch (Exception cause) {
            throw new IllegalStateException("AI送信用画像の変換に失敗しました。", cause);
        }
        return new ResizedImage(output.toByteArray(), "image/jpeg");
    }

    /** 先頭バイトから MIME を判定する（分からないときは jpeg として扱う＝既存と同じ）。 */
    static String mimeOf(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 12), java.nio.charset.StandardCharsets.US_ASCII)
                .toLowerCase(Locale.ROOT);
        if (head.startsWith("riff") && head.contains("webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
