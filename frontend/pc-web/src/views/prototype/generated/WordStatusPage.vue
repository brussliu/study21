<template>
  <div class="prototype-screen prototype-screen--word-status">
    <div class="tabs" id="wsTabs" role="tablist">
              <button class="tabs__tab is-active" data-tab="book" aria-selected="true">書籍別</button>
              <button class="tabs__tab" data-tab="word" aria-selected="false">単語別</button>
            </div>
    
            <section class="tabs__panel is-active" data-panel="book">
              <div class="search-panel">
                <div class="search-panel__head">
                  <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                  <div class="search-panel__actions"><button class="btn btn--primary" id="wsSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button><button class="btn btn--secondary" id="wsReset">リセット</button></div>
                </div>
                <div class="filters"><div class="filters__row">
                  <span class="filter-item"><span class="filter-item__label">等級：</span><select class="select"><option>初級編</option><option>中級編</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">書籍：</span><select class="select"><option>キクタン</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">分類：</span><select class="select"><option>すべて</option><option>Day01</option></select></span>
                </div></div>
              </div>
              <div class="table-section">
                <div class="table-section__head"><h3 class="table-section__title">単語勉強状況一覧</h3></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th>書籍</th><th>分類</th><th>単語</th><th>日本語</th><th>A.勉強</th><th>B.中日訳英</th><th>C.音訳英</th><th>D.英訳中日</th><th>総合</th></tr></thead>
                  <tbody>
                    <tr><td>キクタン</td><td>Day01</td><td class="cell-strong">abandon</td><td>捨てる</td><td><span class="badge badge--success">済</span></td><td><span class="badge badge--warning">中</span></td><td><span class="badge badge--neutral">未</span></td><td><span class="badge badge--success">済</span></td><td><span class="badge badge--primary">勉強中</span></td></tr>
                    <tr><td>キクタン</td><td>Day01</td><td class="cell-strong">achieve</td><td>達成する</td><td><span class="badge badge--success">済</span></td><td><span class="badge badge--success">済</span></td><td><span class="badge badge--warning">中</span></td><td><span class="badge badge--success">済</span></td><td><span class="badge badge--success">勉強済</span></td></tr>
                  </tbody>
                </table></div>
              </div>
            </section>
    
            <section class="tabs__panel" data-panel="word">
              <div class="search-panel">
                <div class="search-panel__head">
                  <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件（単語別）</h3>
                  <div class="search-panel__actions"><button class="btn btn--primary" id="wsCreate">テスト作成</button><button class="btn btn--secondary" id="wsMastery">標識</button><button class="btn btn--primary" id="wsSearch2"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button></div>
                </div>
              </div>
              <div class="table-section">
                <div class="table-section__head"><h3 class="table-section__title">単語勉強状況一覧（単語別）</h3></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th>習得済</th><th>単語</th><th>日本語</th><th>収録書籍</th><th>収録回数</th><th>A</th><th>B</th><th>C</th><th>D</th><th>総合</th></tr></thead>
                  <tbody>
                    <tr><td><span class="badge badge--success">習得済</span></td><td class="cell-strong">abandon</td><td>捨てる</td><td>キクタン</td><td>2</td><td>85%</td><td>72%</td><td>60%</td><td>88%</td><td><span class="badge badge--primary">勉強中</span></td></tr>
                    <tr><td><span class="badge badge--neutral">-</span></td><td class="cell-strong">achieve</td><td>達成する</td><td>キクタン</td><td>1</td><td>90%</td><td>80%</td><td>75%</td><td>85%</td><td><span class="badge badge--success">勉強済</span></td></tr>
                  </tbody>
                </table></div>
              </div>
            </section>
          <div class="overlay" id="createDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">テスト作成</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="cell-muted" style="margin-bottom:12px">親画面の検索条件：キクタン / Day01（N語）</div>
            <div class="field" style="margin-bottom:10px"><label class="field__label">テスト種別</label><select class="select"><option>A勉強</option><option>B中日訳英</option><option>C音訳英</option><option>D英訳中日</option></select></div>
            <div class="field" style="margin-bottom:10px"><label class="field__label">出題数</label><select class="select"><option>全部</option><option>10</option><option>20</option><option>30</option><option>50</option><option>80</option><option>100</option></select></div>
            <div class="cell-muted">B・C・Dでは習得済の単語を除外します。</div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">作成</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
