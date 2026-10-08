<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ArrowLeft, ArrowRight, ArrowUpRight, Check, Clock3, Pause, Play, RefreshCw } from 'lucide-vue-next'
import {
  confirmInterviewNextAction, createInterviewSession, createPartialInterviewReport, evaluateInterviewAnswer,
  evaluateInterviewAttempt, finishInterviewSession, getInterviewSession, listInterviewNextActions,
  listInterviewSources, listLearningPlans, pauseInterviewSession, previewInterviewNextAction,
  resumeInterviewSession, saveInterviewAttempt, saveInterviewSessionAnswer, selectInterviewAttempt,
  type InterviewActionPreview, type InterviewAnswerAttempt, type InterviewNextAction,
  type InterviewSession, type InterviewSessionReport, type InterviewSourceOption,
  type JobSummary, type LearningPlan, type ResumeSummary
} from '../../api/client'
import InterviewFeedbackPanel from './InterviewFeedbackPanel.vue'
import EvidenceContextHint from './EvidenceContextHint.vue'
import { readStudentDraft, writeStudentDraft } from './studentWorkflow'
import {
  comparableInterviewResults, firstUnansweredInterviewQuestion, interviewAttempts,
  interviewAttemptFeedback, interviewDraftQuestionIndex, interviewElapsedSeconds, interviewEvaluationCanRetry, interviewFeedbackVisible, interviewPracticeQuestions, interviewReportContinuationIndex, interviewResumeLabel, timeLabel
} from './interviewWorkflow'

const props = defineProps<{
  page: string; userId: string; sessions: InterviewSession[]; loading: boolean
  selectedSessionId?: string; resumeId?: string; jobId?: string; matchId?: string; targetRole?: string
  resumes: ResumeSummary[]; jobs: JobSummary[]
}>()
const emit = defineEmits<{ session: [session: InterviewSession] }>()
const route = useRoute()
const router = useRouter()
const form = reactive({ mode: 'COACHING' as 'COACHING' | 'MOCK', sourceType: 'JOB' as 'JOB' | 'PROJECT' | 'GAP',
  sourceId: '', resumeId: '', jobId: '', targetRole: '', questionCount: 5, timerEnabled: true, timerMinutes: 20 })
const sources = ref<InterviewSourceOption[]>([])
const sourcesLoading = ref(false)
const sourceError = ref('')
const session = ref<InterviewSession>()
const loadedId = ref('')
const error = ref('')
const unavailable = ref(false)
const busy = ref('')
const questionIndex = ref(0)
const viewingAttemptId = ref('')
const draft = ref<Record<string, string>>({})
const report = ref<InterviewSessionReport>()
const actions = ref<InterviewNextAction[]>([])
const preview = ref<InterviewActionPreview>()
const actionPlans = ref<LearningPlan[]>([])
const actionPlanId = ref('')
const clockNow = ref(Date.now())
let sourceRequest = 0
let sessionRequest = 0
let actionRequest = 0
let lastDraftOwner = ''
const resumeEdited = ref(false)
const jobEdited = ref(false)
const roleEdited = ref(false)
const clock = window.setInterval(() => { clockNow.value = Date.now() }, 1000)
onBeforeUnmount(() => { window.clearInterval(clock); sourceRequest++; sessionRequest++; actionRequest++ })

const id = computed(() => typeof route.query.sessionId === 'string' ? route.query.sessionId : props.selectedSessionId || '')
const sourceOptions = computed(() => sources.value.filter(option => option.sourceType === form.sourceType))
const source = computed(() => sourceOptions.value.find(option => option.sourceId === form.sourceId))
const resume = computed(() => props.resumes.find(item => item.resumeId === form.resumeId))
const job = computed(() => props.jobs.find(item => item.jobId === form.jobId))
const selectedMatchId = computed(() => form.resumeId === (props.resumeId || '') && form.jobId === (props.jobId || '') ? props.matchId : undefined)
const practiceQuestions = computed(() => session.value ? interviewPracticeQuestions(session.value) : [])
const question = computed(() => practiceQuestions.value[questionIndex.value])
const attempts = computed(() => session.value && question.value ? interviewAttempts(session.value, question.value.questionId) : [])
const reanswerImprovements = computed(() => {
  const initialGaps = attempts.value[0]?.evaluation?.gaps || []
  const latest = viewingAttempt.value?.evaluation
  return latest ? initialGaps.filter(gap => !(latest.gaps || []).includes(gap)) : []
})
const viewingAttempt = computed(() => attempts.value.find(attempt => attempt.attemptId === viewingAttemptId.value) || attempts.value.at(-1))
const displayFeedback = computed(() => session.value ? interviewAttemptFeedback(session.value, viewingAttempt.value) : undefined)
const attemptChoice = computed({ get: () => viewingAttempt.value?.attemptId || '', set: (value: string) => { viewingAttemptId.value = value } })
const retrying = computed(() => Boolean(question.value && draft.value[`retry:${question.value.questionId}`]))
const paused = computed(() => Boolean(session.value?.pausedAt))
const isMock = computed(() => session.value?.mode === 'MOCK')
const completed = computed(() => session.value?.status === 'COMPLETED')
const feedbackVisible = computed(() => Boolean(session.value && interviewFeedbackVisible(session.value, props.page)))
const saved = computed(() => Boolean(attempts.value.length))
const firstUnanswered = computed(() => session.value ? firstUnansweredInterviewQuestion(session.value) : 0)
const readonly = computed(() => completed.value || paused.value || Boolean(busy.value)
  || (!retrying.value && (saved.value || questionIndex.value !== firstUnanswered.value)))
const answer = computed({
  get: () => {
    const qid = question.value?.questionId
    if (!qid) return ''
    return retrying.value || !saved.value ? draft.value[qid] || '' : viewingAttempt.value?.answer || ''
  },
  set: (value: string) => {
    if (!question.value || readonly.value) return
    draft.value = { ...draft.value, [question.value.questionId]: value }
    persistDraft()
  }
})
const answeredCount = computed(() => practiceQuestions.value.filter(q => session.value?.answers.some(a => a.questionId === q.questionId && a.answer.trim())).length)
const unansweredCount = computed(() => practiceQuestions.value.length - answeredCount.value)
const elapsed = computed(() => session.value ? interviewElapsedSeconds(session.value, clockNow.value) : 0)
const timed = computed(() => isMock.value && Boolean(session.value?.timerMinutes))
const remaining = computed(() => (session.value?.timerMinutes || 0) * 60 - elapsed.value)
const timeout = computed(() => timed.value && (remaining.value <= 0 || session.value?.timeoutReached))
const comparable = computed(() => session.value ? comparableInterviewResults(session.value, props.sessions) : [])
const topGaps = computed(() => (report.value?.gaps || []).slice(0, 3))
const historyGroups = computed(() => [
  { mode: 'COACHING', label: '辅导练习', sessions: props.sessions.filter(item => item.mode !== 'MOCK') },
  { mode: 'MOCK', label: '模拟面试', sessions: props.sessions.filter(item => item.mode === 'MOCK') }
])
const retryStatus = computed(() => Boolean(session.value && interviewEvaluationCanRetry(session.value, viewingAttempt.value)))

function persistDraft() {
  const owner = props.userId.trim()
  if (!owner || !session.value) return
  // Use the current authenticated owner. During a page refresh the profile can
  // arrive after the interview session; never write a draft under an empty or
  // stale owner in that window.
  lastDraftOwner = owner
  writeStudentDraft(localStorage, owner, 'interview-practice', session.value.sessionId, draft.value)
}
function hydrateDraft(next: InterviewSession) {
  const owner = props.userId.trim()
  lastDraftOwner = owner
  draft.value = {
    ...readStudentDraft(sessionStorage, owner, 'interview', next.sessionId),
    ...readStudentDraft(localStorage, owner, 'interview-practice', next.sessionId)
  }
}
function apply(next: InterviewSession, keepQuestion = true) {
  const previous = session.value?.sessionId
  session.value = next
  loadedId.value = next.sessionId
  emit('session', next)
  if (previous !== next.sessionId || lastDraftOwner !== props.userId.trim()) hydrateDraft(next)
  if (!keepQuestion || previous !== next.sessionId) {
    questionIndex.value = interviewDraftQuestionIndex(next, draft.value)
  }
  questionIndex.value = Math.min(questionIndex.value, Math.max(0, interviewPracticeQuestions(next).length - 1))
  report.value = next.report || next.partialReport || undefined
}
function open(page: string, sessionId = session.value?.sessionId || id.value) {
  return router.push({ path: page === 'start' ? '/student/interview' : `/student/interview/${page}`,
    query: { ...(props.matchId ? { matchId: props.matchId } : {}), ...(sessionId ? { sessionId } : {}) } })
}
async function loadSources() {
  const key = ++sourceRequest
  sourcesLoading.value = true
  sourceError.value = ''
  try {
    const result = await listInterviewSources({ resumeId: form.resumeId || undefined, jobId: form.jobId || undefined, matchId: selectedMatchId.value })
    if (key !== sourceRequest) return
    sources.value = result
    if (form.sourceId && !sourceOptions.value.some(option => option.sourceId === form.sourceId)) form.sourceId = ''
    const evidenceId = typeof route.query.evidenceId === 'string' ? route.query.evidenceId : ''
    const taskId = typeof route.query.taskId === 'string' ? route.query.taskId : ''
    const contextual = result.find(option => option.sourceId.includes(evidenceId || '!missing!')
      || (taskId && option.sourceId.includes(taskId)))
    if (contextual) { form.sourceType = contextual.sourceType; form.sourceId = contextual.sourceId }
  } catch (cause) { if (key === sourceRequest) sourceError.value = message(cause, '练习资料加载失败，请重试') }
  finally { if (key === sourceRequest) sourcesLoading.value = false }
}
async function loadSession() {
  const key = ++sessionRequest
  error.value = ''
  unavailable.value = false
  if (!id.value || ['start', 'history'].includes(props.page)) return
  const requestedId = id.value
  const cached = props.sessions.find(item => item.sessionId === requestedId)
  if (cached && loadedId.value !== requestedId) apply(cached, false)
  try {
    const next = await getInterviewSession(requestedId)
    if (key !== sessionRequest || id.value !== requestedId) return
    apply(next)
    if (props.page === 'report') await loadActions(requestedId)
  } catch (cause) {
    if (key === sessionRequest) {
      session.value = undefined
      error.value = message(cause, '面试记录加载失败，请重试')
      unavailable.value = /不存在|无权|不可访问|not found|404/i.test(error.value)
    }
  }
}
async function loadActions(sessionId: string) {
  const key = ++actionRequest
  actions.value = report.value?.nextActions || []
  preview.value = undefined
  try {
    const result = await listInterviewNextActions(sessionId)
    if (key !== actionRequest || session.value?.sessionId !== sessionId) return
    actions.value = result
    const plans = await listLearningPlans()
    if (key !== actionRequest || session.value?.sessionId !== sessionId) return
    actionPlans.value = plans.filter(plan => plan.status === 'ACTIVE' && plan.targetRole === session.value?.targetRole)
    actionPlanId.value = actionPlans.value[0]?.planId || ''
  } catch (cause) { if (key === actionRequest) error.value = message(cause, '后续练习加载失败，可稍后重试') }
}
function message(cause: unknown, fallback: string) { return cause instanceof Error ? cause.message : fallback }
async function run(key: string, operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = key
  error.value = ''
  try { await operation() }
  catch (cause) { error.value = message(cause, '操作失败，已保存内容仍保留，可重试') }
  finally { busy.value = '' }
}
async function start() {
  if (!Number.isInteger(form.questionCount) || form.questionCount < 1 || form.questionCount > 8) { ElMessage.warning('请选择 1 至 8 道题'); return }
  if (form.mode === 'MOCK' && form.timerEnabled && (!Number.isInteger(form.timerMinutes) || form.timerMinutes < 5 || form.timerMinutes > 60)) { ElMessage.warning('计时需要在 5 至 60 分钟之间'); return }
  if (form.sourceType !== 'JOB' && !source.value) { ElMessage.warning('请先选择具体项目或薄弱项'); return }
  const targetRole = form.targetRole.trim() || source.value?.targetRole || job.value?.title || props.targetRole
  if (!targetRole) { ElMessage.warning('请填写目标岗位'); return }
  const path = route.fullPath
  await run('start', async () => {
    const next = await createInterviewSession({ targetRole, questionCount: form.questionCount, mode: form.mode,
      sourceType: form.sourceType, sourceId: form.sourceId || undefined,
      resumeId: source.value?.resumeId || form.resumeId || undefined,
      jobId: source.value?.jobId || form.jobId || undefined,
      matchId: source.value?.matchId || selectedMatchId.value,
      timerMinutes: form.mode === 'MOCK' ? (form.timerEnabled ? form.timerMinutes : 0) : null })
    emit('session', next)
    if (route.fullPath !== path) return
    apply(next, false)
    await open('practice', next.sessionId)
  })
}
async function refresh(sessionId: string) {
  const next = await getInterviewSession(sessionId)
  if (session.value?.sessionId === sessionId && id.value === sessionId) apply(next)
  else emit('session', next)
  return next
}
async function evaluate(attempt = viewingAttempt.value) {
  const current = session.value
  if (!current || !question.value || !attempt) return
  const qid = question.value.questionId
  const result = attempt.attemptId.startsWith('legacy:')
    ? await evaluateInterviewAnswer(current.sessionId, qid)
    : await evaluateInterviewAttempt(current.sessionId, qid, attempt.attemptId)
  await refresh(current.sessionId)
  if (result.status !== 'SUCCEEDED') throw new Error(result.error || '回答已保存，评价暂不可用，请重试')
}
async function submit() {
  const current = session.value
  const q = question.value
  const value = answer.value.trim()
  if (!current || !q || readonly.value || !value) return
  if (value.length > 8000) { ElMessage.warning('回答最多 8000 字，请精简后提交'); return }
  await run('save', async () => {
    const next = current.mode || current.attempts ? await saveInterviewAttempt(current.sessionId, q.questionId, value)
      : await saveInterviewSessionAnswer(current.sessionId, q.questionId, value)
    if (id.value !== current.sessionId) { emit('session', next); return }
    const remainingDraft = { ...draft.value }
    delete remainingDraft[q.questionId]
    delete remainingDraft[`retry:${q.questionId}`]
    draft.value = remainingDraft
    apply(next)
    viewingAttemptId.value = interviewAttempts(next, q.questionId).at(-1)?.attemptId || ''
    persistDraft()
    if (next.mode !== 'MOCK') await evaluate(interviewAttempts(next, q.questionId).at(-1))
  })
}
function reanswer() {
  if (!question.value || !viewingAttempt.value || isMock.value || completed.value || paused.value) return
  draft.value = { ...draft.value, [`retry:${question.value.questionId}`]: '1', [question.value.questionId]: viewingAttempt.value.answer }
  persistDraft()
}
function cancelReanswer() {
  if (!question.value) return
  const next = { ...draft.value }
  delete next[`retry:${question.value.questionId}`]
  delete next[question.value.questionId]
  draft.value = next
  persistDraft()
}
async function adopt() {
  const current = session.value
  const q = question.value
  const attempt = viewingAttempt.value
  if (!current || !q || !attempt || attempt.selectedForReport) return
  await run('adopt', async () => {
    const next = await selectInterviewAttempt(current.sessionId, q.questionId, attempt.attemptId)
    if (id.value === current.sessionId) apply(next)
    else emit('session', next)
  })
}
function nextQuestion() {
  if (!session.value) return
  const index = practiceQuestions.value.findIndex((q, index) => index > questionIndex.value && !session.value?.answers.some(a => a.questionId === q.questionId))
  questionIndex.value = index >= 0 ? index : Math.min(questionIndex.value + 1, practiceQuestions.value.length - 1)
}
async function togglePause() {
  const current = session.value
  if (!current || completed.value) return
  await run('pause', async () => {
    const next = paused.value ? await resumeInterviewSession(current.sessionId) : await pauseInterviewSession(current.sessionId)
    if (id.value === current.sessionId) apply(next)
    else emit('session', next)
  })
}
async function makeReport(partial: boolean) {
  const current = session.value
  if (!current) return
  const path = route.fullPath
  await run('report', async () => {
    const result = partial ? await createPartialInterviewReport(current.sessionId) : await finishInterviewSession(current.sessionId)
    const next = await refresh(current.sessionId)
    if (id.value !== current.sessionId || route.fullPath !== path) return
    apply(next)
    report.value = result
    await open('report', current.sessionId)
  })
}
async function showAction(action: InterviewNextAction) {
  const current = session.value
  if (!current) return
  await run('preview', async () => {
    const result = await previewInterviewNextAction(current.sessionId, action.actionId, actionPlanId.value || undefined)
    if (id.value === current.sessionId && props.page === 'report') preview.value = result
  })
}
async function confirmAction() {
  const current = session.value
  const value = preview.value
  if (!current || !value) return
  const path = route.fullPath
  await run('confirm', async () => {
    const result = await confirmInterviewNextAction(current.sessionId, value.previewId)
    if (route.fullPath !== path) return
    preview.value = result
    if (result.createdSessionId) await open('practice', result.createdSessionId)
    else if (result.createdPlanId) await router.push({ path: '/student/plan/review', query: { planId: result.planId || actionPlanId.value, revisionId: result.createdPlanId } })
    else if (/KNOWLEDGE|RESOURCE/.test(result.type)) await router.push({ path: '/student/knowledge', query: { q: actions.value.find(action => action.actionId === result.actionId)?.skill || result.description, ai: '0' } })
    else if (/MATERIAL|RESUME|PROJECT/.test(result.type)) await openResumeCandidate()
    else ElMessage.success('已确认后续行动')
  })
}
function reportAttempt(questionId: string) {
  return report.value?.selectedAttempts?.find(attempt => attempt.questionId === questionId)
    || (session.value ? interviewAttempts(session.value, questionId).find(attempt => attempt.selectedForReport) : undefined)
}
async function openResumeCandidate(questionId?: string) {
  const current = session.value
  if (!current) return
  const qid = questionId || current.questions.find(q => !q.followUp && reportAttempt(q.questionId))?.questionId
  const attempt = qid ? reportAttempt(qid) : undefined
  if (!qid || !attempt) { ElMessage.warning('请先保存一份可以补充项目材料的回答'); return }
  await router.push({ path: '/student/resume/profile', query: { interviewSessionId: current.sessionId, questionId: qid, attemptId: attempt.attemptId } })
}
async function continueAfterReport() {
  const current = session.value
  if (!current) return
  if (!completed.value && paused.value) {
    await run('resume', async () => {
      const next = await resumeInterviewSession(current.sessionId)
      if (id.value !== current.sessionId) { emit('session', next); return }
      apply(next)
      questionIndex.value = interviewReportContinuationIndex(next, draft.value, questionIndex.value)
      await open('practice')
    })
  } else {
    questionIndex.value = interviewReportContinuationIndex(current, draft.value, questionIndex.value)
    await open('practice')
  }
}
const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
const modeLabel = (value?: string) => value === 'MOCK' ? '模拟面试' : '辅导练习'
const actionLabel = (value: string) => ({ PRACTICE: '再练一题', KNOWLEDGE: '查看资料', MATERIAL: '补充材料', LEARNING: '调整计划' }[value] || '查看安排')
const gapActions = (gap: string) => actions.value.filter(action => action.skill === gap || action.description === gap)
const attemptComparison = (questionId: string) => report.value?.attemptComparisons?.find(item => item.questionId === questionId)
const reportFollowUps = (questionId: string) => session.value?.questions.filter(item => item.followUp && item.mainQuestionId === questionId) || []
function historyCompletion(item: InterviewSession) {
  if (item.status === 'COMPLETED' && item.report?.completionScope) return item.report.completionScope
  const questions = interviewPracticeQuestions(item)
  return `${questions.filter(q => item.answers.some(a => a.questionId === q.questionId && a.answer.trim())).length}/${questions.length}`
}
const answered = (qid: string) => session.value?.answers.some(item => item.questionId === qid && item.answer.trim())

watch(() => [props.resumeId, props.jobId, props.targetRole], () => {
  if (!resumeEdited.value) form.resumeId = props.resumeId || ''
  if (!jobEdited.value) form.jobId = props.jobId || ''
  if (!roleEdited.value) form.targetRole = props.targetRole || ''
}, { immediate: true })
watch(() => [form.resumeId, form.jobId, props.matchId, props.userId], () => { if (props.userId) void loadSources() }, { immediate: true })
watch(() => form.sourceType, () => { if (!sourceOptions.value.some(item => item.sourceId === form.sourceId)) form.sourceId = '' })
watch(() => form.sourceId, () => { if (source.value?.targetRole) form.targetRole = source.value.targetRole })
watch(() => form.jobId, () => { if (job.value) form.targetRole = job.value.title })
watch(() => [id.value, props.page, props.userId], () => { void loadSession() }, { immediate: true })
watch(() => props.userId, (value, previous) => {
  const owner = value.trim()
  if (!owner) {
    lastDraftOwner = ''
    return
  }
  // The session request and the profile request resolve independently. If the
  // session won the race, reload the draft as soon as the real owner is known.
  if (owner !== previous?.trim() && session.value) {
    hydrateDraft(session.value)
    questionIndex.value = Math.min(
      interviewDraftQuestionIndex(session.value, draft.value),
      Math.max(0, interviewPracticeQuestions(session.value).length - 1)
    )
  }
}, { immediate: true })
watch(questionIndex, () => { viewingAttemptId.value = ''; error.value = '' })
</script>

<template>
  <div class="interview-module">
    <el-alert v-if="error" class="operation-error" type="warning" :closable="false" show-icon :title="error" />
    <div v-if="unavailable" class="empty" data-testid="interview-unavailable"><p>这次面试已不存在或不可访问。</p><RouterLink to="/student/interview/history">查看面试历史</RouterLink></div>
    <section v-if="page === 'start'" class="launch" data-testid="interview-start" v-loading="loading">
      <div v-if="sessions[0]" class="continue-row"><div><strong>{{ sessions[0].targetRole }}</strong><span>{{ modeLabel(sessions[0].mode) }} · {{ sessions[0].status === 'COMPLETED' ? '已完成' : '可继续' }}</span></div><el-button @click="open(sessions[0].status === 'COMPLETED' ? 'report' : 'practice', sessions[0].sessionId)">{{ sessions[0].status === 'COMPLETED' ? '查看报告' : '继续答题' }}<ArrowUpRight :size="15" /></el-button></div>
      <div class="creation-form">
        <label class="form-field"><span>练习模式</span><el-radio-group v-model="form.mode" data-testid="interview-mode" aria-label="练习模式"><el-radio-button value="COACHING">辅导练习</el-radio-button><el-radio-button value="MOCK">模拟面试</el-radio-button></el-radio-group></label>
        <label class="form-field"><span>练习内容</span><el-radio-group v-model="form.sourceType" data-testid="interview-source-type" aria-label="练习内容"><el-radio-button value="JOB">岗位</el-radio-button><el-radio-button value="PROJECT">项目</el-radio-button><el-radio-button value="GAP">薄弱项</el-radio-button></el-radio-group></label>
        <div class="selection-row">
          <label class="form-field"><span>使用的简历</span><el-select v-model="form.resumeId" clearable placeholder="不使用简历" aria-label="使用的简历" :title="resume?.fileName" @change="resumeEdited = true"><el-option v-for="item in resumes" :key="item.resumeId" :value="item.resumeId" :label="interviewResumeLabel(item)" :title="item.fileName" /></el-select></label>
          <label v-if="form.sourceType === 'JOB'" class="form-field"><span>目标岗位</span><el-select v-model="form.jobId" clearable filterable placeholder="通用岗位练习" aria-label="目标岗位" @change="jobEdited = true"><el-option v-for="item in jobs" :key="item.jobId" :value="item.jobId" :label="`${item.title} · ${item.companyName}`" /></el-select></label>
          <label v-else class="form-field"><span>{{ form.sourceType === 'PROJECT' ? '选择项目或已确认成果' : '选择薄弱项' }}</span><el-select v-model="form.sourceId" data-testid="interview-source-picker" :loading="sourcesLoading" filterable :placeholder="sourceOptions.length ? '选择一项' : '暂无可用资料'" aria-label="练习资料"><el-option v-for="item in sourceOptions" :key="item.sourceId" :value="item.sourceId" :label="item.label" /></el-select></label>
        </div>
        <p v-if="sourceError" class="inline-error">{{ sourceError }} <el-button link @click="loadSources"><RefreshCw :size="14" />重试</el-button></p>
        <details v-if="source" class="source-detail"><summary>资料依据 · {{ source.label }}</summary><p class="source-note">{{ source.description }}</p></details>
        <div class="selection-row compact-row">
          <label class="form-field role-field"><span>本次目标岗位</span><el-input v-model="form.targetRole" maxlength="120" placeholder="例如 Java 后端实习生" aria-label="本次目标岗位" @input="roleEdited = true" /></label>
          <label class="form-field count-field"><span>题目数量</span><el-input-number v-model="form.questionCount" :min="1" :max="8" controls-position="right" aria-label="面试题数" /></label>
          <label v-if="form.mode === 'MOCK'" class="form-field timer-field"><span>计时</span><div class="timer-options"><el-switch v-model="form.timerEnabled" data-testid="interview-timer-enabled" aria-label="启用计时" /><el-input-number v-if="form.timerEnabled" v-model="form.timerMinutes" data-testid="interview-timer-minutes" :min="5" :max="60" controls-position="right" aria-label="计时分钟数" /><span>{{ form.timerEnabled ? '分钟' : '关闭' }}</span></div></label>
        </div>
        <div class="launch-footer"><span :title="resume?.fileName">{{ resume ? interviewResumeLabel(resume) : '未选择简历' }} · {{ source?.label || job?.title || '通用岗位建议' }}</span><el-button type="primary" data-testid="interview-start-button" :loading="busy === 'start'" :disabled="Boolean(busy) || sourcesLoading || (form.sourceType !== 'JOB' && !source)" @click="start"><Play :size="15" />{{ form.mode === 'MOCK' ? '开始模拟面试' : '开始辅导练习' }}</el-button></div>
      </div>
    </section>

    <section v-else-if="page === 'history'" class="history" data-testid="interview-history" v-loading="loading">
      <p v-if="!sessions.length" class="empty">暂无面试记录。</p>
      <template v-for="group in historyGroups" :key="group.mode"><template v-if="group.sessions.length"><h3>{{ group.label }}</h3><div class="record-list"><button v-for="item in group.sessions" :key="item.sessionId" class="record" :data-session-id="item.sessionId" @click="open(item.status === 'COMPLETED' ? 'report' : 'practice', item.sessionId)"><div><strong>{{ item.sourceLabel || item.targetRole }}</strong><span>{{ item.status === 'COMPLETED' ? '已完成' : item.partialReport ? '未完成，可继续' : item.pausedAt ? '已暂停' : '进行中' }} · {{ historyCompletion(item) }} 题<span v-if="item.report"> · {{ item.report.overallScore }} 分</span><span v-if="item.timeoutReached"> · 已超时</span> · {{ formatDate(item.updatedAt) }}</span></div><ArrowUpRight :size="17" /></button></div></template></template>
    </section>

    <section v-else-if="page === 'practice'" class="practice" data-testid="interview-practice" v-loading="loading">
      <template v-if="session && question">
        <div class="practice-topline"><span>{{ modeLabel(session.mode) }} · {{ answeredCount }}/{{ practiceQuestions.length }} 题</span><div class="timer" :class="{ overdue: timeout }" data-testid="interview-timer"><Clock3 :size="15" /><span>{{ timed ? `${timeout ? '已超时 ' : '剩余 '}${timeLabel(Math.abs(remaining))}` : `已用 ${timeLabel(elapsed)}` }}</span><span v-if="paused">已暂停</span><el-button v-if="!completed" :data-testid="paused ? 'interview-resume' : 'interview-pause'" size="small" :disabled="Boolean(busy)" @click="togglePause"><Play v-if="paused" :size="14" /><Pause v-else :size="14" />{{ paused ? '继续' : '暂停' }}</el-button></div></div>
        <p v-if="timeout" class="timeout-note">已到计划时间，可以继续回答。</p>
        <div class="question-nav" aria-label="面试题目"><button v-for="(q, index) in practiceQuestions" :key="q.questionId" :class="{ active: index === questionIndex, answered: answered(q.questionId) }" :aria-label="`${q.followUp ? '追问' : '第'} ${index + 1} 题`" :aria-current="index === questionIndex ? 'step' : undefined" @click="questionIndex = index">{{ index + 1 }}<Check v-if="answered(q.questionId)" :size="12" /></button></div>
        <div class="question-content"><div class="question-meta"><span>{{ question.followUp ? '追问' : `问题 ${questionIndex + 1}` }}</span><span>{{ question.category || '综合' }} · {{ question.difficulty || '普通' }}</span><el-tag v-if="session.mocked" type="warning" size="small">通用建议</el-tag></div><p class="question-text">{{ question.question }}</p><p v-if="session.sourceLabel" class="source-note">{{ session.sourceLabel }}</p><details v-if="question.sourceReferences?.length" class="source-detail"><summary>题目依据</summary><blockquote v-for="(reference, index) in question.sourceReferences" :key="index"><p>{{ reference.quote }}</p><small>{{ reference.location }}</small></blockquote></details><details v-if="feedbackVisible && question.referencePoints?.length" class="reference-points" data-testid="interview-reference"><summary>答题参考</summary><ul><li v-for="point in question.referencePoints" :key="point">{{ point }}</li></ul></details></div>
        <div class="answer-heading"><label for="interview-answer">我的回答</label><el-select v-if="attempts.length > 1 && feedbackVisible" v-model="attemptChoice" class="attempt-picker" aria-label="回答版本"><el-option v-for="attempt in attempts" :key="attempt.attemptId" :value="attempt.attemptId" :label="`第 ${attempt.attemptNo} 次${attempt.selectedForReport ? ' · 报告采用' : ''}`" /></el-select><span v-else-if="saved" class="saved-note"><Check :size="14" />已保存</span></div>
        <el-input id="interview-answer" v-model="answer" type="textarea" :rows="readonly ? 6 : 10" :readonly="readonly" :maxlength="8000" :show-word-limit="!readonly" :placeholder="paused ? '面试已暂停' : readonly ? '已保存的回答' : '输入你的回答'" />
        <div v-if="feedbackVisible && saved && !retrying" class="attempt-actions"><el-button v-if="!completed && !paused && !isMock" data-testid="interview-reanswer" :disabled="Boolean(busy)" @click="reanswer"><RefreshCw :size="14" />重新回答</el-button><el-button v-if="viewingAttempt && !viewingAttempt.selectedForReport && !completed" data-testid="interview-adopt-attempt" :disabled="Boolean(busy) || paused || viewingAttempt.evaluationStatus !== 'SUCCEEDED'" :loading="busy === 'adopt'" @click="adopt"><Check :size="14" />采用这次回答</el-button><span v-if="viewingAttempt?.selectedForReport" class="saved-note">报告采用此回答</span><el-button v-if="retryStatus && !isMock" data-testid="interview-evaluate-attempt" :disabled="Boolean(busy)" :loading="busy === 'evaluate'" @click="run('evaluate', () => evaluate())">评价重试</el-button></div>
        <p v-if="feedbackVisible && viewingAttempt?.evaluationError" class="inline-error">{{ viewingAttempt.evaluationError }}</p>
        <details v-if="feedbackVisible && displayFeedback && !retrying" class="question-feedback" open><summary>本题反馈</summary><InterviewFeedbackPanel :feedback="displayFeedback" /><details v-if="displayFeedback.followUpQuestion && !isMock"><summary>针对性追问</summary><p>{{ displayFeedback.followUpQuestion }}</p></details></details>
        <details v-if="feedbackVisible && attempts.length > 1" class="attempt-comparison"><summary>重答前后对比</summary><div class="comparison-grid"><article><strong>首次回答 · {{ attempts[0].evaluation?.score ?? '待评价' }}</strong><p>{{ attempts[0].answer }}</p></article><article><strong>第 {{ viewingAttempt?.attemptNo }} 次 · {{ viewingAttempt?.evaluation?.score ?? '待评价' }}</strong><p>{{ viewingAttempt?.answer }}</p><p v-if="reanswerImprovements.length">已补充：</p><ul><li v-for="item in reanswerImprovements" :key="item">{{ item }}</li></ul><p v-if="viewingAttempt?.evaluation?.gaps?.length">仍需改善：</p><ul><li v-for="gap in viewingAttempt?.evaluation?.gaps || []" :key="gap">{{ gap }}</li></ul></article></div><small>同题重答后的改善需要通过新题再验证。</small></details>
        <div v-if="!completed" class="answer-actions"><el-button v-if="!saved || retrying" type="primary" data-testid="interview-save-answer" :disabled="readonly || !answer.trim()" :loading="busy === 'save'" @click="submit">{{ retrying ? '提交这次回答' : '保存回答' }}</el-button><el-button v-if="retrying" :disabled="Boolean(busy)" @click="cancelReanswer">取消重答</el-button><el-button v-if="saved && questionIndex < practiceQuestions.length - 1" data-testid="interview-next-question" :disabled="Boolean(busy) || paused || retrying" @click="nextQuestion">继续下一题<ArrowRight :size="14" /></el-button><el-button v-if="unansweredCount === 0" type="primary" data-testid="interview-final-report" :disabled="Boolean(busy) || retrying" :loading="busy === 'report'" @click="makeReport(false)">完成并生成报告</el-button><el-button data-testid="interview-partial-report" :disabled="Boolean(busy) || !answeredCount || retrying" :loading="busy === 'report'" @click="makeReport(true)">阶段复盘</el-button></div>
        <el-button v-else @click="open('report')">查看报告<ArrowUpRight :size="15" /></el-button>
      </template>
      <div v-else-if="!loading && !unavailable" class="empty"><p>{{ error ? '暂时无法读取这次面试。' : '暂无可答题的面试。' }}</p><el-button v-if="error" @click="loadSession"><RefreshCw :size="14" />重试</el-button><el-button v-else @click="open('start', '')">开始练习</el-button></div>
    </section>

    <section v-else-if="page === 'report'" class="report" data-testid="interview-report" v-loading="loading">
      <template v-if="session && report">
        <div class="report-header"><div><span>{{ report.reportType === 'PARTIAL' ? '阶段复盘' : '面试报告' }} · {{ modeLabel(session.mode) }}</span><strong>{{ session.targetRole }}</strong><small>{{ report.completionScope || `${answeredCount}/${session.questions.length} 题已回答` }}<span v-if="session.timeoutReached"> · 已超时</span></small><EvidenceContextHint :value="session" compact /></div><span class="report-score">{{ report.overallScore }}<small>分</small></span></div>
        <div class="report-tools"><el-button data-testid="interview-view-answers" :disabled="Boolean(busy)" @click="continueAfterReport"><ArrowLeft :size="14" />{{ completed ? '查看回答' : '继续答题' }}</el-button><el-tag v-if="report.mocked" type="warning">演示评价</el-tag></div>
        <details v-if="report.reportType === 'PARTIAL' && report.unansweredQuestionIds?.length" class="report-details" data-testid="interview-unanswered"><summary>未回答 · {{ report.unansweredQuestionIds.length }} 题</summary><div v-for="qid in report.unansweredQuestionIds" :key="qid" class="report-attempt"><p>{{ session.questions.find(q => q.questionId === qid)?.question || '历史题目未保存' }}</p><small>{{ session.sourceLabel || '通用岗位练习' }}</small><blockquote v-for="(reference, index) in session.questions.find(q => q.questionId === qid)?.sourceReferences || []" :key="index"><p>{{ reference.quote }}</p><small>{{ reference.location }}</small></blockquote></div></details>
        <div v-if="topGaps.length" class="priority-gaps"><h3>优先改进</h3><div class="action-list"><div v-for="(gap, index) in topGaps" :key="gap" class="gap-item"><p>{{ index + 1 }}. {{ gap }}</p><div class="gap-action-buttons"><el-button v-for="action in gapActions(gap)" :key="action.actionId" data-testid="interview-action-preview" :data-action-type="action.type" size="small" :disabled="Boolean(busy) || (report.reportType === 'PARTIAL' && ['PRACTICE', 'LEARNING'].includes(action.type)) || (action.type === 'LEARNING' && !actionPlanId)" @click="showAction(action)">{{ actionLabel(action.type) }}<ArrowUpRight :size="13" /></el-button></div></div></div><details v-if="actionPlans.length" class="action-plan"><summary>选择用于调整的学习计划</summary><el-select v-model="actionPlanId" aria-label="用于调整的学习计划"><el-option v-for="plan in actionPlans" :key="plan.planId" :value="plan.planId" :label="`${plan.targetRole} · 第 ${plan.version} 版`" /></el-select></details></div>
        <div v-if="preview" class="action-preview" data-testid="interview-action-preview-detail"><strong>{{ preview.title }}</strong><p>{{ preview.description }}</p><p>{{ preview.impact }}</p><small v-if="preview.estimatedMinutes">预计 {{ preview.estimatedMinutes }} 分钟</small><div class="preview-buttons"><el-button type="primary" data-testid="interview-action-confirm" :disabled="Boolean(busy) || preview.status === 'CONFIRMED'" :loading="busy === 'confirm'" @click="confirmAction">{{ preview.status === 'CONFIRMED' ? '已确认' : '确认这个安排' }}</el-button><el-button @click="preview = undefined">收起</el-button></div></div>
        <div class="question-reports"><details v-for="(feedback, index) in report.questionFeedback || []" :key="feedback.questionId"><summary>第 {{ index + 1 }} 题 · {{ feedback.score ?? '待评价' }} 分</summary><p class="report-question">{{ session.questions.find(q => q.questionId === feedback.questionId)?.question }}</p><InterviewFeedbackPanel :feedback="feedback" /><details v-if="reportAttempt(feedback.questionId)" class="report-answer"><summary>采用的回答</summary><p>{{ reportAttempt(feedback.questionId)?.answer }}</p><small>{{ reportAttempt(feedback.questionId)?.selectionReason || '首次回答自动采用' }}<span v-if="reportAttempt(feedback.questionId)?.selectedAt"> · {{ formatDate(reportAttempt(feedback.questionId)!.selectedAt!) }}</span></small></details><details v-if="interviewAttempts(session, feedback.questionId).length > 1"><summary>回答变化</summary><p v-if="attemptComparison(feedback.questionId)?.note">{{ attemptComparison(feedback.questionId)?.note }}</p><p v-if="attemptComparison(feedback.questionId)?.improvements?.length">已补充：{{ attemptComparison(feedback.questionId)?.improvements?.join('；') }}</p><p v-if="attemptComparison(feedback.questionId)?.remainingGaps?.length">仍需改善：{{ attemptComparison(feedback.questionId)?.remainingGaps?.join('；') }}</p><div v-for="attempt in interviewAttempts(session, feedback.questionId)" :key="attempt.attemptId" class="report-attempt"><strong>第 {{ attempt.attemptNo }} 次 · {{ attempt.evaluation?.score ?? '待评价' }}<span v-if="attempt.selectedForReport"> · 报告采用</span></strong><p>{{ attempt.answer }}</p><ul><li v-for="gap in attempt.evaluation?.gaps || []" :key="gap">{{ gap }}</li></ul></div></details><el-button v-if="reportAttempt(feedback.questionId)" link @click="openResumeCandidate(feedback.questionId)">补充简历材料<ArrowUpRight :size="14" /></el-button></details></div>
        <details v-if="isMock && session.questions.some(item => item.followUp)" class="report-details" data-testid="interview-report-follow-ups"><summary>针对性追问</summary><template v-for="(main, index) in practiceQuestions" :key="main.questionId"><div v-if="reportFollowUps(main.questionId).length" class="report-attempt"><strong>第 {{ index + 1 }} 题</strong><p>{{ main.question }}</p><blockquote v-for="followUp in reportFollowUps(main.questionId)" :key="followUp.questionId"><p>{{ followUp.question }}</p></blockquote></div></template></details>
        <details class="report-details"><summary>完整建议与历史比较</summary><h4 v-if="report.strengths?.length">优势</h4><ul><li v-for="item in report.strengths || []" :key="item">{{ item }}</li></ul><h4 v-if="report.recommendations?.length">改善建议</h4><ul><li v-for="item in report.recommendations || []" :key="item">{{ item }}</li></ul><p>{{ report.comparisonNote || '历史记录缺少评价版本，暂不比较分数。' }}</p><p v-if="report.difficultyNote">{{ report.difficultyNote }}</p><div v-for="item in comparable" :key="item.sessionId">{{ formatDate(item.updatedAt) }} · {{ item.report?.overallScore }} 分</div><small v-if="report.rubricVersion">评价标准 {{ report.rubricVersion }}</small></details>
        <details v-if="session.partialReports?.length" class="report-details" data-testid="interview-partial-history"><summary>阶段复盘记录 · {{ session.partialReports.length }} 次</summary><details v-for="(historical, index) in session.partialReports" :key="`${index}:${historical.generatedAt}`" class="report-attempt"><summary>{{ formatDate(historical.generatedAt) }} · {{ historical.completionScope }} 题 · {{ historical.overallScore }} 分</summary><ul><li v-for="gap in historical.gaps || []" :key="gap">{{ gap }}</li></ul><details v-for="attempt in historical.selectedAttempts || []" :key="attempt.attemptId"><summary>第 {{ attempt.attemptNo }} 次回答 · {{ attempt.evaluation?.score ?? '待评价' }} 分</summary><p>{{ attempt.answer }}</p><small>{{ attempt.selectionReason || '首次回答自动采用' }}<span v-if="attempt.selectedAt"> · {{ formatDate(attempt.selectedAt) }}</span></small></details></details></details>
      </template>
      <div v-else-if="!loading && !unavailable" class="empty"><p>{{ error ? '暂时无法读取这次面试。' : '这次面试还没有报告。' }}</p><el-button v-if="error" @click="loadSession"><RefreshCw :size="14" />重试</el-button><el-button v-else @click="open('practice')">继续答题</el-button></div>
    </section>
  </div>
</template>

<style scoped>
.interview-module{min-width:0;color:#26392e}.launch,.history,.practice,.report{padding:20px 0;min-width:0}.operation-error{margin-bottom:12px}.creation-form{max-width:880px;display:grid;gap:20px}.continue-row{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:0 0 16px;margin-bottom:20px;border-bottom:1px solid #dfe8e2}.continue-row div{display:grid;gap:5px}.continue-row span,.launch-footer span,.source-note,.saved-note,small{font-size:13px;color:#64756a;overflow-wrap:anywhere}.form-field{display:grid;gap:8px;min-width:0}.form-field>span{font-size:13px;color:#55665b}.selection-row{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.compact-row{grid-template-columns:minmax(180px,1fr) 120px minmax(0,260px)}.timer-options{display:flex;align-items:center;gap:8px}.timer-options :deep(.el-input-number){width:110px}.timer-options>span{white-space:nowrap;font-size:13px}.form-field :deep(.el-select){width:100%}.count-field :deep(.el-input-number){width:100%}.source-note{margin:0;line-height:1.8}.launch-footer{display:flex;justify-content:space-between;align-items:center;gap:12px;border-top:1px solid #dfe8e2;padding-top:16px}.inline-error,.timeout-note{color:#98612b;font-size:13px;line-height:1.7}.history h3,.next-actions h3,.priority-gaps h3{font-size:15px;margin:0 0 12px}.history h3:not(:first-child){margin-top:24px}.record-list{border-top:1px solid #e0e8e3}.record{display:flex;justify-content:space-between;align-items:center;gap:12px;text-align:left;width:100%;padding:14px 0;background:transparent;color:inherit;border:0;border-bottom:1px solid #e0e8e3;cursor:pointer}.record:hover{background:#f3f8f5}.record div{display:grid;gap:6px;min-width:0}.record strong{font-size:14px;overflow-wrap:anywhere}.record span{font-size:12px;color:#63736a;line-height:1.8}.record svg{flex-shrink:0}.practice{max-width:1040px}.practice-topline{display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap;font-size:13px;color:#5b6e61}.timer{display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-variant-numeric:tabular-nums}.timer.overdue{color:#98612b}.question-nav{display:flex;gap:6px;flex-wrap:wrap;margin:18px 0}.question-nav button{display:flex;align-items:center;justify-content:center;gap:2px;min-width:36px;height:32px;padding:0 5px;border:1px solid #dce6df;border-radius:4px;background:transparent;cursor:pointer;color:#627468;font-variant-numeric:tabular-nums}.question-nav button.active{background:#e4f1e9;color:#256344;border-color:#84b79a}.question-nav button.answered{font-weight:600}.question-content{padding:12px 0 18px;border-bottom:1px solid #e0e8e3;margin-bottom:18px}.question-meta{display:flex;align-items:center;gap:10px;flex-wrap:wrap;color:#718177;font-size:12px}.question-text{font-size:18px;line-height:1.8;margin:10px 0;overflow-wrap:anywhere;white-space:pre-wrap}.source-detail,.reference-points,.attempt-comparison{margin-top:12px;color:#52665a;font-size:13px}.source-detail blockquote{margin:12px 0;padding-left:12px;border-left:2px solid #abc4b3}.source-detail p{white-space:pre-wrap;overflow-wrap:anywhere}.reference-points ul{line-height:1.9;padding-left:20px}.answer-heading{display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:10px;font-size:14px}.attempt-picker{max-width:190px}.saved-note{display:flex;align-items:center;gap:4px}.attempt-actions,.answer-actions,.report-tools,.preview-buttons{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:12px 0}.answer-actions :deep(.el-button),.attempt-actions :deep(.el-button),.preview-buttons :deep(.el-button){margin-left:0}.question-feedback{font-size:13px;margin:16px 0}.question-feedback :deep(.feedback-panel){background:transparent;padding:0;border-radius:0}.question-feedback :deep(.dimensions article){border:0;border-bottom:1px solid #e0e8e3;border-radius:0;padding:10px 0}.comparison-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin:12px 0}.comparison-grid p,.report-attempt p,.report-answer p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.8;font-size:13px}.report-header{display:flex;justify-content:space-between;align-items:center;gap:16px;border-bottom:1px solid #dfe8e2;padding-bottom:18px}.report-header>div{display:grid;gap:6px}.report-header>div>span{font-size:13px;color:#637568}.report-header strong{font-size:19px;overflow-wrap:anywhere}.report-score{font-size:32px;white-space:nowrap;color:#286447;font-variant-numeric:tabular-nums}.report-score small{margin-left:4px}.priority-gaps,.next-actions{margin:24px 0}.priority-gaps ol{padding-left:22px;line-height:1.9;font-size:14px}.action-list{border-top:1px solid #dfe8e2}.action-item{display:flex;align-items:center;justify-content:space-between;gap:16px;border-bottom:1px solid #dfe8e2;padding:14px 0}.action-item>div{min-width:0}.action-item strong{font-size:14px;overflow-wrap:anywhere}.action-item p,.action-preview p{color:#65776a;font-size:13px;line-height:1.8;margin:5px 0;overflow-wrap:anywhere}.action-item :deep(.el-button){flex-shrink:0}.action-plan{max-width:400px;margin-top:14px}.action-preview{border-left:3px solid #7fa88e;background:#f3f8f4;padding:14px;margin-bottom:20px}.question-reports>details,.report-details{padding:15px 0;border-top:1px solid #dfe8e2;font-size:14px}.report-question{line-height:1.8;overflow-wrap:anywhere}.question-reports details details{margin:12px 0}.question-reports :deep(.feedback-panel){border-radius:4px}.report-details{color:#64776a;line-height:1.8}.report-details h4{font-size:14px}.report-details ul{padding-left:20px}.empty{padding:12px 0;color:#69796f}summary{cursor:pointer}button:focus-visible,summary:focus-visible{outline:2px solid #75a888;outline-offset:3px}
@media(max-width:760px){.compact-row{grid-template-columns:minmax(0,1fr) 110px}.timer-field{grid-column:1/-1}.selection-row{gap:12px}.question-text{font-size:16px}.comparison-grid{grid-template-columns:1fr}.launch-footer{align-items:flex-start;flex-direction:column}.continue-row{flex-wrap:wrap}.action-item{align-items:flex-start;gap:10px}.action-item :deep(.el-button){padding:8px}.report-header strong{font-size:17px}}
@media(max-width:430px){.selection-row:not(.compact-row){grid-template-columns:1fr}.creation-form{gap:16px}.launch,.history,.practice,.report{padding:12px 0}.answer-heading{flex-wrap:wrap}.action-item{flex-direction:column}.report-score{font-size:28px}.form-field :deep(.el-radio-button__inner){padding:9px 12px}.practice-topline{align-items:flex-start}.timer{gap:5px}.record{padding:12px 0}}
.gap-item{padding:14px 0;border-bottom:1px solid #dfe8e2}.gap-item>p{margin:0 0 12px;line-height:1.8;font-size:14px;overflow-wrap:anywhere}.gap-action-buttons{display:flex;flex-wrap:wrap;gap:7px}.gap-action-buttons :deep(.el-button){margin-left:0}.action-plan :deep(.el-select){margin-top:10px;width:100%}
</style>
