package com.study21.user.classroom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AudioFileSampleRate} の検証（**外部ライブラリを使わずヘッダだけを読む**）。
 *
 * <p>実測: 取り込んだ mp3（44.1kHz）に 16kHz を宣言すると DashScope が
 * 「sample rate 16000 not equals with real 44100」でデコードに失敗する。ここでは
 * 「mp3 / wav のヘッダから正しい周波数を読めること」「読めないときは 0 を返すこと」を固定する。</p>
 */
class AudioFileSampleRateTest {

    /** MPEG 1 Layer III・44.1kHz のフレームヘッダ（`FF FB 90 00`）で始まる mp3 の中身を作る。 */
    private static byte[] mp3(byte versionAndLayer, byte rateBits, String id3SizeBytes) {
        byte[] body = new byte[64];
        body[0] = (byte) 0xFF;
        body[1] = versionAndLayer;
        body[2] = rateBits;
        body[3] = 0x00;
        if (id3SizeBytes == null) {
            return body;
        }
        byte[] tag = new byte[10];
        tag[0] = 'I';
        tag[1] = 'D';
        tag[2] = '3';
        tag[3] = 3;
        for (int index = 0; index < 4; index += 1) {
            tag[6 + index] = (byte) id3SizeBytes.charAt(index);
        }
        byte[] result = new byte[tag.length + body.length];
        System.arraycopy(tag, 0, result, 0, tag.length);
        System.arraycopy(body, 0, result, tag.length, body.length);
        return result;
    }

    /** `RIFF` / `WAVE` / `fmt ` を持つ最小の wav を作る。 */
    private static byte[] wav(int sampleRate) {
        byte[] bytes = new byte[44];
        System.arraycopy("RIFF".getBytes(), 0, bytes, 0, 4);
        System.arraycopy("WAVE".getBytes(), 0, bytes, 8, 4);
        System.arraycopy("fmt ".getBytes(), 0, bytes, 12, 4);
        bytes[16] = 16; // fmt チャンクの大きさ
        bytes[24] = (byte) (sampleRate & 0xFF);
        bytes[25] = (byte) ((sampleRate >> 8) & 0xFF);
        bytes[26] = (byte) ((sampleRate >> 16) & 0xFF);
        bytes[27] = (byte) ((sampleRate >> 24) & 0xFF);
        return bytes;
    }

    @Test
    @DisplayName("mp3（ID3v2 タグつき）のフレームヘッダから 44.1kHz を読む")
    void readsMp3SampleRate() {
        // FF FB = MPEG 1 Layer III、0x90 の bit2-3 が 00 → 44100
        byte[] tagged = mp3((byte) 0xFB, (byte) 0x90, "\0\0\0\0");
        assertThat(AudioFileSampleRate.of(tagged, "mp3")).isEqualTo(44100);

        // タグが無いファイルでも同じ
        byte[] plain = mp3((byte) 0xFB, (byte) 0x90, null);
        assertThat(AudioFileSampleRate.of(plain, "mp3")).isEqualTo(44100);
    }

    @Test
    @DisplayName("mp3: MPEG 2（0xF3）は別の表で読む（22.05kHz→16000 など）")
    void readsMp3Mpeg2SampleRate() {
        // FF F3 = MPEG 2 Layer III。3 バイト目の bit2-3 がサンプリング周波数の番号
        // （00 → 22050 / 10 → 16000）
        assertThat(AudioFileSampleRate.of(mp3((byte) 0xF3, (byte) 0x00, null), "mp3")).isEqualTo(22050);
        assertThat(AudioFileSampleRate.of(mp3((byte) 0xF3, (byte) 0x08, null), "mp3")).isEqualTo(16000);
    }

    @Test
    @DisplayName("wav の fmt チャンクから読む（44.1k / 48k）")
    void readsWavSampleRate() {
        assertThat(AudioFileSampleRate.of(wav(44100), "wav")).isEqualTo(44100);
        assertThat(AudioFileSampleRate.of(wav(48000), "wav")).isEqualTo(48000);
    }

    @Test
    @DisplayName("読めない中身・対象外の形式は 0（＝宣言しない）")
    void unknownReturnsZero() {
        assertThat(AudioFileSampleRate.of(new byte[64], "mp3")).isZero();
        assertThat(AudioFileSampleRate.of(wav(44100), "mp3")).isZero();
        assertThat(AudioFileSampleRate.of(wav(44100), "opus")).isZero();
        assertThat(AudioFileSampleRate.of(null, "mp3")).isZero();
        assertThat(AudioFileSampleRate.of(new byte[10], "wav")).isZero();
        assertThat(AudioFileSampleRate.of(wav(44100), null)).isZero();
    }
}
