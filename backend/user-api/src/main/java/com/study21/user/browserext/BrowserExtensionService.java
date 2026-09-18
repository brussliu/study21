package com.study21.user.browserext;

/**
 * ブラウザ拡張（`extension/`）との連携。
 *
 * <p>画面向け（接続コードの発行・端末一覧）と、拡張向け（受信 API）の両方を扱う。
 * 拡張向け API は接続コードで持ち主を特定するので、リクエストの userId は使わない。</p>
 */
public interface BrowserExtensionService {

    /** 接続コードと端末一覧（未発行なら issued=false）。 */
    BrowserExtensionModels.RegistrationView registration(long accountId);

    /** 接続コードを発行する（発行済みならそのまま返す）。 */
    BrowserExtensionModels.RegistrationView issueToken(long accountId);

    /** 接続コードを再発行する（以前のコードは即時に無効）。 */
    BrowserExtensionModels.RegistrationView reissueToken(long accountId);

    /** 拡張の register（端末の登録・更新）。 */
    BrowserExtensionModels.RegisterResult register(String token, BrowserExtensionModels.RegisterRequest request);

    /** 拡張の heartbeat（生存通知）。 */
    BrowserExtensionModels.HeartbeatResult heartbeat(String token, BrowserExtensionModels.HeartbeatRequest request);

    /** 拡張の events（閲覧イベントのバッチ登録）。 */
    BrowserExtensionModels.BatchSaveResult saveEvents(String token, BrowserExtensionModels.EventBatchRequest request);
}
