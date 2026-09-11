<template>
  <div class="prototype-screen prototype-screen--study-monitor">
    <div class="search-panel">
              <div class="search-panel__head">
                <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg>学習状態の記録・分析</h3>
                <span class="badge badge--success">DB連携</span>
                <div class="search-panel__actions"><button class="btn btn--primary" id="smLatest">最新状態を確認</button></div>
              </div>
              <div class="filters"><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">表示方法：</span><span class="pill-tabs"><button class="pill-tabs__tab is-active">動画から確認</button><button class="pill-tabs__tab">スナップショットを直接検索</button></span></span>
              </div><div class="filters__row">
                <span class="filter-item"><span class="filter-item__label">対象日：</span><input type="date" class="input" style="width:auto"></span>
                <span class="filter-item"><span class="filter-item__label">時間帯：</span><input type="time" class="input" value="08:00" style="width:auto"><span class="range-input__sep">～</span><input type="time" class="input" value="23:59" style="width:auto"></span>
                <span class="filter-item"><span class="filter-item__label">AI分析：</span><select class="select"><option>すべて</option><option>分析済み</option><option>未分析</option><option>エラー</option></select></span>
                <span class="filter-item"><span class="filter-item__label">分析結果：</span><select class="select"><option>すべて</option><option>学習中（PC不使用・読書または筆記）</option><option>学習中（PC使用）</option><option>離席中</option><option>PC使用中（非学習）</option><option>その他</option><option>判断不可</option></select></span>
              </div></div>
              <div class="cell-muted" style="font-size:12px">batL02実行 毎時 05/15/25/35/45/55分</div>
            </div>
    
            <div class="monitor-summary">
              <div class="stat-card"><div><div class="stat-card__label">動画</div><div class="stat-card__value">6本</div></div></div>
              <div class="stat-card"><div><div class="stat-card__label">スナップショット</div><div class="stat-card__value">48枚</div></div></div>
              <div class="stat-card"><div><div class="stat-card__label">分析済み</div><div class="stat-card__value">30件</div></div></div>
              <div class="stat-card"><div><div class="stat-card__label">未分析</div><div class="stat-card__value">18件</div></div></div>
            </div>
    
            <div class="card">
              <div class="card__header">
                <h2 class="card__title">1分間隔スナップショット</h2>
                <div class="row" style="gap:8px">
                  <button class="btn btn--secondary btn--sm" id="bulkBtn">分析結果を一括変更</button>
                  <button class="btn btn--secondary btn--sm" id="timelineBtn">時間軸で確認</button>
                </div>
              </div>
              <div class="card__body"><div class="snap-grid" id="snapGrid"><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:00<br>未分析</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:05<br>分析済</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:10<br>分析済</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:15<br>分析済</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:20<br>未分析</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:25<br>分析済</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:30<br>分析済</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:35<br>分析済</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:40<br>未分析</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:45<br>分析済</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:50<br>分析済</div></div><div class="snap-card" data-open-dialog="snapDlg"><div class="snap-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="snap-card__meta">08:55<br>分析済</div></div></div></div>
            </div>
          <div class="overlay" id="snapDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">スナップショット詳細</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="viewer-shell" style="height:180px;border-radius:8px;overflow:hidden;margin-bottom:12px"><div class="viewer-shell__stage"><svg class="icon" style="width:48px;height:48px;color:#aebdcc"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div></div>
            <div class="field" style="margin-bottom:10px"><label class="field__label">判定理由</label><div class="cell-muted">学習中（PC使用）と判定されました。</div></div>
            <div class="form-grid">
              <div class="field"><label class="field__label">分析結果（修正）</label><select class="select"><option>学習中（PC不使用・読書または筆記）</option><option selected>学習中（PC使用）</option><option>離席中</option><option>PC使用中（非学習）</option><option>その他</option><option>判断不可</option></select></div>
              <div class="field"><label class="field__label">修正理由</label><textarea class="textarea" placeholder="必要に応じて修正理由を入力" rows="2"></textarea></div>
            </div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">修正を保存</button></div>
        </div>
      </div><div class="overlay" id="bulkDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">分析結果を一括変更</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="cell-muted" id="bulkCount" style="margin-bottom:12px">0件の画像を変更します。</div>
            <div class="form-grid">
              <div class="field"><label class="field__label">分析結果</label><select class="select"><option>学習中（PC不使用・読書または筆記）</option><option>学習中（PC使用）</option><option>離席中</option><option>PC使用中（非学習）</option><option>その他</option><option>判断不可</option></select></div>
              <div class="field"><label class="field__label">共通の修正理由</label><textarea class="textarea" rows="2"></textarea></div>
            </div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">一括保存</button></div>
        </div>
      </div><div class="overlay" id="timelineDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">行動タイムライン</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="stack" style="gap:8px">
              <div class="row" style="justify-content:space-between;border:1px solid var(--color-border);border-radius:8px;padding:8px 12px"><span class="cell-muted">08:00</span><span class="badge badge--success">学習中（PC不使用）</span><span class="cell-muted">読書</span></div>
              <div class="row" style="justify-content:space-between;border:1px solid var(--color-border);border-radius:8px;padding:8px 12px"><span class="cell-muted">09:00</span><span class="badge badge--info">学習中（PC使用）</span><span class="cell-muted">英作文</span></div>
              <div class="row" style="justify-content:space-between;border:1px solid var(--color-border);border-radius:8px;padding:8px 12px"><span class="cell-muted">10:00</span><span class="badge badge--warning">離席中</span><span class="cell-muted">休憩</span></div>
            </div>
          </div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .snap-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 8px; }
    .snap-card { border: 1px solid var(--color-border); border-radius: 8px; overflow: hidden; cursor: pointer; background: var(--color-surface); }
    .snap-card__thumb { aspect-ratio: 4/3; background: var(--color-surface-alt); display: flex; align-items: center; justify-content: center; color: var(--color-text-subtle); }
    .snap-card__meta { padding: 4px 6px; font-size: 11px; color: var(--color-text-muted); }
    .monitor-summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
    @media (max-width:1024px){.monitor-summary{grid-template-columns:repeat(2,1fr)}}
  
</style>
