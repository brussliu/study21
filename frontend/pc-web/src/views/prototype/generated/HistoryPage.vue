<template>
  <div class="prototype-screen prototype-screen--history">
    <div class="tabs" id="hTabs" role="tablist">
              <button class="tabs__tab is-active" data-tab="net" aria-selected="true">上網履歴</button>
              <button class="tabs__tab" data-tab="browser" aria-selected="false">ブラウザ閲覧履歴</button>
              <button class="tabs__tab" data-tab="batch" aria-selected="false">バッチ実行履歴</button>
              <button class="tabs__tab" data-tab="ai" aria-selected="false">AI呼出履歴</button>
            </div>
    
            <!-- 上網履歴 -->
            <section class="tabs__panel is-active" data-panel="net">
              <div class="search-panel">
                <div class="search-panel__head"><h3 class="search-panel__title">検索条件</h3><div class="search-panel__actions"><button class="btn btn--primary" id="hSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button><button class="btn btn--secondary" id="hReset">リセット</button></div></div>
                <div class="filters"><div class="filters__row">
                  <span class="filter-item"><span class="filter-item__label">接続先：</span><input class="input" style="width:140px"></span>
                  <span class="filter-item"><span class="filter-item__label">端末名称：</span><select class="select"><option>すべて</option><option>PC-教室1</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">結果：</span><select class="select"><option>制限</option><option>通過</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">件数：</span><select class="select"><option>50</option><option>100</option><option>500</option></select></span>
                </div></div>
              </div>
              <div class="table-section"><div class="table-section__head"><h3 class="table-section__title">上網履歴</h3><span class="table-section__meta">全 2 件</span></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th class="col-actions">操作</th><th>ID</th><th>受付日時</th><th>クライアントIP</th><th>端末名称</th><th>Method</th><th>応答</th><th>結果</th><th>接続先</th><th>URL</th></tr></thead>
                  <tbody>
                    <tr><td class="row-actions"><button class="btn btn--icon btn--sm"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button></td><td>1</td><td class="cell-muted">2026/08/13 10:00</td><td>192.168.0.101</td><td>PC-教室1</td><td>GET</td><td>200</td><td><span class="badge badge--success">通過</span></td><td>khanacademy.org</td><td class="cell-muted">/math/algebra</td></tr>
                    <tr><td class="row-actions"><button class="btn btn--icon btn--sm"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button></td><td>2</td><td class="cell-muted">2026/08/13 10:05</td><td>192.168.0.101</td><td>PC-教室1</td><td>GET</td><td>200</td><td><span class="badge badge--danger">制限</span></td><td>youtube.com</td><td class="cell-muted">/watch</td></tr>
                  </tbody>
                </table></div>
                <div class="pagination"><span class="pagination__info">全 2 件</span><div class="pagination__pages"><button class="page-btn is-active">1</button></div></div>
              </div>
            </section>
    
            <!-- ブラウザ閲覧履歴 -->
            <section class="tabs__panel" data-panel="browser">
              <div class="search-panel"><div class="search-panel__head"><h3 class="search-panel__title">検索条件</h3><div class="search-panel__actions"><button class="btn btn--primary" id="hSearch2"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button></div></div>
                <div class="filters"><div class="filters__row"><span class="filter-item"><span class="filter-item__label">端末名称：</span><select class="select"><option>すべて</option><option>PC-教室1</option></select></span><span class="filter-item"><span class="filter-item__label">ドメイン：</span><input class="input" style="width:140px"></span></div></div>
              </div>
              <div class="table-section"><div class="table-section__head"><h3 class="table-section__title">ブラウザ閲覧履歴</h3></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th>ID</th><th>アクセス日時</th><th>端末ID</th><th>端末名称</th><th>イベント種別</th><th>ドメイン</th><th>ページタイトル</th><th>滞在秒数</th><th>閲覧回数</th></tr></thead>
                  <tbody><tr><td>1</td><td class="cell-muted">2026/08/13 10:00</td><td>T-01</td><td>PC-教室1</td><td>閲覧</td><td>khanacademy.org</td><td class="cell-muted">Algebra</td><td>120</td><td>3</td></tr></tbody>
                </table></div>
              </div>
            </section>
    
            <!-- バッチ実行履歴 -->
            <section class="tabs__panel" data-panel="batch">
              <div class="search-panel"><div class="search-panel__head"><h3 class="search-panel__title">検索条件</h3><div class="search-panel__actions"><button class="btn btn--primary" id="hSearch3"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button></div></div>
                <div class="filters"><div class="filters__row"><span class="filter-item"><span class="filter-item__label">タスク：</span><select class="select"><option>すべて</option><option>batC01</option></select></span><span class="filter-item"><span class="filter-item__label">状態：</span><select class="select"><option>すべて</option><option>正常終了</option></select></span></div></div>
              </div>
              <div class="table-section"><div class="table-section__head"><h3 class="table-section__title">バッチ実行履歴</h3></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th>実行ID</th><th>タスクコード</th><th>種別</th><th>起動</th><th>状態</th><th>予定時刻</th><th>開始時刻</th><th>終了時刻</th><th>処理時間ms</th><th>依頼者</th><th>メッセージ</th></tr></thead>
                  <tbody><tr><td>EX-001</td><td>batC01</td><td>C</td><td>手動</td><td><span class="badge badge--success">正常終了</span></td><td class="cell-muted">2026/08/13 00:00</td><td>00:00:01</td><td>00:05:22</td><td>321000</td><td>system</td><td class="cell-muted">完了</td></tr></tbody>
                </table></div>
              </div>
            </section>
    
            <!-- AI呼出履歴 -->
            <section class="tabs__panel" data-panel="ai">
              <div class="search-panel"><div class="search-panel__head"><h3 class="search-panel__title">検索条件</h3><div class="search-panel__actions"><button class="btn btn--primary" id="hSearch4"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button></div></div>
                <div class="filters"><div class="filters__row"><span class="filter-item"><span class="filter-item__label">AI区分：</span><select class="select"><option>すべて</option><option>deepseek</option><option>doubao</option><option>chatgpt</option><option>gemini</option></select></span><span class="filter-item"><span class="filter-item__label">結果：</span><select class="select"><option>すべて</option><option>成功</option><option>失敗</option></select></span></div></div>
              </div>
              <div class="table-section"><div class="table-section__head"><h3 class="table-section__title">AI呼出履歴</h3></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th class="col-actions">操作</th><th>ID</th><th>実行ID</th><th>タスクコード</th><th>AI区分</th><th>モデル名</th><th>結果</th><th>開始日時</th><th>終了日時</th><th>処理時間ms</th><th>エラー概要</th></tr></thead>
                  <tbody><tr><td class="row-actions"><button class="btn btn--secondary btn--sm" data-open-dialog="aiDlg">詳細</button></td><td>1</td><td>EX-001</td><td>batC01</td><td>deepseek</td><td>deepseek-chat</td><td><span class="badge badge--success">成功</span></td><td class="cell-muted">2026/08/13 10:00</td><td class="cell-muted">10:00:03</td><td>3200</td><td class="cell-muted">—</td></tr></tbody>
                </table></div>
              </div>
            </section>
          <div class="overlay" id="aiDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">AI呼出履歴詳細</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="field" style="margin-bottom:10px"><label class="field__label">プロンプト</label><div style="border:1px solid var(--color-border);border-radius:8px;padding:10px;font-size:13px">英単語テストを集計してください。</div></div>
            <div class="field"><label class="field__label">AIレスポンス</label><div style="border:1px solid var(--color-border);border-radius:8px;padding:10px;font-size:13px">集計が完了しました。（Mock）</div></div>
          </div>
          <div class="dialog__foot"><button class="btn btn--primary" data-dialog-close="">閉じる</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
