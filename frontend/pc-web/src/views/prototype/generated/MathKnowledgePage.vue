<template>
  <div class="prototype-screen prototype-screen--math-knowledge">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--secondary" id="reload">再読み込み</button>
                  <button class="btn btn--primary" data-route-screen="math-knowledge-input"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>知識点を追加</button>
                  <button class="btn btn--primary" id="mkSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="mkReset">リセット</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">分野：</span><select class="select"><option>すべて</option><option>代数</option><option>関数</option><option>図形</option><option>確率</option><option>整数</option><option>データ分析</option></select></span>
                <span class="filter-item"><span class="filter-item__label">難易度：</span><select class="select"><option>すべて</option><option>入門</option><option>易</option><option>標準</option><option>やや難</option><option>難</option></select></span>
                <span class="filter-item"><span class="filter-item__label">ステータス：</span><select class="select"><option>すべて</option><option>記載中</option><option>記載済</option></select></span>
                <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="知識点名、公式、前置知識、誤問題" style="flex:1"></span>
              </div></div>
            </div>
    
            <div class="card"><div class="card__header"><h2 class="card__title">知識点一覧</h2><span class="card__sub" id="mkCount">全 3 件</span></div>
              <div class="card__body"><div class="kp-grid" id="kpGrid"><div class="kp-card"><div class="row" style="justify-content:space-between"><h3>三角形の合同条件</h3><span class="badge badge--success">記載済</span></div><div class="kp-card__formula">SSS / SAS / ASA</div><div class="row" style="gap:6px"><span class="badge badge--subject-ma">図形</span><span class="badge badge--outline">標準</span></div><div class="row" style="gap:6px;margin-top:10px"><button class="btn btn--secondary btn--sm" data-open-dialog="studyDlg">学習</button><button class="btn btn--icon btn--sm" data-route-screen="math-knowledge-input"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button></div></div><div class="kp-card"><div class="row" style="justify-content:space-between"><h3>平方完成</h3><span class="badge badge--success">記載済</span></div><div class="kp-card__formula">y=a(x-p)²+q</div><div class="row" style="gap:6px"><span class="badge badge--subject-ma">関数</span><span class="badge badge--outline">標準</span></div><div class="row" style="gap:6px;margin-top:10px"><button class="btn btn--secondary btn--sm" data-open-dialog="studyDlg">学習</button><button class="btn btn--icon btn--sm" data-route-screen="math-knowledge-input"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button></div></div><div class="kp-card"><div class="row" style="justify-content:space-between"><h3>確率の基本</h3><span class="badge badge--warning">記載中</span></div><div class="kp-card__formula">P(A)=n(A)/n(U)</div><div class="row" style="gap:6px"><span class="badge badge--subject-ma">確率</span><span class="badge badge--outline">入門</span></div><div class="row" style="gap:6px;margin-top:10px"><button class="btn btn--secondary btn--sm" data-open-dialog="studyDlg">学習</button><button class="btn btn--icon btn--sm" data-route-screen="math-knowledge-input"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button></div></div></div></div>
            </div>
          <div class="overlay" id="studyDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">学習</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <h3 style="font-size:16px;margin-bottom:6px">三角形の合同条件</h3>
            <div class="kp-card__formula">3組の辺がそれぞれ等しい（SSS）</div>
            <p class="cell-muted" style="margin-top:8px">2つの三角形が合同であるための条件。対応する辺や角が等しいことを示す。</p>
            <div class="row" style="gap:6px;margin-top:12px"><span class="badge badge--subject-ma">図形</span><span class="badge badge--outline">標準</span><span class="badge badge--success">記載済</span></div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-route-screen="math-knowledge-input">編集</button><button class="btn btn--secondary" data-open-dialog="wrongDlg">誤問題詳細</button><button class="btn btn--primary" data-dialog-close="">閉じる</button></div>
        </div>
      </div><div class="overlay" id="wrongDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">誤問題詳細</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="stack" style="gap:8px">
            <div class="row" style="justify-content:space-between;border:1px solid var(--color-border);border-radius:8px;padding:10px 14px"><strong>三角形の合同証明（問題ID: MW-0012）</strong><span class="badge badge--warning">訂正中</span></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--primary" data-dialog-close="">閉じる</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .kp-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
    .kp-card { border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 16px; background: var(--color-surface); box-shadow: var(--shadow-sm); border-top: 3px solid var(--subject-math); }
    .kp-card h3 { font-size: 15px; margin-bottom: 4px; }
    .kp-card__formula { font-size: 15px; font-family: "SF Mono", Consolas, monospace; margin: 8px 0; }
  
</style>
