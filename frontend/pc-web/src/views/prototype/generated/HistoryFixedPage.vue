<template>
  <div class="prototype-screen prototype-screen--history-fixed">
    <div class="tabs" id="hfTabs" role="tablist">
              <button class="tabs__tab is-active" data-tab="net" aria-selected="true">上網履歴</button>
              <button class="tabs__tab" data-tab="batch" aria-selected="false">バッチ実行履歴</button>
            </div>
    
            <section class="tabs__panel is-active" data-panel="net">
              <div class="search-panel">
                <div class="search-panel__head"><h3 class="search-panel__title">検索条件</h3><div class="search-panel__actions"><button class="btn btn--primary" id="hfSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button></div></div>
                <div class="filters"><div class="filters__row">
                  <span class="filter-item"><span class="filter-item__label">接続先：</span><input class="input" style="width:140px"></span>
                  <span class="filter-item"><span class="filter-item__label">IP：</span><input class="input" style="width:140px"></span>
                  <span class="filter-item"><span class="filter-item__label">件数：</span><select class="select"><option>50</option><option>100</option></select></span>
                </div></div>
              </div>
              <div class="table-section"><div class="table-section__head"><h3 class="table-section__title">上網履歴</h3></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th>ID</th><th>受付日時</th><th>要求日時</th><th>クライアントIP</th><th>Method</th><th>接続先</th><th>Port</th><th>URL</th><th>Protocol</th><th>UA</th></tr></thead>
                  <tbody><tr><td>1</td><td class="cell-muted">2026/08/13 10:00</td><td class="cell-muted">10:00:00</td><td>192.168.0.101</td><td>GET</td><td>khanacademy.org</td><td>443</td><td class="cell-muted">/math</td><td>HTTPS</td><td class="cell-muted">Chrome</td></tr></tbody>
                </table></div>
              </div>
            </section>
    
            <section class="tabs__panel" data-panel="batch">
              <div class="search-panel"><div class="search-panel__head"><h3 class="search-panel__title">検索条件</h3><div class="search-panel__actions"><button class="btn btn--primary" id="hfSearch2"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button></div></div></div>
              <div class="table-section"><div class="table-section__head"><h3 class="table-section__title">バッチ実行履歴</h3></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th>実行ID</th><th>タスクコード</th><th>種別</th><th>起動</th><th>状態</th><th>予定時刻</th><th>開始時刻</th><th>終了時刻</th><th>処理時間ms</th><th>メッセージ</th></tr></thead>
                  <tbody><tr><td>EX-001</td><td>batC01</td><td>C</td><td>手動</td><td><span class="badge badge--success">正常終了</span></td><td class="cell-muted">2026/08/13 00:00</td><td>00:00:01</td><td>00:05:22</td><td>321000</td><td class="cell-muted">完了</td></tr></tbody>
                </table></div>
              </div>
            </section>
  </div>
</template>

<style scoped>

</style>
