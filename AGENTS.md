# AGENTS.md — Study 2.1

规范来源（Standards 轴）：`docs/ARCHITECTURE.md`、`docs/FRONTEND_GUIDE.md`、`docs/BACKEND_GUIDE.md`、`docs/API_CONVENTIONS.md`、`docs/DECISIONS.md`、`docs/PC_FRONTEND_STRUCTURE.md`。动手前先读改动所涉领域的那一份。
行为基准实现是 2.0：`C:\work\Source\study2\study2`（JSP + `src/main/webapp/js/*.js`），移植类需求以它为准。

## 每个功能 / 修 bug 的流程

1. **先加载 `tdd` skill，再写代码**（`skill` 工具，name: `tdd`）。
2. **确认接缝**：写第一个测试前，用一句「公开接口是什么、测哪些接缝」向用户确认，得到答复再写。
3. **red → green，一次一个切片**：一个失败测试 → 最小实现 → 重复；每个测试是 tracer bullet。
4. **收工时做两轴审查**：加载 `code-review` skill，固定点用用户指定的 ref（未指定就问），diff 取 `git diff <fixed-point>...HEAD`。本仓库没有 issue tracker，spec 来源 = 会话中用户的需求描述，或 `docs/` 下的设计文档。
5. **收尾四条命令全绿**：`package.json` 的 `typecheck` / `lint` / `test` / `build`（工作目录 `frontend/pc-web`），并在 Chrome 里实测改动过的画面（本地 `vite preview` + `scripts`/`tmp` 下的截图脚本）。

## 本仓库约定（环境与配置看不出来的）

- UI 文案与代码注释用日文（面向日本学生的日文系统）；与用户交流用中文，系统固有名词保留日文（資料管理、リンククリップ、遊び方 等）。
- `frontend/pc-web/src/assets/prototype/*.css` 是设计系统的只读副本：新增样式写进 `src/assets/app/app.css` 或对应的 `src/features/<feature>/*.css`；颜色只取 `tokens.css` 的变量，让暗色主题自动跟随。
- 部署由用户执行（`deploy\deploy-to-nas.bat`）：agent 的验证止于本地（本地 API / `vite preview` / Chrome）。
- 提交由用户决定：改完先报告结果，等用户说提交再 commit。
- `implement`、`ask-matt`、`to-spec`、`to-tickets`、`handoff` 等 skill 带 `disable-model-invocation: true`，agent 无法自动加载：用上面的 `tdd` + `code-review` 复刻 `implement` 的流程。
