<template>
  <div class="prototype-screen prototype-screen--english-cloze">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--secondary" id="genExplain"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg>AI解説を生成</button>
                  <button class="btn btn--primary" data-open-dialog="clozeReg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規登録</button>
                  <button class="btn btn--secondary" data-open-dialog="scanDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg>スキャン履歴</button>
                  <button class="btn btn--primary" id="clozeSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="試験・練習名、問題、知識ポイント、備考" style="flex:1"></span>
                <span class="filter-item"><span class="filter-item__label">出典区分：</span><select class="select"><option>すべて</option><option>試験過去問</option><option>練習問題</option><option>その他</option></select></span>
                <span class="filter-item"><span class="filter-item__label">正誤：</span><select class="select"><option>すべて</option><option>全問正解</option><option>誤答あり</option></select></span>
                <span class="filter-item"><span class="filter-item__label">AI解説：</span><select class="select"><option>すべて</option><option>生成済み</option><option>未生成</option></select></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">穴埋め問題一覧</h3><span class="table-section__meta" id="clozeCount">全 3 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>No</th><th>登録日</th><th>出典</th><th>問題概要</th><th>知識ポイント</th><th>正誤</th><th>再挑戦</th><th>AI解説</th></tr></thead>
                <tbody id="clozeBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="clozeDetail" title="詳細"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm" data-open-dialog="clozeDetail" title="再挑戦"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button></td><td>3</td><td class="cell-muted">2026/08/13</td><td>英検 準1級</td><td class="cell-strong">予算削減による延期</td><td>語彙</td><td><span class="badge badge--success">全問正解</span></td><td class="cell-muted">正解3</td><td><span class="badge badge--success">生成済み</span></td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="clozeDetail" title="詳細"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm" data-open-dialog="clozeDetail" title="再挑戦"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button></td><td>2</td><td class="cell-muted">2026/08/11</td><td>練習問題</td><td class="cell-strong">環境問題の空所補充</td><td>文法</td><td><span class="badge badge--danger">誤答あり</span></td><td class="cell-muted">正解2/誤1</td><td><span class="badge badge--success">生成済み</span></td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="clozeDetail" title="詳細"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm" data-open-dialog="clozeDetail" title="再挑戦"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button></td><td>1</td><td class="cell-muted">2026/08/09</td><td>試験過去問</td><td class="cell-strong">長文読解の穴埋め</td><td>読解</td><td><span class="badge badge--danger">誤答あり</span></td><td class="cell-muted">正解1/誤2</td><td><span class="badge badge--warning">未生成</span></td></tr></tbody>
              </table></div>
            </div>
          <div class="overlay" id="clozeReg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">英語穴埋め問題 新規登録</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="cloze-steps"><div class="cloze-step is-active">01 出典情報</div><div class="cloze-step">02 問題画像</div><div class="cloze-step">03 OCR結果・設問確認</div></div>
            <div class="form-grid">
              <div class="field"><label class="field__label">出典区分</label><select class="select"><option>試験過去問</option><option>練習問題</option><option>その他</option></select></div>
              <div class="field"><label class="field__label">年度</label><select class="select"><option>2026</option><option>2025</option></select></div>
              <div class="field"><label class="field__label">試験・練習名</label><input class="input" placeholder="英検 準1級"></div>
              <div class="field"><label class="field__label">章節</label><input class="input" placeholder="Chapter 1"></div>
            </div>
            <div class="dropzone" style="margin-top:12px"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>問題画像を追加（臨時ファイルから選択 / AIで画像を読み取る）</div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">閉じる</button><button class="btn btn--primary" id="clozeSave">保存</button></div>
        </div>
      </div><div class="overlay" id="scanDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">スキャン履歴</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><table class="data-table"><thead><tr><th>日時</th><th>画像枚数</th><th>出典</th><th>状態</th><th>問題数</th></tr></thead><tbody><tr><td class="cell-muted">2026/08/13 10:00</td><td>3</td><td>英検 準1級</td><td><span class="badge badge--success">完了</span></td><td>12</td></tr></tbody></table></div>
        </div>
      </div><div class="overlay" id="clozeDetail" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">問題詳細</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <p style="font-size:15px;line-height:1.8">The company decided to <strong>____</strong> the project due to budget cuts.</p>
            <div class="option-list" style="margin-top:12px"><div class="option"><span class="option__index">A</span>abandon</div><div class="option is-correct"><span class="option__index">B</span>postpone</div><div class="option"><span class="option__index">C</span>extend</div></div>
            <div class="field" style="margin-top:14px"><label class="field__label">AI解説</label><div style="border:1px solid var(--color-border);border-radius:8px;padding:12px;font-size:13px">「due to budget cuts（予算削減のため）」から、延期（postpone）が文脈に合う。</div></div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">前へ</button><button class="btn btn--primary" data-dialog-close="">次へ</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .cloze-steps { display: flex; gap: 6px; margin-bottom: 16px; }
    .cloze-step { flex: 1; text-align: center; padding: 8px; border-radius: 8px; border: 1px solid var(--color-border); font-size: 12px; color: var(--color-text-muted); }
    .cloze-step.is-active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-soft); }
  
</style>
