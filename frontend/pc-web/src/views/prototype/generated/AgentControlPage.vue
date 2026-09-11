<template>
  <div class="prototype-screen prototype-screen--agent-control">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions"><button class="btn btn--primary" id="acSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button><button class="btn btn--primary" data-open-dialog="ruleDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規追加</button></div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">区分：</span><select class="select"><option>全区分</option><option>1.通常</option><option>2.休憩</option><option>3.ゲーム</option><option>4.勉強</option></select></span>
                <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="アプリ名称 / 備考" style="flex:1"></span>
                <span class="filter-item"><span class="filter-item__label">状態：</span><select class="select"><option>全状態</option><option>有効</option><option>無効</option></select></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">アプリ制御ルール一覧</h3><span class="table-section__meta">全 3 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>ID</th><th>アプリ名称</th><th>判定方法</th><th>区分</th><th>状態</th><th>備考</th><th>更新日時</th></tr></thead>
                <tbody id="acBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="ruleDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td>3</td><td class="cell-strong">steam.exe</td><td>部分一致</td><td>3.ゲーム</td><td><span class="badge badge--success">有効</span></td><td class="cell-muted">ゲーム</td><td class="cell-muted">2026/08/13</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="ruleDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td>2</td><td class="cell-strong">chrome.exe</td><td>完全一致</td><td>1.通常</td><td><span class="badge badge--success">有効</span></td><td class="cell-muted">ブラウザ</td><td class="cell-muted">2026/08/12</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="ruleDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td>1</td><td class="cell-strong">word.exe</td><td>部分一致</td><td>4.勉強</td><td><span class="badge badge--danger">無効</span></td><td class="cell-muted">文書作成</td><td class="cell-muted">2026/08/11</td></tr></tbody>
              </table></div>
            </div>
          <div class="overlay" id="ruleDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-shield"></use></svg>アプリ新規登録</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">アプリ名称</label><input class="input" placeholder="例：steam.exe"></div>
            <div class="field"><label class="field__label">区分</label><select class="select"><option>1.通常</option><option>2.休憩</option><option>3.ゲーム</option><option>4.勉強</option></select></div>
            <div class="field"><label class="field__label">判定方法</label><select class="select"><option>完全一致</option><option>部分一致</option><option>正規表現</option></select></div>
            <div class="field"><label class="field__label">有効</label><select class="select"><option>有効</option><option>無効</option></select></div>
            <div class="field"><label class="field__label">備考</label><input class="input"></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" id="acSave">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
