<template>
  <div class="prototype-screen prototype-screen--testinfo-inputdialog1">
    <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body">testinfo_inputdialog1.jsp（テスト新規ダイアログ）のマッピング。<a href="#" data-route-screen="testinfo">testinfo.html</a> 内にも同ダイアログが埋め込まれています。</div></div>
            <div class="state state--empty"><div class="state__icon"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clipboard"></use></svg></div><div class="state__title">テスト新規ダイアログ</div><div class="state__desc">テスト番号/テスト名/教科/区分/試験日/得点/正答率/メモ/試験用紙（≤10件）</div><div class="state__action"><button class="btn btn--primary" data-open-dialog="testDlg">ダイアログを開く</button></div></div>
          <div class="overlay" id="testDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clipboard"></use></svg>テスト新規</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">テスト番号</label><input class="input" value="T-0004" disabled></div>
            <div class="field"><label class="field__label">テスト名</label><input class="input" placeholder="テスト名"></div>
            <div class="field"><label class="field__label">教科</label><select class="select"><option>英語</option><option>数学</option></select></div>
            <div class="field"><label class="field__label">区分</label><select class="select"><option>通常</option><option>模擬</option></select></div>
            <div class="field"><label class="field__label">試験日</label><input type="date" class="input"></div>
            <div class="field"><label class="field__label">正答率</label><input class="input" value="85%" readonly></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
