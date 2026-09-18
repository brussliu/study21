/**
 * 書籍閲覧（本文 PDF）で使う pdf.js の読み込みと型。
 *
 * 2.0 の `js/english_reading_reader.js`（`ensurePdfLoaded` / `renderPdfPage`）を移植したもの。
 * ・同梱の `/lib/pdfjs/pdf.min.js` を動的に読み込む（CDN は使わない。配備先が外部に出られなくても読めるように）
 * ・すでに `window.pdfjsLib` があれば読み込み直さない（画面を開き直しても増やさない）
 * ・worker の場所は `GlobalWorkerOptions.workerSrc` で明示する（既定の相対解決に任せない）
 */

export const PDFJS_SCRIPT_URL = '/lib/pdfjs/pdf.min.js'
export const PDFJS_WORKER_URL = '/lib/pdfjs/pdf.worker.min.js'
export const PDFJS_LOAD_ERROR = 'PDF 表示ライブラリを読み込めませんでした。'

/** ページの表示サイズ（pdf.js の Viewport と同じ形）。 */
export interface PdfViewport {
  width: number
  height: number
  scale: number
}

export interface PdfRenderTask {
  promise: Promise<void>
}

export interface PdfTextContent {
  items: unknown[]
}

export interface PdfPageProxy {
  getViewport(params: { scale: number }): PdfViewport
  render(params: {
    canvasContext: CanvasRenderingContext2D
    viewport: PdfViewport
    transform?: number[] | null
  }): PdfRenderTask
  getTextContent(): Promise<PdfTextContent>
}

export interface PdfDocumentProxy {
  numPages: number
  getPage(pageNo: number): Promise<PdfPageProxy>
  destroy?(): void | Promise<void>
}

export interface PdfLoadingTask {
  promise: Promise<PdfDocumentProxy>
  destroy?(): void
}

export interface PdfTextLayerTask {
  promise?: Promise<void>
}

export interface PdfJsLib {
  GlobalWorkerOptions: { workerSrc: string }
  getDocument(params: { url: string; withCredentials?: boolean }): PdfLoadingTask
  renderTextLayer(params: {
    textContentSource: PdfTextContent
    container: HTMLElement
    viewport: PdfViewport
    textDivs: unknown[]
  }): PdfTextLayerTask | undefined
}

declare global {
  interface Window {
    pdfjsLib?: PdfJsLib
  }
}

/** 読み込み中の script（画面を開き直しても二重に読み込まない）。 */
let scriptPromise: Promise<PdfJsLib> | null = null

/** pdf.js を使えるようにする（既にあればそのまま返す）。 */
export function ensurePdfJs(): Promise<PdfJsLib> {
  const loaded = window.pdfjsLib
  if (loaded) return Promise.resolve(loaded)
  if (scriptPromise !== null) return scriptPromise

  scriptPromise = new Promise<PdfJsLib>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = PDFJS_SCRIPT_URL
    script.async = true
    script.dataset.pdfjs = 'true'
    script.addEventListener('load', () => {
      const lib = window.pdfjsLib
      if (lib) {
        resolve(lib)
      } else {
        scriptPromise = null
        reject(new Error(PDFJS_LOAD_ERROR))
      }
    })
    script.addEventListener('error', () => {
      scriptPromise = null
      reject(new Error(PDFJS_LOAD_ERROR))
    })
    document.head.appendChild(script)
  })
  return scriptPromise
}

/**
 * pdf.js が「PDF が無い（404）」と言っているか。
 * pdf.js は 404 のとき `MissingPDFException` を投げる。プロキシ越しなどで
 * メッセージだけが残る場合もあるため、本文の 404 も見る。
 */
export function isMissingPdfError(cause: unknown): boolean {
  if (cause === null || typeof cause !== 'object') return false
  const error = cause as { name?: string; status?: number; message?: string }
  if (error.name === 'MissingPDFException') return true
  if (error.status === 404) return true
  const message = String(error.message ?? '')
  return /404/.test(message) || /missing pdf/i.test(message)
}
