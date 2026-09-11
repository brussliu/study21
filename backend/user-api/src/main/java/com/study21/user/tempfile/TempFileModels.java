package com.study21.user.tempfile;

import jakarta.validation.constraints.Size;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;

public final class TempFileModels {
    private TempFileModels() {}

    /** 一覧・検索結果 1 件。thumbnail は base64（data: URL の接頭辞なし）、画像以外は null。 */
    public record Summary(long tempFileId, String originalFileName, String extension, String mimeType,
                          long fileSize, String comment, String thumbnail, boolean image,
                          String contentUrl, Timestamp createdAt, Timestamp updatedAt) {}

    /** メタ情報（ファイル名・コメント）更新リクエスト。 */
    public record UpdateRequest(
            @Size(max = 255) String originalFileName,
            String comment) {}

    /** バッチ削除リクエスト。 */
    public record DeleteRequest(List<Long> ids) {}

    /** ダウンロード（原本）用の解決済みファイル。 */
    public record Download(Path path, String contentType, String downloadName) {}
}
