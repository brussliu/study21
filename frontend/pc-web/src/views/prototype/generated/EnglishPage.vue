<template>
  <div class="prototype-screen prototype-screen--english">
    <div class="motivation-grid">
              <div class="motivation-card"><span class="motivation-badge">All Time</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg>累計学習時間</div><div class="motivation-card__value">128時間 45分</div><div class="motivation-card__sub">累計学習時間を集計</div></div>
              <div class="motivation-card"><span class="motivation-badge">Level</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-circle"></use></svg>学習レベル</div><div class="motivation-card__value">Lv.7</div><div class="motivation-card__sub">次の Lv.8 まで あと 12時間</div></div>
              <div class="motivation-card"><span class="motivation-badge">英検</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-square"></use></svg>取得級</div><div class="motivation-card__value">2級</div><div class="motivation-card__sub">次の目標：準1級チャレンジ</div></div>
              <div class="motivation-card"><span class="motivation-badge">Today</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg>今日の学習時間</div><div class="motivation-card__value">1時間 12分</div><div class="motivation-card__sub">本日の学習時間</div></div>
              <div class="motivation-card"><span class="motivation-badge">Reading</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book-open"></use></svg>今日の読書時間</div><div class="motivation-card__value">24分</div><div class="motivation-card__sub">本日の読書時間</div></div>
              <div class="motivation-card"><span class="motivation-badge">Streak</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-sun"></use></svg>連続学習</div><div class="motivation-card__value">7日</div><div class="motivation-card__sub">直近データから集計</div></div>
            </div>
    
            <div class="chart-grid">
              <section class="card"><div class="card__header"><h2 class="card__title">レベル別 学習レーダー</h2><span class="card__sub">全12レベル</span></div>
                <div class="card__body"><div class="radar-grid">
                  <div class="radar-item"><strong>78%</strong><span class="cell-muted">語彙理解</span></div>
                  <div class="radar-item"><strong>64%</strong><span class="cell-muted">読解</span></div>
                  <div class="radar-item"><strong>52%</strong><span class="cell-muted">文法</span></div>
                  <div class="radar-item"><strong>71%</strong><span class="cell-muted">リスニング</span></div>
                </div></div>
              </section>
              <section class="card"><div class="card__header"><h2 class="card__title">直近単語テスト情報統計</h2><span class="card__sub">過去1ヶ月</span></div>
                <div class="card__body"><div class="bar-chart" style="height:140px;align-items:flex-end;display:flex;gap:10px">
                  <div style="flex:1;background:var(--color-primary);height:40%;border-radius:4px 4px 0 0"></div>
                  <div style="flex:1;background:var(--color-primary);height:62%;border-radius:4px 4px 0 0"></div>
                  <div style="flex:1;background:var(--color-primary);height:50%;border-radius:4px 4px 0 0"></div>
                  <div style="flex:1;background:var(--color-primary);height:74%;border-radius:4px 4px 0 0"></div>
                </div><div class="cell-muted" style="margin-top:8px">週ごとの正解率推移（Mock）</div></div>
              </section>
              <section class="card"><div class="card__header"><h2 class="card__title">単語勉強状況遷移図</h2><span class="card__sub">累積推移</span></div>
                <div class="card__body"><div style="text-align:center"><strong style="font-size:28px">1,248語</strong><div class="cell-muted">学習済み単語</div></div></div>
              </section>
            </div>
    
            <section class="card">
              <div class="card__header"><h2 class="card__title">英語学習機能</h2><span class="card__sub">Quick access（メニュー外機能含む）</span></div>
              <div class="card__body"><div class="quick-grid">
                <a class="quick-link" href="#" data-route-screen="english-reading"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book-open"></use></svg>読書管理</a>
                <a class="quick-link" href="#" data-route-screen="english-reading-intensive"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book"></use></svg>英語読解・精読</a>
                <a class="quick-link" href="#" data-route-screen="english-essay"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>英作文AI添削</a>
                <a class="quick-link" href="#" data-route-screen="english-cloze"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-filter"></use></svg>英語穴埋め問題</a>
                <a class="quick-link" href="#" data-route-screen="ai"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg>AI英語学習</a>
                <a class="quick-link" href="#" data-route-screen="english-grammar"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-type"></use></svg>英語文法管理</a>
                <a class="quick-link" href="#" data-route-screen="english-word-textbook"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-folder"></use></svg>英単語教材取込</a>
              </div></div>
            </section>
    
            <div class="cell-muted" style="font-size:12px"><svg class="icon icon--sm"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg>最終学習 2026/08/13 14:30</div>
  </div>
</template>

<style scoped>

    .motivation-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    .motivation-card { border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 16px 18px; background: var(--color-surface); box-shadow: var(--shadow-sm); }
    .motivation-card__label { font-size: 13px; color: var(--color-text-muted); display: flex; align-items: center; gap: 6px; }
    .motivation-card__value { font-size: 26px; font-weight: 700; margin-top: 8px; }
    .motivation-card__sub { font-size: 12px; color: var(--color-text-subtle); margin-top: 4px; }
    .motivation-badge { float: right; font-size: 11px; color: var(--color-text-subtle); border: 1px solid var(--color-border); padding: 1px 8px; border-radius: 999px; }
    .chart-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 16px; }
    .radar-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
    .radar-item { border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 12px; text-align: center; }
    .radar-item strong { font-size: 22px; display: block; }
    .quick-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 10px; }
    .quick-link { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border: 1px solid var(--color-border); border-radius: var(--radius-md); color: var(--color-text); font-size: 13px; }
    .quick-link:hover { border-color: var(--color-primary); color: var(--color-primary); }
    @media (max-width: 1024px) { .motivation-grid { grid-template-columns: repeat(2, 1fr); } .chart-grid { grid-template-columns: 1fr; } }
    @media (max-width: 640px) { .motivation-grid { grid-template-columns: 1fr; } }
  
</style>
