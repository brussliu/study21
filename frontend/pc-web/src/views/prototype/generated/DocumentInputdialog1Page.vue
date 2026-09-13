<template>
  <div class="prototype-screen prototype-screen--document-inputdialog1">
    <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body">document_inputdialog1.jsp（資料詳細ダイアログ）のマッピング。<a href="#" data-route-screen="document">document.html</a> 内にも同ダイアログが埋め込まれています。</div></div>
            <div class="state state--empty"><div class="state__icon"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-folder"></use></svg></div><div class="state__title">資料詳細ダイアログ</div><div class="state__desc">ステータス/有効期限/4級分類/アップロード/コメント/ファイル grid</div><div class="state__action"><button class="btn btn--primary" data-open-dialog="docDlg">ダイアログを開く</button></div></div>
          <div class="overlay" id="docDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-folder"></use></svg>資料詳細</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">ステータス</label><select class="select"><option>1. 有効</option><option>0. 無効</option></select></div>
            <div class="field"><label class="field__label">有効期限</label><input type="text" class="input" placeholder="YYYY/MM/DD"></div>
          </div><div class="dropzone" style="margin-top:12px"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>ファイルをここにドラッグしてください</div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
