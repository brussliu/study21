# システムアイコン（Study 2.1）

`icon.svg` / `icon-maskable.svg` が正（SVG）。PNG は生成物なので手で編集しないこと。

| ファイル | 用途 |
|---|---|
| `icon.svg` | PWA（any）。サイドバーのロゴマーク（`AppSidebar.vue` の `.logo-mark`）と同じ図形 |
| `icon-192.png` / `icon-512.png` | PWA（any）。角丸の外側は透過 |
| `icon-maskable-192.png` / `icon-maskable-512.png` | PWA（maskable）。全面ベタ塗りで、マスク（円・角丸）で切られても欠けない |
| `apple-touch-icon.png` | iOS のホーム画面用（180px・不透明） |

図形の正（マスター）は `frontend/pc-web/public/favicon.svg`。PC 側はそれに加えて
`favicon-32.png` と `apple-touch-icon.png` を使う（`pc-web/index.html` を参照）。

## 再生成

```bash
# Chrome が必要（STUDY21_CHROME で実行ファイルを指定できる）
# 生成スクリプトは開発補助ツールなので tmp/ 配下に置いている（git 管理外・デプロイ対象外）
node tmp/tools/generate-app-icons.mjs
```

生成されるもの：`icon-192/512.png`、`icon-maskable-192/512.png`、`apple-touch-icon.png`（mobile と pc）、
`favicon-32.png`（pc）、`icon.svg`（マスターのコピー）。
参照と実ファイルの食い違いは `frontend/pc-web/tests/icons.spec.ts` /
`frontend/mobile-web/tests/icons.spec.ts` が検出する。
