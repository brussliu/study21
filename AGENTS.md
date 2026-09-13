# AGENTS.md — Study 2.1

规范来源（Standards 轴）：`docs/ARCHITECTURE.md`、`docs/FRONTEND_GUIDE.md`、`docs/BACKEND_GUIDE.md`、`docs/API_CONVENTIONS.md`、`docs/DECISIONS.md`、`docs/PC_FRONTEND_STRUCTURE.md`。动手前先读改动所涉领域的那一份。
行为基准实现是 2.0：`C:\work\Source\study2\study2`（JSP + `src/main/webapp/js/*.js`），移植类需求以它为准。

## 每个功能 / 修 bug 的流程

1. **先加载 `tdd` skill，再写代码**（`skill` 工具，name: `tdd`）。
2. **确认接缝**：写第一个测试前，用一句「公开接口是什么、测哪些接缝」向用户确认，得到答复再写。
3. **red → green，一次一个切片**：一个失败测试 → 最小实现 → 重复；每个测试是 tracer bullet。
4. **收工时做两轴审查**：加载 `code-review` skill，固定点用用户指定的 ref（未指定就问），diff 取 `git diff <fixed-point>...HEAD`。本仓库没有 issue tracker，spec 来源 = 会话中用户的需求描述，或 `docs/` 下的设计文档。
5. **收尾四条命令全绿**：`package.json` 的 `typecheck` / `lint` / `test` / `build`（工作目录 `frontend/pc-web`），并在 Chrome 里实测改动过的画面（本地 `vite preview` + `tmp/` 下的截图脚本）。

## 本仓库约定（环境与配置看不出来的）

- UI 文案与代码注释用日文（面向日本学生的日文系统）；与用户交流用中文，系统固有名词保留日文（資料管理、リンククリップ、遊び方 等）。
- `frontend/pc-web/src/assets/prototype/*.css` 是设计系统的只读副本：新增样式写进 `src/assets/app/app.css` 或对应的 `src/features/<feature>/*.css`；颜色只取 `tokens.css` 的变量，让暗色主题自动跟随。
- 部署由用户执行（现为 NAS 上的 `tools/deploy-to-nas.sh`，配置在 `setting/deploy.env`）：agent 的验证止于本地（本地 API / `vite preview` / Chrome / `mvn test`）。
- **DB 操作日志是硬要求**：所有 DB 操作必须被记录。实现方式是 `backend/common-core` 的 `SqlLoggingInterceptor`（MyBatis `Executor` 拦截器），新增 Mapper 自动被记录，**不要在 repository 里手写 SQL 日志**；日志格式与轮转由 `backend/common-core/src/main/resources/logback-spring.xml` 统一决定，输出到 `<发布位置>/logs/backend/<service>-sql.log`。因此**禁止绕过 MyBatis 直接用 JDBC/JdbcTemplate/其它 ORM**（会漏日志）；确实必须用时，要同时提供等价的记录手段。SQL 与系统异常分文件（`-sql.log` / `-error.log`），通常日志进 `-app.log`。设计说明见 `docs/LOGGING.md`。
- **一時ファイル・自動生成ツールの置き場**：エージェントや補助ツールが生成するもの（検証スクリプト、スクリーンショット、調査メモ、生成スクリプト等）は必ずリポジトリ直下の `tmp/` に置く（例: `tmp/tools/`、`tmp/screenshots/`）。`tmp/` は git 管理外で、`tools/deploy-to-nas.sh` のコピー対象からも除外されている。リポジトリ直下に新しい作業用ディレクトリを作らない。
- `design/`・`e2e/`・`scripts/` はユーザーの判断で削除済み（デプロイ先にも不要）。復活させない／新たに作らない。必要になったら `tmp/` 配下に置くか、git 履歴から復元する。
- 提交由用户决定：改完先报告结果，等用户说提交再 commit。
- `implement`、`ask-matt`、`to-spec`、`to-tickets`、`handoff` 等 skill 带 `disable-model-invocation: true`，agent 无法自动加载：用上面的 `tdd` + `code-review` 复刻 `implement` 的流程。
