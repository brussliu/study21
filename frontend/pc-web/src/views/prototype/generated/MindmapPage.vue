<template>
  <div class="prototype-screen prototype-screen--mindmap">
    <section class="card">
              <div class="card__header"><h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-grid"></use></svg>思維導図</h2><span class="card__sub">学習ノートのマインドマップ（Demo）</span></div>
              <div class="card__body">
                <div class="mm-actions">
                  <button class="btn btn--secondary btn--sm" id="mmChild"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>子ノード</button>
                  <button class="btn btn--secondary btn--sm" id="mmSibling"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>同階層ノード</button>
                  <button class="btn btn--secondary btn--sm" id="mmEdit"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>編集</button>
                  <button class="btn btn--danger-outline btn--sm" id="mmDelete"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg>削除</button>
                  <button class="btn btn--secondary btn--sm" id="mmExpand">展开</button>
                  <button class="btn btn--secondary btn--sm" id="mmCollapse">折叠</button>
                  <button class="btn btn--primary btn--sm" id="mmSave"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-download"></use></svg>JSON导出</button>
                  <button class="btn btn--ghost btn--sm" id="mmReset">重置Demo</button>
                </div>
                <div class="mindmap-wrap">
                  <div class="mindmap-canvas"><div class="mindmap-tree" id="mmTree"><ul><li><span class="mindmap-node is-root">英語学習</span><ul><li><span class="mindmap-node">単語</span><ul><li><span class="mindmap-node">初級編</span></li><li><span class="mindmap-node">中級編</span></li></ul></li><li><span class="mindmap-node">文法</span><ul><li><span class="mindmap-node">時制</span></li><li><span class="mindmap-node">構文</span></li></ul></li><li><span class="mindmap-node">読解</span><ul><li><span class="mindmap-node">長文</span></li><li><span class="mindmap-node">精読</span></li></ul></li></ul></li></ul></div></div>
                  <aside class="mindmap-side">
                    <h3 style="font-size:14px;margin-bottom:8px">ノード样式</h3>
                    <div class="field" style="margin-bottom:10px"><label class="field__label">背景色</label><input type="color" class="input" id="mmBg" value="#ffffff" style="padding:2px;height:34px"></div>
                    <div class="field" style="margin-bottom:10px"><label class="field__label">文字色</label><input type="color" class="input" id="mmFg" value="#1f2a35" style="padding:2px;height:34px"></div>
                    <div class="field" style="margin-bottom:10px"><label class="field__label">字号</label><select class="select" id="mmSize"><option>12</option><option selected>14</option><option>16</option><option>18</option><option>20</option></select></div>
                    <div class="field" style="margin-bottom:10px"><label class="check"><input type="checkbox" id="mmBold">粗体</label><label class="check"><input type="checkbox" id="mmItalic">斜体</label></div>
                    <button class="btn btn--primary btn--sm" id="mmApply">应用样式</button>
                  </aside>
                </div>
              </div>
            </section>
  </div>
</template>

<style scoped>

    .mindmap-wrap { display: flex; gap: 16px; }
    .mindmap-canvas { flex: 1; min-width: 0; overflow: auto; padding: 20px; }
    .mindmap-node { display: inline-block; padding: 10px 16px; border-radius: 10px; background: var(--color-surface); border: 1px solid var(--color-border); box-shadow: var(--shadow-sm); font-size: 14px; font-weight: 600; color: var(--color-text-strong); cursor: pointer; position: relative; }
    .mindmap-node.is-root { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }
    .mindmap-node.is-selected { outline: 2px solid var(--color-primary-strong); outline-offset: 2px; }
    .mindmap-tree ul { padding-left: 24px; position: relative; }
    .mindmap-tree li { position: relative; margin: 10px 0; }
    .mindmap-tree li::before { content: ""; position: absolute; left: -14px; top: 50%; width: 12px; height: 1px; background: var(--color-border-strong); }
    .mindmap-side { flex: 0 0 240px; border-left: 1px solid var(--color-border); padding-left: 16px; }
    .mm-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
    @media (max-width: 768px) { .mindmap-wrap { flex-direction: column; } .mindmap-side { border-left: none; border-top: 1px solid var(--color-border); padding-left: 0; padding-top: 16px; } }
  
</style>
