<template>
  <div class="prototype-screen prototype-screen--japanese-word">
    <div class="tabs" role="tablist"><button class="tabs__tab is-active" aria-selected="true" style="border-bottom-color:var(--subject-japanese)"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-type"></use></svg>日本語単語</button></div>
    
            <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <p class="card__sub">日本語単語の見出し、読み、意味、例文を管理します（Demo）。</p>
                <div class="search-panel__actions">
                  <button class="btn btn--primary" id="jwSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="jwNew"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規</button>
                  <button class="btn btn--secondary" id="jwReset">リセット</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">レベル：</span><select class="select"><option>すべて</option><option>N2</option></select></span>
                <span class="filter-item"><span class="filter-item__label">分類（From / To）：</span><span class="range-input"><select class="select"><option>Unit001</option></select><span class="range-input__sep">～</span><select class="select"><option>Unit020</option></select></span></span>
                <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="単語・読み・意味" style="flex:1"></span>
              </div><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">学習状態：</span><select class="select"><option>すべて</option><option>未学習</option><option>学習中</option><option>習得済み</option></select></span>
                <span class="filter-item"><span class="filter-item__label">読み：</span><select class="select"><option>すべて</option><option>苦手</option><option>習得済み</option></select></span>
                <span class="filter-item"><span class="filter-item__label">漢字表記：</span><select class="select"><option>すべて</option><option>苦手</option><option>習得済み</option></select></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">日本語単語一覧</h3><span class="table-section__meta">全 3 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>単語ID</th><th>レベル</th><th>分類</th><th>見出し語</th><th>読み</th><th>中国語</th><th>日本語説明</th><th>学習状態</th><th>最終更新</th></tr></thead>
                <tbody id="jwBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button></td><td>JW-0001</td><td>N2</td><td>Unit001</td><td class="cell-strong">抑える</td><td>おさえる</td><td>控制、抑制</td><td class="cell-muted">感情や増加を一定の範囲にとどめる。</td><td><span class="badge badge--warning">学習中</span></td><td class="cell-muted">2026/08/13</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button></td><td>JW-0002</td><td>N2</td><td>Unit001</td><td class="cell-strong">押さえる</td><td>おさえる</td><td>按住</td><td class="cell-muted">物を押して動かないようにする。</td><td><span class="badge badge--neutral">未学習</span></td><td class="cell-muted">2026/08/13</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button></td><td>JW-0003</td><td>N2</td><td>Unit002</td><td class="cell-strong">覚える</td><td>おぼえる</td><td>记住</td><td class="cell-muted">記憶にとどめる。</td><td><span class="badge badge--info">復習</span></td><td class="cell-muted">2026/08/12</td></tr></tbody>
              </table></div>
            </div>
          <div class="overlay" id="wordDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">単語新規登録</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">見出し語</label><input class="input" placeholder="抑える"></div>
            <div class="field"><label class="field__label">読み</label><input class="input" placeholder="おさえる"></div>
            <div class="field"><label class="field__label">中国語</label><input class="input" placeholder="控制"></div>
            <div class="field"><label class="field__label">日本語説明</label><input class="input" placeholder="説明"></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
