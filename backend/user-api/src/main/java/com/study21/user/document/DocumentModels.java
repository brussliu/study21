package com.study21.user.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.sql.Timestamp;
import java.util.List;

public final class DocumentModels {
    private DocumentModels() {}

    /**
     * 資料の登録・更新リクエスト。
     * 分類（大分類〜細分類）はフォルダ階層そのものなので受け付けず、
     * サーバ側で選択フォルダの祖先名称から導出する。
     */
    public record SaveRequest(
            Long folderId,
            @Pattern(regexp = "[01]", message = "ステータスは0または1を指定してください。") String status,
            @Size(max = 10) String expiryDate,
            @Size(max = 100) String comment) {}

    public record FolderRequest(Long parentFolderId, @NotBlank @Size(max = 100) String folderName,
                                Integer displayOrder, @Size(max = 200) String note) {}

    public record Folder(long folderId, Long parentFolderId, String folderName, int displayOrder, String note) {}

    /**
     * 一覧・フォルダビュー用の資料サマリ。
     * files は資料に含まれるファイル（枝番号順）。フォルダビューでファイルごとの
     * アイコンと操作を出すために、fileCount と併せて内容も返す。
     */
    public record Summary(String documentNo, Long folderId, String status, String expiryDate,
                          String largeCategory, String mediumCategory, String smallCategory,
                          String detailCategory, String comment, int fileCount, List<FileInfo> files,
                          Timestamp createdAt, Timestamp updatedAt) {}

    public record FileInfo(int branchNo, String originalFileName, String extension, String comment,
                           String contentUrl, boolean image) {}

    public record Workspace(List<Folder> folders, List<Summary> documents) {}

    public record Download(java.nio.file.Path path, String contentType, String downloadName) {}
}
