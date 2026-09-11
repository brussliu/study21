<template>
  <div class="prototype-screen prototype-screen--daily-report">
    <div class="report-wrap">
              <section class="card report-main">
                <div class="card__header">
                  <h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book"></use></svg>今日の記録</h2>
                  <span class="card__sub"><span class="badge badge--warning" id="saveState">未保存</span></span>
                </div>
                <div class="card__body">
                  <div class="row" style="margin-bottom:8px">
                    <div class="field"><label class="field__label">対象日</label><input type="date" class="input" id="reportDate" style="width:auto"></div>
                  </div>
    
                  <h3 class="section-title">１．授業順の学習記録</h3>
                  <div id="periods"><div class="period-row"><div class="field"><label class="field__label">科目</label><select class="select"><option>英語</option><option>数学</option><option>国語</option><option>その他</option></select></div><div class="field"><label class="field__label">授業内容</label><input class="input" placeholder="例：単語 Unit01"></div><div class="field"><label class="field__label">メモ</label><input class="input" placeholder="気づき・メモ"></div><button class="btn btn--icon" data-del-period="" aria-label="削除"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></div></div>
                  <button class="btn btn--secondary btn--sm" id="addPeriod"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>授業を追加</button>
    
                  <h3 class="section-title">２．全体の振り返り</h3>
                  <div class="field"><textarea class="textarea" id="reflect" placeholder="今日の学習を振り返って記入"></textarea></div>
    
                  <h3 class="section-title">３．自己評価</h3>
                  <div class="form-grid">
                    <div class="field"><label class="field__label">学習への集中度</label><span class="stars" data-rate="focus"><button type="button" data-v="1" aria-label="1点">★</button><button type="button" data-v="2" aria-label="2点">★</button><button type="button" data-v="3" aria-label="3点">★</button><button type="button" data-v="4" aria-label="4点">★</button><button type="button" data-v="5" aria-label="5点">★</button></span></div>
                    <div class="field"><label class="field__label">理解度</label><span class="stars" data-rate="understand"><button type="button" data-v="1" aria-label="1点">★</button><button type="button" data-v="2" aria-label="2点">★</button><button type="button" data-v="3" aria-label="3点">★</button><button type="button" data-v="4" aria-label="4点">★</button><button type="button" data-v="5" aria-label="5点">★</button></span></div>
                    <div class="field"><label class="field__label">学習量</label><span class="stars" data-rate="volume"><button type="button" data-v="1" aria-label="1点">★</button><button type="button" data-v="2" aria-label="2点">★</button><button type="button" data-v="3" aria-label="3点">★</button><button type="button" data-v="4" aria-label="4点">★</button><button type="button" data-v="5" aria-label="5点">★</button></span></div>
                    <div class="field"><label class="field__label">学習態度</label><span class="stars" data-rate="attitude"><button type="button" data-v="1" aria-label="1点">★</button><button type="button" data-v="2" aria-label="2点">★</button><button type="button" data-v="3" aria-label="3点">★</button><button type="button" data-v="4" aria-label="4点">★</button><button type="button" data-v="5" aria-label="5点">★</button></span></div>
                  </div>
    
                  <h3 class="section-title">４．今夜宿題の想定内容</h3>
                  <div class="field"><textarea class="textarea" id="homework" placeholder="今夜取り組む宿題"></textarea></div>
                </div>
              </section>
    
              <aside class="card report-side">
                <div class="card__header"><h2 class="card__title">今日のサマリー</h2></div>
                <div class="card__body stack" style="gap:8px">
                  <div class="row" style="justify-content:space-between"><span class="cell-muted">集中度</span><strong id="sumFocus">-</strong></div>
                  <div class="row" style="justify-content:space-between"><span class="cell-muted">理解度</span><strong id="sumUnderstand">-</strong></div>
                  <div class="row" style="justify-content:space-between"><span class="cell-muted">学習量</span><strong id="sumVolume">-</strong></div>
                  <div class="row" style="justify-content:space-between"><span class="cell-muted">学習態度</span><strong id="sumAttitude">-</strong></div>
                  <hr style="border:none;border-top:1px solid var(--color-border)">
                  <div class="row" style="justify-content:space-between"><span class="cell-muted">記録授業数</span><strong id="sumLessons">1限</strong></div>
                  <div class="row" style="justify-content:space-between"><span class="cell-muted">対象日</span><strong id="sumDate">2026-08-28</strong></div>
                </div>
                <div class="card__footer" style="justify-content:flex-start">
                  <button class="btn btn--primary" id="saveReport"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>保存</button>
                  <button class="btn btn--secondary" id="clearReport">入力クリア</button>
                </div>
                <div class="card__header" style="border-top:1px solid var(--color-border)"><h3 class="card__title" style="font-size:14px">最近の学習日報</h3></div>
                <div class="card__body stack" id="recentList" style="gap:6px"><div class="row" style="justify-content: space-between;"><span class="cell-muted">2026/08/11</span><span class="badge badge--neutral">記録済み</span></div><div class="row" style="justify-content: space-between;"><span class="cell-muted">2026/08/10</span><span class="badge badge--neutral">記録済み</span></div><div class="row" style="justify-content: space-between;"><span class="cell-muted">2026/08/09</span><span class="badge badge--neutral">記録済み</span></div></div>
              </aside>
            </div>
  </div>
</template>

<style scoped>

    .report-wrap { display: flex; gap: 16px; align-items: flex-start; }
    .report-main { flex: 1; min-width: 0; }
    .report-side { flex: 0 0 260px; position: sticky; top: 76px; }
    .period-row { display: grid; grid-template-columns: 1fr 1fr 2fr; gap: 10px; align-items: center; margin-bottom: 10px; }
    .stars { display: inline-flex; gap: 2px; }
    .stars button { font-size: 20px; line-height: 1; color: var(--color-border-strong); background: none; border: none; cursor: pointer; }
    .stars button.is-on { color: var(--color-warning); }
    .section-title { font-size: 15px; font-weight: 600; margin: 20px 0 10px; display: flex; align-items: center; gap: 6px; }
    .section-title:first-child { margin-top: 0; }
    @media (max-width: 900px) { .report-wrap { flex-direction: column; } .report-side { position: static; flex: 1; width: 100%; } .period-row { grid-template-columns: 1fr; } }
  
</style>
