<template>
  <div class="prototype-screen prototype-screen--english-grammar">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                <div class="search-panel__actions">
                  <button class="btn btn--secondary" data-route-screen="english-grammar-input">新規追加</button>
                  <button class="btn btn--secondary" data-open-dialog="aiDlg">AIで新規</button>
                  <button class="btn btn--primary" id="grSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary" id="grReset">リセット</button>
                </div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item filter-item--grow"><span class="filter-item__label">文法名・キーワード：</span><input class="input" placeholder="文法名・キーワード" style="flex:1"></span>
                <span class="filter-item"><span class="filter-item__label">ステータス：</span><select class="select"><option>すべて</option><option>処理中</option><option>草稿</option><option>確認中</option><option>公開中</option></select></span>
                <span class="filter-item"><span class="filter-item__label">出典：</span><select class="select"><option>すべて</option><option>AIで新規</option><option>スキャン取込</option><option>手動登録</option></select></span>
              </div></div>
            </div>
    
            <div class="grammar-layout">
              <aside class="grammar-cats">
                <div class="cat-item is-active">すべて</div>
                <div class="cat-item">時制</div>
                <div class="cat-item">節・構文</div>
                <div class="cat-item">準動詞</div>
                <div class="cat-item">修飾・比較</div>
                <div class="cat-item">その他</div>
              </aside>
              <div class="grammar-cards" id="grGrid"><div class="grammar-card" data-open-dialog="detailDlg"><div class="grammar-card__title">現在完了形</div><div class="cell-muted" style="font-size:12px">時制</div><div class="grammar-card__example">I have lived in Tokyo for five years.</div><div class="grammar-card__meta"><span class="badge badge--success">公開中</span><span class="badge badge--outline">手動登録</span></div></div><div class="grammar-card" data-open-dialog="detailDlg"><div class="grammar-card__title">関係代名詞</div><div class="cell-muted" style="font-size:12px">節・構文</div><div class="grammar-card__example">This is the book which I bought.</div><div class="grammar-card__meta"><span class="badge badge--info">確認中</span><span class="badge badge--outline">AIで新規</span></div></div><div class="grammar-card" data-open-dialog="detailDlg"><div class="grammar-card__title">不定詞</div><div class="cell-muted" style="font-size:12px">準動詞</div><div class="grammar-card__example">I want to study English.</div><div class="grammar-card__meta"><span class="badge badge--neutral">草稿</span><span class="badge badge--outline">スキャン取込</span></div></div><div class="grammar-card" data-open-dialog="detailDlg"><div class="grammar-card__title">比較級</div><div class="cell-muted" style="font-size:12px">修飾・比較</div><div class="grammar-card__example">He is taller than me.</div><div class="grammar-card__meta"><span class="badge badge--warning">処理中</span><span class="badge badge--outline">手動登録</span></div></div></div>
            </div>
          <div class="overlay" id="detailDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">文法詳細</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="row" style="gap:6px;margin-bottom:12px"><span class="badge badge--primary">公開中</span><span class="badge badge--outline">時制</span><span class="badge badge--neutral">手動登録</span></div>
            <h3 style="font-size:16px;margin-bottom:8px">現在完了形</h3>
            <div style="border:1px solid var(--color-border);border-radius:8px;padding:10px;font-size:13px;margin-bottom:10px">公式：have / has + 過去分詞</div>
            <p class="cell-muted" style="font-size:13px">例文：I have lived in Tokyo for five years.（私は5年間東京に住んでいます。）</p>
            <div class="field" style="margin-top:10px"><label class="field__label">注意点</label><div class="cell-muted" style="font-size:13px">現在までの継続・経験・完了・結果を表す。</div></div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-route-screen="english-grammar-input">編集</button><button class="btn btn--primary" data-dialog-close="">閉じる</button></div>
        </div>
      </div><div class="overlay" id="aiDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">AIで新規</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="dropzone"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>画像アップロード / 臨時画像から選択（最大10枚）</div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-route-screen="english-grammar-input">下書きを作成</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .grammar-layout { display: flex; gap: 16px; }
    .grammar-cats { flex: 0 0 200px; border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 12px; }
    .cat-item { padding: 7px 10px; border-radius: 6px; font-size: 13px; color: var(--color-text-muted); cursor: pointer; }
    .cat-item:hover, .cat-item.is-active { background: var(--color-primary-soft); color: var(--color-primary); }
    .grammar-cards { flex: 1; min-width: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; align-content: start; }
    .grammar-card { border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 14px 16px; background: var(--color-surface); box-shadow: var(--shadow-sm); cursor: pointer; }
    .grammar-card:hover { border-color: var(--color-primary); }
    .grammar-card__title { font-weight: 600; font-size: 15px; margin-bottom: 4px; }
    .grammar-card__example { font-size: 13px; color: var(--color-text-muted); font-style: italic; margin: 6px 0; }
    .grammar-card__meta { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
    @media (max-width: 768px) { .grammar-layout { flex-direction: column; } .grammar-cats { flex: 0 0 auto; display: flex; flex-wrap: wrap; gap: 4px; } }
  
</style>
