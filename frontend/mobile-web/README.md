# mobile-web（骨組みのみ）

モバイル版は現在**空の骨組み**だけを置いている。業務画面・部品・API 呼び出しは
いったんすべて削除してあり、内容は後で追加する（PC 版 `pc-web` が本実装）。

```
mobile-web/
  index.html            エントリ（PWA の manifest / アイコン参照）
  public/icons/         PWA アイコン（tmp/tools/generate-app-icons.mjs で生成）
  src/
    main.ts             Vue + Pinia + Router の起動
    App.vue             RouterView と ToastHost だけの器
    router/index.ts     ルート定義（現在は「準備中」1 画面のみ）
    views/              画面（現在は PreparationView.vue のみ）
    assets/main.css     基本スタイル（デザイントークンは web-shared から読む）
    layouts/            画面共通レイアウト … 後で追加
    components/         部品 … 後で追加
    stores/             Pinia ストア … 後で追加
    api/                バックエンド呼び出し … 後で追加
    config/             メニュー定義など … 後で追加
  tests/                骨組みの維持を確認するテスト（icons / skeleton）
```

## 決めごと

- **業務内容を入れる前に**、どの画面をモバイルに載せるかを決める（PC 版と同じ機能を
  全部載せる必要はない）。載せない機能はルートを作らない。
- ルートを追加したら `tests/skeleton.spec.ts` の「業務ルートが無い」断言を更新する
  （この断言は“うっかり業務画面が入っていないか”を見るためのものではなく、
  骨組みの状態を明示するためのもの）。
- 認証・権限の扱いは PC 版（`pc-web/src/stores/auth.ts`）と揃える。
- ビルドは `npm run build -w mobile-web`。Docker では
  `deploy/docker/nginx.Dockerfile` が `mobile-web/dist` を `/usr/share/nginx/html/m`
  へ配置し、`deploy/docker/nginx.conf` の 81 番ポートで配信する。
