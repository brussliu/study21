<template>
  <div class="prototype-screen prototype-screen--design-system">
    <!-- ===== 颜色 ===== -->
            <section class="card" id="ds-colors">
              <div class="card__header"><h2 class="card__title">カラートークン</h2><span class="card__sub">Colors</span></div>
              <div class="card__body">
                <div class="row" style="gap:10px">
                  <div class="swatch" style="background:var(--primary-600)"><span>primary-600</span></div>
                  <div class="swatch" style="background:var(--primary-500)"><span>primary-500</span></div>
                  <div class="swatch" style="background:var(--accent-700)"><span>accent-700</span></div>
                  <div class="swatch" style="background:var(--success-600)"><span>success</span></div>
                  <div class="swatch" style="background:var(--warning-600)"><span>warning</span></div>
                  <div class="swatch" style="background:var(--danger-600)"><span>danger</span></div>
                  <div class="swatch" style="background:var(--info-600)"><span>info</span></div>
                  <div class="swatch" style="background:var(--subject-english)"><span>英語</span></div>
                  <div class="swatch" style="background:var(--subject-japanese)"><span>日本語</span></div>
                  <div class="swatch" style="background:var(--subject-math)"><span>数学</span></div>
                </div>
              </div>
            </section>
    
            <!-- ===== 排版 ===== -->
            <section class="card" id="ds-typography">
              <div class="card__header"><h2 class="card__title">タイポグラフィ</h2><span class="card__sub">Typography</span></div>
              <div class="card__body stack">
                <div style="font-size:var(--fs-2xl);font-weight:700">ページタイトル 20px / 700 — 単語情報管理</div>
                <div style="font-size:var(--fs-lg);font-weight:600">セクションタイトル 16px / 600 — 検索条件</div>
                <div style="font-size:var(--fs-md)">本文 14px / 400 — 日本語の本文は読みやすさを最優先します。</div>
                <div style="font-size:var(--fs-sm);color:var(--color-text-muted)">補足 13px — 補助説明・メタ情報。</div>
                <div style="font-size:var(--fs-xs);color:var(--color-text-subtle)">ラベル 12px — テーブルヘッダー・注釈。</div>
                <div class="mono" style="font-size:var(--fs-lg)">等幅数字 123,456 / 00:30:00</div>
              </div>
            </section>
    
            <!-- ===== 按钮 ===== -->
            <section class="card" id="ds-buttons">
              <div class="card__header"><h2 class="card__title">ボタン</h2><span class="card__sub">Buttons</span></div>
              <div class="card__body stack">
                <div class="row">
                  <button class="btn btn--primary"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                  <button class="btn btn--secondary">リセット</button>
                  <button class="btn btn--ghost">閉じる</button>
                  <button class="btn btn--danger"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg>削除</button>
                  <button class="btn btn--danger-outline">削除</button>
                  <button class="btn btn--icon" aria-label="編集"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button>
                </div>
                <div class="row">
                  <button class="btn btn--primary btn--sm">小</button>
                  <button class="btn btn--primary">中</button>
                  <button class="btn btn--primary btn--lg">大</button>
                </div>
                <div class="row">
                  <button class="btn btn--primary" disabled>無効</button>
                  <button class="btn btn--secondary" disabled>無効</button>
                  <button class="btn btn--primary"><span class="spinner spinner--sm" style="border-top-color:#fff"></span>処理中</button>
                </div>
              </div>
            </section>
    
            <!-- ===== 表单控件 ===== -->
            <section class="card" id="ds-forms">
              <div class="card__header"><h2 class="card__title">フォームコントロール</h2><span class="card__sub">Forms</span></div>
              <div class="card__body">
                <div class="form-grid">
                  <div class="field"><label class="field__label">ユーザーID</label><input class="input" placeholder="例：admin@example.com"></div>
                  <div class="field"><label class="field__label">パスワード</label><input class="input" type="password" placeholder="••••••••"></div>
                  <div class="field"><label class="field__label">教科</label>
                    <select class="select"><option>英語</option><option>数学</option><option>国語</option></select>
                  </div>
                  <div class="field"><label class="field__label">有効期限</label><input class="input" type="date"></div>
                  <div class="field"><label class="field__label">分類（From / To）</label>
                    <span class="range-input"><select class="select"><option>Unit001</option></select><span class="range-input__sep">～</span><select class="select"><option>Unit020</option></select></span>
                  </div>
                  <div class="field"><label class="field__label">コメント</label><textarea class="textarea" placeholder="コメントを入力"></textarea></div>
                  <div class="field"><label class="field__label">選択</label>
                    <label class="check"><input type="checkbox">有効</label>
                    <label class="radio"><input type="radio" name="demo-radio">通常</label>
                    <label class="radio"><input type="radio" name="demo-radio" checked>休憩</label>
                  </div>
                  <div class="field"><label class="field__label">有効（スイッチ）</label>
                    <label class="switch"><input type="checkbox" checked><span class="switch__track"></span><span class="switch__thumb"></span></label>
                  </div>
                </div>
              </div>
            </section>
    
            <!-- ===== 检索区 ===== -->
            <section class="card" id="ds-search">
              <div class="card__header"><h2 class="card__title">検索条件エリア</h2><span class="card__sub">Search panel</span></div>
              <div class="card__body">
                <div class="search-panel">
                  <div class="search-panel__head">
                    <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
                    <div class="search-panel__actions">
                      <button class="btn btn--primary"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>新規</button>
                      <button class="btn btn--primary"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                      <button class="btn btn--secondary">リセット</button>
                    </div>
                  </div>
                  <div class="filters">
                    <div class="filters__row">
                      <span class="filter-item"><span class="filter-item__label">大分類：</span><select class="select"><option>すべて</option></select></span>
                      <span class="filter-item"><span class="filter-item__label">中分類：</span><select class="select"><option>すべて</option></select></span>
                      <span class="filter-item"><span class="filter-item__label">ステータス：</span><select class="select"><option>すべて</option><option>有効</option><option>無効</option></select></span>
                      <span class="filter-item"><span class="filter-item__label">有効期限：</span><input class="input" type="date"></span>
                    </div>
                    <div class="filters__row">
                      <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="コメントで検索" style="flex:1"></span>
                    </div>
                  </div>
                </div>
              </div>
            </section>
    
            <!-- ===== Tab ===== -->
            <section class="card" id="ds-tabs">
              <div class="card__header"><h2 class="card__title">タブ</h2><span class="card__sub">Tabs</span></div>
              <div class="card__body">
                <div class="tabs" role="tablist" id="demoTabs">
                  <button class="tabs__tab is-active" role="tab" data-tab="t1" aria-selected="true">初級編</button>
                  <button class="tabs__tab" role="tab" data-tab="t2" aria-selected="false">中級編</button>
                  <button class="tabs__tab" role="tab" data-tab="t3" aria-selected="false">熟語</button>
                </div>
                <div class="tabs__panel is-active" data-panel="t1">初級編 コンテンツ</div>
                <div class="tabs__panel" data-panel="t2">中級編 コンテンツ</div>
                <div class="tabs__panel" data-panel="t3">熟語 コンテンツ</div>
    
                <div style="margin-top:20px">
                  <div class="pill-tabs">
                    <button class="pill-tabs__tab is-active">B1. 漢字＋かな</button>
                    <button class="pill-tabs__tab">B2. 読みのみ</button>
                    <button class="pill-tabs__tab">3. 確認</button>
                  </div>
                </div>
              </div>
            </section>
    
            <!-- ===== Badge ===== -->
            <section class="card" id="ds-badges">
              <div class="card__header"><h2 class="card__title">ステータスバッジ</h2><span class="card__sub">Badges（状態は色＋文字で表現）</span></div>
              <div class="card__body row">
                <span class="badge badge--neutral">未着手</span>
                <span class="badge badge--primary">処理中</span>
                <span class="badge badge--success">有効</span>
                <span class="badge badge--warning">勉強中</span>
                <span class="badge badge--danger">無効</span>
                <span class="badge badge--info">承認済</span>
                <span class="badge badge--outline">未承認</span>
                <span class="badge badge--subject-en">英語</span>
                <span class="badge badge--subject-jp">日本語</span>
                <span class="badge badge--subject-ma">数学</span>
              </div>
            </section>
    
            <!-- ===== Alert ===== -->
            <section class="card" id="ds-alerts">
              <div class="card__header"><h2 class="card__title">アラート</h2><span class="card__sub">Alerts</span></div>
              <div class="card__body stack">
                <div class="alert alert--info"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-info"></use></svg><div class="alert__body"><div class="alert__title">お知らせ</div>バッチ処理は 05/15/25/35/45/55 分に実行されます。</div></div>
                <div class="alert alert--success"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-circle"></use></svg><div class="alert__body"><div class="alert__title">保存しました</div>設定を保存しました。</div></div>
                <div class="alert alert--warning"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-alert"></use></svg><div class="alert__body"><div class="alert__title">注意</div>訂正中の誤問題は先に修正してください。</div></div>
                <div class="alert alert--danger"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-alert"></use></svg><div class="alert__body"><div class="alert__title">エラー</div>ファイルのアップロードに失敗しました。</div></div>
              </div>
            </section>
    
            <!-- ===== Toast ===== -->
            <section class="card" id="ds-toast">
              <div class="card__header"><h2 class="card__title">トースト</h2><span class="card__sub">Toasts</span></div>
              <div class="card__body row">
                <button class="btn btn--secondary" data-toast="success" data-toast-msg="保存しました。">Success</button>
                <button class="btn btn--secondary" data-toast="info" data-toast-msg="読み込み中です...">Info</button>
                <button class="btn btn--secondary" data-toast="warning" data-toast-msg="有効期限が近づいています。">Warning</button>
                <button class="btn btn--secondary" data-toast="danger" data-toast-msg="削除に失敗しました。">Danger</button>
              </div>
            </section>
    
            <!-- ===== Card ===== -->
            <section class="card" id="ds-cards">
              <div class="card__header"><h2 class="card__title">カード</h2><span class="card__sub">Cards</span></div>
              <div class="card__body">
                <div class="row">
                  <div class="stat-card grow"><div class="stat-card__icon"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-clock"></use></svg></div><div><div class="stat-card__label">累計学習時間</div><div class="stat-card__value">24時間 18分</div><div class="stat-card__sub">All Time</div></div></div>
                  <div class="stat-card grow"><div class="stat-card__icon"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-check-circle"></use></svg></div><div><div class="stat-card__label">連続学習</div><div class="stat-card__value">7日</div><div class="stat-card__sub">Streak</div></div></div>
                  <div class="stat-card grow"><div class="stat-card__icon"><svg class="icon icon--lg"><use href="/prototype-assets/icons/icons.svg#i-type"></use></svg></div><div><div class="stat-card__label">取得級</div><div class="stat-card__value">2級</div><div class="stat-card__sub">英検</div></div></div>
                </div>
              </div>
            </section>
    
            <!-- ===== Table + Pagination ===== -->
            <section class="card" id="ds-table">
              <div class="card__header"><h2 class="card__title">テーブル + ページネーション</h2><span class="card__sub">Table &amp; Pagination</span></div>
              <div class="card__body" style="padding:0">
                <div class="table-section" style="border:none;box-shadow:none">
                  <div class="table-section__head">
                    <h3 class="table-section__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-file"></use></svg>資料一覧</h3>
                    <span class="table-section__meta">全 3 件</span>
                  </div>
                  <div class="table-wrap">
                    <table class="data-table">
                      <thead><tr>
                        <th class="col-actions">操作</th>
                        <th class="is-sortable">資料番号 <span class="sort-icon">↕</span></th>
                        <th>ステータス</th>
                        <th>有効期限</th>
                        <th>大分類</th>
                        <th>コメント</th>
                      </tr></thead>
                      <tbody>
                        <tr><td class="row-actions"><button class="btn btn--icon btn--sm"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">DOC-0001</td><td><span class="badge badge--success">有効</span></td><td>2026/12/31</td><td>英語</td><td class="cell-muted">動詞の例文集</td></tr>
                        <tr><td class="row-actions"><button class="btn btn--icon btn--sm"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">DOC-0002</td><td><span class="badge badge--success">有効</span></td><td>2026/11/30</td><td>数学</td><td class="cell-muted">合同条件</td></tr>
                        <tr><td class="row-actions"><button class="btn btn--icon btn--sm"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg></button><button class="btn btn--icon btn--sm"><svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg></button></td><td class="cell-strong">DOC-0003</td><td><span class="badge badge--danger">無効</span></td><td>2026/03/01</td><td>英語</td><td class="cell-muted">英検2級 過去問</td></tr>
                      </tbody>
                    </table>
                  </div>
                  <div class="pagination">
                    <span class="pagination__info">全 3 件（1〜3 件目を表示）</span>
                    <div class="pagination__pages">
                      <button class="page-btn" disabled>‹</button>
                      <button class="page-btn is-active">1</button>
                      <button class="page-btn">2</button>
                      <span class="page-dots">…</span>
                      <button class="page-btn">9</button>
                      <button class="page-btn">›</button>
                    </div>
                    <span class="pagination__size">表示件数 <select class="select"><option>10</option><option>20</option><option>50</option></select></span>
                  </div>
                </div>
              </div>
            </section>
    
            <!-- ===== 状态 ===== -->
            <section class="card" id="ds-states">
              <div class="card__header"><h2 class="card__title">状態（Empty / Loading / Error / Success）</h2><span class="card__sub">States</span></div>
              <div class="card__body">
                <div class="form-grid--4" style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px">
                  <div class="state state--empty" style="border:1px solid var(--color-border);border-radius:var(--radius-lg)"><div class="state__icon"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-folder"></use></svg></div><div class="state__title">データがありません</div><div class="state__desc">該当する資料は見つかりませんでした。</div></div>
                  <div class="state state--loading" style="border:1px solid var(--color-border);border-radius:var(--radius-lg)"><div class="state__icon"><span class="spinner"></span></div><div class="state__title">読み込み中...</div><div class="state__desc">データを取得しています。</div></div>
                  <div class="state state--error" style="border:1px solid var(--color-border);border-radius:var(--radius-lg)"><div class="state__icon"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-alert"></use></svg></div><div class="state__title">エラーが発生しました</div><div class="state__desc">通信に失敗しました。再読み込みしてください。</div><div class="state__action"><button class="btn btn--secondary btn--sm">再読み込み</button></div></div>
                  <div class="state state--success" style="border:1px solid var(--color-border);border-radius:var(--radius-lg)"><div class="state__icon"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check-circle"></use></svg></div><div class="state__title">保存しました</div><div class="state__desc">処理が正常に完了しました。</div></div>
                </div>
              </div>
            </section>
    
            <!-- ===== Dialog ===== -->
            <section class="card" id="ds-dialogs">
              <div class="card__header"><h2 class="card__title">ダイアログ</h2><span class="card__sub">Dialogs（S / M / L / Fullscreen + 確認）</span></div>
              <div class="card__body row">
                <button class="btn btn--secondary" data-open-dialog="dlg-sm">Small</button>
                <button class="btn btn--secondary" data-open-dialog="dlg-md">Medium</button>
                <button class="btn btn--secondary" data-open-dialog="dlg-lg">Large</button>
                <button class="btn btn--secondary" data-open-dialog="dlg-full">Fullscreen</button>
                <button class="btn btn--secondary" data-open-confirm="info">確認</button>
                <button class="btn btn--danger-outline" data-open-confirm="danger">危険操作</button>
              </div>
            </section>
    
            <!-- ===== Shell（Popup / Test / Picker / Viewer） ===== -->
            <section class="card" id="ds-shells">
              <div class="card__header"><h2 class="card__title">専用シェル</h2><span class="card__sub">Popup / Test / Picker / Viewer</span></div>
              <div class="card__body stack">
                <div class="popup-shell" style="min-height:0;border:1px solid var(--color-border);border-radius:var(--radius-lg);overflow:hidden">
                  <div class="popup-shell__bar"><span class="popup-shell__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-play"></use></svg>テスト開始</span><button class="btn btn--icon btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
                  <div class="popup-shell__body"><strong>Popup Shell</strong> — テスト開始・閲覧などの独立ウィンドウ相当のコンテナ。</div>
                </div>
    
                <div class="test-shell" style="min-height:0;border:1px solid var(--color-border);border-radius:var(--radius-lg)">
                  <div class="test-header">
                    <div class="test-header__top">
                      <div><div class="test-kicker">INTERMEDIATE COURSE</div><div class="test-title">B. 中日訳英</div>
                        <div class="test-meta"><span>書籍 <strong>中級英単語 1800</strong></span><span>分類 <strong>Unit 04-06</strong></span><span>単語SEQ <strong>23</strong></span></div>
                      </div>
                      <div class="test-nav__counter">1 / 10</div>
                    </div>
                    <div class="test-progress"><div class="test-progress__bar" style="width:20%"></div></div>
                  </div>
                  <div class="test-card">
                    <div class="field" style="margin-bottom:12px"><span class="field__label">単語を英語で答えてください</span></div>
                    <div style="font-size:var(--fs-lg);margin-bottom:16px">重要な、重要な意味を持つ</div>
                    <div class="letter-input">
                      <input class="letter-input__cell" maxlength="1"><input class="letter-input__cell is-correct" value="i" readonly><input class="letter-input__cell" maxlength="1"><input class="letter-input__cell" maxlength="1"><input class="letter-input__cell" maxlength="1"><input class="letter-input__cell" maxlength="1"><input class="letter-input__cell" maxlength="1"><input class="letter-input__cell" maxlength="1">
                    </div>
                    <div class="option-list" style="margin-top:16px">
                      <div class="option is-correct"><span class="option__index">A</span>significant</div>
                      <div class="option is-wrong"><span class="option__index">B</span>important</div>
                    </div>
                  </div>
                  <div class="test-nav">
                    <button class="btn btn--secondary"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-chevron-left"></use></svg>前へ</button>
                    <span class="test-nav__counter">Enter で判定</span>
                    <button class="btn btn--primary">次へ <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-chevron-right"></use></svg></button>
                  </div>
                </div>
    
                <div class="picker-shell" style="min-height:0;height:300px;border:1px solid var(--color-border);border-radius:var(--radius-lg);overflow:hidden">
                  <div class="picker-shell__bar">
                    <div class="picker-shell__toolbar">
                      <input class="input" placeholder="ファイル名・コメント" style="width:200px">
                      <select class="select"><option>すべて</option><option>画像</option></select>
                      <button class="btn btn--primary btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
                    </div>
                    <button class="btn btn--primary btn--sm">選択確定</button>
                  </div>
                  <div class="picker-shell__body">
                    <div class="picker-grid">
                      <div class="picker-card is-selected"><div class="picker-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="picker-card__meta">file_01.png</div><span class="picker-card__check"><svg class="icon icon--sm"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg></span></div>
                      <div class="picker-card"><div class="picker-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="picker-card__meta">file_02.png</div><span class="picker-card__check"><svg class="icon icon--sm"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg></span></div>
                      <div class="picker-card"><div class="picker-card__thumb"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div><div class="picker-card__meta">file_03.png</div><span class="picker-card__check"><svg class="icon icon--sm"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg></span></div>
                    </div>
                  </div>
                </div>
    
                <div class="viewer-shell" style="height:280px;border-radius:var(--radius-lg);overflow:hidden">
                  <div class="viewer-shell__bar">
                    <span class="viewer-shell__title">画像表示 (1 / 3)</span>
                    <div class="viewer-toolbar">
                      <button class="btn btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-chevron-left"></use></svg></button>
                      <button class="btn btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-zoom-out"></use></svg></button>
                      <button class="btn btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-zoom-in"></use></svg></button>
                      <button class="btn btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-rotate"></use></svg></button>
                      <button class="btn btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-download"></use></svg></button>
                      <button class="btn btn--sm"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button>
                    </div>
                  </div>
                  <div class="viewer-shell__stage"><svg class="icon" style="width:64px;height:64px;color:#aebdcc"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg></div>
                  <div class="viewer-shell__footer">縮小 / 拡大 / ドラッグ移動</div>
                </div>
              </div>
            </section>
          <div class="overlay" id="dlg-sm" hidden data-close-on-backdrop="">
        <div class="dialog dialog--sm" role="dialog" aria-modal="true" data-size="sm">
          <div class="dialog__head"><h2 class="dialog__title">Small ダイアログ</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">小サイズのダイアログです。確認や短い入力に使用します。</div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">OK</button></div>
        </div>
      </div><div class="overlay" id="dlg-md" hidden data-close-on-backdrop="">
        <div class="dialog dialog--md" role="dialog" aria-modal="true" data-size="md">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>Medium ダイアログ</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <div class="form-grid">
              <div class="field"><label class="field__label">サイト名称</label><input class="input" placeholder="例：youtube.com"></div>
              <div class="field"><label class="field__label">区分</label><select class="select"><option>0.勉強</option><option>1.通常</option><option>2.休憩</option><option>3.ゲーム</option></select></div>
              <div class="field"><label class="field__label">判定方法</label><select class="select"><option>先頭一致</option><option>末尾一致</option><option>含める</option><option>完全一致</option></select></div>
              <div class="field"><label class="field__label">分類</label><select class="select"><option>学習</option><option>娯楽</option></select></div>
            </div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div><div class="overlay" id="dlg-lg" hidden data-close-on-backdrop="">
        <div class="dialog dialog--lg" role="dialog" aria-modal="true" data-size="lg">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-folder"></use></svg>Large ダイアログ（資料詳細）</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body">
            <p class="cell-muted" style="margin-bottom:16px">大サイズ：複数セクションを持つ編集ダイアログ（分類・ファイルアップロード等）に使用。</p>
            <div class="dropzone"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-upload"></use></svg>ファイルをここにドラッグしてください（Ctrl+V も可）</div>
          </div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close=""><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>保存</button></div>
        </div>
      </div><div class="overlay" id="dlg-full" hidden data-close-on-backdrop="">
        <div class="dialog dialog--full" role="dialog" aria-modal="true" data-size="full">
          <div class="dialog__head"><h2 class="dialog__title"><svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>Fullscreen ダイアログ</h2><button class="dialog__close" data-dialog-close="" aria-label="閉じる"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg></button></div>
          <div class="dialog__body"><p class="cell-muted">全屏サイズ：複雑なフォームや GeoGebra エディタの埋め込みに使用。Esc / 閉じるで終了。</p></div>
          <div class="dialog__foot"><button class="btn btn--secondary" data-dialog-close="">キャンセル</button><button class="btn btn--primary" data-dialog-close="">保存</button></div>
        </div>
      </div>
  </div>
</template>

<style scoped>

    /* 画廊专用演示样式（仅 design-system.html 使用） */
    .swatch {
      width: 118px; height: 62px;
      border-radius: var(--radius-md);
      display: flex; align-items: flex-end;
      padding: 8px;
      color: #fff; font-size: 11px; font-weight: 600;
      box-shadow: var(--shadow-sm);
    }
  
</style>
