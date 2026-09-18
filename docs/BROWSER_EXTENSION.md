# ブラウザ拡張（Web閲覧履歴の収集）

2.0 のブラウザ拡張（`study2/src/main/resources/extension`）を 2.1 向けに作り直したもの。
学校・家庭のパソコンの Chrome に入れて、見たページを **Web閲覧履歴**
（`NET_Web閲覧履歴情報`）に記録する。単語帳（ローカルのみ）も入っている。

- 拡張本体: `extension/`（MV3・ビルド不要の素の HTML/CSS/JS）
- 受信 API: `backend/user-api` の `BrowserExtensionIngestController`
- 配布: デプロイ時に `extension/` から zip を作り、`/downloads/study21-extension.zip` で配る

画面は 3 か所に分けてある（1 枚にまとめると Web閲覧履歴が読みにくくなるため。2026-09-13 変更）:

| やること | 場所 | コンポーネント |
|---|---|---|
| 拡張のダウンロードとインストール手順 | 画面右上（学生・保護者の表示のとなり）のプラグインアイコン | `src/features/browserext/BrowserPluginMenu.vue`（`AppTopbar` から使う） |
| 接続コードの発行・再発行・コピー | インターネット利用履歴 → Web閲覧履歴 の一覧見出し「接続コード」→ ダイアログ | `src/features/browserext/BrowserExtensionCodeDialog.vue` |
| 接続してきた端末の一覧 | 端末コントロール → 「ブラウザ端末」タブ | `src/views/net/TerminalControlView.vue` |

## 1. 2.0 からの変更

| 項目 | 2.0 | 2.1 |
|---|---|---|
| 認証 | **無し**。拡張が送る `userId`（'ljz' など）をそのまま信じていた | **接続コード**（64 文字の 16 進）。コードから持ち主（利用者アカウントID）を特定する。違えば 401 |
| 端末の管理 | `MST_ブラウザ監視端末情報` | `NET_ブラウザ接続情報`（1 アカウント 1 本のコード）+ `NET_ブラウザ端末情報`（ブラウザ 1 つ = 1 行） |
| 端末の登録 | `batchSave` の中の upsert。OS種別・最終起動日時が NULL で上書きされることがあった | `register` / `heartbeat` / `events` に分け、`COALESCE` で既存値を消さない |
| 二重登録 | 再送すると二重に入り得た | イベントごとの `clientEventId`（UUID）を `イベント識別子` に保存し、`ON CONFLICT DO NOTHING` で弾く |
| 配布 | 手作業で zip を作って置いていた（同梱の zip は古い版だった） | デプロイのビルド中に必ず作り直す。画面から落とせる |
| 単語帳 | 2.0 の `/api/word/*` を呼んでいた | **呼ばない**。`chrome.storage.local` に入れるだけ（2.1 に単語帳の受信 API は無い） |

## 2. テーブル

### NET_ブラウザ接続情報（`database/Web閲覧履歴/TBL_NET_ブラウザ接続情報.sql`）

1 アカウント = 1 行。`接続トークン` が接続コード（`SecureRandom` 32 バイトの 16 進 64 文字）。
画面は 4 文字ずつ区切って表示する。`状態コード` は `ACTIVE` / `REVOKED`（再発行で古いコードは即時に無効）。

### NET_ブラウザ端末情報（`database/Web閲覧履歴/TBL_NET_ブラウザ端末情報.sql`）

拡張が `register` してきたブラウザ 1 つ = 1 行。`(利用者アカウントID, 端末識別子)` が一意。
`最終心拍日時` から 10 分以内なら画面は「接続中」と出す（拡張は 5 分ごとに心拍を送る）。

### NET_Web閲覧履歴情報（`database/Web閲覧履歴/TBL_NET_Web閲覧履歴情報.sql`）

2.0 から移行済みの表（3 万件以上）。2026-09-13 に `イベント識別子` を追加し、
`(端末識別子, イベント識別子)` に一意索引を張った（移行データは NULL なので衝突しない）。

## 3. API

### 画面向け（ログイン必須。`BrowserExtensionController`）

| メソッド | パス | 内容 |
|---|---|---|
| GET | `/api/user/browser-extension/registration` | 接続コードと接続中の端末（未発行なら `issued=false`） |
| POST | `/api/user/browser-extension/registration` | 接続コードを発行（発行済みならそのまま返す） |
| POST | `/api/user/browser-extension/registration/reissue` | 再発行（古いコードは使えなくなる） |

### 拡張向け（ログイン不要。`X-Study21-Extension-Token` ヘッダで認証）

| メソッド | パス | 内容 |
|---|---|---|
| POST | `/api/user/browser-extension/register` | 起動時の端末登録（upsert） |
| POST | `/api/user/browser-extension/heartbeat` | 生存通知（未知の端末は心拍から作る） |
| POST | `/api/user/browser-extension/events` | 閲覧イベントのバッチ（最大 500 件。拡張は 100 件ずつ送る） |

- どれも接続コードが無い・違えば **401**（`接続コードが正しくありません。`）。
- `events` の応答は `receivedCount` / `insertedCount` / `duplicateCount` / `skippedCount`。
  知らないイベント種別は「飛ばす」だけで 400 にしない（拡張のキューが詰まるため）。
- `SecurityConfig` の CORS は、この 3 つのパスだけ `chrome-extension://*` を許可する。
  **登録順に意味がある**（`UrlBasedCorsConfigurationSource` は先に登録した設定から照合し、
  最初に当たったものを返す）。個別パスを `/**` より先に登録すること。
  壊れると 403「Invalid CORS request」になり、拡張は履歴を送れなくなる
  （`SecurityConfigCorsTest` が検知する）。

### 拡張からのリクエスト例

```jsonc
// POST /api/user/browser-extension/events
{
  "deviceId": "chrome-1750000000000-ab12cd34",
  "deviceName": "LIU-PC", "browserType": "Chrome", "browserVersion": "153.0.0.0",
  "extensionVersion": "2.1.0", "profileId": "Default",
  "sentAt": "2026-09-13T21:00:00+09:00",
  "events": [{
    "clientEventId": "3f2a…", "eventType": "HISTORY_VISITED",
    "url": "https://example.com/a", "domain": "example.com", "pageTitle": "ページ",
    "referrerUrl": "https://example.com/", "faviconUrl": "https://example.com/f.ico",
    "transitionType": "link", "tabId": 12, "windowId": 1, "sessionId": "…",
    "active": true, "fromHistorySync": false,
    "visitedAt": "2026-09-13T20:59:30+09:00", "staySeconds": 12, "visitCount": 1
  }]
}
```

イベント種別は 2.0 と同じ 5 種類: `HISTORY_VISITED` / `NAV_HISTORY_UPDATED` /
`TAB_UPDATED` / `TAB_ACTIVATED` / `NAV_COMMITTED`。

## 4. 拡張の設定（学生が行うこと）

1. 画面右上の**プラグインアイコン**を押し、「ダウンロード」で zip を落として解凍する
   （パネルの中の「インストール手順を見る」に手順が出る）
2. Chrome で `chrome://extensions` →「デベロッパー モード」ON →
   「パッケージ化されていない拡張機能を読み込む」で解凍したフォルダを選ぶ
3. インターネット利用履歴 → **Web閲覧履歴** → 一覧見出しの「**接続コード**」を押し、
   ダイアログで「接続コードを発行」→「コピー」
4. 拡張の「設定」画面に貼り付けて保存

接続コードを入れると、その端末は 端末コントロール の「**ブラウザ端末**」タブに出る
（心拍が 10 分以内なら「接続中」）。

保存すると `register` → `heartbeat` → 溜まっていたイベントの送信まで一度に走る。
popup に「接続中 / 送信待ち N 件 / 最終送信時刻」が出る。

- 既定の API の URL は `http://192.168.0.100:8082`（設定画面で変更可）
- 保存先は `chrome.storage.local`（`study21.apiBaseUrl` / `study21.accessCode` /
  `study21.deviceName` / `study21.recordingEnabled` / `study21.deviceId` / `study21.queue`）
- 記録を OFF にしても、すでに溜まったイベントは消さずに送信を止める

## 5. 配布パッケージ（zip）の作り方

`extension/build-zip.mjs` が `extension/` を読んで zip と情報ファイルを作る
（依存パッケージ無し。ZIP は自前で組み立て、圧縮は `node:zlib`）。

```bash
node extension/build-zip.mjs
# → frontend/pc-web/public/downloads/study21-extension.zip
#    frontend/pc-web/public/downloads/study21-extension.json（版・サイズ・ファイル数）
```

デプロイ時は `deploy/docker/nginx.Dockerfile` のフロントのビルド中に走る:

```dockerfile
COPY extension ./extension
RUN node extension/build-zip.mjs --out /app/pc-web/dist/downloads/study21-extension.zip
```

出来たものは nginx がそのまま配る（`/downloads/study21-extension.zip`）。
画面は `.json` を読んで版とサイズを出し、URL に `?v=<版>` を付けて古いファイルを掴まないようにする。
**デプロイのたびに作り直される**ので、拡張を直せば次のデプロイで配布物も新しくなる。

`frontend/pc-web/public/downloads/` はビルド生成物なので git 管理外
（`.gitignore`）。`tools/deploy-to-nas.sh` のコピー対象からも外してある
（イメージの中で作られるため）。

## 6. テスト

```bash
# 受信 API（モック + 実 DB。DB テストはパスワードがあるときだけ走る）
cd backend
STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test

# 画面（vitest）
cd frontend/pc-web && npm run test      # tests/browser-extension.spec.ts
```

| テスト | 内容 |
|---|---|
| `BrowserExtensionServiceImplTest`（17 件） | 接続コードの認証・発行・再発行、端末の upsert、イベントの正規化・重複・スキップ |
| `BrowserExtensionRepositoryTest`（3 件） | 実 DB で 発行 → 登録 → 心拍 → イベント受信 → 再送しても増えない |
| `SecurityConfigCorsTest`（3 件） | 拡張の Origin だけを許可し、他のサイトは弾く |
| `tests/browser-extension.spec.ts`（12 件） | 右上のプラグインメニュー（開閉・ダウンロード・手順）と接続コードのダイアログ（発行・コピー・再発行） |
| `tests/net-control.spec.ts` の「ブラウザ端末タブ」（4 件） | タブ 2 つ・開いたときだけ読む・端末の表示・0 件の案内 |

実機（拡張を実際の Chrome に読み込んで送るところまで）:

```bash
# 前提: user-api（8083）と vite dev（5199）
node tmp/e2e/e2e-browser-extension.mjs     # 49 項目（一時アカウントは最後に削除）
```

## 7. 未実装・今後

| 項目 | 補足 |
|---|---|
| 接続の**停止**（`状態コード` を `REVOKED` にする） | 列と CHECK は用意したが入口は無い。止めたいときは今のところ再発行で古いコードを無効にする |
| 端末の削除 | 画面からは消せない（`register` し直すと復活する。行を消すなら履歴の持ち主と一緒に整理する） |
| サーバー側の重複判定を超えた集計 | 「1 ページに何秒いたか」は拡張の申告値（`滞在秒数`）のまま。2.0 と同じ |
| 単語帳の保存先 | 端末ローカルのみ（`chrome.storage.local`）。2.1 のサーバーに取り込むなら単語帳側の API を作る |
| `fromHistorySync` | 履歴同期で入った行かどうかは判定できず、常に `false` |
| 端末の一覧からの操作 | 「ブラウザ端末」タブは見るだけ（削除・無効化の入口は無い） |
| 拡張の配布物の版管理 | zip はビルドのたびに作り直す。**版を上げるときは `extension/manifest.json` の `version` を直す**（画面の表示と URL の `?v=` がそれに従う） |
