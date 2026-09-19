# サイト管理 / 端末コントロール

2.0（`/vol5/1000/DATA0/work/study2`）の `site.jsp` / `terminal_control.jsp` を 2.1 に実装したもの。
端末（クライアント IP）ごとに動作モードを決め、モードごとに許可するサイトを管理する。
2.0 から移行したデータ（サイト 175 件・端末 5 台）をそのまま使っている。

- データベース設計: `database/サイト管理/NET_サイト管理・端末コントロール設計.md`
- DDL: `database/サイト管理/TBL_NET_サイト情報.sql`・`database/端末コントロール/TBL_NET_端末コントロール情報.sql`
- 移行: `database/移行/MIG_NET_サイト・端末コントロール_20260911.sql`

## 1. 画面（pc-web）

| 画面 | ルート | コンポーネント |
|---|---|---|
| サイト管理 | `/{admin\|student\|parent}/site` | `frontend/pc-web/src/views/net/SiteManagementView.vue` |
| 端末コントロール | `/{admin\|student\|parent}/terminal-control` | `frontend/pc-web/src/views/net/TerminalControlView.vue` |

- サイト管理: 検索（分類・区分・承認・ステータス・キーワード）／並び替え／ページング（15 件/頁）／
  新規登録／編集／削除／**行ごとの 承認・却下**。一括操作（選択チェックボックスと一括承認ボタン）は置かない。
  新規は未承認で登録され、**編集すると未承認に戻る**（再承認が必要／2.0 と同じ）。
- 全サイトの有効／無効を一括で切り替えるボタン（「インターネット利用を開始／停止」）は**置かない**。
  2.0 の `SiteController#enableInternetUsage` / `#disableInternetUsage` は 2.0 内に呼び出し元が無く
  （画面にもバッチにも未接続）、2.0 の「インターネット利用の開始／停止」は batR03 / batR04 が
  **端末モード**を `S`（停止）/ `T`（通常）に変えて行っているため。2.1 では端末コントロールの
  一括切替がその入口になる。
- 承認の考え方: 承認 = `承認済`＋承認者・承認日時を記録、却下 = `却下`（承認者・承認日時は残さない）。
  操作列は状態に応じて 1 つだけ出す（**未承認・却下 → 承認 / 承認済 → 却下**）。
  却下したあと、もう一度承認できる。
- 操作アイコンの色: 承認＝緑のチェック / **却下＝橙の取り消し（`icon--reject`）** / 編集＝青 /
  削除＝赤のゴミ箱。**却下に赤を使わない**（削除と紛らわしいため。共通ルールは
  `frontend/pc-web/src/assets/app/app.css`）。
- サイト管理の検索条件: 分類 / 区分 / **判定方法** / 承認 / ステータス / キーワード。
  **サイトURL の並び替えはドメイン単位**（ホスト名のラベルを逆順にしたキーで比較する）ため、
  同じサイトのサブドメインが親ドメインのすぐ後ろに並ぶ:
  `google.com` → `accounts.google.com` → `mail.google.com` → `gakken.jp` → `gakken-ep.jp`
  （文字列の単純比較だと `accounts.google.com` が先頭に来て `google.com` と離れてしまう）。
- 端末コントロールは**タブを 2 つ**持つ（2026-09-13 追加）:
  * 端末コントロール … IP で管理する端末（下記）
  * ブラウザ端末 … ブラウザ拡張が接続コードで登録した端末（`NET_ブラウザ端末情報`。
    端末名称・端末ID・ブラウザ・拡張の版・最終心拍・最終送信・状態＝接続中/オフライン）。
    接続コードの発行・再発行は インターネット利用履歴 の「Web閲覧履歴」側
    （`docs/BROWSER_EXTENSION.md`）
- 端末コントロール: 一覧（IPアドレス・端末名称・端末ステータス・**状態**・備考・最終更新日時・更新者）、
  **【新規】で登録**、【操作】列の鉛筆アイコンで**編集**、選択した端末を**【一括適用】でモード切替**。
  端末は台数が少ないため**検索条件は置かない**（一覧は全件。行ごとのモード切替も置かない＝切替は一括適用だけ）。
  **状態**（`1`=有効 / `0`=無効）は端末ステータス（モード）とは別の列でバッジ表示する
  （2.1 で追加した列。無効にすると同じ IP を別の端末で再利用できる）。
- 表示文言はコードから画面側で作る（`src/features/net/netLabels.ts`。2.0 と同じ表記）。

## 1.1 画面の置き場所（ネットワーク制御）

左メニューの「ネットワーク制御」の下に 3 画面を置く。

| 画面 | ルート | 実装 |
|---|---|---|
| サイト管理 | `/{area}/site` | `frontend/pc-web/src/views/net/SiteManagementView.vue` |
| 端末コントロール | `/{area}/terminal-control` | `views/net/TerminalControlView.vue` |
| インターネット利用履歴 | `/{area}/internet-usage` | `views/network/InternetUsageHistoryView.vue` |

インターネット利用履歴は 2 つのタブを持ち、**どちらのタブにも検索条件カード**（`.search-panel`。
資料管理と同じ形で、右上に【検索】【リセット】）と一覧カードを置く。件数は**一覧カードの見出し**に出し、
タブ名には出さない。

- **サイトアクセス履歴**（2.0 の履歴管理画面の「上網履歴」）: `NET_プロキシ通信履歴情報` を
  接続先・端末名称・結果（許可／拒否）で絞り込んで表示する（`GET /api/user/net-access-logs`）。
  端末名称は `NET_端末コントロール情報` から IP で引くため、絞り込みは**登録端末のドロップダウン**
  （`GET /api/user/net-terminals` の端末名称。重複は 1 つ・名前順）で選ぶ。
- **Web閲覧履歴**（同「ブラウザ閲覧履歴」）: ブラウザ拡張が記録した閲覧イベント
  （`NET_Web閲覧履歴情報`。2.0 の `TRN_ブラウザ閲覧履歴情報` から移行済み）を
  端末ID・端末名称・ドメイン・イベント種別・キーワード・アクセス日で絞り込んで表示する
  （`GET /api/user/web-browsing-logs`）。既定は「今日」の範囲（件数が多いため）。
  2.0 の履歴管理画面（history.jsp）の「ブラウザ閲覧履歴」タブと同じ列を持つ。

## 2. API（user-api）

| メソッド | パス | 説明 |
|---|---|---|
| `GET` | `/api/user/net-sites` | 一覧（kind / judgeMethod / category / approvalStatus / status / keyword / sortBy / sortDir / page / size。`sortBy=siteUrl` はドメイン単位の並び替え） |
| `POST` | `/api/user/net-sites` | 新規登録（未承認） |
| `PUT` | `/api/user/net-sites/{siteId}` | 更新（未承認に戻る。`version` で楽観的ロック） |
| `DELETE` | `/api/user/net-sites/{siteId}` | 削除 |
| `POST` | `/api/user/net-sites/{siteId}/approval` | 承認（承認者・承認日時を記録） |
| `POST` | `/api/user/net-sites/{siteId}/rejection` | 却下（`却下` にし、承認者・承認日時は残さない） |
| `GET` | `/api/user/net-access-logs` | サイトアクセス履歴（host / terminalName / result / page / size） |
| `GET` | `/api/user/web-browsing-logs` | Web閲覧履歴（terminalId / terminalName / domain / eventType / keyword / dateFrom / dateTo / page / size） |
| `GET` | `/api/user/web-browsing-logs/event-types` | 絞り込みに出すイベント種別の一覧（5 種類） |
| `GET` | `/api/user/net-terminals` | 一覧（mode / status / keyword / page / size。画面は size=200 で全件） |
| `POST` | `/api/user/net-terminals` | **新規登録**（IP・端末名称・モード・状態・備考） |
| `PUT` | `/api/user/net-terminals/{terminalId}` | **編集**（`version` で楽観的ロック） |
| `POST` | `/api/user/net-terminals/{terminalId}/mode` | 1 台のモード変更（`version` で楽観的ロック。API としては残している） |
| `POST` | `/api/user/net-terminals/mode` | 選択した端末のモード一括変更（画面の【一括適用】） |

- 端末の IP は **有効（`状態='1'`）な端末の間でのみ一意**。登録・編集時に
  同じ IP の有効な端末があれば 400（「このIPアドレスは既に登録されています（有効な端末）。」）。
  同じ IP を別の端末で使いたいときは、先に古い端末を `状態='0'`（無効）にする。
- IP の書式（IPv4 / IPv6）はサービス層で検証する（DB の CHECK は使える文字だけを見るため）。

- 実装: `backend/user-api/src/main/java/com/study21/user/net/`（`NetSiteService`・`NetTerminalService`・
  `NetSiteMapper`・`NetTerminalMapper`）と `controller/NetSiteController.java`・`NetTerminalController.java`。
- 認証: どちらもログイン必須（`SecurityConfig` の `authenticated()`）。**2.1 は当面ロールで分けない**
  （ユーザーの指定。`docs/SECURITY_AND_ROLES.md` §2.1）。2.0 は端末コントロールを保護者に限定していた
  （それ以外は 403）が、2.1 はログインしていれば生徒・保護者とも使える
  （`NetTerminalServiceImpl#requireLogin` はログインだけを見る）。
  サイト管理は 2.0 の画面にも権限判定が無かったため、同じくログイン中のユーザーなら利用できる。
  **承認・却下も権限で分けていない**（生徒・保護者とも操作できる。2.0 は `site.approveButton` で
  生徒には承認ボタンを隠していたが、2.1 では当面区別しない）。サービス層にも権限チェックは無く、
  テスト（`studentCanApproveBecauseRolesAreNotSeparatedYet` / 「生徒でも承認・却下の操作ができる」）で固定している。
- DB 操作は `SqlLoggingInterceptor` が `logs/backend/user-api-sql.log` に記録する（MyBatis 経由のみ）。

## 3. batS01（プロキシサービス）

2.0 はサイト・端末の更新後に **batL01（プロキシサービス／再起動）** を起動していた
（`triggerBatL01AfterSiteControl` / `triggerBatL01AfterTerminalControl`）。
2.1 のプロキシサービスは **batS01** として実装済み（`docs/PROXY.md`）で、起動の入口は
①admin-api の起動時 ②バッチ管理画面の【再実行】③**batR03 / batR04**（ネット利用の終了／開始）の 3 つ。

- **画面からのサイト・端末の更新では自動起動しない**（DB 更新だけを行う）。プロキシは端末モードを
  **1 リクエストごとに DB から読む**ので、モードを書き換えれば次のリクエストから効く
  （プロキシが動いていれば再起動は不要）。呼び出し箇所にはその旨のコメントを残している
  （`NetSiteServiceImpl` の create/update/delete/approve/reject/approveMany、
  `NetTerminalServiceImpl` の updateMode/updateModes）。
- **batR03 / batR04 は端末モードの一括切替のあとにプロキシの稼働を保証する**
  （`NetworkUsageService` → `ProxyServerService#startIfNeeded`）。設定は実行時刻だけで、
  有効／無効はバッチ一覧のスイッチ（`BAT_バッチコントロール情報`）が唯一の正。
  実行の仕組みは `docs/BATCH_SCHEDULE.md`。

## 4. データ移行

- DDL は study21 に適用済み。移行は `dblink` で study2 を読み、**旧 ID を維持**して投入する（冪等）。
- 値の対応（区分・判定方法・分類・承認・ステータス）は設計書 §3 を参照。
  UI に無い分類（英会話・ニュース）は `分類コード='OTHER'` + `分類名称` で保持している。
- 2.0 の `登録ID`/`更新ID`（`site.jsp`・`batR03`・`AGENT` など）は実行者を特定できないため、
  アカウントID は NULL、`登録元コード`/`更新元コード` に `MIGRATION` を入れている。
- サイトは 2.0 の 175 件を移行したが、運用中に 6 件（旧 サイトID 196〜201）が 2.1 から
  消えていたため `database/移行/MIG_NET_サイト_復元_20260912.sql` で復元した（再度 175 件）。
- Web閲覧履歴は `database/移行/MIG_NET_Web閲覧履歴_20260912.sql`（3 万件超）。
  2.0 の `ユーザーID` は 日報・TODO と同じ対応（'ljz'→2 / 'liu'→1）で `利用者アカウントID` にし、
  対応表に無い ID（'student01' など）は `利用者アカウントID` を NULL にして `旧ユーザーID` を残す。

## 5. テスト

| テスト | 場所 | 見るもの |
|---|---|---|
| サイト管理の業務ルール | `backend/user-api/src/test/java/com/study21/user/net/NetSiteServiceImplTest.java` | 15 tests |
| 端末コントロールの業務ルール（ロールで分けない・登録/編集の検証・IP 重複・楽観ロック） | `.../net/NetTerminalServiceImplTest.java` | 14 tests |
| 実 DB（移行データ・登録〜削除・承認→却下→再承認・端末の登録/編集/モード変更・URL 並び替え。ロールバックする） | `.../net/NetControlRepositoryTest.java` | 6 tests |
| 画面（表示・検証・送信内容・403 表示・承認操作・端末の新規/編集） | `frontend/pc-web/tests/net-control.spec.ts` | 22 tests |
| 実 DB（Web閲覧履歴の絞り込み・ページング・イベント種別） | `.../net/WebBrowsingLogRepositoryTest.java` | 6 tests |
| 画面（インターネット利用履歴の 2 タブ・検索条件カード・端末のドロップダウン・送信内容） | `frontend/pc-web/tests/internet-usage.spec.ts` | 13 tests |

実 DB テストは `STUDY21_DATASOURCE_PASSWORD` がある環境でだけ動く（無いときはスキップ）。

```bash
cd backend && STUDY21_DATASOURCE_PASSWORD=<パスワード> mvn -pl user-api -am test
```

ブラウザでの実機確認は `tmp/e2e/e2e-net.mjs`（検証用の一時ファイル）。ローカルの user-api（8083）と
実 DB を使い、一覧・登録・承認・編集・削除・モード変更・一括切替、そして
**生徒でも端末コントロールが使えること**（一覧の参照とモード変更。2.1 はロールで分けない）、
端末の新規登録・IP 重複の拒否・編集まで通す（45 項目）。

インターネット利用履歴は `tmp/e2e/e2e-internet-usage.mjs` で通す（15 項目。両タブの検索条件カード、
タブ名に件数を出さないこと、端末名称のドロップダウンと絞り込み）。

承認まわりは `tmp/e2e/e2e-site-approval.mjs` で通す（22 項目）。**生徒アカウントでログイン**し、
画面からサイトを登録 → 承認（承認済・承認日時が入る）→ 却下（却下になり承認日時が残らない）→
もう一度承認できることまで確認する（22 項目）。あわせて、選択チェックボックスと一括承認ボタンが無いこと、
未承認の行には承認だけ・承認済の行には却下だけが出ること、却下アイコンが削除（赤）と別物であることも
確認する。どちらも実行後に検証用のサイトとアカウントを削除すること。

## 6. 未実装・今後

| 項目 | 補足 |
|---|---|
| batL01 の起動 | 2.1 は batS01（プロキシ）＋ **batR03 / batR04 が稼働を保証**する（§3）。画面からのサイト・端末の更新では自動起動しない |
| インターネット利用の開始／停止 | **batR03 / batR04** が端末モードを S / T に一括切替して行う（実装済み。`docs/BATCH_SCHEDULE.md`）。画面のボタンは置かない（§1） |
| 端末の**削除** | 画面からの登録・編集は 2.1 で実装した（2026-09-12）。削除は用意していない（使わなくなった端末は `状態='0'` で無効にする） |
| `最終接続日時` の更新 | プロキシ（未実装）が更新する想定 |
| サイトの通信履歴 | `NET_プロキシ通信履歴情報`（2.0 から 138,840 件を移行済み。`docs/PROXY.md`） |
| ブラウザ拡張からの記録 | 2.1 向けに作り直した（2026-09-13）。接続コードによる認証つきの受信 API（`/api/user/browser-extension/register\|heartbeat\|events`）、`extension/` の拡張本体、デプロイ時の zip 配布と画面からのダウンロードまで実装済み。詳細は `docs/BROWSER_EXTENSION.md` |
| サイトの `状態`（有効／無効）を変える入口 | 列と検索条件は持っているが、2.0 の画面にも入口が無く（全件有効）、全サイト一括の切替は §1 の理由で撤去した。行ごとの退役（`状態='0'`）を画面から行うなら、行アクションとして追加する |
| ロールごとの権限分離 | **当面は分けない**（§2・`docs/SECURITY_AND_ROLES.md` §2.1）。承認・却下も端末コントロールも学生が使える。分けるときはサービス層と画面に判定を足し、`docs/ROLE_FUNCTION_MATRIX.md` を更新する |
| 管理者（ADMIN）からの利用 | admin-api とはセッションが別のため、管理者エリアからは user-api を呼べない（要検討） |
