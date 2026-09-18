package com.study21.user.controller;

import com.study21.common.core.exception.ApiException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.user.reading.ReadingDictionaryService;
import com.study21.user.reading.ReadingModels;
import com.study21.user.reading.ReadingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `GET /books/{bookId}/pdf`（表紙も同じ）の HTTP の形と**返すバイト列**の検証。
 *
 * pdf.js がそのまま読めるように、200 は `application/pdf` + `inline` + `Accept-Ranges`、
 * Range つきは 206 + `Content-Range` でその範囲だけを書き出す。不正・範囲外は 416
 * （何も書く前に投げる）。契約 §3.3。
 */
class ReadingControllerFileTest {

    private static final String BODY = "0123456789";

    @TempDir
    Path tempDir;

    private ReadingService service;
    private ReadingController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReadingService.class);
        // 配信の検証しかしないので辞書はモックで足りる
        controller = new ReadingController(service, mock(ReadingDictionaryService.class));
    }

    /** 10 バイトのダミー（表紙も同じ経路なので両方返す）。 */
    private MockHttpServletResponse stubFile() throws Exception {
        Path path = tempDir.resolve("book.pdf");
        Files.write(path, BODY.getBytes(StandardCharsets.UTF_8));
        ReadingModels.FileDownload file =
                new ReadingModels.FileDownload(path, "application/pdf", "Harry Potter.pdf", 10L);
        when(service.downloadPdf(any(), eq(42L))).thenReturn(file);
        when(service.downloadCover(any(), eq(42L))).thenReturn(file);
        return new MockHttpServletResponse();
    }

    @Test
    void returnsWholeFileWhenNoRangeHeader() throws Exception {
        MockHttpServletResponse response = stubFile();

        controller.pdf(null, 42L, null, false, response);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        // 日本語や空白を含む名前は RFC 5987 で符号化される（inline であることが本質）
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline")
                .contains("filename")
                .contains("Harry_Potter.pdf");
        assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).isNull();
        assertThat(response.getContentAsByteArray()).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void returnsOnlyRequestedRange() throws Exception {
        MockHttpServletResponse response = stubFile();

        controller.pdf(null, 42L, "bytes=2-5", false, response);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PARTIAL_CONTENT.value());
        assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 2-5/10");
        assertThat(response.getContentAsByteArray()).isEqualTo("2345".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void returnsSuffixRange() throws Exception {
        MockHttpServletResponse response = stubFile();

        controller.cover(null, 42L, "bytes=-3", response); // 表紙も同じ経路

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PARTIAL_CONTENT.value());
        assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 7-9/10");
        assertThat(response.getContentAsByteArray()).isEqualTo("789".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void returnsOpenEndedRange() throws Exception {
        MockHttpServletResponse response = stubFile();

        controller.pdf(null, 42L, "bytes=7-", false, response);

        assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 7-9/10");
        assertThat(response.getContentAsByteArray()).isEqualTo("789".getBytes(StandardCharsets.UTF_8));
    }

    /** `download=true` は添付（ブラウザが保存する）。日本語の名前も RFC 5987 で入る。 */
    @Test
    void returnsAttachmentWhenDownloadRequested() throws Exception {
        Path path = tempDir.resolve("book.pdf");
        Files.write(path, BODY.getBytes(StandardCharsets.UTF_8));
        ReadingModels.FileDownload japanese = new ReadingModels.FileDownload(
                path, "application/pdf", "金庸全集精排三联版.pdf", 10L);
        when(service.downloadPdf(any(), eq(42L))).thenReturn(japanese);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.pdf(null, 42L, null, true, response);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        // RFC 5987（UTF-8）と ASCII フォールバックの両方が入る
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("filename*=UTF-8''%E9%87%91%E5%BA%B8%E5%85%A8%E9%9B%86%E7%B2%BE%E6%8E%92%E4%B8%89%E8%81%94%E7%89%88.pdf")
                .contains("filename=");
        assertThat(response.getContentAsByteArray()).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
    }

    /** 不正・範囲外は 416（このときは何も書かない＝エラー本文は GlobalExceptionHandler が返す）。 */
    @Test
    void rejectsInvalidRangeWithoutWritingBody() throws Exception {
        MockHttpServletResponse response = stubFile();

        assertThatThrownBy(() -> controller.pdf(null, 42L, "bytes=99-", false, response))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE));
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    /** 行が無い・実体が無いときは 404（画面が「PDF 未登録」を出す）。 */
    @Test
    void propagatesNotFoundWhenPdfIsMissing() {
        when(service.downloadPdf(any(), eq(404L))).thenThrow(new NotFoundException("本文 PDF が登録されていません。"));

        assertThatThrownBy(() -> controller.pdf(null, 404L, null, false, new MockHttpServletResponse()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("登録されていません");
    }
}
