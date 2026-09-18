package com.study21.user.reading;

/**
 * `GET /books/{bookId}/pdf`（と表紙）の Range 要求の解決。
 *
 * <p>RFC 9110 の `bytes=` 形式のうち、単一範囲だけを扱う:</p>
 * <ul>
 *   <li>`bytes=start-end` … 開始と終了（終了が実体より大きければ末尾に丸める）</li>
 *   <li>`bytes=start-` … 開始から最後まで</li>
 *   <li>`bytes=-suffix` … 最後の suffix バイト</li>
 * </ul>
 *
 * <p>複数範囲（`,` 区切り）・解釈できない値・範囲外は 416 にする。Range ヘッダが
 * 無いときだけ null を返し、呼び出し側は 200 で全体を返す。</p>
 *
 * <p>pdf.js は PDF を分割取得するので、この解決が 206 の内容と一致している必要がある。</p>
 */
public final class ReadingRange {

    private ReadingRange() {
    }

    /** 返す範囲（バイト位置。end は含む）。 */
    public record Resolved(long start, long end, long length) {

        /** 返すバイト数。 */
        public long count() {
            return end - start + 1;
        }

        /** `Content-Range` の値。 */
        public String contentRange() {
            return "bytes " + start + "-" + end + "/" + length;
        }
    }

    /**
     * Range ヘッダを解決する。
     *
     * @param header Range ヘッダの値（null・空白のみなら範囲要求なし）
     * @param length 実体のバイト数
     * @return 返す範囲。範囲要求が無ければ null
     * @throws ReadingApiException 解釈できない・範囲外のとき（416）
     */
    public static Resolved resolve(String header, long length) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String value = header.trim();
        if (!value.regionMatches(true, 0, "bytes=", 0, "bytes=".length())) {
            // bytes 以外の単位（items= など）は扱わない
            throw ReadingApiException.rangeNotSatisfiable();
        }
        String spec = value.substring("bytes=".length()).trim();
        if (spec.isEmpty() || spec.contains(",")) {
            // 複数範囲は未対応（PDF の分割取得は単一範囲で足りる）
            throw ReadingApiException.rangeNotSatisfiable();
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            throw ReadingApiException.rangeNotSatisfiable();
        }
        String startText = spec.substring(0, dash).trim();
        String endText = spec.substring(dash + 1).trim();

        if (startText.isEmpty()) {
            // bytes=-suffix（最後の suffix バイト）
            long suffix = parse(endText);
            if (suffix <= 0 || length <= 0) {
                throw ReadingApiException.rangeNotSatisfiable();
            }
            long start = Math.max(0, length - suffix);
            return new Resolved(start, length - 1, length);
        }

        long start = parse(startText);
        if (start < 0 || start >= length) {
            throw ReadingApiException.rangeNotSatisfiable();
        }
        if (endText.isEmpty()) {
            return new Resolved(start, length - 1, length);
        }
        long end = parse(endText);
        if (end < start) {
            throw ReadingApiException.rangeNotSatisfiable();
        }
        // 終了が実体の末尾を超えていたら末尾に丸める（RFC どおり 206 で返す）
        return new Resolved(start, Math.min(end, length - 1), length);
    }

    private static long parse(String text) {
        if (text.isEmpty()) {
            throw ReadingApiException.rangeNotSatisfiable();
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw ReadingApiException.rangeNotSatisfiable();
        }
    }
}
