import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import ResumePdfPreview from './ResumePdfPreview.vue'

vi.mock('pdfjs-dist', () => ({
  GlobalWorkerOptions: { workerSrc: '' },
  getDocument: vi.fn(() => ({
    promise: Promise.reject(new Error('PDF unavailable')),
    destroy: vi.fn(() => Promise.resolve()),
  })),
}))

let app: App | undefined
let host: HTMLElement

beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class {
    observe() {}
    unobserve() {}
    disconnect() {}
  })
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { callback(0); return 1 })
  host = document.createElement('div')
  document.body.append(host)
})

afterEach(() => {
  app?.unmount()
  app = undefined
  host.remove()
  document.body.style.overflow = ''
  vi.unstubAllGlobals()
})

function mountPreview(src = '') {
  app = createApp({ render: () => h(ResumePdfPreview, { src }) })
  app.mount(host)
}

function click(testId: string) {
  const button = document.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)
  expect(button).not.toBeNull()
  button!.click()
}

describe('resume preview scroll lock lifecycle', () => {
  it('locks only while expanded and restores the original overflow on close', async () => {
    document.body.style.overflow = 'auto'
    mountPreview()
    expect(document.body.style.overflow).toBe('auto')
    click('resume-pdf-expand')
    expect(document.body.style.overflow).toBe('hidden')
    await nextTick()
    click('resume-pdf-close')
    expect(document.body.style.overflow).toBe('auto')
  })

  it('releases the lock if the preview is unmounted immediately after expansion', () => {
    mountPreview()
    click('resume-pdf-expand')
    expect(document.body.style.overflow).toBe('hidden')
    app!.unmount()
    app = undefined
    expect(document.body.style.overflow).toBe('')
  })

  it('releases the lock when closing and navigating away in the same update', async () => {
    mountPreview()
    click('resume-pdf-expand')
    await nextTick()
    click('resume-pdf-close')
    app!.unmount()
    app = undefined
    expect(document.body.style.overflow).toBe('')
  })

  it('preserves overflow changed by another dialog while the preview was open', async () => {
    mountPreview()
    click('resume-pdf-expand')
    await nextTick()
    document.body.style.overflow = 'scroll'
    click('resume-pdf-close')
    expect(document.body.style.overflow).toBe('scroll')
  })

  it('preserves a scroll lock that existed before the preview opened', async () => {
    document.body.style.overflow = 'hidden'
    mountPreview()
    click('resume-pdf-expand')
    await nextTick()
    click('resume-pdf-close')
    expect(document.body.style.overflow).toBe('hidden')
  })

  it('releases the lock when Escape closes an expanded preview', async () => {
    mountPreview()
    click('resume-pdf-expand')
    await nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(document.body.style.overflow).toBe('')
    await nextTick()
    expect(document.querySelector('[data-testid="resume-pdf-close"]')).toBeNull()
  })

  it('can close a failed PDF and return to a scrollable page', async () => {
    mountPreview('broken.pdf')
    click('resume-pdf-expand')
    await nextTick()
    await nextTick()
    expect(document.body.style.overflow).toBe('hidden')
    click('resume-pdf-close')
    expect(document.body.style.overflow).toBe('')
  })
})
