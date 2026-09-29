# 写死在代码里、设定页面改不了的配置值（调查清单）

> **目的**：找出「看着像配置项、却直接写在代码里、设定页面改不了」的值，供后续判断哪些该提升为设定项。
> **调查日**：2026-09-27。**行号是调查时点的**（之后编辑过文件会偏移）。
> **来源**：backend(admin-api) / backend(user-api・common-core) / frontend(pc-web) 三份分头调查，
> 合并后由我复核（§2 的每一条我都亲自看过代码；§3〜§5 是分头调查结果，引用时可按行号确认）。
> **「能不能从设定页面改」的判定依据**：
> `database/設定/TBL_COM_設定項目_init.sql`、`TBL_COM_設定情報_init.sql`、
> `backend/admin-api/.../setting/SettingPageFields.java`、
> `frontend/pc-web/src/views/admin/system-settings/{Classroom,Geometry}AiSettingsSection.vue`
> （`study2SettingRuntime.ts` 里这两个 section 是 `external: true`，字段在各自的 .vue 里）。

---

## 0. 先说三个最要紧的结论

1. **「1 回の上限 50 語」删掉也没有意义。** 前面已经被两层切断了（§1）。
   实际生效的是**客户端 10 秒**与 **nginx 60 秒**，根本到不了 50 语这个上限。
2. **「画面上能改、保存也成功，但代码根本没读」的配置有 3 个**（§2 A-3〜A-5）。
   这是「改了却没效果」，比「缺配置」更难发现，也更危险。
3. **同一个含义的值散在多处、而且数字还不一样**（§6）。
   比起逐个提升为设定项，**先「集中到一处」**更有效。例：呼出履历的正文上限 200,000 出现在 6 个文件。

---

## 1. 「1 回の上限」原本有 4 层（以日语单词 AI 取得为例。**2026-09-27 已异步化**）

> **2026-09-27 的变更**：这个入口变成**受理**（写入 `QUEUED` 后立即返回），执行交给
> 后端的后台工作者（`JapaneseWordAiWorker`）。因此下表的 1〜3 **已经不再是「等待时间」的上限**
> （受理几十毫秒就返回）。剩下的限制是 4（受理上限 50 语）与 5（设定值）。
> 1〜3 作为「受理返回之前的等待上限」保留（**两处数值要一致**这条规则仍然有效）。
> 细节见 `docs/DECISIONS.md` 的「なぜ日本語単語の AI 取得を非同期（受付＋后台工作者）にしたか」。

| # | 层 | 实际值 | 位置 | 怎么改 |
|---|---|---|---|---|
| 1 | 浏览器（前端共通 HttpClient） | **10 秒**（默认） | `frontend/packages/web-shared/src/http/HttpClient.ts:5`（`DEFAULT_TIMEOUT_MS = 10_000`） | 调用方传 `timeoutMs` 即可覆盖 |
| 2 | 前端的**受理**调用 | **60 秒**（`AI_RUN_TIMEOUT_MS`。**2026-09-27 由 10 秒改为 60 秒**） | `frontend/pc-web/src/api/japanese.ts`（`runJpnWordAi` 传 `timeoutMs`） | 常量只有一处。要与 nginx 保持一致 |
| 3 | nginx（PC 的 `/api/admin/`） | **显式 60 秒**（默认本来也是 60 秒。**2026-09-27 改为显式写出**） | `deploy/docker/nginx.conf`（PC 与 Mobile 的 `/api/admin/` 共 2 处） | 改 `deploy/docker/nginx.conf`（需重建 web 镜像） |
| 4 | admin-api 的受理 | **只由设定值（10〜200）决定**。**2026-09-27 撤掉了 50 的天花板，也撤掉了代码侧的兜底值**（读不到设定就拒绝执行） | `backend/admin-api/.../japanesewordai/JapaneseWordAiLimits.java`（`limitOf`＝`requireGlobal`） | **设定页面**（唯一的上限来源） |
| 5 | 设定页面「1 回の最大単語数」 | **20 语**（范围 10〜200） | `COM_設定情報` 的 `BAT_C41_BATCH_MAX`〜`BAT_C44_BATCH_MAX` | **画面可改** |
| 6 | 后台工作者的巡查间隔／回收 | **5 秒 / 5 分** | `JapaneseWordAiWorker.java`（`INTERVAL_SECONDS` / `STALE_MINUTES`） | 代码（**候选**：把执行间隔做成设定项） |
| 7 | 画面的观察（轮询） | **每 3 秒・最多 600 次（30 分）** | `JapaneseWordView.vue`（`ACQUIRE_POLL_INTERVAL_MS` / `ACQUIRE_POLL_MAX_TICKS`） | 代码（画面自身的需要） |

- 画面上窗口显示的「1 回の受付」＝ **`min(4, 5)`**（`GET /api/admin/batch/japanese-word-ai/limits`）。
- **2026-09-27 异步化解决了什么**：以前会被 1〜3 的等待时间切断，出现「AI 还没跑完就报失败」。
  现在受理立即返回、执行由后台工作者继续，所以**画面的等待时间不再影响取得的成败**
  （进展看一览的「取得状態」标签）。
- **实际 DB 的值（2026-09-27 实测，直接 SELECT `COM_設定情報`）**：

  | 键 | 值 | 键 | 值 |
  |---|---|---|---|
  | `BAT_C41_BATCH_MAX` | 20 | `BAT_C41_THREADS` / `..._REQUEST_TIMEOUT_SECONDS` | 5 / 300 |
  | `BAT_C42_BATCH_MAX` | 20 | `BAT_C42_THREADS` / `..._REQUEST_TIMEOUT_SECONDS` | 3 / 120 |
  | `BAT_C43_BATCH_MAX` | 20 | `BAT_C43_THREADS` / `..._REQUEST_TIMEOUT_SECONDS` | 3 / 120 |
  | `BAT_C44_BATCH_MAX` | 20 | `BAT_C44_THREADS` / `..._REQUEST_TIMEOUT_SECONDS` | 3 / 120 |

- 最坏等待 ≈ ⌈语数 ÷ 线程数⌉ × 超时。20 语 / 5 并行 / 300 秒 → 最坏 20 分钟（A・B）。
- **上下限在 3 处不一致**（代码 `1..200` / 有効値 `10..200` / 画面滑块 `10..200`）：
  `JapaneseWordAiSettings.java:86`（`number(..., "_BATCH_MAX", 1, 200)`）对应实际 DB 的
  `COM_設定項目.有効値` 是 `10..200`。

---

## 2. 坏了・危险的（A）

| # | 值・状态 | 位置 | 会发生什么 | 建议 |
|---|---|---|---|---|
| ~~A-1~~ | ~~AI 取得的 HTTP 超时用了默认 10 秒~~ **→ 2026-09-27 已修**（传 `AI_RUN_TIMEOUT_MS = 60_000`） | `frontend/pc-web/src/api/japanese.ts` | （修改前）无论几语，10 秒就被前端切断 | 已修。超过 60 秒的取得在画面上仍会显示失败（后台会跑完） |
| ~~A-2~~ | ~~nginx 的 `/api/admin/` 没有 `proxy_read_timeout`~~ **→ 2026-09-27 显式写出**（PC・Mobile 两处都写 `60s`，与默认同值） | `deploy/docker/nginx.conf` | （修改前）隐式依赖默认 60 秒 | 已修。改值时要把前端常量一起对齐 |
| A-3 | `geometryAiMaxConcurrency`（画面上**有字段**，标签「スレッド数」，默认 1，1〜10） | 画面 `GeometryAiSettingsSection.vue:449`（默认 `:241,273`）／映射 `SettingPageFields.java:321`（`GEOMETRY_AI_MAX_CONCURRENCY`）。**后端没有任何读取处**（只有 `AiAssistGenerateStep.java:26` 的注释） | 改了**什么也不会发生**（AI 生图的并发数不变） | 要么接上实现，要么从画面与 DB 目录删除 |
| A-4 | `classroomAiViewScope`（画面上**有字段**，标签「閲覧範囲」，默认 `family`） | 画面 `ClassroomAiSettingsSection.vue:808-811`（默认 `:127`）。键定义在 `ClassroomAiSettings.java:96`（也进了 `KEYS` `:103`），但**没有读值的 accessor**（`viewScope` 的引用为零）。实际可见性判定硬编码在 `ClassroomServiceImpl.java:2110-2129` | 改了**谁能看还是不变** | 要么接上实现，要么删除 |
| A-5 | `CLASSROOM_AI_LANG_ZH` / `_JA` / `_EN` / `_ZH_EN` / `_JA_EN`（键在 `SettingPageFields.java:378-382` 与 DB 目录里，API 能写，但**画面上没有字段**） | 映射在 `SettingPageFields.java:378-382`。**后端也不读**（`ClassroomAiSettings.java:281-296` 从 `languageMode` 直接写死 `zh-CN` / `en-US` / `ja-JP`。`languageMode` 是每间教室的字段，不是设定页的语言码） | 作为设定**存在却没人读**（改了 STT 识别语言也不变） | 要么停止写死、改为读 `CLASSROOM_AI_LANG_*`，要么把键和目录项一起删掉 |
| A-6 | 呼出履历正文上限 `AI_BODY_LIMIT = 200_000` 在 **6 个文件里重复** | `JapaneseWordAiClient.java:37` / `StudyMonitorAiClient.java:51` / `AiFigureGenerateStep.java:64` / `AiAssistGenerateStep.java:53` / `ClassroomAiNoteStep.java:57` / `BatchServiceImpl.java:63`（`:503` 还会出现在 API 响应里） | 改值要改 6 处，漏一处历史的显示口径就不一致 | 收敛到 `common-core` 的一处（设定项化放在之后） |
| A-7 | 画面上固定显示的「検査間隔（秒）= 30」 | `backend/admin-api/.../controller/BatchScheduleController.java:103`（实际间隔来自属性 `study21.batch.schedule.check-interval-ms:30000`，`BatchScheduleScheduler.java:97`） | 改了属性后**画面显示会撒谎** | 把实际值返回给画面 |
| A-8 | `STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY`（画面可编辑） | 实际处理用的根目录来自属性 `study21.study-monitor.snapshot-root`（`StudyMonitorImportHandler.java:108` / `StudyMonitorAnalyzeHandler.java:121`）。画面的值**只用于启动时的警告日志**（同文件 `:265-277`） | 「明明设定了，却读别的目录」 | 要么真的用画面值，要么改掉字段名与说明 |
| A-9 | `CAMERA_NAME = "Xiaomi Camera 00"` | `StudyMonitorImportHandler.java:61` | 相机名写死在代码里（同页的 `STUDY_MONITOR_CAMERA_LOCATION` 却可设定，不对称） | 设定项化 |
| A-10 | `DIAGNOSTIC_TARGET_HOST = "192.168.0.100"` / `DIAGNOSTIC_VERSION = "2026-05-28-2335"` | `backend/admin-api/.../proxy/ProxyServerService.java:74,80` | **特定据点的 IP 与版本号残留在代码里**（诊断日志的分支） | 移到属性或设定项（不要把 IP 放进代码） |
| ~~A-11~~ | ~~英作文 AI 的**実行パラメータがコード固定**（`TEMPERATURE = 0.2` / `MAX_COMPLETION_TOKENS = 8192`、OCR は `0.0` / `4096`）~~ **→ 2026-09-27 已修**（设定项化） | 已修后：`backend/admin-api/.../englishessay/EnglishEssayAiSettings.java`（`ENGLISH_ESSAY_OCR_TEMPERATURE` / `_OCR_MAX_COMPLETION_TOKENS` / `_GRADING_TEMPERATURE` / `_GRADING_MAX_COMPLETION_TOKENS`。既定 0.0 / 4096 / 0.2 / 8192） | （修改前）設定ページ（`ENGLISH_ESSAY_*`）にキーが無く、モデルを変えたときに出力上限を調整できなかった（2026-09-27 に `max_tokens` で 400 を踏んだ前例がある） | 已修。**既定値とコードの兜底が残る**（§6-2 に注記） |
| A-12 | 英作文の画像**枚数・サイズの上限がフロントにも二重にある**（`8 枚` / `10MB`） | `frontend/pc-web/src/features/english-essay/components/EssayDropzone.vue:2-4`（サーバーは設定 `ENGLISH_ESSAY_MAX_IMAGES` / `_MAX_IMAGE_MB`） | 設定を変えても画面の事前チェックが古いままで、サーバーの日本語理由が出る前に弾かれる | **2026-09-27 に `GET /api/user/english-essays/limits` を足して解消**（画面が設定値を読む。取れないときは事前チェックせずサーバーに任せる） |

---

## 3. 设定项候选（B：backend / admin-api）

| 值 | 位置 | 决定什么 | 设定能改吗 | 建议 |
|---|---|---|---|---|
| ~~`MAX_WORDS_PER_RUN = 50`~~ **已删除**（2026-09-27） | `japanesewordai/JapaneseWordAiLimits.java` | 1 次受理的单词数 | **能**（设定页面「1 回の最大単語数」。10〜200） | **代码侧不再有上限定数**（`limitOf` 用 `requireGlobal` 读设定，读不到就拒绝受理）。已实现 |
| `_BATCH_MAX` 的校验范围 `1..200` | `japanesewordai/JapaneseWordAiSettings.java:86` | 同上上下限 | 不能（与有効値 不一致） | 对齐到有効値 `10..200` |
| `TEMPERATURE = 0.1` | `studymonitor/StudyMonitorAiClient.java:40` | batL03 判定的温度（注释写明「設定にキーは無い」） | 不能 | 设定项化（其他 AI 的温度都可设定） |
| `MAX_COMPLETION_TOKENS = 1024` | `studymonitor/StudyMonitorAiClient.java:48` | batL03 的输出上限 | 不能 | 设定项化 |
| `MAX_FILES_PER_RUN = 5` | `studymonitor/StudyMonitorImportHandler.java:58` | 1 次取入处理的视频数 | 不能 | 设定项化（重处理，运维上想调） |
| `TEMPERATURE = 0.2` | `classroomai/ClassroomAiNoteStep.java:59` | 授课笔记生成的温度 | 不能 | 设定项化 |
| `TEMPERATURE = 0.2` / `MAX_TOKENS = 2000` | `geometryai/AiAssistGenerateStep.java:57,59` | 作图助手的温度与输出上限 | 不能 | 设定项化（加到 `GEOMETRY_AI_ASSIST_*` 这一组） |
| `PROMPT_LIMIT = 20_000` | `geometryai/AiAssistGenerateStep.java:55` | 呼出履历里保留的 prompt 长度 | 不能 | 保留（日志容量的考虑） |
| `INTERVAL_SECONDS = 5` / `STALE_MINUTES = 5` | `geometryai/GeometryAiWorker.java:36,38` | 待执行任务的巡查间隔与滞留判定 | 不能 | 设定项化 |
| `STALE_GENERATION_MINUTES = 30` / `BINDING_GRACE` 30 分 | `classroomai/ClassroomAiPipelineService.java:64,360,363` | 「生成丢了」判定的宽限 | 不能 | 设定项化 |
| `BATCH_LIMIT = 200` | `geometryai/GeometryAiImageCleanup.java:33` / `classroomai/ClassroomRecordingCleanup.java:28` | 1 次清理删除的件数 | 不能 | 保留（安全阀） |
| `FALLBACK_RETENTION_DAYS = 30` | `geometryai/GeometryAiImageCleanup.java:35` | 图片保留天数的兜底值（实际值来自设定 `GEOMETRY_AI_IMAGE_RETENTION_DAYS`） | 画面可改（仅兜底值固定） | 兜底值（「设定漏了也照默认跑」的问题见 §6-2） |
| `FFMPEG_TIMEOUT = 10 分` / `FFPROBE_TIMEOUT = 30 秒` / `FFMPEG_OUTPUT_LIMIT = 2000` | `studymonitor/VideoFileTools.java:44,46,48` | ffmpeg 的等待时间与输出长度 | 不能 | 超时应设定项化 |
| `NEWEST_VIDEO_MIN_BYTES = 125MB` / `NEWEST_VIDEO_MIN_AGE = 5 分` | `studymonitor/VideoFileTools.java:50,52` | 「最新视频还没在写入」的判定 | 不能 | 设定项化（换相机・画质后需要调） |
| `ERROR_SAMPLE_LIMIT=3` / `SUMMARY_ERROR_LIMIT=140` / `ERROR_MESSAGE_LIMIT=2000` | `studymonitor/StudyMonitorAnalyzeHandler.java:101,103,104` | 批次结果消息里的错误显示 | 不能 | 保留 |
| `ERROR_DETAIL_LIMIT = 4000` | `batch/BatchServiceImpl.java:60` | 执行履历的错误详情长度 | 不能 | 保留（来自 DB 列长） |
| `Math.min(size, 100)` | `batch/BatchServiceImpl.java:443,468` | 执行履历／AI 呼出履历每页上限 | 不能 | 要确认（是否与画面的件数选项一致） |
| `MAX_SUBMIT_ATTEMPTS = 20` / `DEFAULT_QUEUE_CAPACITY = 16` | `schedule/BatchScheduleExecutor.java:49,52` | 投入重试次数与队列长度 | 不能 | 设定项候选 |
| `check-interval-ms:30000` / `initial-delay-ms:10000` / `skip-log-every:20` / `auto-run.schedule-enabled:true` / `workers:1` | `schedule/BatchScheduleScheduler.java:69,70,97,98`、`:68` | 调度器的周期・初次延迟・自动执行・并行数 | 不能（只能改 application.yml / 环境变量） | 「自动执行」与「并行数」最好在画面上可见 |
| `BACKOFF = {30s,60s,120s,240s,300s}` | `schedule/ScheduleConfigService.java:51-53` / `schedule/BatchExecutionRecovery.java:147-149` | 设定读取失败・复旧的重试间隔 | 不能 | 2 处重复 → 收敛到 1 处 |
| `BACKOFF_MS = {2_000,4_000,8_000}` | `japanesewordai/JapaneseWordAiStep.java:49` | AI 调用的重试间隔（次数可用 `RETRY_LIMIT` 设定） | 不能 | 「次数可设定、等待时间写死」这种不对称要对齐 |
| `sleepBackoff → min(8000, 2000<<attempt)` | `geometryai/AiFigureGenerateStep.java:457` | 同上（图形） | 不能 | 同上 |
| `LINE_MAX = 500` / `TOTAL_MAX = 20_000` | `geometryai/GeometryCommandValidator.java:38,40` / `GeometryAiAssistCommandPolicy.java:18,19` | 作图命令的单行长度・总长度 | 不能 | 2 处重复 → 收敛到 1 处 |
| `OBJECT_NAME_MAX = 200` | `geometryai/GeometryAiAssistPromptBuilder.java:19` | 助手 prompt 里的图形名长度 | 不能 | 保留 |
| `JPEG_QUALITY = 0.85f` | `geometryai/GeometryAiImageStorage.java:48` | 送给 AI 的裁剪图片画质 | 不能 | 要确认（影响 OCR 精度与成本） |
| `MIN_SENSES=1` / `MIN_EXAMPLES=2` / `MIN_COLLOCATIONS=2` / `MIN_CHOICES=5` / `MAX_CHOICES=7` / `REQUIRED_CORRECT_CHOICES=1` | `japanesewordai/JapaneseWordAiDtoMapper.java:60-66,73` | AI 输出结构的最小/最大件数（不足则校验失败） | 不能 | 与 prompt（可设定）成对，只有一边可改是危险的 |
| `TIMEOUT_SECONDS = 30` / `MAX_TOKENS = 16` / 温度 0.0 / `SILENCE_SECONDS = 0.2` | `ai/AiConnectionTester.java:37,39,72` / `ai/SttConnectionTester.java:47,49` | 设定画面【接続テスト】的条件 | 不能 | 保留（诊断用・把费用压到最小） |
| `defaultModel()` / `defaultUrl()`（STT） | `ai/SttConnectionTester.java:109-117` | 设定为空时的模型名・URL | 不能 | 要确认（是否在掩盖设定漏配） |
| `PROVIDER_DETAIL_LIMIT = 500` | `ai/AiErrorMessages.java:20` | 供应商错误正文的长度 | 不能 | 保留 |
| `FALLBACK_PROXY_PORT = 7777` | `proxy/ProxyServerService.java:68` | 端口设定非法时的值 | 不能（属性的兜底值） | 保留 |
| `SECOND_THRESHOLD = 0.750` | `studymonitor/StudyMonitorAnalyzeHandler.java:88` | 2.0 的二次判定阈值。**2.1 未使用**（为填 DB 的 NOT NULL 列） | 不能（不影响动作） | 保留 |

---

## 4. 设定项候选（C：backend / user-api・common-core）

| 值 | 位置 | 决定什么 | 设定能改吗 | 建议 |
|---|---|---|---|---|
| STT 语言码（zh→`zh-CN` / en→`en-US` / 其他→`ja-JP`、混合为 `en-US`） | `classroom/ClassroomAiSettings.java:281-296` | 识别语言（中文・英文授课的转写精度） | **不能**（`CLASSROOM_AI_LANG_*` 未被使用＝§2 A-5） | 停止写死，改为读设定 |
| `MAX_CHUNKS = 7_200` | `classroom/ClassroomModels.java:302` | 单次录音的最大分块数（20s×7200≒40h，与 `MAX_RECORDING_MINUTES=120` 矛盾） | 不能 | 与录音时长上限联动 |
| `MAX_PCM_BYTES`（约 6 小时） | `classroom/ClassroomSttStreamService.java:79` | STT 会话的 PCM 缓冲上限 | 不能 | 与录音时长联动 |
| `RETENTION_MILLIS = 1 時間` | `classroom/ClassroomSttStreamService.java:128` | 收尾状态（待保存文本・音频・完成回执）的保留 | 不能 | 设定项化 |
| `MAX_TAIL_AWAITS=3` / `MAX_FINALIZE_ATTEMPTS=4` / `MAX_RETAINED_PCM_BYTES`（最后 2 分钟） | `classroom/ClassroomSttStreamService.java:131,140,143` | 收尾的重试与回退长度 | 不能 | 设定项化 |
| `SAVE_ATTEMPTS=3` / `SAVE_RETRY_WAIT_MS=50` | `classroom/ClassroomSttStreamService.java:82,84` | DB 保存的重试 | 不能 | 实现细节（有需要再设定项化） |
| `AUTHORIZE_WINDOW_MILLIS = 5_000` | `classroom/ClassroomSttSocketHandler.java:69` | STT 权限复查间隔（＝每秒 DB 查询数） | 不能 | 作为性能与实时性的权衡设定项化 |
| `LATE_CHUNK_GRACE = 10 分` | `classroom/ClassroomServiceImpl.java:57` | 停止后仍接收收尾分块的宽限 | 不能 | 设定项化 |
| `IMPORT_MAX_MINUTES = 90` / `IMPORT_TEXT_MAX = 500_000` | `classroom/ClassroomServiceImpl.java:1940,1941` | mp3 取入时长与粘贴文本量 | 不能 | 设定项化（前端 `UPLOAD_MAX_MINUTES=90` / `PASTE_MAX_CHARS=500_000` 同值的双重定义） |
| `ORPHAN_MIN_AGE_MILLIS = 60 分` | `classroom/ClassroomServiceImpl.java:1786` | 孤立分块可以删除的年龄 | 不能 | 设定项化 |
| `END_SAMPLE_TOLERANCE_SECONDS = 2.0` | `classroom/ClassroomServiceImpl.java:568` | 申告的录音结束位置容差 | 不能（算法） | 保留（用注释写明理由） |
| `RUN_TIMEOUT = 10 分` / `OUTPUT_LIMIT = 2000` / `PROBE_SIZE = "32M"` / 探测 20 秒 | `classroom/ClassroomRecordingFfmpeg.java:36,38,40,199,220` | ffmpeg/ffprobe 的等待时间与日志上限 | 不能 | 设定项化（NAS 慢的时候会踩到） |
| `LOCK_STRIPES=64` / `STATUS_RETAIN=512` / `DURATION_TOLERANCE_SECONDS=1.5` | `classroom/ClassroomRecordingAssembler.java:44,46,48` | 锁数量・状态保留・时长校验容差 | 不能 | 内存类保留，容差要确认 |
| STT 请求下限 `Math.max(5, timeoutSeconds)` | `classroom/ClassroomSttHttpClient.java:103,128` | 设定小于 5 秒会被抬到 5 秒 | 画面可改（但有下限） | 在画面校验里写明下限 |
| DashScope 连接上限 `Math.min(timeout, +5)`＝**封顶 15 秒** / `CONNECT_TIMEOUT_SECONDS=10` / 收尾 `Math.min(5, Math.max(2, …))` | `common-core/.../stt/DashScopeAsrClient.java:137,336,131,333,48,382` | Alibaba STT 的超时（画面调大也只到 15 秒；收尾被夹在 2〜5 秒） | 不能（application.yml 也没有入口） | 不能设定就修不了「长音频超时」 |
| `BEFORE_XML_LIMIT=2_000_000` / `OBJECTS_SUMMARY_LIMIT=4_000` / `FAILURE_DETAIL_LIMIT=2_000` | `geometry/GeometryAiServiceImpl.java:48,50,52` | 助手请求的 XML 等截断长度 | 不能 | 设定项化（前端 `ASSIST_SERVER_SNAPSHOT_MAX` 同值的双重定义） |
| `ASSIST_HISTORY_DEFAULT_LIMIT=20` / `MAX_LIMIT=50` / `ASSIST_REASON_MAX=500` / `PROPOSAL_TAG_MAX=20` / 编号重试 20 | `geometry/GeometryAiServiceImpl.java:54,56,58,67,812` | 助手历史・理由・提案标签 | 不能 | 设定项化 |
| 中央裁剪 `0.15/0.15/0.7/0.7` | `geometry/GeometryAiServiceImpl.java:669-672` | `defaultCrop=center` 的比例 | 不能 | 要确认（与前端的 `clampCrop` 成对） |
| 提前拒绝的倍数 `maxEdge * 4` | `geometry/GeometryAiStorage.java:115` | 超过设定最大边的 4 倍直接拒绝 | 不能 | 设定项化 |
| `TAG_MAX_LENGTH=60` / `TAG_MAX_COUNT=20` / `MEMO_MAX_LENGTH=2000` | `geometry/GeometryServiceImpl.java:39-41` | 图形的标签・备注 | 不能 | 设定项化（前端也有同值的副本） |
| `NOTE_MAX=300` / `INSTRUCTION_MAX=500` / `CROP_MIN=0.05` / `DEFAULT_SIZE=20` / `MAX_SIZE=100` / `DEFAULT_TASK_LIMIT=20` / `MAX_TASK_LIMIT=50` | `geometry/GeometryAiModels.java:153,168,159,161,162,164,165` | 助手的输入长度・列表件数 | 不能 | 设定项化（前端也有副本） |
| `MAX_PDF_BYTES=200MB` / `MAX_COVER_BYTES=5MB` | `reading/ReadingFileStorage.java:31,33` | 书籍 PDF・封面的上限 | 不能（与前端 `api/reading.ts:90,91` 双重；与 nginx 的 `client_max_body_size 210m` 成对） | 设定项化并把 3 处对齐 |
| `DEFAULT_SIZE=20` / `MAX_SIZE=100` / `CATEGORY_NAME_MAX=50` | `reading/ReadingModels.java:41,42,45` | 书架分页与分类名长度 | 不能 | 件数是画面需要（保留），长度要确认 |
| `TAG_MAX=60` / `TAG_COUNT_MAX=10` / `TEXT_MAX=2000` / `DRAWING_MAX=100_000` | `reading/ReadingServiceImpl.java:49-53` | 书签标签・摘要/笔记・手写数据上限 | 不能 | 保留（输入校验） |
| 标记列表默认 50 / 上限 500 | `reading/ReadingServiceImpl.java:377` | 书签标记的分页（与书架的 20/100 不同） | 不能 | 收敛到 `ReadingModels` |
| `MAX_BODY_CHARS=200_000` / 字段截断 100 | `reading/ReadingDictionaryParsers.java:28` / `reading/ReadingDictionaryServiceImpl.java:153` | 词典 API 响应的截断 | 不能 | 保留 |
| `MAX_FILE_BYTES=20MB` / `MAX_FOLDER_DEPTH=4` | `document/DocumentService.java:20,23` | 资料文件上限与层级 | 不能 | 设定项化（前端 `DocumentFolderView.vue:135` 同值） |
| `MAX_FILE_BYTES=20MB` / `DEFAULT_LIMIT=300` / 上限 500 | `tempfile/TempFileService.java:18,20,39` | 临时文件上限与件数 | 不能 | 设定项化（前端 `api/tempfiles.ts:37` 的 300 同值） |
| `MAX_FILES_PER_TEST=10` / `MAX_FILE_BYTES=20MB` / 缩略图 500・200・50px | `testinfo/TestInfoService.java:26,28` / `testinfo/TestFileStorage.java:167` | 测试附件与缩略图 | 不能 | 设定项化（前端 `MAX_FILES=10` 同值） |
| `DEFAULT_SIZE=20` / `MAX_SIZE=100` | `todo/TodoServiceImpl.java:29,30` | TODO 列表分页 | 不能 | 保留 |
| 连接 8 秒 / 请求 10 秒 / `MAX_BODY_CHARS=400_000` | `linkclip/LinkClipMetadataFetcher.java:29,30,31` | 链接剪藏的 OG 取得 | 不能 | 设定项化 |
| `MAX_TAG_LENGTH=100` / 标题截断 300 | `linkclip/LinkClipService.java:29,253` | 链接剪藏的标签与标题 | 不能 | 保留 |
| `MAX_REVIEW_INTERVAL_DAYS=30` / `MASTERY_STEP=20` / `MASTERED_THRESHOLD=80` / `PROMPTED_CHOICE_COUNT=4` / 间隔下限 3 / 默认出题 10 | `japanese/JapaneseServiceImpl.java:59,60,61,84,1276,719` | 单词掌握度与复习节奏、**学习画面的选项数** | 不能 | **设定项化（教育方针上想调的值）** |
| `DEFAULT_SIZE=20` / `MAX_SIZE=200` / `MAX_QUESTIONS=50` | `japanese/JapaneseModels.java:32,33,35` | 日语列表分页与单次测试最大题数 | 不能 | `MAX_QUESTIONS` 设定项化（前端 `MAX_QUESTION_COUNT=50` 同值） |
| `MAX_SQL_LENGTH=4000` / `MAX_VALUE_LENGTH=200` | `common-core/.../logging/SqlLogFormatter.java:28,30` | SQL 日志的截断 | 不能 | 保留（在 logback 侧调整） |
| `maxAge(10 分)` | `controller/GeometryController.java:76` / `controller/GeometryAiController.java:121` | 图形・缩略图的 HTTP 缓存 | 不能 | 保留 |
| `HEARTBEAT_SECONDS=5` / `TIMEOUT_MILLIS=2h` | `game/GameEventPublisher.java:40,42` | 游戏对战的 SSE 心跳与断开 | 不能 | 设定项候选 |
| `MAX_EVENTS_PER_BATCH=500` / `ONLINE_WINDOW_MINUTES=10` | `browserext/BrowserExtensionModels.java:32,35` | 浏览器扩展的取入与「在线」判定 | 不能 | 设定项候选 |
| 令牌 TTL 30 分 | `account/PasswordResetServiceImpl.java:28` | 密码重置令牌的有效期 | 不能 | 作为安全方针保留（但要写明） |
| `@Value` 系（只能改 application.yml）: `study21.classroom-ai.connect-timeout-seconds:10` / `study21.classroom.admin-api-timeout-seconds:15` / `study21.reading.dictionary.timeout:5s` / 各 `storage-root` / `ffmpeg-command` / `study21.cors.allowed-origins` / `study21.proxy.port` / `study21.geometry-ai.stub` / `study21.batch.schedule.*` | 各 `application.yml` 与 `@Value` 处 | 超时・保存位置・CORS・stub | **设定页面不能改**（环境变量・yml 可以） | 不算「写死」，但如果运维只能碰设定页面，问题是一样的 |

---

## 5. 设定项候选（D：frontend / pc-web）

| 值 | 位置 | 决定什么 | 设定能改吗 | 建议 |
|---|---|---|---|---|
| ~~`ACQUIRE_MAX_WORDS = 50`~~ **已删除**（2026-09-27） | `views/japanese/JapaneseWordView.vue` | — | — | 画面不再有兜底值：`GET …/limits` 读不到就**禁用 AI 取得按钮**（设定是唯一的上限来源） |
| `PDF_MAX_BYTES = 200MB` / `COVER_MAX_BYTES = 5MB` | `api/reading.ts:90,91` | 书籍 PDF・封面上传上限（前端先挡） | 不能 | 与后端同值的双重定义（§6） |
| `UPLOAD_MAX_MINUTES = 90` / `PASTE_MAX_CHARS = 500_000` | `views/classroom/ClassroomListView.vue:108,109` | 音频文件时长上限与粘贴文本量 | 不能 | 与后端 `IMPORT_MAX_MINUTES=90` / `IMPORT_TEXT_MAX=500_000` 双重 |
| `CLASSROOM_MAX_SIZE = 100` / `CLASSROOM_TITLE_MAX = 200` | `api/classroom.ts:599,601` | 列表取得件数与标题长度 | 不能 | 后端 `ClassroomModels` 的副本 |
| `DEFAULT_IMAGE_MAX_MB = 10` | `views/geometry/GeometryAiView.vue:85` | AI 生图的图片上限兜底值（有设定 `geometryAiMaxImageMb` 时覆盖） | 兜底值不能改 | 确认与设定默认值一致（§6-2） |
| `NOTE_MAX = 300` | `views/geometry/GeometryAiView.vue:87` | 生图的「补充要求」字数 | 不能 | 与后端 `GeometryAiModels.NOTE_MAX` 双重 |
| `TEMP_FILE_LIMIT = 60` | `views/geometry/GeometryAiView.vue:89` | 临时文件选择窗口的件数 | 不能 | 设定项化 或 分页 |
| `DEFAULT_GEOMETRY_AI_TASK_LIMIT = 20` | `api/geometry-ai.ts:332`（使用 `:510` / `GeometryView.vue:296`） | AI 生图任务列表的默认件数 | 不能 | 设定项化 或 支持 `page` |
| `TAG_MAX_LENGTH=60` / `TAG_MAX_COUNT=20` / `TITLE_MAX=120` / `MEMO_MAX=500` | `views/geometry/GeometryDrawView.vue:82,83,92,93` | 图形的标签・名称・备注 | 不能 | 与后端同值的双重定义（§6） |
| `ASSIST_POLL_INTERVAL_MS=1500` / `ASSIST_POLL_TIMEOUT_MS=180_000` | `views/geometry/GeometryDrawView.vue:147,149` | 作图助手的轮询周期与总超时 | 不能 | 与设定 `geometryAiAssistTimeoutSeconds` 语义重叠 |
| `ASSIST_SERVER_SNAPSHOT_MAX=2_000_000` / `ASSIST_SNAPSHOT_MAX=200_000` / `ASSIST_LOG_LIMIT=20` | `views/geometry/GeometryDrawView.vue:142,135,130` | 作图 XML 上限与助手日志件数 | 不能 | 后端 `BEFORE_XML_LIMIT` 的副本。超限时【戻す】不可用 |
| `AI_TASK_POLL_INTERVAL_MS = 3_000` | `views/geometry/GeometryView.vue:298` | AI 生图任务列表的轮询 | 不能 | 与其他周期（1500 / 2000 / 5000）不统一 |
| `POLL_INTERVAL_MS=2000` / `DETAIL_POLL_TICKS=5` / `DETAIL_LIMIT=20` / `RECORDER_STOP_WAIT_MS=3000` | `views/classroom/ClassroomLiveView.vue:2028,2034,2661,1937`（`ClassroomDetailView.vue:484` 也是 2000） | 录音画面的轮询与显示件数 | 不能 | 轮询直接压服务器负载，最好统一成一个设定 |
| `RETRY_MAX_ATTEMPTS=5` / `RETRY_DELAY_MS=10_000` | `views/classroom/ClassroomLiveView.vue:524,523` | 分块发送重试 | 不能 | 设定项化（取决于教室网络） |
| `maxRecordingMinutes ?? 120` / `chunkSeconds ?? 20` | `views/classroom/ClassroomLiveView.vue:300,466` | 录音时长・分块长度的兜底值 | 实际值来自设定页面 | 「兜底值悄悄生效」的问题（§6-2） |
| `MAX_FRAMES_PER_TICK=50` / `DEFAULT_MAX_RECONNECTS=5` / `DEFAULT_FINISH_TIMEOUT_MS=20_000` / `DEFAULT_MAX_BUFFER_SECONDS=60` / `DEFAULT_MAX_RECOVERY_RANGES=200` / `DEFAULT_RECOVERY_RETENTION_DAYS=30` | `features/classroom/source-stream.ts:289,291,285,284,304,302` | 音频发送的控制 | 不能（一部分可用构造参数） | 复旧保留 30 天属于保留期，是设定项候选 |
| `timeoutMs = 600_000` | `api/geometry-ai.ts:499,554` | AI 生图・助手的 HTTP 超时（10 分） | 不能 | 与设定 `geometryAiRequestTimeoutSeconds`（30〜600 秒）语义重叠 |
| `timeoutMs = 30_000` | `api/classroom.ts:976` | 授课笔记手动生成的 HTTP 超时 | 不能 | 比设定 `classroomAiNoteTimeoutSeconds`（默认 120 秒）短，会**先超时** |
| `limit: query.limit ?? 300` | `api/tempfiles.ts:37` | 临时文件列表的默认件数 | 不能 | 分页 或 设定项化 |
| `PAGE_SIZES=[20,50,100]`（日语 ×2）/ `[24,48,96]`（图形）/ `15`（测试信息・站点管理）/ `20`（资料・链接剪藏）/ `24`（书架）/ `200`（标记） | `views/japanese/JapaneseWordView.vue:101`、`JapaneseTestView.vue:53`、`views/geometry/GeometryView.vue:62`、`views/testinfo/TestInfoView.vue:21`、`views/net/SiteManagementView.vue:51`、`views/document/DocumentListView.vue:17`、`views/linkclip/LinkClipView.vue:22`、`views/reading/BookReaderView.vue:105,109` | 每页件数 | 不能 | 各行其是，且与服务器 `MAX_SIZE` 不一致（§6） |
| `MAX_QUESTION_COUNT = 50` | `views/japanese/JapaneseTestView.vue:57` | 测试最大题数 | 不能 | 与后端 `MAX_QUESTIONS=50` 同值的双重定义 |
| `MAX_FILES = 10` | `views/testinfo/TestInfoFormDialog.vue:27` | 单次测试的附件张数 | 不能 | 与可设定的 `essayMaxImages`（1〜20）不一致 |
| `MAX_CARD_TAGS=4` / `MAX_CHIPS=6` / `CALENDAR_TASK_LIMIT=5` / `MAX_FOLDER_DEPTH=4` | `views/linkclip/LinkClipView.vue:20`、`views/document/DocumentFileChips.vue:17`、`views/todo/TodoView.vue:587`、`views/document/DocumentFolderView.vue:135` | 「只显示前 N 件」与层级 | 不能 | 显示需要可保留。`MAX_FOLDER_DEPTH` 属于业务结构，要留意 |
| `TTS_CHUNK_CHARS=120` / `TTS_RATE_MAX=2` / TTS 速度默认 | `features/reading/readingSpeech.ts:38,33,32-36` | 朗读的分割与速度 | 不能 | 设定项化（设计书里写了默认值） |
| `TIMER_DEFAULTS={study:30分,break:15分,game:30分}` / `STUDY_DURATION_OPTIONS` / `TIMER_MAX_SECONDS=12h` | `features/home/homeTimers.ts:34-38,43,47` | 首页计时器的初始值与选项 | 不能 | 交给每个用户的偏好（localStorage / 设定） |
| `WORD_TEST_MINIMUM_MINUTES = 25` | `features/home/homeMockData.ts:94` | 首页单词测试的达成线 | 不能 | 属于业务目标值，设定项化 |
| `POLL_INTERVAL_MS=5000` / `PRESENCE_INTERVAL_MS=5000` | `features/game/useGameMatch.ts:37,39` | 对战的轮询与在线心跳 | 不能 | 直接压服务器负载，统一成一个设定 |
| `SCHEDULE_INTERVAL_CHOICES=[1,5,10,15,30,60]` / `DEFAULT=5` | `features/batch/batchSchedule.ts:17,20` | 批次执行间隔的候选 | 不能 | 与后端 `ScheduleRuleCatalog.INTERVAL_CHOICES` 同值的双重定义 |
| `timeFrom:'08:00'` / `timeTo:'23:59'` | `views/study-monitor/StudyMonitorView.vue:58` | 学习监视器时间段初始值 | 不能（画面可临时改） | 与设定页的 `monitorVideoProcessing*Time`（06:30/23:30）不是一回事，容易混淆 |
| `MOCK_DELAY_MS=1400` / `MOCK_SAVE_DELAY_MS=700` | `features/japanese-demo/store/japaneseDemo.ts:55,57` | demo 的伪 AI 等待 | 不能 | demo 专用（会让人误以为「AI 只要 1.4 秒」，值得注记） |
| `maxRows ?? 500` / `registerUnitSizeInput ?? 20` | `features/japanese-demo/components/DemoWordGrid.vue:36` / `DemoWordNewView.vue:109` | demo 的行数上限 | 不能 | demo 专用 |
| `DEFAULT_WIDTH/HEIGHT=1180/900` | `features/japanese/popupWindow.ts:17,18` | 学习画面弹窗的大小 | 不能 | 保留（画面需要） |
| `CONVERT_TIMEOUT_MS=3000` | `features/geometry/geometry-ai-image.ts:20` | 图片→DataURL 的超时 | 不能 | 保留 |
| `MAX_SCALE=4` / 标记默认尺寸 / `ROTATION_MS=340` | `views/reading/BookReaderView.vue:112,119,120` / `views/document/DocumentImageViewer.vue:21` | 阅读・显示的需要 | 不能 | 保留 |

---

## 6. 重复与不一致（比设定项化更该先收拾）

1. **同一个值出现在多个文件**：
   - 呼出履历正文上限 `200_000` → **6 个文件**（§2 A-6）
   - `MAX_FILE_BYTES = 20MB` → 资料 / 临时文件 / 测试信息（3 处）
   - 作图命令的 `LINE_MAX` / `TOTAL_MAX` → 2 处
   - 调度器的退避表 → `ScheduleConfigService` 与 `BatchExecutionRecovery` 2 处
   - 前后端同一个数字：`MAX_QUESTIONS=50`、`MAX_FOLDER_DEPTH=4`、`MAX_FILES=10`、
     `PDF_MAX_BYTES`、`NOTE_MAX=300`、`TAG_MAX_*`、`BEFORE_XML_LIMIT`、
     `IMPORT_MAX_MINUTES=90`、`PASTE_MAX_CHARS=500_000`、各种 `MAX_SIZE`、
     `SCHEDULE_INTERVAL_CHOICES`
2. **「设定的默认值」与「代码的兜底值」成对存在**（`ClassroomAiSettings.FALLBACK_*`、
   `GeometryAiSettings.FALLBACK_*`、`AiFigureConfig.DEFAULT_*`、前端的 `?? 120` / `?? 20` / `?? 50`）：
   **把设定删掉就会悄悄按默认值跑**，所以设定漏配发现不了。
   - **例外（故意的，2026-09-27 利用者の指示）**：英作文 AI 的执行参数 4 项
     （`ENGLISH_ESSAY_OCR_TEMPERATURE` / `_OCR_MAX_COMPLETION_TOKENS` / `_GRADING_TEMPERATURE` /
     `_GRADING_MAX_COMPLETION_TOKENS`）在设定缺失・非法时**故意**落到
     `EnglishEssayAiSettings` 的既定（0.0 / 4096 / 0.2 / 8192。移行前の环境でも AI を止めないため）。
     ただし `requireSettings` は必须设定の欠落を実行前に例外にするので、**キー自体が DB に無いと
     その経路は止まる**（`database/移行/MIG_ENG_英作文_AI実行パラメータ設定_20260927.sql` で入れる）。
3. **上限随层不同**（§1）。在增加设定项之前，先按一个思路（语数 → 预估时间）把各层的值对齐。
4. **前端的选项与后端的上限不一致**：图形能选到 96 而后端是 100、
   日语单词能选到 100 而后端是 200、测试信息固定 15。
5. **同一含义有两个项**：`geometryAiAssistTimeoutSeconds`（设定）与
   `ASSIST_POLL_TIMEOUT_MS`（画面固定）、`classroomAiNoteTimeoutSeconds`（设定 120 秒）与
   `api/classroom.ts:976` 的 30 秒、`geometryAiRequestTimeoutSeconds`（设定）与
   `api/geometry-ai.ts` 的 600 秒。

---

## 7. 接着做的事（优先顺序）

1. ~~**修 §2 A-1 / A-2**（10 秒・60 秒）~~ **2026-09-27 已修**（统一为 60 秒）。
2. **收拾 §2 A-3〜A-5 的「无效设定」**（要么接上实现，要么把字段和键删掉）。
   A-7 / A-8 是同型问题（显示与实际处理不一致）。
3. **收敛重复常量**（200,000 / 20MiB / 命令长度 / 退避）。
4. **把运维想调的值做成设定项**：视频取入本数、`STALE_GENERATION_MINUTES`、
   3 种文件大小、`PROMPTED_CHOICE_COUNT`、`MAX_QUESTIONS`、`NEWEST_VIDEO_MIN_BYTES`、
   `MAX_CHUNKS`、`MAX_EVENTS_PER_BATCH`、ffmpeg 超时、STT 的超时上限（封顶 15 秒）。
5. ~~**大处理异步化**（设计书 §11 记为未实现）~~ **2026-09-27 已实现**：
   日语单词 AI 改成「只受理、后台跑、画面看进展」，§1 的上限讨论因此变轻。
   （图形 AI 的 run 仍是同步；如果需要，照同样的型做。）

> **2026-09-27 的进展记录**：
> 1. 最初只是调查（不改代码），随后按指示把 **A-1 / A-2 修掉**（AI 取得的 HTTP 等待时间统一为 **60 秒**）。
> 2. 之后又按指示把日语单词 AI **异步化**（受理＋働き手），并修掉了审查发现的：
>    设定键读错（`/limits` 一直显示 50、后台工作者一直 1 件/轮）、批次禁用未拒收、
>    「全部重新取得」的完成判定错用一览视图（改用新的 `GET …/progress`）、受理的并发重复入队。
> 3. 又把 AI 取得的**对象从「当前页」改成「检索条件匹配的全部单词」**（新增 user-api
>    `GET /words/ai-targets` 在服务端挑词），**一次受理上限改为设定值（10〜200）**，
>    撤掉了同步时代 50 语的天花板。
> 4. 仍然剩下：**A-3〜A-10**（「画面能改却不生效的设定」与「显示与实际不一致」），
>    以及 §3〜§6 的候选清单（未动手）。

---

## 8. 「这算不算设定项？」怎么查（供下次调查）

1. 设定项的定义在 3 处：
   - `database/設定/TBL_COM_設定項目_init.sql`（ページ区分＋設定キー＋有効値）
   - `database/設定/TBL_COM_設定情報_init.sql`（初始值）
   - `backend/admin-api/.../setting/SettingPageFields.java`（画面键 ↔ 页面/设定键）
2. 画面上有没有：看 `frontend/pc-web/src/views/admin/system-settings/` 的各 Section
   （`ClassroomAiSettingsSection.vue` / `GeometryAiSettingsSection.vue`）与
   `features/system-settings/study2SettingRuntime.ts` 的 `fieldKeys`。
3. **一定要看实际 DB 的值・有効値**（init SQL 是旧的，之后被移行 SQL 改过）：
   ```sql
   -- 实际值
   SELECT "ページ区分","設定キー","設定値" FROM public."COM_設定情報"
    WHERE "スコープ"='GLOBAL' ORDER BY 1,2;
   -- 有効値（画面滑块的范围）
   SELECT "ページ区分","設定キー","有効値" FROM public."COM_設定項目" ORDER BY 1,2;
   ```
   例：`BAT_C4x_BATCH_MAX` 在 init SQL 里是 `1..100`，但移行 SQL
   （`database/移行/MIG_COM_設定項目_有効値_スライダー範囲統一_20260918.sql:16`）之后，
   实际 DB 是 **`10..200`**。
4. **「有没有读取方」不能只看键的定义，要追到 accessor 的使用处**
   （§2 A-3〜A-5 都是「键在、画面也在，但没有读取方」）。
5. **读写设定的 API 不要用错**：`SettingsService#loadGlobalSettingFields()` 返回的是
   **画面字段键**（`c25BatchMax`），要用**设定键**（`BAT_C41_BATCH_MAX`）读时得用
   `SettingsService#findGlobal(ページ区分, 設定キー)`。
   （2026-09-27 实际踩过：`/limits` 一直返回 50、后台工作者一直 1 件/轮。）
