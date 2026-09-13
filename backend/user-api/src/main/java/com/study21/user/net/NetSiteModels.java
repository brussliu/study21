package com.study21.user.net;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * サイト管理の API で使う型（リクエスト／レスポンス）。
 * コード値は画面側で日本語の表示文言に変換する（設計書 §2 の方針）。
 */
public final class NetSiteModels {

    /** 区分コード（端末モード別の許可区分）。 */
    public static final List<String> KIND_CODES = List.of("STUDY", "NORMAL", "BREAK", "GAME");
    /** 判定方法コード。 */
    public static final List<String> JUDGE_METHOD_CODES = List.of("PREFIX", "SUFFIX", "CONTAINS", "EXACT");
    /** 分類コード。 */
    public static final List<String> CATEGORY_CODES =
            List.of("LEARNING", "ENTERTAINMENT", "SHOPPING", "SNS", "OTHER");
    /** 承認ステータス。 */
    public static final List<String> APPROVAL_STATUSES = List.of("PENDING", "APPROVED", "REJECTED");
    /** 状態（2.1 共通: '1'=有効 / '0'=無効）。 */
    public static final List<String> STATUSES = List.of("1", "0");

    private NetSiteModels() {
    }

    /** 一覧の 1 行。 */
    public record SiteRow(
            long siteId,
            String siteName,
            String siteUrl,
            String hostName,
            String kindCode,
            String judgeMethodCode,
            String categoryCode,
            String categoryName,
            String approvalStatus,
            String status,
            String note,
            int version,
            String approvedBy,
            LocalDateTime approvedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        static SiteRow of(NetSiteEntity entity) {
            return new SiteRow(
                    entity.getSiteId(),
                    entity.getSiteName(),
                    entity.getSiteUrl(),
                    entity.getHostName(),
                    entity.getKindCode(),
                    entity.getJudgeMethodCode(),
                    entity.getCategoryCode(),
                    entity.getCategoryName(),
                    entity.getApprovalStatus(),
                    entity.getStatus(),
                    entity.getNote(),
                    entity.getVersion() == null ? 1 : entity.getVersion(),
                    null,
                    entity.getApprovedAt() == null ? null : entity.getApprovedAt().toLocalDateTime(),
                    entity.getCreatedAt() == null ? null : entity.getCreatedAt().toLocalDateTime(),
                    entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toLocalDateTime());
        }
    }

    /** 新規登録・更新のリクエスト（サイトURL は必須。分類は OTHER のときだけ名称を使う）。 */
    public record SiteSaveRequest(
            @NotBlank(message = "サイト名称を入力してください。")
            @Size(max = 200, message = "サイト名称は200文字以内で入力してください。")
            String siteName,

            @NotBlank(message = "サイトURLを入力してください。")
            String siteUrl,

            @NotBlank(message = "区分を選択してください。")
            String kindCode,

            String judgeMethodCode,

            @NotBlank(message = "分類を選択してください。")
            String categoryCode,

            @Size(max = 50, message = "分類名称は50文字以内で入力してください。")
            String categoryName,

            @Size(max = 200, message = "備考は200文字以内で入力してください。")
            String note,

            /** 楽観的ロック用。画面が表示していたバージョン（未指定なら現在値で照合する）。 */
            Integer version) {
    }

    /** 一覧のレスポンス。 */
    public record SiteSearchResult(
            List<SiteRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    /** 更新系のレスポンス。 */
    public record SiteMutationResult(String message, SiteRow row, Integer updatedCount) {

        static SiteMutationResult of(String message, NetSiteEntity entity) {
            return new SiteMutationResult(message, entity == null ? null : SiteRow.of(entity), null);
        }

        static SiteMutationResult count(String message, int updatedCount) {
            return new SiteMutationResult(message, null, updatedCount);
        }
    }
}
