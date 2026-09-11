package com.study21.user.linkclip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.sql.Timestamp;
import java.util.List;

public final class LinkClipModels {
    private LinkClipModels() {}

    /** 一覧・詳細の 1 件。 */
    public record Row(long linkClipId, String folderCode, String sourceCode, String clipType,
                      String siteName, String pageTitle, String url, String normalizedUrl,
                      String summary, String aiSummary, String memo, String publisherName,
                      Timestamp publishedAt, Integer videoSeconds, String thumbnailUrl,
                      boolean favorite, boolean read, boolean archived, int viewCount,
                      Timestamp lastViewedAt, Timestamp metaFetchedAt, Timestamp aiSummarizedAt,
                      int version, List<String> tags, Timestamp createdAt, Timestamp updatedAt) {}

    /** タグ候補（使用件数つき）。 */
    public record TagOption(String name, int count) {}

    /** 保存先ごとの件数（左の仕分けレーンの表示用。フォルダ以外の絞り込みを反映する）。 */
    public record LaneCount(String folderCode, int count) {}

    /** 一覧の応答。タグ候補とレーン件数は絞り込み用に毎回返す。 */
    public record Workspace(List<Row> rows, List<TagOption> tags, List<LaneCount> lanes) {}

    /** 登録・更新の共通入力。 */
    public record SaveRequest(
            @NotBlank(message = "URLを入力してください。") String url,
            @NotBlank(message = "タイトルを入力してください。") @Size(max = 300) String pageTitle,
            @Size(max = 200) String siteName,
            @Size(max = 20) String folderCode,
            @Size(max = 20) String sourceCode,
            @Size(max = 20) String clipType,
            String summary,
            String aiSummary,
            String memo,
            @Size(max = 200) String publisherName,
            Timestamp publishedAt,
            Integer videoSeconds,
            String thumbnailUrl,
            Boolean favorite,
            Boolean archived,
            List<String> tags) {}

    /** 更新時は楽観的ロック用の version を必須にする。 */
    public record UpdateRequest(
            Integer version,
            @NotBlank(message = "URLを入力してください。") String url,
            @NotBlank(message = "タイトルを入力してください。") @Size(max = 300) String pageTitle,
            @Size(max = 200) String siteName,
            @Size(max = 20) String folderCode,
            @Size(max = 20) String sourceCode,
            @Size(max = 20) String clipType,
            String summary,
            String aiSummary,
            String memo,
            @Size(max = 200) String publisherName,
            Timestamp publishedAt,
            Integer videoSeconds,
            String thumbnailUrl,
            Boolean favorite,
            Boolean archived,
            List<String> tags) {}

    /** フラグだけを素早く切り替える（お気に入り / 既読 / アーカイブ）。 */
    public record FlagsRequest(Boolean favorite, Boolean read, Boolean archived) {}

    /** プレビュー要求。 */
    public record PreviewRequest(String url) {}

    /** 保存結果。 */
    public record Saved(Row row) {}

    /** 保存前のプレビュー（DB 書き込みなし）。 */
    public record Preview(String url, String normalizedUrl, String sourceCode, String clipType, String siteName,
                          String pageTitle, String summary, String publisherName, Timestamp publishedAt,
                          Integer videoSeconds, String thumbnailUrl, boolean localFile) {}

    /** 削除結果。 */
    public record Deleted(boolean deleted) {}

    /** URL の重複候補（保存前の注意表示用）。 */
    public record DuplicateCheck(boolean duplicated, List<Row> rows) {}
}
