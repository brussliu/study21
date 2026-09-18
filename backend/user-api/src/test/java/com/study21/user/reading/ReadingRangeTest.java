package com.study21.user.reading;

import com.study21.common.core.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `GET /books/{bookId}/pdf`（表紙も同じ）の Range 解決の検証。
 *
 * pdf.js は PDF を分割して取得するので、206 の範囲が要求どおりであること、
 * 不正・範囲外が 416 になることをここで固定する（契約 §3.3）。
 */
class ReadingRangeTest {

    private static final long LENGTH = 100L;

    @Test
    void returnsNullWhenNoRangeRequested() {
        assertThat(ReadingRange.resolve(null, LENGTH)).isNull();
        assertThat(ReadingRange.resolve("", LENGTH)).isNull();
        assertThat(ReadingRange.resolve("   ", LENGTH)).isNull();
    }

    @Test
    void resolvesStartAndEnd() {
        ReadingRange.Resolved range = ReadingRange.resolve("bytes=0-9", LENGTH);

        assertThat(range.start()).isZero();
        assertThat(range.end()).isEqualTo(9);
        assertThat(range.count()).isEqualTo(10);
        assertThat(range.contentRange()).isEqualTo("bytes 0-9/100");
    }

    @Test
    void resolvesOpenEndedRange() {
        ReadingRange.Resolved range = ReadingRange.resolve("bytes=90-", LENGTH);

        assertThat(range.start()).isEqualTo(90);
        assertThat(range.end()).isEqualTo(99);
        assertThat(range.count()).isEqualTo(10);
    }

    @Test
    void resolvesSuffixRange() {
        ReadingRange.Resolved range = ReadingRange.resolve("bytes=-10", LENGTH);

        assertThat(range.start()).isEqualTo(90);
        assertThat(range.end()).isEqualTo(99);
        assertThat(range.contentRange()).isEqualTo("bytes 90-99/100");
    }

    /** suffix が実体より大きいときは先頭から返す（RFC どおり）。 */
    @Test
    void resolvesSuffixLargerThanFile() {
        ReadingRange.Resolved range = ReadingRange.resolve("bytes=-500", LENGTH);

        assertThat(range.start()).isZero();
        assertThat(range.end()).isEqualTo(99);
    }

    /** 終了が実体の末尾を超えていたら末尾に丸める。 */
    @Test
    void clampsEndBeyondFile() {
        ReadingRange.Resolved range = ReadingRange.resolve("bytes=50-9999", LENGTH);

        assertThat(range.start()).isEqualTo(50);
        assertThat(range.end()).isEqualTo(99);
        assertThat(range.count()).isEqualTo(50);
    }

    @Test
    void resolvesWholeFileRange() {
        ReadingRange.Resolved range = ReadingRange.resolve("bytes=0-99", LENGTH);

        assertThat(range.count()).isEqualTo(100);
    }

    @Test
    void rejectsRangeOutsideFile() {
        assertThat416("bytes=100-");
        assertThat416("bytes=200-300");
        // 開始が終了より後
        assertThat416("bytes=10-5");
        // suffix が 0
        assertThat416("bytes=-0");
        // 数値でない・区切りが無い・空
        assertThat416("bytes=abc-def");
        assertThat416("bytes=10");
        assertThat416("bytes=-");
        assertThat416("bytes=");
    }

    /** 複数範囲は未対応（PDF の分割取得は単一範囲で足りる）。 */
    @Test
    void rejectsMultipleRanges() {
        assertThat416("bytes=0-9,20-29");
    }

    /** bytes 以外の単位は扱わない。 */
    @Test
    void rejectsNonByteUnit() {
        assertThat416("items=0-9");
    }

    /** 空のファイルにはどの範囲も成立しない。 */
    @Test
    void rejectsAnyRangeForEmptyFile() {
        assertThat416("bytes=0-", 0L);
        assertThat416("bytes=-10", 0L);
    }

    private static void assertThat416(String header) {
        assertThat416(header, LENGTH);
    }

    private static void assertThat416(String header, long length) {
        assertThatThrownBy(() -> ReadingRange.resolve(header, length))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE));
    }
}
