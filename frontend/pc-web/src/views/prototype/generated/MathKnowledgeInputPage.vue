<template>
  <div class="prototype-screen prototype-screen--math-knowledge-input">
    <section class="card">
              <div class="card__header"><h2 class="card__title"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>知識点新規登録</h2><span class="card__sub"><span class="badge badge--primary">新規作成</span> 分野：未選択</span></div>
              <div class="card__body">
                <h3 class="form-section-title">基本情報</h3>
                <div class="form-grid">
                  <div class="field"><label class="field__label">知識点名*</label><input class="input" placeholder="三角形の合同条件"></div>
                  <div class="field"><label class="field__label">分野*</label><select class="select"><option>代数</option><option>関数</option><option>図形</option><option>確率</option><option>整数</option><option>データ分析</option></select></div>
                  <div class="field"><label class="field__label">大分類*</label><input class="input" placeholder="図形の性質"></div>
                  <div class="field"><label class="field__label">難易度</label><select class="select"><option>入門</option><option>易</option><option>標準</option><option>やや難</option><option>難</option></select></div>
                  <div class="field"><label class="field__label">ステータス</label><select class="select"><option>記載中</option><option>記載済</option></select></div>
                </div>
    
                <h3 class="form-section-title">学習内容</h3>
                <div class="rich-toolbar" id="richToolbar">
                  <button class="btn btn--icon btn--sm" data-cmd="bold" title="太字">B</button>
                  <button class="btn btn--icon btn--sm" data-cmd="italic" title="斜体">I</button>
                  <button class="btn btn--icon btn--sm" data-cmd="underline" title="下線">U</button>
                  <button class="btn btn--secondary btn--sm" data-open-dialog="imageDlg">画像を追加</button>
                  <button class="btn btn--secondary btn--sm" data-open-dialog="geomDlg">図形を挿入</button>
                </div>
                <div class="rich-editor" id="richEditor" contenteditable="true"><p>概要・公式・図形をここに入力します。</p></div>
                <div class="field" style="margin-top:12px"><label class="field__label">前置知識</label><input class="input" placeholder="公式の展開、平方完成、関数の定義..."></div>
    
                <h3 class="form-section-title">誤問題と紐づけ</h3>
                <div class="row" style="gap:8px;margin-bottom:8px"><input class="input" placeholder="検索キーワード" style="flex:1"><select class="select"><option>すべて</option><option>訂正中</option><option>訂正済</option><option>復習中</option><option>定着済</option></select><button class="btn btn--primary btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>誤問題を検索</button></div>
                <div class="cell-muted">紐づけ中 0 件</div>
              </div>
              <div class="card__footer">
                <button class="btn btn--secondary" data-route-screen="math-knowledge">一覧へ戻る</button>
                <button class="btn btn--secondary" id="resetBtn">リセット</button>
                <button class="btn btn--primary" id="saveBtn"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>保存</button>
              </div>
            </section>
          <div class="overlay" id="imageDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">画像編集</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="dropzone" style="height:160px">画像プレビュー（切り抜き範囲）</div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">左回転</button><button class="btn btn--secondary" data-dialog-close="">右回転</button><button class="btn btn--secondary" data-dialog-close="">リセット</button><button class="btn btn--primary" data-dialog-close="">この内容で挿入</button></div>
        </div>
      </div><div class="overlay" id="geomDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">図形を選択</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="row" style="gap:8px;margin-bottom:12px"><input class="input" placeholder="キーワード" style="flex:1"><button class="btn btn--primary btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button></div>
            <div class="picker-grid">
              <div class="picker-card is-selected"><div class="picker-card__thumb"><svg class="icon" style="width:32px;height:32px"><use href="/prototype-assets/icons/icons.svg#i-sigma"></use></svg></div><div class="picker-card__meta">三角形 ABC</div><span class="picker-card__check"><svg class="icon icon--sm"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg></span></div>
              <div class="picker-card"><div class="picker-card__thumb"><svg class="icon" style="width:32px;height:32px"><use href="/prototype-assets/icons/icons.svg#i-sigma"></use></svg></div><div class="picker-card__meta">円 O</div><span class="picker-card__check"><svg class="icon icon--sm"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg></span></div>
              <div class="picker-card"><div class="picker-card__thumb"><svg class="icon" style="width:32px;height:32px"><use href="/prototype-assets/icons/icons.svg#i-sigma"></use></svg></div><div class="picker-card__meta">放物線 y=x²</div><span class="picker-card__check"><svg class="icon icon--sm"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg></span></div>
            </div>
          </div>
          <div class="dialog__foot"><button class="btn btn--primary" data-dialog-close="">完了</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>
.form-section-title { font-size: 15px; font-weight: 600; margin: 20px 0 10px; } .form-section-title:first-child { margin-top: 0; } .rich-editor { border: 1px solid var(--color-border-strong); border-radius: var(--radius-md); min-height: 120px; padding: 10px; } .rich-toolbar { display: flex; gap: 4px; flex-wrap: wrap; padding: 6px; border: 1px solid var(--color-border-strong); border-bottom: none; border-radius: var(--radius-md) var(--radius-md) 0 0; }
</style>
