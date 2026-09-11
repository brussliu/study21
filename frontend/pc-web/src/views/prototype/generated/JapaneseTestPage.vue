<template>
  <div class="prototype-screen prototype-screen--japanese-test">
    <div class="tabs" role="tablist"><button class="tabs__tab is-active" aria-selected="true"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-type"></use></svg>日本語単語</button></div>
    
            <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--primary" id="jpStart"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>テスト開始</button>
                  <button class="btn btn--primary" id="jpSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="jpReset">リセット</button>
                  <button class="btn btn--secondary" id="jpCreate">複数作成</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">レベル：</span><select class="select"><option>すべて</option><option>N3</option><option>N2</option><option>N1</option></select></span>
                <span class="filter-item"><span class="filter-item__label">書籍：</span><select class="select"><option>日本語語彙 N2</option></select></span>
                <span class="filter-item"><span class="filter-item__label">分類：</span><select class="select"><option>すべて</option><option>Unit001</option><option>Unit002</option><option>Unit003</option><option>Unit004</option><option>Unit005</option></select></span>
                <span class="filter-item"><span class="filter-item__label">テスト種別：</span><select class="select"><option>すべて</option><option>A.勉強</option><option>B.意味→日本語</option><option>C.漢字→読み</option><option>D.文脈詞義判断</option><option>E.漢字選択</option></select></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">テスト情報一覧</h3><span class="table-section__meta">全 5 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>書籍</th><th>分類</th><th>ステータス</th><th>テスト方法</th><th>テスト種別</th><th>テスト期間</th><th>数量</th><th>正解率</th><th>勉強時間</th></tr></thead>
                <tbody id="jpBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="a" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button></td><td>日本語語彙 N2</td><td>Unit001</td><td><span class="badge badge--warning">勉強中</span></td><td>単語学習</td><td>A.勉強</td><td class="cell-muted">2026/08/13</td><td>10</td><td>85%</td><td>10分</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="b" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button></td><td>日本語語彙 N2</td><td>Unit001</td><td><span class="badge badge--neutral">未勉強</span></td><td>入力</td><td>B.意味→日本語</td><td class="cell-muted">-</td><td>10</td><td>-</td><td>-</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="c" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button></td><td>日本語語彙 N2</td><td>Unit002</td><td><span class="badge badge--warning">勉強中</span></td><td>入力・選択</td><td>C.漢字→読み</td><td class="cell-muted">2026/08/12</td><td>10</td><td>88%</td><td>12分</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="d" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button></td><td>日本語語彙 N2</td><td>Unit002</td><td><span class="badge badge--success">勉強済</span></td><td>選択</td><td>D.文脈詞義判断</td><td class="cell-muted">2026/08/11</td><td>10</td><td>90%</td><td>8分</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="e" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button></td><td>日本語語彙 N2</td><td>Unit003</td><td><span class="badge badge--neutral">未勉強</span></td><td>選択</td><td>E.漢字選択</td><td class="cell-muted">-</td><td>10</td><td>-</td><td>-</td></tr></tbody>
              </table></div>
            </div>
  </div>
</template>

<style scoped>

</style>
