<template>
  <div class="prototype-screen prototype-screen--terminal-control">
    <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body"><div class="alert__title">端末コントロール</div>parent 権限のみ利用できます。端末の動作モードを一括・個別に切り替えます。</div></div>
    
            <div class="card">
              <div class="card__header"><h2 class="card__title">端末コントロール一覧</h2>
                <div class="row" style="gap:8px">
                  <span class="cell-muted">選択端末を一括切替：</span>
                  <select class="select" id="bulkMode" style="width:auto"><option>T.通常モード</option><option>B.勉強モード</option><option>K.休憩モード</option><option>G.ゲームモード</option><option>S.停止モード</option><option>J.自由モード</option></select>
                  <button class="btn btn--primary btn--sm" id="bulkApply">一括適用</button>
                </div>
              </div>
              <div class="card__body" style="padding:0">
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th class="col-check"><input type="checkbox" id="checkAll"></th><th class="col-actions">操作</th><th>IPアドレス</th><th>端末名称</th><th>端末ステータス</th><th>最終更新日時</th><th>更新者</th></tr></thead>
                  <tbody id="tcBody"><tr><td class="col-check align-center"><input type="checkbox" class="row-check"></td><td class="row-actions"><button class="btn btn--secondary btn--sm" data-mode="PC-教室1">モード切替</button></td><td class="cell-muted">192.168.0.101</td><td class="cell-strong">PC-教室1</td><td><span class="badge badge--success">B.勉強モード</span></td><td class="cell-muted">2026/08/13 10:00</td><td class="cell-muted">保護者</td></tr><tr><td class="col-check align-center"><input type="checkbox" class="row-check"></td><td class="row-actions"><button class="btn btn--secondary btn--sm" data-mode="PC-教室2">モード切替</button></td><td class="cell-muted">192.168.0.102</td><td class="cell-strong">PC-教室2</td><td><span class="badge badge--warning">K.休憩モード</span></td><td class="cell-muted">2026/08/13 09:50</td><td class="cell-muted">保護者</td></tr><tr><td class="col-check align-center"><input type="checkbox" class="row-check"></td><td class="row-actions"><button class="btn btn--secondary btn--sm" data-mode="タブレット-01">モード切替</button></td><td class="cell-muted">192.168.0.103</td><td class="cell-strong">タブレット-01</td><td><span class="badge badge--info">T.通常モード</span></td><td class="cell-muted">2026/08/13 09:40</td><td class="cell-muted">保護者</td></tr></tbody>
                </table></div>
              </div>
            </div>
  </div>
</template>

<style scoped>

</style>
