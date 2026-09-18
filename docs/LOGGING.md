# 日志设计（Study 2.1）

发布位置（NAS）上的日志结构。设计目标是三点：**DB 操作可追溯**、**SQL 与异常分开**、**不依赖容器日志**。

## 1. 目录与文件

```
/vol5/1000/DATA0/tomcat/study21/
├── webapps/                        ← 程序（docker compose 项目目录）
├── files/                          ← 用户数据
│   ├── documents/  legacy-documents/  temp-files/  test-files/
│   ├── reading/                    ← 書籍の本文 PDF・表紙（読書管理）
│   └── legacy-reading/             ← 2.0 の webapps/file/ENGLISH_READING（読取専用）
└── logs/
    ├── backend/                    ← 后端日志（admin-api / user-api 各自写自己的文件）
    │   ├── admin-api-app.log       ← 通常日志（INFO 以上、SQL 与 ERROR 除外）
    │   ├── admin-api-sql.log       ← 全部 DB 操作
    │   ├── admin-api-error.log     ← 异常・ERROR（含堆栈）
    │   ├── user-api-app.log / user-api-sql.log / user-api-error.log
    │   └── archive/                ← 轮转后的历史日志（gzip）
    │       └── admin-api-sql.2026-09-11.0.log.gz …
    └── frontend/                   ← 前端（nginx）日志
        ├── study21-access.log
        └── study21-error.log
```

每个服务只写自己的文件（文件名带服务名前缀），因此两个后端容器同时挂载 `logs/` 也不会互相干扰。
（コンテナ内のパスは `/app/logs`。アプリはその下の `backend/` に出すので、ホスト側は `logs/backend/` になる）

## 2. 后端日志

### 2.1 三种文件的分工

| 文件 | 内容 | 级别 |
|---|---|---|
| `<service>-app.log` | 通常のアプリケーションログ（起動、業務処理の経過など）。**SQL と ERROR は含めない**（重複を避ける） | `STUDY21_LOG_LEVEL`（既定 INFO） |
| `<service>-sql.log` | **すべての DB 操作**（SELECT / INSERT / UPDATE / DELETE）。パラメータ・実行時間・件数付き | `STUDY21_SQL_LEVEL`（既定 INFO） |
| `<service>-error.log` | ERROR 以上（例外＋スタックトレース）。`GlobalExceptionHandler` の未処理例外もここ | 常に ERROR 以上 |

### 2.2 SQL 日志的格式（1 操作 = 1 行）

```
2026-09-11T18:10:43.123+09:00 INFO  [8f3c1a2b] - op=SELECT mapper=com.study21.user.account.AccountMapper.findByLoginId duration=3ms rows=1 params=[loginId=foo@example.com] sql=SELECT * FROM acc_account WHERE LOWER(login_id) = LOWER(?)
2026-09-11T18:11:02.551+09:00 INFO  [9a02ff10] - op=UPDATE mapper=com.study21.admin.setting.SettingValueMapper.updateValue duration=12ms rows=1 params=[attrKey=SCHOOL_NAME, attrValue=〇〇学園] sql=UPDATE com_setting_value SET attr_value = ? WHERE attr_key = ?
2026-09-11T18:12:44.008+09:00 ERROR [b71d0c93] - op=INSERT mapper=com.study21.user.document.DocumentMapper.insert duration=8ms rows=- status=FAILED error=org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "document_pkey" params=[title=テスト資料] sql=INSERT INTO doc_document (title) VALUES (?)
```

| 字段 | 含义 |
|---|---|
| `op` | SELECT / INSERT / UPDATE / DELETE（MyBatis の SqlCommandType） |
| `mapper` | Mapper インターフェースのメソッド（`パッケージ.インターフェース.メソッド`） |
| `duration` | 実行時間（ms） |
| `rows` | SELECT は取得件数、INSERT/UPDATE/DELETE は更新件数。失敗時は `-` |
| `params` | バインドパラメータ（`@Param` の複数引数・エンティティ・単一値のいずれも解決） |
| `sql` | SQL 文（改行は空白へ潰し、1 操作 1 行を保証。長い場合は 4000 文字で切り詰め） |
| `status` / `error` | 失敗時のみ。`error.log` 側に同じ操作のスタックトレースが残る |
| `[traceId]` | リクエスト単位の traceId（`X-Trace-Id` ヘッダと同じ値。API 応答の traceId から DB 操作まで追える） |

### 2.3 実装（追加の手作業は不要）

- **`common-core` の `SqlLoggingInterceptor`**（MyBatis `Executor` の `query` / `update` をインターセプト）が全ての DB 操作を記録する。**Mapper を追加するだけで自動的に記録対象**になり、repository 側にログを書く必要はない。
- 出力先・フォーマット・ローテーションは **`backend/common-core/src/main/resources/logback-spring.xml`** に集約（両サービス共通）。`application.yml` の `study21.log.*` がパラメータ。
- MyBatis を迂回した生 JDBC（`DataSource#getConnection`、`JdbcTemplate` 等）は**記録されない**ため禁止（`AGENTS.md` の規約）。

## 3. 前端（nginx）日志

`logs/frontend/study21-access.log`（`deploy/docker/nginx.conf` で定義）:

```
log_format study21 '$remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent "$http_referer" "$http_user_agent" rt=$request_time';
```

例:

```
192.168.0.42 - - [11/Sep/2026:18:20:11 +0900] "GET /student/document?view=folder HTTP/1.1" 200 1823 "-" "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" rt=0.004
192.168.0.42 - - [11/Sep/2026:18:20:12 +0900] "POST /api/user/document/search HTTP/1.1" 200 912 "http://192.168.0.100:8090/student/document?view=folder" "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" rt=0.031
```

**可以看出的**：哪个 IP 在什么时刻请求了哪个页面 URL（初次打开／刷新时记录全路径），以及 API 调用及其 `Referer`——即「その API 呼び出しがどの画面から来たか」。
**看不出的**：SPA 内部のタブ移動（クライアント側ルーティング）はサーバーへリクエストを出さないため記録されない。ユーザー名も nginx では分からない（必要になったら、フロントから `/api/.../log/pageview` を送る方式に拡張する＝今回は未実装）。

## 4. 轮转与保留

- トリガ：**日次** または **1 ファイルが `STUDY21_LOG_MAX_FILE_SIZE`（既定 100MB）** を超えたとき。
- 退避先：`logs/backend/archive/<service>-<kind>.<yyyy-MM-dd>.<n>.log.gz`（gzip 圧縮）。
- 保持：`STUDY21_LOG_RETENTION_DAYS`（既定 90 日）を超えた分は自動削除。
- nginx 日志はコンテナ内の `/var/log/nginx` に直接書くため、ローテーションは logrotate 等が必要（未設定。必要になれば `logs/frontend` に対して追加する）。

## 5. 配置项（`setting/deploy.env` → コンテナの環境変数）

| 变量 | 默认 | 说明 |
|---|---|---|
| `STUDY21_LOG_HOST_PATH` | `<发布位置>/logs` | バックエンドのコンテナの `/app/logs` にマウントするホスト側パス（実ファイルは `logs/backend/` 配下） |
| `STUDY21_LOG_DIR` | `/app/logs`（コンテナ内） | アプリが使うログのルート。実際の出力は `/app/logs/backend/` |
| `STUDY21_FRONTEND_LOG_HOST_PATH` | `<发布位置>/logs/frontend` | nginx の `/var/log/nginx` にマウント |
| `STUDY21_LOG_LEVEL` | `INFO` | 通常ログのレベル。`DEBUG` にすると詳細が出る |
| `STUDY21_SQL_LOG_LEVEL` | `INFO` | SQL ログのレベル。`OFF` で停止できる |
| `STUDY21_LOG_RETENTION_DAYS` | `90` | 保持日数 |
| `STUDY21_LOG_MAX_FILE_SIZE` | `100MB` | 1 ファイルの上限 |
| `STUDY21_TZ` | `Asia/Tokyo` | 3 コンテナのタイムゾーン（下の「時刻」を参照） |

レベルやサイズを変えたいときは `setting/deploy.env` を編集して再デプロイ（`tools/deploy-to-nas.sh`）する。

## 6. 运维常用命令

```bash
cd /vol5/1000/DATA0/tomcat/study21/logs

# 直近のアプリログ
tail -f backend/admin-api-app.log

# 直近の DB 操作（誰が何をしたか）
tail -f backend/admin-api-sql.log backend/user-api-sql.log

# エラーだけ
tail -f backend/admin-api-error.log backend/user-api-error.log

# 1 つのリクエストを traceId で追う（画面のエラー表示に出る traceId を使う）
grep -h '8f3c1a2b' backend/*.log

# 遅い SQL の抽出（duration 降順）
grep -h 'op=' backend/*-sql.log | sed -E 's/.*duration=([0-9]+)ms.*/\1 &/' | sort -rn | head -20

# 特定の Mapper の実行履歴
grep -h 'mapper=com.study21.user.document.DocumentMapper' backend/user-api-sql.log | tail -50
```

## 7. 测试（回归防线）

| 测试 | 位置 | 见什么 |
|---|---|---|
| 1 行フォーマット・パラメータ解決・切り詰め | `backend/common-core/src/test/java/com/study21/common/core/logging/SqlLogFormatterTest.java` | 9 tests |
| 全 DB 操作の記録・失敗時の FAILED と再スロー | `.../SqlLoggingInterceptorTest.java` | 4 tests |
| logback の組み込み（app/sql/error 3 ファイル、サービス名、SQL が app.log に出ないこと） | `backend/admin-api/src/test/java/com/study21/admin/logging/LogFileWiringTest.java` | 3 tests |

実行: `cd backend && mvn test`（NAS 上では Maven/JDK が必要。通常は `docker compose build` 内でコンパイルされる）。

## 8. 時刻（タイムゾーン）

- 3 コンテナとも `TZ=Asia/Tokyo`（`setting/deploy.env` の `STUDY21_TZ`）で動く。ログの時刻は**すべて日本時間**。
  - バックエンド：Java の既定タイムゾーンが JST になり、`%d{...XXX}` は `+09:00` を出す。`logs/backend/archive/` の日次ローテーションも日本時間の 0 時に切り替わる。
  - フロント（nginx）：`access_log` / `error_log` の時刻が JST になる。Alpine の nginx イメージには tzdata が入っていないため、
    `deploy/docker/nginx.Dockerfile` で `apk add --no-cache tzdata` している（これが無いと TZ を設定しても UTC のまま）。
  - バックエンドのイメージ（`eclipse-temurin`）は tzdata 入りのため、環境変数だけで効く。
- 変えたいときは `setting/deploy.env` の `STUDY21_TZ` を書き換えて再デプロイする（例：`UTC`）。
- なお `docker compose logs`（標準出力のログ）に付く時刻は Docker デーモン側＝ホストのタイムゾーンのまま。
  アプリのログファイル（`logs/`）は JST になる。
