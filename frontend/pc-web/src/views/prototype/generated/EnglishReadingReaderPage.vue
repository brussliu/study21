<template>
  <div class="prototype-screen prototype-screen--english-reading-reader">
    <div class="reader-layout">
              <!-- 左：页码 -->
              <div class="reader-page-list" id="pageList"><div class="reader-page-item is-active" data-page="1">Page 1</div><div class="reader-page-item" data-page="2">Page 2</div><div class="reader-page-item" data-page="3">Page 3</div><div class="reader-page-item" data-page="4">Page 4</div><div class="reader-page-item" data-page="5">Page 5</div><div class="reader-page-item" data-page="6">Page 6</div><div class="reader-page-item" data-page="7">Page 7</div><div class="reader-page-item" data-page="8">Page 8</div><div class="reader-page-item" data-page="9">Page 9</div><div class="reader-page-item" data-page="10">Page 10</div><div class="reader-page-item" data-page="11">Page 11</div><div class="reader-page-item" data-page="12">Page 12</div><div class="reader-page-item" data-page="13">Page 13</div><div class="reader-page-item" data-page="14">Page 14</div><div class="reader-page-item" data-page="15">Page 15</div><div class="reader-page-item" data-page="16">Page 16</div><div class="reader-page-item" data-page="17">Page 17</div><div class="reader-page-item" data-page="18">Page 18</div><div class="reader-page-item" data-page="19">Page 19</div><div class="reader-page-item" data-page="20">Page 20</div></div>
              <!-- 中：本文 -->
              <div>
                <div class="mark-tools" id="markTools">
                  <button class="mark-tool" data-tool="語彙">語彙</button>
                  <button class="mark-tool" data-tool="ハイライト">ハイライト</button>
                  <button class="mark-tool" data-tool="下線">下線</button>
                  <button class="mark-tool" data-tool="メモ">メモ</button>
                  <button class="mark-tool" data-tool="手書き">手書き</button>
                  <input type="color" value="#fbeecb" title="色" style="width:30px;height:30px;border:none;background:none">
                </div>
                <div class="reader-canvas" id="readerCanvas">
                  <div class="reader-text" id="readerText">
                    Once upon a time there was a <mark>little prince</mark> who lived on a tiny planet. He spent his days caring for a single rose and watching the sunsets.<br><br>
                    "What is essential is invisible to the eye," the fox said. The prince remembered these words as he traveled from planet to planet, meeting strange grown-ups.
                  </div>
                </div>
                <div class="test-nav" style="margin-top:12px">
                  <button class="btn btn--secondary" id="prevPage"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-chevron-left"></use></svg>前のページ</button>
                  <span class="test-nav__counter" id="pageCounter">1 / 20</span>
                  <button class="btn btn--primary" id="nextPage">次のページ <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-chevron-right"></use></svg></button>
                </div>
              </div>
              <!-- 右：信息 -->
              <div class="reader-side">
                <div class="card"><div class="card__header"><h3 class="card__title" style="font-size:14px">書籍情報</h3></div>
                  <div class="card__body stack" style="gap:6px"><strong>The Little Prince</strong><span class="cell-muted">A. de Saint-Exupéry · Elementary</span></div>
                </div>
                <div class="card"><div class="card__header"><h3 class="card__title" style="font-size:14px">現在ページの標記</h3><span class="card__sub" id="markCount">0件</span></div>
                  <div class="card__body stack" id="currentMarks" style="gap:6px"><div class="cell-muted">標記なし</div></div>
                </div>
                <div class="card"><div class="card__header"><h3 class="card__title" style="font-size:14px">語彙ピックアップ</h3></div>
                  <div class="card__body stack" id="vocabPool" style="gap:6px"><div class="cell-muted">語彙なし</div></div>
                </div>
              </div>
            </div>
          <div class="overlay" id="markDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">標記一覧</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><div class="stack" id="markListAll" style="gap:6px"><div class="cell-muted">標記なし</div></div></div>
        </div>
      </div><div class="overlay" id="wordDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title">新規単語登録</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body">単語情報管理（word_inputdialog1）の占位です。次ステージで統合予定。</div></div>
            <div class="form-grid" style="margin-top:12px">
              <div class="field"><label class="field__label">英語単語</label><input class="input" value="prince"></div>
              <div class="field"><label class="field__label">日本語訳</label><input class="input" value="王子"></div>
              <div class="field"><label class="field__label">中国語訳</label><input class="input" value="王子"></div>
            </div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .reader-layout { display: grid; grid-template-columns: 160px 1fr 240px; gap: 16px; }
    .reader-page-list { border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 10px; overflow-y: auto; max-height: 520px; }
    .reader-page-item { padding: 8px 10px; border-radius: 6px; font-size: 13px; color: var(--color-text-muted); cursor: pointer; }
    .reader-page-item.is-active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: 600; }
    .reader-canvas { border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 24px; background: var(--color-surface); min-height: 460px; }
    .reader-text { font-size: 16px; line-height: 2; }
    .reader-text mark { background: var(--warning-100); padding: 0 2px; border-radius: 3px; }
    .reader-side { display: flex; flex-direction: column; gap: 16px; }
    .mark-tools { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }
    .mark-tool { padding: 6px 10px; border: 1px solid var(--color-border-strong); border-radius: 6px; font-size: 12px; cursor: pointer; }
    .mark-tool.is-active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-soft); }
    @media (max-width: 1024px) { .reader-layout { grid-template-columns: 1fr; } }
  
</style>
