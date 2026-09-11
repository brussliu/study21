<template>
  <div class="prototype-screen prototype-screen--word">
    <div class="tabs" id="wordTabs" role="tablist">
              <button class="tabs__tab is-active" data-tab="beginner" aria-selected="true">初級編</button>
              <button class="tabs__tab" data-tab="intermediate" aria-selected="false">中級編</button>
              <button class="tabs__tab" data-tab="phrase" aria-selected="false">熟語</button>
            </div>
    
            <!-- 初級編 -->
            <section class="tabs__panel is-active" data-panel="beginner">
              <div class="search-panel">
                <div class="search-panel__head">
                  <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                  <div class="search-panel__actions">
                    <button class="btn btn--secondary" data-open-dialog="wordDlg" disabled>新規単語</button>
                    <button class="btn btn--secondary" data-ai="中国語翻訳">中国語翻訳</button>
                    <button class="btn btn--secondary" data-ai="日本語翻訳">日本語翻訳</button>
                    <button class="btn btn--secondary" data-ai="英語発音取得">英語発音取得</button>
                    <button class="btn btn--secondary" data-ai="英訳中日質問取得">英訳中日質問取得</button>
                    <button class="btn btn--secondary" data-ai="中国語説明取得">中国語説明取得</button>
                    <button class="btn btn--secondary" data-ai="日本語説明取得">日本語説明取得</button>
                    <button class="btn btn--primary" id="wSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                    <button class="btn btn--secondary" id="wReset">リセット</button>
                  </div>
                </div>
                <div class="filters"><div class="filters__row">
                  <span class="filter-item"><span class="filter-item__label">等級：</span><select class="select"><option>すべて</option><option>2級</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">書籍：</span><select class="select"><option>すべて</option><option>キクタン</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">分類：</span><select class="select"><option>すべて</option><option>Day01</option></select></span>
                  <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="単語・日本語・中国語" style="flex:1"></span>
                </div></div>
              </div>
              <div class="table-section">
                <div class="table-section__head"><h3 class="table-section__title">単語一覧</h3><span class="table-section__meta">全 3 件</span></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th class="col-actions">操作</th><th>書籍</th><th>分類</th><th>SEQ</th><th>英語単語</th><th>日本語訳</th><th>中国語訳</th><th>例文1</th><th>例文1日本語</th></tr></thead>
                  <tbody id="beginBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg" title="編集"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-open-ai="" title="AI説明"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg></button></td><td>キクタン</td><td>Day01</td><td>1</td><td class="cell-strong">abandon</td><td>捨てる</td><td>捨てる</td><td class="cell-muted">He abandoned the plan.</td><td class="cell-muted">彼は計画を捨てた。</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg" title="編集"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-open-ai="" title="AI説明"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg></button></td><td>キクタン</td><td>Day01</td><td>2</td><td class="cell-strong">achieve</td><td>達成する</td><td>達成</td><td class="cell-muted">She achieved her goal.</td><td class="cell-muted">彼は計画を捨てた。</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg" title="編集"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-open-ai="" title="AI説明"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg></button></td><td>キクタン</td><td>Day02</td><td>1</td><td class="cell-strong">acquire</td><td>獲得する</td><td>獲得</td><td class="cell-muted">He acquired a skill.</td><td class="cell-muted">彼は計画を捨てた。</td></tr></tbody>
                </table></div>
              </div>
            </section>
    
            <!-- 中級編 -->
            <section class="tabs__panel" data-panel="intermediate">
              <div class="search-panel">
                <div class="search-panel__head">
                  <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件（中級編）</h3>
                  <div class="search-panel__actions">
                    <button class="btn btn--secondary" data-ai="文脈英訳質問取得">文脈英訳質問取得</button>
                    <button class="btn btn--secondary" data-ai="英訳中日質問取得">英訳中日質問取得</button>
                    <button class="btn btn--secondary" data-ai="詳細情報取得">詳細情報取得</button>
                    <button class="btn btn--primary" id="wSearchInt"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                    <button class="btn btn--secondary" data-open-dialog="wordNewDlg">新規</button>
                    <button class="btn btn--secondary" id="wResetInt">リセット</button>
                  </div>
                </div>
                <div class="filters"><div class="filters__row">
                  <span class="filter-item"><span class="filter-item__label">書籍：</span><select class="select"><option>中級英単語 1800</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">分類：</span><span class="range-input"><select class="select"><option>Unit 04-06</option></select><span class="range-input__sep">～</span><select class="select"><option>Unit 10-12</option></select></span></span>
                  <span class="filter-item"><span class="filter-item__label">詳細取得状態：</span><select class="select"><option>すべて</option><option>未取得</option><option>処理中</option><option>取得済み</option><option>エラー</option></select></span>
                </div></div>
              </div>
              <div class="table-section">
                <div class="table-section__head"><h3 class="table-section__title">中級編 単語一覧</h3><span class="table-section__meta">全 3 件</span></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th class="col-actions">操作</th><th>単語ID</th><th>書籍</th><th>分類</th><th>英語単語</th><th>詳細情報件数</th><th>詳細取得状態</th><th>文脈英訳質問</th><th>英訳中日質問</th><th>最終更新</th></tr></thead>
                  <tbody id="intBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-open-ai="" title="AI説明"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg></button></td><td>MW-0001</td><td>中級英単語 1800</td><td>Unit 04-06</td><td class="cell-strong">significant</td><td>2</td><td><span class="badge badge--success">取得済み</span></td><td>取得済</td><td>取得済</td><td class="cell-muted">2026/08/13</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-open-ai="" title="AI説明"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg></button></td><td>MW-0002</td><td>中級英単語 1800</td><td>Unit 04-06</td><td class="cell-strong">considerable</td><td>0</td><td><span class="badge badge--neutral">未取得</span></td><td>未取得</td><td>未取得</td><td class="cell-muted">2026/08/12</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-open-ai="" title="AI説明"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg></button></td><td>MW-0003</td><td>中級英単語 1800</td><td>Unit 04-06</td><td class="cell-strong">substantial</td><td>1</td><td><span class="badge badge--warning">処理中</span></td><td>取得済</td><td>未取得</td><td class="cell-muted">2026/08/11</td></tr></tbody>
                </table></div>
              </div>
            </section>
    
            <!-- 熟語 -->
            <section class="tabs__panel" data-panel="phrase">
              <div class="search-panel">
                <div class="search-panel__head">
                  <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件（熟語）</h3>
                  <div class="search-panel__actions">
                    <button class="btn btn--secondary" data-ai="標準化及び分類">標準化及び分類</button>
                    <button class="btn btn--secondary" data-ai="詳細情報取得">詳細情報取得</button>
                    <button class="btn btn--primary" id="wSearchPh"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                    <button class="btn btn--secondary" id="wResetPh">リセット</button>
                  </div>
                </div>
                <div class="filters"><div class="filters__row">
                  <span class="filter-item"><span class="filter-item__label">書籍：</span><select class="select"><option>英語表現 1000</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">熟語種別：</span><select class="select"><option>すべて</option><option>動詞構文</option><option>句動詞</option><option>コロケーション</option><option>定型表現</option><option>慣用表現</option><option>前置詞表現</option><option>接続表現</option><option>その他</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">標準化状態：</span><select class="select"><option>すべて</option><option>未取得</option><option>処理中</option><option>完了</option><option>要確認</option><option>エラー</option></select></span>
                </div></div>
              </div>
              <div class="table-section">
                <div class="table-section__head"><h3 class="table-section__title">熟語一覧</h3><span class="table-section__meta">全 2 件</span></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th class="col-actions">操作</th><th>熟語ID</th><th>書籍</th><th>分類</th><th>元表現</th><th>表示表現</th><th>標準構文</th><th>熟語種別</th><th>標準化状態</th><th>詳細取得状態</th></tr></thead>
                  <tbody id="phBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-open-ai="" title="AI説明"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg></button></td><td>PH-0001</td><td>英語表現 1000</td><td>句動詞</td><td>give up</td><td class="cell-strong">give up</td><td class="cell-muted">give up doing</td><td>句動詞</td><td><span class="badge badge--success">完了</span></td><td>取得済み</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="wordDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-open-ai="" title="AI説明"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg></button></td><td>PH-0002</td><td>英語表現 1000</td><td>句動詞</td><td>put off</td><td class="cell-strong">put off</td><td class="cell-muted">put off doing</td><td>動詞構文</td><td><span class="badge badge--warning">要確認</span></td><td>未取得</td></tr></tbody>
                </table></div>
              </div>
            </section>
          <div class="overlay" id="wordDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title" id="wordDlgTitle">単語編集</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="form-grid">
              <div class="field"><label class="field__label">英語単語</label><input class="input" value="abandon"></div>
              <div class="field"><label class="field__label">日本語訳</label><input class="input" value="捨てる"></div>
              <div class="field"><label class="field__label">中国語訳</label><input class="input" value="捨てる"></div>
              <div class="field"><label class="field__label">書籍</label><select class="select"><option>キクタン</option></select></div>
              <div class="field"><label class="field__label">分類</label><select class="select"><option>Day01</option><option>Day02</option></select></div>
              <div class="field"><label class="field__label">単語SEQ</label><input type="number" class="input" value="1"></div>
            </div>
            <div class="field" style="margin-top:12px"><label class="field__label">例文1</label><div class="stack" style="gap:6px"><input class="input" value="He abandoned the plan."><input class="input" placeholder="日本語訳"><input class="input" placeholder="中国語訳"></div></div>
            <div class="field" style="margin-top:12px"><label class="field__label">例文2</label><div class="stack" style="gap:6px"><input class="input" placeholder="英語例文"><input class="input" placeholder="日本語訳"><input class="input" placeholder="中国語訳"></div></div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" id="wordSave">保存</button></div>
        </div>
      </div><div class="overlay" id="wordNewDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--sm" role="dialog" aria-modal="true" data-size="sm">
          <div class="dialog__head"><h2 class="dialog__title">単語新規登録</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="field" style="margin-bottom:10px"><label class="field__label">書籍</label><select class="select"><option>中級英単語 1800</option></select></div><div class="field"><label class="field__label">単語</label><input class="input" placeholder="単語"></div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">登録</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
