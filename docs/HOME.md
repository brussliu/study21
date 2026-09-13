# ホーム（`/{area}/home`）

2.0 の `home.jsp`（ホーム）と `english.jsp`（英語勉強）の内容をまとめた画面。
2.1 では学生・管理者が同じ画面を使い、保護者は `ParentHomeView.vue` から同じ
学習サマリ（`components/home/StudyOverview.vue`）を見る。

- 画面: `frontend/pc-web/src/views/home/HomeView.vue`
- スタイル: `frontend/pc-web/src/features/home/home.css`
- 仮データ: `frontend/pc-web/src/features/home/homeMockData.ts`

## 1. カードの構成

| カード | 実装 | 2.0 の対応 | データ |
|---|---|---|---|
| あいさつ（名前・日付・連続学習） | `HomeView.vue` | `part/title.jsp` 相当 | セッション＋仮データ |
| **学習・休憩・ゲームタイマー** | `components/home/StudyTimers.vue` | `home.jsp` の `timer-card` | **ブラウザ内（localStorage）** |
| 学習サマリ（KPI・レーダー・一覧） | `components/home/StudyOverview.vue` | `english.jsp` のダッシュボード | 仮データ |
| **行動タイムライン** | `components/home/ActionTimeline.vue` | `home.jsp` の `home-monitor-card`（`js/home_monitor_timeline.js`） | **仮データ**（学習状況モニターのサンプル） |
| **最近3日 上網状況（時間帯別）** | `components/home/NetUsageChart.vue` | `home.jsp` の `net-usage-card` | **実データ**（`NET_プロキシ通信履歴情報`） |
| **直近単語テスト情報統計** | `components/home/WordTestStats.vue` | `english.jsp` の同名カード（Chart.js） | **仮データ** |

## 2. タイマー（2026-09-12 実装）

2.0 は残り秒数と実行状態をサーバー（`COM_タスク時間管理情報`）に保存し、
`api/home/getStudyClock` / `updateTimerSeconds` / `updateTimerStatus` で複数ウィンドウを
同期していた。2.1 は当分サーバーを持たず、**ブラウザ内（localStorage）＋タブ間同期**で
同じ動きにする（ユーザーの指定）。

- 実装: `features/home/homeTimers.ts`（純粋な計算）＋ `features/home/useTimers.ts`（接続）
- 既定は 2.0 と同じ **学習 30 分 / 休憩 15 分 / ゲーム 30 分**
- 学習タイマーだけ時間設定（5 / 10 / 15 / 20 / 30 / 40 / 60 分＋カスタム 1〜720 分）
- 開始・停止（停止しても残りはそのまま。もう一度 開始 すると続きから）
- 0 になると止まる（2.0 と同じ。0 では開始できない）
- 保存した時刻との差から残り時間を計算し直すので、**再読み込みしても続きから**動く
- 別タブの変更は `storage` イベントで取り込む
- 2.0 の「タスク完了で休憩・ゲームが増える」仕組みは、学習ステータス（タスク）機能が
  未移行のため入れていない。アラート音（2.0 は学習タイマーのみ）も音源が無いため未実装。

## 3. 行動タイムライン（仮データ）

2.0 は `api/study-monitor/search`（viewMode=snapshot）の**スナップショットの AI 分析結果**を
`buildSegments` で区間にして描いていた（同じ結果が続くところはまとめ、1 枚の担当は最大 90 秒）。

2.1 は録画取込（batL02）と AI 分析（batL03）が未実装で API が無いため、
**学習状況モニター画面と同じサンプルデータ**（`features/study-monitor/studyMonitorMock.ts`）で
2.0 と同じ見た目を再現している。画面にも「サンプルデータ」と明記している。

- 対象日と時間帯（既定 08:00〜23:59）で絞り込む
- 分析結果は 学習中 / 休憩中 / ゲーム中 / 離席 / 判定不能（未分析はグレー）
- 目盛りは時間帯の広さに応じて 30 分 / 1 時間 / 2 時間
- 下部に「n 枚 / n 区間｜学習時間 hh:mm（x.x%）」

API ができたら `studyMonitorMock` の呼び出しを差し替える。

## 4. 最近3日 上網状況（実データ）

2.0 の `api/proxy/getRecentUsageSummary` 相当を 2.1 に追加した。

- API: `GET /api/user/net-access-logs/hourly-summary?terminalName=&kind=`
  - 返す形は 2.0 と同じ `{ days, buckets, totalCount, terminalName, kind }`
    （`days` は新しい順で [0] が今日、`buckets` は 3 日 × 24 時間の件数）
- 実装: `NetAccessLogMapper#hourlySummary`（`date_trunc('hour', "受付日時")` で集計）＋
  `NetAccessLogServiceImpl#hourlyUsage`（3 日 × 24 時間の枠に詰め直す）
- 対象は 2.0 と同じく**通過した通信だけ**（応答状態コードが NULL または 0 以下）
- 区分（サイトの 0.勉強 / 1.通常 / 2.休憩 / 3.ゲーム）で絞るときは、承認済み・有効なサイトの
  判定方法（末尾一致 / 先頭一致 / 完全一致 / 含める）で接続先ホストを突き合わせる
- 端末は `NET_端末コントロール情報` の登録端末から選ぶ（端末名称の完全一致）
- 画面は SVG で 3 日 × 24 時間の棒を描く（Chart.js は使わない。凡例に日ごとの合計）

## 5. 直近単語テスト情報統計（仮データ）

2.0 は `english.jsp` で Chart.js の複合グラフ（棒＝単語数 / 破線＝時間(分) /
バブル＝中断回数 / 赤い破線＝最低時間 25 分。平日と週末で棒の色を変える）を描いていた。
2.1 は **単語テストのテーブルがまだ無い**ため、同じ形の仮データ
（`homeMockData.ts` の `buildWordTestDays`。直近 30 日）を SVG で描いている。
単語テスト機能を移行したら実データに差し替える。

## 6. 検証

| 種別 | 場所 |
|---|---|
| 単体（タイマーの計算・接続） | `frontend/pc-web/tests/home-timers.spec.ts`（14 件） |
| 部品（上網状況） | `frontend/pc-web/tests/home-net-usage.spec.ts`（3 件） |
| 部品（行動タイムライン） | `frontend/pc-web/tests/home-action-timeline.spec.ts`（4 件） |
| 部品（単語テスト統計） | `frontend/pc-web/tests/home-word-test.spec.ts`（4 件） |
| 画面全体 | `frontend/pc-web/tests/home.spec.ts` |
| 実 DB（時間帯別集計） | `backend/user-api/src/test/java/com/study21/user/net/NetAccessLogRepositoryTest.java` |
| 業務ルール（時間帯別集計） | `.../net/NetAccessLogServiceImplTest.java` |
| ブラウザ E2E | `tmp/e2e/e2e-home-student.mjs`（`tmp/e2e/run-home-student-e2e.sh` で実行。34 項目） |

E2E が見るもの: 3 枚のタイマー（既定時間・開始・停止・時間設定・localStorage に残る）、
行動タイムライン（区間・色・目盛り・時間帯の絞り込み・状態の行・サンプルの注記）、
上網状況（24 時間 × 3 日の棒・区分/端末の選択肢・実データの件数・区分で絞ると減る）、
単語テスト統計（30 日の棒・折れ線・最低時間ライン・バブル・凡例・集計・サンプルの注記）。

## 7. 未実装・今後

| 項目 | 補足 |
|---|---|
| タイマーのサーバー保存 | 2.0 は `COM_タスク時間管理情報` に保存し、複数端末・複数ウィンドウで同期していた。2.1 はブラウザ内のみ（端末をまたぐと別々） |
| タイマーのアラート音 | 2.0 は学習タイマーのみ音を鳴らしていた。音源が無いため未実装 |
| タイマーと端末モードの連動 | 2.0 の `home.js` は学習タイマーの実行に合わせて端末モードを変えていた（`BAT_R04` など）。2.1 は未実装 |
| 学習ステータス（タスク） | 2.0 はタスク完了で休憩・ゲームの時間が増えた。タスク機能ごと未移行 |
| 行動タイムラインの実データ | batL02（録画取込）・batL03（AI 分析）の実装後（`docs/STUDY_MONITOR.md` §3） |
| 単語テスト統計の実データ | 単語テスト（`TRN_単語テスト情報` 等）の移行後 |
| 天気・学習スケジュール | 2.0 の `home.jsp` にあった天気カードと Google カレンダーは今回の対象外 |
