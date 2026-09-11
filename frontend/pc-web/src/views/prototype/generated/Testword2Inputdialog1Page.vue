<template>
  <div class="prototype-screen prototype-screen--testword2-inputdialog1">
    <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body">testword2_inputdialog1.jsp（複数作成 中級編）のマッピング。<a href="#" data-route-screen="testword">testword.html</a> 内にも同ダイアログが埋め込まれています。</div></div>
            <div class="state state--empty"><div class="state__icon"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg></div><div class="state__title">複数作成ダイアログ（中級編）</div><div class="state__desc">書籍 / 分類 / 数量 / テスト種別（A～F）</div><div class="state__action"><button class="btn btn--primary" data-open-dialog="randomDlg">ダイアログを開く</button></div></div>
          <div class="overlay" id="randomDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title">複数作成（中級編）</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">書籍</label><select class="select"><option>中級英単語 1800</option></select></div>
            <div class="field"><label class="field__label">数量</label><select class="select"><option>全部</option><option>10語</option></select></div>
            <div class="field"><label class="field__label">テスト種別</label><select class="select"><option>A.勉強</option><option>B.中日訳英</option><option>C.音訳英</option><option>D.英訳中日</option><option>E.文脈英訳</option><option>F.例文入力</option></select></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">作成</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
