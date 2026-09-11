<template>
  <div class="prototype-screen prototype-screen--math">
    <div class="math-hero">
              <button class="btn btn--secondary" id="reanalyze"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-sun"></use></svg>再分析</button>
              <a class="btn btn--primary" href="#" data-route-screen="math-knowledge">知識点管理へ</a>
              <a class="btn btn--primary" href="#" data-route-screen="math-wrong">誤問題集へ</a>
              <a class="btn btn--primary" href="#" data-route-screen="geometry">図形作成へ</a>
            </div>
    
            <div class="stat-grid">
              <div class="stat-card"><div class="stat-card__icon" style="background:color-mix(in srgb,var(--subject-math) 12%,transparent);color:var(--subject-math)"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-alert"></use></svg></div><div><div class="stat-card__label">分析対象</div><div class="stat-card__value">24件</div><div class="stat-card__sub">数学の誤問題</div></div></div>
              <div class="stat-card"><div class="stat-card__icon" style="background:color-mix(in srgb,var(--subject-math) 12%,transparent);color:var(--subject-math)"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></div><div><div class="stat-card__label">要対応</div><div class="stat-card__value">6件</div><div class="stat-card__sub">訂正中・復習中</div></div></div>
              <div class="stat-card"><div class="stat-card__icon" style="background:color-mix(in srgb,var(--subject-math) 12%,transparent);color:var(--subject-math)"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-check-circle"></use></svg></div><div><div class="stat-card__label">平均理解度</div><div class="stat-card__value">58%</div><div class="stat-card__sub">全体の定着感</div></div></div>
              <div class="stat-card"><div class="stat-card__icon" style="background:color-mix(in srgb,var(--subject-math) 12%,transparent);color:var(--subject-math)"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg></div><div><div class="stat-card__label">復習期限</div><div class="stat-card__value">3件</div><div class="stat-card__sub">今日までに見直す件数</div></div></div>
            </div>
    
            <div class="analysis-grid">
              <div class="analysis-card"><h3>分野の偏り</h3><div class="tag-chips"><span class="badge badge--subject-ma">図形</span><span class="badge badge--subject-ma">関数</span><span class="badge badge--outline">代数</span><span class="badge badge--outline">確率</span></div></div>
              <div class="analysis-card"><h3>原因キーワード</h3><div class="tag-chips"><span class="badge badge--warning">計算ミス</span><span class="badge badge--warning">公式忘れ</span><span class="badge badge--outline">条件見落とし</span></div></div>
              <div class="analysis-card"><h3>知識点の弱点</h3><div class="tag-chips"><span class="badge badge--danger">合同条件</span><span class="badge badge--danger">二次関数</span><span class="badge badge--outline">確率</span></div></div>
              <div class="analysis-card"><h3>誤答種別</h3><div class="tag-chips"><span class="badge badge--neutral">方針ミス</span><span class="badge badge--neutral">読み違い</span><span class="badge badge--outline">単位ミス</span></div></div>
            </div>
    
            <section class="card">
              <div class="card__header"><h2 class="card__title">最近の要注意問題</h2></div>
              <div class="card__body"><div class="stack" id="recentList" style="gap:8px"><div class="row" style="justify-content:space-between;border:1px solid var(--color-border);border-radius:8px;padding:10px 14px"><div><strong>三角形の合同証明</strong><span class="cell-muted" style="margin-left:8px">知識点：合同条件</span></div><div class="row" style="gap:8px"><span class="badge badge--warning">訂正中</span><span class="cell-muted">2026/08/15</span></div></div><div class="row" style="justify-content:space-between;border:1px solid var(--color-border);border-radius:8px;padding:10px 14px"><div><strong>二次関数の最大・最小</strong><span class="cell-muted" style="margin-left:8px">知識点：平方完成</span></div><div class="row" style="gap:8px"><span class="badge badge--info">復習中</span><span class="cell-muted">2026/08/15</span></div></div><div class="row" style="justify-content:space-between;border:1px solid var(--color-border);border-radius:8px;padding:10px 14px"><div><strong>確率の加法定理</strong><span class="cell-muted" style="margin-left:8px">知識点：排反事象</span></div><div class="row" style="gap:8px"><span class="badge badge--success">訂正済</span><span class="cell-muted">2026/08/18</span></div></div></div></div>
            </section>
  </div>
</template>

<style scoped>

    .math-hero { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
    .stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
    .analysis-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
    .analysis-card { border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 14px 16px; background: var(--color-surface); border-top: 3px solid var(--subject-math); }
    .analysis-card h3 { font-size: 13px; color: var(--color-text-muted); margin-bottom: 8px; }
    .tag-chips { display: flex; flex-wrap: wrap; gap: 6px; }
    @media (max-width: 1024px) { .stat-grid, .analysis-grid { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 640px) { .stat-grid, .analysis-grid { grid-template-columns: 1fr; } }
  
</style>
