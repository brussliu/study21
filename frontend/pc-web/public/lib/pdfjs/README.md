# pdf.js（書籍閲覧の PDF 表示用）

| 項目 | 内容 |
|---|---|
| ファイル | `pdf.min.js` / `pdf.worker.min.js` |
| 版 | pdf.js **3.11.174**（`pdfjsVersion` の値） |
| ライセンス | Apache License 2.0（Mozilla Foundation。各ファイルの先頭にライセンス表示あり） |
| 出所 | 2.0 が `webapp/lib/pdfjs/` に同梱していたものと同じファイルをそのまま置いている（版・サイズ一致） |
| 使い方 | `frontend/pc-web/src/views/reading/BookReaderView.vue` が `/lib/pdfjs/pdf.min.js` を動的に読み込み、`window.pdfjsLib` で PDF を描画する |

2.0（`english_reading_reader.jsp`）と同じく **CDN ではなく同梱**にしている。配備先（NAS の
コンテナ）がインターネットに出られなくても書籍を読めるようにするため。

worker の場所は `pdfjsLib.GlobalWorkerOptions.workerSrc = '/lib/pdfjs/pdf.worker.min.js'` で
明示している（既定の相対解決に任せない）。
