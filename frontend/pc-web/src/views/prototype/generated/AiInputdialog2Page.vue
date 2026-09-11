<template>
  <div class="prototype-screen prototype-screen--ai-inputdialog2">
    <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body"><div class="alert__title">ダイアログ Host</div>このページは ai_inputdialog2.jsp（AI勉強内容ビューア）のマッピングです。<a href="#" data-route-screen="ai">ai.html</a> 内にも同ダイアログが埋め込まれています。</div></div>
            <div class="state state--empty"><div class="state__icon"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book"></use></svg></div><div class="state__title">AI勉強内容ダイアログ</div><div class="state__desc">元情報 / プロンプト / 添付資料 / AI回答内容（読み取り専用）</div><div class="state__action"><button class="btn btn--primary" data-open-dialog="studyDlg"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg>ダイアログを開く</button></div></div>
          <div class="overlay" id="studyDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book"></use></svg>AI勉強内容</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="row" style="gap:8px;margin-bottom:12px"><span class="badge badge--neutral">内容番号 AI-0001</span><span class="badge badge--primary">英語</span><span class="badge badge--outline">2級</span><span class="badge badge--info">chatgpt</span></div>
            <div class="field"><label class="field__label">プロンプト概要</label><div>読解問題を生成</div></div>
            <div class="field" style="margin-top:10px"><label class="field__label">添付資料</label><div class="cell-muted">なし</div></div>
            <div class="field" style="margin-top:10px"><label class="field__label">AI回答内容</label><div style="border:1px solid var(--color-border);border-radius:8px;padding:12px;font-size:13px;line-height:1.6">ここに生成された勉強内容が表示されます。（Mock）</div></div>
          </div>
          <div class="dialog__foot"><button class="btn btn--primary" data-dialog-close="">閉じる</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
