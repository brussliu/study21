# バッチの実行スケジュール（2.1）

2.0 は batR03 / batR04 の 23:30 / 06:30 が**コードと通知文に固定**で、batL02 は専用の
5 分スケジューラ（`BatL02FiveMinuteScheduler` + `BatL02ScheduleGuard`）、batL03 は定義の
`loopEveryMinutes=5` だった。2.1 はこの 4 つを**1 つの統一スケジューラ**で扱い、
実行時刻・実行間隔・ずらしを `COM_設定情報` で変えられるようにする。

- 対象: `batR03`（インターネット利用終了）/ `batR04`（利用開始）/
  `batL02`（学習モニター動画取込・スナップショット切出）/ `batL03`（スナップショット AI 分析）
- 実装: `backend/admin-api/src/main/java/com/study21/admin/schedule/`
- DDL: `database/バッチ/TBL_BAT_スケジュール状態情報.sql`
- 移行: `database/移行/MIG_BAT_スケジュール設定_20260919.sql`
- 業務（ハンドラ）: `com.study21.admin.network`（batR03 / batR04）、
  `com.study21.admin.studymonitor`（batL02 / batL03）

## 1. 3 つの「間隔」を混ぜない

| 概念 | 何を決めるか | 設定キー |
|---|---|---|
| **実行間隔・ずらし** | **バッチをいつ動かすか** | `STUDY_MONITOR_L02_INTERVAL_MINUTES` / `_OFFSET_MINUTES`、`STUDY_MONITOR_L03_*` |
| 動画処理時間帯 | **どの撮影時刻の動画を取り込むか** | `STUDY_MONITOR_VIDEO_PROCESSING_START_TIME` / `_END_TIME` |
| スナップショット間隔 | **動画から何秒ごとに画像を切るか** | `STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS` |

## 2. 設定（COM_設定項目 / COM_設定情報）

| 設定キー | 意味 | 初期値 | 有効値 |
|---|---|---|---|
| `NET_CONTROL_START_TIME` | batR04（利用開始）の実行時刻 | `06:30` | `HH:mm` |
| `NET_CONTROL_END_TIME` | batR03（利用終了）の実行時刻 | `23:30` | `HH:mm` |
| `STUDY_MONITOR_L02_INTERVAL_MINUTES` | batL02 の実行間隔（分） | `5` | 1/5/10/15/30/60 |
| `STUDY_MONITOR_L02_OFFSET_MINUTES` | batL02 のずらし（分） | `1` | 0〜間隔-1 |
| `STUDY_MONITOR_L03_INTERVAL_MINUTES` | batL03 の実行間隔（分） | `5` | 1/5/10/15/30/60 |
| `STUDY_MONITOR_L03_OFFSET_MINUTES` | batL03 のずらし（分） | `0` | 0〜間隔-1 |

- 初期値は **2.0 の実際の動きに合わせてある**: batL02 は毎時 `01/06/…/56` 分、
  batL03 は毎時 `00/05/…/55` 分、ネット利用は 23:30 終了 / 06:30 開始。
- 時刻は**常に Asia/Tokyo**（`ScheduleConfigService.ZONE`）。サーバーの OS タイムゾーンに依存しない。
- 設定画面は**Cron を編集させない**。実行間隔とずらしを選ばせ、実行時間の例・時計・
  次回実行時刻を表示する（`GET /api/admin/batch/schedule`）。

## 3. 有効／無効は 1 つだけ

有効／無効は **`BAT_バッチコントロール情報`（バッチ一覧のスイッチ）が唯一の正**。
設定画面にはスイッチを置かない（`BAT_スケジュール状態情報` もスイッチを持たない）。
2.0 は `COM_設定情報` の `BATCH_TASK_ENABLED_*` だったが 2.1 は移行済み。

## 4. メモリ上の設定キャッシュ（`ScheduleConfigService`）

30 秒ごとの検査で**設定テーブルを引きに行かない**ため、プロセス常駐のキャッシュを持つ。

- `ScheduleConfigSnapshot`（不変）を `AtomicReference` で**丸ごと差し替える**
  （検査の途中で設定が入れ替わって、古い間隔と新しい時刻が混ざらない）。
  版（version）・読み込み時刻・時計・タスクごとの規則・**状態（null で兼用しない）**を持つ。
- タスクの状態は `NOT_LOADED`（未読込）/ `LOADED`（有効）/ `MISSING`（未設定）/
  `INVALID`（設定不正）。**`MISSING` / `INVALID` のタスクは自動実行しない**
  （コードに隠れた既定値でネット制御を動かさない）。
- 読み込みのタイミングは 4 つだけ: **起動時 / 設定の保存後（コミット後）/ メモリに無いときの托底 /
  管理者の再読み込み**。托底はロック内で再確認して**同時に何本要求されても 1 回**だけ読む。
- 失敗したときは**前の有効なスナップショットを残す**（消さない）。再試行は
  **30 秒 → 60 秒 → 120 秒 → 240 秒 → 300 秒（上限）**の退避。退避中は DB を引かない。
  失敗が続く間のログは抑止する（1 回目だけ WARN、以降は DEBUG と件数）。

### 保存の順序（トランザクションの外で反映する）

1. 入力を検証する 2. DB を保存する（`@Transactional`）3. **コミット後**にキャッシュを更新する
4. 新しいスナップショットを発行して次回実行時刻を計算し直す。

**トランザクションのコミット前にキャッシュを触らない**（ロールバックしたのに実行設定だけ
変わっている、という食い違いを防ぐ）。反映は
`SettingPageController#saveSettings`（設定保存）と `BatchController#updateActive`（有効／無効）と
`BatchScheduleController#reload`（管理者の再読み込み）の**コミット後**から呼ぶ。

### 反映に失敗したとき

- DB は**巻き戻さない**（保存は済んでいる）。
- 画面には **「保存済み・実行設定への反映待ち（理由）。自動で再試行します。」** と出す
  （`ScheduleConfigReport#pendingMessage`）。
- **前の有効なスナップショットのまま動き続ける**（タスクは止めない）。
- 30 秒スケジューラが退避に従って自動で再試行する（即時の無限リトライはしない）。
- 画面で**いま効いている版・読み込み時刻・最後の失敗理由・次の再試行**が見える。

### 並行保存

発行はロックで直列化し、**要求の通し番号**で守る（自分より後に要求された更新が先に発行済みなら、
古い結果は破棄する）。「リクエストが終わった順」だけで上書きしない。

## 5. スケジューラ（`BatchScheduleScheduler`）

- `@Scheduled(fixedDelay = 30 秒)`（`study21.batch.schedule.check-interval-ms` で変更可）。
  起動 10 秒後に 1 回目。検査は**メモリのスナップショット 1 枚**だけを見る。
- 各タスクの**「いま以前で最後に到来した計画実行点」1 点**を確保（claim）する。
  「秒が 0 のときだけ」のような判定はしない（取りこぼすため）。何度取りこぼしても
  **最新の 1 点だけ**なので、一括の補跑にはならない。
- 確保できたら**実行記録（待機中）を作り**、業務は `BatchScheduleExecutor`（別スレッド、
  既定 1 本）に渡す。**検査のスレッドは ffmpeg・AI・端末切替を待たない**。
- 無効なタスクは**計画だけ進める**（`advanceWithoutRun`。実行記録は残さない）。
  有効に戻したときに古い計画実行点を実行しないため。
- 設定が無い・不正なタスクは実行しない（托底を試し、それでも駄目なら理由つきでログ）。

### 二重実行を防ぐ（永続的な確保）

`BAT_スケジュール状態情報` に**バッチごとに 1 行**（最後に確保した計画実行点）を持ち、
確保は 1 文で原子的に行う（`INSERT ... ON CONFLICT DO UPDATE ... WHERE 最終予定日時 < 新しい点`）。
確保と実行記録は**同じトランザクション**（落ちれば両方巻き戻り、次の検査でやり直す）。

- 再起動・多重起動・ポーリングのゆらぎでも**同じ計画実行点は 1 回だけ**。
- JVM のメモリロックには依存しない（メモリは DB を叩く回数を減らす早見だけ）。
- 実行記録には `予定時刻`（計画実行点）と `起動種別`（`R` / `L`）が入る。

### 前回が終わっていないとき

同じタスクの前回がまだ未完了なら、**重ねて実行せず**、その回を**スキップ**として記録する
（`前回の実行（実行ID=…）が終わっていないためスキップしました。`）。積み上げない。

### 再起動の復旧

起動時に、**未完了（待機中・実行中）の実行を失敗として閉じる**
（`BatchExecutionRecovery`、`BatchStartupRunner` より先に走る）。
残したままだと次の実行が「前回が実行中」と見なされて永久に走らない。
第一版は **admin-api が 1 インスタンス**で動く前提（多重起動する構成にするときは、
他インスタンスが実行中の行を閉じてしまわないよう条件を見直すこと）。

## 6. 取りこぼしと復帰の規則

- **L（batL02 / batL03）**: 何点取りこぼしても**最新の 1 点だけ**を実行し、あとは通常の周期に戻る。
  batL03 は「時間がずれているから L02 は済んでいる」と決めつけず、**スナップショットの
  完成状態**（`切出状態コード='CREATED'` かつ `最新版フラグ='1'` の分析行が無い）で対象を選ぶ。
- **R（batR03 / batR04）**: 古いイベントを機械的に全部は実行しない。**最後に到来した 1 点**だけを
  実行する。例: 23:30〜06:30 の間に停止していた場合、復帰後に実行されるのは **06:30 の開始**だけ
  （前夜の終了を今さら実行してから開始する、ということはしない）。
- **手動操作を上書きしない**: 計画時刻より**後に端末が更新されている**ときは切り替えを見送り、
  理由を実行履歴に残す（`端末が計画時刻（…）より後に更新されているため、手動操作を上書きしない…`）。
- **「停止後の補償」と「計画の変更」を区別する**: 計画（時刻・間隔）を変えても過去の補償は起きない
  （次に到来する計画実行点から適用）。有効／無効を切り替えても同じ（無効の間は計画だけ進む）。
- 手動実行（バッチ一覧の【再実行】）は**次の自動実行の計画を変えない**。

## 7. 業務の意味（2.0 と同じ）

| バッチ | 業務 |
|---|---|
| batR03 | 有効な端末を**停止モード（S）**へ一括切替 → **ネットワーク制御アプリ（プロキシ）の稼働を保証** → 通知文を作る |
| batR04 | 有効な端末を**通常モード（T）**へ一括切替 → プロキシの稼働を保証。T は許可サイトで判定されるモードなので**他のネット制限は迂回されない** |
| batL02 | 監視カメラの動画を取込み、**ffmpeg** でスナップショットを切り出す（`docs/STUDY_MONITOR.md`） |
| batL03 | 切出済みで未分析のスナップショットを **AI（vision）** で判定し、結果を書き戻す |

2.1 で**対象外**のもの（2.0 にあって 2.1 に無い）:

| 2.0 | 2.1 の扱い |
|---|---|
| batR03 の学習タイマー停止（`stopRunningTimersForInternetShutdown`） | 2.1 のタイマーは**ブラウザ内（localStorage）**でサーバーに状態が無い（`docs/HOME.md` §2・§6）。サーバー側で止める対象が無い |
| batR03 の LINE 通知 | 2.1 は**LINE 送信そのものが未実装**（設定キー `LINE_MESSAGING_*` だけ）。文面は作ってログと実行履歴に残す（送信を実装するときにそのまま使える） |
| batL03 の二次判定（精密） | 2.1 の設定カタログに二次判定のキーが無いため一次判定のみ（`二次判定要否='0'` / `二次分析状態コード='NOT_REQUIRED'`）。必要になったら設定キーと一緒に足す |

## 8. 運用

- **確認**: バッチ一覧の「実行タイミング」と、設定画面の実行スケジュール表示
  （時計・版・次回実行時刻・反映待ち）。
- **DB を直接触ったとき**: 設定画面の**【設定を再読み込み】**（`POST /api/admin/batch/schedule/reload`）。
  托底はキャッシュの欠落を埋めるだけで、**外部からの DB 変更を自動で見つける仕組みではない**。
- 実行は 30 秒周期で検査するので、計画時刻から**おおむね 0〜30 秒**で始まる。
- 手動実行（【再実行】）は有効／無効に関係なく今までどおり可能（種別 C は除く）。

## 9. 制限（第一版）

- **単一インスタンス前提**。`admin-api` を 2 つ以上動かす構成にするときは、
  (1) 起動時の未完了実行の復旧条件、(2) 設定変更の通知（他インスタンスのキャッシュ更新）、
  (3) 実行の跨インスタンス重複防止（確保は DB なので既に安全だが、復旧の条件は要見直し）
  の 3 点を設計し直すこと。
- スケジューラは Spring の `@Scheduled`（1 スレッド）で、業務は専用の実行器（既定 1 本）で走らせる。
  大きなスケジューラ製品（Quartz 等）は入れない（利用者の指示）。

## 10. 検証（どこで確かめているか）

自動テストは `backend/admin-api/src/test/java/com/study21/admin/schedule/`・`.../network/`・
`.../controller/`（新規分）と `.../batch/BatchRerunServiceImplTest`（スケジューラ実行の入口）。
実機は `tmp/e2e/check-batch-schedule.mjs`（設定の保存・再読み込み・検査・確保・実行）。

| # | 確かめること | テスト / 実機 |
|---|---|---|
| 1 | 起動後、複数回の 30 秒検査で設定テーブルを引かない | `ScheduleConfigServiceTest#loadsOnceAndThenServesFromMemory`、`BatchScheduleSchedulerTest#doesNotTouchTheLoaderWhenConfigIsInMemory`、実機（SQL ログの `ScheduleConfigMapper` 行数） |
| 2 | 設定の保存でキャッシュが更新され、再起動が要らない | `ScheduleConfigServiceTest#refreshPicksUpNewValues`、`SettingPageControllerTest#設定の保存後に実行スケジュールを反映する`、実機（保存→照会） |
| 3 | DB のロールバックでキャッシュが変わらない | `SettingPageControllerTest#保存が失敗したときは実行スケジュールに触らない`（検証エラー＝保存しない＝refresh を呼ばない） |
| 4 | 並行保存で古い設定が新しい設定を上書きしない | `ScheduleConfigServiceTest#concurrentRefreshesDoNotOverwriteWithOlderData`（要求の通し番号で古い結果を破棄） |
| 5 | メモリ欠落時に DB から托底して回填する | `ScheduleConfigServiceTest#fallbackLoadsOnceForConcurrentMisses` |
| 6 | 同時に欠落しても読み込みは 1 回 | 同上（ロック内で再確認。`loads == 1`） |
| 7 | 托底の失敗は退避で再試行し、毎回 DB を引かない | `ScheduleConfigServiceTest#keepsPreviousSnapshotWhenRefreshFails`・`#backoffGrowsAndIsCapped`・`#fallbackForMissingConfigBacksOff` |
| 8 | 設定が無い・不正なら隠れた既定値で実行しない | `ScheduleConfigServiceTest#missingOrInvalidConfigIsNotRunnable`、`BatchScheduleSchedulerTest#doesNotRunTasksWithoutConfig` |
| 9 | 保存は成功・反映に失敗したとき「待生效」を見せ、後で回復できる | `ScheduleConfigServiceTest#keepsPreviousSnapshotWhenRefreshFails`（`pendingMessage`）、`SettingPageControllerTest#実行設定の反映に失敗したら保存済み反映待ちを返す`、`BatchScheduleControllerTest#statusShowsPendingRefresh` |
| 10 | 有効／無効が 1 か所（`BAT_バッチコントロール情報`）で一致する | `ScheduleConfigServiceTest#enabledComesFromControlTable`、既存の `batch-management.spec.ts`（一覧のスイッチ）、実機（切替→照会） |
| 11 | 4 タスクが設定どおりに計画時刻＋0〜30 秒で動く | `BatchScheduleSchedulerTest`（計画実行点の計算）、実機（有効化→実行履歴） |
| 12 | 再起動・多重起動・ポーリングのゆらぎで二重実行しない | `BatchScheduleSchedulerTest#doesNothingWhenAlreadyClaimed`・`#doesNotRunWhenAnotherExecutionClaimedFirst`、`SchedulePlanMapperTest`（実 DB の確保 SQL。**移行適用後に有効**） |
| 13 | 前回が終わっていないときに重ねて実行しない | `BatchRerunServiceImplTest#scheduledExecutionIsSkippedWhenThePreviousRunIsStillRunning` |
| 14 | 跨午夜・時刻変更・停止補償・手動実行の規則 | `TaskScheduleTest`（定時・循環の計画実行点）、`BatchScheduleSchedulerTest#triggersOnlyTheLatestIntervalPoint`・`#disabledTaskOnlyAdvancesThePlan`、`NetworkUsageServiceTest#doesNotOverwriteNewerManualChange` |
| 15 | L02 が切り出したデータだけを L03 が分析する | L03 の対象選定 SQL（`切出状態コード='CREATED'` かつ `最新版フラグ='1'` の分析行が無い）＋ `StudyMonitorAnalyze*Test` |
| 16 | R03 / R04 が 2.0 の意味（端末切替＋ネット制御の適用）を保つ | `NetworkUsageServiceTest`（モード `S` / `T`、プロキシの稼働保証、上書きしない規則） |

時間に関わるテストは**時計を差し替えて**動かす（`ScheduleConfigServiceTest.MutableClock`、
`Clock.fixed`）。実際に待つのは実機の検査（30 秒周期）だけ。
