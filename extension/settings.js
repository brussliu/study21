// Study 2.1 ブラウザ記録 - 設定画面
// API の URL・接続コード・端末名・記録の ON/OFF を保存し、接続テストを行う。
(function() {
  const DEFAULT_API_BASE_URL = "http://192.168.0.100:8082";

  const STORAGE_KEY_API_BASE_URL = "study21.apiBaseUrl";
  const STORAGE_KEY_ACCESS_CODE = "study21.accessCode";
  const STORAGE_KEY_DEVICE_NAME = "study21.deviceName";
  const STORAGE_KEY_DEVICE_ID = "study21.deviceId";
  const STORAGE_KEY_RECORDING_ENABLED = "study21.recordingEnabled";
  const STORAGE_KEY_QUEUE = "study21.queue";
  const STORAGE_KEY_LAST_SENT_AT = "study21.lastSentAt";

  function trim(value) {
    return String(value == null ? "" : value).trim();
  }

  function normalizeBaseUrl(url) {
    const text = trim(url || DEFAULT_API_BASE_URL);
    return text.replace(/\/+$/, "");
  }

  // 端末名の既定値（例: Win32 / MacIntel / Linux x86_64）
  function defaultDeviceName() {
    const platform = trim(navigator.platform || "");
    if (platform) return platform;
    return "Chrome";
  }

  function formatDateTime(isoText) {
    const text = trim(isoText);
    if (!text) return "—";
    const date = new Date(text);
    if (isNaN(date.getTime())) return text;
    return date.toLocaleString("ja-JP");
  }

  function setStatus(message, kind) {
    const status = document.getElementById("status");
    if (!status) return;
    status.textContent = message || "";
    status.classList.toggle("error", kind === "error");
    status.classList.toggle("info", kind === "info");
  }

  function setBusy(busy) {
    ["saveBtn", "testBtn"].forEach((id) => {
      const button = document.getElementById(id);
      if (button) button.disabled = !!busy;
    });
  }

  function readForm() {
    return {
      apiBaseUrl: normalizeBaseUrl(document.getElementById("apiBaseUrl").value),
      accessCode: trim(document.getElementById("accessCode").value),
      deviceName: trim(document.getElementById("deviceName").value) || defaultDeviceName(),
      recordingEnabled: !!document.getElementById("recordingEnabled").checked
    };
  }

  async function loadSettings() {
    const data = await chrome.storage.local.get([
      STORAGE_KEY_API_BASE_URL,
      STORAGE_KEY_ACCESS_CODE,
      STORAGE_KEY_DEVICE_NAME,
      STORAGE_KEY_DEVICE_ID,
      STORAGE_KEY_RECORDING_ENABLED,
      STORAGE_KEY_QUEUE,
      STORAGE_KEY_LAST_SENT_AT
    ]);
    document.getElementById("apiBaseUrl").value =
      normalizeBaseUrl(data[STORAGE_KEY_API_BASE_URL] || DEFAULT_API_BASE_URL);
    document.getElementById("accessCode").value = trim(data[STORAGE_KEY_ACCESS_CODE] || "");
    document.getElementById("deviceName").value =
      trim(data[STORAGE_KEY_DEVICE_NAME] || "") || defaultDeviceName();
    document.getElementById("recordingEnabled").checked = data[STORAGE_KEY_RECORDING_ENABLED] !== false;

    const queue = Array.isArray(data[STORAGE_KEY_QUEUE]) ? data[STORAGE_KEY_QUEUE] : [];
    document.getElementById("deviceId").textContent = trim(data[STORAGE_KEY_DEVICE_ID] || "") || "—";
    document.getElementById("extensionVersion").textContent = chrome.runtime.getManifest().version;
    document.getElementById("pendingCount").textContent = queue.length + " 件";
    document.getElementById("lastSentAt").textContent = formatDateTime(data[STORAGE_KEY_LAST_SENT_AT]);
  }

  async function refreshInfo() {
    const data = await chrome.storage.local.get([
      STORAGE_KEY_DEVICE_ID,
      STORAGE_KEY_QUEUE,
      STORAGE_KEY_LAST_SENT_AT
    ]);
    const queue = Array.isArray(data[STORAGE_KEY_QUEUE]) ? data[STORAGE_KEY_QUEUE] : [];
    document.getElementById("deviceId").textContent = trim(data[STORAGE_KEY_DEVICE_ID] || "") || "—";
    document.getElementById("pendingCount").textContent = queue.length + " 件";
    document.getElementById("lastSentAt").textContent = formatDateTime(data[STORAGE_KEY_LAST_SENT_AT]);
  }

  async function saveSettings() {
    const form = readForm();
    await chrome.storage.local.set({
      [STORAGE_KEY_API_BASE_URL]: form.apiBaseUrl,
      [STORAGE_KEY_ACCESS_CODE]: form.accessCode,
      [STORAGE_KEY_DEVICE_NAME]: form.deviceName,
      [STORAGE_KEY_RECORDING_ENABLED]: form.recordingEnabled
    });

    // バックグラウンドに設定変更を伝える（端末登録・ハートビート・キューの送信を試みる）
    let notice = "保存しました。";
    try {
      const response = await chrome.runtime.sendMessage({ type: "study21-settings-updated" });
      if (response && response.success === false) {
        notice = "保存しました（送信の確認に失敗: " + (response.error || "不明なエラー") + "）";
      }
    } catch (error) {
      notice = "保存しました（バックグラウンドへの通知に失敗しました）。";
    }
    if (!form.accessCode) {
      notice += "\n接続コードが未設定のため、履歴は送信されず端末内に保存されます。";
    }
    await refreshInfo();
    setStatus(notice, form.accessCode ? "ok" : "info");
  }

  async function testConnection() {
    const form = readForm();
    if (!form.accessCode) {
      setStatus("接続テストには接続コードが必要です。", "error");
      return;
    }
    const response = await chrome.runtime.sendMessage({
      type: "study21-test-connection",
      apiBaseUrl: form.apiBaseUrl,
      accessCode: form.accessCode,
      deviceName: form.deviceName
    });
    if (!response || !response.success || !response.result) {
      setStatus("接続テストに失敗しました: " + ((response && response.error) || "応答がありません"), "error");
      return;
    }
    const result = response.result;
    if (result.ok) {
      setStatus("接続テスト成功（HTTP " + result.status + "）: " + (result.message || "OK"), "ok");
      await refreshInfo();
      return;
    }
    setStatus("接続テスト失敗（HTTP " + (result.status || "-") + "）: " + (result.message || "エラー"), "error");
  }

  document.getElementById("saveBtn").addEventListener("click", () => {
    setBusy(true);
    saveSettings()
      .catch((error) => setStatus("保存に失敗しました: " + (error && error.message ? error.message : error), "error"))
      .finally(() => setBusy(false));
  });

  document.getElementById("testBtn").addEventListener("click", () => {
    setBusy(true);
    setStatus("接続を確認しています...", "info");
    testConnection()
      .catch((error) => setStatus("接続テストに失敗しました: " + (error && error.message ? error.message : error), "error"))
      .finally(() => setBusy(false));
  });

  document.getElementById("openWordbookBtn").addEventListener("click", () => {
    chrome.tabs.create({ url: chrome.runtime.getURL("wordbook.html") });
  });

  loadSettings().catch((error) => {
    setStatus("初期化に失敗しました: " + (error && error.message ? error.message : error), "error");
  });
})();
