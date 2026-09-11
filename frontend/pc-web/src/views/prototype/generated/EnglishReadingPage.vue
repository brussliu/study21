<template>
  <div class="prototype-screen prototype-screen--english-reading">
    <section class="card">
              <div class="card__header">
                <h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book"></use></svg>書籍管理</h2>
                <div class="row" style="gap:8px">
                  <button class="btn btn--secondary btn--sm" id="reloadBooks"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-sun"></use></svg>再読み込み</button>
                  <button class="btn btn--primary btn--sm" data-open-dialog="entryDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>書籍を追加</button>
                </div>
              </div>
              <div class="card__body"><div class="book-grid" id="bookGrid"><div class="book-card"><div class="book-card__cover"><svg class="icon" style="width:36px;height:36px"><use href="/prototype-assets/icons/icons.svg#i-book-open"></use></svg></div><div class="book-card__body"><div class="book-card__title">The Little Prince <span class="badge badge--warning">読書中</span></div><div class="cell-muted" style="font-size:12px">A. de Saint-Exupéry · Elementary</div><div class="cell-muted" style="font-size:12px">現在ページ 42 / 120 · 標記 8件</div><div class="book-progress"><div class="book-progress__bar" style="width:35%"></div></div><div class="cell-muted" style="font-size:12px">#Classic</div></div><div class="book-card__foot"><button class="btn btn--primary btn--sm" data-open="0"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>この書籍を開く</button><button class="btn btn--icon btn--sm" data-marks="0" title="標記一覧"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm" data-edit="0" title="修正"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="0" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></div></div><div class="book-card"><div class="book-card__cover"><svg class="icon" style="width:36px;height:36px"><use href="/prototype-assets/icons/icons.svg#i-book-open"></use></svg></div><div class="book-card__body"><div class="book-card__title">Harry Potter #1 <span class="badge badge--neutral">未着手</span></div><div class="cell-muted" style="font-size:12px">J.K. Rowling · Intermediate</div><div class="cell-muted" style="font-size:12px">現在ページ 0 / 320 · 標記 0件</div><div class="book-progress"><div class="book-progress__bar" style="width:0%"></div></div><div class="cell-muted" style="font-size:12px">#Novel</div></div><div class="book-card__foot"><button class="btn btn--primary btn--sm" data-open="1"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>この書籍を開く</button><button class="btn btn--icon btn--sm" data-marks="1" title="標記一覧"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm" data-edit="1" title="修正"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="1" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></div></div><div class="book-card"><div class="book-card__cover"><svg class="icon" style="width:36px;height:36px"><use href="/prototype-assets/icons/icons.svg#i-book-open"></use></svg></div><div class="book-card__body"><div class="book-card__title">Oxford Bookworms <span class="badge badge--success">読了</span></div><div class="cell-muted" style="font-size:12px">— · Starter</div><div class="cell-muted" style="font-size:12px">現在ページ 80 / 80 · 標記 12件</div><div class="book-progress"><div class="book-progress__bar" style="width:100%"></div></div><div class="cell-muted" style="font-size:12px">#Vocabulary</div></div><div class="book-card__foot"><button class="btn btn--primary btn--sm" data-open="2"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>この書籍を開く</button><button class="btn btn--icon btn--sm" data-marks="2" title="標記一覧"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button><button class="btn btn--icon btn--sm" data-edit="2" title="修正"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm is-danger" data-del="2" title="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></div></div></div></div>
            </section>
    
            <section class="card">
              <div class="card__header"><h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg>読書履歴</h2><span class="card__sub">Session Timeline</span></div>
              <div class="card__body" style="padding:0">
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th>日時</th><th>書籍</th><th>ページ</th><th>読書時間</th><th>標記</th><th>メモ</th><th class="col-actions">操作</th></tr></thead>
                  <tbody id="historyBody"><tr><td class="cell-muted">2026/08/13 14:00</td><td class="cell-strong">The Little Prince</td><td>40-42</td><td>24分</td><td>3</td><td class="cell-muted">語彙メモ</td><td class="row-actions"><button class="btn btn--icon btn--sm" data-delhist=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td></tr><tr><td class="cell-muted">2026/08/12 20:00</td><td class="cell-strong">Oxford Bookworms</td><td>75-80</td><td>18分</td><td>2</td><td class="cell-muted">読了</td><td class="row-actions"><button class="btn btn--icon btn--sm" data-delhist=""><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td></tr></tbody>
                </table></div>
              </div>
            </section>
          <div class="overlay" id="markDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">標記一覧</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="row" style="gap:8px;margin-bottom:12px"><input type="number" class="input" placeholder="現在頁" style="width:120px"><button class="btn btn--primary btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button><button class="btn btn--secondary btn--sm">全書</button></div>
            <div class="stack" id="markList" style="gap:6px"></div>
          </div>
        </div>
      </div><div class="overlay" id="entryDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>英語読書書籍登録</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="form-grid">
              <div class="field"><label class="field__label">書籍名（必須）</label><input class="input" placeholder="書籍名"></div>
              <div class="field"><label class="field__label">作者（必須）</label><input class="input" placeholder="作者"></div>
              <div class="field"><label class="field__label">難易度</label><select class="select"><option>Starter</option><option>Elementary</option><option>Intermediate</option><option>Upper</option></select></div>
              <div class="field"><label class="field__label">総ページ数（必須）</label><input type="number" class="input" placeholder="100"></div>
            </div>
            <div class="field" style="margin-top:12px"><label class="field__label">タグ</label><input class="input" placeholder="Oxford, Classic, Vocabulary..."></div>
            <div class="field" style="margin-top:12px"><label class="field__label">概要・読書方針</label><textarea class="textarea" rows="3"></textarea></div>
            <div class="dropzone" style="margin-top:12px"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>本文 PDF / 表紙・補助画像を選択</div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">閉じる</button><button class="btn btn--secondary" id="entryReset">リセット</button><button class="btn btn--primary" id="entrySave">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .book-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
    .book-card { border: 1px solid var(--color-border); border-radius: var(--radius-lg); overflow: hidden; background: var(--color-surface); box-shadow: var(--shadow-sm); display: flex; flex-direction: column; }
    .book-card__cover { height: 120px; background: linear-gradient(135deg, var(--accent-100), var(--primary-100)); display: flex; align-items: center; justify-content: center; color: var(--color-text-muted); }
    .book-card__body { padding: 14px 16px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
    .book-card__title { font-weight: 600; font-size: 15px; display: flex; align-items: center; gap: 6px; }
    .book-progress { height: 6px; background: var(--color-surface-alt); border-radius: 999px; overflow: hidden; }
    .book-progress__bar { height: 100%; background: var(--color-primary); }
    .book-card__foot { display: flex; gap: 6px; padding: 10px 16px; border-top: 1px solid var(--color-border); }
  
</style>
