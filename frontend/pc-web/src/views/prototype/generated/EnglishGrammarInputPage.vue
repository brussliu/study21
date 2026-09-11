<template>
  <div class="prototype-screen prototype-screen--english-grammar-input">
    <section class="card">
              <div class="card__header"><h2 class="card__title"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>文法登録</h2><span class="card__sub"><span class="badge badge--neutral">草稿</span> 管理ID：EG-DRAFT-001 · 最終編集：たった今</span></div>
              <div class="card__body">
                <h3 class="form-section-title">出典情報</h3>
                <div class="form-grid">
                  <div class="field"><label class="field__label">出典区分</label><select class="select"><option>自作</option><option>学校教材</option><option>塾教材</option><option>参考書</option></select></div>
                  <div class="field"><label class="field__label">出典名</label><input class="input" placeholder="出典名"></div>
                  <div class="field"><label class="field__label">参照ページ</label><input class="input" placeholder="p.12"></div>
                </div>
                <h3 class="form-section-title">基本情報</h3>
                <div class="form-grid">
                  <div class="field"><label class="field__label">ステータス</label><select class="select"><option>草稿</option><option>処理中</option><option>確認中</option><option>公開中</option></select></div>
                  <div class="field"><label class="field__label">等級</label><select class="select"><option>2級</option><option>準2級</option><option>3級以下</option></select></div>
                  <div class="field"><label class="field__label">分野</label><select class="select"><option>時制</option><option>節・構文</option><option>準動詞</option><option>修飾・比較</option><option>その他</option></select></div>
                  <div class="field"><label class="field__label">文法タイトル</label><input class="input" placeholder="現在完了形"></div>
                </div>
                <h3 class="form-section-title">説明</h3>
                <div class="form-grid">
                  <div class="field"><label class="field__label">説明文</label><textarea class="textarea" rows="3"></textarea></div>
                  <div class="field"><label class="field__label">キーポイント</label><input class="input" placeholder="カンマ区切り"></div>
                </div>
                <h3 class="form-section-title">文法公式</h3>
                <div class="form-grid">
                  <div class="field"><label class="field__label">公式</label><input class="input" placeholder="have/has + 過去分詞"></div>
                  <div class="field"><label class="field__label">補足説明</label><input class="input"></div>
                </div>
                <h3 class="form-section-title">例文</h3>
                <div class="form-grid">
                  <div class="field"><label class="field__label">英文</label><textarea class="textarea" rows="2"></textarea></div>
                  <div class="field"><label class="field__label">日本語訳</label><textarea class="textarea" rows="2"></textarea></div>
                </div>
                <h3 class="form-section-title">注意点 / よくある間違い</h3>
                <div class="form-grid">
                  <div class="field"><label class="field__label">注意点</label><textarea class="textarea" rows="2"></textarea></div>
                  <div class="field"><label class="field__label">誤用 / 正しい形</label><div class="stack" style="gap:6px"><input class="input" placeholder="誤用"><input class="input" placeholder="正しい形"></div></div>
                </div>
              </div>
              <div class="card__footer">
                <button class="btn btn--secondary" data-route-screen="english-grammar">一覧へ戻る</button>
                <button class="btn btn--secondary" id="grReset">リセット</button>
                <button class="btn btn--secondary" id="grDraft">下書き保存</button>
                <button class="btn btn--primary" id="grSave"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>保存</button>
              </div>
            </section>
  </div>
</template>

<style scoped>
.form-section-title { font-size: 15px; font-weight: 600; margin: 20px 0 10px; display: flex; align-items: center; gap: 6px; } .form-section-title:first-child { margin-top: 0; }
</style>
