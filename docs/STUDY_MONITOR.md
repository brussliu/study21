# 学習状況モニター（`/{area}/study-monitor`）

2.0 の `study_monitor.jsp`（Web カメラの録画から切り出したスナップショットを、AI 分析の
結果つきで一覧・修正する画面）を 2.1 の PC 画面として移植したもの。

- 画面: `frontend/pc-web/src/views/study-monitor/StudyMonitorView.vue`
- スタイル: `frontend/pc-web/src/features/study-monitor/study-monitor.css`
- 仮データ: `frontend/pc-web/src/features/study-monitor/studyMonitorMock.ts`
- ルート: `/{admin|student|parent}/study-monitor`（`docs/FRONTEND_GUIDE.md` §3）
- 左メニュー: 学生 UI のトップレベル「学習状況モニター」（アイコン `video`）

## 1. 画面構成（2.0 との対応）

**2.0 `study_monitor.jsp` のレイアウトをそのまま使う**（ユーザーの指定。2026-09-13 に合わせた）。
色は 2.0 の直値ではなく `tokens.css` の変数（暗色テーマも追従）、モーダルは 2.1 の共通ダイアログ。

| 2.0（study_monitor.jsp） | 2.1 |
|---|---|
| `monitor-heading`（eyebrow ＋ タイトル ＋ 説明 ＋ 右にバッジと【最新状態を確認】） | 同じ（バッジは「サンプルデータ」＝仮データであることを明記） |
| `monitor-toolbar`（表示方法 / 対象日 / 時間帯 / AI分析 / 分析結果 / batL02 の注記） | 同じ。**表示方法**（動画から確認 / スナップショットから確認）で動画の列を畳む。分析結果はスナップショット検索モードのときだけ出す |
| `snapshot-summary`（4 つの件数（動画・スナップショット・分析済み・未分析＋エラー）＋ 分析結果の割合バーと凡例 ＋【時間軸で確認】） | 同じ（`data-stat` 属性つきのカード。割合のバーは `.sm-bar__part`） |
| `monitor-main-column` の 2 カラム（左 520px の動画セグメント / 右にスナップショット） | 同じ（左 420px。行は カメラ アイコン ＋ ファイル名 ＋ 開始〜終了 ＋ 状態バッジ ＋ 切出枚数） |
| スナップショット（縮略図／一覧の切替、全選択、一括変更、詳細モーダル） | 同じ（グリッドは `auto-fill minmax(170px, 1fr)`、一覧は横並び。**詳細は画像の右下の編集アイコンから開き、そこでも 1 枚ずつ修正できる**） |
| 時間軸モーダル（`activity-timeline`） | 同じ（【時間軸で確認】で、**時間帯全体**の画像を時刻順に帯・目盛り・凡例・一覧で出す） |
| `api/study-monitor/snapshots` | **未実装**（下記 §3）。現在は仮データ（DB は実データを移行済み） |

スナップショットの見た目は 2026-09-13 に次を直した（`study-monitor.css`）。

- **画像は縦横比どおりの長方形**。DB の `幅`/`高さ`（実データは 3840×2160 = 16:9）を `style="aspect-ratio: …"` で渡し、
  `object-fit: contain` で切り取らない（以前は 4:3 の箱に `cover` で詰めていたため、正方形に近く見えていた）。
- **横スクロールバーを解消**。`.snapshot-card`（`<figure>`）はブラウザ既定の `margin: 1em 40px` を持ち、
  グリッドはそのマージンぶんも列幅に足して計算するため、カードが列からはみ出していた（実測: 7 列で +32px）。
  `margin: 0` にして解消。
- **詳細モーダルは画像を最大まで広げる**（`width: min(1600px, 96vw)` / `height: min(94vh, 1080px)`。
  以前は `dialog--md`（640px）＋ `max-height: 22rem` で上下左右に余白が残っていた）。
- **縮略図 / 一覧の切替は選択中が分かる**（`.snapshot-display-mode .btn.is-active` を `--color-primary-soft` で塗る）。

DB は 2.0 の 4 テーブルを 2.1 の規約で再設計して移行済み
（`database/学習状況モニター/学習状況モニター設計.md`。カメラ 1・動画 2,777・
スナップショット 43,326・分析 43,326 件）。**画像の実ファイルだけは 2.0 サーバーからの
コピーが必要**（`tmp/tools/copy-study-monitor-snapshots.sh`）。

## 2. 操作とボタンの外観

| 操作 | 外観（`docs/FRONTEND_GUIDE.md` §9.1 準拠） |
|---|---|
| 最新状態を確認（＝2.0 の更新） | primary + `rotate` |
| 時間軸で確認 | secondary + `clock` |
| 縮略図表示 / 一覧表示 | アイコンボタン（切替。選択中は `is-active` で塗る） |
| 表示中を全選択 / 選択解除 | チェックボックス（2.0 と同じ「表示中の分析済み画像をすべて選択」） |
| 一括変更 | primary + `edit`（選択 0 件のときは disabled） |
| スナップショットの詳細 | **画像の右下の編集アイコン**（`.snapshot-edit-button` ＝ `.btn.btn--icon.btn--sm` ＋ `edit`（青）／`data-snapshot-open`）。画像そのものはクリックしても開かない |
| 詳細の保存 | primary + `check`（`data-action="detail-apply"`。モーダル内の「閉じる」は secondary） |

一括変更は分析結果（学習中／休憩中／ゲーム中／離席／判定不能）と**修正理由**を必須にし、
2.0 と同じく理由なしでは適用できない（画面側で止める）。
詳細モーダルは 分析結果 ＋ 修正理由（**任意**。ユーザーの指定。2026-09-13）＋ 保存 で、
**分析済み（`COMPLETED`）の画像だけ**直せる（未分析・エラーはその旨を出す）。
理由が空のときは DB に NULL で残す（「理由なし」と分かるように）。
送信は一括・1 枚とも `PATCH /api/user/study-monitor/snapshots`（`{updates:[{snapshotId,version}], result, reason}`）。

## 3. バッチ（batL02 / batL03）— 2026-09-19 に実装

2.0 の 2 つのバッチを 2.1 の admin-api に移植した（`com.study21.admin.studymonitor`）。
実行のきっかけは**統一スケジューラ**（30 秒ごと。実行間隔とずらしは設定。`docs/BATCH_SCHEDULE.md`）。

| バッチ | 役割 | 実装 |
|---|---|---|
| batL02 | 録画ファイルの取込とスナップショット切り出し（ffmpeg） | `StudyMonitorImportHandler` |
| batL03 | スナップショットの AI 分析（結果・信頼度・理由の書き込み） | `StudyMonitorAnalyzeHandler` |

- **batL02**: `checked/` を先に処理 → ソースフォルダーの動画（最新の 1 本は「125MB 超かつ更新から
  5 分以上」のときだけ）を `checked/` へ移してから取り込む。処理対象時間帯の外は `bak/` へ移す
  （判定は**ファイル名の撮影時刻**で、跨日も扱う）。1 回の実行で最大 5 本。
  ffprobe で長さを読み、ffmpeg（`-vf fps=1/切出間隔秒`）で切り出して、日付フォルダー
  （`<snapshot-root>/<yyyyMMdd>/snapshot_%06d.jpg`）へ移す。DB には
  `MON_学習モニター動画情報`（`取込状態コード='IMPORTED'`）と
  `MON_学習モニタースナップショット情報`（`切出状態コード='CREATED'`）を書き、`保存パス` は
  **保存ルートからの相対パス**にする。監査は `登録元コード='BAT_L02'`。
- **batL03**: `切出状態コード='CREATED'` かつ**最新版（`最新版フラグ='1'`）の分析行が無い**
  スナップショットを古い順に `STUDY_MONITOR_AI_BATCH_LIMIT` 枚だけ取る。画像を設定解像度へ縮小して
  1 枚ずつ vision AI に渡し、`判定（status）`・`confidence`・`reason` を検証して
  `MON_学習モニター画像分析情報` に書く（`最終採用段階コード='FLASH'`、二次判定は
  `二次判定要否='0'` / `二次分析状態コード='NOT_REQUIRED'`）。失敗は `ERROR` 行。
  AI 呼出は `BAT_AI呼出履歴情報` に 1 行ずつ残す。監査は `登録元コード='BAT_L03'`。
  - **2.0 の欠陥を直した**: 2.0 は実行のたびに `分析状態='ERROR'` の全行を DELETE してから
    対象を選んでいたため、壊れた画像が**永久に再試行**され、最古の数枚が毎回枠を埋めて
    後続が餓死した（実測 532 枚が未分析のまま）。2.1 は **ERROR 行を消さない**
    （最新版フラグが立つので対象から外れる）。自動リトライはしない（画面で ERROR を見て人が直す）。
  - **L03 は時間ではなくスナップショットの完成状態で選ぶ**（「L02 の時刻がずれているから
    済んでいるはず」とは考えない）。

### 3.1 配備の前提（動画と画像の置き場）

| 設定 | 置き場 | 備考 |
|---|---|---|
| `STUDY_MONITOR_VIDEO_SOURCE_DIRECTORY` | 監視カメラの録画フォルダ | **書き込み可**でマウントする（取り込んだ動画を `checked/`・`bak/` へ移すため） |
| `study21.study-monitor.snapshot-root`（`STUDY21_STUDY_MONITOR_SNAPSHOT_ROOT`） | スナップショットの保存ルート | **user-api と同じ場所**を指す（user-api は読み取り専用、admin-api は書き込み可） |
| `study21.study-monitor.ffmpeg-command` / `ffprobe-command` | 実行ファイル | 既定 `ffmpeg` / `ffprobe`（admin-api のイメージに同梱。docker-compose.yml を参照） |

2.0 の設定 `STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY`（絶対パス）は**使わない**
（配備ごとに場所を変えられるよう、2.1 はプロパティ＋相対パスにした）。値が違えば batL02 が
警告ログで知らせる（画面の欄は 2.0 との対応のために残してある）。

**移行の確認（重要）**: 動画・スナップショット・分析の ID は 2.0 から維持したため、ID の連番
（シーケンス）が実データより遅れていると、batL02 / batL03 の INSERT が**主キー重複で失敗**する。
実測で 3 つのシーケンスが遅れていたので `database/移行/MIG_シーケンス同期_20260919.sql` で
同期する（冪等。引っ越しの直後に流す）。

## 4. 検証

- 単体/コンポーネント: `frontend/pc-web/tests/study-monitor.spec.ts`
  （時間軸が時間帯全体を見ること、詳細は編集アイコンからだけ開くこと、詳細で修正できること、
  詳細は修正理由が空でも保存できること、未分析は修正できないことを含む）
- 業務ルール（サーバー）: `backend/user-api/src/test/java/com/study21/user/studymonitor/StudyMonitorServiceImplTest.java`
  （修正理由は任意・空は NULL で保存・2000 文字超は拒否・分析結果は必須）
- ブラウザ E2E（レイアウト）: `tmp/e2e/e2e-study-monitor-layout.mjs`（39 項目）
  - API と画像は puppeteer の interception で差し替えるので、**実 DB を触らず・画像が未コピーでも**
    検証できる（前提: `tmp/e2e/vite.e2e.config.ts` の vite dev 5199）
  - 横スクロールバーが出ない（動画／スナップショットのタブ × 縮略図／一覧の 4 通り）、
    画像が 16:9 の長方形（切り取らない）、表示切替の選択中が分かる、編集アイコンの位置と
    画像クリックでは開かないこと、詳細の画像が大きい（1600×1200 と 1366×768）、
    詳細からの修正が 1 件だけ飛ぶ・一覧に反映される、時間軸が時間帯全体を出す
- ブラウザ E2E（実データ）: `tmp/e2e/e2e-study-monitor.mjs`（`tmp/e2e/run-study-monitor-e2e.sh` で実行）
  - サマリ、既定の動画のスナップショット、動画の切替、時間帯の絞り込み、表示切替、
    一括変更（理由つき。DB を確認して元に戻す）、詳細モーダル
  - **2.0 のレイアウト**（見出し・表示方法の切替・分析集計の 4 カード・2 カラム・動画セグメント・
    【時間軸で確認】）、時間軸モーダル（区間・目盛り・色・凡例）、
    スナップショット検索モード（動画の列が畳まれて 1 列になる・分析結果の絞り込みが出る）
  - 前提: localhost の user-api（8083）と vite dev（5199）。E2E は検証用アカウントを作って
    最後に削除する。

## 5. ロール

`docs/ROLE_FUNCTION_MATRIX.md` のとおり 2.1 では**ロールを分けていない**（学生・保護者・
管理者とも同じ画面）。3 エリアすべてにルートを登録済み。認証を厳格化するときに
「自分の録画のみ」「保護者は子のみ」の条件を API 側に入れる。
