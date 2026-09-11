<template>
  <div class="prototype-screen prototype-screen--english-reading-input">
    <section class="card">
              <div class="card__header"><h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>英語読書書籍登録</h2><span class="card__sub"><span class="badge badge--primary">新規作成</span></span></div>
              <div class="card__body">
                <h3 class="section-title" style="font-size:14px;font-weight:600;margin:0 0 12px">基本情報</h3>
                <div class="form-grid">
                  <div class="field"><label class="field__label">書籍名（必須）</label><input class="input" placeholder="書籍名"></div>
                  <div class="field"><label class="field__label">作者（必須）</label><input class="input" placeholder="作者"></div>
                  <div class="field"><label class="field__label">難易度</label><select class="select"><option>Starter</option><option>Elementary</option><option>Intermediate</option><option>Upper</option></select></div>
                  <div class="field"><label class="field__label">総ページ数（必須）</label><input type="number" class="input" placeholder="100"></div>
                </div>
                <h3 class="section-title" style="font-size:14px;font-weight:600;margin:16px 0 12px">読書設定</h3>
                <div class="form-grid">
                  <div class="field"><label class="field__label">タグ</label><input class="input" placeholder="Oxford, Classic, Vocabulary..."></div>
                  <div class="field"><label class="field__label">概要・読書方針</label><textarea class="textarea" rows="3"></textarea></div>
                </div>
                <h3 class="section-title" style="font-size:14px;font-weight:600;margin:16px 0 12px">PDF・関連資料</h3>
                <div class="dropzone"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>本文 PDF / 表紙・補助画像を選択</div>
              </div>
              <div class="card__footer">
                <button class="btn btn--secondary" data-route-screen="english-reading">一覧へ戻る</button>
                <button class="btn btn--secondary" id="resetBtn">リセット</button>
                <button class="btn btn--primary" id="saveBtn"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>保存</button>
              </div>
            </section>
  </div>
</template>

<style scoped>

</style>
