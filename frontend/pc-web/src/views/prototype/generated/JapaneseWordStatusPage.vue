<template>
  <div class="prototype-screen prototype-screen--japanese-word-status">
    <div class="tabs" id="jwsTabs" role="tablist">
              <button class="tabs__tab is-active" data-tab="book" aria-selected="true">書籍別</button>
              <button class="tabs__tab" data-tab="word" aria-selected="false">単語別</button>
            </div>
    
            <section class="tabs__panel is-active" data-panel="book">
              <div class="search-panel">
                <div class="search-panel__head">
                  <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                  <div class="search-panel__actions"><button class="btn btn--primary" id="jwsSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button><button class="btn btn--secondary" id="jwsReset">リセット</button><button class="btn btn--secondary" id="jwsAddType">種別を追加</button></div>
                </div>
                <div class="filters"><div class="filters__row">
                  <span class="filter-item"><span class="filter-item__label">レベル：</span><select class="select"><option>N2</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">書籍：</span><select class="select"><option>日本語語彙 N2</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">分類：</span><select class="select"><option>すべて</option><option>Unit001</option></select></span>
                </div></div>
              </div>
              <div class="table-section">
                <div class="table-section__head"><h3 class="table-section__title">日本語単語勉強状況一覧</h3><span class="table-section__meta">全 3 件</span></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th>書籍</th><th>分類</th><th>単語</th><th>読み</th><th>中国語</th><th>B.意味→日本語</th><th>C.漢字→読み</th><th>D.文脈詞義判断</th><th>E.漢字選択</th><th>総合</th></tr></thead>
                  <tbody>
                    <tr><td>日本語語彙 N2</td><td>Unit001</td><td class="cell-strong">抑える</td><td>おさえる</td><td>控制</td><td>72%/5/学習中</td><td>88%/4/習得</td><td>76%/4/学習中</td><td>63%/3/復習</td><td><span class="badge badge--warning">73%</span></td></tr>
                    <tr><td>日本語語彙 N2</td><td>Unit001</td><td class="cell-strong">押さえる</td><td>おさえる</td><td>按住</td><td>90%/3/習得</td><td>85%/3/習得</td><td>80%/2/習得</td><td>92%/3/習得</td><td><span class="badge badge--success">87%</span></td></tr>
                    <tr><td>日本語語彙 N2</td><td>Unit002</td><td class="cell-strong">覚える</td><td>おぼえる</td><td>记住</td><td>-/0/未学習</td><td>-/0/未学習</td><td>-/0/未学習</td><td>-/0/未学習</td><td><span class="badge badge--neutral">未学習</span></td></tr>
                  </tbody>
                </table></div>
              </div>
            </section>
    
            <section class="tabs__panel" data-panel="word">
              <div class="search-panel">
                <div class="search-panel__head"><h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件（単語別）</h3></div>
              </div>
              <div class="table-section">
                <div class="table-section__head"><h3 class="table-section__title">日本語単語勉強状況一覧（単語別）</h3></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th>単語</th><th>読み</th><th>中国語</th><th>収録書籍</th><th>B</th><th>C</th><th>D</th><th>E</th><th>総合</th></tr></thead>
                  <tbody>
                    <tr><td class="cell-strong">抑える</td><td>おさえる</td><td>控制</td><td>日本語語彙 N2</td><td>72%</td><td>88%</td><td>76%</td><td>63%</td><td><span class="badge badge--warning">73%</span></td></tr>
                    <tr><td class="cell-strong">押さえる</td><td>おさえる</td><td>按住</td><td>日本語語彙 N2</td><td>90%</td><td>85%</td><td>80%</td><td>92%</td><td><span class="badge badge--success">87%</span></td></tr>
                  </tbody>
                </table></div>
              </div>
            </section>
  </div>
</template>

<style scoped>

</style>
