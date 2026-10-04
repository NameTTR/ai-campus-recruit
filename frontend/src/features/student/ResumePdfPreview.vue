<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { ChevronLeft, ChevronRight, ZoomIn, ZoomOut, RefreshCw } from 'lucide-vue-next'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl

const props = defineProps<{ src: string }>()
const emit = defineEmits<{ error: [message: string]; loaded: [pageCount: number] }>()

const canvasElements = ref<HTMLCanvasElement[]>([])
const pageCount = ref(0)
const currentPage = ref(1)
const scale = ref(1)
const loading = ref(false)
const error = ref('')
let loadingTask: ReturnType<typeof pdfjsLib.getDocument> | undefined
let documentProxy: pdfjsLib.PDFDocumentProxy | undefined
let renderToken = 0

function clearViewer() {
  renderToken += 1
  void loadingTask?.destroy().catch(() => undefined)
  loadingTask = undefined
  documentProxy = undefined
  canvasElements.value = []
  pageCount.value = 0
  currentPage.value = 1
}

async function loadPdf() {
  clearViewer()
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
    await nextTick()
    for (let index = 0; index < pageCount.value; index += 1) {
      if (token !== renderToken || !documentProxy) return
      const page = await documentProxy.getPage(index + 1)
      if (token !== renderToken) return
      const viewport = page.getViewport({ scale: Math.max(1, window.devicePixelRatio || 1) * scale.value })
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
  scale.value = Math.min(1.8, Math.max(0.7, Number((scale.value + delta).toFixed(2))))
  if (props.src) loadPdf()
}
function goToPage(page: number) {
  currentPage.value = Math.min(pageCount.value, Math.max(1, page))
  document.querySelector(`[data-resume-pdf-page="${currentPage.value}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

watch(() => props.src, loadPdf, { immediate: true })
onBeforeUnmount(clearViewer)
</script>

<template>
  <section class="resume-pdf-preview" data-testid="resume-pdf-preview" aria-label="简历 PDF 预览">
    <div v-if="pageCount" class="resume-pdf-toolbar">
      <button v-if="pageCount > 1" type="button" class="preview-tool" :disabled="currentPage <= 1" aria-label="上一页" title="上一页" @click="goToPage(currentPage - 1)"><ChevronLeft :size="16" /></button>
      <span>{{ currentPage }} / {{ pageCount }}</span>
      <button v-if="pageCount > 1" type="button" class="preview-tool" :disabled="currentPage >= pageCount" aria-label="下一页" title="下一页" @click="goToPage(currentPage + 1)"><ChevronRight :size="16" /></button>
      <button type="button" class="preview-tool" aria-label="缩小" title="缩小" @click="zoom(-0.1)"><ZoomOut :size="16" /></button>
      <button type="button" class="preview-tool" aria-label="放大" title="放大" @click="zoom(0.1)"><ZoomIn :size="16" /></button>
    </div>
    <div v-if="loading" class="preview-state">正在生成预览…</div>
    <div v-else-if="error" class="preview-state preview-error">
      <span>{{ error }}</span>
      <button type="button" class="preview-retry" @click="retry"><RefreshCw :size="14" />重试</button>
    </div>
    <div v-if="pageCount" v-show="!loading && !error" class="resume-pdf-pages">
      <canvas v-for="index in pageCount" :key="index" :style="{ width: `${scale * 100}%` }" :data-resume-pdf-page="index" :ref="(element) => { if (element) canvasElements[index - 1] = element as HTMLCanvasElement }" />
    </div>
    <div v-if="!loading && !error && !pageCount" class="preview-state">生成 PDF 后将在这里显示</div>
  </section>
</template>

<style scoped>
.resume-pdf-preview { display: flex; min-width: 0; min-height: 520px; flex-direction: column; border: 1px solid #dbe3ec; background: #f3f5f8; }
.resume-pdf-toolbar { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 8px; border-bottom: 1px solid #dbe3ec; background: #fff; color: #526173; font-size: 12px; }
.preview-tool, .preview-retry { display: inline-flex; align-items: center; gap: 6px; border: 1px solid #cbd5e1; background: #fff; color: #344256; padding: 5px 9px; cursor: pointer; }
.preview-tool:disabled { cursor: not-allowed; opacity: .45; }
.resume-pdf-pages { flex: 1; min-width: 0; overflow: auto; padding: 18px; }
.resume-pdf-page { display: block; height: auto; margin: 0 auto 18px; background: #fff; box-shadow: 0 2px 8px rgb(15 23 42 / 12%); }
.preview-state { display: flex; min-height: 520px; align-items: center; justify-content: center; gap: 12px; color: #64748b; font-size: 14px; }
.preview-error { flex-direction: column; color: #b42318; }
@media (max-width: 700px) { .resume-pdf-preview { min-height: 420px; } .resume-pdf-pages { padding: 8px; } .preview-state { min-height: 420px; } }
</style>
