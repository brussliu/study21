<template>
  <div class="prototype-screen prototype-screen--testword">
    <div class="tabs" id="twTabs" role="tablist">
              <button class="tabs__tab is-active" data-tab="beginner" aria-selected="true">初級編</button>
              <button class="tabs__tab" data-tab="intermediate" aria-selected="false">中級編</button>
            </div>
    
            <!-- 初級編 -->
            <section class="tabs__panel is-active" data-panel="beginner">
              <div class="search-panel">
                <div class="search-panel__head">
                  <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                  <div class="search-panel__actions">
                    <button class="btn btn--primary" id="beginTest"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>テスト開始</button>
                    <button class="btn btn--primary" id="beginSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                    <button class="btn btn--secondary" id="beginReset">リセット</button>
                    <button class="btn btn--secondary" data-open-dialog="randomDlg">複数作成</button>
                    <button class="btn btn--secondary" id="wrongCreate">誤り分作成</button>
                  </div>
                </div>
                <div class="filters"><div class="filters__row">
                  <span class="filter-item"><span class="filter-item__label">等級：</span><select class="select"><option>すべて</option><option>3級</option><option>2級</option><option>準1級</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">書籍：</span><select class="select"><option>すべて</option><option>キクタン</option><option>三級単語</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">分類：</span><select class="select"><option>すべて</option><option>Day01</option><option>Day02</option></select></span>
                </div></div>
              </div>
              <div class="table-section">
                <div class="table-section__head"><h3 class="table-section__title">テスト情報一覧</h3><span class="table-section__meta" id="beginCount">全 3 件</span></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th class="col-actions">操作</th><th>等級</th><th>書籍</th><th>分類</th><th>ステータス</th><th>テスト方法</th><th>テスト種別</th><th>数量</th><th>正解率</th><th>勉強時間</th></tr></thead>
                  <tbody id="beginBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="0" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open-dialog="wordlistDlg" title="単語リスト"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>2級</td><td>キクタン</td><td>Day01</td><td><span class="badge badge--warning">勉強中</span></td><td>単語学習</td><td>A.勉強</td><td>20</td><td>85%</td><td>12分</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="1" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open-dialog="wordlistDlg" title="単語リスト"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>2級</td><td>キクタン</td><td>Day02</td><td><span class="badge badge--neutral">未勉強</span></td><td>入力</td><td>B.中日訳英</td><td>10</td><td>-</td><td>-</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="2" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open-dialog="wordlistDlg" title="単語リスト"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>3級</td><td>三級単語</td><td>Day01</td><td><span class="badge badge--success">勉強済</span></td><td>選択</td><td>D.英訳</td><td>30</td><td>92%</td><td>25分</td></tr></tbody>
                </table></div>
              </div>
            </section>
    
            <!-- 中級編 -->
            <section class="tabs__panel" data-panel="intermediate">
              <div class="search-panel">
                <div class="search-panel__head">
                  <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件（中級編）</h3>
                  <div class="search-panel__actions">
                    <button class="btn btn--primary" id="intSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                    <button class="btn btn--secondary" data-open-dialog="randomDlgInt">複数作成</button>
                  </div>
                </div>
                <div class="filters"><div class="filters__row">
                  <span class="filter-item"><span class="filter-item__label">書籍：</span><select class="select"><option>中級英単語 1800</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">分類：</span><select class="select"><option>Unit 04-06</option><option>Unit 07-09</option><option>Unit 10-12</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">テスト種別：</span><select class="select"><option>すべて</option><option>A.勉強</option><option>B.中日訳英</option><option>C.音訳英</option><option>D.英訳中日</option><option>E.文脈英訳</option><option>F.例文入力</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">ステータス：</span><select class="select"><option>すべて</option><option>勉強済</option><option>勉強中</option><option>未勉強</option></select></span>
                </div></div>
              </div>
              <div class="table-section">
                <div class="table-section__head"><h3 class="table-section__title">テスト情報一覧（中級編）</h3><span class="table-section__meta" id="intCount">全 3 件</span></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th class="col-actions">操作</th><th>書籍</th><th>分類</th><th>ステータス</th><th>テスト方法</th><th>テスト種別</th><th>数量</th><th>正解率</th><th>勉強時間</th></tr></thead>
                  <tbody id="intBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="0" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open-dialog="wordlistDlg" title="単語リスト"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>中級英単語 1800</td><td>Unit 04-06</td><td><span class="badge badge--warning">勉強中</span></td><td>入力</td><td>B.中日訳英</td><td>10</td><td>80%</td><td>15分</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="1" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open-dialog="wordlistDlg" title="単語リスト"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>中級英単語 1800</td><td>Unit 07-09</td><td><span class="badge badge--neutral">未勉強</span></td><td>選択</td><td>D.英訳中日</td><td>10</td><td>-</td><td>-</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open="2" title="開始"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></button><button class="btn btn--icon btn--sm" data-open-dialog="wordlistDlg" title="単語リスト"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>中級英単語 1800</td><td>Unit 10-12</td><td><span class="badge badge--success">勉強済</span></td><td>入力</td><td>F.例文入力</td><td>8</td><td>90%</td><td>20分</td></tr></tbody>
                </table></div>
              </div>
            </section>
          <div class="overlay" id="randomDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">複数作成</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">等級</label><select class="select"><option>2級</option><option>3級</option></select></div>
            <div class="field"><label class="field__label">書籍</label><select class="select"><option>キクタン</option></select></div>
            <div class="field"><label class="field__label">数量</label><select class="select"><option>全部</option><option>10語</option><option>20語</option><option>50語</option></select></div>
            <div class="field"><label class="field__label">テスト種別</label><select class="select"><option>A.勉強</option><option>B.中日訳英</option><option>C.音訳英</option><option>D.英訳中日</option></select></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--secondary" id="addRow">追加行</button><button class="btn btn--primary" id="makeRandom">作成</button></div>
        </div>
      </div><div class="overlay" id="randomDlgInt" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">複数作成（中級編）</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">書籍</label><select class="select"><option>中級英単語 1800</option></select></div>
            <div class="field"><label class="field__label">数量</label><select class="select"><option>全部</option><option>10語</option><option>20語</option></select></div>
            <div class="field"><label class="field__label">テスト種別</label><select class="select"><option>A.勉強</option><option>B.中日訳英</option><option>C.音訳英</option><option>D.英訳中日</option><option>E.文脈英訳</option><option>F.例文入力</option></select></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" id="makeRandomInt">作成</button></div>
        </div>
      </div><div class="overlay" id="wordlistDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">単語リスト</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="row" style="gap:12px;margin-bottom:12px"><label class="radio"><input type="radio" name="wl" checked>全部</label><label class="radio"><input type="radio" name="wl">誤りのみ</label></div>
            <table class="data-table"><thead><tr><th>枝番</th><th>単語</th><th>日本語</th></tr></thead><tbody><tr><td>1</td><td class="cell-strong">abandon</td><td>捨てる</td></tr><tr><td>2</td><td class="cell-strong" style="color:var(--color-danger)">achieve ✕</td><td>達成する</td></tr></tbody></table>
          </div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
