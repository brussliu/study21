package com.study21.admin.studymonitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI へ渡す画像の作成（2.0 の {@code resizeForAi} の移行）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>設定の解像度へ**縦横比を無視して**引き伸ばす（2.0 と同じ）</li>
 *   <li>大きさが同じなら元のバイト列をそのまま返す（再エンコードしない）</li>
 *   <li>縮小したときは JPEG のバイト列と {@code image/jpeg} を返す</li>
 *   <li>画像として読めないバイト列は例外（AI を呼ぶ前に落とす）</li>
 * </ol>
 */
class StudyMonitorImageResizerTest {

    private final StudyMonitorImageResizer resizer = new StudyMonitorImageResizer();

    @Test
    @DisplayName("縦横比を無視して設定の解像度へ引き伸ばす")
    void stretchesToTargetResolution() throws Exception {
        // 2:1 の画像を 16:9 の設定解像度へ（2.0 と同じく比は合わせない）
        byte[] original = image(200, 100, "png");

        StudyMonitorImageResizer.ResizedImage resized =
                resizer.resize(original, new StudyMonitorAnalysisSettings.Resolution(1920, 1080));

        assertThat(resized.mime()).isEqualTo("image/jpeg");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(resized.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(1920);
        assertThat(decoded.getHeight()).isEqualTo(1080);
    }

    @Test
    @DisplayName("大きさが同じなら元のバイト列をそのまま返す（2.0 と同じ）")
    void keepsOriginalWhenSizeMatches() throws Exception {
        byte[] original = image(1920, 1080, "jpg");

        StudyMonitorImageResizer.ResizedImage resized =
                resizer.resize(original, new StudyMonitorAnalysisSettings.Resolution(1920, 1080));

        assertThat(resized.bytes()).isSameAs(original);
        assertThat(resized.mime()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("大きさが同じでも PNG なら MIME は png（2.0 は常に jpeg と名乗っていた）")
    void detectsPngMimeWhenSizeMatches() throws Exception {
        byte[] original = image(1280, 720, "png");

        StudyMonitorImageResizer.ResizedImage resized =
                resizer.resize(original, new StudyMonitorAnalysisSettings.Resolution(1280, 720));

        assertThat(resized.bytes()).isSameAs(original);
        assertThat(resized.mime()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("画像として読めないバイト列は例外にする")
    void rejectsUndecodableBytes() {
        assertThatThrownBy(() -> resizer.resize("これは画像ではありません".getBytes(),
                new StudyMonitorAnalysisSettings.Resolution(1920, 1080)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("読み込めません");

        assertThatThrownBy(() -> resizer.resize(new byte[0],
                new StudyMonitorAnalysisSettings.Resolution(1920, 1080)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] image(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
