// Study 2.1 ブラウザ記録 - ポップアップ
// 接続状態・送信待ち件数・最終送信時刻を表示し、設定画面と単語帳への導線を提供する。
(function() {
  const STORAGE_KEY_API_BASE_URL = "study21.apiBaseUrl";
  const STORAGE_KEY_ACCESS_CODE = "study21.accessCode";
  const STORAGE_KEY_DEVICE_ID = "study21.deviceId";
  const STORAGE_KEY_RECORDING_ENABLED = "study21.recordingEnabled";
  const STORAGE_KEY_QUEUE = "study21.queue";
  const STORAGE_KEY_LAST_SENT_AT = "study21.lastSentAt";
  const STORAGE_KEY_CONNECTION_STATUS = "study21.connectionStatus";

  const DEFAULT_API_BASE_URL = "http://192.168.0.100:8082";

  const STATE_LABELS = {
    unset: "未設定",
    ok: "接続OK",
    error: "エラー"
  };

  function trim(value) {
    return String(value == null ? "" : value).trim();
  }

  function formatDateTime(isoText) {
    const text = trim(isoText);
    if (!text) return "—";
    const date = new Date(text);
    if (isNaN(date.getTime())) return text;
    return date.toLocaleString("ja-JP", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit"
    });
  }

  // バックグラウンドから最新の状態を取得する。取得できない場合は storage から直接読む。
  async function fetchStatus() {
    try {
      const response = await chrome.runtime.sendMessage({ type: "study21-get-status" });
      if (response && response.success && response.status) {
        return response.status;
      }
    } catch (error) {
      console.warn("failed to get status from background", error);
    }
    return readStatusFromStorage();
  }

  async function readStatusFromStorage() {
    const data = await chrome.storage.local.get([
      STORAGE_KEY_API_BASE_URL,
      STORAGE_KEY_ACCESS_CODE,
      STORAGE_KEY_DEVICE_ID,
      STORAGE_KEY_RECORDING_ENABLED,
      STORAGE_KEY_QUEUE,
      STORAGE_KEY_LAST_SENT_AT,
      STORAGE_KEY_CONNECTION_STATUS
    ]);
    const queue = Array.isArray(data[STORAGE_KEY_QUEUE]) ? data[STORAGE_KEY_QUEUE] : [];
    const stored = data[STORAGE_KEY_CONNECTION_STATUS];
    const accessCodeSet = !!trim(data[STORAGE_KEY_ACCESS_CODE] || "");
    return {
      apiBaseUrl: trim(data[STORAGE_KEY_API_BASE_URL] || DEFAULT_API_BASE_URL),
      accessCodeSet: accessCodeSet,
      recordingEnabled: data[STORAGE_KEY_RECORDING_ENABLED] !== false,
      deviceId: trim(data[STORAGE_KEY_DEVICE_ID] || ""),
      pendingCount: queue.length,
      lastSentAt: trim(data[STORAGE_KEY_LAST_SENT_AT] || ""),
      connectionState: accessCodeSet ? trim((stored && stored.state) || "unset") : "unset",
      connectionMessage: trim((stored && stored.message) || "")
    };
  }

  function render(status) {
    const state = status.accessCodeSet ? (status.connectionState || "unset") : "unset";
    const badge = document.getElementById("connectionState");
    badge.textContent = STATE_LABELS[state] || STATE_LABELS.unset;
    badge.className = "badge badge-" + (STATE_LABELS[state] ? state : "unset");

    const message = document.getElementById("connectionMessage");
    if (!status.accessCodeSet) {
      message.textContent = "接続コード未設定です。設定画面で入力してください（履歴は端末内に保存されます）。";
    } else if (state === "error") {
      message.textContent = status.connectionMessage || "送信に失敗しました。次の送信で再試行します。";
    } else if (state === "ok") {
      message.textContent = status.connectionMessage || "正常に送信しています。";
    } else {
      message.textContent = "まだ送信していません。";
    }

    document.getElementById("pendingCount").textContent = status.pendingCount + " 件";
    document.getElementById("lastSentAt").textContent = formatDateTime(status.lastSentAt);
    document.getElementById("recordingState").textContent = status.recordingEnabled ? "ON" : "OFF";
    document.getElementById("deviceId").textContent = status.deviceId || "—";
    document.getElementById("apiBaseUrl").textContent = status.apiBaseUrl || DEFAULT_API_BASE_URL;
  }

  function renderError(error) {
    const badge = document.getElementById("connectionState");
    badge.textContent = STATE_LABELS.error;
    badge.className = "badge badge-error";
    document.getElementById("connectionMessage").textContent =
      "状態の取得に失敗しました: " + (error && error.message ? error.message : error);
  }

  document.getElementById("openSettingsBtn").addEventListener("click", () => {
    chrome.runtime.openOptionsPage();
    window.close();
  });

  document.getElementById("openWordbookBtn").addEventListener("click", () => {
    chrome.tabs.create({ url: chrome.runtime.getURL("wordbook.html") });
    window.close();
  });

  fetchStatus()
    .then(render)
    .catch(renderError);
})();
