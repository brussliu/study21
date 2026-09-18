// Study 2.1 単語帳（ローカル保存）
// chrome.storage.local の "study21.words" に保存する。サーバー API は呼び出さない。
(function() {
  const STORAGE_KEY_WORDS = "study21.words";

  let words = [];

  function trim(value) {
    return String(value == null ? "" : value).trim();
  }

  function pad2(value) {
    return String(value).padStart(2, "0");
  }

  function isoNow() {
    return new Date().toISOString();
  }

  function formatDateTime(isoText) {
    const text = trim(isoText);
    if (!text) return "";
    const date = new Date(text);
    if (isNaN(date.getTime())) return text;
    return date.toLocaleString("ja-JP", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    });
  }

  function createId() {
    try {
      return crypto.randomUUID();
    } catch (error) {
      return "word-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10);
    }
  }

  function setStatus(message, kind) {
    const status = document.getElementById("status");
    if (!status) return;
    status.textContent = message || "";
    status.classList.toggle("error", kind === "error");
    status.classList.toggle("info", kind === "info");
  }

  async function loadWords() {
    const data = await chrome.storage.local.get([STORAGE_KEY_WORDS]);
    const stored = data[STORAGE_KEY_WORDS];
    words = Array.isArray(stored) ? stored.filter((item) => item && typeof item === "object") : [];
    sortWords();
  }

  async function saveWords() {
    await chrome.storage.local.set({ [STORAGE_KEY_WORDS]: words });
  }

  function sortWords() {
    words.sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
  }

  function getFilteredWords() {
    const keyword = trim(document.getElementById("searchInput").value).toLowerCase();
    if (!keyword) return words;
    return words.filter((item) => {
      return [item.word, item.meaning, item.memo, item.sourceUrl]
        .map((value) => trim(value).toLowerCase())
        .some((value) => value.indexOf(keyword) >= 0);
    });
  }

  function buildTextCell(text, className) {
    const cell = document.createElement("td");
    if (className) cell.className = className;
    cell.textContent = trim(text);
    return cell;
  }

  function buildSourceCell(sourceUrl) {
    const cell = document.createElement("td");
    cell.className = "source-cell";
    const url = trim(sourceUrl);
    if (!url) {
      cell.textContent = "";
      return cell;
    }
    const link = document.createElement("a");
    link.href = url;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = url;
    cell.appendChild(link);
    return cell;
  }

  function buildActionCell(id) {
    const cell = document.createElement("td");
    cell.className = "action-cell";
    const button = document.createElement("button");
    button.type = "button";
    button.className = "link";
    button.textContent = "削除";
    button.addEventListener("click", () => {
      void deleteWord(id);
    });
    cell.appendChild(button);
    return cell;
  }

  function render() {
    const filtered = getFilteredWords();
    const body = document.getElementById("wordTableBody");
    body.textContent = "";

    filtered.forEach((item) => {
      const row = document.createElement("tr");
      row.appendChild(buildTextCell(item.word, "word-cell"));
      row.appendChild(buildTextCell(item.meaning));
      row.appendChild(buildTextCell(item.memo));
      row.appendChild(buildSourceCell(item.sourceUrl));
      row.appendChild(buildTextCell(formatDateTime(item.createdAt), "created-cell"));
      row.appendChild(buildActionCell(item.id));
      body.appendChild(row);
    });

    document.getElementById("totalCount").textContent = words.length + " 件";
    const filteredCount = document.getElementById("filteredCount");
    filteredCount.textContent = filtered.length === words.length ? "" : "表示中 " + filtered.length + " 件";
    document.getElementById("emptyMessage").textContent = words.length
      ? "条件に一致する単語はありません。"
      : "登録された単語はありません。";
    document.getElementById("emptyMessage").style.display = filtered.length ? "none" : "block";
  }

  async function addWord() {
    const wordInput = document.getElementById("wordInput");
    const meaningInput = document.getElementById("meaningInput");
    const memoInput = document.getElementById("memoInput");
    const sourceUrlInput = document.getElementById("sourceUrlInput");

    const word = trim(wordInput.value);
    if (!word) {
      setStatus("英単語を入力してください。", "error");
      wordInput.focus();
      return;
    }
    words.push({
      id: createId(),
      word: word,
      meaning: trim(meaningInput.value),
      memo: trim(memoInput.value),
      sourceUrl: trim(sourceUrlInput.value),
      createdAt: isoNow()
    });
    sortWords();
    await saveWords();
    render();

    wordInput.value = "";
    meaningInput.value = "";
    memoInput.value = "";
    // 同じページから続けて登録できるよう、登録元 URL は残す。
    setStatus("「" + word + "」を追加しました。", "ok");
    wordInput.focus();
  }

  async function deleteWord(id) {
    const target = words.find((item) => item.id === id);
    if (!target) return;
    if (!window.confirm("「" + trim(target.word) + "」を削除しますか？")) {
      return;
    }
    words = words.filter((item) => item.id !== id);
    await saveWords();
    render();
    setStatus("削除しました。", "ok");
  }

  function escapeCsvValue(value) {
    const text = String(value == null ? "" : value);
    if (/[",\r\n]/.test(text)) {
      return "\"" + text.replace(/"/g, "\"\"") + "\"";
    }
    return text;
  }

  async function exportCsv() {
    if (!words.length) {
      setStatus("エクスポートする単語がありません。", "error");
      return;
    }
    const rows = [["英単語", "意味", "メモ", "登録元URL", "登録日時"]];
    words.forEach((item) => {
      rows.push([
        trim(item.word),
        trim(item.meaning),
        trim(item.memo),
        trim(item.sourceUrl),
        formatDateTime(item.createdAt)
      ]);
    });
    const csv = rows.map((row) => row.map(escapeCsvValue).join(",")).join("\r\n");
    // Excel で文字化けしないよう BOM を付ける。
    const blob = new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const now = new Date();
    const fileName = "study21-wordbook-" + now.getFullYear() + pad2(now.getMonth() + 1) + pad2(now.getDate()) +
      "-" + pad2(now.getHours()) + pad2(now.getMinutes()) + ".csv";
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setStatus(words.length + " 件を " + fileName + " に出力しました。", "ok");
  }

  function clearForm() {
    ["wordInput", "meaningInput", "memoInput", "sourceUrlInput"].forEach((id) => {
      document.getElementById(id).value = "";
    });
    setStatus("入力をクリアしました。", "info");
    document.getElementById("wordInput").focus();
  }

  // 右クリックメニューから開いた場合は ?word=...&url=... をフォームに反映する。
  function applyQueryParams() {
    const params = new URLSearchParams(window.location.search);
    const word = trim(params.get("word") || "");
    const sourceUrl = trim(params.get("url") || "");
    if (word) {
      document.getElementById("wordInput").value = word;
    }
    if (sourceUrl) {
      document.getElementById("sourceUrlInput").value = sourceUrl;
    }
    if (word) {
      document.getElementById("meaningInput").focus();
    }
  }

  document.getElementById("addBtn").addEventListener("click", () => {
    addWord().catch((error) => setStatus("追加に失敗しました: " + (error && error.message ? error.message : error), "error"));
  });

  document.getElementById("clearBtn").addEventListener("click", clearForm);
  document.getElementById("exportCsvBtn").addEventListener("click", () => {
    exportCsv().catch((error) => setStatus("エクスポートに失敗しました: " + (error && error.message ? error.message : error), "error"));
  });

  document.getElementById("searchInput").addEventListener("input", render);

  ["wordInput", "meaningInput", "memoInput", "sourceUrlInput"].forEach((id) => {
    document.getElementById(id).addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        addWord().catch((error) => setStatus("追加に失敗しました: " + (error && error.message ? error.message : error), "error"));
      }
    });
  });

  loadWords()
    .then(() => {
      applyQueryParams();
      render();
    })
    .catch((error) => {
      setStatus("読み込みに失敗しました: " + (error && error.message ? error.message : error), "error");
    });
})();
