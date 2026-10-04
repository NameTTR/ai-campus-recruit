<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import MarkdownIt from 'markdown-it'
import {
  ArrowUpRight,
  Bot,
  BrainCircuit,
  BriefcaseBusiness,
  CalendarDays,
  CheckCircle2,
  CircleDashed,
  Clock3,
  Compass,
  FileText,
  GraduationCap,
  Library,
  MapPin,
  PencilLine,
  RefreshCw,
  Route,
  Search,
  Sparkles,
  Target,
  TrendingUp,
  Upload
} from 'lucide-vue-next'
import {
  confirmLearningPlan,
  compareResumeJobs,
  evaluateInterviewAnswer,
  analyzeResume,
  answerKnowledgeBase,
  createInterviewSession,
  createLearningPlan,
  deleteResume,
  finishInterviewSession,
  getProfile,
  getInterviewSession,
  getLearningReminders,
  getLearningToday,
  getLearningWeeklyReview,
  getLearningPlan,
  listInterviewSessions,
  listJobs,
  listLearningPlans,
  listLearningPlanVersions,
  listMyMatches,
  listResumeDiagnoses,
  listResumes,
  matchResumeJob,
  replanLearningPlan,
  submitLearningWeeklyReview,
  rewriteResume,
  saveInterviewSessionAnswer,
  updateLearningTask,
  updateResumeProfile,
  uploadResume,
  type InterviewSession,
  type InterviewSessionReport,
  type AiSearchResponse,
  type JobSummary,
  type KnowledgeAnswerResponse,
  type LearningPlan,
  type LearningPlanSchedule,
  type LearningEvidence,
  type LearningTodayResponse,
  type LearningWeeklyReview,
  type MatchResult,
  type ResumeCompareResult,
  type ResumeDiagnosis,
  type ResumeSummary,
  type ResumeRewriteResponse,
  type UserProfile
} from '../api/client'
import {
  appendRecentQuery,
  filterJobs,
  latestMatchForPair,
  matchUsesCurrentSkills,
  mergeAnswerDrafts,
  planStatusLabel,
  readStudentDraft,
  resumeProfileIsDirty,
  resumeSummaryFromProfile,
  splitProfileLines,
  unfinishedQuestionCount,
  validateResumeFile,
  writeStudentDraft
} from '../features/student/studentWorkflow'

import ResumeEvidencePanel from '../features/student/ResumeEvidencePanel.vue'
import ResumeBuilderPanel from '../features/student/ResumeBuilderPanel.vue'
import MatchEvidencePanel from '../features/student/MatchEvidencePanel.vue'
import LearningEvidenceForm from '../features/student/LearningEvidenceForm.vue'
import InterviewFeedbackPanel from '../features/student/InterviewFeedbackPanel.vue'
import {
  citationLocation, comparableInterviewSessions, evaluationCanRetry,
  firstActionableQuestionIndex, matchContextIsCurrent, retrievalFromAnswer, retrievalModeLabel
} from '../features/student/coreDeepening'

const route = useRoute()
const router = useRouter()
const markdown = new MarkdownIt({ breaks: true, linkify: true })
const activeModule = computed(() => typeof route.params.module === 'string' ? route.params.module : 'resume')
const profile = ref<UserProfile>()
const targetRole = ref('')
const resumes = ref<ResumeSummary[]>([])
const selectedResumeId = ref('')
const diagnoses = ref<ResumeDiagnosis[]>([])
const diagnosisJobId = ref('')
const resumeRewrite = ref<ResumeRewriteResponse>()
const resumeLoading = ref(false)
const resumeActionLoading = ref(false)
let resumeDiagnosisRequest = 0
let resumeFormId = ''
let hydratingResume = false
const resumeForm = reactive({
  education: '',
  skills: '',
  projects: '',
  targetJob: ''
})

const jobs = ref<JobSummary[]>([])
const matches = ref<MatchResult[]>([])
const selectedJobId = ref('')
const selectedCompareJobIds = ref<string[]>([])
const compareResult = ref<ResumeCompareResult>()
const compareLoading = ref(false)
const currentMatch = ref<MatchResult>()
const jobsLoading = ref(false)
const matchLoading = ref(false)
const jobSearch = ref('')
const jobCityFilter = ref('')
const jobSkillFilter = ref('')

const plans = ref<LearningPlan[]>([])
const selectedPlanId = ref('')
// Keep the first visit focused on the plan itself. The full builder is still
// available through the compact settings bar whenever students need it.
const planBuilderOpen = ref(false)
let planBuilderInitialized = false
const planVersions = ref<LearningPlan[]>([])
const planLoading = ref(false)
const planActionLoading = ref(false)
const replanPreview = ref<LearningPlan>()
const replanPreviewSourceId = ref('')
const replanPreviewOpen = ref(false)
const planForm = reactive({
  targetRole: '',
  weeklyHours: 6,
  durationWeeks: 8,
  replanReason: '',
  startDate: new Date().toISOString().slice(0, 10),
  reminderTime: '20:00',
  studyDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  dailyMinutesCap: 72
})
type TaskScheduleMeta = {
  status?: 'PAUSED' | 'DEFERRED'
  scheduledDate?: string
  dueDate?: string
  reminderAt?: string
  snoozedUntil?: string
  startedAt?: string
  pausedAt?: string
  completedAt?: string
  actualMinutes?: number
}
type LocalPlanSchedule = LearningPlanSchedule & { weeklyReview?: string }
const planSchedules = ref<Record<string, LocalPlanSchedule>>({})
const taskScheduleMeta = ref<Record<string, TaskScheduleMeta>>({})
const weeklyReviewDraft = ref('')
const weeklyReviewSaved = ref(false)
const todayPlanData = ref<LearningTodayResponse>()
const weeklyReviewData = ref<LearningWeeklyReview>()
const taskFeedback = ref<Record<string, string>>({})
const taskDraftsByPlan = new Map<string, Record<string, string>>()
let taskFeedbackPlanId = ''
const taskSavingIds = ref<string[]>([])
const taskErrors = ref<Record<string, string>>({})
const taskRetryStatus = ref<Record<string, string>>({})
let taskWriteRevision = 0
let planVersionRequest = 0

const interviewSessions = ref<InterviewSession[]>([])
const selectedSessionId = ref('')
const selectedCompletedSessionId = ref('')
const sessionReport = ref<InterviewSessionReport>()
const interviewLoading = ref(false)
const interviewActionLoading = ref(false)
const interviewEvaluatingId = ref('')
const interviewEvaluationError = ref('')
const activeQuestionIndex = ref(0)
const answerDrafts = ref<Record<string, string>>({})
const interviewQuestionCount = ref(5)
const interviewTargetRole = ref('')
let answerDraftSessionId = ''
let interviewReadRequest = 0

const knowledgeQuery = ref('Java Redis 面试')
const knowledgeAnswer = ref<KnowledgeAnswerResponse>()
const knowledgeRetrieval = ref<AiSearchResponse>()
const knowledgeResultCount = ref<number>()
const knowledgeLoading = ref(false)
const knowledgeUseAi = ref(false)
const knowledgeAnswerUsedAi = ref(false)
const knowledgeError = ref('')
const knowledgeRecentQueries = ref<string[]>([])
let knowledgeRequestKey = 0

const selectedResume = computed(() => resumes.value.find((resume) => resume.resumeId === selectedResumeId.value))
const selectedJob = computed(() => jobs.value.find((job) => job.jobId === selectedJobId.value))
const selectedPlan = computed(() => plans.value.find((plan) => plan.planId === selectedPlanId.value))
const selectedPlanIsActive = computed(() => selectedPlan.value?.status === 'ACTIVE')
const selectedSession = computed(() => interviewSessions.value.find((session) => session.sessionId === selectedSessionId.value))
const activeQuestion = computed(() => selectedSession.value?.questions[activeQuestionIndex.value])
const interviewHistoryOpen = computed(() => route.query.tab === 'history')
const filteredJobs = computed(() => filterJobs(jobs.value, {
  keyword: jobSearch.value,
  city: jobCityFilter.value,
  skill: jobSkillFilter.value
}))
const jobCities = computed(() => [...new Set(jobs.value.map((job) => job.city).filter(Boolean))].sort())
const jobSkills = computed(() => [...new Set(jobs.value.flatMap((job) => job.requiredSkills).filter(Boolean))].sort())
const selectedPairMatch = computed(() => latestMatchForPair(matches.value, selectedResumeId.value, selectedJobId.value))
const selectedPairContext = computed(() => currentMatch.value
  && currentMatch.value.resumeId === selectedResumeId.value
  && currentMatch.value.jobId === selectedJobId.value
  ? currentMatch.value
  : selectedPairMatch.value)
const selectedContextMatch = computed(() => selectedPairContext.value && selectedResume.value && selectedJob.value
  && (matchContextIsCurrent(selectedPairContext.value, selectedResume.value, selectedJob.value)
    ?? matchUsesCurrentSkills(selectedPairContext.value, selectedResume.value.skills, selectedJob.value.requiredSkills))
  ? selectedPairContext.value : undefined)
const currentMatchStale = computed(() => Boolean(currentMatch.value && (!selectedResume.value || !selectedJob.value
  || !(matchContextIsCurrent(currentMatch.value, selectedResume.value, selectedJob.value)
    ?? matchUsesCurrentSkills(currentMatch.value, selectedResume.value.skills, selectedJob.value.requiredSkills)))))
const resumeProfileDirty = computed(() => resumeProfileIsDirty(selectedResume.value, resumeForm))
const selectedPlanCompletedTasks = computed(() => selectedPlan.value?.tasks.filter((task) => task.status === 'COMPLETED').length || 0)
const selectedPlanProgress = computed(() => {
  const total = selectedPlan.value?.tasks.length || 0
  return total ? Math.round((selectedPlanCompletedTasks.value / total) * 100) : 0
})
const activePlanSchedule = computed<LocalPlanSchedule>(() => {
  const plan = selectedPlan.value
  if (!plan) return { startDate: planForm.startDate, reminderTime: planForm.reminderTime }
  const stored = planSchedules.value[plan.planId] || {}
  const apiStudyDays = Array.isArray(plan.studyDays) && plan.studyDays.length
    ? plan.studyDays
    : (Array.isArray(plan.schedule?.studyDays) && plan.schedule.studyDays.length ? plan.schedule.studyDays : undefined)
  const fromApi = {
    ...(plan.schedule || {}),
    startDate: plan.startDate || plan.schedule?.startDate,
    studyDays: apiStudyDays || ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    dailyMinutesCap: plan.dailyMinutesCap || plan.schedule?.dailyMinutesCap
  } as LocalPlanSchedule
  const storedStudyDays = Array.isArray(stored.studyDays) && stored.studyDays.length ? stored.studyDays : undefined
  return {
    ...fromApi,
    ...stored,
    studyDays: storedStudyDays || fromApi.studyDays,
    startDate: stored.startDate || fromApi.startDate || plan.startDate
  }
})
const todayKey = computed(() => new Date().toISOString().slice(0, 10))
const currentPlanWeek = computed(() => {
  const start = activePlanSchedule.value.startDate
  if (!start) return 1
  const elapsed = Math.floor((Date.parse(`${todayKey.value}T00:00:00`) - Date.parse(`${start}T00:00:00`)) / 86400000)
  return Math.max(1, Math.min(selectedPlan.value?.durationWeeks || 1, Math.floor(elapsed / 7) + 1))
})
const planTasks = computed(() => selectedPlan.value?.tasks || [])
const taskMetaKey = (planId: string, taskId: string) => `${planId}:${taskId}`
const taskSchedule = (task: LearningPlan['tasks'][number]) => {
  const plan = selectedPlan.value
  const meta = plan ? taskScheduleMeta.value[taskMetaKey(plan.planId, task.taskId)] || {} : {}
  const apiTask = task as LearningPlan['tasks'][number] & TaskScheduleMeta
  const schedule = activePlanSchedule.value
  const base = schedule.startDate ? Date.parse(`${schedule.startDate}T00:00:00`) : NaN
  const derived = Number.isFinite(base) ? new Date(base + Math.max(0, (task.week || 1) - 1) * 7 * 86400000).toISOString().slice(0, 10) : ''
  const scheduledDate = meta.scheduledDate || apiTask.scheduledDate || apiTask.taskDate || derived
  const dueDate = meta.dueDate || apiTask.dueDate || apiTask.deferredUntil || scheduledDate
  return { ...apiTask, ...meta, scheduledDate, dueDate }
}
const taskDisplayStatus = (task: LearningPlan['tasks'][number]) => {
  const meta = selectedPlan.value ? taskScheduleMeta.value[taskMetaKey(selectedPlan.value.planId, task.taskId)] : undefined
  return meta?.status || (task.status === 'TODO' ? 'PENDING' : task.status)
}
const todayTasks = computed(() => planTasks.value.filter((task) => taskSchedule(task).scheduledDate === todayKey.value))
const weekTasks = computed(() => planTasks.value.filter((task) => task.week === currentPlanWeek.value))
const overdueTasks = computed(() => planTasks.value.filter((task) => {
  const due = taskSchedule(task).dueDate
  return Boolean(due && due < todayKey.value && !['COMPLETED', 'SKIPPED'].includes(taskDisplayStatus(task)))
}))
const upcomingReminders = computed(() => planTasks.value.filter((task) => {
  const reminder = taskSchedule(task).reminderAt || activePlanSchedule.value.reminderTime
  return Boolean(reminder && !['COMPLETED', 'SKIPPED'].includes(taskDisplayStatus(task)))
}))
const selectedSessionProgress = computed(() => {
  const total = selectedSession.value?.questions.length || 0
  return total ? Math.round(((selectedSession.value?.answers.length || 0) / total) * 100) : 0
})
const firstUnansweredIndex = computed(() => selectedSession.value ? firstActionableQuestionIndex(selectedSession.value) : 0)
const activeSavedAnswer = computed(() => selectedSession.value?.answers.find((answer) => answer.questionId === activeQuestion.value?.questionId))
const activeQuestionLocked = computed(() => !selectedSession.value || selectedSession.value.status !== 'IN_PROGRESS'
  || activeQuestionIndex.value !== firstUnansweredIndex.value || Boolean(activeSavedAnswer.value?.answer.trim()))
const pendingInterviewEvaluations = computed(() => (selectedSession.value?.answers || []).filter((answer) => answer.evaluationStatus && answer.evaluationStatus !== 'SUCCEEDED').length)
const comparisonSessions = computed(() => selectedSession.value ? comparableInterviewSessions(selectedSession.value, interviewSessions.value) : [])
const unfinishedInterviewQuestions = computed(() => selectedSession.value
  ? unfinishedQuestionCount(selectedSession.value.questions, selectedSession.value.answers)
  : 0)
const activeQuestionFeedback = computed(() => activeSavedAnswer.value?.evaluation || sessionReport.value?.questionFeedback
  ?.find((feedback) => feedback.questionId === activeQuestion.value?.questionId))
const compatibleCompletedSessions = computed(() => {
  const plan = selectedPlan.value
  return plan
    ? interviewSessions.value.filter((session) => session.status === 'COMPLETED'
      && Boolean(session.report)
      && session.targetRole === plan.targetRole
      && (session.resumeId || '') === (plan.resumeId || '')
      && (session.jobId || '') === (plan.jobId || '')
      && (session.matchId || '') === (plan.matchId || ''))
    : []
})
const compatibleInterviewSessionId = computed(() => {
  return compatibleCompletedSessions.value.some((session) => session.sessionId === selectedCompletedSessionId.value)
    ? selectedCompletedSessionId.value
    : undefined
})
const currentAnswer = computed({
  get: () => activeQuestion.value ? answerDrafts.value[activeQuestion.value.questionId] || '' : '',
  set: (value: string) => {
    if (activeQuestion.value && !activeQuestionLocked.value) {
      answerDrafts.value = { ...answerDrafts.value, [activeQuestion.value.questionId]: value }
      persistInterviewDraft(selectedSession.value)
    }
  }
})

function renderMarkdown(value: string) {
  return markdown.render(value || '')
}

function sourceTagLabel(source?: string, mocked?: boolean) {
  if (mocked) {
    return '演示数据'
  }
  const normalized = source?.trim().toUpperCase()
  if (!normalized) {
    return 'AI生成'
  }
  if (normalized === 'AI_TEXT_RULE_SCORE') {
    return 'AI 诊断 · 规则证据分'
  }
  if (normalized.includes('DEMO') || normalized.includes('MOCK')) {
    return `演示：${source}`
  }
  if (normalized.includes('RULE')) {
    return `规则：${source}`
  }
  if (normalized.includes('AI')) {
    return `AI：${source}`
  }
  return `来源：${source}`
}

function learningStageLabel(stage?: string) {
  const labels: Record<string, string> = {
    FOUNDATION: '基础夯实',
    PRACTICE: '专项练习',
    APPLICATION: '应用产出'
  }
  return labels[stage?.trim().toUpperCase() || ''] || stage || '未分阶段'
}

function sourceTagType(source?: string, mocked?: boolean) {
  if (mocked || source?.toUpperCase().includes('DEMO') || source?.toUpperCase().includes('MOCK')) {
    return 'warning'
  }
  if (source?.toUpperCase().includes('RULE')) {
    return 'info'
  }
  return 'success'
}

function validatePlanSchedule() {
  if (!Number.isInteger(planForm.weeklyHours) || planForm.weeklyHours < 2 || planForm.weeklyHours > 40) {
    ElMessage.warning('每周投入时间需在 2 到 40 小时之间')
    return false
  }
  if (!Number.isInteger(planForm.durationWeeks) || planForm.durationWeeks < 1 || planForm.durationWeeks > 24) {
    ElMessage.warning('计划周期需在 1 到 24 周之间')
    return false
  }
  if (!planForm.studyDays.length) {
    ElMessage.warning('至少选择一个学习日')
    return false
  }
  if (!Number.isInteger(planForm.dailyMinutesCap) || planForm.dailyMinutesCap < 30 || planForm.dailyMinutesCap > 480) {
    ElMessage.warning('每天上限需在 30 到 480 分钟之间')
    return false
  }
  if (planForm.weeklyHours * 60 > planForm.dailyMinutesCap * planForm.studyDays.length) {
    ElMessage.warning('每周投入时间超过学习日和每天上限的总容量')
    return false
  }
  return true
}

function validateInterviewQuestionCount() {
  if (!Number.isInteger(interviewQuestionCount.value)
    || interviewQuestionCount.value < 1
    || interviewQuestionCount.value > 8) {
    ElMessage.warning('面试题数需在 1 到 8 题之间')
    return false
  }
  return true
}

function hydrateResumeForm(resume?: ResumeSummary) {
  hydratingResume = true
  resumeFormId = resume?.resumeId || ''
  const draft = readStudentDraft(sessionStorage, profile.value?.userId || '', 'resume', resumeFormId)
  resumeForm.education = draft.education ?? resume?.education ?? ''
  resumeForm.skills = draft.skills ?? resume?.skills.join(', ') ?? profile.value?.skills.join(', ') ?? ''
  resumeForm.projects = draft.projects ?? resume?.projects.join('\n') ?? ''
  resumeForm.targetJob = targetRole.value
  hydratingResume = false
}

function syncTargetRole() {
  const userId = profile.value?.userId
  if (!userId) {
    return
  }
  const saved = localStorage.getItem(`aicampus.target-role.${userId}`)?.trim()
  targetRole.value = saved || profile.value?.targetPosition || ''
  planForm.targetRole = planForm.targetRole || targetRole.value
  interviewTargetRole.value = interviewTargetRole.value || targetRole.value
  loadLearningWorkspaceMeta()
  loadKnowledgeHistory()
}

function knowledgeHistoryStorageKey() {
  return profile.value?.userId ? `aicampus.knowledge-history.${profile.value.userId}` : ''
}

function loadKnowledgeHistory() {
  const key = knowledgeHistoryStorageKey()
  if (!key) {
    knowledgeRecentQueries.value = []
    return
  }
  try {
    const saved = JSON.parse(localStorage.getItem(key) || '[]')
    knowledgeRecentQueries.value = Array.isArray(saved)
      ? saved.filter((item): item is string => typeof item === 'string').slice(0, 6)
      : []
  } catch {
    knowledgeRecentQueries.value = []
  }
}

function saveKnowledgeQuery(query: string) {
  knowledgeRecentQueries.value = appendRecentQuery(knowledgeRecentQueries.value, query)
  const key = knowledgeHistoryStorageKey()
  if (key) {
    localStorage.setItem(key, JSON.stringify(knowledgeRecentQueries.value))
  }
}

function knowledgeAnswerLabel() {
  return !knowledgeAnswerUsedAi.value ? '检索摘要'
    : knowledgeAnswer.value?.mocked && knowledgeAnswer.value.provider === 'local-rag-fallback'
    ? '备用回答'
    : 'AI 引用回答'
}

function matchSourceLabel(source?: string) {
  return source === 'RULE_INSUFFICIENT_JOB_SKILLS' ? '匹配依据不足' : sourceTagLabel(source)
}

function matchScoreLabel(match?: MatchResult) {
  return match?.analysisSource === 'RULE_INSUFFICIENT_JOB_SKILLS' ? '—' : `${match?.score ?? 0}%`
}

function taskSaving(taskId: string) {
  return taskSavingIds.value.includes(taskId)
}

function learningWorkspaceKey(suffix: string) {
  return profile.value?.userId ? `aicampus.learning.${suffix}.${profile.value.userId}` : ''
}

function loadLearningWorkspaceMeta() {
  try {
    const schedules = learningWorkspaceKey('schedules')
    const tasks = learningWorkspaceKey('tasks')
    planSchedules.value = schedules ? JSON.parse(localStorage.getItem(schedules) || '{}') : {}
    taskScheduleMeta.value = tasks ? JSON.parse(localStorage.getItem(tasks) || '{}') : {}
  } catch {
    planSchedules.value = {}
    taskScheduleMeta.value = {}
  }
}

function persistLearningWorkspaceMeta() {
  try {
    const schedules = learningWorkspaceKey('schedules')
    const tasks = learningWorkspaceKey('tasks')
    if (schedules) localStorage.setItem(schedules, JSON.stringify(planSchedules.value))
    if (tasks) localStorage.setItem(tasks, JSON.stringify(taskScheduleMeta.value))
  } catch {
    // Private browsing or storage limits should not block task updates.
  }
}

function syncPlanSchedule(plan?: LearningPlan) {
  if (!plan) {
    weeklyReviewDraft.value = ''
    weeklyReviewSaved.value = false
    return
  }
  const apiSchedule = {
    ...(plan.schedule || {}),
    startDate: plan.startDate || plan.schedule?.startDate,
    studyDays: plan.studyDays || plan.schedule?.studyDays,
    dailyMinutesCap: plan.dailyMinutesCap || plan.schedule?.dailyMinutesCap
  } as LocalPlanSchedule
  const saved = planSchedules.value[plan.planId] || {}
  const savedStudyDays = Array.isArray(saved.studyDays) && saved.studyDays.length
    ? saved.studyDays
    : undefined
  const apiStudyDays = Array.isArray(apiSchedule.studyDays) && apiSchedule.studyDays.length
    ? apiSchedule.studyDays
    : undefined
  planSchedules.value = {
    ...planSchedules.value,
    [plan.planId]: {
      ...apiSchedule,
      ...saved,
      startDate: saved.startDate || apiSchedule.startDate || plan.startDate,
      studyDays: savedStudyDays || apiStudyDays || ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      dailyMinutesCap: saved.dailyMinutesCap || apiSchedule.dailyMinutesCap || Math.max(30, Math.ceil(plan.weeklyHours * 60 / 5))
    }
  }
  weeklyReviewDraft.value = saved.weeklyReview || ''
  weeklyReviewSaved.value = Boolean(saved.weeklyReview)
}

async function loadPlanInsights(plan?: LearningPlan) {
  if (!plan) {
    todayPlanData.value = undefined
    weeklyReviewData.value = undefined
    return
  }
  try {
    const [today, review, reminders] = await Promise.all([
      getLearningToday(plan.planId, todayKey.value),
      getLearningWeeklyReview(plan.planId, currentPlanWeek.value),
      getLearningReminders(plan.planId, todayKey.value)
    ])
    if (selectedPlanId.value === plan.planId) {
      todayPlanData.value = { ...today, reminders }
      weeklyReviewData.value = review
    }
  } catch {
    // Keep derived local schedule data when the optional insight endpoints are unavailable.
    todayPlanData.value = undefined
    weeklyReviewData.value = undefined
  }
}

async function savePlanSchedule() {
  const plan = selectedPlan.value
  if (!plan) return
  planSchedules.value = {
    ...planSchedules.value,
    [plan.planId]: {
      ...planSchedules.value[plan.planId],
      startDate: planForm.startDate || undefined,
      reminderTime: planForm.reminderTime || undefined,
      weeklyReview: weeklyReviewDraft.value.trim() || undefined
    }
  }
  weeklyReviewSaved.value = true
  persistLearningWorkspaceMeta()
  if (weeklyReviewDraft.value.trim()) {
    try {
      await submitLearningWeeklyReview(plan.planId, {
        week: currentPlanWeek.value,
        plannedMinutes: weeklyReviewData.value?.plannedMinutes,
        actualMinutes: weeklyReviewData.value?.actualMinutes,
        completedTasks: selectedPlanCompletedTasks.value,
        incompleteReason: weeklyReviewDraft.value.trim()
      })
    } catch {
      // Keep the local review when an older backend has no review endpoint.
    }
  }
  ElMessage.success('学习安排已保存')
}

function taskActionLabel(task: LearningPlan['tasks'][number]) {
  const status = taskDisplayStatus(task)
  if (status === 'IN_PROGRESS') return '进行中'
  if (status === 'COMPLETED') return '已完成'
  if (status === 'PAUSED') return '已暂停'
  if (status === 'DEFERRED') return '已延期'
  if (status === 'SKIPPED') return '已跳过'
  return '待开始'
}

async function performTaskAction(task: LearningPlan['tasks'][number], action: 'START' | 'PAUSE' | 'COMPLETE' | 'DEFER' | 'SKIP') {
  const plan = selectedPlan.value
  if (!plan || !selectedPlanIsActive.value) return
  const now = new Date()
  const taskKey = taskMetaKey(plan.planId, task.taskId)
  const previous = taskScheduleMeta.value[taskKey] || {}
  const next: TaskScheduleMeta = { ...previous }
  let status = task.status === 'TODO' ? 'PENDING' : task.status
  if (action === 'START') { status = 'IN_PROGRESS'; next.status = undefined; next.startedAt = now.toISOString() }
  if (action === 'PAUSE') { status = 'PENDING'; next.status = 'PAUSED'; next.pausedAt = now.toISOString() }
  if (action === 'COMPLETE') { status = 'COMPLETED'; next.status = undefined; next.completedAt = now.toISOString() }
  if ((action === 'PAUSE' || action === 'COMPLETE') && previous.startedAt) {
    const elapsed = Math.max(0, Math.round((now.getTime() - Date.parse(previous.startedAt)) / 60000))
    next.actualMinutes = (previous.actualMinutes || currentTaskActualMinutes(task)) + elapsed
  }
  if (action === 'SKIP') { status = 'SKIPPED'; next.status = undefined }
  if (action === 'DEFER') {
    status = 'PENDING'; next.status = 'DEFERRED'
    next.snoozedUntil = new Date(now.getTime() + 86400000).toISOString().slice(0, 10)
    next.scheduledDate = next.snoozedUntil
    next.dueDate = next.snoozedUntil
  }
  taskScheduleMeta.value = { ...taskScheduleMeta.value, [taskKey]: next }
  persistLearningWorkspaceMeta()
  await saveTask(task.taskId, status, next)
}

function summarizeText(value?: string, maxLength = 72) {
  const text = value?.trim() || ''
  if (text.length <= maxLength) return text
  return `${text.slice(0, maxLength)}…`
}

function currentTaskActualMinutes(task: LearningPlan['tasks'][number]) {
  return task.actualMinutes || 0
}

function ensureResumeProfileSaved() {
  if (!resumeProfileDirty.value) {
    return true
  }
  ElMessage.warning('请先保存简历资料，再进行诊断或改写。')
  return false
}

function selectionStorageKey() {
  return profile.value?.userId ? `aicampus.selection.${profile.value.userId}` : ''
}

function storedSelection() {
  const key = selectionStorageKey()
  if (!key) {
    return {}
  }
  try {
    return JSON.parse(localStorage.getItem(key) || '{}') as { resumeId?: string, jobId?: string, matchId?: string }
  } catch {
    return {}
  }
}

function persistSelection() {
  const key = selectionStorageKey()
  if (key) {
    const saved = storedSelection()
    localStorage.setItem(key, JSON.stringify({
      resumeId: resumes.value.length ? selectedResumeId.value : saved.resumeId,
      jobId: jobs.value.length ? selectedJobId.value : saved.jobId,
      matchId: jobs.value.length ? currentMatch.value?.matchId : saved.matchId
    }))
  }
}

function syncSelectedResume() {
  const saved = storedSelection()
  if (!selectedResumeId.value || !resumes.value.some((resume) => resume.resumeId === selectedResumeId.value)) {
    selectedResumeId.value = resumes.value.find((resume) => resume.resumeId === saved.resumeId)?.resumeId || resumes.value[0]?.resumeId || ''
  }
  hydrateResumeForm(selectedResume.value)
}

async function loadResumeData() {
  resumeLoading.value = true
  try {
    const profileData = await getProfile()
    profile.value = profileData
    syncTargetRole()
    const resumeList = await listResumes()
    resumes.value = resumeList
    syncSelectedResume()
    await loadDiagnoses()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '简历数据加载失败')
  } finally {
    resumeLoading.value = false
  }
}

async function syncWorkspaceResume(resumeId: string) {
  try {
    resumes.value = await listResumes()
    selectedResumeId.value = resumeId
    hydrateResumeForm(selectedResume.value)
    persistSelection()
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '简历已保存，列表暂时未更新，请稍后刷新。')
  }
}

async function loadDiagnoses(resumeId = selectedResumeId.value) {
  const requestId = ++resumeDiagnosisRequest
  if (!resumeId) {
    diagnoses.value = []
    return
  }
  try {
    const loaded = await listResumeDiagnoses(resumeId)
    if (requestId === resumeDiagnosisRequest && selectedResumeId.value === resumeId) {
      diagnoses.value = loaded
    }
  } catch (error) {
    if (requestId !== resumeDiagnosisRequest || selectedResumeId.value !== resumeId) {
      return
    }
    diagnoses.value = []
    ElMessage.error(error instanceof Error ? error.message : '诊断记录加载失败')
  }
}

async function selectResume() {
  resumeRewrite.value = undefined
  hydrateResumeForm(selectedResume.value)
  await loadDiagnoses(selectedResumeId.value)
}

async function handleResumeUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) {
    return
  }
  const validation = validateResumeFile(file)
  if (!validation.valid) {
    ElMessage.warning(validation.message)
    ;(event.target as HTMLInputElement).value = ''
    return
  }
  resumeActionLoading.value = true
  try {
    const uploaded = await uploadResume(file)
    resumes.value = [uploaded, ...resumes.value.filter((resume) => resume.resumeId !== uploaded.resumeId)]
    selectedResumeId.value = uploaded.resumeId
    await selectResume()
    ElMessage.success('简历已上传')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '简历上传失败')
  } finally {
    resumeActionLoading.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}

async function saveResumeProfile() {
  if (!selectedResume.value) {
    ElMessage.warning('请先上传或选择简历')
    return
  }
  const resumeId = selectedResume.value.resumeId
  const submitted = { education: resumeForm.education, skills: resumeForm.skills, projects: resumeForm.projects }
  resumeActionLoading.value = true
  try {
    const updated = await updateResumeProfile(resumeId, {
      education: submitted.education.trim(),
      skills: splitProfileLines(submitted.skills),
      projects: splitProfileLines(submitted.projects)
    })
    resumes.value = resumes.value.map((resume) => resume.resumeId === updated.resumeId ? updated : resume)
    const draft = readStudentDraft(sessionStorage, profile.value?.userId || '', 'resume', resumeId)
    if (Object.entries(submitted).every(([key, value]) => draft[key] === undefined || draft[key] === value)) {
      writeStudentDraft(sessionStorage, profile.value?.userId || '', 'resume', resumeId, {})
    }
    if (selectedResumeId.value === resumeId) {
      hydrateResumeForm(updated)
      resumeRewrite.value = undefined
    }
    ElMessage.success('简历资料已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '简历资料保存失败')
  } finally {
    resumeActionLoading.value = false
  }
}

async function runResumeAnalysis() {
  if (!selectedResume.value) {
    ElMessage.warning('请先上传或选择简历')
    return
  }
  if (!ensureResumeProfileSaved()) {
    return
  }
  const resumeId = selectedResume.value.resumeId
  resumeActionLoading.value = true
  try {
    const analyzed = await analyzeResume(resumeId, { targetJob: targetRole.value.trim(), jobId: diagnosisJobId.value || undefined })
    resumes.value = resumes.value.map((resume) => resume.resumeId === analyzed.resumeId ? analyzed : resume)
    if (selectedResumeId.value === resumeId) {
      currentMatch.value = undefined
      resumeRewrite.value = undefined
      hydrateResumeForm(analyzed)
      await loadDiagnoses(resumeId)
    }
    ElMessage.success('简历诊断已完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '简历诊断失败')
  } finally {
    resumeActionLoading.value = false
  }
}

async function runResumeRewrite() {
  const resume = selectedResume.value
  if (!resume) {
    ElMessage.warning('请先上传或选择简历')
    return
  }
  if (!ensureResumeProfileSaved()) {
    return
  }
  resumeActionLoading.value = true
  try {
    const rewritten = await rewriteResume({
      studentId: profile.value?.userId || '',
      resumeId: resume.resumeId,
      targetRole: targetRole.value.trim() || profile.value?.targetPosition || '目标岗位',
      resumeSummary: resumeSummaryFromProfile(resumeForm),
      skills: splitProfileLines(resumeForm.skills),
      projects: splitProfileLines(resumeForm.projects)
    })
    if (selectedResumeId.value === resume.resumeId) {
      resumeRewrite.value = rewritten
    }
    ElMessage.success('简历改写建议已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '简历改写失败')
  } finally {
    resumeActionLoading.value = false
  }
}

async function removeResume() {
  const resume = selectedResume.value
  if (!resume) {
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除简历“${resume.fileName}”吗？此操作无法撤销。`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  resumeActionLoading.value = true
  try {
    await deleteResume(resume.resumeId)
    writeStudentDraft(sessionStorage, profile.value?.userId || '', 'resume', resume.resumeId, {})
    resumes.value = resumes.value.filter((item) => item.resumeId !== resume.resumeId)
    selectedResumeId.value = resumes.value[0]?.resumeId || ''
    await selectResume()
    ElMessage.success('简历已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '简历删除失败')
  } finally {
    resumeActionLoading.value = false
  }
}

async function loadJobsData() {
  jobsLoading.value = true
  try {
    const [jobList, matchList] = await Promise.all([listJobs(), listMyMatches()])
    jobs.value = jobList
    matches.value = matchList
    if (!selectedJobId.value || !jobs.value.some((job) => job.jobId === selectedJobId.value)) {
      const saved = storedSelection()
      selectedJobId.value = jobs.value.find((job) => job.jobId === saved.jobId)?.jobId || jobs.value[0]?.jobId || ''
    }
    syncCurrentMatch()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '岗位数据加载失败')
  } finally {
    jobsLoading.value = false
  }
}

async function runMatch() {
  const resume = selectedResume.value
  const job = selectedJob.value
  if (!resume || !job) {
    ElMessage.warning('\u8bf7\u5148\u9009\u62e9\u7b80\u5386\u548c\u5c97\u4f4d')
    return
  }
  if (matchLoading.value) {
    return
  }
  const resumeId = resume.resumeId
  const jobId = job.jobId
  matchLoading.value = true
  try {
    const match = await matchResumeJob(resumeId, jobId)
    matches.value = [match, ...matches.value.filter((item) => item.matchId !== match.matchId)]
    if (selectedResumeId.value === resumeId && selectedJobId.value === jobId) {
      currentMatch.value = match
      persistSelection()
    }
    ElMessage.success('\u5c97\u4f4d\u5339\u914d\u5df2\u5b8c\u6210')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '\u5c97\u4f4d\u5339\u914d\u5931\u8d25')
  } finally {
    matchLoading.value = false
  }
}

async function compareSelectedJobs() {
  if (!selectedResumeId.value) {
    ElMessage.warning('\u8bf7\u5148\u9009\u62e9\u7b80\u5386')
    return
  }
  const ids = [...new Set(selectedCompareJobIds.value)].filter(Boolean)
  if (ids.length < 2 || ids.length > 3) {
    ElMessage.warning('\u8bf7\u9009\u62e9\u4e24\u5230\u4e09\u4e2a\u5c97\u4f4d\u8fdb\u884c\u6bd4\u8f83')
    return
  }
  compareLoading.value = true
  try {
    compareResult.value = await compareResumeJobs({ resumeId: selectedResumeId.value, jobIds: ids })
    ElMessage.success('\u5c97\u4f4d\u6bd4\u8f83\u5df2\u5b8c\u6210')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '\u5c97\u4f4d\u6bd4\u8f83\u5931\u8d25')
  } finally {
    compareLoading.value = false
  }
}

async function restoreMatch(match: MatchResult) {
  selectedResumeId.value = match.resumeId
  selectedJobId.value = match.jobId
  await selectResume()
  currentMatch.value = match
  persistSelection()
}

function syncCurrentMatch() {
  const saved = storedSelection()
  const preferredId = currentMatch.value?.matchId || saved.matchId
  currentMatch.value = matches.value.find((match) => match.matchId === preferredId
    && match.resumeId === selectedResumeId.value && match.jobId === selectedJobId.value)
    || latestMatchForPair(matches.value, selectedResumeId.value, selectedJobId.value)
}

async function openMatchWorkspace(module: 'plan' | 'interview') {
  if (!currentMatch.value || currentMatchStale.value) {
    return
  }
  await restoreMatch(currentMatch.value)
  await router.push({ path: `/student/${module}`, query: { matchId: currentMatch.value.matchId } })
}

async function loadPlans() {
  planLoading.value = true
  try {
    plans.value = await listLearningPlans()
    if (!planBuilderInitialized) {
      planBuilderOpen.value = plans.value.length === 0
      planBuilderInitialized = true
    }
    if (!selectedPlanId.value || !plans.value.some((plan) => plan.planId === selectedPlanId.value)) {
      const preferredPlan = [...plans.value]
        .filter((plan) => plan.status === 'ACTIVE')
        .sort((left, right) => right.version - left.version)[0]
        || [...plans.value].sort((left, right) => right.version - left.version)[0]
      selectedPlanId.value = preferredPlan?.planId || ''
    }
    await loadPlanVersions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学习计划加载失败')
  } finally {
    planLoading.value = false
  }
}

async function loadPlanVersions() {
  const requestId = ++planVersionRequest
  const plan = selectedPlan.value
  if (plan) {
    planForm.targetRole = route.query.matchId ? selectedJob.value?.title || plan.targetRole : plan.targetRole
    planForm.weeklyHours = plan.weeklyHours
    planForm.durationWeeks = plan.durationWeeks
    syncPlanSchedule(plan)
    planForm.startDate = activePlanSchedule.value.startDate || ''
    planForm.reminderTime = activePlanSchedule.value.reminderTime || '20:00'
    planForm.studyDays = activePlanSchedule.value.studyDays || ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']
    planForm.dailyMinutesCap = activePlanSchedule.value.dailyMinutesCap || Math.max(30, Math.round(plan.weeklyHours * 60 / Math.max(1, planForm.studyDays.length)))
  }
  syncTaskFeedback(plan)
  void loadPlanInsights(plan)
  if (!selectedPlanId.value) {
    planVersions.value = []
    return
  }
  try {
    const versions = await listLearningPlanVersions(selectedPlanId.value)
    if (requestId === planVersionRequest) planVersions.value = versions
  } catch (error) {
    if (requestId !== planVersionRequest) return
    planVersions.value = []
    ElMessage.error(error instanceof Error ? error.message : '计划版本加载失败')
  }
}

function syncTaskFeedback(plan?: LearningPlan) {
  if (taskFeedbackPlanId) taskDraftsByPlan.set(taskFeedbackPlanId, { ...taskFeedback.value })
  taskFeedbackPlanId = plan?.planId || ''
  const drafts = plan?.status === 'ACTIVE' ? taskDraftsByPlan.get(taskFeedbackPlanId) || {} : {}
  taskFeedback.value = Object.fromEntries((plan?.tasks || []).map((task) => [task.taskId, drafts[task.taskId] ?? task.feedback ?? '']))
  taskErrors.value = {}
}

async function createPlan() {
  if (!validatePlanSchedule()) {
    return
  }
  planActionLoading.value = true
  try {
    const plan = await createLearningPlan({
      studentId: profile.value?.userId,
      resumeId: selectedResume.value?.resumeId,
      jobId: selectedJob.value?.jobId,
      matchId: selectedContextMatch.value?.matchId,
      targetRole: planForm.targetRole.trim() || selectedJob.value?.title || profile.value?.targetPosition,
      weeklyHours: planForm.weeklyHours,
      durationWeeks: planForm.durationWeeks,
      startDate: planForm.startDate || todayKey.value,
      studyDays: planForm.studyDays,
      dailyMinutesCap: planForm.dailyMinutesCap
    })
    planSchedules.value = {
      ...planSchedules.value,
      [plan.planId]: {
        ...(plan.schedule || {}),
        startDate: planForm.startDate || todayKey.value,
        reminderTime: planForm.reminderTime || '20:00',
        studyDays: planForm.studyDays,
        dailyMinutesCap: planForm.dailyMinutesCap
      }
    }
    persistLearningWorkspaceMeta()
    plans.value = [plan, ...plans.value.filter((item) => item.planId !== plan.planId)]
    selectedPlanId.value = plan.planId
    planBuilderOpen.value = false
    await loadPlanVersions()
    ElMessage.success('学习计划已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学习计划生成失败')
  } finally {
    planActionLoading.value = false
  }
}

async function saveTask(taskId: string, status: string, metadata?: TaskScheduleMeta) {
  const plan = selectedPlan.value
  if (!plan) {
    return
  }
  if (plan.status !== 'ACTIVE') {
    ElMessage.warning('历史版本为只读，不能更新任务')
    return
  }
  if (taskSaving(taskId)) {
    return
  }
  taskSavingIds.value = [...taskSavingIds.value, taskId]
  taskRetryStatus.value[taskId] = status
  ++taskWriteRevision
  const { [taskId]: _previousError, ...remainingErrors } = taskErrors.value
  taskErrors.value = remainingErrors
  try {
    const currentTask = plan.tasks.find((task) => task.taskId === taskId)
    const updated = await updateLearningTask(plan.planId, taskId, {
      status,
      feedback: taskFeedback.value[taskId] ?? currentTask?.feedback,
      scheduledDate: metadata?.scheduledDate,
      dueDate: metadata?.dueDate,
      snoozedUntil: metadata?.snoozedUntil,
      action: metadata?.status === 'PAUSED' ? 'PAUSE' : metadata?.status === 'DEFERRED' ? 'DEFER' : undefined,
      actualMinutes: metadata?.actualMinutes ?? currentTask?.actualMinutes,
      deferredUntil: metadata?.snoozedUntil
    })
    if (taskFeedbackPlanId === plan.planId) {
      taskFeedback.value = { ...taskFeedback.value, [taskId]: updated.feedback || '' }
    } else {
      taskDraftsByPlan.set(plan.planId, { ...taskDraftsByPlan.get(plan.planId), [taskId]: updated.feedback || '' })
    }
    plans.value = plans.value.map((item) => {
      if (item.planId !== plan.planId) {
        return item
      }
      const tasks = item.tasks.map((task) => task.taskId === taskId ? updated : task)
      return {
        ...item,
        tasks,
        status: tasks.every((task) => task.status === 'COMPLETED') ? 'COMPLETED' : 'ACTIVE',
        updatedAt: updated.updatedAt
      }
    })
    const storedTaskMeta = taskScheduleMeta.value[taskMetaKey(plan.planId, taskId)]
    if (metadata || storedTaskMeta) {
      taskScheduleMeta.value = {
        ...taskScheduleMeta.value,
        [taskMetaKey(plan.planId, taskId)]: metadata || { ...storedTaskMeta, status: undefined }
      }
      persistLearningWorkspaceMeta()
    }
    ElMessage.success('任务进度已保存')
  } catch (error) {
    if (selectedPlanId.value === plan.planId) {
      taskErrors.value = { ...taskErrors.value, [taskId]: error instanceof Error ? error.message : '任务保存失败，请重试。' }
    }
    ElMessage.error(error instanceof Error ? error.message : '任务进度保存失败')
  } finally {
    taskSavingIds.value = taskSavingIds.value.filter((id) => id !== taskId)
    if (!taskSavingIds.value.length) {
      const refreshRevision = taskWriteRevision
      try {
        const refreshed = await getLearningPlan(plan.planId)
        if (refreshRevision === taskWriteRevision) {
          plans.value = plans.value.map((item) => item.planId === refreshed.planId ? refreshed : item)
          void loadPlanInsights(refreshed)
          if (refreshed.status !== 'ACTIVE' && selectedPlanId.value === refreshed.planId) syncTaskFeedback(refreshed)
        }
      } catch {
        ElMessage.warning('任务汇总刷新失败，已输入的备注仍保留，可稍后重试。')
      }
    }
  }
}

async function replan() {
  const plan = selectedPlan.value
  if (!plan || !planForm.replanReason.trim()) {
    ElMessage.warning('请填写重规划原因')
    return
  }
  if (plan.status !== 'ACTIVE') {
    ElMessage.warning('历史版本为只读，不能重新规划')
    return
  }
  if (!validatePlanSchedule()) {
    return
  }
  planActionLoading.value = true
  try {
    const revised = await replanLearningPlan(plan.planId, {
      reason: planForm.replanReason.trim(),
      weeklyHours: planForm.weeklyHours,
      durationWeeks: planForm.durationWeeks,
      startDate: planForm.startDate || activePlanSchedule.value.startDate,
      studyDays: planForm.studyDays,
      dailyMinutesCap: planForm.dailyMinutesCap,
      interviewSessionId: compatibleInterviewSessionId.value,
      previewOnly: true
    })
    replanPreview.value = revised
    replanPreviewSourceId.value = plan.planId
    replanPreviewOpen.value = true
    ElMessage.success('新计划已生成，请核对后确认切换')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学习计划重规划失败')
  } finally {
    planActionLoading.value = false
  }
}

async function confirmReplan() {
  const preview = replanPreview.value
  if (!preview || !replanPreviewSourceId.value) return
  planActionLoading.value = true
  try {
    const revised = await confirmLearningPlan(replanPreviewSourceId.value, preview.planId)
    plans.value = [
      revised,
      ...plans.value
        .filter((item) => item.planId !== revised.planId)
        .map((item) => item.planId === replanPreviewSourceId.value
          ? { ...item, status: 'SUPERSEDED', updatedAt: revised.createdAt }
          : item)
    ]
    selectedPlanId.value = revised.planId
    planForm.replanReason = ''
    replanPreviewOpen.value = false
    replanPreview.value = undefined
    await loadPlanVersions()
    ElMessage.success(`已生成 V${revised.version} 学习计划`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学习计划重规划失败')
  } finally {
    planActionLoading.value = false
  }
}

function recordTaskEvidence(evidence: LearningEvidence) {
  plans.value = plans.value.map((plan) => plan.planId === evidence.planId ? { ...plan,
    tasks: plan.tasks.map((task) => task.taskId === evidence.taskId ? { ...task,
      evidence: [evidence, ...(task.evidence || []).filter((item) => item.evidenceId !== evidence.evidenceId)] } : task) } : plan)
}

function openResumeCandidateFromEvidence(evidence: LearningEvidence) {
  ElMessage.info('成果已记录。打开简历页面后，可在主资料中选择它作为候选经历。')
  void router.push('/student/resume')
}

function openInterviewFollowUpFromEvidence(evidence: LearningEvidence) {
  const plan = selectedPlan.value
  if (plan) interviewTargetRole.value = plan.targetRole
  ElMessage.info('已带入当前目标岗位，请在模拟面试中开始追问练习。')
  void router.push({ path: '/student/interview', query: { taskId: evidence.taskId, planId: evidence.planId } })
}

function persistInterviewDraft(session?: InterviewSession) {
  if (!session) return
  const pending = session.status === 'IN_PROGRESS'
    ? Object.fromEntries(session.questions
      .filter((question) => !session.answers.some((answer) => answer.questionId === question.questionId))
      .map((question) => [question.questionId, answerDrafts.value[question.questionId] || ''])
      .filter(([, value]) => Boolean(value)))
    : {}
  writeStudentDraft(sessionStorage, profile.value?.userId || '', 'interview', session.sessionId, pending)
}

function syncSessionDrafts(session?: InterviewSession) {
  const saved = session?.status === 'IN_PROGRESS'
    ? readStudentDraft(sessionStorage, profile.value?.userId || '', 'interview', session.sessionId)
    : {}
  const existing = answerDraftSessionId === session?.sessionId && session?.status === 'IN_PROGRESS'
    ? answerDrafts.value : saved
  answerDrafts.value = mergeAnswerDrafts(existing, session?.answers || [])
  answerDraftSessionId = session?.sessionId || ''
  persistInterviewDraft(session)
  sessionReport.value = session?.report
  const firstUnanswered = session ? firstActionableQuestionIndex(session) : 0
  activeQuestionIndex.value = Math.min(
    Math.max(0, firstUnanswered),
    Math.max(0, (session?.questions.length || 1) - 1)
  )
}

async function loadInterviewSessions() {
  const requestId = ++interviewReadRequest
  interviewLoading.value = true
  try {
    const sessions = await listInterviewSessions()
    if (requestId !== interviewReadRequest) return
    interviewSessions.value = sessions
    if (!selectedSessionId.value || !interviewSessions.value.some((session) => session.sessionId === selectedSessionId.value)) {
      selectedSessionId.value = interviewSessions.value[0]?.sessionId || ''
    }
    syncSessionDrafts(selectedSession.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模拟面试会话加载失败')
  } finally {
    if (requestId === interviewReadRequest) interviewLoading.value = false
  }
}

async function selectSession() {
  if (!selectedSessionId.value) {
    return
  }
  const sessionId = selectedSessionId.value
  const requestId = ++interviewReadRequest
  syncSessionDrafts(selectedSession.value)
  interviewLoading.value = true
  try {
    const session = await getInterviewSession(sessionId)
    if (requestId !== interviewReadRequest || selectedSessionId.value !== sessionId) return
    interviewSessions.value = interviewSessions.value.map((item) => item.sessionId === session.sessionId ? session : item)
    syncSessionDrafts(session)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模拟面试会话读取失败')
  } finally {
    if (requestId === interviewReadRequest) interviewLoading.value = false
  }
}

async function startInterview() {
  if (!validateInterviewQuestionCount()) {
    return
  }
  const resolvedTargetRole = interviewTargetRole.value.trim() || selectedJob.value?.title || targetRole.value.trim() || profile.value?.targetPosition
  if (!resolvedTargetRole) {
    ElMessage.warning('请填写目标岗位后开始模拟面试。')
    return
  }
  const matchedContext = selectedContextMatch.value
  interviewActionLoading.value = true
  try {
    const session = await createInterviewSession({
      studentId: profile.value?.userId,
      resumeId: selectedResume.value?.resumeId,
      jobId: selectedJob.value?.jobId,
      matchId: matchedContext?.matchId,
      targetRole: resolvedTargetRole,
      questionCount: interviewQuestionCount.value
    })
    interviewSessions.value = [session, ...interviewSessions.value]
    selectedSessionId.value = session.sessionId
    sessionReport.value = undefined
    syncSessionDrafts(session)
    await router.replace({ path: '/student/interview' })
    ElMessage.success('模拟面试已开始')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模拟面试创建失败')
  } finally {
    interviewActionLoading.value = false
  }
}

async function saveCurrentAnswer() {
  const session = selectedSession.value
  const question = activeQuestion.value
  if (!session || !question || !currentAnswer.value.trim()) {
    ElMessage.warning('请输入本题回答')
    return
  }
  if (activeQuestionLocked.value) {
    ElMessage.warning('请按题目顺序作答；已保存回答不可修改。')
    return
  }
  interviewActionLoading.value = true
  try {
    const updated = await saveInterviewSessionAnswer(session.sessionId, question.questionId, currentAnswer.value)
    interviewSessions.value = interviewSessions.value.map((item) => item.sessionId === updated.sessionId ? updated : item)
    if (selectedSessionId.value === updated.sessionId) syncSessionDrafts(updated)
    else writeStudentDraft(sessionStorage, profile.value?.userId || '', 'interview', updated.sessionId,
      Object.fromEntries(Object.entries(readStudentDraft(sessionStorage, profile.value?.userId || '', 'interview', updated.sessionId))
        .filter(([id]) => !updated.answers.some((answer) => answer.questionId === id))))
    ElMessage.success('回答已保存，正在评价')
    await evaluateSavedAnswer(updated.sessionId, question.questionId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '回答保存失败')
  } finally {
    interviewActionLoading.value = false
  }
}

async function evaluateSavedAnswer(sessionId: string, questionId: string) {
  if (interviewEvaluatingId.value) return
  interviewEvaluatingId.value = questionId
  interviewEvaluationError.value = ''
  try {
    const result = await evaluateInterviewAnswer(sessionId, questionId)
    const refreshed = await getInterviewSession(sessionId)
    interviewSessions.value = interviewSessions.value.map((session) => session.sessionId === sessionId ? refreshed : session)
    if (selectedSessionId.value === sessionId) {
      syncSessionDrafts(refreshed)
      if (result.status !== 'SUCCEEDED') {
        activeQuestionIndex.value = Math.max(0, refreshed.questions.findIndex((question) => question.questionId === questionId))
        interviewEvaluationError.value = result.error || '回答已保存，评价暂不可用，请重试。'
      }
    }
  } catch (error) {
    if (selectedSessionId.value === sessionId) {
      const session = selectedSession.value
      if (session) activeQuestionIndex.value = Math.max(0, session.questions.findIndex((question) => question.questionId === questionId))
      interviewEvaluationError.value = error instanceof Error ? `回答已保存；评价失败：${error.message}` : '回答已保存，评价暂不可用，请重试。'
    }
  } finally { interviewEvaluatingId.value = '' }
}

async function finishInterview() {
  const session = selectedSession.value
  if (!session || session.status !== 'IN_PROGRESS' || interviewActionLoading.value) {
    return
  }
  const activeQuestionIsSaved = activeQuestion.value
    ? session.answers.some((answer) => answer.questionId === activeQuestion.value?.questionId && answer.answer.trim())
    : true
  if (!activeQuestionIsSaved && currentAnswer.value.trim()) {
    await saveCurrentAnswer()
  }
  if (selectedSessionId.value !== session.sessionId) return
  const pendingCount = unfinishedQuestionCount(selectedSession.value?.questions || [], selectedSession.value?.answers || [])
  if (pendingCount > 0) {
    ElMessage.warning(`还有 ${pendingCount} 题未保存，请逐题完成后再生成报告。`)
    return
  }
  if (pendingInterviewEvaluations.value) {
    ElMessage.warning('还有已保存答案未完成评价，请先重试逐题评价。')
    return
  }
  interviewActionLoading.value = true
  try {
    const report = await finishInterviewSession(session.sessionId)
    interviewSessions.value = interviewSessions.value.map((item) => item.sessionId === session.sessionId
      ? { ...item, status: 'COMPLETED', report } : item)
    writeStudentDraft(sessionStorage, profile.value?.userId || '', 'interview', session.sessionId, {})
    if (selectedSessionId.value === session.sessionId) await selectSession()
    ElMessage.success('模拟面试报告已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模拟面试完成失败')
  } finally {
    interviewActionLoading.value = false
  }
}

async function runKnowledgeSearch() {
  if (knowledgeLoading.value) {
    return
  }
  if (!knowledgeQuery.value.trim()) {
    ElMessage.warning('请输入检索关键词')
    return
  }
  const query = knowledgeQuery.value.trim()
  const useAi = knowledgeUseAi.value
  const requestKey = ++knowledgeRequestKey
  knowledgeError.value = ''
  knowledgeAnswer.value = undefined
  knowledgeRetrieval.value = undefined
  knowledgeResultCount.value = undefined
  knowledgeAnswerUsedAi.value = useAi
  knowledgeLoading.value = true
  try {
    const answer = await answerKnowledgeBase({ query, role: 'STUDENT', limit: 5, useAi })
    if (requestKey !== knowledgeRequestKey) return
    knowledgeAnswer.value = { ...answer, citations: answer.citations || [] }
    knowledgeRetrieval.value = retrievalFromAnswer(answer)
    knowledgeResultCount.value = knowledgeRetrieval.value.results.length
    saveKnowledgeQuery(query)
    if (!answer.citations?.length) ElMessage.warning('没有找到可引用的知识资料')
  } catch (error) {
    knowledgeError.value = error instanceof Error ? error.message : '知识库检索失败'
  } finally {
    knowledgeLoading.value = false
  }
}

async function loadModule(module: string) {
  if (module === 'resume') {
    await loadResumeData()
  } else if (module === 'jobs') {
    await loadResumeData()
    await loadJobsData()
  } else if (module === 'plan') {
    await loadResumeData()
    await loadJobsData()
    await Promise.all([loadPlans(), loadInterviewSessions()])
  } else if (module === 'interview') {
    await loadResumeData()
    await loadJobsData()
    await loadInterviewSessions()
  } else if (module === 'knowledge') {
    await loadResumeData()
  }
  if (route.query.matchId && selectedJob.value) {
    if (module === 'plan') planForm.targetRole = selectedJob.value.title
    if (module === 'interview') interviewTargetRole.value = selectedJob.value.title
  }
}

onMounted(() => { void loadModule(activeModule.value) })
watch(activeModule, (module) => { void loadModule(module) })
watch([selectedResumeId, selectedJobId], () => {
  syncCurrentMatch()
  persistSelection()
})
watch(() => [resumeForm.education, resumeForm.skills, resumeForm.projects], () => {
  if (!hydratingResume && resumeFormId) {
    writeStudentDraft(sessionStorage, profile.value?.userId || '', 'resume', resumeFormId, {
      education: resumeForm.education, skills: resumeForm.skills, projects: resumeForm.projects
  })
}

}, { flush: 'sync' })
watch(selectedJobId, () => {
  const selectedRole = selectedJob.value?.title
  if (selectedRole && (!planForm.targetRole || planForm.targetRole === targetRole.value)) {
    planForm.targetRole = selectedRole
  }
  if (selectedRole && (!interviewTargetRole.value || interviewTargetRole.value === targetRole.value)) {
    interviewTargetRole.value = selectedRole
  }
})
watch([selectedPlanId, interviewSessions], () => {
  if (!compatibleCompletedSessions.value.some((session) => session.sessionId === selectedCompletedSessionId.value)) {
    selectedCompletedSessionId.value = ''
  }
})
watch(targetRole, (value) => {
  resumeForm.targetJob = value
  if (profile.value?.userId) {
    localStorage.setItem(`aicampus.target-role.${profile.value.userId}`, value.trim())
  }
})
</script>

<template>
  <section class="page student-workspace">
    <header v-if="activeModule !== 'resume'" class="workspace-header compact-workspace-header">
      <label class="target-role-control" aria-label="目标岗位">
        <el-input v-model="targetRole" placeholder="例如 Java 后端实习生">
          <template #prefix><Target :size="16" /></template>
        </el-input>
      </label>
    </header>

    <template v-if="activeModule === 'resume'">
      <ResumeBuilderPanel :target-role="targetRole" :resume-id="selectedResumeId || undefined" @confirmed="syncWorkspaceResume" />

      <details class="legacy-resume-history" data-testid="resume-legacy-history">
        <summary>上传原件与历史诊断</summary>
      <section class="overview-grid resume-overview" v-loading="resumeLoading">
        <article class="overview-card accent-mint"><span>已上传简历</span><strong>{{ resumes.length }}</strong><FileText :size="22" /></article>
        <article class="overview-card accent-lavender"><span>历史诊断评分</span><strong>{{ selectedResume?.score ?? '—' }}<small v-if="selectedResume"> 分</small></strong><TrendingUp :size="22" /></article>
        <article class="overview-card accent-peach"><span>诊断记录</span><strong>{{ diagnoses.length }}</strong><Sparkles :size="22" /></article>
        <article class="overview-card accent-plain"><span>已提取技能</span><strong>{{ selectedResume?.skills.length || 0 }}</strong><GraduationCap :size="22" /></article>
      </section>

      <section class="resume-hero panel" v-loading="resumeLoading">
        <div class="section-heading">
          <div><span class="eyebrow">RESUME LIBRARY</span><h2>我的简历</h2></div>
          <label class="upload-control">
            <Upload :size="16" />
            <span>上传简历</span>
            <input type="file" accept=".pdf,.doc,.docx" :disabled="resumeActionLoading" @change="handleResumeUpload" />
          </label>
        </div>
        <div class="resume-hero-content">
          <div class="resume-picker">
            <span>当前版本</span>
            <el-select v-model="selectedResumeId" placeholder="选择简历" @change="selectResume">
              <el-option v-for="resume in resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" />
            </el-select>
            <el-button text :disabled="!selectedResume" :loading="resumeActionLoading" @click="removeResume">删除该版本</el-button>
          </div>
          <template v-if="selectedResume">
            <div class="resume-score"><span>原诊断评分</span><strong>{{ selectedResume.score }}</strong><small>/ 100</small></div>
            <div class="resume-summary"><strong>{{ selectedResume.fileName }}</strong><p>完善技能与项目经历，让岗位匹配更准确。</p></div>
            <div class="resume-status">
              <span>文件解析</span>
              <div class="tag-row"><el-tag type="info">{{ selectedResume.sourceFormat || '未知格式' }}</el-tag><el-tag type="success">{{ selectedResume.parseStatus || '待解析' }}</el-tag><el-tag>{{ selectedResume.parsedTextLength || 0 }} 字符</el-tag></div>
            </div>
          </template>
          <el-empty v-else description="暂无简历，请先上传" />
        </div>
        <ResumeEvidencePanel v-if="selectedResume?.structuredDiagnosis" :diagnosis="selectedResume.structuredDiagnosis" />
        <details v-if="selectedResume?.diagnosis" class="resume-diagnosis">
          <summary>查看完整诊断报告</summary>
          <div class="diagnosis-report-copy" v-html="renderMarkdown(selectedResume.diagnosis)" />
        </details>
      </section>

      <section class="resume-workspace">
        <article class="panel profile-panel">
          <div class="section-heading"><div><span class="eyebrow">PROFILE EVIDENCE</span><h2>简历资料与诊断</h2></div><PencilLine :size="20" /></div>
          <div class="profile-form">
            <label class="form-field"><span>学历与专业</span><el-input v-model="resumeForm.education" placeholder="学历与专业" /></label>
            <label class="form-field"><span>实际岗位（优先）</span><el-select v-model="diagnosisJobId" clearable filterable placeholder="选择实际岗位；留空使用通用建议"><el-option v-for="job in jobs" :key="job.jobId" :label="`${job.title} · ${job.companyName}`" :value="job.jobId" /></el-select></label>
            <label class="form-field"><span>通用岗位名称</span><el-input v-model="targetRole" :disabled="Boolean(diagnosisJobId)" placeholder="目标岗位" /></label>
            <label class="form-field wide"><span>核心技能</span><el-input v-model="resumeForm.skills" type="textarea" :rows="4" placeholder="技能，使用逗号或换行分隔" /></label>
            <label class="form-field wide"><span>项目经历</span><el-input v-model="resumeForm.projects" type="textarea" :rows="4" placeholder="项目，使用逗号或换行分隔" /></label>
          </div>
          <div class="actions action-bar"><el-button type="primary" :loading="resumeActionLoading" @click="saveResumeProfile">保存资料</el-button><el-button :loading="resumeActionLoading" @click="runResumeAnalysis">重新诊断</el-button><el-button :loading="resumeActionLoading" @click="runResumeRewrite">生成改写</el-button></div>
          <p v-if="resumeProfileDirty" class="form-dirty-note">资料有未提交修改，草稿会在当前浏览器标签页保留。保存后才可重新诊断或生成改写。</p>
          <div v-if="resumeRewrite" class="rewrite-result">
            <div class="result-header"><strong>改写摘要</strong><el-tag :type="sourceTagType(undefined, resumeRewrite.mocked)">{{ sourceTagLabel(undefined, resumeRewrite.mocked) }}</el-tag></div>
            <div v-html="renderMarkdown(resumeRewrite.improvedSummary)" />
            <div class="tag-row"><el-tag v-for="keyword in resumeRewrite.keywordSuggestions" :key="keyword">{{ keyword }}</el-tag></div>
            <div v-if="resumeRewrite.rewrittenProjects.length" class="rewrite-detail"><strong>项目改写建议</strong><ul class="plain-list"><li v-for="project in resumeRewrite.rewrittenProjects" :key="project">{{ project }}</li></ul></div>
            <div v-if="resumeRewrite.missingEvidence.length" class="rewrite-detail"><strong>待补充证据</strong><ul class="plain-list"><li v-for="evidence in resumeRewrite.missingEvidence" :key="evidence">{{ evidence }}</li></ul></div>
            <div v-if="resumeRewrite.actionChecklist.length" class="rewrite-detail"><strong>行动清单</strong><ul class="plain-list"><li v-for="action in resumeRewrite.actionChecklist" :key="action">{{ action }}</li></ul></div>
          </div>
        </article>
        <aside class="panel diagnosis-panel">
          <div class="section-heading"><div><span class="eyebrow">ANALYSIS HISTORY</span><h2>诊断记录</h2></div><RefreshCw :size="20" /></div>
          <el-empty v-if="!diagnoses.length" description="完成诊断后将在这里展示" :image-size="84" />
          <div v-else class="diagnosis-list">
            <article v-for="diagnosis in diagnoses" :key="diagnosis.diagnosisId" class="diagnosis-item">
              <div class="diagnosis-item-head"><div><strong>{{ diagnosis.targetJob || '通用诊断' }}</strong><span>{{ diagnosis.createdAt }}</span></div><b>{{ diagnosis.score }}<small>分</small></b></div>
              <el-tag :type="sourceTagType(diagnosis.source)">{{ sourceTagLabel(diagnosis.source) }}</el-tag>
              <ResumeEvidencePanel v-if="diagnosis.details" :diagnosis="diagnosis.details" compact />
              <details v-if="diagnosis.details"><summary>原诊断文本与评分来源</summary><div class="diagnosis-copy" v-html="renderMarkdown(diagnosis.diagnosis)" /></details><div v-else class="diagnosis-copy" v-html="renderMarkdown(diagnosis.diagnosis)" />
            </article>
          </div>
        </aside>
      </section>
      </details>
    </template>

    <template v-else-if="activeModule === 'jobs'">
      <section class="overview-grid" v-loading="jobsLoading">
        <article class="overview-card accent-mint"><span>开放岗位</span><strong>{{ jobs.length }}</strong><BriefcaseBusiness :size="22" /></article>
        <article class="overview-card accent-lavender"><span>已完成匹配</span><strong>{{ matches.length }}</strong><Sparkles :size="22" /></article>
        <article class="overview-card accent-peach"><span>当前岗位技能</span><strong>{{ selectedJob?.requiredSkills.length || 0 }}</strong><GraduationCap :size="22" /></article>
        <article class="overview-card accent-plain"><span>当前覆盖率</span><strong>{{ currentMatch?.analysisSource === 'RULE_INSUFFICIENT_JOB_SKILLS' ? '—' : currentMatch?.score ?? '—' }}<small v-if="currentMatch && currentMatch.analysisSource !== 'RULE_INSUFFICIENT_JOB_SKILLS'">%</small></strong><TrendingUp :size="22" /></article>
      </section>

      <section class="jobs-layout" v-loading="jobsLoading">
        <article class="panel job-browser">
          <div class="section-heading"><div><span class="eyebrow">ROLE EXPLORER</span><h2>岗位与匹配</h2></div><span class="result-count">{{ filteredJobs.length }} 个结果</span></div>
          <el-input v-model="jobSearch" class="job-search" placeholder="搜索岗位、公司、城市或技能">
            <template #prefix><Search :size="17" /></template>
          </el-input>
          <div class="job-filter-row">
            <el-select v-model="jobCityFilter" clearable placeholder="城市"><el-option v-for="city in jobCities" :key="city" :label="city" :value="city" /></el-select>
            <el-select v-model="jobSkillFilter" clearable placeholder="技能"><el-option v-for="skill in jobSkills" :key="skill" :label="skill" :value="skill" /></el-select>
          </div>
           <div class="compare-toolbar">
             <el-select v-model="selectedCompareJobIds" multiple collapse-tags :max-collapse-tags="3" placeholder="选择 2-3 个岗位比较">
               <el-option v-for="job in jobs" :key="`compare-${job.jobId}`" :label="`${job.title} ? ${job.companyName}`" :value="job.jobId" />
             </el-select>
             <el-button type="primary" plain :loading="compareLoading" :disabled="selectedCompareJobIds.length < 2" @click="compareSelectedJobs">比较岗位</el-button>
           </div>
          <div class="job-card-list">
            <button v-for="job in filteredJobs" :key="job.jobId" class="job-card" :class="{ selected: job.jobId === selectedJobId }" @click="selectedJobId = job.jobId">
              <span class="job-card-mark">{{ job.companyName.slice(0, 1) }}</span>
              <span class="job-card-copy"><strong>{{ job.title }}</strong><small>{{ job.companyName }} · {{ job.city }}</small><em>{{ job.salaryRange }}</em></span>
              <ArrowUpRight :size="18" />
            </button>
          </div>
          <el-empty v-if="!filteredJobs.length" description="没有匹配的岗位" :image-size="88" />
        </article>

        <div class="job-detail-stack">
          <article v-if="compareResult" class="panel comparison-panel">
            <div class="section-heading"><div><span class="eyebrow">JOB COMPARISON</span><h2>岗位条件与证据比较</h2></div><el-button text @click="compareResult = undefined">关闭</el-button></div>
            <p class="form-dirty-note">各岗位使用同一份选定简历。覆盖率表示要求覆盖情况；缺少安排或岗位条件时保留“信息不足”。</p>
            <div class="comparison-grid">
              <article v-for="item in compareResult.jobs" :key="item.job.jobId">
                <strong>{{ item.job.title }}</strong><small>{{ item.job.companyName }} · {{ item.job.city }}</small>
                <p class="comparison-evidence">技能覆盖 {{ item.match.score }}% · 当前简历证据覆盖 {{ item.match.details?.evidenceCoverage ?? '—' }}%</p>
                <details v-for="req in item.requirements" :key="`${item.job.jobId}-${req.skill}`" class="comparison-requirement">
                  <summary><el-tag size="small" :type="req.tier === 'REQUIRED' ? 'danger' : req.tier === 'PREFERRED' ? 'warning' : 'info'">{{ ({ REQUIRED: '明确必需', PREFERRED: '明确优先', UNSPECIFIED: '未注明' } as Record<string, string>)[req.tier] || '未注明' }}</el-tag> {{ req.skill }}</summary>
                  <p>岗位原文：{{ req.quote || '技能清单中列出，正文未找到明确说明' }}</p>
                  <template v-for="evidence in item.availableEvidence.filter(e => e.skill === req.skill)" :key="evidence.skill">
                    <p>主资料：{{ evidence.declaredInMaster ? '已声明' : '尚未声明' }} · {{ evidence.supportedInMaster ? '已有材料支撑' : '材料中尚未体现实践' }}</p>
                    <p>{{ evidence.shownInResume ? '当前简历已体现实践证据' : evidence.supportedInMaster ? '主资料已有，当前简历未体现：建议补充已有经历的表达' : '待补材料：可先核对已有经历，再安排学习或练习' }}</p>
                    <p v-for="(source, index) in evidence.sources" :key="`${source.sourceId}-${index}`">资料依据：{{ source.quote || source.sourceId }}<span v-if="source.assessment"> · {{ source.assessment }}</span></p>
                  </template>
                </details>
                <p v-for="condition in item.conditions" :key="`${item.job.jobId}-${condition.type}`"><b>{{ ({ EDUCATION: '学历', LOCATION: '工作地点', GRADUATION: '毕业时间', START_DATE: '到岗日期', WEEKLY_DAYS: '每周出勤', CONTINUOUS_MONTHS: '连续实习时长' } as Record<string, string>)[condition.type] || condition.type }}</b>：{{ ({ SATISFIED: '满足', NOT_SATISFIED: '不满足', UNKNOWN: '信息不足' } as Record<string, string>)[condition.status] || '信息不足' }}<small>{{ condition.requirement }} · {{ condition.explanation }}</small></p>
              </article>
            </div>
          </article>
          <article class="panel job-detail">
            <template v-if="selectedJob">
              <div class="section-heading"><div><span class="eyebrow">SELECTED ROLE</span><h2>{{ selectedJob.title }}</h2></div><el-tag type="success">{{ selectedJob.status || 'OPEN' }}</el-tag></div>
              <div class="job-detail-meta"><span><BriefcaseBusiness :size="15" />{{ selectedJob.companyName }}</span><span><MapPin :size="15" />{{ selectedJob.city }}</span><strong>{{ selectedJob.salaryRange }}</strong></div>
              <p>{{ selectedJob.description }}</p>
              <div class="tag-row"><el-tag v-for="skill in selectedJob.requiredSkills" :key="skill">{{ skill }}</el-tag></div>
            </template>
            <el-empty v-else description="请选择一个岗位" :image-size="84" />
          </article>
          <article class="panel match-launcher">
            <div><span class="eyebrow">SKILL COVERAGE</span><h2>开始匹配</h2><p>选择简历与岗位，查看已具备的技能证据和下一步建议。</p></div>
            <div class="match-controls">
              <el-select v-model="selectedResumeId" placeholder="选择简历" @change="selectResume"><el-option v-for="resume in resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" /></el-select>
              <el-select v-model="selectedJobId" placeholder="选择岗位"><el-option v-for="job in jobs" :key="job.jobId" :label="`${job.title} · ${job.companyName}`" :value="job.jobId" /></el-select>
              <el-button type="primary" :loading="matchLoading" @click="runMatch">匹配</el-button>
            </div>
          </article>
        </div>
      </section>

      <section class="match-history-grid">
        <article v-if="currentMatch" class="panel match-result">
          <div class="section-heading"><div><span class="eyebrow">LATEST RESULT</span><h2>本次匹配结果</h2></div><CheckCircle2 :size="21" /></div>
          <MatchEvidencePanel v-if="currentMatch.details" :details="currentMatch.details" />
          <div v-else class="coverage-score"><strong>{{ matchScoreLabel(currentMatch) }}</strong><div><b>技能覆盖率</b><span>{{ currentMatch.analysisSource === 'RULE_INSUFFICIENT_JOB_SKILLS' ? '岗位要求缺少可比技能，暂不生成覆盖率。' : '根据岗位要求与简历技能计算' }}</span></div></div>
          <div class="tag-row"><el-tag :type="sourceTagType(currentMatch.analysisSource)">{{ matchSourceLabel(currentMatch.analysisSource) }}</el-tag></div>
          <div class="match-insights"><div><span>优势</span><p>{{ currentMatch.strengths.join('；') || '等待匹配结果' }}</p></div><div><span>待补齐</span><p>{{ currentMatch.gaps.join('；') || '暂无明显缺口' }}</p></div></div>
          <div v-if="currentMatch.matchedSkills?.length || currentMatch.missingSkills?.length" class="tag-row"><el-tag v-for="skill in currentMatch.matchedSkills" :key="`matched-${skill}`" type="success">已声明 · {{ skill }}</el-tag><el-tag v-for="skill in currentMatch.missingSkills" :key="`missing-${skill}`" type="warning">待补齐 · {{ skill }}</el-tag></div>
          <ul v-if="currentMatch.suggestions.length" class="plain-list"><li v-for="suggestion in currentMatch.suggestions" :key="suggestion">{{ suggestion }}</li></ul>
          <el-alert v-if="currentMatchStale" title="这条历史记录的简历资料或岗位要求已变化，或来源已不可用，请重新匹配后生成计划或面试。" type="warning" :closable="false" show-icon />
          <div class="match-next-actions"><el-button :disabled="currentMatchStale" @click="openMatchWorkspace('plan')">生成学习计划 <ArrowUpRight :size="15" /></el-button><el-button type="primary" :disabled="currentMatchStale" @click="openMatchWorkspace('interview')">进入模拟面试 <ArrowUpRight :size="15" /></el-button></div>
        </article>
        <article class="panel match-history">
          <div class="section-heading"><div><span class="eyebrow">MATCH ARCHIVE</span><h2>匹配覆盖</h2></div><Sparkles :size="20" /></div>
          <el-empty v-if="!matches.length" description="尚无匹配记录" :image-size="76" />
          <div v-else class="match-records"><button v-for="match in matches" :key="match.matchId" class="match-record" :data-match-id="match.matchId" @click="restoreMatch(match)"><div><strong>{{ jobs.find((job) => job.jobId === match.jobId)?.title || match.jobId }}</strong><span>{{ matchSourceLabel(match.analysisSource) }}</span></div><b>{{ matchScoreLabel(match) }}</b></button></div>
        </article>
      </section>
    </template>

    <template v-else-if="activeModule === 'plan'">
      <section class="overview-grid" v-loading="planLoading">
        <article class="overview-card accent-mint"><span>完成任务</span><strong>{{ selectedPlanCompletedTasks }}<small>/{{ selectedPlan?.tasks.length || 0 }}</small></strong><CheckCircle2 :size="22" /></article>
        <article class="overview-card accent-lavender"><span>当前进度</span><strong>{{ selectedPlanProgress }}<small>%</small></strong><TrendingUp :size="22" /></article>
        <article class="overview-card accent-peach"><span>每周投入</span><strong>{{ selectedPlan?.weeklyHours || planForm.weeklyHours }}<small>h</small></strong><Clock3 :size="22" /></article>
        <article class="overview-card accent-plain"><span>计划周期</span><strong>{{ selectedPlan?.durationWeeks || planForm.durationWeeks }}<small>周</small></strong><CalendarDays :size="22" /></article>
      </section>

      <section class="plan-builder panel" v-loading="planLoading">
        <div class="section-heading"><div><span class="eyebrow">PERSONAL ROADMAP</span><h2>学习计划</h2><p>按可投入时间生成与目标岗位关联的练习节奏。</p></div><Route :size="22" /></div>
        <div v-if="plans.length && !planBuilderOpen" class="plan-builder-collapsed">
          <div class="plan-builder-collapsed-copy">
            <strong>{{ selectedPlan?.targetRole || planForm.targetRole || '学习计划设置' }}</strong>
            <span>{{ selectedPlan?.weeklyHours || planForm.weeklyHours }} 小时/周 · {{ selectedPlan?.durationWeeks || planForm.durationWeeks }} 周</span>
            <small v-if="activePlanSchedule.startDate">从 {{ activePlanSchedule.startDate }} 开始 · 每天最多 {{ activePlanSchedule.dailyMinutesCap || planForm.dailyMinutesCap }} 分钟</small>
          </div>
          <el-button size="small" @click="planBuilderOpen = true">调整计划</el-button>
        </div>
        <div v-if="planBuilderOpen || !plans.length" class="plan-builder-fields">
          <label class="form-field"><span>简历来源</span><el-select v-model="selectedResumeId" placeholder="选择简历" @change="selectResume"><el-option v-for="resume in resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" /></el-select></label>
          <label class="form-field"><span>岗位来源</span><el-select v-model="selectedJobId" placeholder="选择岗位"><el-option v-for="job in jobs" :key="job.jobId" :label="`${job.title} · ${job.companyName}`" :value="job.jobId" /></el-select></label>
          <label class="form-field"><span>目标岗位</span><el-input v-model="planForm.targetRole" placeholder="目标岗位" /></label>
          <label class="form-field"><span>每周投入（小时）</span><el-input-number v-model="planForm.weeklyHours" :min="2" :max="40" controls-position="right" /></label>
          <label class="form-field"><span>计划周期（周）</span><el-input-number v-model="planForm.durationWeeks" :min="1" :max="24" controls-position="right" /></label>
          <details class="plan-advanced-settings">
            <summary>高级安排（可选）</summary>
            <div class="plan-advanced-fields">
              <label class="form-field"><span>开始日期</span><el-date-picker v-model="planForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="默认今天" /></label>
              <label class="form-field"><span>提醒时间</span><el-time-picker v-model="planForm.reminderTime" value-format="HH:mm" format="HH:mm" placeholder="每天提醒" /></label>
              <label class="form-field"><span>每周学习日</span><el-select v-model="planForm.studyDays" multiple collapse-tags placeholder="选择学习日"><el-option label="周一" value="MONDAY" /><el-option label="周二" value="TUESDAY" /><el-option label="周三" value="WEDNESDAY" /><el-option label="周四" value="THURSDAY" /><el-option label="周五" value="FRIDAY" /><el-option label="周六" value="SATURDAY" /><el-option label="周日" value="SUNDAY" /></el-select></label>
              <label class="form-field"><span>每天上限（分钟）</span><el-input-number v-model="planForm.dailyMinutesCap" :min="30" :max="480" controls-position="right" /></label>
            </div>
          </details>
          <el-button type="primary" :loading="planActionLoading" @click="createPlan">生成学习计划 <ArrowUpRight :size="16" /></el-button>
        </div>
      </section>

      <p class="plan-context">{{ selectedContextMatch ? `已关联匹配：${matchScoreLabel(selectedContextMatch)} · ${selectedContextMatch.matchId}` : '无当前技能对应的匹配记录，计划将基于已选简历、岗位和目标岗位生成。' }}</p>
      <section v-if="plans.length" class="plan-layout">
        <aside class="panel plan-sidebar">
          <div class="section-heading"><div><span class="eyebrow">PLAN VERSION</span><h2>计划版本</h2></div><RefreshCw :size="20" /></div>
          <el-select v-model="selectedPlanId" placeholder="选择学习计划" @change="loadPlanVersions"><el-option v-for="plan in plans" :key="plan.planId" :label="`${plan.targetRole} · V${plan.version} · ${planStatusLabel(plan.status)}`" :value="plan.planId" /></el-select>
          <div v-if="selectedPlan" class="plan-summary-card">
            <div><span>{{ selectedPlan.targetRole }}</span><strong>V{{ selectedPlan.version }}</strong></div>
            <p>{{ selectedPlan.weeklyHours }} 小时/周 · {{ selectedPlan.durationWeeks }} 周</p>
            <el-progress :percentage="selectedPlanProgress" :show-text="false" :stroke-width="8" color="#28664f" />
            <div class="tag-row"><el-tag :type="selectedPlanIsActive ? 'success' : 'info'">{{ planStatusLabel(selectedPlan.status) }}{{ selectedPlanIsActive ? ' · 当前可编辑版本' : ' · 历史只读版本' }}</el-tag><el-tag :type="sourceTagType(undefined, selectedPlan.mocked)">{{ sourceTagLabel(undefined, selectedPlan.mocked) }}</el-tag></div>
          </div>
          <div class="version-rail"><span v-for="version in planVersions" :key="version.planId" :class="{ current: version.planId === selectedPlanId }">V{{ version.version }}</span></div>
          <div v-if="planVersions.length" class="version-actions"><el-button v-for="version in planVersions" :key="version.planId" :data-plan-id="version.planId" size="small" :type="version.planId === selectedPlanId ? 'primary' : 'default'" @click="selectedPlanId = version.planId; loadPlanVersions()">V{{ version.version }} · {{ planStatusLabel(version.status) }}</el-button></div>
          <el-alert v-if="selectedPlan && !selectedPlanIsActive" title="当前选择的是历史版本，任务和重新规划均为只读。" type="info" :closable="false" show-icon />
          <details v-if="selectedPlan?.revisionReason" class="revision-reason">
            <summary>调整原因：{{ summarizeText(selectedPlan.revisionReason) }}</summary>
            <p>{{ selectedPlan.revisionReason }}</p>
          </details>
          <div class="schedule-summary"><strong>本周安排</strong><span>{{ weekTasks.length }} 项 · {{ activePlanSchedule.startDate ? `第 ${currentPlanWeek} 周` : '尚未设置日期' }}</span><small v-if="todayPlanData?.reminders?.length">{{ todayPlanData.reminders.join('；') }}</small><small v-else-if="upcomingReminders.length">每天 {{ activePlanSchedule.reminderTime || '20:00' }} 提醒未完成任务</small><small v-else>暂无待提醒任务</small><small v-if="weeklyReviewData">上周实际 {{ weeklyReviewData.actualMinutes }} 分钟 · 完成 {{ weeklyReviewData.completedTasks }} 项 · 延期 {{ weeklyReviewData.delayedTasks }} 项</small></div>
          <div class="replan-form"><span>调整节奏与周复盘</span><el-input v-model="planForm.replanReason" :disabled="!selectedPlanIsActive" type="textarea" :rows="3" placeholder="计划变化或复盘原因" /><el-input v-model="weeklyReviewDraft" :disabled="!selectedPlanIsActive" type="textarea" :rows="3" placeholder="本周复盘：完成了什么、哪里卡住、下周准备怎么调整" /><el-select v-model="selectedCompletedSessionId" :disabled="!selectedPlanIsActive" clearable placeholder="选择同目标的已完成面试会话（可选)"><el-option v-for="session in compatibleCompletedSessions" :key="session.sessionId" :label="`${session.targetRole} · ${session.completedAt || session.updatedAt}`" :value="session.sessionId" /></el-select><div class="schedule-actions"><el-button :disabled="!selectedPlanIsActive" @click="savePlanSchedule">保存安排与复盘</el-button><el-button :disabled="!selectedPlanIsActive" :loading="planActionLoading" @click="replan">重新规划并预览</el-button></div><small v-if="weeklyReviewSaved" class="saved-note">最近已保存本周复盘</small></div>
        </aside>
        <article class="panel task-panel">
          <el-alert title="完成状态为自报进度；成果评价单独记录，不会自动更新已掌握技能。" type="info" :closable="false" />
          <div class="section-heading"><div><h2>任务进度</h2><p class="task-context">今日 {{ todayPlanData?.tasks.length ?? todayTasks.length }} 项 · 本周 {{ weekTasks.length }} 项<span v-if="overdueTasks.length"> · 逾期 {{ overdueTasks.length }} 项</span></p></div><div class="progress-text"><strong>{{ selectedPlanProgress }}%</strong><span>{{ selectedPlanCompletedTasks }}/{{ selectedPlan?.tasks.length || 0 }} 已完成</span></div></div>
          <div v-if="todayTasks.length || todayPlanData?.tasks.length" class="today-strip"><strong>今天先做</strong><span v-for="task in (todayPlanData?.tasks || todayTasks).slice(0, 3)" :key="task.taskId">{{ task.title }}</span></div>
          <el-alert v-if="overdueTasks.length" title="有任务已经超过安排日期，请完成、延期或跳过后再继续。" type="warning" :closable="false" />
          <el-empty v-if="!selectedPlan" description="请选择学习计划" :image-size="88" />
          <div v-else class="task-list">
            <div v-for="task in selectedPlan.tasks" :key="task.taskId" class="task-row" :class="{ 'task-today': todayTasks.some((item) => item.taskId === task.taskId), 'task-overdue': overdueTasks.some((item) => item.taskId === task.taskId) }">
              <div class="task-main"><span class="week-chip">W{{ task.week }}</span><div><div class="task-title-line"><strong>{{ task.title }}</strong><el-tag size="small" :type="taskDisplayStatus(task) === 'COMPLETED' ? 'success' : taskDisplayStatus(task) === 'IN_PROGRESS' ? 'primary' : taskDisplayStatus(task) === 'PAUSED' || taskDisplayStatus(task) === 'DEFERRED' ? 'warning' : 'info'">{{ taskActionLabel(task) }}</el-tag></div><details class="task-details"><summary>查看任务详情</summary><p>{{ task.description }}</p><div class="task-detail-lines"><small>安排：{{ taskSchedule(task).scheduledDate || `第 ${task.week} 周` }}</small><small v-if="taskSchedule(task).actualMinutes">实际：{{ taskSchedule(task).actualMinutes }} 分钟</small><small v-if="task.stage">{{ learningStageLabel(task.stage) }}</small><small v-if="task.skillGap">缺口：{{ task.skillGap }}</small><small v-if="task.acceptanceCriteria">验收：{{ task.acceptanceCriteria }}</small><small v-if="task.practiceDeliverable">交付：{{ task.practiceDeliverable }}</small></div></details></div></div>
              <span class="task-hours"><Clock3 :size="14" />{{ task.estimatedHours }}h</span>
              <div class="task-actions"><el-button size="small" :disabled="!selectedPlanIsActive || taskSaving(task.taskId) || taskDisplayStatus(task) === 'IN_PROGRESS'" @click="performTaskAction(task, 'START')">开始</el-button><el-button size="small" :disabled="!selectedPlanIsActive || taskSaving(task.taskId) || taskDisplayStatus(task) !== 'IN_PROGRESS'" @click="performTaskAction(task, 'PAUSE')">暂停</el-button><el-button size="small" type="success" plain :disabled="!selectedPlanIsActive || taskSaving(task.taskId) || taskDisplayStatus(task) === 'COMPLETED'" @click="performTaskAction(task, 'COMPLETE')">完成</el-button><el-button size="small" text :disabled="!selectedPlanIsActive || taskSaving(task.taskId) || taskDisplayStatus(task) === 'COMPLETED'" @click="performTaskAction(task, 'DEFER')">延期一天</el-button></div>
              <details class="task-management-details">
                <summary>{{ task.evidence?.length ? '查看进度与成果' : '记录复盘或提交成果' }}</summary>
                <div class="task-management-fields">
                  <el-select class="task-status-select" :disabled="!selectedPlanIsActive || taskSaving(task.taskId)" :model-value="task.status === 'TODO' ? 'PENDING' : task.status" @update:model-value="saveTask(task.taskId, String($event))"><el-option label="待开始" value="PENDING" /><el-option label="进行中" value="IN_PROGRESS" /><el-option label="已完成" value="COMPLETED" /><el-option label="已跳过" value="SKIPPED" /></el-select>
                  <el-input v-model="taskFeedback[task.taskId]" :disabled="!selectedPlanIsActive || taskSaving(task.taskId)" placeholder="复盘备注" @change="saveTask(task.taskId, task.status)" />
                  <LearningEvidenceForm :plan-id="selectedPlan.planId" :task="task" :readonly="selectedPlan.status !== 'ACTIVE' && selectedPlan.status !== 'COMPLETED'" @saved="recordTaskEvidence" @resume-candidate="openResumeCandidateFromEvidence" @interview-follow-up="openInterviewFollowUpFromEvidence" />
                  <small v-if="taskSaving(task.taskId)" class="task-save-state">正在保存…</small><small v-else-if="taskErrors[task.taskId]" class="task-save-state error">{{ taskErrors[task.taskId] }} <el-button link type="primary" :disabled="!selectedPlanIsActive" @click="saveTask(task.taskId, taskRetryStatus[task.taskId] || task.status)">重试</el-button></small>
                </div>
              </details>
            </div>
          </div>
        </article>
      </section>
      <el-empty v-else description="尚未生成学习计划" :image-size="92" />
    </template>

    <template v-else-if="activeModule === 'interview'">
      <section class="overview-grid" v-loading="interviewLoading">
        <article class="overview-card accent-mint"><span>模拟会话</span><strong>{{ interviewSessions.length }}</strong><Bot :size="22" /></article>
        <article class="overview-card accent-lavender"><span>当前完成度</span><strong>{{ selectedSessionProgress }}<small>%</small></strong><CircleDashed :size="22" /></article>
        <article class="overview-card accent-peach"><span>本次题目</span><strong>{{ selectedSession?.questions.length || interviewQuestionCount }}</strong><BrainCircuit :size="22" /></article>
        <article class="overview-card accent-plain"><span>已保存回答</span><strong>{{ selectedSession?.answers.length || 0 }}</strong><FileText :size="22" /></article>
      </section>

      <section class="interview-launch panel" v-loading="interviewLoading">
        <div><span class="eyebrow">AI INTERVIEW STUDIO</span><h2>模拟面试会话</h2><p>围绕目标岗位生成问题，逐题保存作答并在完成后查看报告。</p></div>
        <div class="interview-launch-actions"><label class="target-role-editor"><span>本次目标岗位</span><el-input v-model="interviewTargetRole" placeholder="例如 Java 后端" /></label><label><span>题目数量</span><el-input-number v-model="interviewQuestionCount" :min="1" :max="8" controls-position="right" aria-label="面试题数" /></label><el-button type="primary" :loading="interviewActionLoading" @click="startInterview"><Bot :size="16" />开始模拟面试</el-button><el-button @click="router.push({ path: '/student/interview', query: { tab: 'history' } })">会话历史</el-button><el-button @click="router.push('/student/interview')">当前会话</el-button></div>
      </section>

      <section v-if="interviewHistoryOpen" class="panel interview-history">
        <div class="section-heading"><div><span class="eyebrow">SESSION ARCHIVE</span><h2>面试记录</h2></div><RefreshCw :size="20" /></div>
        <el-empty v-if="!interviewSessions.length" description="暂无模拟面试记录" :image-size="92" />
        <div v-else class="session-grid"><button v-for="session in interviewSessions" :key="session.sessionId" class="session-card" :data-session-id="session.sessionId" :class="{ selected: session.sessionId === selectedSessionId }" @click="selectedSessionId = session.sessionId; selectSession(); router.push('/student/interview')"><div><span class="session-icon"><Bot :size="18" /></span><strong>{{ session.targetRole }}</strong></div><span>{{ session.status }} · {{ session.answers.length }}/{{ session.questions.length }} 题</span><div class="session-card-foot"><el-tag :type="sourceTagType(undefined, session.mocked)">{{ sourceTagLabel(undefined, session.mocked) }}</el-tag><ArrowUpRight :size="17" /></div></button></div>
      </section>
      <section v-else-if="selectedSession && activeQuestion" class="interview-workspace">
        <article class="panel interview-question-card">
          <div class="question-topline"><span>问题 {{ activeQuestionIndex + 1 }} / {{ selectedSession.questions.length }}</span><span>{{ selectedSessionProgress }}% 已作答</span></div>
          <div class="section-heading"><div><span class="eyebrow">QUESTION ROOM</span><h2>模拟面试</h2></div><BrainCircuit :size="22" /></div>
          <div class="tag-row"><el-tag type="info">{{ activeQuestion.category || '综合' }}</el-tag><el-tag>{{ activeQuestion.difficulty || '普通' }}</el-tag><el-tag :type="sourceTagType(activeQuestion.source || activeQuestion.generationSource, selectedSession.mocked)">{{ sourceTagLabel(activeQuestion.source || activeQuestion.generationSource, selectedSession.mocked) }}</el-tag></div>
          <p class="question-text">{{ activeQuestion.question }}</p>
          <div v-if="activeQuestion.referencePoints?.length" class="reference-points"><span>答题参考</span><ul class="plain-list"><li v-for="point in activeQuestion.referencePoints" :key="point">{{ point }}</li></ul></div>
          <div class="question-nav"><el-button v-for="(_, index) in selectedSession.questions" :key="index" size="small" :type="index === activeQuestionIndex ? 'primary' : 'default'" @click="activeQuestionIndex = index">第 {{ index + 1 }} 题</el-button></div>
        </article>
        <article class="panel answer-card">
          <div class="section-heading"><div><span class="eyebrow">YOUR RESPONSE</span><h2>我的回答</h2></div><PencilLine :size="21" /></div>
          <el-input v-model="currentAnswer" class="answer-input" type="textarea" :rows="13" :readonly="activeQuestionLocked || interviewActionLoading" :placeholder="activeQuestionLocked ? '该题已保存或当前会话只读' : '输入回答，保存后可在会话中恢复'" />
          <p v-if="!activeQuestionLocked" class="form-dirty-note">草稿会在当前浏览器标签页保留，保存回答后才会提交至面试会话。</p>
          <InterviewFeedbackPanel v-if="activeQuestionFeedback" :feedback="activeQuestionFeedback" />
          <el-alert v-if="activeSavedAnswer && activeSavedAnswer.evaluationStatus !== 'SUCCEEDED' && activeSavedAnswer.evaluationStatus" type="warning" :closable="false" :title="interviewEvaluationError || activeSavedAnswer.evaluationError || '回答已保存，待完成评价。'" />
          <el-button v-if="activeSavedAnswer && evaluationCanRetry(activeSavedAnswer.evaluationStatus)" :loading="interviewEvaluatingId === activeSavedAnswer.questionId" :disabled="Boolean(interviewEvaluatingId)" @click="evaluateSavedAnswer(selectedSession.sessionId, activeSavedAnswer.questionId)">评价已保存回答 / 重试</el-button>
          <div class="answer-actions"><el-button type="primary" :disabled="activeQuestionLocked || !currentAnswer.trim()" :loading="interviewActionLoading || Boolean(interviewEvaluatingId)" @click="saveCurrentAnswer">保存回答</el-button><el-button :disabled="Boolean(interviewEvaluatingId) || pendingInterviewEvaluations > 0 || selectedSession.status !== 'IN_PROGRESS' || unfinishedInterviewQuestions > 1 || (unfinishedInterviewQuestions === 1 && (activeQuestionLocked || !currentAnswer.trim()))" :loading="interviewActionLoading" @click="finishInterview">完成并生成报告</el-button></div>
        </article>
      </section>
      <el-empty v-else-if="!interviewHistoryOpen" description="开始一次模拟面试后可在此继续作答" :image-size="92" />
      <section v-if="sessionReport" class="panel interview-report">
        <div class="report-score"><div><span class="eyebrow">SESSION REPORT</span><h2>面试报告</h2><el-tag :type="sourceTagType(undefined, sessionReport.mocked)">{{ sourceTagLabel(undefined, sessionReport.mocked) }}</el-tag></div><strong>{{ sessionReport.overallScore }}<small>分</small></strong></div>
        <p class="form-dirty-note">{{ sessionReport.comparisonNote || '历史记录缺少评价版本，暂不进行分数比较。' }}</p>
        <p v-if="sessionReport.difficultyNote" class="form-dirty-note">{{ sessionReport.difficultyNote }}</p>
        <div v-if="comparisonSessions.length" class="version-actions"><span>同岗位、同评价版本的历史表现：</span><span v-for="session in comparisonSessions" :key="session.sessionId">{{ session.completedAt || session.updatedAt }} · {{ session.report?.overallScore }} 分</span></div>
        <details v-for="feedback in sessionReport.questionFeedback || []" :key="feedback.questionId" class="resume-diagnosis"><summary>逐题反馈 · {{ feedback.score }} 分</summary><InterviewFeedbackPanel :feedback="feedback" /></details>
        <div class="report-columns"><div><span>优势</span><ul class="plain-list"><li v-for="item in sessionReport.strengths" :key="item">{{ item }}</li></ul></div><div><span>待改进</span><ul class="plain-list"><li v-for="item in sessionReport.gaps" :key="item">{{ item }}</li></ul></div><div><span>建议</span><ul class="plain-list"><li v-for="item in sessionReport.recommendations" :key="item">{{ item }}</li></ul></div></div>
      </section>
    </template>

    <template v-else-if="activeModule === 'knowledge'">
      <section class="overview-grid knowledge-overview">
        <article class="overview-card accent-mint"><span>检索结果</span><strong>{{ knowledgeResultCount ?? '—' }}</strong><Search :size="22" /></article>
        <article class="overview-card accent-lavender"><span>引用片段</span><strong>{{ knowledgeAnswer?.citations.length || 0 }}</strong><Library :size="22" /></article>
        <article class="overview-card accent-peach"><span>回答来源</span><strong class="provider-value">{{ knowledgeAnswer?.provider || '—' }}</strong><Sparkles :size="22" /></article>
        <article class="overview-card accent-plain"><span>生成状态</span><strong>{{ knowledgeLoading ? '生成中' : knowledgeAnswer ? '已就绪' : '待提问' }}</strong><Compass :size="22" /></article>
      </section>
      <section class="panel knowledge-shell">
        <div class="knowledge-intro"><div><span class="eyebrow">RAG KNOWLEDGE BASE</span><p>检索岗位技能、面试问题和简历证据，并查看可追溯的引用来源。</p></div><span class="knowledge-orb"><Library :size="28" /></span></div>
        <h2 class="panel-title"><span>RAG 知识库问答</span><Library :size="19" /></h2>
        <div class="knowledge-mode"><span>回答模式</span><el-switch v-model="knowledgeUseAi" active-text="AI 回答" inactive-text="仅检索" /></div>
        <div class="knowledge-search">
          <el-input v-model="knowledgeQuery" placeholder="搜索 Java、Redis、面试或简历证据" @keyup.enter="runKnowledgeSearch" />
          <el-button type="primary" :loading="knowledgeLoading" @click="runKnowledgeSearch"><Search :size="17" />检索</el-button>
        </div>
        <div v-if="knowledgeRecentQueries.length" class="knowledge-history"><span>最近查询</span><el-button v-for="query in knowledgeRecentQueries" :key="query" text @click="knowledgeQuery = query; runKnowledgeSearch()">{{ query }}</el-button></div>
        <el-alert v-if="knowledgeError" class="knowledge-error" type="warning" :title="knowledgeError" :closable="false" show-icon><template #default><el-button link type="primary" @click="runKnowledgeSearch">重试</el-button></template></el-alert>
        <div v-if="knowledgeAnswer" class="knowledge-history"><el-tag type="info">{{ retrievalModeLabel(knowledgeAnswer.retrievalMode) }}</el-tag><el-tag>{{ knowledgeAnswer.generationMode === 'AI' ? 'AI 回答' : '检索资料' }}</el-tag><span>{{ knowledgeAnswer.algorithmVersion }}</span></div>
        <el-alert v-if="knowledgeAnswer && ['NO_EVIDENCE', 'INSUFFICIENT_EVIDENCE', 'INSUFFICIENT'].includes(knowledgeAnswer.evidenceStatus || '')" title="现有资料不足以支持完整回答，请核对检索资料或补充知识库。" type="info" :closable="false" />
        <section v-if="knowledgeRetrieval" class="knowledge-retrieval"><header><strong>检索摘要</strong><span>{{ knowledgeRetrieval.results.length }} 条</span></header><el-empty v-if="!knowledgeRetrieval.results.length" description="未检索到可引用资料" :image-size="64" /><article v-for="result in knowledgeRetrieval.results" v-else :key="result.id" class="retrieval-result"><div><strong>{{ result.title }}</strong><span>{{ result.type }} · {{ result.owner }} · {{ result.score }} 分</span></div><p>{{ result.summary }}</p><small v-if="result.citation">{{ citationLocation(result.citation) }} · {{ result.citation.source }}</small><div class="tag-row"><el-tag v-for="highlight in result.highlights" :key="highlight" type="info">{{ highlight }}</el-tag></div></article></section>
        <div v-if="knowledgeAnswer" class="rag-answer">
          <header><strong>{{ knowledgeAnswerLabel() }}</strong><el-tag :type="knowledgeAnswer.mocked ? 'warning' : 'success'">{{ knowledgeAnswer.provider }}</el-tag></header>
          <div class="knowledge-answer" v-html="renderMarkdown(knowledgeAnswer.answer)" />
          <div v-if="knowledgeAnswer.claims?.length" class="claim-list"><strong>可核对的事实项</strong><article v-for="(claim, index) in knowledgeAnswer.claims" :key="`${claim.text}-${index}`"><p>{{ claim.text }}</p><small>引用：{{ claim.citationIds?.join('、') || '未关联引用' }}</small><blockquote v-if="claim.supportQuote">{{ claim.supportQuote }}</blockquote></article></div>
          <div v-if="knowledgeAnswer.citations.length" class="citation-list">
            <details v-for="(citation, index) in knowledgeAnswer.citations" :key="citation.chunkId" class="citation-row">
              <summary>[{{ index + 1 }}] {{ citation.title }}</summary>
              <p>{{ citation.source }} · {{ citation.score }} 分</p><p>{{ citationLocation(citation) }}</p>
              <div v-html="renderMarkdown(citation.snippet)" />
            </details>
          </div>
        </div>
        <el-empty v-if="!knowledgeAnswer && !knowledgeRetrieval && !knowledgeError && !knowledgeLoading" description="输入关键词后检索知识库" />
      </section>
    </template>
    <el-dialog v-model="replanPreviewOpen" title="核对新计划并确认切换" width="min(760px, 94vw)">
      <template v-if="replanPreview"><p>确认后启用 V{{ replanPreview.version }}；原版本和已完成成果仍可查看。</p><p>调整原因：{{ replanPreview.revisionReason || planForm.replanReason }}</p><p>每周 {{ replanPreview.weeklyHours }} 小时 · {{ replanPreview.durationWeeks }} 周</p><div class="task-list"><article v-for="task in replanPreview.tasks" :key="task.taskId" class="preview-task"><strong>第 {{ task.week }} 周 · {{ task.title }} · {{ task.estimatedHours }}h</strong><el-tag v-if="task.status === 'COMPLETED'" type="success">已完成成果保留</el-tag><p>{{ task.description }}</p><p>{{ task.acceptanceCriteria }}</p></article></div></template>
      <template #footer><el-button @click="replanPreviewOpen = false">继续当前计划</el-button><el-button type="primary" :loading="planActionLoading" @click="confirmReplan">确认切换计划版本</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.legacy-resume-history{margin-top:20px;border-top:1px solid var(--line,#e5ebe7);padding-top:16px}
.legacy-resume-history>summary{color:var(--muted,#66716c);font-size:13px;cursor:pointer;width:fit-content;margin-bottom:18px}
.workspace-header.compact-workspace-header{justify-content:flex-end;align-items:center;padding:0}
.workspace-header.compact-workspace-header .target-role-control{display:block;width:min(318px,100%)}
.claim-list { display: grid; gap: 9px; margin: 12px 0; padding: 13px; border-radius: 10px; background: #f5f9f6; font-size: 13px; }
.claim-list article { padding: 9px; border: 1px solid #e5ebe7; border-radius: 8px; background: #fff; }
.claim-list p { margin: 0 0 4px; line-height: 1.7; white-space: pre-wrap; }
.claim-list small { color: #66716c; }
.claim-list blockquote { margin: 7px 0 0; padding-left: 10px; border-left: 3px solid #8db7a2; color: #66716c; white-space: pre-wrap; }
.preview-task { padding: 12px; border: 1px solid #e5ebe7; border-radius: 10px; font-size: 13px; line-height: 1.7; }
.preview-task .el-tag { margin-left: 10px; }
.student-workspace {
  display: grid;
  gap: 22px;
  min-width: 0;
  color: var(--ink, #1f2724);
}

.workspace-header,
.section-heading,
.result-header,
.job-detail-meta,
.question-topline,
.report-score,
.diagnosis-item-head,
.match-record,
.session-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.workspace-header {
  align-items: end;
  padding: 5px 2px 2px;
}

.header-copy,
.section-heading > div,
.knowledge-intro > div,
.resume-summary,
.job-card-copy,
.task-main > div,
.diagnosis-item-head > div,
.report-score > div {
  min-width: 0;
}

.eyebrow {
  display: inline-block;
  color: var(--accent, #28664f);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  line-height: 1.2;
}

.workspace-header h1,
.section-heading h2,
.knowledge-shell h2,
.report-score h2 {
  margin: 5px 0 0;
  color: var(--ink, #1f2724);
  font-size: 28px;
  font-weight: 750;
  letter-spacing: 0;
  line-height: 1.2;
}

.workspace-header p,
.section-heading p,
.knowledge-intro p,
.interview-launch p,
.match-launcher p,
.job-detail p,
.knowledge-empty p {
  margin: 7px 0 0;
  color: var(--muted, #66716c);
  font-size: 14px;
  line-height: 1.65;
}

.target-role-control,
.form-field,
.interview-launch-actions label {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.target-role-control {
  width: min(318px, 100%);
}

.target-role-control > span,
.form-field > span,
.interview-launch-actions label > span {
  color: var(--muted, #66716c);
  font-size: 12px;
  font-weight: 700;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.overview-card,
.panel {
  border: 1px solid var(--line, #e8ebea);
  border-radius: 16px;
  background: var(--surface, #fff);
  box-shadow: 0 1px 2px rgba(32, 43, 38, 0.025);
}

.overview-card {
  position: relative;
  display: grid;
  min-height: 126px;
  align-content: space-between;
  padding: 18px;
  overflow: hidden;
}

.overview-card::after {
  position: absolute;
  right: -18px;
  bottom: -24px;
  width: 78px;
  height: 78px;
  border: 1px solid rgba(40, 102, 79, 0.1);
  border-radius: 50%;
  content: '';
}

.overview-card span {
  color: var(--muted, #66716c);
  font-size: 13px;
  font-weight: 650;
}

.overview-card strong {
  color: var(--ink, #1f2724);
  font-size: 31px;
  font-weight: 760;
  line-height: 1;
}

.overview-card strong small {
  margin-left: 2px;
  color: var(--muted, #66716c);
  font-size: 14px;
  font-weight: 650;
}

.overview-card > svg {
  position: absolute;
  top: 18px;
  right: 18px;
  color: var(--accent, #28664f);
}

.overview-card.accent-mint { background: #c8f1df; }
.overview-card.accent-lavender { background: #ebe8fa; }
.overview-card.accent-peach { background: #fff0e5; }
.overview-card.accent-plain { background: var(--surface, #fff); }
.overview-card .provider-value { font-size: 18px; overflow-wrap: anywhere; }

.resume-hero,
.plan-builder,
.interview-launch,
.knowledge-shell {
  padding: 24px;
}

.section-heading {
  align-items: flex-start;
}

.section-heading > svg {
  flex: 0 0 auto;
  color: var(--accent, #28664f);
}

.section-heading h2,
.knowledge-shell h2,
.report-score h2 {
  font-size: 20px;
}

.upload-control {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 13px;
  border-radius: 8px;
  background: var(--ink, #1f2724);
  color: #fff;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
}

.upload-control input { display: none; }

.resume-hero-content {
  display: grid;
  grid-template-columns: minmax(180px, 0.72fr) 122px minmax(260px, 1.2fr) minmax(180px, 0.72fr);
  gap: 22px;
  align-items: center;
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid var(--line, #e8ebea);
}

.resume-picker { display: grid; gap: 7px; }
.resume-picker > span,
.resume-status > span { color: var(--muted, #66716c); font-size: 12px; font-weight: 700; }
.resume-picker :deep(.el-button) { justify-self: start; padding: 0; color: var(--muted, #66716c); }

.resume-score {
  display: grid;
  grid-template-columns: auto auto;
  align-items: baseline;
  gap: 3px;
  padding-left: 22px;
  border-left: 1px solid var(--line, #e8ebea);
}

.resume-score span { grid-column: 1 / -1; color: var(--muted, #66716c); font-size: 12px; font-weight: 700; }
.resume-score strong { color: var(--accent, #28664f); font-size: 44px; line-height: 1; }
.resume-score small { color: var(--muted, #66716c); font-size: 12px; }
.resume-summary strong { display: block; margin-bottom: 7px; font-size: 15px; }
.resume-summary :deep(p) { margin: 0; color: var(--muted, #66716c); font-size: 13px; line-height: 1.55; }
.resume-diagnosis { margin-top: 18px; padding-top: 8px; border-top: 1px solid var(--line); }
.resume-diagnosis > summary { width: fit-content; font-weight: 600; }
.diagnosis-report-copy { padding: 16px 20px; background: #f6faf7; border-radius: 10px; color: #55655b; font-size: 13px; line-height: 1.85; }
.diagnosis-report-copy :deep(p) { margin: 8px 0; }
.resume-status { display: grid; gap: 10px; }
.resume-summary,
.resume-summary strong,
.diagnosis-copy,
.job-card-copy,
.job-card-copy strong,
.match-record span,
.knowledge-answer,
.citation-row,
.citation-row summary,
.citation-row div { max-width: 100%; overflow-wrap: anywhere; word-break: break-word; }
.tag-row :deep(.el-tag),
.diagnosis-item :deep(.el-tag) { max-width: 100%; height: auto; }
.tag-row :deep(.el-tag__content),
.diagnosis-item :deep(.el-tag__content) { white-space: normal; overflow-wrap: anywhere; word-break: break-word; }

.resume-workspace,
.jobs-layout,
.match-history-grid,
.plan-layout,
.interview-workspace {
  display: grid;
  gap: 18px;
}

.resume-workspace { grid-template-columns: minmax(0, 1.25fr) minmax(330px, 0.75fr); }
.profile-panel,
.diagnosis-panel,
.job-browser,
.job-detail,
.match-launcher,
.plan-sidebar,
.task-panel,
.interview-question-card,
.answer-card,
.interview-history,
.interview-report { padding: 22px; }

.profile-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 22px;
}

.form-field.wide { grid-column: 1 / -1; }
.action-bar { margin-top: 18px; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }

.rewrite-result {
  display: grid;
  gap: 12px;
  margin-top: 20px;
  padding: 16px;
  border: 1px solid #d7eadf;
  border-radius: 12px;
  background: #f5fbf7;
}

.result-header strong { font-size: 14px; }
.rewrite-result :deep(p) { margin: 0; color: var(--muted, #66716c); line-height: 1.6; }
.rewrite-detail { padding-top: 10px; border-top: 1px solid var(--line, #e8ebea); }
.rewrite-detail strong { font-size: 12px; }
.form-dirty-note, .plan-context { margin: 0; color: #a35f23; font-size: 12px; }
.plan-context { color: var(--muted, #66716c); }
.tag-row { display: flex; flex-wrap: wrap; gap: 7px; }

.diagnosis-list { display: grid; gap: 12px; margin-top: 20px; }
.diagnosis-item { display: grid; gap: 9px; padding: 14px 0 0; border-top: 1px solid var(--line, #e8ebea); }
.diagnosis-item:first-child { padding-top: 0; border-top: 0; }
.diagnosis-item-head strong { display: block; font-size: 14px; }
.diagnosis-item-head span { display: block; margin-top: 4px; color: var(--muted, #66716c); font-size: 11px; }
.diagnosis-item-head b { color: var(--accent, #28664f); font-size: 20px; }
.diagnosis-item-head b small { margin-left: 1px; font-size: 11px; }
.diagnosis-copy { max-height: 85px; overflow: auto; color: var(--muted, #66716c); font-size: 12px; line-height: 1.6; }
.diagnosis-copy :deep(p) { margin: 0; }

.jobs-layout { grid-template-columns: minmax(310px, 0.75fr) minmax(0, 1.25fr); }
.job-browser { display: grid; align-content: start; gap: 15px; }
.result-count { padding: 6px 9px; border-radius: 7px; background: #f0f3f1; color: var(--muted, #66716c); font-size: 12px; font-weight: 700; white-space: nowrap; }
.job-card-list { display: grid; gap: 8px; max-height: 485px; overflow: auto; padding-right: 2px; }
.job-card { display: grid; grid-template-columns: 36px minmax(0, 1fr) 18px; gap: 10px; align-items: center; width: 100%; padding: 11px; border: 1px solid transparent; border-radius: 11px; background: transparent; color: var(--ink, #1f2724); cursor: pointer; text-align: left; }
.job-card:hover,
.job-card.selected { border-color: #cfe7d9; background: #f2fbf5; }
.job-card-mark { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 10px; background: #e6f5ec; color: var(--accent, #28664f); font-size: 14px; font-weight: 800; }
.job-card-copy { display: grid; gap: 3px; }
.job-card-copy strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.job-card-copy small { color: var(--muted, #66716c); font-size: 11px; }
.job-card-copy em { color: var(--accent, #28664f); font-size: 11px; font-style: normal; font-weight: 700; }
.job-filter-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 10px 0; }
.job-card > svg { color: var(--muted, #66716c); }
.job-detail-stack { display: grid; grid-template-rows: minmax(0, 1fr) auto; gap: 18px; }
.job-detail { display: grid; align-content: start; gap: 17px; min-height: 270px; }
.job-detail-meta { justify-content: flex-start; flex-wrap: wrap; gap: 14px; color: var(--muted, #66716c); font-size: 13px; }
.job-detail-meta span { display: inline-flex; align-items: center; gap: 5px; }
.job-detail-meta strong { color: var(--accent, #28664f); }
.job-detail p { max-width: 720px; }
.match-launcher { display: grid; grid-template-columns: minmax(220px, 0.8fr) minmax(360px, 1.2fr); gap: 24px; align-items: center; background: #f6fbf8; }
.match-controls { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; gap: 10px; align-items: center; }
.match-history-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.match-result { display: grid; gap: 17px; }
.coverage-score { display: flex; align-items: center; gap: 16px; }
.coverage-score > strong { color: var(--accent, #28664f); font-size: 52px; line-height: 1; }
.coverage-score > strong small { font-size: 18px; }
.coverage-score b,
.coverage-score span { display: block; }
.coverage-score b { font-size: 14px; }
.coverage-score span { margin-top: 4px; color: var(--muted, #66716c); font-size: 12px; }
.match-insights { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.match-insights > div { padding: 13px; border-radius: 11px; background: #f7f8f7; }
.match-insights span { color: var(--muted, #66716c); font-size: 11px; font-weight: 800; }
.match-insights p { margin: 6px 0 0; font-size: 13px; line-height: 1.55; }
.match-next-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.match-history { display: grid; align-content: start; gap: 16px; }
.match-records { display: grid; }
.match-record { padding: 12px 0; border-top: 1px solid var(--line, #e8ebea); }
.match-record { width: 100%; border-right: 0; border-bottom: 0; border-left: 0; background: transparent; color: inherit; cursor: pointer; text-align: left; }
.match-record:hover { background: #f4fbf6; }
.match-record div { display: grid; gap: 4px; }
.match-record strong { font-size: 13px; }
.match-record span { color: var(--muted, #66716c); font-size: 11px; }
.match-record b { color: var(--accent, #28664f); font-size: 20px; }

.plan-builder { display: grid; gap: 20px; }
.plan-builder-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 13px; align-items: end; }
.plan-builder-fields :deep(.el-input-number) { width: 100%; }
.plan-builder-fields :deep(.el-select__selected-item) { min-width: 0; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.plan-builder-fields :deep(.el-select__selected-item > span) { display: block; min-width: 0; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.plan-builder-collapsed { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 12px 14px; border: 1px solid #dcebe2; border-radius: 10px; background: #f6fbf8; }
.plan-builder-collapsed-copy { display: grid; gap: 3px; min-width: 0; }
.plan-builder-collapsed-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.plan-builder-collapsed-copy span { color: var(--muted, #66716c); font-size: 12px; }
.plan-builder-collapsed-copy small { color: var(--muted, #66716c); font-size: 11px; }
.plan-advanced-settings { grid-column: 1 / -1; padding-top: 3px; }
.plan-advanced-settings summary,
.revision-reason summary,
.task-details summary,
.task-management-details summary { color: var(--muted, #66716c); cursor: pointer; font-size: 12px; font-weight: 700; list-style: none; }
.plan-advanced-settings summary::-webkit-details-marker,
.revision-reason summary::-webkit-details-marker,
.task-details summary::-webkit-details-marker,
.task-management-details summary::-webkit-details-marker { display: none; }
.plan-advanced-settings summary::before,
.revision-reason summary::before,
.task-details summary::before,
.task-management-details summary::before { display: inline-block; margin-right: 5px; content: '＋'; color: var(--accent, #28664f); font-size: 14px; }
.plan-advanced-settings[open] summary::before,
.revision-reason[open] summary::before,
.task-details[open] summary::before,
.task-management-details[open] summary::before { content: '−'; }
.plan-advanced-fields { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 13px; margin-top: 12px; padding: 12px; border-radius: 9px; background: #f7faf8; }
.plan-layout { grid-template-columns: minmax(300px, 0.65fr) minmax(0, 1.35fr); }
.plan-sidebar { display: grid; align-content: start; gap: 15px; }
.plan-summary-card { display: grid; gap: 11px; padding: 16px; border-radius: 12px; background: #eff9f3; }
.plan-summary-card > div:first-child { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.plan-summary-card span { font-size: 14px; font-weight: 750; }
.plan-summary-card strong { color: var(--accent, #28664f); }
.plan-summary-card p { margin: 0; color: var(--muted, #66716c); font-size: 12px; }
.revision-reason { display: grid; gap: 6px; padding: 9px 10px; border-radius: 8px; background: #f7f9f8; }
.revision-reason p { margin: 0; color: var(--muted, #66716c); font-size: 12px; line-height: 1.55; white-space: pre-wrap; }
.version-rail { display: flex; flex-wrap: wrap; gap: 7px; }
.version-rail span { padding: 5px 8px; border: 1px solid var(--line, #e8ebea); border-radius: 6px; color: var(--muted, #66716c); font-size: 11px; font-weight: 700; }
.version-rail span.current { border-color: #b9dcc7; background: #e7f6ed; color: var(--accent, #28664f); }
.version-actions { display: flex; flex-wrap: wrap; gap: 7px; }
.replan-form { display: grid; gap: 10px; margin-top: 3px; padding-top: 15px; border-top: 1px solid var(--line, #e8ebea); }
.replan-form > span { color: var(--muted, #66716c); font-size: 12px; font-weight: 750; }
.task-panel { min-width: 0; }
.progress-text { display: grid; justify-items: end; gap: 2px; }
.progress-text strong { color: var(--accent, #28664f); font-size: 22px; }
.progress-text span { color: var(--muted, #66716c); font-size: 11px; }
.task-list { display: grid; margin-top: 14px; }
.task-row { display: grid; grid-template-columns: minmax(0, 1fr) 64px 128px; gap: 12px; align-items: start; padding: 17px 0; border-top: 1px solid var(--line, #e8ebea); min-width: 0; }
.task-row.task-today { border-left: 2px solid var(--accent, #28664f); padding-left: 10px; }
.task-row.task-overdue { border-left: 2px solid #b46c2e; padding-left: 10px; }
.task-main { display: grid; grid-template-columns: 32px minmax(0, 1fr); gap: 11px; min-width: 0; }
.task-title-line { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.week-chip { display: grid; width: 32px; height: 32px; place-items: center; border-radius: 9px; background: #eef7f1; color: var(--accent, #28664f); font-size: 11px; font-weight: 800; }
.task-main strong { display: block; font-size: 13px; }
.task-main p { margin: 4px 0 0; color: var(--muted, #66716c); font-size: 12px; line-height: 1.55; }
.task-details { margin-top: 6px; }
.task-details > p { margin-top: 8px; }
.task-details[open] { padding-bottom: 2px; }
.task-detail-lines { display: flex; flex-wrap: wrap; gap: 4px 8px; margin-top: 7px; }
.task-detail-lines small { color: #728078; font-size: 10px; line-height: 1.45; }
.task-hours { display: inline-flex; align-items: center; gap: 3px; color: var(--muted, #66716c); font-size: 12px; font-weight: 700; white-space: nowrap; }
.task-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 5px; grid-column: 1 / -1; }
.task-status-select { grid-column: 1 / -1; }
.task-management-details { grid-column: 1 / -1; min-width: 0; padding-top: 2px; }
.task-management-fields { display: grid; gap: 9px; margin-top: 9px; }
.task-row > :last-child { grid-column: 1 / -1; min-width: 0; }
.task-row :deep(.el-select),
.task-row :deep(.el-input) { min-width: 0; width: 100%; }
.task-save-state { grid-column: 1 / -1; color: var(--accent, #28664f); }
.task-save-state.error { color: #b14d4d; }
.task-context { margin: 5px 0 0; color: var(--muted, #66716c); font-size: 13px; line-height: 1.6; }
.today-strip { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 12px 0; padding: 9px 0; border-block: 1px solid var(--line, #e8ebea); color: var(--muted, #66716c); font-size: 13px; }
.today-strip strong { color: var(--ink, #1f2724); }
.today-strip span { padding: 3px 8px; border-left: 2px solid var(--accent, #28664f); }
.schedule-summary { display: grid; gap: 5px; padding: 12px 0; border-block: 1px solid var(--line, #e8ebea); color: var(--muted, #66716c); font-size: 13px; }
.schedule-summary strong { color: var(--ink, #1f2724); font-size: 14px; }
.schedule-summary small, .saved-note { color: var(--muted, #66716c); font-size: 12px; }
.schedule-actions { display: flex; gap: 8px; flex-wrap: wrap; }

.interview-launch { display: flex; align-items: center; justify-content: space-between; gap: 24px; background: #f4fbf6; }
.interview-launch h2 { margin: 5px 0 0; font-size: 21px; }
.interview-launch-actions { display: flex; flex-wrap: wrap; align-items: end; justify-content: flex-end; gap: 10px; }
.interview-launch-actions label { width: 116px; }
.interview-launch-actions .target-role-editor { width: 190px; }
.interview-launch-actions :deep(.el-input-number) { width: 100%; }
.session-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 18px; }
.session-card { display: grid; gap: 13px; padding: 15px; border: 1px solid var(--line, #e8ebea); border-radius: 12px; background: #fff; color: var(--ink, #1f2724); cursor: pointer; text-align: left; }
.session-card:hover,
.session-card.selected { border-color: #b9dcc7; background: #f4fbf6; }
.session-card > div:first-child { display: flex; align-items: center; gap: 9px; }
.session-icon { display: grid; width: 32px; height: 32px; place-items: center; border-radius: 9px; background: #e5f5ec; color: var(--accent, #28664f); }
.session-card strong { font-size: 13px; }
.session-card > span { color: var(--muted, #66716c); font-size: 12px; }
.interview-workspace { grid-template-columns: minmax(0, 1fr) minmax(0, 0.92fr); }
.interview-question-card,
.answer-card { display: grid; align-content: start; gap: 18px; }
.question-topline { padding-bottom: 13px; border-bottom: 1px solid var(--line, #e8ebea); color: var(--muted, #66716c); font-size: 12px; font-weight: 700; }
.question-text { margin: 0; color: var(--ink, #1f2724); font-size: 19px; font-weight: 650; line-height: 1.65; }
.question-feedback { padding: 12px; border-left: 3px solid #8ebea4; background: #f4fbf6; }
.reference-points { padding: 14px; border-radius: 11px; background: #f7f8f7; }
.reference-points > span { color: var(--muted, #66716c); font-size: 11px; font-weight: 800; }
.plain-list { display: grid; gap: 7px; margin: 10px 0 0; padding-left: 18px; color: var(--muted, #66716c); font-size: 13px; line-height: 1.55; }
.question-nav,
.answer-actions { display: flex; justify-content: space-between; gap: 10px; padding-top: 14px; border-top: 1px solid var(--line, #e8ebea); }
.answer-actions { justify-content: flex-end; }
.answer-input :deep(textarea) { min-height: 278px !important; resize: vertical; }
.interview-report { margin-top: 18px; }
.report-score { align-items: start; padding-bottom: 19px; border-bottom: 1px solid var(--line, #e8ebea); }
.report-score strong { color: var(--accent, #28664f); font-size: 55px; line-height: 1; }
.report-score strong small { margin-left: 2px; font-size: 16px; }
.report-columns { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; margin-top: 20px; }
.report-columns > div { padding: 14px; border-radius: 11px; background: #f7f8f7; }
.report-columns > div > span { color: var(--muted, #66716c); font-size: 11px; font-weight: 800; }

.knowledge-shell { display: grid; gap: 22px; }
.knowledge-intro { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.knowledge-intro p { max-width: 670px; }
.knowledge-shell .panel-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 0; color: var(--ink, #1f2724); font-size: 20px; }
.knowledge-shell .panel-title svg { color: var(--accent, #28664f); }
.knowledge-orb,
.knowledge-empty > span { display: grid; flex: 0 0 auto; width: 62px; height: 62px; place-items: center; border-radius: 50%; background: #c8f1df; color: var(--accent, #28664f); }
.knowledge-search { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }
.knowledge-mode, .knowledge-history { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; margin: 12px 0; color: var(--muted, #66716c); font-size: 12px; }
.knowledge-retrieval { display: grid; gap: 10px; margin-top: 16px; padding: 16px; border: 1px solid #d5e8dd; background: #fbfefc; }
.knowledge-retrieval > header { display: flex; justify-content: space-between; color: var(--muted, #66716c); font-size: 12px; }
.retrieval-result { padding: 12px 0; border-top: 1px solid var(--line, #e8ebea); }
.retrieval-result > div:first-child { display: flex; justify-content: space-between; gap: 10px; }
.retrieval-result > div:first-child span { color: var(--muted, #66716c); font-size: 11px; }
.retrieval-result p { margin: 7px 0; color: var(--muted, #66716c); line-height: 1.55; }
.rag-answer { display: grid; gap: 18px; padding: 20px; border: 1px solid #d5e8dd; border-radius: 13px; background: #f5fbf7; }
.rag-answer > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.rag-answer > header strong { font-size: 18px; }
.knowledge-result { display: grid; gap: 18px; padding: 20px; border: 1px solid #d5e8dd; border-radius: 13px; background: #f5fbf7; }
.knowledge-result header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.knowledge-result h3 { margin: 4px 0 0; font-size: 18px; }
.knowledge-answer { color: var(--ink, #1f2724); font-size: 14px; line-height: 1.72; }
.knowledge-answer :deep(p) { margin: 0 0 10px; }
.citation-list { display: grid; gap: 9px; padding-top: 5px; }
.citation-title { color: var(--muted, #66716c); font-size: 11px; font-weight: 800; }
.citation-row { display: grid; gap: 7px; padding: 13px 0 0; border-top: 1px solid #dcece2; }
.citation-row summary { display: flex; align-items: center; gap: 8px; color: var(--ink, #1f2724); cursor: pointer; font-size: 13px; font-weight: 700; list-style: none; }
.citation-row summary::-webkit-details-marker { display: none; }
.citation-row summary b { color: var(--accent, #28664f); }
.citation-row summary svg { margin-left: auto; }
.citation-row p { margin: 0; color: var(--muted, #66716c); font-size: 11px; }
.citation-row div { color: var(--muted, #66716c); font-size: 12px; line-height: 1.6; }
.citation-row div :deep(p) { margin: 0; }
.knowledge-empty { display: flex; align-items: center; gap: 15px; padding: 20px; border: 1px dashed #cbd7cf; border-radius: 13px; background: #fbfcfb; }
.knowledge-empty > span { width: 46px; height: 46px; }
.knowledge-empty strong { font-size: 14px; }

:deep(.el-input__wrapper),
:deep(.el-textarea__inner),
:deep(.el-select__wrapper) { border-radius: 8px; box-shadow: 0 0 0 1px var(--line, #e8ebea) inset !important; }
:deep(.el-input__wrapper.is-focus),
:deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 1px var(--accent, #28664f) inset !important; }
:deep(.el-button) { border-radius: 8px; font-weight: 700; }
:deep(.el-tag) { border-radius: 6px; font-size: 11px; font-weight: 650; }

@media (max-width: 1180px) {
  .resume-hero-content { grid-template-columns: minmax(180px, 1fr) 112px minmax(230px, 1.1fr); }
  .resume-status { grid-column: 1 / -1; grid-template-columns: auto 1fr; align-items: center; padding-top: 15px; border-top: 1px solid var(--line, #e8ebea); }
  .jobs-layout,
  .plan-layout { grid-template-columns: minmax(280px, 0.7fr) minmax(0, 1.3fr); }
  .session-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 1450px) {
  .match-launcher { grid-template-columns: 1fr; }
}

@media (max-width: 900px) {
  .workspace-header,
  .interview-launch { align-items: stretch; flex-direction: column; }
  .target-role-control { width: 100%; }
  .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .resume-workspace,
  .jobs-layout,
  .plan-layout,
  .interview-workspace { grid-template-columns: 1fr; }
  .resume-hero-content { grid-template-columns: minmax(180px, 1fr) 112px; }
  .resume-summary { grid-column: 1 / -1; }
  .match-history-grid { grid-template-columns: 1fr; }
  .plan-builder-fields { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
  .plan-builder-fields > .el-button { justify-self: end; }
  .plan-advanced-fields { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
  .interview-launch-actions { justify-content: flex-start; }
}

@media (max-width: 600px) {
  .student-workspace { gap: 16px; }
  .workspace-header h1 { font-size: 25px; }
  .overview-grid { gap: 10px; }
  .overview-card { min-height: 108px; padding: 14px; }
  .overview-card strong { font-size: 25px; }
  .overview-card > svg { top: 14px; right: 14px; }
  .resume-hero,
  .plan-builder,
  .interview-launch,
  .knowledge-shell,
  .profile-panel,
  .diagnosis-panel,
  .job-browser,
  .job-detail,
  .match-launcher,
  .plan-sidebar,
  .task-panel,
  .interview-question-card,
  .answer-card,
  .interview-history,
  .interview-report { padding: 17px; border-radius: 13px; }
  .resume-hero-content,
  .profile-form,
  .plan-builder-fields,
  .plan-advanced-fields,
  .match-controls,
  .report-columns,
  .match-insights { grid-template-columns: 1fr; }
  .resume-score { padding: 15px 0 0; border-top: 1px solid var(--line, #e8ebea); border-left: 0; }
  .resume-status { grid-template-columns: 1fr; }
  .plan-builder-fields > :first-child,
  .plan-builder-fields > .el-button { grid-column: auto; justify-self: stretch; }
  .plan-builder-fields > .el-button { width: 100%; }
  .plan-builder-collapsed { align-items: flex-start; flex-direction: column; }
  .plan-builder-collapsed > .el-button { width: 100%; }
  .task-row { grid-template-columns: minmax(0, 1fr) 62px; }
  .task-row > :nth-child(3) { grid-column: 1 / -1; }
  .task-row > :last-child { grid-column: 1 / -1; }
  .task-hours { justify-self: end; }
  .task-actions { grid-column: 1 / -1; }
  .session-grid { grid-template-columns: 1fr; }
  .question-nav,
  .answer-actions { flex-wrap: wrap; }
  .question-nav :deep(.el-button),
  .answer-actions :deep(.el-button) { flex: 1; }
  .knowledge-search { grid-template-columns: 1fr; }
  .knowledge-search :deep(.el-button) { width: 100%; }
}

.compare-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  margin: 12px 0 16px;
}
.compare-toolbar :deep(.el-select) { min-width: 0; }
.comparison-panel { margin-bottom: 16px; }
.comparison-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 12px;
}
.comparison-grid > article {
  padding: 14px;
  border: 1px solid var(--line, #e8ebea);
  border-radius: 12px;
  background: var(--surface-soft, #fbfcfb);
}
.comparison-grid small { display: block; margin-top: 4px; color: var(--muted, #75807c); }
.comparison-grid p { margin: 8px 0 0; color: var(--muted, #596560); line-height: 1.5; }
.comparison-requirement { margin-top: 12px; }
.comparison-requirement summary { cursor: pointer; line-height: 1.8; }
.comparison-requirement p { overflow-wrap: anywhere; }
.comparison-evidence { font-weight: 600; color: var(--ink, #1f2b27) !important; }
@media (max-width: 700px) {
  .compare-toolbar { grid-template-columns: 1fr; }
  .compare-toolbar :deep(.el-button) { width: 100%; }
}
</style>
