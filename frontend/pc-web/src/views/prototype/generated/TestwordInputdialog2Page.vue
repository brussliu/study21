<template>
  <div class="prototype-screen prototype-screen--testword-inputdialog2">
    <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body">testword_inputdialog2.jsp（単語リスト）のマッピング。<a href="#" data-route-screen="testword">testword.html</a> 内にも同ダイアログが埋め込まれています。</div></div>
            <div class="state state--empty"><div class="state__icon"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></div><div class="state__title">単語リストダイアログ</div><div class="state__desc">全部 / 誤りのみ の切替と単語結果表</div><div class="state__action"><button class="btn btn--primary" data-open-dialog="wordlistDlg">ダイアログを開く</button></div></div>
          <div class="overlay" id="wordlistDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">単語リスト</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="row" style="gap:12px;margin-bottom:12px"><label class="radio"><input type="radio" checked>全部</label><label class="radio"><input type="radio">誤りのみ</label></div>
          <table class="data-table"><thead><tr><th>枝番</th><th>単語</th><th>日本語</th></tr></thead><tbody><tr><td>1</td><td class="cell-strong">abandon</td><td>捨てる</td></tr><tr><td>2</td><td style="color:var(--color-danger)">achieve ✕</td><td>達成する</td></tr></tbody></table></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
