package com.study21.user.net;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 端末コントロールの API で使う型（リクエスト／レスポンス）。
 */
public final class NetTerminalModels {

    /** 端末モード: T=通常 / K=休憩 / G=ゲーム / B=勉強 / S=停止 / J=自由。 */
    public static final List<String> MODE_CODES = List.of("T", "K", "G", "B", "S", "J");
    /** 状態（2.1 共通: '1'=有効 / '0'=無効）。 */
    public static final List<String> STATUSES = List.of("1", "0");

    private NetTerminalModels() {
    }

    /** 一覧の 1 行。 */
    public record TerminalRow(
            long terminalId,
            String ipAddress,
            String terminalName,
            String terminalMode,
            String status,
            String note,
            LocalDateTime lastSeenAt,
            int version,
            String updatedByName,
            String updatedByCode,
            LocalDateTime updatedAt) {

        static TerminalRow of(NetTerminalEntity entity) {
            return new TerminalRow(
                    entity.getTerminalId(),
                    entity.getIpAddress(),
                    entity.getTerminalName(),
                    entity.getTerminalMode(),
                    entity.getStatus(),
                    entity.getNote(),
                    entity.getLastSeenAt() == null ? null : entity.getLastSeenAt().toLocalDateTime(),
                    entity.getVersion() == null ? 1 : entity.getVersion(),
                    entity.getUpdatedByName(),
                    entity.getUpdatedByCode(),
                    entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toLocalDateTime());
        }
    }

    /** 端末モード変更のリクエスト（version は楽観的ロック用。未指定なら照合しない）。 */
    public record ModeChangeRequest(
            @NotBlank(message = "端末ステータスが不正です。") String terminalMode,
            Integer version) {
    }

    /** 選択した端末のモードを一括変更するリクエスト。 */
    public record BulkModeChangeRequest(
            @NotEmpty(message = "更新対象の端末を選択してください。") List<Long> terminalIds,
            @NotBlank(message = "端末ステータスが不正です。") String terminalMode) {
    }

    /**
     * 端末の新規登録・編集の内容。
     * `version` は編集時の楽観的ロック用（新規では使わない）。
     */
    public record TerminalSaveRequest(
            @NotBlank(message = "IPアドレスを入力してください。")
            @Size(max = 45, message = "IPアドレスは45文字以内で入力してください。") String ipAddress,
            @NotBlank(message = "端末名称を入力してください。")
            @Size(max = 100, message = "端末名称は100文字以内で入力してください。") String terminalName,
            @NotBlank(message = "端末ステータスが不正です。") String terminalMode,
            @NotBlank(message = "状態が不正です。") String status,
            @Size(max = 200, message = "備考は200文字以内で入力してください。") String note,
            Integer version) {
    }

    /** 一覧のレスポンス。 */
    public record TerminalSearchResult(
            List<TerminalRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    /** 更新系のレスポンス。 */
    public record TerminalMutationResult(String message, int requestedCount, int updatedCount) {
    }
}
