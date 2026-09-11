import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import DocumentFilesDialog from '@/views/document/DocumentFilesDialog.vue'

vi.mock('@/api/documents', () => ({
  getDocumentFiles: vi.fn().mockResolvedValue({ data: [
    { branchNo: 1, originalFileName: '英検2級_過去問_問題.jpg', extension: 'jpg', comment: null, contentUrl: '/content/1', image: true },
    { branchNo: 2, originalFileName: '英検2級_過去問_解説.pdf', extension: 'pdf', comment: null, contentUrl: '/content/2', image: false }
  ] }),
  uploadDocumentFiles: vi.fn(),
  deleteDocumentFile: vi.fn()
}))

describe('DocumentFilesDialog', () => {
  it('資料に紐づくファイル一覧を表示する', async () => {
    const wrapper = mount(DocumentFilesDialog, {
      props: { docNo: 'DOC-0001', open: true },
      attachTo: document.body
    })
    await flushPromises()

    expect(document.body.textContent).toContain('DOC-0001 のファイル')
    await vi.waitFor(() => expect(document.body.textContent).toContain('英検2級_過去問_問題.jpg'))
    expect(document.body.textContent).toContain('英検2級_過去問_解説.pdf')

    wrapper.unmount()
  })

  it('画像ファイルをクリックすると画像ビューアが開く', async () => {
    const wrapper = mount(DocumentFilesDialog, {
      props: { docNo: 'DOC-0001', open: true },
      attachTo: document.body
    })
    await flushPromises()
    await vi.waitFor(() => expect(document.querySelectorAll('.doc-files-item').length).toBe(2))

    const imageItem = Array.from(document.querySelectorAll<HTMLElement>('.doc-files-item')).find((n) => n.textContent?.includes('.jpg'))
    expect(imageItem).toBeTruthy()
    imageItem!.querySelector<HTMLButtonElement>('.doc-files-open')!.click()
    await flushPromises()

    expect(document.querySelector('.doc-viewer')).toBeTruthy()
    expect(document.querySelector('.doc-viewer')?.textContent).toContain('英検2級_過去問_問題.jpg')

    wrapper.unmount()
  })
})
