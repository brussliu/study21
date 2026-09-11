<template>
  <div class="prototype-screen prototype-screen--geometry">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--secondary" id="reload">再読込</button>
                  <button class="btn btn--primary" data-open-dialog="editorDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規作成</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="図形名、メモ、タグ" style="flex:1"></span>
                <span class="filter-item"><span class="filter-item__label">並び順：</span><select class="select"><option>更新日が新しい順</option><option>作成日が新しい順</option><option>名前順</option></select></span>
                <span class="filter-item"><span class="filter-item__label">タイプ：</span><select class="select"><option>すべて</option><option>幾何図形</option><option>関数グラフ</option></select></span>
              </div></div>
            </div>
    
            <div class="card"><div class="card__header"><h2 class="card__title">図形一覧</h2><span class="card__sub">全 3 件</span></div>
              <div class="card__body"><div class="geo-grid" id="geoGrid"><div class="geo-card"><div class="geo-card__thumb"><svg class="icon" style="width:36px;height:36px"><use href="/prototype-assets/icons/icons.svg#i-sigma"></use></svg></div><div class="geo-card__body"><strong>三角形 ABC</strong><div class="row" style="gap:6px;margin-top:4px"><span class="badge badge--subject-ma">幾何図形</span><span class="badge badge--outline">三角形</span></div><div class="cell-muted" style="font-size:12px;margin-top:4px">更新：2026/08/13</div></div><div class="geo-card__foot"><button class="btn btn--secondary btn--sm" data-open-dialog="editorDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>編集</button><button class="btn btn--icon btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-copy"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></div></div><div class="geo-card"><div class="geo-card__thumb"><svg class="icon" style="width:36px;height:36px"><use href="/prototype-assets/icons/icons.svg#i-sigma"></use></svg></div><div class="geo-card__body"><strong>円 O</strong><div class="row" style="gap:6px;margin-top:4px"><span class="badge badge--subject-ma">幾何図形</span><span class="badge badge--outline">円</span></div><div class="cell-muted" style="font-size:12px;margin-top:4px">更新：2026/08/12</div></div><div class="geo-card__foot"><button class="btn btn--secondary btn--sm" data-open-dialog="editorDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>編集</button><button class="btn btn--icon btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-copy"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></div></div><div class="geo-card"><div class="geo-card__thumb"><svg class="icon" style="width:36px;height:36px"><use href="/prototype-assets/icons/icons.svg#i-sigma"></use></svg></div><div class="geo-card__body"><strong>放物線 y=x²</strong><div class="row" style="gap:6px;margin-top:4px"><span class="badge badge--subject-ma">関数グラフ</span><span class="badge badge--outline">関数</span></div><div class="cell-muted" style="font-size:12px;margin-top:4px">更新：2026/08/10</div></div><div class="geo-card__foot"><button class="btn btn--secondary btn--sm" data-open-dialog="editorDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>編集</button><button class="btn btn--icon btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-copy"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></div></div></div></div>
            </div>
          <div class="overlay" id="editorDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--full" role="dialog" aria-modal="true" data-size="full">
          <div class="dialog__head"><h2 class="dialog__title">図形作成</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body" style="padding:0"><iframe style="width:100%;height:100%;min-height:70vh;border:none" title="図形作成" data-route-screen="geometry-draw"></iframe></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .geo-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
    .geo-card { border: 1px solid var(--color-border); border-radius: var(--radius-lg); overflow: hidden; background: var(--color-surface); box-shadow: var(--shadow-sm); }
    .geo-card__thumb { aspect-ratio: 4/3; background: var(--color-surface-alt); display: flex; align-items: center; justify-content: center; color: var(--color-text-muted); }
    .geo-card__body { padding: 12px 14px; }
    .geo-card__foot { display: flex; gap: 6px; padding: 8px 14px; border-top: 1px solid var(--color-border); }
  
</style>
