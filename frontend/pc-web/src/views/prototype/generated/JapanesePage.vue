<template>
  <div class="prototype-screen prototype-screen--japanese">
    <div class="motivation-grid">
              <div class="motivation-card"><span class="motivation-badge">All Time</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg>累計学習時間</div><div class="motivation-card__value">24時間 18分</div><div class="motivation-card__sub">日本語単語 Demo</div></div>
              <div class="motivation-card"><span class="motivation-badge">Level</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-circle"></use></svg>学習レベル</div><div class="motivation-card__value">Lv.3</div><div class="motivation-card__sub">次の Lv.4 まで あと16時間</div></div>
              <div class="motivation-card"><span class="motivation-badge">JLPT</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-square"></use></svg>到達目標</div><div class="motivation-card__value">N2</div><div class="motivation-card__sub">次の目標：N1 チャレンジ</div></div>
              <div class="motivation-card"><span class="motivation-badge">Today</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg>今日の学習時間</div><div class="motivation-card__value">18分32秒</div><div class="motivation-card__sub">単語学習のデモ集計</div></div>
              <div class="motivation-card"><span class="motivation-badge">Reading</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book-open"></use></svg>今日の読解時間</div><div class="motivation-card__value">12分</div><div class="motivation-card__sub">例文・文脈学習</div></div>
              <div class="motivation-card"><span class="motivation-badge">Streak</span><div class="motivation-card__label"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-sun"></use></svg>連続学習</div><div class="motivation-card__value">7日</div><div class="motivation-card__sub">前日から継続中</div></div>
            </div>
    
            <div class="chart-grid">
              <section class="card"><div class="card__header"><h2 class="card__title">レベル別 学習レーダー</h2><span class="card__sub">Demo</span></div>
                <div class="card__body"><div class="radar-grid"><div class="radar-item"><strong>78%</strong><span class="cell-muted">意味・表記・読み</span></div><div class="radar-item"><strong>64%</strong><span class="cell-muted">文脈・使い分け</span></div></div></div>
              </section>
              <section class="card"><div class="card__header"><h2 class="card__title">直近単語テスト情報統計</h2><span class="card__sub">過去1ヶ月</span></div>
                <div class="card__body"><div style="text-align:center"><strong style="font-size:28px">86問</strong><div class="cell-muted">意味・読み・漢字テストのデモ集計</div></div></div>
              </section>
              <section class="card"><div class="card__header"><h2 class="card__title">単語勉強状況遷移図</h2><span class="card__sub">累積推移</span></div>
                <div class="card__body"><div style="text-align:center"><strong style="font-size:28px">128語</strong><div class="cell-muted">学習済み単語</div></div></div>
              </section>
            </div>
    
            <div class="row" style="justify-content:space-between;align-items:center">
              <div class="cell-muted" style="font-size:12px"><svg class="icon icon--sm"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg>最終学習 2026/08/13 14:30</div>
              <a class="btn btn--primary" href="#" data-route-screen="japanese-test"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>単語テストを開始</a>
            </div>
  </div>
</template>

<style scoped>

    .motivation-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    .motivation-card { border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 16px 18px; background: var(--color-surface); box-shadow: var(--shadow-sm); border-top: 3px solid var(--subject-japanese); }
    .motivation-card__label { font-size: 13px; color: var(--color-text-muted); display: flex; align-items: center; gap: 6px; }
    .motivation-card__value { font-size: 26px; font-weight: 700; margin-top: 8px; }
    .motivation-card__sub { font-size: 12px; color: var(--color-text-subtle); margin-top: 4px; }
    .motivation-badge { float: right; font-size: 11px; color: var(--subject-japanese); border: 1px solid var(--subject-japanese); padding: 1px 8px; border-radius: 999px; }
    .chart-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 16px; }
    .radar-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
    .radar-item { border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 12px; text-align: center; }
    .radar-item strong { font-size: 22px; display: block; }
    @media (max-width: 1024px) { .motivation-grid { grid-template-columns: repeat(2, 1fr); } .chart-grid { grid-template-columns: 1fr; } }
    @media (max-width: 640px) { .motivation-grid { grid-template-columns: 1fr; } }
  
</style>
