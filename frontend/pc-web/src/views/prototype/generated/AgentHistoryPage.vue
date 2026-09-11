<template>
  <div class="prototype-screen prototype-screen--agent-history">
    <div class="card">
              <div class="card__header">
                <h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-user"></use></svg>エージェント状態</h2>
                <div class="row" style="gap:8px">
                  <select class="select" style="width:auto"><option>端末を選択してください</option><option>PC-教室1</option><option>PC-教室2</option></select>
                  <button class="btn btn--primary btn--sm" id="update">更新</button>
                </div>
              </div>
              <div class="card__body">
                <div class="row" style="gap:16px;flex-wrap:wrap;margin-bottom:16px">
                  <span>状態：<strong id="agState">オンライン</strong></span>
                  <span>モード：<strong id="agMode">B.勉強モード</strong></span>
                  <span>バージョン：<strong>v2.3.1</strong></span>
                  <span>前面ウィンドウ：<strong>word.exe</strong></span>
                  <span>最終心跳：<strong>2026/08/13 10:00:05</strong></span>
                </div>
                <div class="table-section" style="box-shadow:none;border:none">
                  <div class="table-section__head"><h3 class="table-section__title" style="font-size:14px">プロセス一覧</h3></div>
                  <div class="table-wrap"><table class="data-table">
                    <thead><tr><th class="col-actions">操作</th><th>No.</th><th>プロセス名</th><th>PID</th><th>ユーザー</th><th>起動時刻</th><th>CPU(秒)</th><th>区分</th><th>備考</th><th>パス</th></tr></thead>
                    <tbody id="agBody"><tr><td class="row-actions"><button class="btn btn--secondary btn--sm" data-open-dialog="appDlg">登録</button></td><td>1</td><td class="cell-strong">word.exe</td><td>4120</td><td>student</td><td class="cell-muted">09:05</td><td>12.4</td><td>4.勉強</td><td class="cell-muted">文書作成</td><td class="cell-muted" style="max-width:200px;overflow:hidden;text-overflow:ellipsis">C:\Program Files\...\word.exe</td></tr><tr><td class="row-actions"><button class="btn btn--secondary btn--sm" data-open-dialog="appDlg">登録</button></td><td>2</td><td class="cell-strong">chrome.exe</td><td>3108</td><td>student</td><td class="cell-muted">09:10</td><td>45.2</td><td>1.通常</td><td class="cell-muted">ブラウザ</td><td class="cell-muted" style="max-width:200px;overflow:hidden;text-overflow:ellipsis">C:\...\chrome.exe</td></tr><tr><td class="row-actions"><button class="btn btn--secondary btn--sm" data-open-dialog="appDlg">登録</button></td><td>3</td><td class="cell-strong">steam.exe</td><td>2214</td><td>student</td><td class="cell-muted">09:40</td><td>3.1</td><td>3.ゲーム</td><td class="cell-muted">ゲーム</td><td class="cell-muted" style="max-width:200px;overflow:hidden;text-overflow:ellipsis">C:\...\steam.exe</td></tr></tbody>
                  </table></div>
                </div>
              </div>
            </div>
          <div class="overlay" id="appDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">アプリ制御登録</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="form-grid">
            <div class="field"><label class="field__label">アプリ名称（読み取り専用）</label><input class="input" value="steam.exe" readonly></div>
            <div class="field"><label class="field__label">区分</label><select class="select"><option>1.通常</option><option>2.休憩</option><option>3.ゲーム</option><option>4.勉強</option></select></div>
            <div class="field"><label class="field__label">判定方法</label><select class="select"><option>完全一致</option><option>部分一致</option><option>正規表現</option></select></div>
            <div class="field"><label class="field__label">備考</label><input class="input"></div>
          </div></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

</style>
