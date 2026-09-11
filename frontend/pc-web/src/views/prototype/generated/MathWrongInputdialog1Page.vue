<template>
  <div class="prototype-screen prototype-screen--math-wrong-inputdialog1">
    <div class="alert alert--warning"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-alert"></use></svg><div class="alert__body"><div class="alert__title">レガシー・オーファンフラグメント</div>math_wrong_inputdialog1.jsp はどのページからも include されていない孤立片段です。現行の復習 UI は <a href="#" data-route-screen="math-wrong-review">math_wrong_review.html</a> に置き換えられています。</div></div>
            <div class="state state--empty"><div class="state__icon"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg></div><div class="state__title">誤問題の復習（旧ダイアログ）</div><div class="state__desc">解き直し / 知識点復習 Tab + 画像編集</div><div class="state__action"><button class="btn btn--primary" data-open-dialog="reviewDlg">ダイアログを開く</button></div></div>
          <div class="overlay" id="reviewDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>誤問題の復習</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="tabs"><button class="tabs__tab is-active" data-tab="redo">解き直し</button><button class="tabs__tab" data-tab="kp">知識点復習</button></div>
            <div class="tabs__panel is-active" data-panel="redo"><div class="field" style="margin-bottom:10px"><label class="field__label">解き直しメモ</label><textarea class="textarea" rows="3"></textarea></div><div class="dropzone"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>解答写真のアップロード（0/5枚）</div></div>
            <div class="tabs__panel" data-panel="kp"><p class="cell-muted">正答・解説、知識点タグ、関連する概念・単元の整理。</p></div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">閉じる</button><button class="btn btn--secondary" data-open-dialog="imgDlg">画像編集</button><button class="btn btn--secondary" data-route-screen="math-wrong-input">修正へ</button><button class="btn btn--primary" data-dialog-close="">復習を記録</button></div>
        </div>
      </div><div class="overlay" id="imgDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">画像編集</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="dropzone" style="height:160px">画像編集（Mock）</div></div>
          <div class="dialog__foot"><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
