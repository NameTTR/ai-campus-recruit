<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { ElCheckbox, ElCheckboxGroup } from 'element-plus/es/components/checkbox/index'
import { ElRadioButton, ElRadioGroup } from 'element-plus/es/components/radio/index'
import { ArrowLeft, ArrowRight, Bookmark, Check, ChevronDown, FileText, Lightbulb, RefreshCw, Search, Send, Timer, Trash2 } from 'lucide-vue-next'
import * as pdfjs from 'pdfjs-dist'
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import {
  confirmKnowledgeAction, createKnowledgePractice, deleteKnowledgeHistory, deleteKnowledgeItem, getKnowledgeAttempts,
  getKnowledgeHistory, getKnowledgeHistoryEntry, getKnowledgeItems, getKnowledgeLibrary, getKnowledgeOriginal,
  getKnowledgePractice, getKnowledgeRecommendations, getKnowledgeTopic, getKnowledgeTopics,
  previewKnowledgeAction, reviewKnowledgeItem, retryKnowledgeAttempt, saveKnowledgeAnswer,
  saveKnowledgeItem, searchKnowledge, updateKnowledgeItem,
  type KnowledgeActionPreview, type KnowledgeAttempt, type KnowledgeHistory, type KnowledgeItem,
  type KnowledgeLibraryDocument, type KnowledgePractice, type KnowledgeRecommendation, type KnowledgeTopic
} from '../../api/knowledge'
import { listLearningPlans, type JobSummary, type KnowledgeAnswerResponse, type LearningPlan, type ResumeSummary } from '../../api/client'
import {
  knowledgeContext, knowledgeDateLabel, knowledgeDirection, knowledgeDue, knowledgeErrorMessage, knowledgeExcerpt,
  knowledgeHighlightParts, knowledgeIsExplanation, knowledgeNoteStorageKey, knowledgePosition, knowledgePracticeQuestionIndex, knowledgePracticeStorageKey, knowledgeReadableFeedback, knowledgeSafeLink, knowledgeSearchExcerpt, knowledgeStateLabel
} from './knowledgeWorkspace'

pdfjs.GlobalWorkerOptions.workerSrc = pdfWorker
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: false })
const props = defineProps<{ userId?: string; targetRole?: string; resumeId?: string; jobId?: string; matchId?: string; planId?: string; interviewSessionId?: string; jobs?: JobSummary[]; resumes?: ResumeSummary[] }>()
const route = useRoute()
const router = useRouter()
const page = computed(() => String(route.meta.knowledgePage || 'search'))
const context = computed(() => knowledgeContext(route.query as Record<string, unknown>))
const chosenJobId = ref(context.value.jobId || props.jobId || '')
const chosenResumeId = ref(context.value.resumeId || props.resumeId || '')
const chosenJob = computed(() => props.jobs?.find(item => item.jobId === chosenJobId.value))
const requestContext = computed(() => ({ resumeId: chosenResumeId.value || undefined, jobId: chosenJobId.value || undefined,
  matchId: chosenJobId.value === (context.value.jobId || props.jobId || '') && chosenResumeId.value === (context.value.resumeId || props.resumeId || '') ? context.value.matchId || props.matchId : undefined, planId: context.value.planId || props.planId,
  interviewSessionId: context.value.sessionId || props.interviewSessionId, targetRole: chosenJob.value?.title || context.value.targetRole || props.targetRole }))
const form = reactive({ query: context.value.q || '', useAi: context.value.ai === '1', roleDirection: context.value.direction || knowledgeDirection(props.targetRole), skill: context.value.skill || '', difficulty: context.value.difficulty || '', contentType: context.value.contentType || '' })
const loading = ref(false)
const busy = ref('')
const error = ref('')
const notice = ref('')
const recommendations = ref<KnowledgeRecommendation[]>([])
const topics = ref<KnowledgeTopic[]>([])
const topicDirectory = ref<Record<string, KnowledgeTopic>>({})
const answer = ref<KnowledgeAnswerResponse>()
const history = ref<KnowledgeHistory[]>([])
const items = ref<KnowledgeItem[]>([])
const learningFilter = ref('TO_LEARN')
const topic = ref<KnowledgeTopic>()
const library = ref<KnowledgeLibraryDocument>()
const practice = ref<KnowledgePractice>()
const attempts = ref<KnowledgeAttempt[]>([])
const practiceDrafts = ref<Record<string, string>>({})
const selectedQuestion = ref(0)
const selectedAttemptId = ref('')
const reanswer = ref(false)
const note = ref('')
const noteConflict = ref<KnowledgeItem>()
const intervals = ref('1,3,7,14')
const reviewEnabled = ref(true)
const readerMode = ref<'text' | 'original'>('text')
const originalUrl = ref('')
const originalPdf = ref(false)
const pdfPageCount = ref(0)
const pdfCanvases = ref<HTMLCanvasElement[]>([])
const actionOpen = ref(false)
const actionPreview = ref<KnowledgeActionPreview>()
const plans = ref<LearningPlan[]>([])
const action = reactive({ type: 'LEARNING_PLAN', planId: '', weeklyHours: 2, durationWeeks: 1, dailyMinutesCap: 60,
  startDate: '', studyDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] })
let readRequest = 0
let queryRequest = 0
let pdfRequest = 0
let lastUserId = ''
let originalDocument: pdfjs.PDFDocumentProxy | undefined
let allowHistoryRestore = true
let hydratingNote = false
const actionSource = computed(() => actionPreview.value?.payload.sourceReference as { documentId?: string; title?: string; snippet?: string } | undefined)
const actionCriteria = computed(() => Array.isArray(actionPreview.value?.payload.acceptanceCriteria) ? actionPreview.value!.payload.acceptanceCriteria as string[] : [])

const citations = computed(() => answer.value?.citations || [])
const sourceCitation = computed(() => citations.value.find(item => item.chunkId === context.value.source))
const location = computed(() => library.value?.locations.find(item => item.chunkId === context.value.source))
const noteItem = computed(() => items.value.find(item => item.topicId === topic.value?.id && item.kind === 'NOTE'))
const studyItem = computed(() => items.value.find(item => item.topicId === topic.value?.id && item.kind === 'STUDY'))
const bookmarked = computed(() => items.value.some(item => item.topicId === topic.value?.id && item.kind === 'BOOKMARK'))
const topicRecommendation = computed(() => recommendations.value.find(item => item.topicId === topic.value?.id && item.recommendationId === context.value.recommendationId))
const visibleItems = computed(() => items.value.filter(item => {
  if (learningFilter.value === 'BOOKMARK') return item.kind === 'BOOKMARK'
  if (learningFilter.value === 'NOTE') return item.kind === 'NOTE'
  if (item.kind !== 'STUDY') return false
  if (learningFilter.value === 'TO_REVIEW') return item.status === 'TO_REVIEW' || knowledgeDue(item.nextReviewAt)
  return learningFilter.value === 'SELF_MASTERED' ? item.status === 'SELF_MASTERED' : ['TO_LEARN', 'LEARNING'].includes(item.status)
}))
const question = computed(() => practice.value?.questions[selectedQuestion.value])
const questionAttempts = computed(() => attempts.value.filter(item => item.questionId === question.value?.questionId).sort((a, b) => a.attemptNo - b.attemptNo))
const attempt = computed(() => questionAttempts.value.find(item => item.attemptId === selectedAttemptId.value) || questionAttempts.value.at(-1))
const readableFeedback = computed(() => knowledgeReadableFeedback(attempt.value?.evaluationSnapshot?.feedback, question.value?.referenceChunkIds || []))
const readableNextAction = computed(() => knowledgeReadableFeedback(attempt.value?.evaluationSnapshot?.nextAction, question.value?.referenceChunkIds || []))
const hasEvaluationDetails = computed(() => Boolean(attempt.value?.evaluationSnapshot && ((attempt.value.evaluationSnapshot.feedback?.length || 0) > 220 || (attempt.value.evaluationSnapshot.nextAction?.length || 0) > 140 || attempt.value.evaluationSnapshot.quote || question.value?.rubric.length || topic.value?.documentId)))
const draft = computed({
  get: () => question.value ? practiceDrafts.value[question.value.questionId] ?? (reanswer.value ? '' : attempt.value?.answer || '') : '',
  set: (value: string) => { if (question.value) { practiceDrafts.value[question.value.questionId] = value; persistDraft() } }
})
const answeredCount = computed(() => practice.value?.questions.filter(q => attempts.value.some(item => item.questionId === q.questionId)).length || 0)
const selectedPlan = computed(() => plans.value.find(item => item.planId === action.planId))
const readerPages = computed(() => {
  const document = library.value
  if (!document) return []
  const from = location.value?.startOffset ?? sourceCitation.value?.startOffset
  const to = location.value?.endOffset ?? sourceCitation.value?.endOffset
  const quote = location.value?.snippet || sourceCitation.value?.snippet
  const pages = document.pages?.length ? document.pages : [{ pageNumber: 0, startOffset: 0, endOffset: document.content.length }]
  return pages.map(mapping => {
    const text = document.content.slice(mapping.startOffset, mapping.endOffset)
    const start = typeof from === 'number' ? from - mapping.startOffset : undefined
    const end = typeof to === 'number' ? to - mapping.startOffset : undefined
    const overlap = typeof start === 'number' && typeof end === 'number' && start < text.length && end > 0
    return { pageNumber: mapping.pageNumber, parts: knowledgeHighlightParts(text,
      overlap ? Math.max(0, start!) : undefined, overlap ? Math.min(text.length, end!) : undefined, overlap ? undefined : quote) }
  })
})
const noEvidence = computed(() => ['NO_EVIDENCE', 'INSUFFICIENT_EVIDENCE', 'INSUFFICIENT'].includes(answer.value?.evidenceStatus || ''))
const sourceUrl = computed(() => knowledgeSafeLink(topic.value?.sourceUrl))
const days = [
  { value: 'MONDAY', label: '一' }, { value: 'TUESDAY', label: '二' }, { value: 'WEDNESDAY', label: '三' },
  { value: 'THURSDAY', label: '四' }, { value: 'FRIDAY', label: '五' }, { value: 'SATURDAY', label: '六' }, { value: 'SUNDAY', label: '日' }
]

function navigate(path: string, extra: Record<string, string | undefined> = {}) {
  return router.push({ path, query: Object.fromEntries(Object.entries({ ...context.value, ...extra }).filter(([, value]) => value)) })
}
function saveContextSelection() {
  const next = { ...context.value, jobId: chosenJobId.value || undefined, resumeId: chosenResumeId.value || undefined,
    targetRole: chosenJob.value?.title || props.targetRole || undefined, matchId: requestContext.value.matchId }
  return router.replace({ path: route.path, query: Object.fromEntries(Object.entries(next).filter(([, value]) => value)) })
}
function fail(cause: unknown) { error.value = knowledgeErrorMessage(cause) }
function remember(next: KnowledgeTopic[]) { topicDirectory.value = { ...topicDirectory.value, ...Object.fromEntries(next.map(item => [item.id, item])) } }
function title(item: KnowledgeItem) { return topicDirectory.value[item.topicId]?.title || '资料已更新或不可访问' }
function difficulty(value?: string) { return ({ BASIC: '基础', BEGINNER: '基础', INTERMEDIATE: '进阶', ADVANCED: '进阶' } as Record<string, string>)[value || ''] || value || '基础' }
function gap(value: string) { return ({ MATERIAL_GAP: '材料中尚未体现', MATERIAL_NOT_SHOWN: '材料中尚未体现', PRACTICE_GAP: '练习中出现不足', SELF_REQUESTED: '希望补充学习', STUDENT_REQUEST: '希望补充学习', GENERIC: '通用岗位建议', GENERAL: '通用岗位建议', JOB_REQUIREMENT: '岗位要求', LEARNING_TASK: '学习任务' } as Record<string, string>)[value] || value }
function renderMarkdown(text: string) { return markdown.render(text || '') }
async function loadPage() {
  const token = ++readRequest
  loading.value = true; error.value = ''; notice.value = ''
  try {
    if (page.value === 'search' || page.value === 'topics') {
      const [nextRecommendations, nextTopics] = await Promise.all([getKnowledgeRecommendations({ ...requestContext.value, roleDirection: form.roleDirection || undefined, skill: form.skill || undefined, difficulty: form.difficulty || undefined, contentType: form.contentType || undefined }), getKnowledgeTopics({ roleDirection: form.roleDirection || undefined, skill: form.skill || undefined, difficulty: form.difficulty || undefined, contentType: form.contentType || undefined })])
      if (token !== readRequest) return
      recommendations.value = nextRecommendations.slice(0, 3); topics.value = nextTopics; remember(nextTopics)
    } else if (page.value === 'learning') {
      const [nextItems, nextTopics] = await Promise.all([getKnowledgeItems(), getKnowledgeTopics()])
      if (token !== readRequest) return
      items.value = nextItems; remember(nextTopics)
    } else if (page.value === 'history') {
      const next = await getKnowledgeHistory(); if (token === readRequest) history.value = next
    } else if (page.value === 'reader' || page.value === 'sources') {
      const id = context.value.documentId || sourceCitation.value?.documentId
      library.value = undefined
      if (id) { const next = await getKnowledgeLibrary(id); if (token === readRequest) library.value = next }
      else if (!citations.value.length) error.value = '请选择查询结果中的引用资料'
    } else if (page.value === 'topic') {
      const id = String(route.params.topicId || context.value.topicId || '')
      topic.value = undefined
      const [nextTopic, nextItems, nextRecommendations] = await Promise.all([getKnowledgeTopic(id), getKnowledgeItems(), context.value.recommendationId ? getKnowledgeRecommendations({ ...requestContext.value, roleDirection: form.roleDirection || undefined }).catch(() => []) : Promise.resolve(recommendations.value)])
      if (token !== readRequest) return
      topic.value = nextTopic; remember([nextTopic]); items.value = nextItems; recommendations.value = nextRecommendations
      hydratingNote = true
      note.value = localStorage.getItem(knowledgeNoteStorageKey(props.userId || '', id)) ?? noteItem.value?.note ?? ''
      hydratingNote = false
      noteConflict.value = undefined
      intervals.value = (studyItem.value?.intervalDays || [1, 3, 7, 14]).join(',')
      reviewEnabled.value = !studyItem.value || Boolean(studyItem.value.nextReviewAt)
    } else if (page.value === 'practice') {
      const id = context.value.practiceId
      if (!id) { error.value = '请选择一个专题开始练习'; return }
      const [nextPractice, nextAttempts] = await Promise.all([getKnowledgePractice(id), getKnowledgeAttempts(id)])
      if (token !== readRequest) return
      const changed = practice.value?.practiceId !== nextPractice.practiceId
      practice.value = nextPractice; attempts.value = nextAttempts
      if (changed) {
        try { practiceDrafts.value = JSON.parse(localStorage.getItem(knowledgePracticeStorageKey(props.userId || '', id)) || '{}') } catch { practiceDrafts.value = {} }
        selectedQuestion.value = knowledgePracticeQuestionIndex(nextPractice.questions, nextAttempts, practiceDrafts.value)
        selectedAttemptId.value = ''; reanswer.value = practiceDrafts.value[`retry:${question.value?.questionId}`] === '1'
      }
      const nextTopic = await getKnowledgeTopic(nextPractice.topicId)
      if (token === readRequest) { topic.value = nextTopic; remember([nextTopic]) }
    }
    if (allowHistoryRestore && context.value.historyId && ['search', 'answer', 'reader'].includes(page.value)) {
      answer.value = undefined
      const entry = await getKnowledgeHistoryEntry(context.value.historyId)
      if (token === readRequest) { answer.value = entry.answerSnapshot; form.query = entry.query }
    } else if (allowHistoryRestore && context.value.q && !answer.value && ['search', 'answer'].includes(page.value)) {
      const next = await searchKnowledge({ query: context.value.q, useAi: context.value.ai === '1', roleDirection: form.roleDirection || undefined,
        skill: form.skill || undefined, difficulty: form.difficulty || undefined, contentType: form.contentType || undefined, ...requestContext.value })
      if (token === readRequest) answer.value = next
    }
    if (page.value === 'reader') await nextTick().then(() => window.document.querySelector('.knowledge-workspace mark')?.scrollIntoView({ block: 'center' }))
  } catch (cause) { if (token === readRequest) fail(cause) }
  finally { if (token === readRequest) loading.value = false }
}
async function runSearch(ai = false) {
  const text = form.query.trim()
  if (!text) { error.value = '请输入想了解的知识或面试问题'; return }
  if (text.length > 2000) { error.value = '问题最多 2000 字，请精简后查询'; return }
  const token = ++queryRequest
  const searchPath = route.fullPath
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const next = await searchKnowledge({ query: text, useAi: ai, roleDirection: form.roleDirection || undefined, skill: form.skill || undefined, difficulty: form.difficulty || undefined, contentType: form.contentType || undefined, ...requestContext.value })
    if (token !== queryRequest || route.fullPath !== searchPath) return
    answer.value = next; form.useAi = ai; allowHistoryRestore = true
    let historyId: string | undefined
    if (next.inputFingerprint) {
      const historyRows = await getKnowledgeHistory(100).catch(() => [])
      historyId = historyRows.find(entry => entry.query === text && entry.answerSnapshot?.inputFingerprint === next.inputFingerprint && entry.permissionVersion === next.permissionVersion && entry.answerSnapshot?.generationMode === next.generationMode)?.historyId
    }
    if (token !== queryRequest || route.fullPath !== searchPath) return
    await navigate(ai ? '/student/knowledge/answer' : '/student/knowledge', { q: text, ai: ai ? '1' : '0', historyId, direction: form.roleDirection || undefined, skill: form.skill || undefined, difficulty: form.difficulty || undefined, contentType: form.contentType || undefined })
  } catch (cause) { if (token === queryRequest) fail(cause) }
  finally { if (token === queryRequest) loading.value = false }
}
function openTopic(id: string, recommendationId?: string) { return navigate(`/student/knowledge/topics/${encodeURIComponent(id)}`, { topicId: id, practiceId: undefined, documentId: undefined, source: undefined, recommendationId, direction: form.roleDirection || undefined }) }
function openReader(id: string, source?: string) { return navigate('/student/knowledge/library', { documentId: id, source }) }
function replaceItem(saved: KnowledgeItem) { items.value = items.value.filter(item => item.itemId !== saved.itemId).concat(saved) }
async function saveNote() {
  if (!topic.value) return
  busy.value = 'note'; error.value = ''
  localStorage.setItem(knowledgeNoteStorageKey(props.userId || '', topic.value.id), note.value)
  try {
    const payload = { topicId: topic.value.id, kind: 'NOTE', status: 'TO_LEARN', note: note.value, expectedRevision: noteItem.value?.revision ?? 0 }
    replaceItem(noteItem.value ? await updateKnowledgeItem(noteItem.value.itemId, payload) : await saveKnowledgeItem(payload))
    noteConflict.value = undefined; localStorage.removeItem(knowledgeNoteStorageKey(props.userId || '', topic.value.id)); notice.value = '笔记已保存'
  } catch (cause) {
    fail(cause)
    if (/版本|version|conflict|冲突/i.test(error.value)) { const next = await getKnowledgeItems().catch(() => []); noteConflict.value = next.find(item => item.topicId === topic.value?.id && item.kind === 'NOTE') }
  } finally { busy.value = '' }
}
function mergeNote() { if (noteConflict.value) replaceItem(noteConflict.value); noteConflict.value = undefined; error.value = ''; notice.value = '已读取最新版本，你的输入仍保留，请合并后保存。' }
async function deleteNote() { if (!noteItem.value) return; await removeItem(noteItem.value); note.value = '' }
async function setStudy(status: KnowledgeItem['status']) {
  if (!topic.value) return
  const next = intervals.value.split(/[,，\s]+/).map(Number).filter(v => Number.isInteger(v) && v > 0 && v <= 365)
  if (reviewEnabled.value && !next.length) { error.value = '请填写 1～365 天的复习间隔'; return }
  busy.value = 'status'; error.value = ''
  try {
    const payload = { topicId: topic.value.id, kind: 'STUDY', status, intervalDays: next, reviewEnabled: reviewEnabled.value, expectedRevision: studyItem.value?.revision ?? 0 }
    replaceItem(studyItem.value ? await updateKnowledgeItem(studyItem.value.itemId, payload) : await saveKnowledgeItem(payload))
    notice.value = `已标记为${knowledgeStateLabel(status)}`
  } catch (cause) { fail(cause) } finally { busy.value = '' }
}
async function bookmark() {
  if (!topic.value) return
  busy.value = 'bookmark'
  try {
    const item = items.value.find(entry => entry.topicId === topic.value?.id && entry.kind === 'BOOKMARK')
    if (item) { await deleteKnowledgeItem(item.itemId); items.value = items.value.filter(entry => entry.itemId !== item.itemId) }
    else replaceItem(await saveKnowledgeItem({ topicId: topic.value.id, kind: 'BOOKMARK', status: 'TO_LEARN', expectedRevision: 0 }))
  } catch (cause) { fail(cause) } finally { busy.value = '' }
}
async function review(item: KnowledgeItem, passed: boolean) { busy.value = item.itemId; try { replaceItem(await reviewKnowledgeItem(item.itemId, passed)) } catch (cause) { fail(cause) } finally { busy.value = '' } }
async function removeItem(item: KnowledgeItem) { busy.value = item.itemId; try { await deleteKnowledgeItem(item.itemId); items.value = items.value.filter(entry => entry.itemId !== item.itemId) } catch (cause) { fail(cause) } finally { busy.value = '' } }
async function removeHistory(id: string) { busy.value = id; try { await deleteKnowledgeHistory(id); history.value = history.value.filter(item => item.historyId !== id) } catch (cause) { fail(cause) } finally { busy.value = '' } }
function persistDraft() { if (practice.value) localStorage.setItem(knowledgePracticeStorageKey(props.userId || '', practice.value.practiceId), JSON.stringify(practiceDrafts.value)) }
async function beginPractice() {
  if (!topic.value) return
  busy.value = 'practice'; error.value = ''
  try { const next = await createKnowledgePractice(topic.value.id); await navigate('/student/knowledge/practice', { practiceId: next.practiceId, topicId: topic.value.id }) }
  catch (cause) { fail(cause) } finally { busy.value = '' }
}
async function submitAnswer() {
  if (!practice.value || !question.value || !draft.value.trim()) { error.value = '请先填写回答'; return }
  const practiceId = practice.value.practiceId
  const questionId = question.value.questionId
  const answerText = draft.value.trim()
  busy.value = 'answer'; error.value = ''
  try {
    const saved = await saveKnowledgeAnswer(practiceId, questionId, answerText)
    attempts.value = attempts.value.filter(item => item.attemptId !== saved.attemptId).concat(saved)
    selectedAttemptId.value = saved.attemptId; reanswer.value = false
    delete practiceDrafts.value[questionId]; delete practiceDrafts.value[`retry:${questionId}`]; persistDraft(); notice.value = '回答已保存，正在评价'
    const evaluated = await retryKnowledgeAttempt(practiceId, saved.attemptId)
    attempts.value = attempts.value.map(item => item.attemptId === evaluated.attemptId ? evaluated : item)
    notice.value = evaluated.status === 'FAILED' ? '回答已保存，评价失败，可重试' : '回答已保存并完成评价'
  } catch (cause) { fail(cause); const saved = await getKnowledgeAttempts(practiceId).catch(() => []); if (saved.length && practice.value?.practiceId === practiceId) attempts.value = saved }
  finally { busy.value = '' }
}
async function retry() {
  if (!practice.value || !attempt.value) return
  busy.value = 'evaluation'; error.value = ''
  try { const next = await retryKnowledgeAttempt(practice.value.practiceId, attempt.value.attemptId); attempts.value = attempts.value.map(item => item.attemptId === next.attemptId ? next : item) }
  catch (cause) { fail(cause) } finally { busy.value = '' }
}
function chooseQuestion(index: number) { selectedQuestion.value = index; selectedAttemptId.value = ''; reanswer.value = practiceDrafts.value[`retry:${question.value?.questionId}`] === '1' }
function startReanswer() { reanswer.value = true; if (question.value) { if (!practiceDrafts.value[question.value.questionId]) practiceDrafts.value[question.value.questionId] = attempt.value?.answer || ''; practiceDrafts.value[`retry:${question.value.questionId}`] = '1' }; persistDraft() }
async function openAction(type: string) {
  action.type = type; actionPreview.value = undefined; actionOpen.value = true; error.value = ''
  if (type !== 'LEARNING_PLAN') return
  busy.value = 'plans'
  try { plans.value = (await listLearningPlans()).filter(item => item.status === 'ACTIVE'); action.planId = requestContext.value.planId || plans.value[0]?.planId || ''; budget() }
  catch (cause) { fail(cause) } finally { busy.value = '' }
}
function budget() {
  const plan = selectedPlan.value
  action.weeklyHours = plan?.weeklyHours || 2; action.durationWeeks = plan?.durationWeeks || 1
  action.dailyMinutesCap = plan?.dailyMinutesCap || 60; action.startDate = plan?.startDate || ''
  action.studyDays = plan?.studyDays?.length ? [...plan.studyDays] : ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']
}
async function previewAction() {
  if (!topic.value) return
  if (action.type === 'LEARNING_PLAN' && (!action.studyDays.length || action.weeklyHours * 60 > action.dailyMinutesCap * action.studyDays.length)) { error.value = '每周时间超过学习日与每日上限的总容量，请调整安排'; return }
  busy.value = 'preview'; error.value = ''
  try { actionPreview.value = await previewKnowledgeAction({ type: action.type, topicId: topic.value.id, ...requestContext.value, planId: action.planId || undefined, practiceId: practice.value?.practiceId, ...(action.type === 'LEARNING_PLAN' ? { weeklyHours: action.weeklyHours, durationWeeks: action.durationWeeks, dailyMinutesCap: action.dailyMinutesCap, startDate: action.startDate || undefined, studyDays: action.studyDays } : {}) }) }
  catch (cause) { fail(cause) } finally { busy.value = '' }
}
async function confirmAction() {
  if (!actionPreview.value) return
  busy.value = 'confirm'; error.value = ''
  try {
    const result = await confirmKnowledgeAction(actionPreview.value.previewId); actionOpen.value = false
    const path = result.payload?.path
    if (typeof path === 'string' && /^\/student\/(plan|interview)(\/|\?|$)/.test(path)) await router.push(path)
    else notice.value = '安排已确认，可在学习计划或模拟面试中查看'
  } catch (cause) { fail(cause) } finally { busy.value = '' }
}
async function showOriginal() {
  if (!library.value?.originalAvailable) return
  busy.value = 'original'; error.value = ''; const token = ++pdfRequest
  try {
    const blob = await getKnowledgeOriginal(library.value.documentId)
    if (token !== pdfRequest) return
    if (originalUrl.value) URL.revokeObjectURL(originalUrl.value)
    originalUrl.value = URL.createObjectURL(blob)
    const bytes = new Uint8Array(await blob.arrayBuffer())
    originalPdf.value = new TextDecoder().decode(bytes.slice(0, 5)) === '%PDF-'; readerMode.value = 'original'
    if (!originalPdf.value) return
    const pdf = await pdfjs.getDocument({ data: bytes, isEvalSupported: false }).promise
    if (token !== pdfRequest) { await pdf.destroy(); return }
    originalDocument = pdf; pdfPageCount.value = pdf.numPages; await nextTick()
    for (let number = 1; number <= pdf.numPages; number++) {
      if (token !== pdfRequest) return
      const pdfPage = await pdf.getPage(number); const viewport = pdfPage.getViewport({ scale: 1.4 })
      const canvas = pdfCanvases.value[number - 1]; const draw = canvas?.getContext('2d', { alpha: false })
      if (!canvas || !draw) throw new Error('原件预览初始化失败')
      canvas.width = viewport.width; canvas.height = viewport.height
      await pdfPage.render({ canvasContext: draw, viewport }).promise
    }
    if (location.value?.pageNumber) window.document.querySelector(`[data-knowledge-pdf-page="${location.value.pageNumber}"]`)?.scrollIntoView({ block: 'start' })
  } catch (cause) { fail(cause) } finally { if (token === pdfRequest) busy.value = '' }
}
function clearOriginal() {
  pdfRequest++; void originalDocument?.destroy(); originalDocument = undefined
  if (originalUrl.value) URL.revokeObjectURL(originalUrl.value)
  originalUrl.value = ''; pdfPageCount.value = 0; readerMode.value = 'text'
}
watch(() => [route.fullPath, props.userId], () => {
  if (props.userId !== lastUserId) { lastUserId = props.userId || ''; answer.value = undefined; topic.value = undefined; practice.value = undefined; items.value = []; history.value = []; topicDirectory.value = {} }
  if (page.value !== 'reader') clearOriginal()
  if (context.value.q && answer.value?.query !== context.value.q) answer.value = undefined
  if (context.value.historyId) allowHistoryRestore = true
  form.query = context.value.q || form.query; form.useAi = context.value.ai === '1'; void loadPage()
}, { immediate: true })
watch(() => props.targetRole, value => { if (!form.roleDirection) form.roleDirection = knowledgeDirection(value) })
watch(() => props.resumeId, value => { if (!chosenResumeId.value) chosenResumeId.value = value || '' })
watch(() => JSON.stringify(requestContext.value), () => { answer.value = undefined; queryRequest++; allowHistoryRestore = false; if (page.value === 'search' || page.value === 'topics') void loadPage() })
watch(() => [form.roleDirection, form.skill, form.difficulty, form.contentType], () => {
  answer.value = undefined; queryRequest++; allowHistoryRestore = false
  if (page.value === 'search' || page.value === 'topics') void loadPage()
  else { readRequest++; loading.value = false }
})
watch(() => context.value.documentId, () => clearOriginal())
watch(note, value => { if (!hydratingNote && topic.value) localStorage.setItem(knowledgeNoteStorageKey(props.userId || '', topic.value.id), value) }, { flush: 'sync' })
onBeforeUnmount(() => { readRequest++; queryRequest++; clearOriginal() })
</script>

<template>
  <section class="knowledge-workspace" data-testid="knowledge-workspace" :aria-busy="loading">
    <div v-if="error" class="inline-alert warning" role="alert">{{ error }}<button v-if="!noteConflict" @click="loadPage"><RefreshCw :size="14" />重试</button></div>
    <p v-if="notice" class="inline-alert success" role="status">{{ notice }}</p>
    <p v-if="loading" class="loading-status">正在读取…</p>
    <section v-if="page === 'search' || page === 'topics'" class="search-page" data-testid="knowledge-search">
      <div class="search-row">
        <el-input v-model="form.query" maxlength="2000" clearable placeholder="搜索技能或具体问题" aria-label="知识库查询" data-testid="knowledge-query" @keyup.enter="runSearch(false)"><template #prefix><Search :size="17" /></template></el-input>
        <el-button type="primary" :loading="loading" data-testid="knowledge-submit" @click="runSearch(false)"><Search :size="16" />搜索</el-button>
      </div>
      <div class="search-context">
        <span v-if="requestContext.targetRole">{{ requestContext.targetRole }}</span>
        <details class="filters"><summary><ChevronDown :size="14" />筛选</summary>
          <div class="filter-grid">
            <label v-if="props.jobs?.length">目标岗位<el-select v-model="chosenJobId" clearable placeholder="通用岗位建议" @change="saveContextSelection"><el-option v-for="job in props.jobs" :key="job.jobId" :label="job.title" :value="job.jobId" /></el-select></label>
            <label v-if="props.resumes?.length">使用简历<el-select v-model="chosenResumeId" clearable placeholder="不使用简历" @change="saveContextSelection"><el-option v-for="resume in props.resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" /></el-select></label>
            <label>岗位方向<el-select v-model="form.roleDirection" clearable aria-label="岗位方向"><el-option label="Java" value="JAVA" /><el-option label="前端" value="FRONTEND" /><el-option label="运营" value="OPERATIONS" /></el-select></label>
            <label>技能<el-input v-model="form.skill" placeholder="如 Redis" /></label>
            <label>难度<el-select v-model="form.difficulty" clearable><el-option label="基础" value="BEGINNER" /><el-option label="进阶" value="INTERMEDIATE" /></el-select></label>
            <label>内容类型<el-select v-model="form.contentType" clearable><el-option label="专题" value="TOPIC" /><el-option label="资料" value="DOCUMENT" /></el-select></label>
          </div>
        </details>
      </div>
      <section v-if="page === 'search' && !answer && recommendations.length" class="recommendations">
        <div class="section-head"><strong>优先学习</strong></div>
        <button v-for="(item, index) in recommendations" :key="item.recommendationId" class="recommendation" @click="openTopic(item.topicId, item.recommendationId)"><span class="priority">{{ index + 1 }}</span><span><strong>{{ item.title }}</strong><small class="recommendation-reason">{{ knowledgeExcerpt(item.reason, 100) }}</small><small>{{ gap(item.gapType) }} · {{ item.estimatedMinutes }} 分钟</small></span><ArrowRight :size="16" /></button>
      </section>
      <section v-if="answer && page === 'search'" class="results">
        <div class="section-head"><strong>{{ citations.length }} 条资料</strong><el-button :loading="loading" data-testid="knowledge-explain" @click="runSearch(true)">通俗讲解 <ArrowRight :size="15" /></el-button></div>
        <p v-if="noEvidence" class="inline-alert neutral">现有资料不足以支持完整回答，可核对原文或调整问题。</p>
        <article v-for="citation in citations.slice(0, 5)" :key="citation.chunkId" class="result"><button class="result-title" @click="openReader(citation.documentId, citation.chunkId)"><strong>{{ citation.title }}</strong><ArrowRight :size="15" /></button><p v-if="knowledgeSearchExcerpt(citation.snippet)">{{ knowledgeSearchExcerpt(citation.snippet) }}</p><small>{{ citation.source }} · {{ knowledgePosition(citation) }}</small></article>
        <p v-if="!citations.length" class="empty">没有找到可引用资料。</p>
      </section>
      <section v-else-if="page === 'topics'" class="topics" data-testid="knowledge-topics">
        <div class="section-head"><strong>全部专题</strong><el-button text @click="navigate('/student/knowledge')"><ArrowLeft :size="15" />查知识</el-button></div>
        <button v-for="item in topics" :key="item.id" class="topic-row" @click="openTopic(item.id)"><span><strong>{{ item.title }}</strong><small>{{ knowledgeExcerpt(item.summary, 90) }}</small></span><small>{{ difficulty(item.difficulty) }} · {{ item.estimatedMinutes }} 分钟</small><ArrowRight :size="15" /></button>
        <p v-if="!loading && !topics.length" class="empty">没有符合筛选条件的专题。</p>
      </section>
      <el-button v-if="page === 'search' && !answer" text @click="navigate('/student/knowledge/topics')">查看全部专题 <ArrowRight :size="15" /></el-button>
    </section>
    <section v-else-if="page === 'answer'" data-testid="knowledge-answer">
      <div class="page-actions"><el-button text @click="navigate('/student/knowledge')"><ArrowLeft :size="16" />查询结果</el-button></div>
      <template v-if="answer"><h2>{{ answer.query }}</h2><p v-if="noEvidence" class="inline-alert neutral">资料不足的部分已保留为待确认项。</p><div class="answer-text" v-html="renderMarkdown(answer.answer)" /><div class="citation-links"><button v-for="(citation, index) in citations" :key="citation.chunkId" @click="openReader(citation.documentId, citation.chunkId)">[{{ index + 1 }}] {{ citation.title }} · {{ knowledgePosition(citation) }}</button></div><details v-if="answer.claims?.length || citations.length" class="advanced"><summary>核对回答依据</summary><article v-for="(claim, index) in answer.claims" :key="index"><p>{{ claim.text }}</p><blockquote>{{ claim.supportQuote }}</blockquote></article><p v-for="citation in citations" :key="citation.chunkId">{{ citation.title }} · {{ knowledgePosition(citation) }}<span v-if="citation.documentVersion"> · 资料版本 {{ citation.documentVersion }}</span></p></details></template>
      <p v-else-if="!loading" class="empty">回答未保存在当前页面，可从查询历史恢复，或重新搜索。</p>
    </section>
    <section v-else-if="page === 'learning'" data-testid="knowledge-learning">
      <div class="learning-filters"><el-radio-group v-model="learningFilter" aria-label="个人学习分类"><el-radio-button value="TO_LEARN">待学习</el-radio-button><el-radio-button value="TO_REVIEW">待复习</el-radio-button><el-radio-button value="BOOKMARK">收藏</el-radio-button><el-radio-button value="NOTE">笔记</el-radio-button><el-radio-button value="SELF_MASTERED">自报掌握</el-radio-button></el-radio-group></div>
      <article v-for="item in visibleItems" :key="item.itemId" class="learning-row"><div><button class="result-title" @click="openTopic(item.topicId)"><strong>{{ title(item) }}</strong><ArrowRight :size="15" /></button><p v-if="item.kind === 'NOTE'">{{ knowledgeExcerpt(item.note, 180) }}</p><small>{{ knowledgeStateLabel(item.status) }}<span v-if="item.nextReviewAt"> · {{ knowledgeDue(item.nextReviewAt) ? '应复习' : '下次复习' }} {{ knowledgeDateLabel(item.nextReviewAt) }}</span></small></div><div class="row-actions"><el-button v-if="learningFilter === 'TO_REVIEW'" size="small" @click="openTopic(item.topicId)">复习</el-button><el-button text :loading="busy === item.itemId" aria-label="移除个人记录" @click="removeItem(item)"><Trash2 :size="15" /></el-button></div></article>
      <p v-if="!loading && !visibleItems.length" class="empty">暂无此类记录。</p>
    </section>
    <section v-else-if="page === 'history'" data-testid="knowledge-history">
      <article v-for="entry in history" :key="entry.historyId" class="history-row"><button @click="navigate('/student/knowledge/answer', { historyId: entry.historyId, q: entry.query })"><strong>{{ entry.query }}</strong><small>{{ knowledgeDateLabel(entry.createdAt) }}{{ entry.role ? ` · ${entry.role}` : '' }}</small></button><el-button text :loading="busy === entry.historyId" aria-label="删除查询记录" @click="removeHistory(entry.historyId)"><Trash2 :size="15" /></el-button></article>
      <p v-if="!loading && !history.length" class="empty">暂无查询历史。</p>
    </section>
    <section v-else-if="page === 'reader' || page === 'sources'" data-testid="knowledge-reader">
      <div class="page-actions"><el-button text @click="navigate(knowledgeIsExplanation(answer?.generationMode) ? '/student/knowledge/answer' : '/student/knowledge')"><ArrowLeft :size="16" />返回结果</el-button><el-button v-if="library?.originalAvailable" :loading="busy === 'original'" @click="showOriginal"><FileText :size="15" />查看原件</el-button><a v-if="originalUrl" :href="originalUrl" :download="library?.title">下载原件</a></div>
      <template v-if="library">
        <h2>{{ library.title }}</h2><p class="source-line">{{ library.source }}<span v-if="location?.pageNumber"> · 第 {{ location.pageNumber }} 页</span></p>
        <div v-if="readerMode === 'original' && originalPdf" class="pdf-pages"><figure v-for="number in pdfPageCount" :key="number" :data-knowledge-pdf-page="number"><canvas :ref="element => { if (element) pdfCanvases[number - 1] = element as HTMLCanvasElement }" /><figcaption>第 {{ number }} 页</figcaption></figure></div>
        <div v-if="readerMode === 'text' || !originalPdf"><details v-if="library.locations.some(item => item.heading)" class="advanced"><summary>目录</summary><div class="reader-toc"><button v-for="item in library.locations.filter(item => item.heading)" :key="item.chunkId" @click="navigate('/student/knowledge/library', { source: item.chunkId })">{{ item.heading }}{{ item.pageNumber ? ` · ${item.pageNumber}` : '' }}</button></div></details><article v-for="mapping in readerPages" :key="mapping.pageNumber" class="reader-page"><small v-if="mapping.pageNumber" class="page-number">第 {{ mapping.pageNumber }} 页</small><div><template v-for="(part, index) in mapping.parts" :key="index"><mark v-if="part.highlighted">{{ part.text }}</mark><span v-else>{{ part.text }}</span></template></div></article></div>
        <el-button v-if="readerMode === 'original' && originalPdf" text @click="readerMode = 'text'">查看可复制正文</el-button>
        <p v-if="!library.pages.length" class="source-line">当前资料没有原文件页码，使用正文位置定位。</p>
        <details class="advanced"><summary>来源详情</summary><p>资料版本 {{ library.version }} · {{ library.status === 'LEGACY' ? '历史资料' : '已发布资料' }}</p><p v-if="!library.originalAvailable">原件不可用，已保存正文仍可阅读。</p></details>
      </template>
      <div v-else-if="citations.length" class="citation-links"><button v-for="citation in citations" :key="citation.chunkId" @click="openReader(citation.documentId, citation.chunkId)">{{ citation.title }}</button></div>
    </section>
    <section v-else-if="page === 'topic'" data-testid="knowledge-topic">
      <div class="page-actions"><el-button text @click="navigate('/student/knowledge')"><ArrowLeft :size="16" />查知识</el-button><el-button data-testid="knowledge-bookmark" :loading="busy === 'bookmark'" @click="bookmark"><Bookmark :size="15" :fill="bookmarked ? 'currentColor' : 'none'" />{{ bookmarked ? '已收藏' : '收藏' }}</el-button></div>
      <template v-if="topic">
        <h2>{{ topic.title }}</h2><p class="source-line">{{ topic.skill }} · {{ difficulty(topic.difficulty) }} · {{ topic.estimatedMinutes }} 分钟</p><p class="topic-summary">{{ topic.summary }}</p><div class="topic-body" v-html="renderMarkdown(topic.content)" />
        <details v-if="topicRecommendation" class="advanced"><summary>为什么推荐</summary><p>{{ topicRecommendation.reason }}</p><p>{{ gap(topicRecommendation.gapType) }} · {{ topicRecommendation.estimatedMinutes }} 分钟</p></details>
        <section v-if="topic.example" class="example"><strong>资料中的例子</strong><p>{{ topic.example }}</p></section>
        <section class="topic-next"><el-button type="primary" :loading="busy === 'practice'" @click="beginPractice"><Lightbulb :size="16" />做 3 道短练习</el-button><el-button @click="openAction('LEARNING_PLAN')"><Timer :size="16" />安排实践</el-button><el-button @click="openAction('INTERVIEW')">练一道面试题 <ArrowRight :size="15" /></el-button></section>
        <details class="advanced"><summary>学习状态与复习</summary><div class="study-controls"><el-select :model-value="studyItem?.status || 'TO_LEARN'" aria-label="学习状态" :disabled="busy === 'status'" @change="setStudy"><el-option label="待学习" value="TO_LEARN" /><el-option label="学习中" value="LEARNING" /><el-option label="自报掌握" value="SELF_MASTERED" /><el-option label="待复习" value="TO_REVIEW" /></el-select><el-checkbox v-model="reviewEnabled">安排站内复习</el-checkbox><label>间隔（天）<el-input v-model="intervals" aria-label="复习间隔" /></label><el-button :loading="busy === 'status'" @click="setStudy(studyItem?.status || 'TO_LEARN')">保存安排</el-button></div><p v-if="studyItem?.nextReviewAt" class="source-line">下次复习 {{ knowledgeDateLabel(studyItem.nextReviewAt) }}</p><div v-if="studyItem && (studyItem.status === 'TO_REVIEW' || knowledgeDue(studyItem.nextReviewAt))" class="row-actions"><el-button @click="review(studyItem, true)">本次复习通过</el-button><el-button @click="review(studyItem, false)">明天再复习</el-button></div><p class="source-line">自报掌握与练习表现分别记录。</p></details>
        <section class="note-box"><label for="knowledge-note">我的笔记</label><el-input id="knowledge-note" v-model="note" type="textarea" :rows="4" maxlength="8000" data-testid="knowledge-note" placeholder="记录自己的理解和待验证问题" /><div v-if="noteConflict" class="inline-alert warning"><p>其他页面的最新笔记：{{ noteConflict.note }}</p><el-button @click="mergeNote">使用最新版本号，保留我的输入</el-button></div><div class="row-actions"><el-button :loading="busy === 'note'" data-testid="knowledge-save-note" @click="saveNote"><Check :size="15" />保存笔记</el-button><el-button v-if="noteItem" text @click="deleteNote"><Trash2 :size="15" />删除笔记</el-button></div></section>
        <details class="advanced"><summary>来源与适用范围</summary><p>{{ topic.source }}{{ topic.applicableVersion ? ` · ${topic.applicableVersion}` : '' }}{{ topic.checkedAt ? ` · 核对于 ${topic.checkedAt}` : '' }}</p><a v-if="sourceUrl" :href="sourceUrl" target="_blank" rel="noopener noreferrer">参考出处</a><el-button v-if="topic.documentId" text @click="openReader(topic.documentId)">查看引用原文</el-button><p v-if="topic.prerequisites.length">前置知识：{{ topic.prerequisites.join('、') }}</p></details>
      </template>
    </section>
    <section v-else-if="page === 'practice'" data-testid="knowledge-practice">
      <div class="page-actions"><el-button text @click="openTopic(practice?.topicId || context.topicId || '')"><ArrowLeft :size="16" />返回专题</el-button><span v-if="practice">{{ answeredCount }}/{{ practice.questions.length }} 已回答 · {{ practice.estimatedMinutes }} 分钟</span></div>
      <template v-if="practice && question">
        <nav class="question-nav" aria-label="练习题目"><button v-for="(item, index) in practice.questions" :key="item.questionId" :class="{ active: selectedQuestion === index }" @click="chooseQuestion(index)">{{ index + 1 }}<Check v-if="attempts.some(x => x.questionId === item.questionId)" :size="12" /></button></nav>
        <h2>{{ question.prompt }}</h2><el-input v-model="draft" type="textarea" :rows="7" maxlength="4000" show-word-limit :disabled="Boolean(attempt && !reanswer)" data-testid="knowledge-practice-answer" placeholder="用自己的话回答，说明方法和验证方式" />
        <div class="row-actions"><el-button v-if="!attempt || reanswer" type="primary" :loading="busy === 'answer'" data-testid="knowledge-practice-submit" @click="submitAnswer"><Send :size="15" />提交回答</el-button><el-button v-else @click="startReanswer">重答</el-button><el-button v-if="attempt && ['FAILED', 'RECORDED'].includes(attempt.status)" :loading="busy === 'evaluation'" data-testid="knowledge-practice-retry" @click="retry">{{ attempt.status === 'FAILED' ? '重试评价' : '评价已保存回答' }}</el-button><el-button v-if="selectedQuestion < practice.questions.length - 1" text @click="chooseQuestion(selectedQuestion + 1)">下一题 <ArrowRight :size="15" /></el-button></div>
        <section v-if="attempt" class="evaluation"><p v-if="attempt.status === 'FAILED'" class="inline-alert warning">回答已保存，评价未成功。</p><template v-if="attempt.evaluationSnapshot"><strong>{{ attempt.evaluationSnapshot.status === 'DEMO' ? '规则演示评价' : '本次评价' }} · {{ attempt.evaluationSnapshot.score }} 分</strong><p>{{ knowledgeExcerpt(readableFeedback, 220) }}</p><p v-if="readableNextAction">下一步：{{ knowledgeExcerpt(readableNextAction, 140) }}</p><details v-if="hasEvaluationDetails" class="advanced"><summary>完整反馈与依据</summary><p v-if="attempt.evaluationSnapshot.feedback.length > 220">{{ attempt.evaluationSnapshot.feedback }}</p><p v-if="attempt.evaluationSnapshot.nextAction.length > 140">下一步：{{ attempt.evaluationSnapshot.nextAction }}</p><blockquote v-if="attempt.evaluationSnapshot.quote">{{ attempt.evaluationSnapshot.quote }}</blockquote><p v-for="criterion in question.rubric" :key="criterion">{{ criterion }}</p><el-button v-if="topic?.documentId" text @click="openReader(topic.documentId, attempt.evaluationSnapshot.referenceChunkIds[0])">核对参考原文</el-button></details></template></section>
        <details v-if="questionAttempts.length > 1" class="advanced"><summary>回答历史 · {{ questionAttempts.length }} 次</summary><article v-for="item in questionAttempts" :key="item.attemptId"><button class="result-title" @click="selectedAttemptId = item.attemptId; reanswer = false">第 {{ item.attemptNo }} 次 · {{ item.evaluationSnapshot?.score ?? '待评价' }} 分</button><p>{{ item.answer }}</p></article></details>
        <div v-if="answeredCount === practice.questions.length" class="topic-next"><el-button @click="openAction('LEARNING_PLAN')">把薄弱项安排进学习计划</el-button><el-button @click="openAction('INTERVIEW')">继续面试练习</el-button></div>
      </template>
    </section>
    <el-dialog v-model="actionOpen" title="后续安排" width="min(540px, calc(100vw - 24px))" :close-on-click-modal="false" data-testid="knowledge-action-preview">
      <div v-if="action.type === 'LEARNING_PLAN' && !actionPreview" class="action-form"><label>学习计划<el-select v-model="action.planId" @change="budget"><el-option label="新建计划" value="" /><el-option v-for="plan in plans" :key="plan.planId" :label="plan.targetRole" :value="plan.planId" /></el-select></label><div class="budget-grid"><label>每周小时<el-input-number v-model="action.weeklyHours" :min="2" :max="40" /></label><label>计划周数<el-input-number v-model="action.durationWeeks" :min="1" :max="24" /></label><label>每天最多分钟<el-input-number v-model="action.dailyMinutesCap" :min="30" :max="480" /></label></div><label>开始日期<el-input v-model="action.startDate" type="date" /></label><div class="study-days"><el-checkbox-group v-model="action.studyDays"><el-checkbox v-for="day in days" :key="day.value" :value="day.value">周{{ day.label }}</el-checkbox></el-checkbox-group></div></div>
      <section v-if="actionPreview" class="action-content"><strong>{{ actionPreview.title }}</strong><p>{{ actionPreview.reason }}</p><p>{{ actionPreview.impact }}</p><p>{{ actionPreview.estimatedMinutes }} 分钟</p><p v-if="actionPreview.payload.exercise || topic?.practicePrompt">练习：{{ actionPreview.payload.exercise || topic?.practicePrompt }}</p><div v-if="actionCriteria.length"><strong>验收标准</strong><p v-for="criterion in actionCriteria" :key="criterion">{{ criterion }}</p></div><p v-if="actionSource?.snippet">知识依据：{{ knowledgeExcerpt(actionSource.snippet, 180) }}</p><el-button v-if="actionSource?.documentId || topic?.documentId" text @click="openReader(actionSource?.documentId || topic!.documentId!); actionOpen = false">核对知识引用</el-button><el-button text @click="actionPreview = undefined">调整安排</el-button></section>
      <p v-else-if="action.type === 'INTERVIEW'">将围绕当前资料及授权的求职上下文创建辅导练习。</p>
      <div v-if="error" class="inline-alert warning">{{ error }}</div>
      <template #footer><el-button @click="actionOpen = false">取消</el-button><el-button v-if="!actionPreview" type="primary" :loading="busy === 'preview'" @click="previewAction">查看调整预览</el-button><el-button v-else type="primary" :loading="busy === 'confirm'" data-testid="knowledge-action-confirm" @click="confirmAction">确认安排</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.knowledge-workspace{display:grid;gap:12px;min-width:0;width:100%;max-width:980px;margin:0 auto;padding:8px 0 28px;color:var(--ink,#1f2724);font-size:13px;letter-spacing:0;overflow-wrap:anywhere}
.knowledge-workspace h2{font-size:18px;line-height:1.5;margin:12px 0}.search-page{display:grid;gap:14px}.search-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px}.search-context,.section-head,.page-actions,.row-actions{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}.search-context{justify-content:flex-start;color:var(--muted,#66716c);font-size:12px}.filters summary,.advanced summary{display:flex;align-items:center;gap:5px;cursor:pointer;color:var(--muted,#66716c);font-size:12px;line-height:22px}.filter-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;padding:12px 0}.filter-grid label,.action-form>label,.budget-grid label{display:grid;gap:5px;font-size:12px;color:var(--muted,#66716c);min-width:0}.filter-grid .el-button{justify-self:start}.recommendations,.topics,.results{padding-top:12px;border-top:1px solid var(--line,#e4ebe7)}.section-head{min-height:30px;margin-bottom:4px;font-size:13px}.section-head>span{color:var(--muted,#66716c);font-size:12px}
.recommendation,.topic-row{display:flex;align-items:center;gap:10px;width:100%;min-width:0;padding:12px 0;border:0;border-bottom:1px solid var(--line,#e8eeea);background:transparent;text-align:left;cursor:pointer;color:inherit}.recommendation>span:nth-child(2),.topic-row>span{display:grid;gap:4px;flex:1;min-width:0}.recommendation strong,.topic-row strong{font-size:14px;line-height:1.5}.recommendation small,.topic-row small{color:var(--muted,#66716c);font-size:12px;line-height:1.5}.topic-row>small{flex:none;max-width:110px}.recommendation:hover,.topic-row:hover,.history-row:hover{background:#f5faf7}.priority{display:grid;place-items:center;flex:none;width:23px;height:23px;border-radius:50%;background:#e5f4ed;color:var(--accent,#28664f);font-weight:750;font-size:12px}.result{padding:14px 0;border-bottom:1px solid var(--line,#e8eeea)}.result-title{display:flex;align-items:center;gap:7px;max-width:100%;border:0;padding:0;background:none;color:inherit;font-size:14px;line-height:1.5;text-align:left;cursor:pointer;overflow-wrap:anywhere}.result-title>strong{min-width:0}.result-title svg{flex:none;color:var(--accent,#28664f)}.result p{margin:7px 0;font-size:13px;line-height:1.7;color:#53615a}.result small,.source-line{color:var(--muted,#66716c);font-size:12px;line-height:1.6}.source-line{margin:6px 0 12px}.empty{padding:16px 0;margin:0;color:var(--muted,#66716c);font-size:13px}
.answer-text,.topic-body,.reader-page>div{white-space:pre-wrap;line-height:1.9;font-size:14px;color:#36423c}.topic-body{white-space:normal}.topic-body :deep(p){margin:7px 0}.answer-text{margin-top:16px;white-space:normal}.answer-text :deep(h2),.answer-text :deep(h3){font-size:16px;line-height:1.5;margin:16px 0 8px}.answer-text :deep(p){margin:7px 0}.answer-text :deep(pre){white-space:pre-wrap;overflow-wrap:anywhere;background:#f3f6f4;padding:10px}.citation-links{display:grid;gap:6px;margin:18px 0}.citation-links button,.reader-toc button{border:0;background:none;text-align:left;font-size:12px;line-height:1.7;color:var(--accent,#28664f);padding:5px 0;cursor:pointer}.advanced{margin-top:16px;padding-top:12px;border-top:1px solid var(--line,#e5ece7);font-size:12px;line-height:1.8}.advanced p{margin:7px 0}.advanced a,.page-actions a{color:var(--accent,#28664f);font-size:12px}.advanced article{padding:10px 0;border-bottom:1px solid var(--line,#e4ebe7)}.advanced blockquote,.evaluation blockquote{margin:8px 0;padding:0 0 0 10px;border-left:2px solid #c2dace;color:#66716c;white-space:pre-wrap}
.learning-filters{margin-bottom:10px;max-width:100%}.learning-filters :deep(.el-radio-group){display:flex;flex-wrap:wrap}.learning-filters :deep(.el-radio-button__inner){padding:8px 10px;font-size:12px;border-radius:0!important}.learning-row,.history-row{display:flex;align-items:center;gap:10px;padding:14px 0;border-bottom:1px solid var(--line,#e4ebe7)}.learning-row>div:first-child,.history-row>button{flex:1;min-width:0}.learning-row p{margin:6px 0;line-height:1.7;color:#53615a}.learning-row small,.history-row small{display:block;margin-top:4px;color:var(--muted,#66716c);font-size:12px}.row-actions{justify-content:flex-start;margin-top:12px}.learning-row .row-actions{margin-top:0;flex:none}.history-row>button{display:grid;gap:4px;border:0;background:none;padding:0;text-align:left;color:inherit;cursor:pointer}.history-row strong{font-size:14px}.page-actions{justify-content:flex-start;font-size:12px;color:var(--muted,#66716c)}
.reader-page{padding:14px 0}.reader-page+.reader-page{border-top:1px dashed var(--line,#dfe8e2)}.page-number{display:block;font-size:12px;color:#758079;margin-bottom:8px}.reader-page mark{background:#fff0a5;color:inherit;padding:2px 0;scroll-margin-top:70px}.reader-toc{display:grid;gap:3px}.pdf-pages{display:grid;gap:12px}.pdf-pages figure{margin:0;scroll-margin-top:20px}.pdf-pages canvas{display:block;max-width:100%;width:100%;height:auto;border:1px solid #e5e9e6}.pdf-pages figcaption{text-align:center;font-size:12px;color:var(--muted,#66716c);padding:7px}.topic-summary{font-size:14px;color:#53615a;line-height:1.8}.example{margin-top:18px;padding:12px 14px;background:#f4f8f5;border-left:3px solid #7b9e8a;font-size:13px;line-height:1.8}.example p{white-space:pre-wrap;margin:6px 0 0}.topic-next{display:flex;gap:8px;flex-wrap:wrap;margin:20px 0}.note-box{display:grid;gap:8px;margin-top:18px;padding-top:14px;border-top:1px solid var(--line,#e4ebe7)}.note-box label{font-size:13px;font-weight:650}.note-box .row-actions{margin-top:0}.study-controls{display:flex;align-items:end;gap:10px;flex-wrap:wrap;margin:10px 0}.study-controls>.el-select{width:140px}.study-controls>label{display:grid;gap:4px;width:140px}
.question-nav{display:flex;gap:8px;margin:15px 0}.question-nav button{display:flex;align-items:center;justify-content:center;gap:3px;width:35px;height:30px;border:1px solid var(--line,#dfe8e2);border-radius:5px;background:#fff;color:#66716c;cursor:pointer}.question-nav button.active{border-color:var(--accent,#28664f);color:var(--accent,#28664f);background:#f0f8f3}.evaluation{margin:18px 0;padding:14px;background:#f4faf6;border-left:3px solid #4b8b68;font-size:13px;line-height:1.8}.evaluation p{margin:7px 0}.inline-alert{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:10px 12px;border-radius:5px;font-size:12px;line-height:1.7;margin:0;overflow-wrap:anywhere}.inline-alert button{display:flex;align-items:center;gap:4px;flex:none;padding:0;border:0;background:none;color:inherit;cursor:pointer}.inline-alert.warning{color:#885826;background:#fff6e8}.inline-alert.success{color:#28664f;background:#edf8f1}.inline-alert.neutral{color:#53615a;background:#f2f5f3}.loading-status{margin:0;color:#66716c;font-size:12px}.action-form{display:grid;gap:13px}.budget-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.budget-grid :deep(.el-input-number){width:100%}.action-content{line-height:1.8;font-size:13px}.study-days :deep(.el-checkbox-group){display:flex;flex-wrap:wrap}.study-days :deep(.el-checkbox){margin-right:13px}.knowledge-workspace :deep(.el-button){max-width:100%;margin-left:0}.knowledge-workspace :deep(.el-select),.knowledge-workspace :deep(.el-input){min-width:0}.knowledge-workspace button:focus-visible{outline:2px solid #28664f;outline-offset:2px}
.recommendation-reason{display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden}
@media(max-width:640px){.knowledge-workspace{padding:6px 0 22px}.search-row{grid-template-columns:minmax(0,1fr) auto;gap:6px}.search-row .el-button{padding:8px 10px}.filter-grid{grid-template-columns:1fr 1fr}.topic-row{align-items:flex-start;flex-wrap:wrap}.topic-row>span{flex:1 1 calc(100% - 30px)}.topic-row>small{max-width:none;order:3;flex:1 1 100%}.learning-row{align-items:flex-start}.history-row{align-items:flex-start}.row-actions{gap:5px}.topic-next .el-button{font-size:12px;padding:8px 9px}.budget-grid{grid-template-columns:1fr}.inline-alert{align-items:flex-start}.knowledge-workspace h2{font-size:17px}.learning-filters :deep(.el-radio-button__inner){padding:7px 8px}.recommendation{align-items:flex-start}.recommendation>svg{margin-top:4px}}
</style>
