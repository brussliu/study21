<template>
  <div class="prototype-screen prototype-screen--word-question-fix">
    <div class="fix-layout">
              <section class="card">
                <div class="card__header"><h2 class="card__title">問題一覧</h2></div>
                <div class="card__body" style="padding:0">
                  <div class="table-wrap"><table class="data-table">
                    <thead><tr><th>等級</th><th>書籍</th><th>分類</th><th>SEQ</th><th>連番</th><th>問題不備</th><th>正解</th></tr></thead>
                    <tbody id="listBody"><tr><td>2級</td><td>キクタン</td><td>Day01</td><td>1</td><td>1</td><td><span class="badge badge--danger">あり</span></td><td>捨てる</td></tr><tr><td>2級</td><td>キクタン</td><td>Day01</td><td>2</td><td>1</td><td><span class="badge badge--success">なし</span></td><td>達成する</td></tr><tr><td>3級</td><td>三級単語</td><td>Day01</td><td>1</td><td>2</td><td><span class="badge badge--danger">あり</span></td><td>獲得する</td></tr></tbody>
                  </table></div>
                </div>
              </section>
              <section class="card">
                <div class="card__header"><h2 class="card__title">問題補正</h2><span class="card__sub"><span class="badge badge--warning">未保存</span></span></div>
                <div class="card__body">
                  <div class="row" style="gap:6px;margin-bottom:12px"><span class="badge badge--outline">2級</span><span class="badge badge--outline">キクタン</span><span class="badge badge--outline">Day01 / SEQ 1 / 連番 1</span></div>
                  <div class="form-grid">
                    <div class="field"><label class="field__label">質問</label><input class="input" value="abandon の意味は？"></div>
                    <div class="field"><label class="field__label">正解</label><input class="input" value="捨てる"></div>
                    <div class="field"><label class="field__label">誤解1</label><input class="input" value="達成する"></div>
                    <div class="field"><label class="field__label">誤解2</label><input class="input" value="獲得する"></div>
                    <div class="field"><label class="field__label">誤解3</label><input class="input" value="延期する"></div>
                    <div class="field"><label class="field__label">説明</label><textarea class="textarea" rows="2"></textarea></div>
                  </div>
                  <label class="check" style="margin-top:12px"><input type="checkbox" id="issue">問題不備あり</label>
                  <div class="field" style="margin-top:8px"><label class="field__label">問題不備メモ</label><input class="input"></div>
                </div>
                <div class="card__footer">
                  <button class="btn btn--secondary" id="release">不備解除</button>
                  <button class="btn btn--primary" id="save"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>保存</button>
                </div>
              </section>
            </div>
  </div>
</template>

<style scoped>
.fix-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; } @media (max-width:900px){.fix-layout{grid-template-columns:1fr}}
</style>
