<template>
  <div class="prototype-screen prototype-screen--home">
    <!-- 统计卡片 -->
            <div class="stats-grid">
              <div class="stat-card"><div class="stat-card__icon"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-sun"></use></svg></div><div><div class="stat-card__label">今日の天気</div><div class="stat-card__value">18°C / 12°C</div><div class="stat-card__sub">東京・晴れ</div></div></div>
    
              <div class="stat-card">
                <div class="stat-card__icon"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg></div>
                <div>
                  <div class="stat-card__label">学習タイマー
                    <button class="btn btn--icon btn--sm" id="studyFocusOpen" title="全画面で開く" aria-label="全画面で開く"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-zoom-in"></use></svg></button>
                  </div>
                  <div class="timer-time" id="studyClock">00:30:00</div>
                  <div class="timer-actions">
                    <button class="btn btn--primary btn--sm" id="studyStart"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>開始</button>
                    <button class="btn btn--secondary btn--sm" id="studyStop" disabled><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg>停止</button>
                  </div>
                </div>
              </div>
    
              <div class="stat-card">
                <div class="stat-card__icon"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg></div>
                <div>
                  <div class="stat-card__label">休憩タイマー</div>
                  <div class="timer-time" id="breakClock">00:15:00</div>
                  <div class="timer-actions">
                    <button class="btn btn--primary btn--sm" id="breakStart"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>開始</button>
                    <button class="btn btn--secondary btn--sm" id="breakStop" disabled><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg>停止</button>
                  </div>
                </div>
              </div>
    
              <div class="stat-card">
                <div class="stat-card__icon"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-check-circle"></use></svg></div>
                <div><div class="stat-card__label">連続学習</div><div class="stat-card__value">7日</div><div class="stat-card__sub">Streak</div></div>
              </div>
            </div>
    
            <!-- 学习状态 -->
            <section class="card">
              <div class="card__header"><h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-square"></use></svg>学習ステータス</h2></div>
              <div class="card__body">
                <div class="task-grid" id="taskGrid">
                  <div class="task-item"><div class="task-item__title">DMM英会話</div><div class="task-slider" data-status="0"><span class="task-seg is-active" data-i="0">未実施</span><span class="task-seg" data-i="1">実施中</span><span class="task-seg" data-i="2">実施済</span></div></div>
                  <div class="task-item"><div class="task-item__title">単語暗記</div><div class="task-slider" data-status="0"><span class="task-seg is-active" data-i="0">未実施</span><span class="task-seg" data-i="1">実施中</span><span class="task-seg" data-i="2">実施済</span></div></div>
                  <div class="task-item"><div class="task-item__title">英語閲読</div><div class="task-slider" data-status="0"><span class="task-seg is-active" data-i="0">未実施</span><span class="task-seg" data-i="1">実施中</span><span class="task-seg" data-i="2">実施済</span></div></div>
                  <div class="task-item"><div class="task-item__title">数学のレッスン</div><div class="task-slider" data-status="0"><span class="task-seg is-active" data-i="0">未実施</span><span class="task-seg" data-i="1">実施中</span><span class="task-seg" data-i="2">実施済</span></div></div>
                  <div class="task-item"><div class="task-item__title">新概念のレッスン</div><div class="task-slider" data-status="0"><span class="task-seg is-active" data-i="0">未実施</span><span class="task-seg" data-i="1">実施中</span><span class="task-seg" data-i="2">実施済</span></div></div>
                </div>
              </div>
            </section>
    
            <!-- 上网状况 -->
            <section class="card">
              <div class="card__header">
                <h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-globe"></use></svg>最近3日 上網状況（時間帯別）</h2>
                <select class="select" style="width:auto" aria-label="区分"><option>2.休憩</option><option>1.通常</option><option>3.ゲーム</option></select>
              </div>
              <div class="card__body">
                <div class="bar-chart" aria-label="上网柱状图">
                  <div class="bar-chart__col"><div class="bar-chart__bar" style="height:20%"></div><div class="bar-chart__bar is-rest" style="height:34%"></div><span class="bar-chart__label">08時</span></div>
                  <div class="bar-chart__col"><div class="bar-chart__bar" style="height:44%"></div><div class="bar-chart__bar is-rest" style="height:12%"></div><span class="bar-chart__label">12時</span></div>
                  <div class="bar-chart__col"><div class="bar-chart__bar" style="height:62%"></div><div class="bar-chart__bar is-rest" style="height:22%"></div><span class="bar-chart__label">16時</span></div>
                  <div class="bar-chart__col"><div class="bar-chart__bar" style="height:80%"></div><div class="bar-chart__bar is-rest" style="height:30%"></div><span class="bar-chart__label">20時</span></div>
                  <div class="bar-chart__col"><div class="bar-chart__bar" style="height:28%"></div><div class="bar-chart__bar is-rest" style="height:18%"></div><span class="bar-chart__label">23時</span></div>
                </div>
              </div>
            </section>
    
            <!-- 学习日程 -->
            <section class="card">
              <div class="card__header">
                <h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg>学習スケジュール</h2>
                <div class="pill-tabs"><button class="pill-tabs__tab is-active">週</button><button class="pill-tabs__tab">月</button></div>
              </div>
              <div class="card__body">
                <div class="calendar-mock">
                  <span class="calendar-mock__head">日</span><span class="calendar-mock__head">月</span><span class="calendar-mock__head">火</span><span class="calendar-mock__head">水</span><span class="calendar-mock__head">木</span><span class="calendar-mock__head">金</span><span class="calendar-mock__head">土</span>
                  <div class="calendar-mock__day">2</div><div class="calendar-mock__day">3</div><div class="calendar-mock__day">4</div><div class="calendar-mock__day is-today">5<br><span class="badge badge--primary" style="margin-top:4px">英検</span></div><div class="calendar-mock__day">6</div><div class="calendar-mock__day">7</div><div class="calendar-mock__day">8</div>
                </div>
              </div>
            </section>
    
            <div class="weekly-goal"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-circle"></use></svg>週間学習目標: 12h / 20h</div>
          <div class="overlay" id="reminderModal" data-close-on-backdrop="">
        <div class="dialog dialog--sm" role="dialog" aria-modal="true" data-size="sm">
          <div class="dialog__body" style="text-align:center;padding:28px 24px">
            <div class="state__icon" style="background:var(--color-primary-soft);color:var(--color-primary);width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;margin:0 auto 12px"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book"></use></svg></div>
            <div class="confirm-dialog__title">今日の学習日報がまだありません</div>
            <div class="confirm-dialog__msg">今日の学習内容を振り返って、記録を残しましょう。</div>
          </div>
          <div class="dialog__foot" style="justify-content:center">
            <button class="btn btn--secondary" data-dialog-close="">あとで</button>
            <a class="btn btn--primary" href="#" data-route-screen="daily-report"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>学習日報を書く</a>
          </div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    /* 页面内容层样式（home 专用） */
    .stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
    .timer-time { font-family: "SF Mono", "Cascadia Code", Consolas, monospace; font-size: 30px; font-weight: 700; font-variant-numeric: tabular-nums; }
    .timer-actions { display: flex; gap: 8px; margin-top: 8px; }
    .task-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
    .task-item { border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 12px 14px; }
    .task-item__title { font-size: 13px; font-weight: 600; margin-bottom: 8px; }
    .task-slider { display: flex; gap: 6px; }
    .task-seg { flex: 1; text-align: center; font-size: 11px; padding: 5px 2px; border-radius: 4px; background: var(--color-surface-alt); color: var(--color-text-muted); cursor: pointer; }
    .task-seg.is-active { background: var(--color-primary); color: #fff; }
    .bar-chart { display: flex; align-items: flex-end; gap: 10px; height: 160px; padding-top: 12px; }
    .bar-chart__col { flex: 1; display: flex; flex-direction: column; gap: 3px; align-items: center; }
    .bar-chart__bar { width: 60%; min-width: 14px; background: var(--color-primary); border-radius: 3px 3px 0 0; min-height: 4px; }
    .bar-chart__bar.is-rest { background: var(--subject-japanese); opacity: .7; }
    .bar-chart__label { font-size: 11px; color: var(--color-text-muted); }
    .calendar-mock { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }
    .calendar-mock__day { border: 1px solid var(--color-border); border-radius: 6px; min-height: 56px; padding: 6px; font-size: 12px; }
    .calendar-mock__day.is-today { border-color: var(--color-primary); background: var(--color-primary-soft); }
    .calendar-mock__head { font-weight: 600; text-align: center; font-size: 12px; color: var(--color-text-muted); padding: 4px 0; }
    .weekly-goal { display: inline-flex; align-items: center; gap: 8px; padding: 12px 16px; border-radius: 999px; background: var(--color-primary-soft); color: var(--color-primary); font-weight: 600; font-size: 14px; }
    @media (max-width: 1024px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 640px) { .stats-grid { grid-template-columns: 1fr; } }
  
</style>
