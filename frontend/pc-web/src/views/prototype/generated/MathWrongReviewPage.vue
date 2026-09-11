<template>
  <div class="prototype-screen prototype-screen--math-wrong-review">
    <div class="review-layout">
              <section class="card">
                <div class="card__header">
                  <div><div class="test-kicker" style="color:var(--subject-math)">Review Session</div><h2 class="card__title">三角形の合同証明</h2></div>
                  <div class="row" style="gap:6px"><span class="badge badge--subject-ma">図形</span><span class="badge badge--warning">訂正中</span><span class="badge badge--outline">理解度 35%</span></div>
                </div>
                <div class="card__body">
                  <div class="tabs" id="rvTabs">
                    <button class="tabs__tab is-active" data-tab="redo" aria-selected="true">解き直し</button>
                    <button class="tabs__tab" data-tab="kp" aria-selected="false">知識点復習</button>
                  </div>
                  <div class="tabs__panel is-active" data-panel="redo">
                    <p style="line-height:1.8">△ABC と △DEF について、AB=DE、BC=EF、CA=FD のとき、△ABC≡△DEF であることを証明せよ。</p>
                    <div class="field" style="margin-top:12px"><label class="field__label">解き直しメモ</label><textarea class="textarea" rows="4"></textarea></div>
                    <div class="dropzone" style="margin-top:12px"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>解答写真のアップロード（0/5枚）</div>
                  </div>
                  <div class="tabs__panel" data-panel="kp">
                    <div class="field" style="margin-bottom:10px"><label class="field__label">正答・解説</label><div class="cell-muted" style="border:1px solid var(--color-border);border-radius:8px;padding:10px">3組の辺がそれぞれ等しいので、SSS 条件により合同。</div></div>
                    <div class="field"><label class="field__label">知識点タグ</label><div class="row" style="gap:6px;margin-top:4px"><span class="badge badge--subject-ma">合同条件</span></div></div>
                    <div class="field" style="margin-top:10px"><label class="field__label">つまずいた知識・公式</label><div class="cell-muted">SSS / SAS / ASA の使い分け</div></div>
                  </div>
                </div>
                <div class="card__footer">
                  <button class="btn btn--secondary" data-route-screen="math-wrong">一覧へ戻る</button>
                  <button class="btn btn--secondary" data-route-screen="math-wrong-input">修正へ</button>
                  <button class="btn btn--primary" id="rvSave"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>復習を記録</button>
                </div>
              </section>
    
              <aside class="card">
                <div class="card__header"><h3 class="card__title" style="font-size:14px">復習結果の記録</h3></div>
                <div class="card__body stack" style="gap:12px">
                  <div class="field"><label class="field__label">理解度</label><input type="range" min="0" max="100" value="60" style="width:100%" id="rvSlider"><div class="cell-muted" id="rvVal">60%</div></div>
                  <div class="field"><label class="field__label">結果</label><select class="select"><option>まだ不安</option><option>部分定着</option><option>定着</option></select></div>
                  <div class="field"><label class="field__label">時間（分）</label><input type="number" class="input" value="10"></div>
                  <div class="field"><label class="field__label">次回復習日</label><input type="date" class="input"></div>
                </div>
              </aside>
            </div>
          <div class="overlay" id="imgDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">解答画像の編集</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="dropzone" style="height:160px">画像編集（Mock）</div></div>
          <div class="dialog__foot"><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div><div class="overlay" id="photoDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">問題写真プレビュー</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="viewer-shell" style="height:300px;border-radius:var(--radius-lg);overflow:hidden"><div class="viewer-shell__stage"><svg class="icon" style="width:64px;height:64px;color:#aebdcc"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div></div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">前/次</button><button class="btn btn--secondary" data-dialog-close="">縮小</button><button class="btn btn--secondary" data-dialog-close="">拡大</button><button class="btn btn--primary" data-dialog-close="">閉じる</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>
.review-layout { display: grid; grid-template-columns: 1fr 280px; gap: 16px; } @media (max-width:900px){.review-layout{grid-template-columns:1fr}}
</style>
