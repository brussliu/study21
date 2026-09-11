<template>
  <div class="prototype-screen prototype-screen--ai">
    <div class="tabs" id="aiTabs" role="tablist">
              <button class="tabs__tab is-active" data-tab="gen" aria-selected="true">AI生成内容</button>
              <button class="tabs__tab" data-tab="chat" aria-selected="false">AIチャット</button>
            </div>
    
            <!-- AI生成内容 -->
            <section class="tabs__panel is-active" data-panel="gen">
              <div class="search-panel">
                <div class="search-panel__head">
                  <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                  <div class="search-panel__actions">
                    <button class="btn btn--primary" data-open-dialog="genDlg"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>内容生成</button>
                    <button class="btn btn--primary" id="aiSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                    <button class="btn btn--secondary" id="aiReset">リセット</button>
                  </div>
                </div>
                <div class="filters"><div class="filters__row">
                  <span class="filter-item"><span class="filter-item__label">類型：</span><select class="select" id="aiType"><option>すべて</option><option>読解</option><option>文法</option><option>作文</option></select></span>
                  <span class="filter-item"><span class="filter-item__label">難易度：</span><select class="select" id="aiLevel"><option>すべて</option><option>2級</option><option>準2級</option><option>3級</option></select></span>
                </div></div>
              </div>
    
              <div class="table-section" style="margin-top:16px">
                <div class="table-section__head"><h3 class="table-section__title">AI生成内容一覧</h3><span class="table-section__meta" id="aiCount">全 4 件</span></div>
                <div class="table-wrap"><table class="data-table">
                  <thead><tr><th class="col-actions">操作</th><th>NO</th><th>科目</th><th>類型</th><th>AIモデル</th><th>ステータス</th><th>難易度</th><th>プロンプト概要</th><th>作成日時</th></tr></thead>
                  <tbody id="aiBody"><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="studyDlg"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>AI-0001</td><td>英語</td><td>読解</td><td>chatgpt</td><td><span class="badge badge--success">完了</span></td><td>2級</td><td class="cell-muted">読解問題を生成</td><td class="cell-muted">2026/08/13 10:00</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="studyDlg"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>AI-0002</td><td>英語</td><td>文法</td><td>deepseek</td><td><span class="badge badge--warning">処理中</span></td><td>準2級</td><td class="cell-muted">文法問題を生成</td><td class="cell-muted">2026/08/13 09:30</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="studyDlg"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>AI-0003</td><td>英語</td><td>作文</td><td>gemini</td><td><span class="badge badge--danger">エラー</span></td><td>2級</td><td class="cell-muted">英作文の添削</td><td class="cell-muted">2026/08/12 18:20</td></tr><tr><td class="row-actions"><button class="btn btn--icon btn--sm" data-open-dialog="studyDlg"><svg class="icon icon--view"><use href="/prototype-assets/icons/icons.svg#i-eye"></use></svg></button></td><td>AI-0004</td><td>英語</td><td>読解</td><td>doubao</td><td><span class="badge badge--success">完了</span></td><td>3級</td><td class="cell-muted">長文読解を生成</td><td class="cell-muted">2026/08/12 15:00</td></tr></tbody>
                </table></div>
              </div>
            </section>
    
            <!-- AIチャット -->
            <section class="tabs__panel" data-panel="chat">
              <div class="chat-layout">
                <div class="chat-side">
                  <button class="btn btn--primary btn--sm" style="margin-bottom:8px" id="newChat"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新しい会話</button>
                  <div class="chat-side__item is-active">読解問題の解説</div>
                  <div class="chat-side__item">英作文の添削</div>
                  <div class="chat-side__item">文法の質問</div>
                </div>
                <div class="chat-main">
                  <div class="row" style="gap:8px">
                    <select class="select" style="width:auto"><option>英語</option></select>
                    <select class="select" style="width:auto"><option>中学2年</option></select>
                    <select class="select" style="width:auto"><option>読解</option></select>
                    <select class="select" style="width:auto"><option>chatgpt</option><option>deepseek</option><option>doubao</option><option>gemini</option></select>
                  </div>
                  <div class="chat-log" id="chatLog">
                    <div class="chat-msg chat-msg--user">この英文の主語と動詞を教えてください。</div>
                    <div class="chat-msg chat-msg--ai">主語は "The students"、動詞は "are studying" です。…（Mock AI 回答）</div>
                  </div>
                  <div class="chat-composer">
                    <textarea class="textarea" id="chatInput" placeholder="メッセージを入力（Ctrl+Enter で送信）" rows="2"></textarea>
                    <button class="btn btn--secondary" id="chatAttach"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg><span id="chatAttachCount">0 / 5</span></button>
                    <button class="btn btn--primary" id="chatSend"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>送信</button>
                  </div>
                </div>
              </div>
            </section>
          <div class="overlay" id="genDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-video"></use></svg>AI生成内容</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="form-grid">
              <div class="field"><label class="field__label">科目</label><select class="select"><option>英語</option><option>数学</option><option>国語</option></select></div>
              <div class="field"><label class="field__label">学年</label><select class="select"><option>中学2年</option><option>中学3年</option><option>高校1年</option></select></div>
              <div class="field"><label class="field__label">分類</label><input class="input" value="読解"></div>
              <div class="field"><label class="field__label">レベル</label><select class="select"><option>2級</option><option>準2級</option><option>3級以下</option></select></div>
              <div class="field"><label class="field__label">AIモデル</label><select class="select"><option>chatgpt</option><option>deepseek</option><option>doubao</option><option>gemini</option></select></div>
              <div class="field"><label class="field__label">プロンプト概要</label><input class="input" value="読解問題を生成"></div>
            </div>
            <div class="field" style="margin-top:12px"><label class="field__label">プロンプト詳細</label><textarea class="textarea" rows="3"></textarea></div>
            <div class="dropzone" style="margin-top:12px"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>添付ファイル（最大5件）— ドラッグ&amp;ドロップ、または Ctrl+V</div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">閉じる</button><button class="btn btn--primary" id="aiGenRun"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>生成</button></div>
        </div>
      </div><div class="overlay" id="studyDlg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-book"></use></svg>AI勉強内容</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="row" style="gap:8px;margin-bottom:12px"><span class="badge badge--neutral">内容番号 AI-0001</span><span class="badge badge--primary">英語</span><span class="badge badge--outline">2級</span><span class="badge badge--info">chatgpt</span></div>
            <div class="field"><label class="field__label">プロンプト概要</label><div>読解問題を生成</div></div>
            <div class="field" style="margin-top:10px"><label class="field__label">AI回答内容</label><div style="border:1px solid var(--color-border);border-radius:8px;padding:12px;font-size:13px;line-height:1.6">ここに生成された勉強内容（読解問題・解説など）が表示されます。Mock データ。</div></div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" id="aiContinue" hidden>会話を続ける</button><button class="btn btn--primary" data-dialog-close="">閉じる</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    .chat-layout { display: flex; gap: 16px; height: 520px; }
    .chat-side { flex: 0 0 220px; border-right: 1px solid var(--color-border); display: flex; flex-direction: column; gap: 6px; overflow-y: auto; padding-right: 12px; }
    .chat-side__item { padding: 8px 10px; border-radius: 6px; font-size: 13px; color: var(--color-text-muted); cursor: pointer; }
    .chat-side__item:hover, .chat-side__item.is-active { background: var(--color-surface-hover); color: var(--color-text); }
    .chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
    .chat-log { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 12px; padding: 8px 4px; }
    .chat-msg { max-width: 80%; padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.5; }
    .chat-msg--user { align-self: flex-end; background: var(--color-primary); color: #fff; border-bottom-right-radius: 4px; }
    .chat-msg--ai { align-self: flex-start; background: var(--color-surface-alt); border-bottom-left-radius: 4px; }
    .chat-composer { display: flex; gap: 8px; margin-top: 12px; }
    .chat-composer textarea { flex: 1; }
    @media (max-width: 768px) { .chat-layout { flex-direction: column; height: auto; } .chat-side { flex-direction: row; flex-wrap: wrap; border-right: none; border-bottom: 1px solid var(--color-border); padding: 0 0 12px; } .chat-main { min-height: 380px; } }
  
</style>
