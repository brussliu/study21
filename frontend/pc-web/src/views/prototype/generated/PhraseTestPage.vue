<template>
  <div class="prototype-screen prototype-screen--phrase-test">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--primary" id="phSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="phReset">リセット</button>
                  <button class="btn btn--secondary" id="phCreate">複数作成</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">書籍：</span><select class="select"><option>英語表現 1000</option><option>英検 熟語・構文</option></select></span>
                <span class="filter-item"><span class="filter-item__label">分類：</span><select class="select"><option>すべて</option><option>動詞構文</option><option>句動詞</option><option>固定表現</option><option>コロケーション</option></select></span>
                <span class="filter-item"><span class="filter-item__label">テスト種別：</span><select class="select"><option>すべて</option><option>A. 勉強</option><option>B. 意味 → 表現</option><option>C. 音声 → 表現</option><option>D. 文脈 → 意味</option><option>E. 構文・穴埋め</option></select></span>
                <span class="filter-item"><span class="filter-item__label">ステータス：</span><select class="select"><option>すべて</option><option>勉強済</option><option>勉強中</option><option>未勉強</option></select></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">テスト情報一覧</h3><span class="table-section__meta">全 5 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>書籍</th><th>分類</th><th>ステータス</th><th>テスト方法</th><th>テスト種別</th><th>数量</th><th>正解率</th><th>勉強時間</th></tr></thead>
                <tbody id="phBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="a" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open="a" title="表示"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>英語表現 1000</td><td>動詞構文</td><td><span class="badge badge--warning">勉強中</span></td><td>選択</td><td>A. 勉強</td><td>12</td><td>85%</td><td>10分</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="b" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open="b" title="表示"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>英語表現 1000</td><td>句動詞</td><td><span class="badge badge--neutral">未勉強</span></td><td>入力</td><td>B. 意味 → 表現</td><td>12</td><td>-</td><td>-</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="c" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open="c" title="表示"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>英検 熟語・構文</td><td>固定表現</td><td><span class="badge badge--success">勉強済</span></td><td>入力</td><td>C. 音声 → 表現</td><td>10</td><td>90%</td><td>15分</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="d" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open="d" title="表示"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>英検 熟語・構文</td><td>コロケーション</td><td><span class="badge badge--success">勉強済</span></td><td>選択</td><td>D. 文脈 → 意味</td><td>10</td><td>88%</td><td>12分</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="e" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open="e" title="表示"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>英語表現 1000</td><td>動詞構文</td><td><span class="badge badge--neutral">未勉強</span></td><td>穴埋め</td><td>E. 構文・穴埋め</td><td>10</td><td>-</td><td>-</td></tr></tbody>
              </table></div>
            </div>
  </div>
</template>

<style scoped>

</style>
