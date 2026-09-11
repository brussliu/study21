<template>
  <div class="prototype-screen prototype-screen--math-wrong-input">
    <div class="mw-layout">
              <section class="card">
                <div class="card__header"><h2 class="card__title"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>誤問題登録</h2><span class="card__sub"><span class="badge badge--neutral">下書き</span> 記録番号：MW-0012</span></div>
                <div class="card__body">
                  <h3 class="form-section-title">出典情報</h3>
                  <div class="row" style="gap:12px;margin-bottom:10px"><label class="radio"><input type="radio" name="src" checked>テスト情報に登録済みの問題</label><label class="radio"><input type="radio" name="src">その他の出典の問題</label></div>
                  <div class="form-grid">
                    <div class="field"><label class="field__label">教科</label><select class="select"><option>数学</option></select></div>
                    <div class="field"><label class="field__label">対象テスト*</label><input class="input" placeholder="テストを検索"></div>
                    <div class="field"><label class="field__label">設問番号*</label><input class="input" placeholder="問2"></div>
                    <div class="field"><label class="field__label">試験日</label><input type="date" class="input"></div>
                  </div>
    
                  <h3 class="form-section-title">基本情報</h3>
                  <div class="form-grid">
                    <div class="field"><label class="field__label">ステータス</label><select class="select"><option>処理中</option><option>訂正中</option><option>訂正済</option><option>復習中</option><option>定着済</option></select></div>
                    <div class="field"><label class="field__label">学年*</label><select class="select"><option>中学2年</option><option>中学3年</option><option>高校1年</option></select></div>
                    <div class="field"><label class="field__label">分野*</label><input class="input" value="図形"></div>
                    <div class="field"><label class="field__label">問題形式*</label><select class="select"><option>単一選択</option><option>複数選択</option><option>穴埋め</option><option>記述</option><option>証明</option><option>計算</option></select></div>
                  </div>
    
                  <h3 class="form-section-title">問題内容</h3>
                  <label class="check" style="margin-bottom:8px"><input type="checkbox">画像から識別</label>
                  <div class="form-grid">
                    <div class="field"><label class="field__label">問題タイトル*</label><input class="input" value="三角形の合同証明"></div>
                    <div class="field"><label class="field__label">正答・解説*</label><input class="input" placeholder="正答・解説"></div>
                  </div>
                  <div class="field" style="margin-top:10px"><label class="field__label">問題文*</label><textarea class="textarea" rows="4">△ABC と △DEF について、AB=DE、BC=EF、CA=FD のとき…</textarea></div>
    
                  <h3 class="form-section-title">知識点分析</h3>
                  <div class="form-grid">
                    <div class="field"><label class="field__label">知識点タグ</label><input class="input" value="合同条件" data-open-dialog="kpDlg"></div>
                    <div class="field"><label class="field__label">つまずいた知識・公式</label><input class="input" placeholder="SSS / SAS"></div>
                  </div>
    
                  <h3 class="form-section-title">誤答分析</h3>
                  <div class="form-grid">
                    <div class="field"><label class="field__label">誤答種別タグ</label><input class="input" placeholder="方針ミス、計算ミス..."></div>
                    <div class="field"><label class="field__label">原因メモ</label><input class="input" placeholder="原因"></div>
                  </div>
    
                  <h3 class="form-section-title">復習管理</h3>
                  <div class="form-grid">
                    <div class="field"><label class="field__label">復習方式</label><select class="select"><option>解き直し</option><option>知識点復習</option></select></div>
                    <div class="field"><label class="field__label">復習周期</label><select class="select"><option>1-2-7日</option><option>1-3-7-15日</option><option>2-5-10-20日</option></select></div>
                    <div class="field"><label class="field__label">次回復習日</label><input type="date" class="input"></div>
                  </div>
                </div>
                <div class="card__footer">
                  <button class="btn btn--secondary" data-route-screen="math-wrong">一覧へ戻る</button>
                  <button class="btn btn--secondary" id="mwReset">リセット</button>
                  <button class="btn btn--secondary" id="mwDraft">下書き</button>
                  <button class="btn btn--primary" id="mwSave"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>保存</button>
                </div>
              </section>
    
              <aside class="card">
                <div class="card__header"><h3 class="card__title" style="font-size:14px">現在の理解度</h3></div>
                <div class="card__body"><input type="range" min="0" max="100" value="35" style="width:100%" id="accSlider"><div class="cell-muted" id="accVal">35%</div></div>
                <div class="card__header" style="border-top:1px solid var(--color-border)"><h3 class="card__title" style="font-size:14px">問題写真</h3></div>
                <div class="card__body stack" style="gap:6px">
                  <div class="row" style="justify-content:space-between"><span class="cell-muted">問題 (0/6)</span><button class="btn btn--secondary btn--sm" data-open-dialog="geomDlg">図形</button><button class="btn btn--secondary btn--sm">追加</button></div>
                  <div class="row" style="justify-content:space-between"><span class="cell-muted">自分の解答 (0/6)</span><button class="btn btn--secondary btn--sm" data-open-dialog="photoDlg">プレビュー</button></div>
                </div>
              </aside>
            </div>
          <div class="overlay" id="kpDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">知識点詳細</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="row" style="gap:6px;margin-bottom:10px"><span class="badge badge--subject-ma">図形</span><span class="badge badge--outline">標準</span></div><h3 style="font-size:16px;margin-bottom:6px">三角形の合同条件</h3><p class="cell-muted">3組の辺がそれぞれ等しい（SSS）など。</p></div>
          <div class="dialog__foot"><button class="btn btn--primary" data-dialog-close="">閉じる</button></div>
        </div>
      </div><div class="overlay" id="geomDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">図形を選択</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="row" style="gap:8px;margin-bottom:12px"><input class="input" placeholder="キーワード" style="flex:1"><button class="btn btn--primary btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button></div><div class="cell-muted">図形一覧（Mock）</div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">挿入</button><button class="btn btn--primary" data-dialog-close="">完了</button></div>
        </div>
      </div><div class="overlay" id="photoDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">問題写真プレビュー</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="viewer-shell" style="height:320px;border-radius:var(--radius-lg);overflow:hidden"><div class="viewer-shell__stage"><svg class="icon" style="width:64px;height:64px;color:#aebdcc"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div></div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">前の画像</button><button class="btn btn--secondary" data-dialog-close="">縮小</button><button class="btn btn--secondary" data-dialog-close="">拡大</button><button class="btn btn--primary" data-dialog-close="">閉じる</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>
.form-section-title { font-size: 15px; font-weight: 600; margin: 20px 0 10px; } .form-section-title:first-child { margin-top: 0; } .mw-layout { display: grid; grid-template-columns: 1fr 260px; gap: 16px; } @media (max-width:900px){.mw-layout{grid-template-columns:1fr}}
</style>
