# 英作文AI添削 設計（2.1）

> 2.0 の `english_essay.jsp` ＋ `STY_英作文情報` / `STY_英作文画像情報` を土台に、
> **2.1 の要件「何度も添削して、历次を切り替えて見る」**を足した設計。
> 実装日: 2026-09-27。DB は適用済み（`database/英作文/TBL_ENG_*.sql`）。

## 1. 何をする機能か

1. 設問と答案の**画像を上げる**（ドラッグ＆ドロップ／臨時ファイル）。1 枚ずつ**トリミング**し、
   **設問画像／答案画像**を指定する（既定 8 枚・1 枚 10MB）。
2. **OCR（画像 → 文字）は同期**で実行し、結果を**手で直せる**（2.0 と同じ）。
3. 直した本文を保存し、**AI 添削を受付ける（非同期）**（2.0 と同じ。バッチ `batC12`）。
4. 添削が終わったら、一覧の【目】から**レポート全体**（得点・4 観点・修正ポイント・改善後の作文例）を見る。
5. **何度でも添削でき、回ごとに切り替えて見られる**（2.1 の新要件。2.0 は 1 列を上書きしていた）。

## 2. テーブル

### `ENG_英作文情報`（1 行 = 1 回の提出）
| 列 | 型 | 意味 |
|---|---|---|
| `英作文ID` | BIGSERIAL PK | |
| `利用者アカウントID` | BIGINT FK→`ACC_アカウント` CASCADE | 提出した利用者（**この人の作文だけが見える**） |
| `英検級` | VARCHAR(20) CHECK | `GRADE1` / `PRE1` / `GRADE2`。**観点の満点（8 か 4）がこれで決まる** |
| `題` / `題_中国語` | VARCHAR(200) | レポートの「作文タイトル」（日本語／中文） |
| `設問文` / `作文本文` | TEXT | OCR の結果を利用者が直したもの |
| `語数` | INTEGER | 本文から数えた値（一覧と検索のため） |
| `状態コード` | VARCHAR(20) CHECK | `A` = 有効 / `X` = 削除（論理削除。**履歴は残す**） |
| 監査列 | | `バージョン` / `登録者・更新者アカウントID` / `登録元・更新元コード` / `登録日時` / `更新日時` |

索引: `(利用者アカウントID, 登録日時 DESC)`（一覧・部分索引 `状態コード='A'`）、`(利用者アカウントID, 英検級)`。

### `ENG_英作文画像情報`（1 行 = 1 枚）
`英作文画像ID` PK／`英作文ID` FK CASCADE／`表示順`（`UNIQUE(英作文ID, 表示順)`）／
`画像区分` CHECK(`question`/`answer`)／`原本ファイル名`／`保存ファイル名`／`相対パス`／`MIMEタイプ`／
`ファイルサイズ`／**`認識テキスト`**（その画像の OCR 結果）／**`認識信頼度`**（0〜100）／`登録者`／`登録日時`。

実体は DB の外（§3）。OCR の**生の結果**はここに持ち、利用者が直した本文は `ENG_英作文情報` 側に持つ
（どちらも残すので「認識がどうだったか」を後から確かめられる）。
**同期 OCR（`POST /ocr`）の結果は画面が持っている**ので、保存（`PUT /english-essays/{id}` の `images[]`）で
`recognizedText` / `confidence` として送り、ここへ書く（2026-09-27 改修。以前は batC11 の回写のときだけ
書かれ、同期の経路では NULL のままだった）。**送らなかった欄の既存値は消さない**。

### `ENG_AI添削履歴情報`（1 行 = 1 回の添削。**2.0 には無い**）
| 列 | 意味 |
|---|---|
| `添削ID` | BIGSERIAL PK |
| `英作文ID` / `回数` | `UNIQUE(英作文ID, 回数)`。画面はこの**回数で切り替える** |
| `状態コード` | `QUEUED`（受付済）/ `RUNNING`（実行中）/ `SUCCEEDED` / `FAILED` / `CANCELED` |
| `英検級`・`題_日本語`・`題_中国語`・`設問文`・`作文本文`・`語数` | **そのときの写し**（あとで作文や級を直しても、出したレポートは動かない） |
| `総合得点` / `満点` | 一覧と絞り込み用 |
| `添削結果JSON` | JSONB。2.0 と同形のレポート（日/中の 2 言語・4 観点・修正ポイント・改善例） |
| `AI呼出履歴ID` | `BAT_AI呼出履歴情報`.`呼出履歴ID`（FK は張らない＝追記専用ログへの参照） |
| `失敗理由` | 人が読む日本語（成功なら NULL） |
| `開始日時` / `終了日時` | 実行の記録 |
| `登録者アカウントID` / `登録日時` | |

索引: `(英作文ID, 回数 DESC)`、**部分索引** `(状態コード, 登録日時) WHERE 状態コード IN ('QUEUED','RUNNING')`
（働き手の取件を小さく保つ）。

### 関係
```
ACC_アカウント 1 ─── n ENG_英作文情報 1 ─── n ENG_英作文画像情報
                                    └──── n ENG_AI添削履歴情報
```

## 3. 画像の実体

- 置き場: `<study21.english-essay.storage-root>/english-essay/{アカウントID}/{yyyyMM}/{uuid}.{ext}`
  （`相対パス` はこの規約の途中まで。**パスの遍歴（`..`）は弾く**）
  - **根（`storage-root`）は `english-essay/` の親**を指す（`相対パス` 側が `english-essay/…` で
    始まるため）。配備は `/app/data`（= ホストの `files/`）で、両サービスに同じボリューム
    `files/english-essay → /app/data/english-essay` を割り当てる。**根を `english-essay` 自身にすると
    1 段深く探して「実体がありません」になる**（2026-09-28 に実際に起きた）
- 上限: 設定 `ENGLISH_ESSAY_MAX_IMAGES`（既定 8）・`ENGLISH_ESSAY_MAX_IMAGE_MB`（既定 10）
- MIME: png / jpeg / webp（2.0 と同じ）。**WebP は JDK の ImageIO で寸法が取れない**ので、寸法チェックは
  png/jpeg だけ行う
- 注意: 実体はファイルなので、**環境（ホスト）ごとに置く必要がある**。見本データは画像を持たない（§7）

## 4. API

### user-api（作文の保管・配信。ログイン必須。**自分の作文だけ**）
| メソッド | パス | 用途 |
|---|---|---|
| GET | `/api/user/english-essays` | 一覧（`keyword`/`level`/`dateFrom`/`dateTo`/`page`/`size`）。各行に**最新の添削**（回・状態・得点）と画像枚数 |
| GET | `/api/user/english-essays/{id}` | 詳細（画像・**添削の历次すべて**。`report` は JSON のまま） |
| POST | `/api/user/english-essays` | 新規（級・題 日/中・設問・本文） |
| PUT | `/api/user/english-essays/{id}` | 更新（本文・題・級・**画像の並びと区分**。含めなかった画像は削除）。`images[]` の各要素に **`recognizedText`（任意）／`confidence`（任意・0〜100）** を載せると `ENG_英作文画像情報` の `認識テキスト` / `認識信頼度` を更新する（**省略した欄の既存値は消さない**） |
| DELETE | `/api/user/english-essays/{id}` | 論理削除（`状態コード='X'`） |
| POST | `/api/user/english-essays/{id}/images` | 画像 1 枚を上げる（multipart。`recognizedText` / `confidence` も任意で受け取る） |
| GET | `/api/user/english-essays/{id}/images/{imageId}` | 画像の実体 |
| POST | `/api/user/english-essays/{id}/gradings` | **AI 添削の受付**（`{"round"?:2}`。省略時は次の回＝**admin-api が決める**）。応答は `data = {gradingId, round, message}`。**所有者をここで確かめる**（他人・存在しない・削除済みは 404） |
| POST | `/api/user/english-essays/ocr` | **同期 OCR の公開入口**（multipart。`level` / `categories` / `files`）。応答は `data = {questionText, essayText, pages[], questionConfidence, essayConfidence}` を**そのまま**。**作文IDは要らない**（編集中の画像をその場で送る） | 要ログイン（`/api/user/**`）。中で合言葉つきで admin-api へ転送 |
| GET | `/api/user/english-essays/limits` | 画像の上限 `{maxImages, maxImageMb}`（設定 `ENGLISH_ESSAY_MAX_IMAGES` / `_MAX_IMAGE_MB`。読めないときは既定 8 / 10）。**画面が事前チェックに使う**（同じ値を画面に二重に持たない） |

### admin-api（AI）
| メソッド | パス | 用途 | 認証 |
|---|---|---|---|
| POST | `/api/admin/batch/english-essay/ocr` | **同期**。画像（1〜8 枚）→ `{questionText, essayText, pages[], questionConfidence, essayConfidence}` | **内部入口**。`X-Internal-Token` 必須（`SecurityConfig` の `InternalServiceAuthorizer`。匿名・利用者の session では通らない）。呼ぶのは user-api だけ |
| POST | `/api/admin/batch/english-essay/gradings` | **受付**（`QUEUED` を積んで即返る）。本文は `{essayId, round?}` | 同上（内部入口）。呼ぶのは user-api だけ |
| GET | `/api/admin/batch/english-essay/gradings/{id}` | 状態 | 同上（内部入口） |

**なぜ OCR と添削の受付を user-api を通すか（2026-09-27 改修）**: 以前は admin-api の受付と同期 OCR が
`permitAll` で、`essayId` を差し替えるだけで**他人の作文に添削を積めた**／**URL を知っていれば
誰でも OCR で AI を呼べた**（どちらも AI の費用を使わせられる）。利用者の権限（ログイン・作文の
所有権）は user-api で確かめ、**そのうえで**合言葉つきで admin-api を呼ぶ。OCR は所有者を確かめる
対象（まだ作文の行が無い）が無いので、**ログイン必須**までを user-api の入口で担保する。
`batC12` / `batC11` の有効確認（`BatchService#requireCallable`）は admin-api 側に残す
（内部トークン経由でも同じ）。**バッチの実行経路（アプリ内部から Step を直接呼ぶ）は HTTP を通らない**
ので、この変更の影響を受けない。

## 5. 処理の流れ

```
[画面] 画像を上げる ──► user-api（保存）──► ENG_英作文画像情報
[画面] AIで画像を読み取る ──► user-api /ocr（**ログイン必須**）──► admin-api /ocr（X-Internal-Token・同期・1 枚ずつ AI）──► 画面（手で直せる）
[画面] 保存 ──► user-api（本文・題・画像の並び/区分 ＋ OCR の生の結果 = 認識テキスト/認識信頼度）
[画面] この内容でAI添削 ──► user-api /gradings（**所有者を確認**）
                              └─► admin-api /gradings（X-Internal-Token。ENG_AI添削履歴情報 に QUEUED。写しを取る）
                              │
                    働き手（5 秒ごと。batC12 の Step を呼ぶ）
                              ├─ SUCCEEDED: 得点・満点・添削結果JSON・AI呼出履歴ID
                              └─ FAILED: 失敗理由（**拾い直さない**＝利用者がやり直す）
[画面] 3 秒ごとに GET /english-essays/{id}（**静かな読み直し**でちらつかせない）
        → QUEUED/RUNNING が消えたら結果を知らせる
```

## 6. 設定（`COM_設定情報` の `ENGLISH_ESSAY_*` 17 項。**設定ページに既にある**）
`ENGLISH_ESSAY_ENABLED`／`_MAX_IMAGES`(8)／`_MAX_IMAGE_MB`(10)／
`_OCR_AI_PROVIDER`・`_OCR_PROMPT`・`_OCR_USER_PROMPT`・`_OCR_MAX_IMAGE_PIXELS`(2048)・
`_OCR_REQUEST_TIMEOUT_SECONDS`(600)・`_OCR_RETRY_LIMIT`(1)／
`_GRADING_AI_PROVIDER`・`_GRADING_PROMPT`・`_GRADING_USER_PROMPT`・
`_GRADING_REQUEST_TIMEOUT_SECONDS`(600)・`_GRADING_RETRY_LIMIT`(1)／
`_TITLE_AI_PROVIDER`・`_TITLE_PROMPT`・`_TITLE_USER_PROMPT`

バッチ: `batC11`（英作文 画像分類・OCR・主題タイトル生成。同期エンドポイントと同じ Step）/
`batC12`（英作文 英検基準AI添削。働き手が呼ぶ）。**有効にするのは運用（バッチ一覧）**だが、
実装時に `database/移行/MIG_ENG_英作文_バッチ有効_20260927.sql` で 2 行を「有効」で入れてある
（行が無いと定義の既定＝無効になり、受付が「バッチが無効に設定されています」で拒否されるため）。
**同期 OCR も `batC11` の有効／無効に従う**（日語単語の AI 取得と同じ。無効にすれば画面からも実行できない）。

## 6.1 実行の定数（コード側。設定ではない）

| 値 | 場所 | 意味 |
|---|---|---|
| 5 秒 | `EnglishEssayAiWorker.INTERVAL_SECONDS` | 働き手が見回る間隔 |
| 1 件 | `EnglishEssayAiWorker.CLAIM_LIMIT` | 1 周期で確保する件数（並列数の設定が無いので安全側） |
| 5 分 | `EnglishEssayAiWorker.STALE_MINUTES` | これを過ぎた `RUNNING` は落ちたと見なして確保し直す |
| 60 秒以上 | 画面（`api/english-essay.ts`） | 同期 OCR の HTTP タイムアウト（設定の 600 秒はサーバ側の AI 呼び出し） |

## 7. データの移行と見本

### 7.1 2.0 からの移行（2026-09-27 実施済み）

`database/移行/MIG_ENG_英作文_2.0から移行_20260927.sql`（何度流しても同じ）。移行元は **study2**
（`STY_英作文情報` / `STY_英作文画像情報`）で、`dblink('dbname=study2', …)` で読む
（日本語勉強の移行と同じ。study2 は study21 と同じインスタンスに同居している）。

| 2.0 | 2.1 | 実績 |
|---|---|---|
| `STY_英作文情報` | `ENG_英作文情報` | 35 件（`liu`→アカウント 1 が 27／`ljz`→アカウント 2 が 8。有効 21・削除 14 もそのまま） |
| `STY_英作文画像情報` | `ENG_英作文画像情報` | 83 件（question 39／answer 37／auto 5／both 2。auto・both は設問側へ） |
| `批改結果JSON`（1 列） | `ENG_AI添削履歴情報` の**第 1 回** | 35 件のうち**本当の添削結果は 24 件**（点数あり）。残り **11 件は 2.0 では OCR までで未添削**（後述） |

- **ID はそのまま**使う（追跡のため）。移行後にシーケンスを進めるので、新規作成と衝突しない
- 対応の無い `ユーザーID` があれば `NOTICE` で知らせる（黙って落とさない）
- **2.0 の JSON は形が一定しない**: 古い版は `rubricValues` / `modelAnswer` が無く、
  `wordRequirement` も**文字列**の行と `{"type":"range","minimum":…,"maximum":55}` の行が混在、
  `taskRequirements` も**配列**の行と `{"commentJa":…}` の行が混在する。JSON は**そのまま**移し、
  画面側の `normalizeGradingReport()` が**どの形も読む**（読めない部分は出さない＝でっち上げない）
- **未添削の 11 件**: JSON の `status` が `OCR_ONLY`（5）/ `OCR_COMPLETED`（5）/ `OCR_PENDING`（1）で、
  得点は NULL。2.0 では AI 添削まで実施していない。いまは移行の単純化で
  `状態コード='SUCCEEDED'` の行を作ってしまっているので、**「添削の行を作らない」に直すのが正しい**
  （未添削として扱う）。削除は破壊的なので**実施前に利用者へ確認する**（11 篇はいずれも
  `状態コード='X'` なので画面の一覧には出ない）
- 移行した行は `登録元コード = 'MIGRATION'`、題は `タイトル_日本語`（無ければ `タイトル`）
- **画像の実体は別途コピー**（§3 のストレージ規約に合わせ、`相対パス` に `english-essay/legacy/` を前置）。
  旧サーバで `deploy/copy-essay-images.sh --from <2.0 の english-essay> --to <storage-root>/english-essay/legacy`
  （**未コピーの環境では移行した 83 枚が 404 になる**）

### 7.2 見本データ

`database/英作文/SAMPLE_ENG_英作文.sql`（**リポジトリに入れてある**。何度流しても同じ）で、
作文 12 件（3 アカウント × 4 篇）と添削 18 件が入る。登録元は `登録元コード='SAMPLE'`。
管理者 2517／保護者 1／生徒 2 のどれで入っても見える。中身はフロントの生成器から出力したもの。
消すとき:
```sql
DELETE FROM public."ENG_英作文情報" WHERE "登録元コード" = 'SAMPLE';
-- 画像と添削履歴は ON DELETE CASCADE で消える
```
**見本は画像を持たない**（実体はファイルなので環境ごとに要る）。レポートを見せるのが目的なので本文と
添削結果だけで足りる。画像つきで見せたいときは、画面から上げるのが早い。

## 8. 今後の課題

1. **家族スコープ**: いまは「自分の作文だけ」。保護者が**子どもの作文**を見る権限（`docs/PERMISSION_MATRIX.md`
   の「親＝子 結果の閲覧」）は未実装（家族の関係を引く必要がある）。
2. **batC11 は画面からは同期で使う**（非同期にしたくなったら、この Step をそのまま呼べばよい）。
3. **レポートの見出し**: 設定の添削プロンプトが headline を返さないので、`japanese/chinese.title` は
   総評（summary）の 1 文目を充てている（無ければ題）。2.0 は AI に見出しを作らせていた。
4. 見本データの画像、DB 行を消したときの実ファイルの掃除。
5. **同期 OCR も user-api の入口＋内部トークンへ寄せた（2026-09-27 改修・完了）**。admin-api の
   `/ocr` は `X-Internal-Token` 必須で、画面は `POST /api/user/english-essays/ocr`（**ログイン必須**）
   を叩く。以前は `permitAll` で、URL を知っていれば**匿名で AI を呼べた**。
   タイムアウトは OCR 専用の設定 `study21.english-essay.admin-api-ocr-timeout-seconds`
   （既定 180 秒。`STUDY21_ENGLISH_ESSAY_ADMIN_API_OCR_TIMEOUT_SECONDS`）。

