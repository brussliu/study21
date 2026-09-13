# 前端指南

## 1. PC 前端结构（`frontend/pc-web`）

```
src/
├── main.ts                 # 入口：Pinia + Router + 共享 token
├── App.vue                 # RouterView + ToastHost
├── assets/main.css         # 基础样式（引用共享 token）
├── router/index.ts         # 路由 + Route Meta
├── config/menuRegistry.ts  # UI 移行期业务菜单
├── stores/                 # Pinia（theme / UI确认用假认证 / system）
├── layouts/                # AdminLayout / UserLayout
├── components/
│   ├── layout/             # AppSidebar / AppTopbar / AppBreadcrumb / PageTitle
│   └── SystemStatus.vue    # 系统状态（调用 health）
└── views/                  # login / prototype(Vue移行画面) / error
```

## 2. Mobile 前端结构（`frontend/mobile-web`）

**現在は骨組みのみ**（業務画面・部品・API 呼び出しは削除済み。内容は後で追加する）。
置いているもの：エントリ（`index.html` / `src/main.ts` / `src/App.vue`）、最小のルート（`/` の「準備中」1 画面）、
基本スタイル、空のディレクトリ（`layouts/` `components/` `stores/` `api/` `config/`）、PWA アイコン。
追加手順は `frontend/mobile-web/README.md`、骨組みの維持は `frontend/mobile-web/tests/skeleton.spec.ts` が見張る。

PWA 结构：`public/manifest.webmanifest`、`public/icons/`（实际图标：`icon.svg` + 192/512 PNG + maskable + apple-touch-icon。生成命令与说明见该目录的 `README.md`，生成脚本 `tmp/tools/generate-app-icons.mjs`（開発補助ツールは `tmp/` 配下に置く））。
本阶段**不创建 Service Worker**（不缓存 API 响应、登录状态、敏感数据、用户信息）。

## 3. 路由

### PC

| 路由 | 布局 | 说明 |
|---|---|---|
| `/login` | blank | 普通用户登录 |
| `/admin/login` | blank | 管理员登录 |
| `/admin/home` `/admin/:screen` | AdminLayout | 管理员 UI 页面 |
| `/student/home` `/student/:screen` | UserLayout | 学生 UI 页面 |
| `/{area}/site` `/{area}/terminal-control` | Admin/User | サイト管理・端末コントロール（`docs/NET_CONTROL.md`） |
| `/{area}/daily-report` | Admin/User | 学習日報（`views/daily-report/DailyReportView.vue`。月カレンダー＋マスを開いて授業の追加・まとめ・提出。`database/学習日報/学習日報設計.md`） |
| `/{area}/internet-usage` | Admin/User | インターネット利用履歴（`views/network/InternetUsageHistoryView.vue`。タブ: サイトアクセス履歴／Web閲覧履歴。どちらも 2.0 から移行した実データ。`docs/NET_CONTROL.md`） |
| `/{area}/batch` | Admin/User | バッチ一覧（`views/batch/BatchListView.vue`） |
| `/{area}/batch-history` | Admin/User | バッチ実行履歴（`views/batch/BatchHistoryView.vue`。実データ `BAT_バッチ実行履歴情報`） |
| `/{area}/batch-ai-history` | Admin/User | AI呼出履歴（`views/batch/AiCallHistoryView.vue`。2.0 の `BAT_AI呼出履歴情報` を全件移行。`database/バッチ/BAT_バッチ管理設計.md` §5.5） |
| `/{area}/todo` | Admin/User | TODO（`views/todo/TodoView.vue`。タブ: 一覧／カレンダー。`database/TODO/TODO設計.md`） |
| `/{area}/study-monitor` | Admin/User | 学習状況モニター（`views/study-monitor/StudyMonitorView.vue`。`docs/STUDY_MONITOR.md`） |

左メニューの「バッチ管理」は**親メニュー**で、上の 3 画面を子に持つ（3 エリアとも同じ。
`database/バッチ/BAT_バッチ管理設計.md`）。
左メニューの「ネットワーク制御」も**親メニュー**で、子に サイト管理 / 端末コントロール /
インターネット利用履歴 を持つ（サイト管理・端末コントロールはトップレベルから移した）。

左メニューの「アプリ制御」も**親メニュー**で、子に アプリ管理 / アプリ利用履歴 を持つ
（旧: トップレベルの「アプリ制御」「エージェント履歴」。ルートは `/{area}/agent-control`・
`/{area}/agent-history` のままで、画面は移行用プロトタイプ `AgentControlPage.vue` /
`AgentHistoryPage.vue` が受ける）。

ユーザー情報の修正とパスワードの変更は**ルートを持たない**（右上メニューからダイアログで開く。
保存後に親ページを再読み込みする。詳細は `docs/ACCOUNT.md`）。
| `/parent/home` `/parent/:screen` | UserLayout | 家长 UI 页面（ホームはアカウント情報・生徒一覧＋「お子さまの学習状況」＝ `components/home/StudyOverview.vue`。生徒ホームと同じ内容を共用する） |
| `/403` `/404` `/500` | blank | 错误页 |
| 其他 | - | 重定向 `/404` |

### Mobile

現在は `/`（準備中ページ）のみ。業務画面を追加したときにこの表を更新する。

## 3.9 ダイアログの閉じ方（全画面共通）

- **背景（灰色の部分）をクリックしても閉じない**（ユーザーの指定）。
  入力中に背景を押して内容が消えるのを防ぐため、`overlay` に `@click.self` を付けない。
- 閉じる手段は **右上の ×（`.dialog__close`）** と、**「閉じる」「キャンセル」ボタン**。
  画像ビューア・注釈・画像編集はこれに加えて `Esc` でも閉じられる。
- 背景クリックで閉じる指定が復活していないことは `tests/dialog-backdrop.spec.ts` が見張る
  （`src` の `.vue` を走査して `@click.self` が残っていないこと、
  閉じるボタンが必ずあることを確認する）。

## 3.10 一覧のページングと「件数」の置き場所

一覧の右下のページング（`.pagination`）は次の並びにそろえる（ユーザーの指定）。

```
全 138848 件（1 / 6943 ページ）        件数 [20 件 ▾]  ‹ 1 2 3 4 5 … 6943 ›
└ 件数の情報（左端）                    └ 件数の選択  └ ページ番号
```

- **1 ページの件数の選択（`.pagination__size`）は検索条件に置かず、ページ番号の左隣**に置く
  （`ネットワーク制御 > インターネット利用履歴`・`バッチ管理 > AI呼出履歴`・`バッチ管理 > バッチ実行履歴`）。
- 設計システムの `.pagination` は「左に件数情報・右にページ番号」の 2 つを想定しているため、
  `src/assets/app/app.css` で `.pagination__info` を左端へ寄せて
  「件数 + ページ番号」を右にまとめている。
- 行が 1 件以上あるときは常に出す（1 ページしか無くても件数は変えられる）。
  狭い画面では設計システムの `responsive.css` が `.pagination__size` を隠す。
- 件数を変えたら 1 ページ目から取り直す（画面ごとの `search()` を呼ぶ）。
- 選択肢は 20 / 50 / 100 件（API の上限は 100 件）。

## 3.11 検索条件カードの並べ方

一覧画面の検索条件は **`.search-panel` ＋ `.filters`** にまとめ、**検索・リセットは見出しの右端**
（`.search-panel__actions`）に置く（資料管理・インターネット利用履歴・AI呼出履歴など）。
項目は `.filters__row` を行ごとに分けて並べる。

- 見出しは `.search-panel__head` ＋ `.search-panel__title` を使う（`.table-section__head` は
  一覧の見出し用で下線を持ち、検索条件のカードの途中に線が入る）。
- **`card` だけで囲まない**。`.card` は内側の余白を持たず、項目が枠線に接してしまう
  （AI呼出履歴が以前そうなっていた）。`.search-panel` は `padding: 16px 20px` を持つ。
- 選択（`select`）の幅は**内容に任せない**。選択肢が長いと勝手に広がって
  後ろの項目が次の行へ落ちるため、`style="width: 10rem"` のように指定して揃える
  （AI呼出履歴の「モデル名」が典型例）。
- ただし設計システムは `.filter-item .select/.input` に **`min-width: 150px`** を持つため、
  **9.375rem（150px）未満の指定は無視される**（AI呼出履歴の AI区分・結果が以前 8rem / 7rem の
  指定で 150px になっていた）。指定する値は 150px 以上にする。
- 残り幅いっぱいに広げたい項目（キーワードなど）は `.filter-item--grow` を付ける。
  設計システムの `.filter-item--grow` は中の `.input` しか伸ばさないので、
  `src/assets/app/app.css` で項目自体も伸ばしている（`flex: 1 1 12rem`）。
- **幅を固定した項目だけの行と、残り幅を使う行を分ける**。残り幅を使う項目を 1 行目に置くと、
  画面幅が少し狭いだけでその項目が次の行へ落ち、その行の幅いっぱいまで伸びて
  さらに次の行（期間など）を押し出す（AI呼出履歴が 1440px 以下でそうなっていた）。
  1 行目を幅固定の項目だけにし、2 行目を「キーワード（grow）＋ 期間」にすると
  1366px まで 2 行のままに収まる。
- 期間（From / To）は同じ行にまとめ、幅を揃える。
- ボタンを項目と同じ行に置かない（行の高さがそろわず、折り返しの原因になる）。

回帰確認は `tests/ai-call-history.spec.ts`（項目の並びと指定幅）と
`tmp/e2e/e2e-ai-call-history-layout.mjs`（実機の余白・実際の幅・1600/1440/1366 の折り返し、
他の一覧画面との一致）が見張る。

## 3.12 複数行の入力（textarea）

- 設計システムの `.input` は **1 行入力用**（`height: ハンドル高さ` ＋ `padding: 0 12px`）。
  これを `<textarea class="input" rows="3">` に使うと、**文字が上端に張り付き**（上下の余白 0）、
  高さも 1 行ぶんに潰れる。
- そのため `src/assets/app/app.css` で `textarea.input` を複数行向けに上書きしている
  （`height: auto` ＋ `padding: var(--sp-2) var(--sp-3)` ＋ `resize: vertical`）。
  新しく複数行の入力を足すときは `.textarea` を使うのが本来だが、`.input` でも崩れない。
- 回帰確認は `tests/dialog-backdrop.spec.ts`（共通スタイルの存在）と
  `tmp/e2e/e2e-daily-report.mjs`（実機で上下の余白 8px・3 行ぶんの高さ）が見張る。

## 4. Layout

- `AdminLayout`：侧栏 + 顶栏 + 面包屑 + 内容区 + 页面标题区 + 响应式侧栏。
- `UserLayout`（PC）：侧栏 + 顶栏 + 内容区。
- Mobile のレイアウトは未実装（骨組みのみ。追加時に `frontend/mobile-web/src/layouts/` へ置く）。

## 5. Menu Registry

业务菜单权限仍未决定。`src/config/menuRegistry.ts` 暂时向三个区域提供相同的迁移页面菜单：

- `frameworkMenu(area)`：系统首页。
- `prototypeMenu(area)`：原 UI 左侧菜单对应的 Vue 页面。
- `resolveMenu(area, role?)`：菜单统一入口。

不把角色可访问的业务功能写死。

## 6. Pinia

- `useThemeStore`：亮/暗主题切换（仅持久化主题，不持久化登录状态）。
- `useAuthStore`：UI 确认专用假认证；仅保存用户名和角色到 `sessionStorage`，不保存密码。
- `useSystemStore`：调用两个后端 health，维护 `adminHealth` / `userHealth` 状态。

## 7. Design Token

共享于 `web-shared/src/styles/design-tokens.css`（亮色 + `html[data-theme="dark"]` 暗色）。
组件只引用语义化别名（`--color-*`、`--sp-*`、`--fs-*` 等）。JS 侧使用 `web-shared/src/tokens/tokens.ts`。

## 8. AdminApiClient / UserApiClient

位于 `web-shared/src/api/`：

- `AdminApiClient`：baseUrl 默认 `/api/admin`，可被 `VITE_ADMIN_API_BASE_URL` 覆盖。
- `UserApiClient`：baseUrl 默认 `/api/user`，可被 `VITE_USER_API_BASE_URL` 覆盖。

统一处理：超时、网络错误、非 2xx、401/403/404/500、Trace ID、取消请求、JSON 响应、统一错误信息。
页面组件**不直接散落 fetch**，一律通过客户端调用。

## 9. 角色权限

角色权限暂未决定。前端仅定义 `Role` 类型（`'ADMIN' | 'STUDENT' | 'GUARDIAN'`），
不建立任何「角色 → 业务功能」的映射。

**当面の運用（2026-09-12・ユーザーの指定）**: ロールで機能を分けない。3 エリア
（`/admin` `/student` `/parent`）で左メニューは同じものを出し（`resolveMenu` は `role` を
受け取るが使っていない）、すべての機能を学生にも開放する。検証も学生だけで行う
（`tmp/e2e/e2e-student-screens.mjs` が実装済み 15 画面を学生で順に開いて確認する）。
詳細は `docs/SECURITY_AND_ROLES.md` §2.1。

## 9.1 操作按钮的外观规则（各画面共通）

同じ役割のボタンは、どの画面でも同じ見た目にする。基準は次のとおり
（実機で一致していることを `tmp/e2e/e2e-action-buttons.mjs` が確認し、ソース側は
`tests/ui-action-buttons.spec.ts` が固定する）。

| ボタン | 見た目 | 備考 |
|---|---|---|
| 検索 | `.btn.btn--primary` ＋ `search` アイコン | 検索条件の確定 |
| **リセット** | **`.btn.btn--secondary` ＋ `rotate` アイコン ＋ ラベル「リセット」** | **基準はリンククリップ**。`btn--sm` は付けない（隣の検索ボタンと高さを揃える） |
| **新規** | **`.btn.btn--primary` ＋ `plus` アイコン ＋ ラベル「新規」** | **基準はサイト管理**。`btn--sm` は付けない |
| **提出（学習日報）** | **`.btn.btn--primary` ＋ `check-circle` アイコン ＋ ラベル「提出」** | 提出済みの日は `disabled` にしてラベルを「提出済み」にする（記録が無い日も `disabled`）。基準は学習日報 |
| 承認 | `.btn.btn--icon` ＋ `check-circle`（緑） | 一覧の行操作 |
| 却下 | `.btn.btn--icon` ＋ **`rotate` ＋ `.icon--reject`（橙）** | **赤は使わない**（削除と紛らわしいため） |
| 編集 | `.btn.btn--icon` ＋ `edit`（青） | |
| 削除 | `.btn.btn--icon` ＋ `trash`（赤） | アイコンボタン自体は `.is-danger` を使う |

- アイコンの色は `src/assets/app/app.css` の共通ルール（`.icon--danger` / `.icon--edit` /
  `.icon--view` / `.icon--reject`）で定義する。色は `tokens.css` の変数だけを使う。
- 画像ビューア・画像エディタの中の「リセット」（回転・拡大のリセット）は、
  同じツールバーに「左回転 / 右回転」があるため `rotate` アイコンを付けず、
  ツールバーの高さに合わせて `btn--sm` のままにする（操作の意味が紛らわしくなるため）。
## 10. UI 页面迁移

原 UI 页面主体已转换为 PC 前端中的 Vue SFC，通过系统路由和 Layout 显示。
不再提供 `/demo` 页面目录，也不发布 `/ui-demo/*.html`。

- 必须先通过登录页的假认证进入系统。
- 当前页面数据和按钮操作仍为 UI Mock，不连接正式 API 或数据库。
- 后续按功能逐项整理组件状态、校验、API 和角色权限。
