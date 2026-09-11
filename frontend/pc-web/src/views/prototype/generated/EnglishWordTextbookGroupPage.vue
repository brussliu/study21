<template>
  <div class="prototype-screen prototype-screen--english-word-textbook-group">
    <section class="card">
              <div class="card__header">
                <h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-folder"></use></svg>英単語教材取込</h2>
                <span class="card__sub"><span class="badge badge--outline">原図保護</span> WTB-0004 · 英単語ターゲット 1900</span>
              </div>
              <div class="card__body">
                <div class="wtb-stepper" id="wtbStepper">
                  <button class="wtb-step is-active" data-step="1"><b>01 アップロード</b>画像を追加</button>
                  <button class="wtb-step" data-step="2"><b>02 左右ペア</b>ペア作成</button>
                  <button class="wtb-step" data-step="3"><b>03 ページ補正</b>裁切</button>
                  <button class="wtb-step" data-step="4"><b>04 合成画像</b>左右合成</button>
                  <button class="wtb-step" data-step="5"><b>05 AI認識</b>語彙抽出</button>
                  <button class="wtb-step" data-step="6"><b>06 結果確認</b>確認・Excel</button>
                </div>
    
                <div id="wtbPanels">
                  <!-- Step 1 -->
                  <div class="wtb-panel is-active" data-panel="1">
                    <div class="field" style="margin-bottom:12px"><label class="field__label">教材名</label><input class="input" value="英単語ターゲット 1900"></div>
                    <div class="dropzone"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>JPG/PNG/WEBP をドラッグ&amp;ドロップ（ページ順に自動採番）</div>
                    <div class="row" style="gap:8px;margin-top:12px"><button class="btn btn--primary btn--sm">アップロード開始</button><button class="btn btn--secondary btn--sm">リセット</button></div>
                  </div>
                  <!-- Step 2 -->
                  <div class="wtb-panel" data-panel="2" hidden>
                    <div class="row" style="gap:8px;margin-bottom:12px"><span class="badge badge--neutral">未作成</span><button class="btn btn--primary btn--sm">自動ペア作成</button><button class="btn btn--secondary btn--sm">全ペア確認</button></div>
                    <div class="pair-grid"><div class="pair-card">ペア 1<br><span class="cell-muted">p.2 + p.3</span></div><div class="pair-card">ペア 2<br><span class="cell-muted">p.4 + p.5</span></div><div class="pair-card">ペア 3<br><span class="cell-muted">p.6 + p.7</span></div></div>
                  </div>
                  <!-- Step 3 -->
                  <div class="wtb-panel" data-panel="3" hidden>
                    <div class="form-grid">
                      <div class="field"><label class="field__label">ページ種別</label><select class="select"><option>左ページ</option><option>右ページ</option><option>すべて</option></select></div>
                      <div class="field"><label class="field__label">裁切量（px）</label><input type="number" class="input" value="0"></div>
                    </div>
                    <div class="row" style="gap:8px;margin-top:12px"><button class="btn btn--primary btn--sm" data-open-dialog="cropDlg">裁切プレビュー</button><button class="btn btn--secondary btn--sm">裁切を確定</button><button class="btn btn--ghost btn--sm">戻す</button></div>
                  </div>
                  <!-- Step 4 -->
                  <div class="wtb-panel" data-panel="4" hidden>
                    <div class="pair-grid"><div class="pair-card"><strong>ペア 1</strong><br><span class="cell-muted">左/偶数頁 + 右/奇数頁</span></div></div>
                    <div class="row" style="gap:8px;margin-top:12px"><button class="btn btn--primary btn--sm">このペアを確認</button><button class="btn btn--secondary btn--sm">合成</button></div>
                  </div>
                  <!-- Step 5 -->
                  <div class="wtb-panel" data-panel="5" hidden>
                    <div class="row" style="gap:8px;margin-bottom:12px"><span class="badge badge--warning">待機中</span><button class="btn btn--primary btn--sm">全画像AI認識</button><button class="btn btn--secondary btn--sm">再実行</button></div>
                    <div class="test-progress"><div class="test-progress__bar" style="width:45%"></div></div>
                  </div>
                  <!-- Step 6 -->
                  <div class="wtb-panel" data-panel="6" hidden>
                    <div class="row" style="gap:8px;margin-bottom:12px"><span class="badge badge--neutral">未実行</span><button class="btn btn--primary btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-download"></use></svg>Excel出力</button></div>
                    <table class="data-table"><thead><tr><th>ページ</th><th>単語</th><th>音標</th><th>品詞</th><th>日本語意味</th><th>フレーズ1</th><th>例文</th></tr></thead><tbody><tr><td>2</td><td class="cell-strong">abandon</td><td>/əbǽndən/</td><td>動</td><td>捨てる</td><td>abandon a plan</td><td>He abandoned the plan.</td></tr></tbody></table>
                  </div>
                </div>
              </div>
            </section>
          <div class="overlay" id="cropDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">裁切プレビュー</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="dropzone" style="height:160px">裁切範囲プレビュー（Mock）</div></div>
          <div class="dialog__foot"><button class="btn btn--primary" data-dialog-close="">閉じる</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .wtb-stepper { display: flex; gap: 6px; margin-bottom: 16px; overflow-x: auto; }
    .wtb-step { flex: 1; min-width: 110px; text-align: center; padding: 8px; border-radius: 8px; border: 1px solid var(--color-border); font-size: 12px; color: var(--color-text-muted); cursor: pointer; }
    .wtb-step.is-active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-soft); }
    .wtb-step.is-done { border-color: var(--color-success); color: var(--color-success); }
    .wtb-step b { display: block; font-size: 13px; }
    .pair-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
    .pair-card { border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 12px; text-align: center; }
  
</style>
