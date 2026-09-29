package com.study21.admin.englishessay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI へ送る画像の縮小（設定 {@code ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS} = 長辺の上限）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>長辺が上限を超えるときだけ縮小する（縦横比は保つ）</li>
 *   <li>上限以内なら<b>元のバイト列をそのまま返す</b>（再エンコードの劣化と CPU を避ける）</li>
 *   <li>画像として読めないものは日本語の理由で拒否する</li>
 * </ol>
 */
class EnglishEssayImageResizerTest {

    private final EnglishEssayImageResizer resizer = new EnglishEssayImageResizer();

    private static byte[] image(int width, int height, String format) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
            return output.toByteArray();
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    @Test
    @DisplayName("長辺が上限を超えたら、縦横比を保って縮小する")
    void shrinksToMaxEdge() throws Exception {
        EnglishEssayImageResizer.ResizedImage resized = resizer.resize(image(4000, 3000, "png"), 2048);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(resized.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(2048);
        assertThat(decoded.getHeight()).isEqualTo(1536);
        assertThat(resized.mime()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("上限以内なら元のバイト列をそのまま返す（MIME は先頭バイトから判定）")
    void keepsSmallImage() {
        byte[] original = image(800, 600, "png");

        EnglishEssayImageResizer.ResizedImage resized = resizer.resize(original, 2048);

        assertThat(resized.bytes()).isSameAs(original);
        assertThat(resized.mime()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("画像として読めないときは日本語の理由で拒否する")
    void rejectsNonImage() {
        assertThatThrownBy(() -> resizer.resize(new byte[] {1, 2, 3}, 2048))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("画像");
        assertThatThrownBy(() -> resizer.resize(new byte[0], 2048))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
