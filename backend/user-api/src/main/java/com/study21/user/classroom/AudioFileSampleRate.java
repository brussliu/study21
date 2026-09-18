package com.study21.user.classroom;

import java.util.Locale;

/**
 * 取り込んだ音声ファイル（mp3 / wav）の**サンプリング周波数**をヘッダから読む。
 *
 * <p>阿里巴巴（DashScope の Paraformer-Realtime-V2）は `sample_rate` を宣言どおりに扱うため、
 * 画面が作る 16kHz PCM と同じ値を mp3・wav にも宣言すると
 * 「Failed to decode audio: sample rate 16000 not equals with real 44100」で**デコードに失敗**する
 * （実測）。mp3 は 44.1kHz、wav は 48kHz など**ファイルごとに違う**ので、ヘッダから読んで渡す。</p>
 *
 * <p>分からないときは 0（＝`sample_rate` を送らない）。読むのは先頭だけで、外部ライブラリを使わない。</p>
 */
final class AudioFileSampleRate {

    /** MP3 のヘッダを探す範囲（ID3v2 タグの直後にあるので先頭だけで足りる）。 */
    private static final int MP3_SCAN_BYTES = 64 * 1024;

    private AudioFileSampleRate() {
    }

    /**
     * 音声ファイルのサンプリング周波数（Hz）。
     *
     * @param format DashScope へ送る形式（`mp3` / `wav`。それ以外は 0）
     * @return 周波数。分からないときは 0
     */
    static int of(byte[] bytes, String format) {
        if (bytes == null || bytes.length < 16 || format == null) {
            return 0;
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "mp3" -> mp3(bytes);
            case "wav" -> wav(bytes);
            default -> 0;
        };
    }

    /**
     * MP3 の最初のフレームヘッダから読む（`ID3v2` タグがあれば飛ばす）。
     *
     * <p>フレームヘッダは 4 バイト: `FF Ex` のあとのビットに
     * MPEG のバージョン（bit 3-4）とサンプリング周波数の番号（bit 10-11）が入る。</p>
     */
    static int mp3(byte[] bytes) {
        int start = id3TagSize(bytes);
        int limit = Math.min(bytes.length - 3, start + MP3_SCAN_BYTES);
        for (int index = start; index < limit; index += 1) {
            // フレーム同期（11 ビットの 1）＋ Layer（00 は予約）＋サンプリング周波数の番号（11 は予約）
            if ((bytes[index] & 0xFF) != 0xFF) continue;
            int second = bytes[index + 1] & 0xFF;
            int third = bytes[index + 2] & 0xFF;
            if ((second & 0xE0) != 0xE0) continue;
            int versionBits = (second >> 3) & 0x03;
            int layerBits = (second >> 1) & 0x03;
            int rateIndex = (third >> 2) & 0x03;
            if (versionBits == 0x01 || layerBits == 0x00 || rateIndex == 0x03) continue;
            return switch (versionBits) {
                case 0x03 -> new int[] { 44100, 48000, 32000 }[rateIndex]; // MPEG 1
                case 0x02 -> new int[] { 22050, 24000, 16000 }[rateIndex]; // MPEG 2
                default -> new int[] { 11025, 12000, 8000 }[rateIndex];    // MPEG 2.5
            };
        }
        return 0;
    }

    /** `ID3v2` タグの大きさ（タグが無ければ 0）。 */
    private static int id3TagSize(byte[] bytes) {
        if (bytes.length < 10 || bytes[0] != 'I' || bytes[1] != 'D' || bytes[2] != '3') {
            return 0;
        }
        // 大きさは 7 ビットずつ（最上位ビットは使わない）
        int size = ((bytes[6] & 0x7F) << 21) | ((bytes[7] & 0x7F) << 14)
                | ((bytes[8] & 0x7F) << 7) | (bytes[9] & 0x7F);
        return Math.min(10 + size, bytes.length);
    }

    /** WAV の `fmt ` チャンクから読む（`RIFF` / `WAVE` でなければ 0）。 */
    static int wav(byte[] bytes) {
        if (bytes.length < 44 || !tagAt(bytes, 0, "RIFF") || !tagAt(bytes, 8, "WAVE")) {
            return 0;
        }
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            int size = littleEndianInt(bytes, offset + 4);
            if (size < 0) return 0;
            if (tagAt(bytes, offset, "fmt ") && offset + 8 + 8 <= bytes.length) {
                int rate = littleEndianInt(bytes, offset + 8 + 4);
                return rate > 0 ? rate : 0;
            }
            // チャンクは 2 バイト単位（奇数サイズは 1 バイト詰め物）
            offset += 8 + size + (size % 2);
        }
        return 0;
    }

    private static boolean tagAt(byte[] bytes, int offset, String tag) {
        if (offset + tag.length() > bytes.length) return false;
        for (int index = 0; index < tag.length(); index += 1) {
            if (bytes[offset + index] != tag.charAt(index)) return false;
        }
        return true;
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        if (offset + 4 > bytes.length) return 0;
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16) | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
