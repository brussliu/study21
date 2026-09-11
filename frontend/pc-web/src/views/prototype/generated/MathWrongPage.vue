<template>
  <div class="prototype-screen prototype-screen--math-wrong">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--primary" data-route-screen="math-wrong-input"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規</button>
                  <button class="btn btn--secondary" data-open-dialog="aiDlg">AIで新規</button>
                  <button class="btn btn--primary" id="mwSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="mwReset">条件リセット</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">分野：</span><select class="select"><option>すべて</option><option>代数</option><option>関数</option><option>図形</option><option>確率</option><option>整数</option><option>データ分析</option></select></span>
                <span class="filter-item"><span class="filter-item__label">教科：</span><select class="select"><option>すべて</option><option>数学</option></select></span>
                <span class="filter-item"><span class="filter-item__label">ステータス：</span><select class="select"><option>すべて</option><option>下書き</option><option>処理中</option><option>修正中</option><option>修正済</option><option>復習中</option><option>定着済</option></select></span>
                <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="問題ID・問題名などで検索" style="flex:1"></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">誤問題一覧</h3><div class="row" style="gap:8px"><span class="table-section__meta">合計 3 件</span><select class="select" style="width:auto;height:var(--control-sm)"><option>10件</option><option>20件</option><option>50件</option></select></div></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>問題情報</th><th>日付・履歴</th><th>進捗</th><th>タグ</th><th>問題写真</th></tr></thead>
                <tbody id="mwBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-edit="MW-0012" title="修正"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-review="MW-0012" title="復習"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="MW-0012" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td><strong>三角形の合同証明</strong><div class="row" style="gap:6px;margin-top:4px"><span class="badge badge--outline">MW-0012</span><span class="badge badge--outline">中学2年</span><span class="badge badge--subject-ma">図形</span></div></td><td class="cell-muted">試験日 2026/07/20 · 次回 2026/08/15</td><td><span class="badge badge--warning">訂正中</span><div class="cell-muted" style="margin-top:4px">理解度 35%</div></td><td class="cell-muted">誤答:方針ミス / 知識:合同条件</td><td><button class="btn btn--icon btn--sm" data-photo="" title="問題写真"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg> 2</button></td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-edit="MW-0011" title="修正"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-review="MW-0011" title="復習"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="MW-0011" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td><strong>二次関数の最大・最小</strong><div class="row" style="gap:6px;margin-top:4px"><span class="badge badge--outline">MW-0011</span><span class="badge badge--outline">中学3年</span><span class="badge badge--subject-ma">関数</span></div></td><td class="cell-muted">試験日 2026/07/20 · 次回 2026/08/15</td><td><span class="badge badge--info">復習中</span><div class="cell-muted" style="margin-top:4px">理解度 50%</div></td><td class="cell-muted">誤答:計算ミス / 知識:平方完成</td><td><button class="btn btn--icon btn--sm" data-photo="" title="問題写真"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg> 1</button></td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-edit="MW-0010" title="修正"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-review="MW-0010" title="復習"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="MW-0010" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td><strong>確率の加法定理</strong><div class="row" style="gap:6px;margin-top:4px"><span class="badge badge--outline">MW-0010</span><span class="badge badge--outline">高校1年</span><span class="badge badge--subject-ma">確率</span></div></td><td class="cell-muted">試験日 2026/07/20 · 次回 2026/08/15</td><td><span class="badge badge--success">定着済</span><div class="cell-muted" style="margin-top:4px">理解度 90%</div></td><td class="cell-muted">知識:排反事象</td><td>-</td></tr></tbody>
              </table></div>
              <div class="pagination"><span class="pagination__info">全 3 件</span><div class="pagination__pages"><button class="page-btn is-active">1</button></div></div>
            </div>
          <div class="overlay" id="aiDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">AIで新規</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="row" style="gap:8px;margin-bottom:12px"><button class="btn btn--secondary">画像をアップロード</button><button class="btn btn--secondary" data-open-dialog="testinfoDlg">テスト情報から選択</button><button class="btn btn--secondary" data-route-screen="temp-file-picker">臨時ファイルから選択</button></div>
            <div class="dropzone"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>問題画像をドラッグ&amp;ドロップ（Ctrl+V 可）</div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--secondary" data-open-dialog="cropDlg">画像の切り抜き</button><button class="btn btn--primary" id="aiDraft">下書きを作成</button></div>
        </div>
      </div><div class="overlay" id="testinfoDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">テスト情報から選択</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="row" style="gap:8px;margin-bottom:12px"><select class="select"><option>数学</option></select><select class="select"><option>通常</option><option>模試</option><option>期中</option><option>期末</option><option>小テスト</option></select><input class="input" placeholder="キーワード" style="flex:1"><button class="btn btn--primary btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button></div><div class="cell-muted">該当テストの画像一覧（Mock）</div></div>
          <div class="dialog__foot"><button class="btn btn--primary" data-dialog-close="">画像を追加</button></div>
        </div>
      </div><div class="overlay" id="cropDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">画像の切り抜き</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="dropzone" style="height:160px">切り抜き範囲（Mock）</div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">左回転</button><button class="btn btn--secondary" data-dialog-close="">右回転</button><button class="btn btn--primary" data-dialog-close="">この範囲で保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
