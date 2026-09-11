<template>
  <div class="prototype-screen prototype-screen--english-essay">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--primary" data-open-dialog="editorDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>英作文新規</button>
                  <button class="btn btn--primary" id="esSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="esReset">リセット</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="タイトル・設問・作文本文" style="flex:1"></span>
                <span class="filter-item"><span class="filter-item__label">英検級：</span><select class="select"><option>すべて</option><option>1級</option><option>準1級</option><option>2級</option></select></span>
                <span class="filter-item"><span class="filter-item__label">登録日：</span><input type="date" class="input" style="width:auto"><span class="range-input__sep">～</span><input type="date" class="input" style="width:auto"></span>
              </div></div>
            </div>
    
            <div class="table-section">
              <div class="table-section__head"><h3 class="table-section__title">英作文一覧</h3><span class="table-section__meta" id="esCount">全 3 件</span></div>
              <div class="table-wrap"><table class="data-table">
                <thead><tr><th class="col-actions">操作</th><th>No.</th><th>登録日時</th><th>英検級</th><th>タイトル</th><th>設問</th><th>得点</th><th>語数</th><th>画像</th></tr></thead>
                <tbody id="esBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="editorDlg" title="編集"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm is-danger" title="削除" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td>3</td><td class="cell-muted">2026/08/13 11:00</td><td>2級</td><td class="cell-strong">My Hometown</td><td class="cell-muted">あなたの故郷について</td><td>14</td><td>98</td><td><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg> 1</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="editorDlg" title="編集"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm is-danger" title="削除" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td>2</td><td class="cell-muted">2026/08/11 20:00</td><td>準1級</td><td class="cell-strong">Online Education</td><td class="cell-muted">オンライン教育の是非</td><td>18</td><td>142</td><td><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg> 2</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="editorDlg" title="編集"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm is-danger" title="削除" data-del=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td>1</td><td class="cell-muted">2026/08/10 19:00</td><td>2級</td><td class="cell-strong">Reading Habit</td><td class="cell-muted">読書習慣について</td><td>16</td><td>120</td><td><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg> 1</td></tr></tbody>
              </table></div>
              <div class="pagination"><span class="pagination__info">全 3 件</span><div class="pagination__pages"><button class="page-btn is-active">1</button><button class="page-btn">2</button></div></div>
            </div>
          <div class="overlay" id="editorDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>英作文AI添削</h2><span class="dialog__subtitle"><span class="badge badge--outline">デモ版</span></span><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="essay-steps"><div class="essay-step is-active">1 画像アップロード</div><div class="essay-step">2 文字認識・確認</div><div class="essay-step">3 AI添削</div></div>
            <div class="dropzone" style="margin-bottom:12px"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>JPG/PNG/WEBP 最大8枚・1枚10MB（0/8枚）</div>
            <div class="form-grid">
              <div class="field"><label class="field__label">作文の設問</label><textarea class="textarea" placeholder="AI文字認識後の設問"></textarea></div>
              <div class="field"><label class="field__label">手書き作文</label><textarea class="textarea" placeholder="AI文字認識後の作文本文（0 words）"></textarea></div>
            </div>
            <div class="form-grid" style="margin-top:12px">
              <div class="field"><label class="field__label">英検級</label><select class="select"><option>1級</option><option>準1級</option><option>2級</option></select></div>
              <div class="field"><label class="field__label">結果言語</label><select class="select"><option>日本語</option><option>中文</option></select></div>
            </div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">一覧へ戻る</button><button class="btn btn--secondary" id="esOcr">AIで画像を読み取る</button><button class="btn btn--primary" id="esSave">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .essay-steps { display: flex; gap: 6px; margin-bottom: 16px; }
    .essay-step { flex: 1; text-align: center; padding: 8px; border-radius: 8px; border: 1px solid var(--color-border); font-size: 12px; color: var(--color-text-muted); }
    .essay-step.is-active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-soft); }
  
</style>
