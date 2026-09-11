<template>
  <div class="prototype-screen prototype-screen--site">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--primary" id="siteSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="siteReset">リセット</button>
                  <button class="btn btn--primary" data-open-dialog="siteDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">分類：</span><input class="input" placeholder="分類" style="width:140px"></span>
                <span class="filter-item"><span class="filter-item__label">区分：</span><select class="select"><option>0.勉強</option><option>1.通常</option><option>2.休憩</option><option>3.ゲーム</option></select></span>
                <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="サイト名称 / サイトURL / 備考" style="flex:1"></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">サイト一覧</h3><span class="table-section__meta">全 3 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>サイト名称</th><th>サイトURL</th><th>区分</th><th>判定方法</th><th>分類</th><th>承認ステータス</th><th>ステータス</th><th>備考</th><th>登録日時</th></tr></thead>
                <tbody id="siteBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="siteDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">youtube.com</td><td class="cell-muted">https://youtube.com</td><td>2.休憩</td><td>含める</td><td>娯楽</td><td><span class="badge badge--success">承認済</span></td><td><span class="badge badge--success">有効</span></td><td class="cell-muted">休憩用</td><td class="cell-muted">2026/08/10</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="siteDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">khanacademy.org</td><td class="cell-muted">https://khanacademy.org</td><td>0.勉強</td><td>先頭一致</td><td>学習</td><td><span class="badge badge--success">承認済</span></td><td><span class="badge badge--success">有効</span></td><td class="cell-muted">学習</td><td class="cell-muted">2026/08/10</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-approve="" title="承認"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-circle"></use></svg></button><button class="btn btn--icon btn--sm" data-open-dialog="siteDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">example.com</td><td class="cell-muted">https://example.com</td><td>1.通常</td><td>完全一致</td><td>その他</td><td><span class="badge badge--neutral">未承認</span></td><td><span class="badge badge--danger">無効</span></td><td class="cell-muted">確認待ち</td><td class="cell-muted">2026/08/12</td></tr></tbody>
              </table></div>
              <div class="pagination"><span class="pagination__info">全 3 件</span><div class="pagination__pages"><button class="page-btn is-active">1</button></div></div>
            </div>
          <div class="overlay" id="siteDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-globe"></use></svg>サイト新規登録</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">サイト名称</label><input class="input" placeholder="例：youtube.com"></div>
            <div class="field"><label class="field__label">サイトURL</label><input class="input" placeholder="https://example.com"></div>
            <div class="field"><label class="field__label">区分</label><select class="select"><option>0.勉強</option><option>1.通常</option><option>2.休憩</option><option>3.ゲーム</option></select></div>
            <div class="field"><label class="field__label">判定方法</label><select class="select"><option>先頭一致</option><option>末尾一致</option><option>含める</option><option>完全一致</option></select></div>
            <div class="field"><label class="field__label">分類</label><select class="select"><option>学習</option><option>娯楽</option><option>ショッピング</option><option>SNS</option><option>その他</option></select></div>
            <div class="field"><label class="field__label">備考</label><textarea class="textarea" rows="2"></textarea></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" id="siteSave">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
