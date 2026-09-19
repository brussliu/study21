package com.study21.admin.studymonitor;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * AI に渡す 1 枚を作る（設定の解像度へ縮小し、JPEG のバイト列にする）。
 *
 * <p>2.0 の {@code BatL03Task#resizeForAi} をそのまま移した。**縦横比は無視して引き伸ばす**
 * （{@code drawImage(source, 0, 0, width, height, null)}）。切り出し画像と設定解像度は
 * どちらも 16:9 なので通常は同じ比になるが、比が違う画像でも 2.0 と同じ結果にする。</p>
 *
 * <p>大きさが既に設定と同じときは**元のバイト列をそのまま返す**（2.0 と同じ。43,000 枚規模で
 * 再エンコードの画質劣化と CPU を避ける）。そのときの MIME は先頭バイトから判定する
 * （2.0 は常に {@code image/jpeg} を名乗っていた。切り出しは ffmpeg の JPEG なので通常は jpeg）。</p>
 */
@Component
public class StudyMonitorImageResizer {

    /** AI に渡す画像（バイト列と MIME）。 */
    public record ResizedImage(byte[] bytes, String mime) {
    }

    /**
     * 設定の解像度へ縮小する。
     *
     * @throws IllegalArgumentException 画像として読み込めない、または JPEG へ変換できないとき
     */
    public ResizedImage resize(byte[] original, StudyMonitorAnalysisSettings.Resolution target) {
        if (original == null || original.length == 0) {
            throw new IllegalArgumentException("AI送信用の画像を読み込めません。");
        }
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(original));
        } catch (Exception cause) {
            throw new IllegalArgumentException("AI送信用の画像を読み込めません。", cause);
        }
        if (source == null) {
            throw new IllegalArgumentException("AI送信用の画像を読み込めません。");
        }
        if (source.getWidth() == target.width() && source.getHeight() == target.height()) {
            return new ResizedImage(original, mimeOf(original));
        }
        BufferedImage resized = new BufferedImage(target.width(), target.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, target.width(), target.height(), null);
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

    /** 先頭バイトから MIME を判定する（分からないときは jpeg として扱う＝2.0 と同じ）。 */
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
