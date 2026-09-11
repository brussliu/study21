<template>
  <div class="prototype-screen prototype-screen--ai-inputdialog1">
    <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body"><div class="alert__title">ダイアログ Host</div>このページは ai_inputdialog1.jsp（AI生成内容ダイアログ）のマッピングです。<a href="#" data-route-screen="ai">ai.html</a> 内にも同ダイアログが埋め込まれています。</div></div>
            <div class="state state--empty"><div class="state__icon"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg></div><div class="state__title">AI生成内容ダイアログ</div><div class="state__desc">科目/学年/分類/レベル/AIモデル/プロンプト/添付（最大5件）＋ 画像編集（切り抜き）</div><div class="state__action"><button class="btn btn--primary" data-open-dialog="genDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>ダイアログを開く</button></div></div>
          <div class="overlay" id="genDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg>AI生成内容</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="form-grid">
              <div class="field"><label class="field__label">科目</label><select class="select"><option>英語</option></select></div>
              <div class="field"><label class="field__label">学年</label><select class="select"><option>中学2年</option></select></div>
              <div class="field"><label class="field__label">分類</label><input class="input" value="読解"></div>
              <div class="field"><label class="field__label">レベル</label><select class="select"><option>2級</option></select></div>
              <div class="field"><label class="field__label">AIモデル</label><select class="select"><option>chatgpt</option><option>deepseek</option><option>doubao</option><option>gemini</option></select></div>
              <div class="field"><label class="field__label">プロンプト概要</label><input class="input" value="読解問題を生成"></div>
            </div>
            <div class="field" style="margin-top:12px"><label class="field__label">プロンプト詳細</label><textarea class="textarea" rows="3"></textarea></div>
            <div class="dropzone" style="margin-top:12px" id="dropDemo"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>添付ファイル（最大5件）— ドラッグ&amp;ドロップ / Ctrl+V</div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">閉じる</button><button class="btn btn--primary" id="openCrop"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>画像編集</button><button class="btn btn--primary" id="runGen"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>生成</button></div>
        </div>
      </div><div class="overlay" id="cropDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">画像編集</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="dropzone" style="height:160px">切り抜き範囲（Mock）</div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">閉じる</button><button class="btn btn--primary" data-dialog-close="">切り抜きを保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
