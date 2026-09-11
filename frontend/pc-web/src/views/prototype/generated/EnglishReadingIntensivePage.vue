<template>
  <div class="prototype-screen prototype-screen--english-reading-intensive">
    <div class="batch-bar" id="batchBar"><span class="batch-chip is-running">batC91 文章画像OCR（確認中）</span><span class="batch-chip is-running">batC15 OCR結果集計（確認中）</span><span class="batch-chip is-running">batC15-3 方式B文字構造化（確認中）</span><span class="batch-chip">batC16 基本情報・導読（確認中）</span><span class="batch-chip">batC17 解説・語彙（確認中）</span><span class="batch-chip">batC18 設問解析（確認中）</span></div>
    
            <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--primary" data-open-dialog="registerDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規登録</button>
                  <button class="btn btn--primary" id="irSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="irReset">リセット</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" id="irKw" placeholder="タイトル・出典・テーマ" style="flex:1"></span>
                <span class="filter-item"><span class="filter-item__label">処理状態：</span><select class="select" id="irStatus"><option>すべて</option><option>生成済み</option><option>AI生成中</option><option>OCR処理中</option></select></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">精読一覧</h3><span class="table-section__meta" id="irCount">全 3 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>No</th><th>登録日</th><th>タイトル・出典</th><th>出典区分</th><th>英検級</th><th>難易度</th><th>語数</th><th>問題数</th><th>処理ステータス</th><th>再挑戦</th></tr></thead>
                <tbody id="irBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-study="3" title="学習"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm" data-practice="" title="再挑戦"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="3" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td>3</td><td class="cell-muted">2026/08/13</td><td class="cell-strong">The Value of Reading</td><td>練習問題</td><td>準1級</td><td>中</td><td>420</td><td>4</td><td><span class="badge badge--success">処理済</span></td><td class="cell-muted">正解3/不正解1</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-study="2" title="学習"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm" data-practice="" title="再挑戦"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="2" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td>2</td><td class="cell-muted">2026/08/11</td><td class="cell-strong">Climate Change Essay</td><td>試験過去問</td><td>2級</td><td>高</td><td>560</td><td>5</td><td><span class="badge badge--success">処理済</span></td><td class="cell-muted">正解4/不正解1</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-study="1" title="学習"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm" data-practice="" title="再挑戦"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="1" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td>1</td><td class="cell-muted">2026/08/09</td><td class="cell-strong">AI and Society</td><td>その他</td><td>準1級</td><td>低</td><td>380</td><td>3</td><td><span class="badge badge--warning">処理中</span></td><td class="cell-muted">-</td></tr></tbody>
              </table></div>
            </div>
          <div class="overlay" id="registerDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">英語長文 精読 新規登録（AI自動生成）</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="stepper">
              <div class="step is-active">01 OCR実行</div><div class="step">02 基本情報・導読</div><div class="step">03 解説・語彙</div><div class="step">04 設問解析</div>
            </div>
            <div class="dropzone" style="margin-bottom:12px"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>画像を追加（ファイル選択 / 臨時ファイル）0/20枚</div>
            <div class="form-grid">
              <div class="field"><label class="field__label">タイトル</label><input class="input" placeholder="タイトル"></div>
              <div class="field"><label class="field__label">出典区分</label><select class="select"><option>試験過去問</option><option>練習問題</option><option>その他</option></select></div>
              <div class="field"><label class="field__label">試験・練習名</label><input class="input" placeholder="英検 準1級"></div>
              <div class="field"><label class="field__label">等級</label><select class="select"><option>1級</option><option>準1級</option><option>2級及び以下</option></select></div>
              <div class="field"><label class="field__label">難易度</label><select class="select"><option>低</option><option>中</option><option>高</option></select></div>
              <div class="field"><label class="field__label">テーマ</label><input class="input" placeholder="環境問題"></div>
            </div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">一覧へ戻る</button><button class="btn btn--secondary" id="runAll">01→04 一括実行</button><button class="btn btn--primary" id="saveIr">保存</button></div>
        </div>
      </div><div class="overlay" id="studyDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--full" role="dialog" aria-modal="true" data-size="full">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book"></use></svg>学習：The Value of Reading</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="tabs" id="studyTabs"><button class="tabs__tab is-active" data-tab="read">原文精読</button><button class="tabs__tab" data-tab="qa">設問解析</button><button class="tabs__tab" data-tab="vocab">重点語彙</button></div>
            <div class="tabs__panel is-active" data-panel="read"><div class="reader-text" style="padding:16px">Reading expands our understanding of the world. It allows us to <mark>experience</mark> lives we have never lived and to see through the eyes of others.</div></div>
            <div class="tabs__panel" data-panel="qa"><div class="option-list"><div class="option is-correct"><span class="option__index">A</span>正解：読解は世界への理解を広げる</div><div class="option"><span class="option__index">B</span>誤答選択肢 1</div><div class="option"><span class="option__index">C</span>誤答選択肢 2</div></div></div>
            <div class="tabs__panel" data-panel="vocab"><table class="data-table"><thead><tr><th>語彙</th><th>意味</th><th>本文の文脈</th><th>操作</th></tr></thead><tbody><tr><td class="cell-strong">experience</td><td>経験する</td><td class="cell-muted">to experience lives</td><td><button class="btn btn--secondary btn--sm">生詞本へ</button></td></tr></tbody></table></div>
          </div>
        </div>
      </div><div class="overlay" id="practiceDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">再挑戦</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="option-list"><div class="option"><span class="option__index">A</span>選択肢 1</div><div class="option is-correct"><span class="option__index">B</span>選択肢 2（正解）</div><div class="option"><span class="option__index">C</span>選択肢 3</div><div class="option"><span class="option__index">D</span>選択肢 4</div></div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">前へ</button><button class="btn btn--primary" data-dialog-close="">次へ</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .batch-bar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
    .batch-chip { font-size: 12px; padding: 5px 10px; border: 1px solid var(--color-border); border-radius: 999px; color: var(--color-text-muted); }
    .batch-chip.is-running { border-color: var(--color-primary); color: var(--color-primary); }
    .stepper { display: flex; gap: 6px; margin-bottom: 16px; }
    .step { flex: 1; text-align: center; padding: 8px; border-radius: 8px; border: 1px solid var(--color-border); font-size: 12px; color: var(--color-text-muted); }
    .step.is-active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-soft); }
    .step.is-done { border-color: var(--color-success); color: var(--color-success); }
  
</style>
