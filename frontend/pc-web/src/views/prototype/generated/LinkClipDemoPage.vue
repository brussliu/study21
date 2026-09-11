<template>
  <div class="prototype-screen prototype-screen--link-clip-demo">
    <div class="clip-layout">
              <section class="card">
                <div class="card__header"><h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-bookmark"></use></svg>リンクを保存</h2></div>
                <div class="card__body stack" style="gap:10px">
                  <div class="field"><label class="field__label">URL</label><input class="input" placeholder="https://..."></div>
                  <div class="field"><label class="field__label">保存先</label><select class="select"><option>未整理</option><option>あとで見る</option><option>学習素材</option><option>参考資料</option><option>完了</option></select></div>
                  <div class="field"><label class="field__label">タイトル補足</label><input class="input" placeholder="タイトル補足"></div>
                  <div class="field"><label class="field__label">タグ</label><input class="input" placeholder="タグ"></div>
                  <div class="field"><label class="field__label">メモ</label><textarea class="textarea" rows="2"></textarea></div>
                  <div class="row" style="gap:8px"><button class="btn btn--primary" id="save"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>保存</button><button class="btn btn--secondary" id="newInput">新規入力</button></div>
                </div>
              </section>
    
              <div class="stack" style="gap:16px">
                <div class="card"><div class="card__header"><h2 class="card__title">プレビュー</h2><span class="card__sub">URL を入力すると Open Graph / oEmbed / AI 概要の完成イメージを表示します</span></div>
                  <div class="card__body"><div class="cell-muted">プレビューはここに表示されます（Mock）。</div></div>
                </div>
                <div class="row" style="gap:12px">
                  <div class="lane grow"><h4>取得したい情報（サイト名 / タイトル / 説明文）</h4><div class="cell-muted">—</div></div>
                  <div class="lane grow"><h4>AI要約 / 重要ポイント / 次アクション</h4><div class="cell-muted">—</div></div>
                </div>
                <div class="card"><div class="card__header"><h2 class="card__title">保存リンク一覧</h2><span class="card__sub">全 0 件</span></div>
                  <div class="card__body"><div class="state state--empty" style="padding:20px"><div class="state__title">リンクがありません</div><div class="state__desc">URL を保存するとここに表示されます。</div></div></div>
                </div>
              </div>
            </div>
  </div>
</template>

<style scoped>

    .clip-layout { display: grid; grid-template-columns: 320px 1fr; gap: 16px; }
    .lane { border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 12px; background: var(--color-surface); }
    .lane h4 { font-size: 12px; color: var(--color-text-muted); margin-bottom: 8px; }
    @media (max-width:900px){.clip-layout{grid-template-columns:1fr}}
  
</style>
