<template>
  <div class="prototype-screen prototype-screen--todo">
    <div class="todo-summary">
              <div class="todo-card" data-filter="未着手"><div><div class="cell-muted">未着手</div><div class="todo-card__count" id="cntTodo">1</div></div><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-circle"></use></svg></div>
              <div class="todo-card" data-filter="進行中"><div><div class="cell-muted">進行中</div><div class="todo-card__count" id="cntDoing">1</div></div><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg></div>
              <div class="todo-card" data-filter="完了"><div><div class="cell-muted">完了</div><div class="todo-card__count" id="cntDone">1</div></div><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-circle"></use></svg></div>
            </div>
    
            <section class="card">
              <div class="card__header">
                <h2 class="card__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-square"></use></svg>タスク一覧</h2>
                <div class="row" style="gap:8px">
                  <div class="pill-tabs"><button class="pill-tabs__tab is-active" data-view="list">一覧</button><button class="pill-tabs__tab" data-view="cal">カレンダー</button></div>
                  <button class="btn btn--primary btn--sm" data-open-dialog="todoDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規Task</button>
                </div>
              </div>
              <div class="card__body">
                <div class="filters" style="margin-bottom:16px">
                  <div class="filters__row">
                    <span class="filter-item"><span class="filter-item__label">キーワード：</span><input class="input" id="todoKw" placeholder="タスク検索" style="width:200px"></span>
                    <span class="filter-item"><span class="filter-item__label">状態：</span><select class="select" id="todoStatus"><option>すべて</option><option>未着手</option><option>進行中</option><option>完了</option></select></span>
                    <span class="filter-item"><span class="filter-item__label">優先度：</span><select class="select" id="todoPri"><option>すべて</option><option>高</option><option>中</option><option>低</option></select></span>
                    <span class="filter-item"><label class="check"><input type="checkbox" id="todoDoneVisible" checked>完了も表示</label></span>
                  </div>
                </div>
    
                <div id="listView">
                  <div class="table-wrap"><table class="data-table">
                    <thead><tr><th class="col-actions">操作</th><th>タイトル</th><th>優先度</th><th>状態</th><th>期限</th><th>子Task</th></tr></thead>
                    <tbody id="todoBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-edit="0"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del="0"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">英検2級 単語 100</td><td>高</td><td><span class="badge badge--warning">進行中</span></td><td class="cell-muted">2026/08/20</td><td class="cell-muted">2件</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-edit="1"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del="1"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">数学 図形 演習</td><td>中</td><td><span class="badge badge--neutral">未着手</span></td><td class="cell-muted">2026/08/22</td><td class="cell-muted">0件</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-edit="2"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm" data-del="2"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">英語 長文精読</td><td>低</td><td><span class="badge badge--success">完了</span></td><td class="cell-muted">2026/08/10</td><td class="cell-muted">1件</td></tr></tbody>
                  </table></div>
                </div>
    
                <div id="calView" hidden>
                  <div class="cal-grid" id="calGrid"></div>
                </div>
              </div>
            </section>
          <div class="overlay" id="todoDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-square"></use></svg>新規Task登録</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <h3 class="section-title" style="font-size:14px;font-weight:600;margin:0 0 12px">Step1 親Task</h3>
            <div class="form-grid">
              <div class="field"><label class="field__label">タイトル（必須）</label><input class="input" id="taskTitle" maxlength="120"></div>
              <div class="field"><label class="field__label">優先度</label><select class="select" id="taskPriority"><option>高</option><option>中</option><option>低</option></select></div>
              <div class="field"><label class="field__label">期限</label><input type="date" class="input" id="taskDue"></div>
              <div class="field"><label class="field__label">メモ</label><input class="input" id="taskMemo" maxlength="500"></div>
            </div>
            <h3 class="section-title" style="font-size:14px;font-weight:600;margin:16px 0 12px">Step2 子Task</h3>
            <div class="child-row" style="color:var(--color-text-muted);font-size:12px"><span>No.</span><span>子Taskタイトル</span><span>状態</span></div>
            <div id="childList"></div>
            <div class="row" style="gap:8px;margin-top:8px">
              <button class="btn btn--secondary btn--sm" id="childAdd"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>行追加</button>
              <button class="btn btn--ghost btn--sm" id="childClear">クリア</button>
            </div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" id="todoSubmit"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>親Taskと子Taskを登録</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .todo-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 16px; }
    .todo-card { border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 14px 16px; display: flex; align-items: center; justify-content: space-between; cursor: pointer; }
    .todo-card__count { font-size: 24px; font-weight: 700; }
    .task-row { display: flex; align-items: center; gap: 10px; padding: 10px 4px; border-bottom: 1px solid var(--color-border); }
    .task-row:last-child { border-bottom: none; }
    .child-row { display: grid; grid-template-columns: 40px 1fr 1fr; gap: 8px; align-items: center; margin-bottom: 8px; }
    .cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }
    @media (max-width: 640px) { .todo-summary { grid-template-columns: 1fr; } }
  
</style>
