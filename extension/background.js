// Study 2.1 ブラウザ記録 - バックグラウンドサービスワーカー
// 2.0 拡張の閲覧イベント収集を、2.1 のユーザー API 向けに移植したもの。
// 収集したイベントは chrome.storage.local のキューに蓄積し、100 件または 1 分ごとに送信する。

const DEFAULT_API_BASE_URL = "http://192.168.0.100:8082";

const STORAGE_KEY_API_BASE_URL = "study21.apiBaseUrl";
const STORAGE_KEY_ACCESS_CODE = "study21.accessCode";
const STORAGE_KEY_DEVICE_NAME = "study21.deviceName";
const STORAGE_KEY_DEVICE_ID = "study21.deviceId";
const STORAGE_KEY_RECORDING_ENABLED = "study21.recordingEnabled";
const STORAGE_KEY_QUEUE = "study21.queue";
const STORAGE_KEY_LAST_SENT_AT = "study21.lastSentAt";
const STORAGE_KEY_CONNECTION_STATUS = "study21.connectionStatus";

const SESSION_KEY_SESSION_ID = "study21.sessionId";

const FLUSH_ALARM_NAME = "study21-queue-flush";
const HEARTBEAT_ALARM_NAME = "study21-heartbeat";

const FLUSH_PERIOD_MINUTES = 1;
const HEARTBEAT_PERIOD_MINUTES = 5;

const QUEUE_LIMIT = 2000;
const BATCH_LIMIT = 100;
const MAX_BATCHES_PER_FLUSH = 20;
const DEDUPE_WINDOW_MS = 4000;
const DEDUPE_KEEP_MS = 10 * 60 * 1000;

const PROFILE_ID = "Default";
const BROWSER_TYPE = "Chrome";
const EXTENSION_TOKEN_HEADER = "X-Study21-Extension-Token";

const REGISTER_PATH = "/api/user/browser-extension/register";
const HEARTBEAT_PATH = "/api/user/browser-extension/heartbeat";
const EVENTS_PATH = "/api/user/browser-extension/events";

const MENU_ID = "study21-wordbook-menu";
const MENU_TITLE = "Study 2.1 単語帳に追加";

// アクティブタブの滞在秒数を計算するためのメモリ内マップ（tabId -> アクティブになった時刻）
const activeSinceMap = new Map();
// 4 秒以内の同一 (eventType, url, tabId) を除外するためのメモリ内マップ
const lastEventMap = new Map();

let sessionIdCache = "";
let flushInProgress = false;
// キューへの読み書きは直列化する（同時に走ったイベントで更新が失われるのを防ぐ）
let queueLock = Promise.resolve();

function withQueueLock(task) {
  const run = queueLock.then(task, task);
  queueLock = run.then(() => undefined, () => undefined);
  return run;
}

function trim(value) {
  return String(value == null ? "" : value).trim();
}

function defaultString(value, fallback) {
  const text = trim(value);
  return text || fallback || "";
}

function pad2(value) {
  return String(value).padStart(2, "0");
}

// 日時は必ずこの関数を通して ISO 8601（タイムゾーン付き）にする。
function isoFrom(date) {
  const target = date instanceof Date ? date : new Date(date);
  if (isNaN(target.getTime())) {
    return isoNow();
  }
  const offsetMinutes = -target.getTimezoneOffset();
  const sign = offsetMinutes < 0 ? "-" : "+";
  const absolute = Math.abs(offsetMinutes);
  return target.getFullYear() +
    "-" + pad2(target.getMonth() + 1) +
    "-" + pad2(target.getDate()) +
    "T" + pad2(target.getHours()) +
    ":" + pad2(target.getMinutes()) +
    ":" + pad2(target.getSeconds()) +
    sign + pad2(Math.floor(absolute / 60)) + ":" + pad2(absolute % 60);
}

function isoNow() {
  return isoFrom(new Date());
}

function normalizeBaseUrl(url) {
  return defaultString(url, DEFAULT_API_BASE_URL).replace(/\/+$/, "");
}

function isSupportedUrl(url) {
  const text = trim(url);
  if (!text) return false;
  return /^https?:\/\//i.test(text);
}

function buildDomain(url) {
  try {
    return new URL(url).hostname || "";
  } catch (error) {
    return "";
  }
}

function readExtensionVersion() {
  try {
    return chrome.runtime.getManifest().version || "2.1.0";
  } catch (error) {
    return "2.1.0";
  }
}

function detectBrowserVersion() {
  const ua = defaultString(navigator.userAgent, "");
  const match = ua.match(/Chrome\/([0-9.]+)/i);
  if (match && match[1]) {
    return match[1];
  }
  return ua.slice(0, 96);
}

function detectOsType() {
  const ua = defaultString(navigator.userAgent, "");
  const platform = defaultString(navigator.platform, "");
  if (/Windows/i.test(ua) || /^Win/i.test(platform)) return "Windows";
  if (/CrOS/i.test(ua)) return "ChromeOS";
  if (/Android/i.test(ua)) return "Android";
  if (/iPhone|iPad|iPod/i.test(ua)) return "iOS";
  if (/Mac OS X|Macintosh/i.test(ua) || /^Mac/i.test(platform)) return "macOS";
  if (/Linux/i.test(ua) || /Linux/i.test(platform)) return "Linux";
  return defaultString(platform, "Unknown");
}

// 端末名が未設定のときに使う既定値（例: "Win32" / "MacIntel"）
function defaultDeviceName() {
  return defaultString(navigator.platform, defaultString(navigator.userAgent, BROWSER_TYPE)).slice(0, 64);
}

async function getSettings() {
  const data = await chrome.storage.local.get([
    STORAGE_KEY_API_BASE_URL,
    STORAGE_KEY_ACCESS_CODE,
    STORAGE_KEY_DEVICE_NAME,
    STORAGE_KEY_RECORDING_ENABLED
  ]);
  return {
    apiBaseUrl: normalizeBaseUrl(data[STORAGE_KEY_API_BASE_URL] || DEFAULT_API_BASE_URL),
    accessCode: trim(data[STORAGE_KEY_ACCESS_CODE] || ""),
    deviceName: defaultString(data[STORAGE_KEY_DEVICE_NAME], defaultDeviceName()),
    recordingEnabled: data[STORAGE_KEY_RECORDING_ENABLED] !== false
  };
}

// 端末識別子は初回のみ生成し、以後は再利用する。
async function ensureDeviceId() {
  const data = await chrome.storage.local.get([STORAGE_KEY_DEVICE_ID]);
  let deviceId = trim(data[STORAGE_KEY_DEVICE_ID] || "");
  if (deviceId) {
    return deviceId;
  }
  deviceId = "chrome-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10);
  await chrome.storage.local.set({ [STORAGE_KEY_DEVICE_ID]: deviceId });
  return deviceId;
}

// ブラウザ起動後は変わらない閲覧セッション ID（service worker の再起動では保持される）
async function getSessionId() {
  if (sessionIdCache) {
    return sessionIdCache;
  }
  try {
    const data = await chrome.storage.session.get([SESSION_KEY_SESSION_ID]);
    const stored = trim(data[SESSION_KEY_SESSION_ID] || "");
    if (stored) {
      sessionIdCache = stored;
      return stored;
    }
  } catch (error) {
    console.warn("session storage is not available", error);
  }
  const generated = "sess-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10);
  sessionIdCache = generated;
  try {
    await chrome.storage.session.set({ [SESSION_KEY_SESSION_ID]: generated });
  } catch (error) {
    console.warn("failed to persist session id", error);
  }
  return generated;
}

// イベントごとに一度だけ発行する識別子。キューと一緒に保存するので再送時も変わらない。
function createClientEventId() {
  try {
    return crypto.randomUUID();
  } catch (error) {
    return "evt-" + Date.now() + "-" + Math.random().toString(36).slice(2, 12);
  }
}

async function getQueue() {
  const data = await chrome.storage.local.get([STORAGE_KEY_QUEUE]);
  return Array.isArray(data[STORAGE_KEY_QUEUE]) ? data[STORAGE_KEY_QUEUE] : [];
}

async function setQueue(queue) {
  await chrome.storage.local.set({ [STORAGE_KEY_QUEUE]: Array.isArray(queue) ? queue : [] });
}

// キューに追加する。上限を超えた場合は最も古いものから破棄する。
async function enqueueEvent(event) {
  return withQueueLock(async () => {
    const queue = await getQueue();
    queue.push(event);
    const overflow = queue.length - QUEUE_LIMIT;
    if (overflow > 0) {
      queue.splice(0, overflow);
      console.warn("queue limit reached, " + overflow + " event(s) dropped");
    }
    await setQueue(queue);
    return queue.length;
  });
}

function buildDedupeKey(eventType, url, tabId) {
  return defaultString(eventType, "") + "|" + (tabId == null ? "na" : String(tabId)) + "|" + defaultString(url, "");
}

// 同一 (eventType, url, tabId) が 4 秒以内に再来した場合は重複とみなす。
function isDuplicateEvent(eventType, url, tabId) {
  const key = buildDedupeKey(eventType, url, tabId);
  const now = Date.now();
  const previousAt = Number(lastEventMap.get(key) || 0);
  lastEventMap.set(key, now);
  lastEventMap.forEach((value, currentKey) => {
    if (now - Number(value || 0) > DEDUPE_KEEP_MS) {
      lastEventMap.delete(currentKey);
    }
  });
  return previousAt > 0 && (now - previousAt) < DEDUPE_WINDOW_MS;
}

// そのタブが最後にアクティブになってから現在までの秒数。
// アクティブでないタブ、またはアクティブになった時刻が不明な場合は 0 を返す。
function computeStaySeconds(tabId, isActive) {
  if (typeof tabId !== "number" || !isActive) {
    return 0;
  }
  const now = Date.now();
  const activatedAt = Number(activeSinceMap.get(tabId) || 0);
  if (!activatedAt) {
    activeSinceMap.set(tabId, now);
    return 0;
  }
  return Math.max(0, Math.floor((now - activatedAt) / 1000));
}

// history.onVisited が履歴同期由来かどうか。判別できないため常に false とする。
function isFromHistorySync(historyItem) {
  if (!historyItem) return false;
  return false;
}

function buildCommonHeaders(accessCode) {
  return {
    "Content-Type": "application/json",
    [EXTENSION_TOKEN_HEADER]: accessCode
  };
}

async function readJsonBody(response) {
  try {
    return await response.json();
  } catch (error) {
    return null;
  }
}

// 2.1 のユーザー API は共通レスポンス形式
// （{"success":..., "code":..., "message":..., "data":{...}}）で返す。
// フラットな形式（{"insertedCount":...}）で返す実装にも対応できるよう、
// data があればそれを本文として扱う。
function unwrapBody(body) {
  if (body && typeof body === "object" && body.data && typeof body.data === "object") {
    return body.data;
  }
  return body && typeof body === "object" ? body : {};
}

function bodyMessage(body, fallback) {
  if (body && typeof body === "object" && body.message) {
    return String(body.message);
  }
  const payload = unwrapBody(body);
  if (payload && payload.message) {
    return String(payload.message);
  }
  return fallback || "";
}

async function setConnectionStatus(state, message) {
  const status = {
    state: state,
    message: defaultString(message, ""),
    at: isoNow()
  };
  await chrome.storage.local.set({ [STORAGE_KEY_CONNECTION_STATUS]: status });
  return status;
}

// ---- イベント収集 ----

async function captureTabEvent(eventType, tab, extra) {
  try {
    const settings = await getSettings();
    if (!settings.recordingEnabled) {
      return;
    }
    if (!tab || !isSupportedUrl(tab.url)) {
      return;
    }
    const tabId = typeof tab.id === "number" ? tab.id : null;
    if (isDuplicateEvent(eventType, tab.url, tabId)) {
      return;
    }
    const isActive = tab.active === true;
    const event = {
      clientEventId: createClientEventId(),
      eventType: eventType,
      url: tab.url,
      domain: buildDomain(tab.url),
      pageTitle: defaultString(tab.title, ""),
      referrerUrl: "",
      faviconUrl: defaultString(tab.favIconUrl, ""),
      transitionType: extra && extra.transitionType ? String(extra.transitionType) : "",
      tabId: tabId,
      windowId: typeof tab.windowId === "number" ? tab.windowId : null,
      sessionId: await getSessionId(),
      active: isActive,
      fromHistorySync: false,
      visitedAt: isoNow(),
      staySeconds: computeStaySeconds(tabId, isActive),
      visitCount: 1
    };
    await enqueueEvent(event);
    await flushIfBatchFull();
  } catch (error) {
    console.warn("captureTabEvent failed", error);
  }
}

async function captureNavigationEvent(eventType, details) {
  try {
    const tab = typeof details.tabId === "number" ? await chrome.tabs.get(details.tabId) : null;
    await captureTabEvent(eventType, tab, {
      transitionType: details.transitionType || ""
    });
  } catch (error) {
    console.warn("captureNavigationEvent failed", error);
  }
}

async function captureHistoryVisit(historyItem) {
  try {
    const settings = await getSettings();
    if (!settings.recordingEnabled) {
      return;
    }
    if (!historyItem || !isSupportedUrl(historyItem.url)) {
      return;
    }
    if (isDuplicateEvent("HISTORY_VISITED", historyItem.url, null)) {
      return;
    }
    const visitedAt = historyItem.lastVisitTime ? isoFrom(historyItem.lastVisitTime) : isoNow();
    const event = {
      clientEventId: createClientEventId(),
      eventType: "HISTORY_VISITED",
      url: historyItem.url,
      domain: buildDomain(historyItem.url),
      pageTitle: defaultString(historyItem.title, ""),
      referrerUrl: "",
      faviconUrl: "",
      transitionType: "",
      tabId: null,
      windowId: null,
      sessionId: await getSessionId(),
      active: false,
      fromHistorySync: isFromHistorySync(historyItem),
      visitedAt: visitedAt,
      staySeconds: 0,
      visitCount: Number(historyItem.visitCount || 1)
    };
    await enqueueEvent(event);
    await flushIfBatchFull();
  } catch (error) {
    console.warn("captureHistoryVisit failed", error);
  }
}

async function flushIfBatchFull() {
  const queue = await getQueue();
  if (queue.length >= BATCH_LIMIT) {
    await flushQueue("batch_limit");
  }
}

// ---- 送信 ----

// 送信済みのイベントだけをキューから取り除く（送信中に増えたイベントは残す）。
// clientEventId を持たない古い形式のイベントは、先頭から数えて送信した件数分だけ取り除く。
function removeSentEvents(queue, batch) {
  const sentIds = new Set();
  let idlessCount = 0;
  batch.forEach((item) => {
    const id = item && item.clientEventId ? String(item.clientEventId) : "";
    if (id) {
      sentIds.add(id);
    } else {
      idlessCount += 1;
    }
  });
  const remaining = [];
  let idlessToDrop = idlessCount;
  queue.forEach((item) => {
    const id = item && item.clientEventId ? String(item.clientEventId) : "";
    if (id && sentIds.has(id)) {
      return;
    }
    if (!id && idlessToDrop > 0) {
      idlessToDrop -= 1;
      return;
    }
    remaining.push(item);
  });
  return remaining;
}

// キューを 1 バッチ送信する。失敗した場合はキューを保持し、次回 (1 分後のアラームなど) に再送する。
async function flushQueue(trigger) {
  if (flushInProgress) {
    return { sent: false, reason: "in_progress" };
  }
  const settings = await getSettings();
  if (!settings.recordingEnabled) {
    return { sent: false, reason: "disabled" };
  }
  if (!settings.accessCode) {
    // 接続コード未設定のときは送信せず、キューに貯めるだけにする。
    await setConnectionStatus("unset", "接続コード未設定");
    return { sent: false, reason: "no_access_code" };
  }
  const queue = await getQueue();
  if (!queue.length) {
    return { sent: false, reason: "empty" };
  }

  flushInProgress = true;
  const deviceId = await ensureDeviceId();
  let sentCount = 0;
  let insertedTotal = 0;
  let duplicatedTotal = 0;
  let skippedTotal = 0;
  let lastMessage = "";
  let currentQueue = queue;
  try {
    // 大量に溜まっている場合は 1 回の呼び出しで複数バッチを送る（上限あり）。
    for (let round = 0; round < MAX_BATCHES_PER_FLUSH && currentQueue.length > 0; round += 1) {
      const batch = currentQueue.slice(0, BATCH_LIMIT);
      const response = await fetch(settings.apiBaseUrl + EVENTS_PATH, {
        method: "POST",
        headers: buildCommonHeaders(settings.accessCode),
        body: JSON.stringify({
          deviceId: deviceId,
          deviceName: settings.deviceName,
          browserType: BROWSER_TYPE,
          browserVersion: detectBrowserVersion(),
          extensionVersion: readExtensionVersion(),
          profileId: PROFILE_ID,
          sentAt: isoNow(),
          events: batch
        })
      });
      const body = await readJsonBody(response);
      if (!response.ok || (body && body.success === false)) {
        const message = bodyMessage(body, "HTTP " + response.status);
        throw new Error(message);
      }
      // 送信済みの clientEventId だけを取り除く（送信中に増えたイベントは残す）
      const remaining = await withQueueLock(async () => {
        const latest = await getQueue();
        const next = removeSentEvents(latest, batch);
        await setQueue(next);
        return next;
      });
      const payload = unwrapBody(body);
      currentQueue = remaining;
      sentCount += batch.length;
      insertedTotal += Number((payload && payload.insertedCount) || 0);
      duplicatedTotal += Number((payload && payload.duplicateCount) || 0);
      skippedTotal += Number((payload && payload.skippedCount) || 0);
      lastMessage = bodyMessage(body, "送信しました");
      if (batch.length < BATCH_LIMIT) {
        break;
      }
    }
    await chrome.storage.local.set({ [STORAGE_KEY_LAST_SENT_AT]: isoNow() });
    await setConnectionStatus(
      "ok",
      lastMessage + "（登録 " + insertedTotal + " 件 / 重複 " + duplicatedTotal + " 件" +
        (skippedTotal > 0 ? " / スキップ " + skippedTotal + " 件" : "") + "）"
    );
    return {
      sent: true,
      count: sentCount,
      inserted: insertedTotal,
      duplicated: duplicatedTotal,
      skipped: skippedTotal,
      remaining: currentQueue.length,
      trigger: trigger
    };
  } catch (error) {
    const message = error && error.message ? error.message : String(error || "送信に失敗しました");
    console.warn("flushQueue failed", error);
    await setConnectionStatus("error", message);
    return { sent: false, reason: "error", message: message };
  } finally {
    flushInProgress = false;
  }
}

// ---- 端末登録とハートビート ----

function buildDevicePayload(settings, deviceId) {
  return {
    deviceId: deviceId,
    deviceName: settings.deviceName,
    osType: detectOsType(),
    browserType: BROWSER_TYPE,
    browserVersion: detectBrowserVersion(),
    extensionVersion: readExtensionVersion(),
    profileId: PROFILE_ID
  };
}

async function registerDevice(trigger) {
  const settings = await getSettings();
  if (!settings.recordingEnabled) {
    return { ok: false, reason: "disabled" };
  }
  if (!settings.accessCode) {
    await setConnectionStatus("unset", "接続コード未設定");
    return { ok: false, reason: "no_access_code" };
  }
  const deviceId = await ensureDeviceId();
  try {
    const response = await fetch(settings.apiBaseUrl + REGISTER_PATH, {
      method: "POST",
      headers: buildCommonHeaders(settings.accessCode),
      body: JSON.stringify(Object.assign(buildDevicePayload(settings, deviceId), { registeredAt: isoNow() }))
    });
    const body = await readJsonBody(response);
    if (!response.ok || (body && body.success === false)) {
      const message = bodyMessage(body, "HTTP " + response.status);
      if (response.status === 401) {
        await setConnectionStatus("error", "接続コードが正しくありません");
        return { ok: false, reason: "unauthorized", status: 401, message: message };
      }
      throw new Error(message);
    }
    const message = bodyMessage(body, "端末を登録しました");
    await setConnectionStatus("ok", message);
    return { ok: true, status: response.status, message: message, trigger: trigger };
  } catch (error) {
    const message = error && error.message ? error.message : String(error || "端末登録に失敗しました");
    console.warn("registerDevice failed", error);
    await setConnectionStatus("error", message);
    return { ok: false, reason: "error", message: message };
  }
}

async function sendHeartbeat(trigger) {
  const settings = await getSettings();
  if (!settings.recordingEnabled) {
    return { ok: false, reason: "disabled" };
  }
  if (!settings.accessCode) {
    await setConnectionStatus("unset", "接続コード未設定");
    return { ok: false, reason: "no_access_code" };
  }
  const deviceId = await ensureDeviceId();
  try {
    const response = await fetch(settings.apiBaseUrl + HEARTBEAT_PATH, {
      method: "POST",
      headers: buildCommonHeaders(settings.accessCode),
      body: JSON.stringify({
        deviceId: deviceId,
        heartbeatAt: isoNow()
      })
    });
    const body = await readJsonBody(response);
    if (!response.ok || (body && body.success === false)) {
      throw new Error(bodyMessage(body, "HTTP " + response.status));
    }
    await setConnectionStatus("ok", bodyMessage(body, "ハートビートを送信しました"));
    return { ok: true, status: response.status, trigger: trigger };
  } catch (error) {
    const message = error && error.message ? error.message : String(error || "ハートビート送信に失敗しました");
    console.warn("sendHeartbeat failed", error);
    await setConnectionStatus("error", message);
    return { ok: false, reason: "error", message: message };
  }
}

// 設定画面の「接続テスト」。入力中の値で試せるよう、保存前の値も受け取る。
async function testConnection(overrides) {
  const settings = await getSettings();
  const source = overrides && typeof overrides === "object" ? overrides : {};
  const apiBaseUrl = normalizeBaseUrl(source.apiBaseUrl || settings.apiBaseUrl);
  const accessCode = trim(source.accessCode || settings.accessCode);
  const deviceName = defaultString(source.deviceName, settings.deviceName);
  if (!accessCode) {
    return { ok: false, status: 0, message: "接続コードが未入力です。" };
  }
  const deviceId = await ensureDeviceId();
  try {
    const response = await fetch(apiBaseUrl + REGISTER_PATH, {
      method: "POST",
      headers: buildCommonHeaders(accessCode),
      body: JSON.stringify(Object.assign(
        buildDevicePayload({ deviceName: deviceName }, deviceId),
        { registeredAt: isoNow() }
      ))
    });
    const body = await readJsonBody(response);
    if (!response.ok || (body && body.success === false)) {
      if (response.status === 401) {
        await setConnectionStatus("error", "接続コードが正しくありません");
        return { ok: false, status: 401, message: "接続コードが正しくありません（401）" };
      }
      const message = bodyMessage(body, "HTTP " + response.status);
      await setConnectionStatus("error", message);
      return { ok: false, status: response.status, message: message };
    }
    const message = bodyMessage(body, "接続に成功しました");
    await setConnectionStatus("ok", message);
    return { ok: true, status: response.status, message: message };
  } catch (error) {
    const message = error && error.message ? error.message : String(error || "接続に失敗しました");
    console.warn("testConnection failed", error);
    await setConnectionStatus("error", message);
    return { ok: false, status: 0, message: "接続できませんでした: " + message };
  }
}

// ---- 状態取得（ポップアップ用） ----

async function getStatusSnapshot() {
  const settings = await getSettings();
  const data = await chrome.storage.local.get([
    STORAGE_KEY_DEVICE_ID,
    STORAGE_KEY_LAST_SENT_AT,
    STORAGE_KEY_CONNECTION_STATUS
  ]);
  const queue = await getQueue();
  const stored = data[STORAGE_KEY_CONNECTION_STATUS];
  const accessCodeSet = !!settings.accessCode;
  const state = !accessCodeSet
    ? "unset"
    : (stored && stored.state ? String(stored.state) : "unset");
  return {
    apiBaseUrl: settings.apiBaseUrl,
    accessCodeSet: accessCodeSet,
    recordingEnabled: settings.recordingEnabled,
    deviceId: trim(data[STORAGE_KEY_DEVICE_ID] || ""),
    pendingCount: queue.length,
    lastSentAt: trim(data[STORAGE_KEY_LAST_SENT_AT] || ""),
    connectionState: state,
    connectionMessage: stored && stored.message ? String(stored.message) : ""
  };
}

// ---- 単語帳（右クリックメニュー） ----

function createContextMenu() {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({
      id: MENU_ID,
      title: MENU_TITLE,
      contexts: ["selection"]
    });
  });
}

async function openWordbookForm(info, tab) {
  if (!info || info.menuItemId !== MENU_ID) {
    return;
  }
  const selectedText = trim(info.selectionText || "");
  if (!selectedText) {
    return;
  }
  const params = new URLSearchParams();
  params.set("word", selectedText);
  if (tab && tab.url) {
    params.set("url", tab.url);
  }
  await chrome.tabs.create({ url: chrome.runtime.getURL("wordbook.html?" + params.toString()) });
}

// ---- アラームと起動処理 ----

function scheduleAlarms() {
  chrome.alarms.create(FLUSH_ALARM_NAME, { periodInMinutes: FLUSH_PERIOD_MINUTES });
  chrome.alarms.create(HEARTBEAT_ALARM_NAME, { periodInMinutes: HEARTBEAT_PERIOD_MINUTES });
}

async function ensureAlarms() {
  const flushAlarm = await chrome.alarms.get(FLUSH_ALARM_NAME).catch(() => null);
  const heartbeatAlarm = await chrome.alarms.get(HEARTBEAT_ALARM_NAME).catch(() => null);
  if (!flushAlarm || !heartbeatAlarm) {
    scheduleAlarms();
  }
}

chrome.runtime.onInstalled.addListener(() => {
  createContextMenu();
  scheduleAlarms();
  void ensureDeviceId().catch(() => {});
  void (async () => {
    await registerDevice("onInstalled");
    await flushQueue("onInstalled");
  })();
});

chrome.runtime.onStartup.addListener(() => {
  createContextMenu();
  scheduleAlarms();
  void (async () => {
    await registerDevice("onStartup");
    await flushQueue("onStartup");
  })();
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  void openWordbookForm(info, tab);
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (!alarm) return;
  if (alarm.name === FLUSH_ALARM_NAME) {
    void flushQueue("alarm");
    return;
  }
  if (alarm.name === HEARTBEAT_ALARM_NAME) {
    void sendHeartbeat("alarm");
  }
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (!changeInfo || !changeInfo.url) return;
  // changeInfo.url を今回の遷移先 URL として扱う
  const base = tab && typeof tab === "object" ? Object.assign({}, tab) : {};
  base.url = changeInfo.url;
  if (typeof base.id !== "number") {
    base.id = tabId;
  }
  void captureTabEvent("TAB_UPDATED", base);
});

chrome.tabs.onActivated.addListener((activeInfo) => {
  if (!activeInfo || typeof activeInfo.tabId !== "number") return;
  // アクティブになった時刻を記録し、この時点の滞在秒数は 0 とする。
  activeSinceMap.set(activeInfo.tabId, Date.now());
  void chrome.tabs.get(activeInfo.tabId).then((tab) => {
    void captureTabEvent("TAB_ACTIVATED", tab);
  }).catch(() => {});
});

chrome.tabs.onRemoved.addListener((tabId) => {
  activeSinceMap.delete(tabId);
});

chrome.webNavigation.onCommitted.addListener((details) => {
  if (!details || details.frameId !== 0) return;
  void captureNavigationEvent("NAV_COMMITTED", details);
});

chrome.webNavigation.onHistoryStateUpdated.addListener((details) => {
  if (!details || details.frameId !== 0) return;
  void captureNavigationEvent("NAV_HISTORY_UPDATED", details);
});

chrome.history.onVisited.addListener((historyItem) => {
  if (!historyItem || !isSupportedUrl(historyItem.url)) return;
  void captureHistoryVisit(historyItem);
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!message || !message.type) return false;

  if (message.type === "study21-settings-updated") {
    void (async () => {
      try {
        await ensureDeviceId();
        await registerDevice("settings_saved");
        await sendHeartbeat("settings_saved");
        await flushQueue("settings_saved");
        sendResponse({ success: true, status: await getStatusSnapshot() });
      } catch (error) {
        sendResponse({
          success: false,
          error: error && error.message ? error.message : String(error || "設定の反映に失敗しました")
        });
      }
    })();
    return true;
  }

  if (message.type === "study21-get-status") {
    void (async () => {
      try {
        sendResponse({ success: true, status: await getStatusSnapshot() });
      } catch (error) {
        sendResponse({
          success: false,
          error: error && error.message ? error.message : String(error || "状態の取得に失敗しました")
        });
      }
    })();
    return true;
  }

  if (message.type === "study21-test-connection") {
    void (async () => {
      const result = await testConnection({
        apiBaseUrl: message.apiBaseUrl,
        accessCode: message.accessCode,
        deviceName: message.deviceName
      });
      sendResponse({ success: true, result: result, status: await getStatusSnapshot() });
    })();
    return true;
  }

  return false;
});

void ensureAlarms();
