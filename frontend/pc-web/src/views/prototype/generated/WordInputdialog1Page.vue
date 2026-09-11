<template>
  <div class="prototype-screen prototype-screen--word-inputdialog1">
    <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body">word_inputdialog1.jsp（単語登録・編集ダイアログ）のマッピング。<a href="#" data-route-screen="word">word.html</a> / <a href="#" data-route-screen="english-reading-reader">english_reading_reader.html</a> 内にも同ダイアログが埋め込まれています。</div></div>
            <div class="state state--empty"><div class="state__icon"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></div><div class="state__title">単語登録・編集ダイアログ</div><div class="state__desc">基本情報 / 単語情報 / 例文1・例文2</div><div class="state__action"><button class="btn btn--primary" data-open-dialog="wordDlg">ダイアログを開く</button></div></div>
          <div class="overlay" id="wordDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">単語編集</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">英語単語</label><input class="input" value="abandon"></div>
            <div class="field"><label class="field__label">日本語訳</label><input class="input" value="捨てる"></div>
            <div class="field"><label class="field__label">中国語訳</label><input class="input" value="捨てる"></div>
            <div class="field"><label class="field__label">書籍</label><select class="select"><option>キクタン</option></select></div>
            <div class="field"><label class="field__label">分類</label><select class="select"><option>Day01</option></select></div>
            <div class="field"><label class="field__label">単語SEQ</label><input type="number" class="input" value="1"></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
