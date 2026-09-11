<template>
  <div class="prototype-screen prototype-screen--document">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--primary" data-open-dialog="docDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規</button>
                  <button class="btn btn--primary" id="docSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="docReset">リセット</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">大分類：</span><select class="select"><option>すべて</option><option>英語</option><option>数学</option></select></span>
                <span class="filter-item"><span class="filter-item__label">中分類：</span><select class="select"><option>すべて</option><option>単語</option><option>図形</option></select></span>
                <span class="filter-item"><span class="filter-item__label">小分類：</span><select class="select"><option>すべて</option><option>動詞</option></select></span>
                <span class="filter-item"><span class="filter-item__label">細分類：</span><select class="select"><option>すべて</option><option>基本</option></select></span>
              </div><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">ステータス：</span><select class="select"><option>すべて</option><option>有効</option><option>無効</option></select></span>
                <span class="filter-item"><span class="filter-item__label">有効期限：</span><input type="date" class="input"></span>
                <span class="filter-item filter-item--grow"><span class="filter-item__label">コメント：</span><input class="input" id="docKw" placeholder="コメントで検索（「error」でエラー状態を表示）" style="flex:1"></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">資料一覧</h3><span class="table-section__meta" id="docCount">全 3 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>資料番号</th><th>ステータス</th><th>有効期限</th><th>大分類</th><th>中分類</th><th>小分類</th><th>細分類</th><th>コメント</th><th>内容</th></tr></thead>
                <tbody id="docBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="docDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">DOC-0003</td><td><span class="badge badge--success">有効</span></td><td class="cell-muted">2026/12/31</td><td>英語</td><td>単語</td><td>動詞</td><td>基本</td><td class="cell-muted">動詞の例文集</td><td><button class="btn btn--icon btn--sm" data-photo=""><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="docDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">DOC-0002</td><td><span class="badge badge--success">有効</span></td><td class="cell-muted">2026/11/30</td><td>数学</td><td>図形</td><td>三角形</td><td>証明</td><td class="cell-muted">合同条件</td><td><button class="btn btn--icon btn--sm" data-photo=""><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="docDlg"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">DOC-0001</td><td><span class="badge badge--danger">無効</span></td><td class="cell-muted">2026/03/01</td><td>英語</td><td>読解</td><td>長文</td><td>精読</td><td class="cell-muted">英検2級 過去問</td><td><button class="btn btn--icon btn--sm" data-photo=""><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td></tr></tbody>
              </table></div>
              <div class="pagination"><span class="pagination__info">全 3 件</span><div class="pagination__pages"><button class="page-btn is-active">1</button></div></div>
            </div>
          <div class="overlay" id="docDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-folder"></use></svg>資料詳細</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="form-grid">
              <div class="field"><label class="field__label">ステータス</label><select class="select"><option>1. 有効</option><option>0. 無効</option></select></div>
              <div class="field"><label class="field__label">有効期限</label><input type="text" class="input" placeholder="YYYY/MM/DD"></div>
            </div>
            <div class="form-grid form-grid--4" style="margin-top:12px">
              <div class="field"><label class="field__label">大分類</label><select class="select"><option>選択または入力してください</option></select></div>
              <div class="field"><label class="field__label">中分類</label><select class="select"><option>選択または入力してください</option></select></div>
              <div class="field"><label class="field__label">小分類</label><select class="select"><option>選択または入力してください</option></select></div>
              <div class="field"><label class="field__label">細分類</label><select class="select"><option>選択または入力してください</option></select></div>
            </div>
            <div class="dropzone" style="margin-top:12px"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>ファイルをここにドラッグしてください（Ctrl+V 可）<br><button class="btn btn--secondary btn--sm" style="margin-top:8px">ファイルを選択</button> <button class="btn btn--secondary btn--sm" data-route-screen="temp-file-picker">臨時ファイルから選択</button></div>
            <div class="field" style="margin-top:12px"><label class="field__label">コメント</label><textarea class="textarea" rows="3" placeholder="コメントを入力してください"></textarea></div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" id="docSave">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
