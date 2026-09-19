# バッチの実行スケジュール（2.1）

2.0 は batR03 / batR04 の 23:30 / 06:30 が**コードと通知文に固定**で、batL02 は専用の
5 分スケジューラ（`BatL02FiveMinuteScheduler` + `BatL02ScheduleGuard`）、batL03 は定義の
`loopEveryMinutes=5` だった。2.1 はこの 4 つを**1 つの統一スケジューラ**で扱い、
実行時刻・実行間隔・ずらしを `COM_設定情報` で変えられるようにする。

- 対象: `batR03`（インターネット利用終了）/ `batR04`（利用開始）/
  `batL02`（学習モニター動画取込・スナップショット切出）/ `batL03`（スナップショット AI 分析）
- 実装: `backend/admin-api/src/main/java/com/study21/admin/schedule/`
- DDL: `database/バッチ/TBL_BAT_スケジュール状態情報.sql`
- 移行: `database/移行/MIG_BAT_スケジュール設定_20260919.sql`、
  `database/移行/MIG_BAT_スケジュール設定適用時刻_20260919.sql`（設定の適用時刻の列）、
  `database/移行/MIG_BAT_スケジュール設定版_20260919.sql`（計画バージョンの列）、
  `database/移行/MIG_BAT_実行履歴_元実行ID_20260920.sql`（復旧のやり直し関係の列と一意索引）
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
- **読み込みは何も書かない。** 適用時刻と計画バージョンは設定値の保存と**同じトランザクション**で
  書く（下の「設定の適用時刻」）。読み込みの途中で補って書くと、書けたかどうか分からないまま
  「新しい設定＋古い適用時刻」が残り、過去の計画実行点を実行しかねない。
- **3 つの表は 1 つのスナップショットで読む**（`MyBatisScheduleConfigLoader#load` は
  `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`）。設定値・有効／無効・適用時刻は
  別の表にあり、読み込みの途中で設定の保存（コミット）が入ると、既定の `READ COMMITTED` では
  **文ごと**にスナップショットを取るため「設定値は新しい・適用時刻は古い」という混ざった版を
  読みうる。PostgreSQL の `REPEATABLE READ` はトランザクションで 1 つのスナップショットなので、
  3 クエリでも混ざらない（単一 SQL にしない理由: 種類の違う行を UNION して Java で振り分ける形になり、
  既存の Mapper と SQL ログの形を崩すため）。
- **1 回の判断は 1 枚のスナップショット**（`SchedulePlanGuard#decide` はスナップショットを引数で受ける）。
  検査は「その回に固定した 1 枚」、復旧は「そのパスの 1 枚」、実行直前は「そのとき最新の 1 枚」。
- 失敗したときは**前の有効なスナップショットを残す**（消さない）。再試行は
  **30 秒 → 60 秒 → 120 秒 → 240 秒 → 300 秒（上限）**の退避。退避中は DB を引かない。
  失敗が続く間のログは抑止する（1 回目だけ WARN、以降は DEBUG と件数）。

### 2 種類の「退避」を混ぜない（DB が読めない／設定が無い）

| 種類 | 何が起きているか | 退避の単位 | 消えるとき |
|---|---|---|---|
| **読み込み失敗** | DB に触れない（接続不能・タイムアウト） | **全体**（30→60→120→240→300 秒） | 読み込みに成功したとき |
| **設定が無い／不正** | DB は読めたが、そのタスクの設定が無い・値が不正 | **タスクごと**（30→60→120→240→300 秒） | **そのタスクの設定が使えるようになったとき**だけ |

- 読み込みに**成功しても**「設定が無いタスク」の回数は**消さない**（消すと退避が 30 秒に戻り、
  30 秒ごとに DB を引き続ける）。**使えるようになったタスクだけ**回数を消す。
- 托底は**足りないタスクぶんをまとめて 1 回**（1 回の読み込みで**全タスクの状態**が更新される）。
  タスクごとに 1 回ずつ読みに行かない（`ScheduleConfigService#ensureUsableConfig`）。
- 設定がそろっているときは、30 秒の検査は**メモリだけ**を見る（周期的な DB 読み取りを増やさない）。
- 利用者が設定を保存したときは**いつでも即座に読み直す**（退避中でも待たない）。
- ログは**退避の段階ごとに 1 行**（毎回の検査で同じことを出さない）。
- 管理画面（設定画面の実行スケジュール）には、タスクごとに**「動かない理由・何回連続か・
  次にいつ確認するか」**（`fallbackMessage` / `fallbackFailures` / `nextFallbackCheckAt`）と、
  上部に**「設定が無いバッチがある」案内**（`configMissingMessage`）を出す。

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

発行はロックで直列化し、**要求ごとの通し番号**で守る。

- 読み込みを始めるときに**その要求の番号**を取り、**読み込みと発行の両方に同じ番号を持たせる**。
  「いま発行済みの番号」と比べるのはこの番号（`requestSequence.get()` を発行時に読むと、
  遅れて終わった古い読み込みが「自分が最新だ」と誤認して、新しい設定を捨ててしまう）。
- **実際に DB を読まなかった要求**（メモリの托底で読まずに済んだ等）は、**発行済みとして記録しない**
  （記録すると本当の最新の読み込みが「古い」と判定されて捨てられる）。
- 失敗した読み込みは版を進めない（前の有効なスナップショットのまま）。

### 設定の適用時刻と計画バージョンを**同じトランザクション**で保存する

利用者が時刻・間隔・ずらし・有効／無効を変えたとき、**その変更時刻を「設定の適用時刻」として記録**し、
**適用時刻より前の計画実行点は実行しない**（`TaskSchedule#canRunAt` /
`previousRunnablePointAtOrBefore`）。例: 22:00 に終了時刻を 23:30 → 21:00 に変えても、
**その日の 21:00 はもう過去**なので実行しない（ネットワークを即座に止めない）。次の 21:00（翌日）から効く。

- 記録するのは `ScheduleTimingRecorder`。**設定値を保存するトランザクションの中**（コミット前）で
  `BAT_スケジュール状態情報` の `設定適用日時` と `設定版`（計画バージョン）を書く。
  同じトランザクションなので、コミットできたかロールバックしたかのどちらかしか残らない
  （「新しい設定＋古い適用時刻」で過去の点を実行する、という食い違いを作らない）。
- 入口は 3 つで、**すべてこの記録を通る**（規則を入口ごとに分けない）:
  1. 設定ページの保存（`SettingsServiceImpl` → `SettingSaveTransactionHook` → `ScheduleSettingSaveHook`。
     **値が変わった設定だけ**を渡す）
  2. バッチの有効／無効の切替（`BatchServiceImpl#updateActive` の `@Transactional` の中）
  3. （将来）実行設定を変える入口を足すとき
- 計画バージョン（`設定版`）は保存のたびに 1 つ進む。再起動後は
  「いま読んだ設定が保存時の版と同じか」をこの値で判定できる。設定画面にも出す
  （`configEffectiveFrom` と `planVersion`）。
- **実行設定が実際に変わったときだけ**更新する（`TaskSchedule#sameTiming` で比較）。
  AI モデルなど**無関係な設定を保存しただけ**では動かさない（過去の補償を止めない）。
- DB を**直接**書き換えた場合（アプリの保存経路以外）は計画バージョンが進まない。このときは
  記録せず、**メモリだけで**「いまから新しい設定」として扱い、警告ログを残す
  （再起動すると判断をやり直す。設定画面の【設定を再読み込み】でも同じ）。
- 適用時刻は**タスクごと**で**再起動しても引き継ぐ**（メモリが空でも「変更前の古い点」を実行しない）。
- 「設定を変えたから実行しない」と「サービス停止のあいだに取りこぼしたから補執行する」は**別物**。
  停止中の取りこぼしは今までどおり復帰時に補執行する（適用時刻は付かない＝`null`）。
- 設定画面の**「次回実行時刻」も同じ規則で出す**（`nextRunnablePointAfter`）。
  画面とスケジューラの判断が食い違わないようにするため。

## 5. スケジューラ（`BatchScheduleScheduler`）

- `@Scheduled(fixedDelay = 30 秒)`（`study21.batch.schedule.check-interval-ms` で変更可）。
  起動 10 秒後に 1 回目。検査は**メモリのスナップショット 1 枚**だけを見る。
- 各タスクの**「いま以前で最後に到来した計画実行点」1 点**を確保（claim）する。
  「秒が 0 のときだけ」のような判定はしない（取りこぼすため）。何度取りこぼしても
  **最新の 1 点だけ**なので、一括の補跑にはならない。
- **設定の適用時刻より前の点は実行しない**（無効と同じ扱いで**計画だけ進める**）。
- 確保できたら**実行記録（待機中）を作り**、業務は `BatchScheduleExecutor`（別スレッド、
  既定 1 本）に渡す。**検査のスレッドは ffmpeg・AI・端末切替を待たない**。
- 無効なタスクは**計画だけ進める**（`advanceWithoutRun`。実行記録は残さない）。
  有効に戻したときに古い計画実行点を実行しないため。
- 設定が無い・不正なタスクは実行しない（托底を試し、それでも駄目なら理由つきでログ）。
- 実行してよいかの判定は**すべて `SchedulePlanGuard` に一本化**する（下の「共通の判定」）。
  検査・復旧・実行直前で同じ規則を使う（同じ判定を 3 か所に書かない）。

### 共通の判定（`SchedulePlanGuard`）— 検査・復旧・実行直前で同じ規則

`decide(taskCode, plannedAt, now, stage)` が 1 つの計画実行点について判定し、3 つの結果を返す:

| 結果 | 意味 | 検査（30 秒） | 復旧（起動時） | 実行直前 |
|---|---|---|---|---|
| `RUN` | 実行してよい | 確保して投入 | 実行し直す | 実行する |
| `WAIT` | 今は実行しないが**無効と決めつけない** | 点を確保せず次の検査へ | **閉じずに保留**（次の起動で再判定） | スキップとして記録 |
| `SKIP` | 実行しない（理由つき） | 計画だけ進める | **スキップとして閉じ、理由を残す** | スキップとして記録 |

見るもの（利用者の指示）: **現在時刻 / 設定の版 / 適用時刻 / 有効・無効 / タスクの実行状態**。

- **種別 R（batR03 / batR04）は 1 つのネット状態**: 「いま以前で最後に到来した点」だけが `RUN`。
  それより古い点（逆向きの操作）は `SKIP`（理由に「より新しいネット状態の切替（…）があるため」）。
- **種別 L（batL02 / batL03）**: 最新の点だけが `RUN`。古い点は `SKIP`（最新の 1 点に合并）。
  ただし**実行直前の再検証だけは 1 周期ぶん許す**（待ち行列で少し待っただけで実行できなくならない）。
- もう一方のネット切替が**実行中**なら検査では `WAIT`（点を確保しない＝次の検査で再挑戦）。
  実行直前では「いま有効なはずの状態」を適用する（古い逆向きの操作の結果を残さない）。
- 設定を**まだ読めていない**とき（起動直後・DB を読めなかった）は `WAIT`。
  **無効と決めつけて実行記録を閉じない**（設定が読めたあとにもう一度判定する）。
  復旧（`Stage.RECOVERY`）では**設定が無い・不正も `WAIT`**（閉じない）: 「いま実行できない」だけで、
  設定を直せば実行できるため。無効・適用時刻より前・新しい点に追い越された、は `SKIP`（閉じる）。

### 二重実行を防ぐ（永続的な確保）

`BAT_スケジュール状態情報` に**バッチごとに 1 行**（最後に確保した計画実行点）を持ち、
確保は 1 文で原子的に行う（`INSERT ... ON CONFLICT DO UPDATE ... WHERE 最終予定日時 < 新しい点`）。
確保と実行記録は**同じトランザクション**。落ちれば両方巻き戻り、次の検査でやり直す。

- **`lastClaimed`（メモリの早見）はコミット後にだけ進める**。ロールバックしたのに「確保済み」と
  覚えると、**その計画実行点が永久に飛ばされる**（漏執行）。コミットできたときだけ更新する。
- 再起動・多重起動・ポーリングのゆらぎでも**同じ計画実行点は 1 回だけ**。
- JVM のメモリロックには依存しない（メモリは DB を叩く回数を減らす早見だけ）。
- 実行記録には `予定時刻`（計画実行点）と `起動種別`（`R` / `L`）が入る。

### 前回が終わっていないとき

同じタスクの前回がまだ未完了なら、**重ねて実行せず**、その回を**スキップ**として記録する
（`前回の実行（実行ID=…）が終わっていないためスキップしました。`）。積み上げない。

### 再起動の復旧（`BatchExecutionRecovery`）

起動時に、**未完了（待機中・実行中）の実行を復旧する**。残したままだと次の実行が
「前回が実行中」と見なされて永久に走らない。**待機中（QUEUED）と実行中（RUNNING）で扱いを分ける**。

#### 段階を持ち、**再起動しなくても自動で続く**

| 段階 | 意味 | すること |
|---|---|---|
| `PENDING_DISCOVERY` | 起動の**遺留リストと境界**をまだ読めていない（DB が読めない等） | 30 秒の検査のたびに、退避（30→60→120→240→300 秒）しながら読み直す |
| `PENDING_REVIEW` | 遺留リストは読めた（**この時点で固定**）。設定が読めない等でまだ判定できない行が残っている | 30 秒の検査のたびに、**同じリスト**を判定し直す |
| `COMPLETED` | すべて処理した | **以後は DB を引かない**（無駄な問い合わせをしない） |

- 続きを進める入口は 2 つ: **30 秒の検査**（`BatchScheduleScheduler#runOnce` の先頭）と、
  **設定が読めるようになった直後**（設定の保存・管理者の再読み込み。`retryPendingRecoveryNow`）。
  後者は退避を待たずにその場で判定し直す（「設定を直したのに最大 5 分待たされる」を避ける）。
- 処理は**1 本に直列化**し（起動イベント・周期検査・再読み込みが同時に来ても 1 件ずつ）、
  処理が確定した行はリストから外す（**同じ行を 2 回処理しない**）。
- 起動の順序は **設定の読み込み → この復旧 → 起動時バッチ**（`@Order` で明示）。
  逆にすると「設定が読めていない」で判定を保留してしまい、読み込みも 2 回になる。

#### 復旧の境界（**実行記録の帰属**で決める。**このプロセスの実行は絶対に拾わない**）

- 実行記録には、**それを作ったプロセスの起動識別子**（`起動識別子` 列・
  `ProcessRunId`）が入る。挿入の直前に `BatchExecutionRunIdInterceptor`（MyBatis）が
  **すべての入口**（自動スケジューラの確保・起動時バッチ・画面の【再実行】・復旧のやり直し）に
  自動で刻む（入口ごとに設定する書き方にしない。足し忘れると復旧が自分の実行を誤認する）。
- 復旧は `findLeftoversExceptRunId(自分の識別子)` で
  **「起動識別子が NULL（この列が無かった頃の行＝互換）または自分と違う」**未完了の実行だけを引く。
- これにより「最初の読み込みが失敗し、その間に現在のプロセスが実行を作り、再試行でそれを
  遺留と誤認する」「最大ID は読めたが遺留リストの読み込みが失敗し、境界が広がる」という
  穴が**構造的に**ふさがる（ID の大小や時刻では判定しない）。
  判定の直前にもう一度帰属を見て、自分の実行はリストから外す（二重の守り）。
- 履歴の `起動識別子` が NULL の行は互換規則で遺留として扱う（この列を入れる前の記録）。
  **移行（列の追加）を先に、新しいアプリを後に配備する**。

| 復旧前の状態 | 何が起きていたか | 復旧のしかた |
|---|---|---|
| `QUEUED`（まだ業務が始まっていない） | 確保と実行記録は済んだが、業務が始まる前に落ちた（＝**実行していない**） | `RUN` なら**失敗にしない**。**同じ実行IDのまま実行器に渡し直す**（実行記録を捨てて作り直さない＝履歴が増えない）。`SKIP` なら `SKIPPED` として閉じ、理由を残す。`WAIT` なら**閉じずに保留**する |
| `RUNNING`（業務の途中で落ちた） | 業務が途中まで進んだ可能性がある | **無条件に再実行しない**。まず `FAILED` として閉じ、**`RUN` のときだけ**（＝その点がいま有効な計画）、**新しい実行記録**（`起動種別=RETRY`）で 1 回だけやり直す。`SKIP` なら閉じるだけ（結果不明のままやり直さない） |
| カタログに無いタスク（旧バッチ・手動実行） | — | `FAILED` として閉じるだけ（勝手に再実行しない） |

- 判定は**スケジューラと同じ `SchedulePlanGuard`**。ただし 1 パスでは**1 枚のスナップショット**を
  使う（行ごとに設定を取り直さない）。
- **計画実行点の新しい順**に処理する。R の未完了が両方あるときは、**新しい方だけ**が `RUN` になり、
  古い方（逆向きの操作）は `SKIPPED` として閉じる。
- 実行の直前にも**もう一度**判定する（`BatchScheduleExecutor`）。このときは
  **そのとき最新の 1 枚**を取り直す（待機中に設定が変わっていれば新しい設定で判断する）。
  無効なら理由を残して**スキップ**する（実行記録は残す）。

#### 実行記録の入口と、帰属の刻み方（すべて `BatchExecutionRunIdInterceptor` が刻む）

| 入口 | 実装 | 実行記録の作り方 |
|---|---|---|
| 自動スケジューラ（30 秒） | `BatchScheduleScheduler#runOnce` → `ScheduledTriggerStore#claimAndRecord` | 計画実行点を確保して `QUEUED` を作る |
| 起動時バッチ | `BatchStartupRunner` → `BatchServiceImpl#runOnStartup` → `execute` | `RUNNING` を作ってから業務を実行 |
| 画面の【再実行】 | `BatchController#rerun` → `BatchServiceImpl#rerun` → `execute` | 同上（種別 C は不可） |
| 復旧のやり直し | `BatchExecutionRecovery` → `ScheduledTriggerStore#recoverRunningExecution` | 旧実行を閉じて `QUEUED` を作る（1 トランザクション） |

#### 1 パスは「整理」と「投入」の 2 段階（古い記録を先に閉じる）

判定しながら実行器へ渡すと、**同じタスクの古い記録が未完了のうちに新しい記録が走り出し**、
業務側の「前回の実行が終わっていないのでスキップ」（`BatchServiceImpl#runQueued`）に当たって
**本来実行すべき記録が失われる**（例: batL02 の古い A と最新の B があり、B が A のためにスキップされ、
そのあと A が「計画が古い」で閉じられる）。そこで 1 パスを 2 段階に分ける:

1. **第 1 段階（判定と整理）** … そのパスの**1 枚のスナップショット**で全遺留を判定し、
   実行しない記録は理由つきで閉じ、有効な記録は**投入待ちとして集めるだけ**（実行器へは渡さない）。
   実行中（RUNNING）は `recoverRunningExecution` で「閉じる＋やり直しを作る」を 1 トランザクションで行い、
   **コミット後に**やり直しを投入待ちへ入れる。
2. **第 2 段階（投入）** … 投入待ちを実行器へ渡す。**同じタスクに未決の遺留**
   （保留・閉じられなかった・復旧トランザクションが失敗した）が残っているタスクは
   **今回は投入せず持ち越す**（投入すると冒頭のスキップで失われるため）。未決が片付けば次で投入する。
   他のタスクは進める（1 つのタスクの失敗で全体を止めない）。

- **投入待ちは復旧の追跡（`pendingDispatches`）に残す**（実行器へ渡すまで、または明確に終わったと
  分かるまで外さない）。実行中から作ったやり直しは**このプロセスの記録**なので
  「前のプロセスの遺留」の検索では二度と出ない → **この追跡が唯一の持ち主**で、
  次パス・次回起動まで責任を持つ。**局所変数（判定結果のリスト）だけに持たせない**:
  - 復旧トランザクションが成功してやり直しを作ったら、**先に追跡へ入れ、そのあと元の実行IDを
    遺留リストから外す**（順序を逆にすると、間で照会が失敗したときに新しい記録が誰の追跡にも入らない）。
  - 第 2 段階は**追跡の複製**を回す（回しながら集合を触らない）。判断は 3 状態:
    **待機中**（投入してよい）／**終了済み**（投入しないで追跡から外す）／
    **照会できず**（**「無い」「終わった」と解釈しない**。追跡に残して次のパスでやり直す）。
  - 同じタスクの未決が残っている間、投入は持ち越す（何パス続いても失わない）。
  - `executor.submit` が例外でも追跡に残す。**実行器が引き受けた**か、**記録が終了済みと
    確認できた**ときだけ外す。
  - 復旧の完了は「遺留リストと追跡の両方が空」のときだけ（画面の保留件数もその和）。
- 投入の直前に**記録がいまも待機中か**を確かめ、実行済み・閉じられていれば投入しない（二重実行しない）。
  実行の直前には**そのとき最新の 1 枚**で再検証する（待機中に設定が変わっていれば見送る）。
- 待ち行列があふれたときの入り直しは**実行器の既存の仕組み**（`retryPendingSubmissions`）に任せる
  （復旧側に別の投入経路を作らない）。
- 同じ実行IDの二重実行は、実行側の最後の砦（`runQueued` は**待機中の記録だけ**を実行する）でも防ぐ。

#### 「閉じる＋やり直しを作る」は**1 トランザクション**（`ScheduledTriggerStore#recoverRunningExecution`）

1. 旧実行を**未完了のときだけ**閉じる（条件つき UPDATE。他の復旧と同時でも閉じられるのは 1 つ）
2. 既にやり直しがあればそれを使う（**元実行ID** で引く）
3. 無ければやり直し（`QUEUED`・`元実行ID` つき）を作る
4. 計画表への紐付け（`最終実行ID`）を更新する
5. **コミット後**に実行器へ投入する（コミット前に投入すると、ロールバックしたのに実行が始まる）

- 二重の防止は DB 側で 2 段: 条件つき UPDATE ＋ **`元実行ID` の部分一意索引**
  （`INSERT ... ON CONFLICT DO NOTHING`）。**メモリのロックだけに依存しない**。
- コミット後・投入前に落ちても、やり直しは `QUEUED` のまま残るので**次の起動の復旧が拾う**。
- 投入（待ち行列）があふれたときは**記録を閉じない**。次の検査で入り直しを試し、上限まで
  入らなければ失敗として閉じる（`BatchScheduleExecutor#retryPendingSubmissions`。
  入り直しを待っている間に落ちても、記録は `QUEUED` のまま残るので次の起動が拾う）。

再実行してよい根拠は**業務の冪等性**である: batR03 / batR04 は端末モードの**設定**（同じモードを
二度書いても同じ）、batL02 は取込済みファイルの**重複除外**（`alreadyImported` と `ON CONFLICT`）、
batL03 は `最新版フラグ` の張り替え。**同じ計画実行点が二重に走らない**ことは DB の確保が保証する。

第一版は **admin-api が 1 インスタンス**で動く前提（多重起動する構成にするときは、
他インスタンスが実行中の行を閉じてしまわないよう条件を見直すこと）。

## 6. 取りこぼしと復帰の規則

- **L（batL02 / batL03）**: 何点取りこぼしても**最新の 1 点だけ**を実行し、あとは通常の周期に戻る。
  batL03 は「時間がずれているから L02 は済んでいる」と決めつけず、**スナップショットの
  完成状態**（`切出状態コード='CREATED'` かつ `最新版フラグ='1'` の分析行が無い）で対象を選ぶ。
- **R（batR03 / batR04）は 1 つのネット状態として扱う**: 2 つは**同じ状態に対する逆向きの操作**なので、
  別々に補執行すると「開始したのに止まっている」という**誤った状態**を作りうる。
  復帰時は**いま以前で最後に到来した 1 点（＝現在有効なはずのネット状態）だけ**を実行し、
  それより古い点（＝対になる逆向きの操作）は**実行せずに計画だけ進める**
  （**再起動の復旧でも同じ**: 前夜の停止が残っていても、実行するのは朝の開始だけ。
  古い方はスキップとして理由つきで閉じる）。
  例: 23:30〜06:30 の間に停止していた場合、復帰後に実行されるのは **06:30 の開始**だけ。
  最新の点が「無効」または「設定の適用時刻より前」なら、**古い点も実行しない**。
  もう一方のネット切替が**実行中**のときは、その回を**見送る**（点を確保しない＝次の検査で再挑戦。
  逆向きの操作が同時に走らない）。
- **手動操作を上書きしない**: 計画時刻より**後に端末が更新されている**ときは切り替えを見送り、
  理由を実行履歴に残す（`端末が計画時刻（…）より後に更新されているため、手動操作を上書きしない…`）。
  この判定は**更新の出所で区別する**: `更新元コード` が `batR03` / `batR04` の行は**このバッチ自身の
  更新**なので手動操作に数えない（数えると、前回の実行時刻が計画時刻より後になり、
  **次の切替をいつまでも見送ってネット状態が切り替わらない**）。出所が NULL の行は
  「利用者・不明」として**手動操作に数える**（上書きしない側に倒す）。
- **「停止後の補償」と「計画の変更」を区別する**: 計画（時刻・間隔）を変えても過去の補償は起きない
  （適用時刻より前の点は実行せず、次に到来する計画実行点から適用）。有効／無効を切り替えても同じ。
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

## 7-2. 移行と配備の順序（実行記録の列）

実行記録の復旧に必要な列は**移行スクリプトで足す**（`CREATE TABLE IF NOT EXISTS` は
既にある表に列を足さない）。

| 移行スクリプト | 足すもの | 何のため |
|---|---|---|
| `MIG_BAT_実行履歴_元実行ID_20260920.sql` | `元実行ID`（NULL 可）＋ **部分一意索引** `UQ_BAT_実行履歴_元実行ID` | 復旧のやり直しは 1 つだけ（`insertRetryIfAbsent` の `ON CONFLICT` が依存） |
| `MIG_BAT_実行履歴_起動識別子_20260920.sql` | `起動識別子`（VARCHAR(40)・NULL 可）＋ 遺留検索の索引 | 復旧の境界（実行記録の帰属） |

- どちらも**冪等**（`ADD COLUMN IF NOT EXISTS` / `CREATE ... INDEX IF NOT EXISTS`）。
  既存の実行記録は**変更しない**（既存行の `起動識別子` は NULL のまま＝互換規則で遺留として扱う）。
- `元実行ID` に**重複がある**ときは、一意索引を作る前に**中止して件数を報告**する
  （履歴は削除も変更もしない。利用者が内容を確認してから再実行する）。
- **配備順序**: ① 移行（列と索引を足す。アプリはまだ新しくなくてよい）→ ② 新しいアプリを配備
  （`起動識別子` を書き始める）。逆順（先にアプリ）だと、列が無いため実行記録の挿入が失敗する。
- 新規環境は `database/バッチ/TBL_BAT_バッチ実行履歴情報.sql` が同じ形を作る
  （移行後と一致することをテストで確認: `BatchExecutionMigrationTest`）。

## 7-3. 実 DB 統合試験の環境（**専用の使い捨て DB**）

バッチの実 DB 統合試験は、**日常の開発 DB には繋がない**。専用のテスト DB を指す URL が
環境変数で渡されたときだけ動き、無ければ**スキップ**する（理由を出して落とさない）。

```bash
tmp/tools/study21-batchtestdb.sh start     # 使い捨ての PostgreSQL（127.0.0.1:55433）＋スキーマ＋初期データ
STUDY21_TEST_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55433/study21_test \
STUDY21_TEST_DATASOURCE_USERNAME=postgres \
  tmp/tools/mvn-test.sh test               # 実 DB 統合試験はこちらで実行される
tmp/tools/study21-batchtestdb.sh stop      # 止める（使い捨てなので消してもよい）
```

- 試験側は `@ActiveProfiles("testdb")`（`src/test/resources/application-testdb.properties`）で
  データ源を切り替え、**自動運転の 3 つのスイッチ**（§7-4）で起動時の副作用を止める
  （「遅延で避ける」ではなく明示的に無効。テストは業務の入口を明示的に呼ぶ）。
- 実業務のハンドラ（動画取込・AI・プロキシ起動）は**代役**にして、外部への副作用を出さない
  （MyBatis・起動識別子のインターセプタ・トランザクションの経路は本物のまま通す）。
- 試験が作った記録は `BatchTestExecutionCleanup` で**自分が作った実行IDだけ**を閉じる
  （「最近の N 件」をまとめて触らない）。

## 7-4. 自動運転のスイッチ（テスト・手動運用のため）

自動で走る入口は 3 つ。どれも**既定は有効**（正常環境の挙動は変えない）。テストや、
手動でだけ動かす環境では**明示的に止める**（「間隔を遅くする」で逃げない）。

| 設定キー | 既定 | 止めると |
|---|---|---|
| `study21.batch.auto-run.schedule-enabled` | `true` | 30 秒ごとの検査（定期実行）を何もしない |
| `study21.batch.auto-run.startup-enabled` | `true` | 起動時バッチ（種別 S）を実行しない |
| `study21.batch.auto-run.recovery-enabled` | `true` | 起動時の自動復旧と、30 秒検査からの復旧の再試行をしない |

- **業務の入口はスイッチの影響を受けない**（`BatchService#runOnStartup`、
  `BatchExecutionRecovery#recoverInterruptedExecutions` を明示的に呼べば動く。テストはこれを利用する）。
  管理画面の【設定を再読み込み】からの `retryPendingRecoveryNow` も明示の入口として動く。
- 入口は**薄い適配層**（リスナー／`@Scheduled` の先頭でスイッチを見て、本体を呼ぶだけ）。
  Bean は常に生成されるので、Mapper・MyBatis のインターセプタ・トランザクションはそのまま使える。

## 8. 運用

- **確認**: バッチ一覧の「実行タイミング」と、設定画面の実行スケジュール表示
  （時計・版・次回実行時刻・**設定の適用時刻**・反映待ち）。
  実行設定を変えた直後は、その変更時刻が「設定の適用: …以降」として出る
  （この時刻より前の計画実行点は実行しないため、次回実行時刻もその次の点になる）。
- **DB を直接触ったとき**: 設定画面の**【設定を再読み込み】**（`POST /api/admin/batch/schedule/reload`）。
  托底はキャッシュの欠落を埋めるだけで、**外部からの DB 変更を自動で見つける仕組みではない**。
  直接書き換えた場合は計画バージョンが進まないため、**適用時刻を記録できない**（メモリだけで
  「いまから新しい設定」として扱い、警告ログを残す）。次の保存で記録される。
- **設定が無い・不正でバッチが動かないとき**: 設定画面の実行スケジュールに
  **理由・連続回数・次に確認する時刻**が出る（托底は 30→60→120→240→300 秒と退避する）。
  設定を直して保存すれば、**その場で読み直して**すぐ動く（退避を待たない）。
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
| 4-2 | **並行に更新しても最新の要求が失われない**（読み込みと発行に同じ要求番号を持たせる／読まなかった要求を発行済みにしない） | `ScheduleConfigServiceTest#concurrentRefreshDoesNotLoseTheLatestRequest` |
| 4-3 | 実行設定を**変えた直後に過去の点を実行しない**（22:00 に 23:30→21:00 でも止めない） | `ScheduleConfigServiceTest#changingTheTimeOnlyAffectsFuturePoints`・`#effectiveFromSurvivesRestart`・`#unchangedScheduleKeepsEffectiveFrom`、`BatchScheduleSchedulerTest#doesNotRunPointsBeforeTheConfigEffectiveFrom`・`#intervalTaskDoesNotRunAPointBeforeTheConfigEffectiveFrom`、実機（`check-batch-schedule.mjs` の「過去の点は実行しない」） |
| 4-4 | 夜間の停止から復帰しても**逆向きの操作を両方実行しない**（最新の 1 点だけ） | `BatchScheduleSchedulerTest#nightRestartAppliesOnlyTheNewestNetworkEvent`・`#restartAfterTheStopTimeAppliesTheStopOnly`・`#defersWhenTheSiblingNetworkTaskIsRunning` |
| 4-5 | 手動操作の保護が**更新の出所で区別**する（このバッチ自身の更新を手動と数えない） | `NetTerminalModeMapperTest#batchOwnUpdateIsNotCountedAsAManualChange`（実 DB）・`NetworkUsageServiceTest` |
| 4-6 | トランザクションが巻き戻ったら「確保済み」を残さない（漏執行しない） | `ScheduledTriggerStoreTest`（コミット後のみメモリを進める／巻き戻しは残さない） |
| 4-7 | **確保した直後に落ちた実行**を復旧する（待機中はやり直す・実行中は結果不明として 1 回だけ） | `BatchExecutionRecoveryTest`（15 件。1 件失敗しても残りを続ける）、実機（`tmp/e2e/check-batch-recovery.mjs`） |
| 4-8 | 復旧は**設定を読んでから**判定する（起動直後に空のスナップショットで誤判定しない。読めないときは保留） | `BatchExecutionRecoveryTest#loadsTheConfigBeforeDeciding`・`#keepsTheRowWhenTheConfigIsNotLoadedYet` |
| 4-9 | 夜間の停止が翌朝まで残っても、**復旧で実行されるのは朝の開始だけ** | `SchedulePlanGuardTest#theMorningStartIsTheOnlyValidNetworkEvent`・`BatchExecutionRecoveryTest#onlyTheNewestNetworkEventIsRecovered`、実機（古い方=SKIPPED／新しい方=実行） |
| 4-10 | 検査・復旧・**実行直前**で同じ判定を使う（待機中に無効化・設定変更・計画の古び） | `SchedulePlanGuardTest`（7 件）・`BatchScheduleExecutorTest`（5 件） |
| 4-11 | 設定値と「適用時刻＋計画バージョン」を**同じトランザクション**で保存する（片方だけ残らない） | `ScheduleSettingTransactionTest`（実 DB。ロールバックで 3 つとも元に戻る）・`ScheduleTimingRecorderTest`・`SettingsServiceImplTest#saveHookFailureRollsBackTheSave`、実機（保存で計画バージョンが 1 つ進む） |
| 4-12 | 計画バージョンは再起動しても同じ値が読める | `SchedulePlanMapperTest#configEffectiveFromIsSavedWithoutTouchingThePlan`（実 DB）・実機（保存→再起動→照会） |
| 4-13 | 設定が無いタスクの退避は**30→60→120→240→300 秒**で伸び、**読み込みに成功しても消えない**（使えるようになったタスクだけ消える） | `ScheduleConfigServiceTest#missingConfigBackoffGrowsAndIsKeptAfterSuccessfulLoads`・`#readFailureBackoffIsSeparateFromMissingConfig` |
| 4-14 | 設定が無いタスクが複数あっても、托底の読み込みは**1 回にまとめる** | `ScheduleConfigServiceTest#fallbackLoadsOnceForSeveralMissingTasks`・`#fallbackLoadsOnceForConcurrentMisses` |
| 4-16 | 起動時に**遺留リストが読めない**ときも、退避して読み直す（境界は同じものを使う） | `BatchExecutionRecoveryTest#discoveryFailureIsRetriedWithTheSameBoundary` |
| 4-17 | 起動時に**設定が読めない**ときは保留し、**再起動なしで**設定が読めた時点で判定して実行する | `BatchExecutionRecoveryTest#keepsAndRetriesWhenTheConfigIsNotLoadedYet`・`#missingConfigIsKeptWaitingDuringRecovery`、実機（保留→再読み込みで完了） |
| 4-18 | 保留の間に**無効・新しい点に追い越された**実行は、次の判定で理由つきで閉じる | `BatchExecutionRecoveryTest#disabledWhileWaitingIsClosedOnTheNextPass`・`#supersededWhileWaitingIsClosedOnTheNextPass` |
| 4-19 | 復旧が**このプロセスの実行**を拾わない（起動の境界） | `BatchExecutionRecoveryTest#executionsCreatedByThisProcessAreNotRecovered`、実機（境界=起動時の最大実行ID） |
| 4-20 | 同じ遺留の行を 2 回処理しない（周期検査から何度呼んでも投入は 1 回）／完了後は DB を引かない | `BatchExecutionRecoveryTest#eachLeftoverIsProcessedOnce`・`#doesNothingWhenNothingIsUnfinished`、実機（SQL ログの件数が増えない） |
| 4-21 | 「閉じる＋やり直しを作る」が**1 トランザクション**（挿入失敗で全部巻き戻る） | `ScheduleRecoveryTransactionTest#insertFailureRollsBackTheClose`（実 DB） |
| 4-22 | 2 つの復旧が同時でもやり直しは 1 つ（DB の一意性） | `ScheduleRecoveryTransactionTest#concurrentRecoveriesCreateOnlyOneRetry`（実 DB） |
| 4-23 | コミット後・投入前に落ちても、やり直しは次の復旧が拾える | `ScheduleRecoveryTransactionTest#retryRowSurvivesBeforeBeingDispatched`・`BatchExecutionRecoveryTest#retryRowCreatedByRecoveryIsPickedUpByTheNextStart` |
| 4-24 | 待ち行列があふれても記録を閉じず、入り直す（漏執行にしない） | `BatchScheduleExecutorTest#rejectedSubmissionIsRetriedInsteadOfFailing` |
| 4-25 | 設定の読み込みは**1 つのスナップショット**（途中で保存がコミットされても混ざらない） | `ScheduleConfigSnapshotConsistencyTest`（実 DB・同期バリア。`REPEATABLE_READ` が効いていることも見る） |
| 4-27 | 復旧は**実行記録の帰属**で遺留を選ぶ（最初の読み込みが失敗して自分の実行が増えても、自分の実行は遺留に入らない） | `BatchExecutionRecoveryTest#discoveryFailureIsRetriedWithTheSameOwnershipFilter`・`#executionsCreatedByThisProcessAreNotRecovered`・`#ownExecutionIsDroppedAgainAtReviewTime` |
| 4-28 | **すべての実行入口**（自動・起動時・手動・復旧のやり直し）で起動識別子が刻まれる | `BatchExecutionRunIdTest`（実 DB・4 入口） |
| 4-29 | 起動識別子が NULL の古い記録は互換規則で遺留として扱う | `BatchExecutionRunIdTest#legacyRowsWithoutRunIdAreLeftovers` |
| 4-30 | 移行: 古い構造から列と索引が増え、**既存の実行記録は変わらない／繰り返し実行できる** | `BatchExecutionMigrationTest#migratesOldStructureAndKeepsTheRows`（実 DB・出荷スクリプトをそのまま実行） |
| 4-31 | 移行: `元実行ID` に重複があれば**中止して報告**（履歴を消さない・直さない） | `BatchExecutionMigrationTest#abortsWhenSourceExecutionIdHasDuplicates` |
| 4-32 | 新規作成と移行後で表の形が同じ（列と部分一意索引がそろっている） | `BatchExecutionMigrationTest#theRealTableHasTheColumnsAndTheUniqueIndex` |
| 4-33 | 同じタスクの古い記録を**閉じてから**最新を投入する（最新がスキップされない） | `BatchExecutionRecoveryTest#closesTheOlderRecordBeforeSubmittingTheNewestOne`・`#closesTheOlderRunningRecordBeforeSubmittingTheNewestOne`・`BatchRecoveryDispatchIntegrationTest`（実 DB・実実行器・実 `runQueued`＋代役ハンドラ） |
| 4-34 | 未決の遺留が残る間は投入を持ち越し、片付いたら投入する（スキップで失わせない） | `BatchExecutionRecoveryTest#holdsSubmissionWhileTheSameTaskHasAnUnresolvedLeftover`・`#defersBothAndExecutesTheNewestAfterTheConfigRecovers`・`#dropsThePendingDispatchWhenTheRecordIsAlreadyTerminal` |
| 4-35 | 復旧トランザクション失敗でも次のパスで回復し、やり直しを二重に作らない | `BatchExecutionRecoveryTest#retriesWhenTheRecoveryTransactionFailsWithoutDuplicatingTheRetry` |
| 4-36 | 待機中でない記録は実行しない（同じ実行IDの二重実行を防ぐ） | `BatchRerunServiceImplTest`（実行済みの記録は実行しない）・`BatchExecutionRecoveryTest#dropsThePendingDispatchWhenTheRecordIsAlreadyTerminal` |
| 4-37 | テストの後始末は自分が作った記録だけを閉じる（他の記録を変えない） | `BatchExecutionCleanupIsolationTest`（実 DB） |
| 4-38 | 復旧トランザクション後の**照会失敗**でも新しいやり直しは追跡に残り、次で 1 回だけ投入する | `BatchExecutionRecoveryTest#keepsTheNewRetryTrackedWhenTheFirstLookupFails` |
| 4-39 | 同じパスで作った複数のやり直しは、1 つの照会失敗でも全部追跡に残る（片方は投入済みでも再投入しない） | `BatchExecutionRecoveryTest#keepsAllRetriesTrackedWhenOneLookupFails` |
| 4-40 | 未決が何パス続いても投入待ちを失わない／投入失敗は保留し、終了済みと分かったら外す | `BatchExecutionRecoveryTest#keepsThePendingDispatchAcrossSeveralPasses`・`#keepsOnSubmitFailureAndDropsWhenTerminal` |
| 4-41 | 自動運転のスイッチは自動の入口だけを止める（明示呼び出しは動く） | `BatchExecutionRecoveryTest#autoRecoverySwitchGatesOnlyTheAutomaticEntries`・`BatchScheduleSchedulerTest#autoRunSwitchDisablesThePeriodicCheck`・`#autoRunSwitchEnabledKeepsThePeriodicCheck`・`BatchStartupRunnerTest` |
| 4-42 | testdb では 3 つのスイッチが無効で、起動イベントでも起動時バッチ・自動復旧・定期実行が走らない（明示呼び出しでは動く） | `BatchRecoveryDispatchIntegrationTest#autoRunSwitchesAreDisabledInTestDb`・`#applicationReadyEventDoesNotRecoverButExplicitCallDoes`（実 DB・実リスナー） |
| 4-26 | 1 回の判断が**1 枚のスナップショット**だけを使う（途中で入れ替わっても混ざらない） | `SchedulePlanGuardTest#usesOnlyTheGivenSnapshot`・`#networkAndIntervalDecisionsUseTheGivenSnapshot`、`BatchScheduleExecutorTest#beforeRunUsesTheLatestSnapshot` |
| 4-15 | 設定が無い・不正のときは、画面に**理由と次に確認する時刻**を出す | `ScheduleConfigServiceTest`（`fallbackMessage` / `configMissingMessage`）・`batch-schedule-panel.spec.ts`（`task-fallback` / `schedule-config-missing`）、実機（設定画面） |
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
