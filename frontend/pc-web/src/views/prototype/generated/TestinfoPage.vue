<template>
  <div class="prototype-screen prototype-screen--testinfo">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--primary" data-open-dialog="testDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>テスト新規</button>
                  <button class="btn btn--primary" id="tiSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="tiReset">リセット</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">教科：</span><select class="select"><option>英語</option><option>数学</option><option>国語</option><option>物理</option><option>化学</option><option>生物</option><option>歴史</option><option>地理</option></select></span>
                <span class="filter-item"><span class="filter-item__label">区分：</span><select class="select"><option>通常</option><option>模擬</option><option>復習</option><option>練習</option></select></span>
                <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="テスト名・番号" style="flex:1"></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">テスト一覧</h3><span class="table-section__meta" id="tiCount">全 3 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>テスト番号</th><th>テスト名</th><th>教科</th><th>区分</th><th>試験日</th><th>得点数</th><th>満点数</th><th>正答率</th><th>詳細メモ</th><th>試験用紙ファイル</th></tr></thead>
                <tbody id="tiBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="testDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">T-0003</td><td>英語 期末テスト</td><td>英語</td><td>通常</td><td class="cell-muted">2026/07/20</td><td>85</td><td>100</td><td>85%</td><td class="cell-muted">読解で失点</td><td><button class="btn btn--icon btn--sm" data-photo=""><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg> 2</button></td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="testDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">T-0002</td><td>数学 模擬試験</td><td>数学</td><td>模擬</td><td class="cell-muted">2026/07/15</td><td>72</td><td>100</td><td>72%</td><td class="cell-muted">図形が弱点</td><td><button class="btn btn--icon btn--sm" data-photo=""><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg> 3</button></td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="testDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">T-0001</td><td>英語 単語小テスト</td><td>英語</td><td>練習</td><td class="cell-muted">2026/07/10</td><td>45</td><td>50</td><td>90%</td><td class="cell-muted">順調</td><td><button class="btn btn--icon btn--sm" data-photo=""><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg> 1</button></td></tr></tbody>
              </table></div>
              <div class="pagination"><span class="pagination__info">全 3 件</span><div class="pagination__pages"><button class="page-btn is-active">1</button></div></div>
            </div>
          <div class="overlay" id="testDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clipboard"></use></svg>テスト新規</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="form-grid">
              <div class="field"><label class="field__label">テスト番号</label><input class="input" value="T-0004" disabled></div>
              <div class="field"><label class="field__label">テスト名</label><input class="input" placeholder="テスト名（max 120）"></div>
              <div class="field"><label class="field__label">教科</label><select class="select"><option>英語</option><option>数学</option></select></div>
              <div class="field"><label class="field__label">区分</label><select class="select"><option>通常</option><option>模擬</option><option>復習</option><option>練習</option></select></div>
              <div class="field"><label class="field__label">試験日</label><input type="date" class="input"></div>
              <div class="field"><label class="field__label">得点数 / 満点数</label><div class="row" style="gap:6px"><input type="number" class="input" placeholder="得点"><span class="range-input__sep">/</span><input type="number" class="input" placeholder="満点"></div></div>
              <div class="field"><label class="field__label">正答率</label><input class="input" value="85%" readonly></div>
              <div class="field"><label class="field__label">詳細メモ</label><textarea class="textarea" placeholder="試験内容・反省・次回対策など"></textarea></div>
            </div>
            <div class="field" style="margin-top:12px"><label class="field__label">試験用紙ファイル（10件以内）</label>
              <div class="row" style="gap:8px;margin-top:6px"><button class="btn btn--secondary btn--sm">ファイルを選択</button><button class="btn btn--secondary btn--sm" data-route-screen="temp-file-picker">臨時ファイルから選択</button><button class="btn btn--secondary btn--sm" data-route-screen="testinfo-file-picker">テスト情報から選択</button></div>
              <div class="dropzone" style="margin-top:10px"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>0 / 10 枚</div>
            </div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" id="tiSave">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
