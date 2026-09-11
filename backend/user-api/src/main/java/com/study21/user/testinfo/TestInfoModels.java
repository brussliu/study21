package com.study21.user.testinfo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

public final class TestInfoModels {
    private TestInfoModels() {}

    /**
     * 一覧・検索結果 1 件。
     * accuracyText はサーバ側で導出した表示用文言（満点が無い場合は "--"）。
     */
    public record Row(long testId, String testNo, String testName, String subject, String kind,
                      LocalDate examDate, Integer score, Integer fullScore, String accuracyText,
                      String memo, int version, int photoCount, List<FileInfo> files,
                      Timestamp createdAt, Timestamp updatedAt) {}

    /** 試験用紙ファイル 1 件。 */
    public record FileInfo(long fileId, int displayOrder, String originalFileName, String extension,
                           String mimeType, Long fileSize, String comment, boolean image,
                           String contentUrl, String previewUrl) {}

    public record Workspace(List<Row> rows) {}

    /** 登録・更新の共通入力。 */
    public record SaveRequest(
            @NotBlank(message = "テスト名を入力してください。") @Size(max = 120) String testName,
            @Size(max = 20) String subject,
            @Size(max = 20) String kind,
            LocalDate examDate,
            @Min(0) Integer score,
            @Min(0) Integer fullScore,
            String memo) {}

    /** 更新時は楽観的ロック用の version と、ファイルの並び順・備考を受け取る。 */
    public record UpdateRequest(
            @Min(1) Integer version,
            @NotBlank(message = "テスト名を入力してください。") @Size(max = 120) String testName,
            @Size(max = 20) String subject,
            @Size(max = 20) String kind,
            LocalDate examDate,
            @Min(0) Integer score,
            @Min(0) Integer fullScore,
            String memo,
            List<FileOrderRequest> files) {}

    /** 既存ファイルの並び順と備考。 */
    public record FileOrderRequest(long fileId, @Min(0) Integer displayOrder, String comment) {}

    /** 臨時ファイルから取り込むリクエスト。 */
    public record FromTempFileRequest(long tempFileId) {}

    /** 他のテストのファイルから取り込むリクエスト。 */
    public record FromTestFileRequest(@NotBlank String sourceTestNo, long sourceFileId) {}

    /** 保存結果（更新後の行を返す）。 */
    public record Saved(Row row) {}

    /** ダウンロード（原本）用の解決済みファイル。 */
    public record Download(Path path, String contentType, String downloadName) {}

    /** 削除結果。 */
    public record Deleted(boolean deleted) {}
}
