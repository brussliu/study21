<template>
  <div class="prototype-screen prototype-screen--english-word-textbook">
    <div class="wtb-metrics">
              <div class="stat-card"><div><div class="stat-card__label">グループ数</div><div class="stat-card__value">4</div></div></div>
              <div class="stat-card"><div><div class="stat-card__label">総ページ数</div><div class="stat-card__value">32</div></div></div>
              <div class="stat-card"><div><div class="stat-card__label">処理中</div><div class="stat-card__value">1</div></div></div>
              <div class="stat-card"><div><div class="stat-card__label">完了</div><div class="stat-card__value">2</div></div></div>
            </div>
    
            <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--secondary" id="wtbReset">リセット</button>
                  <button class="btn btn--primary" data-route-screen="english-word-textbook-group"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item filter-item--grow"><span class="filter-item__label">教材名：</span><input class="input" placeholder="教材名" style="flex:1"></span>
                <span class="filter-item"><span class="filter-item__label">状態：</span><select class="select"><option>すべて</option><option>作成済</option><option>アップロード中</option><option>補正中</option><option>裁切確認</option><option>ペア確認</option><option>合成中</option><option>AI認識中</option><option>結果確認中</option><option>完了</option><option>エラー</option></select></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">教材取込グループ一覧</h3><span class="table-section__meta">全 4 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>グループID</th><th>教材名</th><th>ページ数</th><th>ペア数</th><th>認識語彙</th><th>状態</th><th>登録日時</th><th>更新日時</th></tr></thead>
                <tbody id="wtbBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" title="詳細" data-route-screen="english-word-textbook-group"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="WTB-0004" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">WTB-0004</td><td>英単語ターゲット 1900</td><td>8</td><td>4</td><td>120</td><td><span class="badge badge--warning">AI認識中</span></td><td class="cell-muted">2026/08/13</td><td class="cell-muted">2026/08/13</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" title="詳細" data-route-screen="english-word-textbook-group"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="WTB-0003" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">WTB-0003</td><td>速読英単語</td><td>10</td><td>5</td><td>180</td><td><span class="badge badge--success">完了</span></td><td class="cell-muted">2026/08/13</td><td class="cell-muted">2026/08/13</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" title="詳細" data-route-screen="english-word-textbook-group"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="WTB-0002" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">WTB-0002</td><td>DUO 3.0</td><td>6</td><td>3</td><td>80</td><td><span class="badge badge--success">完了</span></td><td class="cell-muted">2026/08/13</td><td class="cell-muted">2026/08/13</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" title="詳細" data-route-screen="english-word-textbook-group"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="WTB-0001" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">WTB-0001</td><td>システム英単語</td><td>8</td><td>4</td><td>0</td><td><span class="badge badge--danger">エラー</span></td><td class="cell-muted">2026/08/13</td><td class="cell-muted">2026/08/13</td></tr></tbody>
              </table></div>
            </div>
  </div>
</template>

<style scoped>
.wtb-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; } @media (max-width:640px){.wtb-metrics{grid-template-columns:repeat(2,1fr)}}
</style>
