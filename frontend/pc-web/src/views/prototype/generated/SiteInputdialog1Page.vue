<template>
  <div class="prototype-screen prototype-screen--site-inputdialog1">
    <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body">site_inputdialog1.jsp（サイト新規登録ダイアログ）のマッピング。<a href="#" data-route-screen="site">site.html</a> と <a href="#" data-route-screen="history">history.html</a> 内にも埋め込まれます。</div></div>
            <div class="state state--empty"><div class="state__icon"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-globe"></use></svg></div><div class="state__title">サイト新規登録ダイアログ</div><div class="state__desc">サイト名称/URL/区分/判定方法/分類/分類詳細/備考</div><div class="state__action"><button class="btn btn--primary" data-open-dialog="siteDlg">ダイアログを開く</button></div></div>
          <div class="overlay" id="siteDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-globe"></use></svg>サイト新規登録</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">サイト名称</label><input class="input" placeholder="例：youtube.com"></div>
            <div class="field"><label class="field__label">サイトURL</label><input class="input" placeholder="https://example.com"></div>
            <div class="field"><label class="field__label">区分</label><select class="select"><option>0.勉強</option><option>1.通常</option><option>2.休憩</option><option>3.ゲーム</option></select></div>
            <div class="field"><label class="field__label">判定方法</label><select class="select"><option>先頭一致</option><option>末尾一致</option><option>含める</option><option>完全一致</option></select></div>
            <div class="field"><label class="field__label">分類</label><select class="select"><option>学習</option><option>娯楽</option><option>ショッピング</option><option>SNS</option><option>その他</option></select></div>
            <div class="field"><label class="field__label">備考</label><textarea class="textarea" rows="2"></textarea></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
