<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { ArrowLeftRight, ChevronLeft, ChevronRight, Maximize2, Minimize2, RefreshCw, Scan, ZoomIn, ZoomOut } from 'lucide-vue-next'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl
defineOptions({ inheritAttrs: false })

const props = defineProps<{ src: string }>()
const emit = defineEmits<{ error: [message: string]; loaded: [pageCount: number] }>()

const canvasElements = ref<HTMLCanvasElement[]>([])
const pagesElement = ref<HTMLElement>()
const viewerElement = ref<HTMLElement>()
const viewportSize = ref({ width: 0, height: 0 })
const pageCount = ref(0)
const currentPage = ref(1)
const scale = ref(1)
const fitMode = ref<'page' | 'width'>('page')
const expanded = ref(false)
const pageSizes = ref<Array<{ width: number; height: number }>>([])
const loading = ref(false)
const error = ref('')
let loadingTask: ReturnType<typeof pdfjsLib.getDocument> | undefined
let documentProxy: pdfjsLib.PDFDocumentProxy | undefined
let renderToken = 0
let resizeObserver: ResizeObserver | undefined
let previousFocus: HTMLElement | undefined
let previousBodyOverflow = ''
let bodyScrollLocked = false
let layoutRequest = 0
let alignedPage = 0
let layoutPending = false
const renderResolution = 2

const firstPage = computed(() => pageSizes.value[0] || { width: 794, height: 1123 })
const fitSize = computed(() => pageSizes.value.reduce((size, page) => ({ width: Math.max(size.width, page.width), height: Math.max(size.height, page.height) }), pageSizes.value.length ? { width: 0, height: 0 } : firstPage.value))
const displayScale = computed(() => {
  const { width, height } = viewportSize.value
  if (!width || !height) return 0.5
  if (fitMode.value === 'width') return Math.max(0.1, (width - 36) / fitSize.value.width) * scale.value
  return Math.max(0.1, Math.min(
    (width - 36) / fitSize.value.width,
    (height - 36) / fitSize.value.height,
  )) * scale.value
})

function pageStyle(index: number) {
  const size = pageSizes.value[index] || firstPage.value
  return { width: `${Math.max(1, size.width * displayScale.value)}px`, height: `${Math.max(1, size.height * displayScale.value)}px` }
}

function clearViewer() {
  renderToken += 1
  layoutRequest += 1
  layoutPending = false
  void loadingTask?.destroy().catch(() => undefined)
  loadingTask = undefined
  documentProxy = undefined
  canvasElements.value = []
  pageCount.value = 0
  currentPage.value = 1
  pageSizes.value = []
}

async function loadPdf() {
  clearViewer()
  fitMode.value = 'page'
  scale.value = 1
  error.value = ''
  if (!props.src) return
  const token = renderToken
  loading.value = true
  try {
    const task = pdfjsLib.getDocument({ url: props.src, isEvalSupported: false })
    loadingTask = task
    const pdf = await task.promise
    if (token !== renderToken) return
    documentProxy = pdf
    pageCount.value = pdf.numPages
    canvasElements.value = []
    pageSizes.value = []
    await nextTick()
    for (let index = 0; index < pageCount.value; index += 1) {
      if (token !== renderToken || !documentProxy) return
      const page = await documentProxy.getPage(index + 1)
      if (token !== renderToken) return
      const unscaled = page.getViewport({ scale: 1 })
      pageSizes.value[index] = { width: unscaled.width, height: unscaled.height }
      const viewport = page.getViewport({ scale: renderResolution * Math.max(1, window.devicePixelRatio || 1) })
      const canvas = canvasElements.value[index]
      if (!canvas) throw new Error('预览画布初始化失败')
      canvas.width = Math.ceil(viewport.width)
      canvas.height = Math.ceil(viewport.height)
      canvas.className = 'resume-pdf-page'
      const context = canvas.getContext('2d', { alpha: false })
      if (!context) throw new Error('浏览器不支持 PDF 预览画布')
      await page.render({ canvasContext: context, viewport }).promise
    }
    currentPage.value = 1
    emit('loaded', pageCount.value)
  } catch (caught) {
    if (token !== renderToken) return
    const message = caught instanceof Error ? caught.message : 'PDF 预览失败'
    error.value = '预览失败，请重试或下载 PDF 文件'
    emit('error', message)
  } finally {
    if (token === renderToken) loading.value = false
  }
}

function retry() { loadPdf() }
function zoom(delta: number) {
  scale.value = Math.min(2.4, Math.max(0.65, Number((scale.value + delta).toFixed(2))))
}
async function fitPage() {
  const page = currentPage.value
  layoutPending = true
  alignedPage = page
  fitMode.value = 'page'; scale.value = 1
  await alignAfterLayout(page)
}
async function fitWidth() {
  const page = currentPage.value
  layoutPending = true
  alignedPage = page
  fitMode.value = 'width'; scale.value = 1
  await alignAfterLayout(page)
}
function goToPage(page: number, smooth = true) {
  currentPage.value = Math.min(pageCount.value, Math.max(1, page))
  const canvas = canvasElements.value[currentPage.value - 1]
  const container = pagesElement.value
  if (!canvas || !container) return
  const top = canvas.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop - 18
  container.scrollTo({ top, left: 0, behavior: smooth ? 'smooth' : 'instant' })
}
function syncCurrentPage() {
  if (layoutPending) return
  const container = pagesElement.value
  if (!container || !canvasElements.value.length) return
  const bounds = container.getBoundingClientRect()
  let closest = 0
  let maximumVisibleHeight = -1
  canvasElements.value.forEach((canvas, index) => {
    const page = canvas.getBoundingClientRect()
    const visibleHeight = Math.max(0, Math.min(page.bottom, bounds.bottom) - Math.max(page.top, bounds.top))
    if (visibleHeight > maximumVisibleHeight) { maximumVisibleHeight = visibleHeight; closest = index }
  })
  currentPage.value = closest + 1
}
function toggleExpanded() {
  alignedPage = currentPage.value
  layoutPending = true
  expanded.value = !expanded.value
}
function releaseBodyScroll() {
  if (!bodyScrollLocked) return
  if (document.body.style.overflow === 'hidden') document.body.style.overflow = previousBodyOverflow
  bodyScrollLocked = false
}
function measureViewport() {
  const container = pagesElement.value
  if (!container?.clientWidth || !container.clientHeight) return false
  const width = container.clientWidth
  const height = container.clientHeight
  if (viewportSize.value.width === width && viewportSize.value.height === height) return false
  viewportSize.value = { width, height }
  return true
}
async function alignAfterLayout(page: number) {
  const request = ++layoutRequest
  layoutPending = true
  alignedPage = page
  await nextTick()
  await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
  if (request !== layoutRequest) return
  measureViewport()
  await nextTick()
  await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
  if (request !== layoutRequest) return
  measureViewport()
  await nextTick()
  goToPage(page, false)
  await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
  if (request !== layoutRequest) return
  goToPage(page, false)
  layoutPending = false
  syncCurrentPage()
}
function expandedKeys(event: KeyboardEvent) {
  if (!expanded.value) return
  if (event.key === 'Escape') { event.preventDefault(); toggleExpanded(); return }
  if (event.key !== 'Tab') return
  const controls = Array.from(viewerElement.value?.querySelectorAll<HTMLElement>('button:not(:disabled), [tabindex="0"]') || [])
  const first = controls[0]
  const last = controls.at(-1)
  if (!first || !last) return
  if (event.shiftKey && (document.activeElement === first || document.activeElement === viewerElement.value)) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && (document.activeElement === last || document.activeElement === viewerElement.value)) { event.preventDefault(); first.focus() }
}

onMounted(() => {
  resizeObserver = new ResizeObserver(() => {
    const page = layoutPending ? alignedPage : currentPage.value
    if (measureViewport() && !loading.value) void alignAfterLayout(page)
  })
  if (pagesElement.value) resizeObserver.observe(pagesElement.value)
  document.addEventListener('keydown', expandedKeys)
})

watch(pagesElement, (element, old) => {
  if (old) resizeObserver?.unobserve(old)
  if (element) resizeObserver?.observe(element)
  measureViewport()
}, { flush: 'post' })
watch(expanded, async (value, _previous, onCleanup) => {
  let canceled = false
  onCleanup(() => { canceled = true })
  const page = layoutPending ? alignedPage : currentPage.value
  if (value) {
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    bodyScrollLocked = true
  } else {
    releaseBodyScroll()
  }
  await alignAfterLayout(page)
  if (canceled) return
  if (value) viewerElement.value?.focus({ preventScroll: true })
  else previousFocus?.focus({ preventScroll: true })
}, { flush: 'sync' })

watch(() => props.src, loadPdf, { immediate: true })
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  document.removeEventListener('keydown', expandedKeys)
  releaseBodyScroll()
  if (expanded.value) previousFocus?.focus({ preventScroll: true })
  clearViewer()
})
</script>

<template>
  <div class="resume-pdf-slot">
  <Teleport to="body" :disabled="!expanded">
  <div :class="{ 'resume-pdf-overlay': expanded }" @click.self="expanded && toggleExpanded()">
  <section v-bind="$attrs" ref="viewerElement" :class="['resume-pdf-preview', { expanded }]" data-testid="resume-pdf-preview" :data-current-page="currentPage" aria-label="简历 PDF 预览" :role="expanded ? 'dialog' : undefined" :aria-modal="expanded ? true : undefined" tabindex="-1">
    <div class="resume-pdf-toolbar" data-testid="resume-pdf-toolbar">
      <span class="preview-mode-label">{{ expanded ? '展开预览' : '预览' }}</span>
      <button v-if="pageCount > 1" type="button" class="preview-tool" :disabled="currentPage <= 1" aria-label="上一页" title="上一页" @click="goToPage(currentPage - 1)"><ChevronLeft :size="16" /></button>
      <span data-testid="resume-pdf-page-count">{{ currentPage }} / {{ pageCount }}</span>
      <button v-if="pageCount > 1" type="button" class="preview-tool" :disabled="currentPage >= pageCount" aria-label="下一页" title="下一页" @click="goToPage(currentPage + 1)"><ChevronRight :size="16" /></button>
      <button type="button" class="preview-tool" :disabled="!pageCount || scale <= .65" aria-label="缩小" title="缩小" @click="zoom(-0.1)"><ZoomOut :size="16" /></button>
      <button type="button" class="preview-tool" :disabled="!pageCount || scale >= 2.4" aria-label="放大" title="放大" @click="zoom(0.1)"><ZoomIn :size="16" /></button>
      <button type="button" class="preview-tool" data-testid="resume-pdf-fit-page" :disabled="!pageCount" aria-label="适应整页" title="适应整页" :class="{ selected: fitMode === 'page' && scale === 1 }" @click="fitPage"><Scan :size="16" /></button>
      <button type="button" class="preview-tool" data-testid="resume-pdf-fit-width" :disabled="!pageCount" aria-label="适应宽度" title="适应宽度" :class="{ selected: fitMode === 'width' && scale === 1 }" @click="fitWidth"><ArrowLeftRight :size="16" /></button>
      <button type="button" class="preview-tool" :data-testid="expanded ? 'resume-pdf-close' : 'resume-pdf-expand'" :aria-label="expanded ? '收起预览' : '放大查看'" :title="expanded ? '收起预览' : '放大查看'" @click="toggleExpanded"><Minimize2 v-if="expanded" :size="16" /><Maximize2 v-else :size="16" /></button>
    </div>
    <div v-if="loading" class="preview-state">正在生成预览…</div>
    <div v-else-if="error" class="preview-state preview-error">
      <span>{{ error }}</span>
      <button type="button" class="preview-retry" @click="retry"><RefreshCw :size="14" />重试</button>
    </div>
    <div v-if="pageCount" ref="pagesElement" data-testid="resume-pdf-pages" v-show="!loading && !error" class="resume-pdf-pages" tabindex="0" aria-label="简历页面" @scroll.passive="syncCurrentPage">
      <div class="resume-pdf-page-stack">
      <canvas v-for="index in pageCount" :key="index" :style="pageStyle(index - 1)" :data-resume-pdf-page="index" :ref="(element) => { if (element) canvasElements[index - 1] = element as HTMLCanvasElement }" />
      </div>
    </div>
    <div v-if="!loading && !error && !pageCount" class="preview-state">生成 PDF 后将在这里显示</div>
  </section>
  </div>
  </Teleport>
  </div>
</template>

<style scoped>
.resume-pdf-slot { min-width: 0; height: clamp(360px, 65dvh, 740px); }
.resume-pdf-preview { display: flex; width: 100%; height: clamp(360px, 65dvh, 740px); min-width: 0; min-height: 0; flex-direction: column; border: 1px solid #dbe3ec; background: #f3f5f8; box-sizing: border-box; }
.resume-pdf-toolbar { display: flex; flex: none; align-items: center; justify-content: center; flex-wrap: wrap; gap: 6px; padding: 8px; border-bottom: 1px solid #dbe3ec; background: #fff; color: #526173; font-size: 12px; }
.preview-mode-label { margin-right: auto; }
.preview-tool, .preview-retry { display: inline-flex; flex: none; align-items: center; justify-content: center; gap: 6px; min-width: 30px; height: 30px; border: 1px solid #cbd5e1; border-radius: 4px; background: #fff; color: #344256; padding: 5px; box-sizing: border-box; cursor: pointer; }
.preview-tool.selected { color: #28664f; border-color: #8ab79b; background: #edf6f0; }
.preview-tool:focus-visible { outline: 2px solid #28664f; outline-offset: 2px; }
.preview-tool:disabled { cursor: not-allowed; opacity: .45; }
.resume-pdf-pages { flex: 1 1 0; min-width: 0; min-height: 0; overflow: auto; overscroll-behavior: auto; scrollbar-gutter: stable; }
.expanded .resume-pdf-pages { overscroll-behavior: contain; }
.resume-pdf-pages:focus-visible { outline: 2px solid #28664f; outline-offset: -2px; }
.resume-pdf-page-stack { display: grid; justify-items: center; gap: 18px; width: max-content; min-width: 100%; padding: 18px; box-sizing: border-box; }
.resume-pdf-page { display: block; max-width: none; flex: none; background: #fff; box-shadow: 0 2px 8px rgb(15 23 42 / 12%); }
.preview-state { display: flex; flex: 1; min-height: 0; align-items: center; justify-content: center; gap: 12px; padding: 20px; color: #64748b; font-size: 14px; }
.preview-error { flex-direction: column; color: #b42318; }
.resume-pdf-overlay { position: fixed; inset: 0; z-index: 3000; padding: 18px; background: rgb(20 30 24 / 60%); box-sizing: border-box; }
.resume-pdf-preview.expanded { height: 100%; max-width: 1200px; margin: 0 auto; }
@media (max-width: 700px) { .preview-mode-label { display: none; } .resume-pdf-toolbar { gap: 4px; } .resume-pdf-overlay { padding: 8px; } }
</style>
