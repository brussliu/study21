package com.study21.user.browserext;

import com.study21.user.net.WebBrowsingLogModels;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * ブラウザ拡張（`extension/`）との連携のモデル。
 *
 * <p>2.0 では拡張が送ってくる JSON の `userId` をそのまま信じていた（認証なし）。
 * 2.1 では「インターネット利用履歴 → Web閲覧履歴」画面で発行する**接続コード**を
 * 拡張に設定させ、その接続コードから持ち主（利用者アカウントID）を特定する。</p>
 *
 * <ul>
 *   <li>画面向け … {@code /api/user/browser-extension/registration}（ログイン必須）</li>
 *   <li>拡張向け … {@code /api/user/browser-extension/register|heartbeat|events}
 *       （接続コードで認証。ログイン不要）</li>
 * </ul>
 */
public final class BrowserExtensionModels {

    private BrowserExtensionModels() {
    }

    /** 拡張が送ってくるイベント種別（Web閲覧履歴と同じ 5 種類）。 */
    public static final List<String> EVENT_TYPES = WebBrowsingLogModels.EVENT_TYPES;

    /** 1 バッチで受け付ける上限。拡張は 100 件ずつ送ってくる。 */
    public static final int MAX_EVENTS_PER_BATCH = 500;

    /** 最終心拍からこの分数以内なら「接続中」とみなす（拡張は 5 分ごとに心拍を送る）。 */
    public static final int ONLINE_WINDOW_MINUTES = 10;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    // ---------------------------------------------------------------- 画面向け

    /**
     * Web閲覧履歴タブの「ブラウザ拡張」カードが使う。
     *
     * @param issued      接続コードを発行済みか
     * @param token       接続コード（未発行なら null）
     * @param deviceCount 登録済みの端末数
     * @param onlineCount 接続中の端末数（最終心拍が {@link #ONLINE_WINDOW_MINUTES} 分以内）
     */
    public record RegistrationView(
            boolean issued,
            String token,
            int deviceCount,
            int onlineCount,
            List<DeviceRow> devices,
            String message) {
    }

    /** 登録済み端末の 1 行。 */
    public record DeviceRow(
            long registrationId,
            String deviceId,
            String deviceName,
            String osType,
            String browserType,
            String browserVersion,
            String extensionVersion,
            String profileId,
            String status,
            /** 最終心拍が 10 分以内か */
            boolean online,
            String lastStartedAt,
            String lastSentAt,
            String lastHeartbeatAt,
            String createdAt) {
    }

    // ------------------------------------------------------------ 拡張からの受信

    /** `POST /register` … 拡張が起動時に送る端末の情報。 */
    public record RegisterRequest(
            @NotBlank(message = "端末識別子を指定してください。")
            @Size(max = 200, message = "端末識別子が長すぎます。") String deviceId,
            @Size(max = 200, message = "端末名称が長すぎます。") String deviceName,
            @Size(max = 50, message = "OS種別が長すぎます。") String osType,
            @Size(max = 50, message = "ブラウザ種別が長すぎます。") String browserType,
            @Size(max = 50, message = "ブラウザバージョンが長すぎます。") String browserVersion,
            @Size(max = 50, message = "拡張機能バージョンが長すぎます。") String extensionVersion,
            @Size(max = 200, message = "ブラウザプロファイルIDが長すぎます。") String profileId,
            /** ブラウザの起動時刻（ISO 8601。読めない値は受信時刻で代用する） */
            String registeredAt) {
    }

    /** `POST /heartbeat` … 5 分ごとの生存通知。 */
    public record HeartbeatRequest(
            @NotBlank(message = "端末識別子を指定してください。")
            @Size(max = 200, message = "端末識別子が長すぎます。") String deviceId,
            String heartbeatAt) {
    }

    /** `POST /events` … 閲覧イベントのバッチ。 */
    public record EventBatchRequest(
            @NotBlank(message = "端末識別子を指定してください。")
            @Size(max = 200, message = "端末識別子が長すぎます。") String deviceId,
            @Size(max = 200, message = "端末名称が長すぎます。") String deviceName,
            @Size(max = 50, message = "ブラウザ種別が長すぎます。") String browserType,
            @Size(max = 50, message = "ブラウザバージョンが長すぎます。") String browserVersion,
            @Size(max = 50, message = "拡張機能バージョンが長すぎます。") String extensionVersion,
            @Size(max = 200, message = "ブラウザプロファイルIDが長すぎます。") String profileId,
            String sentAt,
            @NotEmpty(message = "イベントが空です。")
            @Size(max = MAX_EVENTS_PER_BATCH, message = "1 回に送れるイベントは " + MAX_EVENTS_PER_BATCH + " 件までです。")
            List<EventItem> events) {
    }

    /** 1 イベント。拡張の `background.js` が作る形。 */
    public record EventItem(
            /** 拡張が付ける UUID。再送で二重登録しないための鍵（無くても登録はできる） */
            @Size(max = 100, message = "イベント識別子が長すぎます。") String clientEventId,
            @NotBlank(message = "イベント種別を指定してください。")
            @Size(max = 50, message = "イベント種別が長すぎます。") String eventType,
            String url,
            @Size(max = 500, message = "ドメインが長すぎます。") String domain,
            String pageTitle,
            String referrerUrl,
            String faviconUrl,
            @Size(max = 100, message = "ページ遷移種別が長すぎます。") String transitionType,
            Long tabId,
            Long windowId,
            @Size(max = 200, message = "セッションIDが長すぎます。") String sessionId,
            Boolean active,
            Boolean fromHistorySync,
            /** アクセス日時（ISO 8601。読めない値は受信時刻で代用する） */
            String visitedAt,
            Integer staySeconds,
            Integer visitCount) {
    }

    // ------------------------------------------------------------ 拡張への返信

    /** `POST /register` の返信。 */
    public record RegisterResult(String deviceId, String deviceName, String message) {
    }

    /** `POST /heartbeat` の返信。 */
    public record HeartbeatResult(String deviceId, String heartbeatAt, String message) {
    }

    /**
     * `POST /events` の返信。
     *
     * @param receivedCount  受け取った件数
     * @param insertedCount  実際に登録した件数
     * @param duplicateCount すでに登録済みだった件数（再送）
     * @param skippedCount   登録しなかった件数（未知のイベント種別など）
     */
    public record BatchSaveResult(
            int receivedCount,
            int insertedCount,
            int duplicateCount,
            int skippedCount,
            String message) {
    }
}
