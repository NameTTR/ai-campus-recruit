<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import { ElPagination } from 'element-plus/es/components/pagination/index'
import MarkdownIt from 'markdown-it'
import {
  ArrowLeft,
  ArrowUpRight,
  Bot,
  BrainCircuit,
  BriefcaseBusiness,
  CalendarDays,
  CheckCircle2,
  CircleDashed,
  Clock3,
  FileText,
  GitCompareArrows,
  GraduationCap,
  History,
  List,
  MapPin,
  PencilLine,
  Plus,
  RefreshCw,
  Search,
  Sparkles,
  TrendingUp,
  Upload
} from 'lucide-vue-next'
import {
  confirmLearningPlan,
  compareResumeJobs,
  analyzeResume,
  answerKnowledgeBase,
  createLearningPlan,
  deleteResume,
  getProfile,
  getKnowledgeRevision,
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
  updateLearningTask,
  updateResumeProfile,
  uploadResume,
  type InterviewSession,
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
  planStatusLabel,
  readStudentDraft,
  resumeProfileIsDirty,
  resumeSummaryFromProfile,
  splitProfileLines,
  validateResumeFile,
  writeStudentDraft
} from '../features/student/studentWorkflow'

import ResumeEvidencePanel from '../features/student/ResumeEvidencePanel.vue'
import ResumeBuilderPanel from '../features/student/ResumeBuilderPanel.vue'
import StudentModuleNav from '../features/student/StudentModuleNav.vue'
import LearningEvidenceForm from '../features/student/LearningEvidenceForm.vue'
import InterviewWorkspace from '../features/student/InterviewWorkspace.vue'
import KnowledgeWorkspace from '../features/student/KnowledgeWorkspace.vue'
import { knowledgeContext } from '../features/student/knowledgeWorkspace'
import { formatLearningTaskDuration, sumLearningTaskMinutes } from '../features/student/learningDuration'
import {
  citationLocation, matchContextIsCurrent, retrievalFromAnswer, retrievalModeLabel
} from '../features/student/coreDeepening'

const route = useRoute()
const router = useRouter()
const markdown = new MarkdownIt({ breaks: true, linkify: true })
const activeModule = computed(() => typeof route.params.module === 'string' ? route.params.module : 'resume')
const resumePage = computed(() => String(route.meta.resumePage || 'editor'))
const planPage = computed(() => String(route.meta.planPage || 'today'))
const interviewPage = computed(() => String(route.meta.interviewPage || 'start'))
const knowledgePage = computed(() => String(route.meta.knowledgePage || 'search'))
const queryValue = (key: string) => typeof route.query[key] === 'string' ? String(route.query[key]) : ''
const moduleDataReady = reactive({ resume: false, plan: false, interview: false, knowledge: false })
const moduleLoadError = ref('')
const planListWeek = ref<number | undefined>()
const planListPage = ref(1)
const modulePageSize = 12
let moduleLoadRequest = 0
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
const jobsLoadError = ref('')
const matchLoading = ref(false)
const jobSearch = ref('')
const jobCityFilter = ref('')
const jobSkillFilter = ref('')
type JobsPage = 'list' | 'detail' | 'match' | 'compare' | 'history'
const jobsPage = computed(() => (route.meta.jobsPage as JobsPage | undefined) || 'list')
const jobRouteId = computed(() => typeof route.params.jobId === 'string' ? route.params.jobId : '')
const routeMatchId = computed(() => typeof route.query.matchId === 'string' ? route.query.matchId : '')
const jobPage = ref(1)
const jobPageSize = 12
const jobsDataLoaded = ref(false)
let jobsStateUserId = ''
let hydratingJobsState = false
let compareRequest = 0
let jobsReadRequest = 0

const plans = ref<LearningPlan[]>([])
const selectedPlanId = ref('')
const planVersions = ref<LearningPlan[]>([])
const planLoading = ref(false)
const planActionLoading = ref(false)
const replanPreview = ref<LearningPlan>()
const replanPreviewSourceId = ref('')
const replanPreviewOpen = ref(false)
let replanRouteRequest = 0
let restoredReplanRoute = ''
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
let hydratingTaskFeedback = false
const taskSavingIds = ref<string[]>([])
const taskErrors = ref<Record<string, string>>({})
const taskRetryStatus = ref<Record<string, string>>({})
let taskWriteRevision = 0
let planVersionRequest = 0

const interviewSessions = ref<InterviewSession[]>([])
const selectedSessionId = ref('')
const selectedCompletedSessionId = ref('')
const interviewLoading = ref(false)
const interviewTargetRole = ref('')
let interviewReadRequest = 0

const knowledgeQuery = ref('')
const knowledgeAnswer = ref<KnowledgeAnswerResponse>()
const knowledgeRetrieval = ref<AiSearchResponse>()
const knowledgeLoading = ref(false)
const knowledgeRestoring = ref(false)
const knowledgeUseAi = ref(false)
const knowledgeAnswerUsedAi = ref(false)
const knowledgeError = ref('')
const knowledgeRecentQueries = ref<string[]>([])
let knowledgeRequestKey = 0
let knowledgeRestoreRequest = 0
let knowledgeSearchContext = ''

const selectedResume = computed(() => resumes.value.find((resume) => resume.resumeId === selectedResumeId.value))
const selectedJob = computed(() => jobs.value.find((job) => job.jobId === selectedJobId.value))
const selectedPlan = computed(() => plans.value.find((plan) => plan.planId === selectedPlanId.value))
const selectedPlanIsActive = computed(() => selectedPlan.value?.status === 'ACTIVE')
const selectedSession = computed(() => interviewSessions.value.find((session) => session.sessionId === selectedSessionId.value))
const selectedTask = computed(() => selectedPlan.value?.tasks.find(task => task.taskId === route.params.taskId))
const filteredPlanTasks = computed(() => (selectedPlan.value?.tasks || []).filter(task => !planListWeek.value || task.week === planListWeek.value))
const pagedPlanTasks = computed(() => filteredPlanTasks.value.slice((planListPage.value - 1) * modulePageSize, planListPage.value * modulePageSize))
const visibleTodayTasks = computed(() => (todayPlanData.value?.tasks || todayTasks.value)
  .filter(task => !['COMPLETED', 'SKIPPED'].includes(taskDisplayStatus(task))).slice(0, 5))
const pendingEvidenceTasks = computed(() => planTasks.value.filter(task => task.status === 'COMPLETED' && !task.evidence?.length).slice(0, 3))
const invalidPlan = computed(() => moduleDataReady.plan && Boolean(queryValue('planId')) && !selectedPlan.value)
const invalidOriginal = computed(() => moduleDataReady.resume && Boolean(route.params.resumeId) && !selectedResume.value)
const moduleContext = computed<Record<string, string>>(() => {
  const context: Record<string, string> = {}
  if (activeModule.value === 'resume' && queryValue('draftId')) context.draftId = queryValue('draftId')
  if (activeModule.value === 'plan' && selectedPlanId.value) context.planId = selectedPlanId.value
  if (activeModule.value === 'interview' && selectedSessionId.value) context.sessionId = selectedSessionId.value
  if (activeModule.value === 'knowledge') Object.assign(context, knowledgeContext(route.query as Record<string, unknown>))
  if (queryValue('matchId') && ['plan', 'interview'].includes(activeModule.value)) context.matchId = queryValue('matchId')
  return context
})
const selectedKnowledgeCitation = computed(() => knowledgeAnswer.value?.citations.find(citation => citation.chunkId === queryValue('source')))
const filteredJobs = computed(() => filterJobs(jobs.value, {
  keyword: jobSearch.value,
  city: jobCityFilter.value,
  skill: jobSkillFilter.value
}))
const pagedJobs = computed(() => filteredJobs.value.slice((jobPage.value - 1) * jobPageSize, jobPage.value * jobPageSize))
const compareContextKey = computed(() => JSON.stringify([selectedResumeId.value, selectedCompareJobIds.value]))
const missingMatchRecord = computed(() => jobsPage.value === 'match' && routeMatchId.value
  && !matches.value.some((match) => match.matchId === routeMatchId.value && match.jobId === jobRouteId.value))
const matchJobTitle = computed(() => selectedJob.value?.title || currentMatch.value?.details?.jobSnapshot?.title || '历史岗位')
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
    if (selectedPlanId.value === plan.planId) {
      todayPlanData.value = undefined
      weeklyReviewData.value = undefined
    }
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
        plannedMinutes: sumLearningTaskMinutes(weekTasks.value),
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
  if (activeModule.value === 'resume' && typeof route.params.resumeId === 'string') {
    selectedResumeId.value = route.params.resumeId
  } else if (!selectedResumeId.value || !resumes.value.some((resume) => resume.resumeId === selectedResumeId.value)) {
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
    moduleLoadError.value = error instanceof Error ? error.message : '简历数据加载失败'
    ElMessage.error(moduleLoadError.value)
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
  const uploadPage = route.fullPath
  try {
    const uploaded = await uploadResume(file)
    resumes.value = [uploaded, ...resumes.value.filter((resume) => resume.resumeId !== uploaded.resumeId)]
    selectedResumeId.value = uploaded.resumeId
    await selectResume()
    if (route.fullPath === uploadPage) await openOriginalResume(uploaded.resumeId)
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
  const requestId = ++jobsReadRequest
  jobsLoading.value = true
  jobsLoadError.value = ''
  try {
    const [jobList, matchList] = await Promise.all([listJobs(), listMyMatches()])
    if (requestId !== jobsReadRequest) return
    jobs.value = jobList
    matches.value = matchList
    if (activeModule.value === 'jobs' && jobRouteId.value) {
      selectedJobId.value = jobRouteId.value
    } else if (!selectedJobId.value || !jobs.value.some((job) => job.jobId === selectedJobId.value)) {
      const saved = storedSelection()
      selectedJobId.value = jobs.value.find((job) => job.jobId === saved.jobId)?.jobId || jobs.value[0]?.jobId || ''
    }
    restoreJobsState()
    jobsDataLoaded.value = true
    syncJobsRoute()
    syncCurrentMatch()
  } catch (error) {
    if (requestId !== jobsReadRequest) return
    jobsLoadError.value = error instanceof Error ? error.message : '岗位数据加载失败'
    ElMessage.error(jobsLoadError.value)
  } finally {
    if (requestId === jobsReadRequest) jobsLoading.value = false
  }
}

function jobsStateKey() {
  return profile.value?.userId ? `aicampus.jobs-workspace.${profile.value.userId}` : ''
}

function restoreJobsState() {
  const userId = profile.value?.userId || ''
  if (!userId || jobsStateUserId === userId) return
  jobsStateUserId = userId
  hydratingJobsState = true
  try {
    const saved = JSON.parse(sessionStorage.getItem(jobsStateKey()) || '{}') as {
      search?: string, city?: string, skill?: string, page?: number, compareJobIds?: string[]
    }
    jobSearch.value = typeof saved.search === 'string' ? saved.search : ''
    jobCityFilter.value = typeof saved.city === 'string' ? saved.city : ''
    jobSkillFilter.value = typeof saved.skill === 'string' ? saved.skill : ''
    selectedCompareJobIds.value = Array.isArray(saved.compareJobIds)
      ? [...new Set(saved.compareJobIds)].filter((id) => jobs.value.some((job) => job.jobId === id)).slice(0, 3) : []
    const maxPage = Math.max(1, Math.ceil(filteredJobs.value.length / jobPageSize))
    const savedPage = typeof saved.page === 'number' && Number.isFinite(saved.page) && saved.page > 0
      ? Math.floor(saved.page) : 1
    jobPage.value = Math.min(maxPage, savedPage)
  } catch {
    jobPage.value = 1
  } finally {
    hydratingJobsState = false
  }
}

function persistJobsState() {
  const key = jobsStateKey()
  if (!key || hydratingJobsState) return
  try {
    sessionStorage.setItem(key, JSON.stringify({
      search: jobSearch.value, city: jobCityFilter.value, skill: jobSkillFilter.value,
      page: jobPage.value, compareJobIds: selectedCompareJobIds.value
    }))
  } catch {
    // Navigation remains usable when browser storage is unavailable.
  }
}

function syncJobsRoute() {
  if (activeModule.value !== 'jobs' || !jobsDataLoaded.value) return
  if (jobsPage.value === 'list' && routeMatchId.value) {
    const match = matches.value.find((item) => item.matchId === routeMatchId.value)
    if (match) void router.replace({ path: `/student/jobs/${encodeURIComponent(match.jobId)}/match`, query: { matchId: match.matchId } })
    return
  }
  if (jobsPage.value !== 'detail' && jobsPage.value !== 'match') return
  selectedJobId.value = jobRouteId.value
  const requestedMatch = routeMatchId.value
    ? matches.value.find((match) => match.matchId === routeMatchId.value && match.jobId === jobRouteId.value)
    : undefined
  if (requestedMatch) {
    selectedResumeId.value = requestedMatch.resumeId
    hydrateResumeForm(selectedResume.value)
    currentMatch.value = requestedMatch
  } else if (typeof route.query.resumeId === 'string') {
    selectedResumeId.value = route.query.resumeId
    hydrateResumeForm(selectedResume.value)
  }
  syncCurrentMatch()
}

function openJob(jobId: string) {
  void router.push(`/student/jobs/${encodeURIComponent(jobId)}`)
}

async function openHistoricalMatch(match: MatchResult) {
  await router.push({ path: `/student/jobs/${encodeURIComponent(match.jobId)}/match`, query: { matchId: match.matchId } })
}

function matchDateLabel(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(date)
}

async function changeMatchResume() {
  const resumeId = selectedResumeId.value
  await router.replace({ path: route.path, query: { resumeId } })
  await selectResume()
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
  const requestPath = route.fullPath
  matchLoading.value = true
  try {
    const match = await matchResumeJob(resumeId, jobId)
    matches.value = [match, ...matches.value.filter((item) => item.matchId !== match.matchId)]
    if (selectedResumeId.value === resumeId && selectedJobId.value === jobId && route.fullPath === requestPath) {
      currentMatch.value = match
      persistSelection()
      if (activeModule.value === 'jobs' && jobRouteId.value === jobId) {
        await router.push({ path: `/student/jobs/${encodeURIComponent(jobId)}/match`, query: { matchId: match.matchId } })
      }
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
  if (compareLoading.value) return
  const contextKey = compareContextKey.value
  const requestId = ++compareRequest
  const resumeId = selectedResumeId.value
  compareLoading.value = true
  try {
    const result = await compareResumeJobs({ resumeId, jobIds: ids })
    if (requestId !== compareRequest || contextKey !== compareContextKey.value || jobsPage.value !== 'compare') return
    compareResult.value = result
    ElMessage.success('\u5c97\u4f4d\u6bd4\u8f83\u5df2\u5b8c\u6210')
  } catch (error) {
    if (requestId === compareRequest && contextKey === compareContextKey.value) {
      ElMessage.error(error instanceof Error ? error.message : '\u5c97\u4f4d\u6bd4\u8f83\u5931\u8d25')
    }
  } finally {
    if (requestId === compareRequest) compareLoading.value = false
  }
}

async function restoreMatch(match: MatchResult) {
  selectedResumeId.value = match.resumeId
  selectedJobId.value = match.jobId
  if (selectedResume.value) await selectResume()
  else hydrateResumeForm(undefined)
  currentMatch.value = match
  persistSelection()
}

function syncCurrentMatch() {
  const saved = storedSelection()
  if (activeModule.value === 'jobs' && jobsPage.value === 'match' && routeMatchId.value) {
    currentMatch.value = matches.value.find((match) => match.matchId === routeMatchId.value
      && match.jobId === jobRouteId.value && match.resumeId === selectedResumeId.value)
    return
  }
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
  await router.push({ path: module === 'plan' ? '/student/plan/create' : '/student/interview', query: { matchId: currentMatch.value.matchId } })
}

async function loadPlans() {
  planLoading.value = true
  try {
    plans.value = await listLearningPlans()
    if (queryValue('planId')) {
      selectedPlanId.value = queryValue('planId')
    } else if (!selectedPlanId.value || !plans.value.some((plan) => plan.planId === selectedPlanId.value)) {
      const preferredPlan = [...plans.value]
        .filter((plan) => plan.status === 'ACTIVE')
        .sort((left, right) => right.version - left.version)[0]
        || [...plans.value].sort((left, right) => right.version - left.version)[0]
      selectedPlanId.value = preferredPlan?.planId || ''
    }
    await loadPlanVersions()
  } catch (error) {
    moduleLoadError.value = error instanceof Error ? error.message : '学习计划加载失败'
    ElMessage.error(moduleLoadError.value)
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
  if (!plan) {
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
  const drafts = plan?.status === 'ACTIVE' ? taskDraftsByPlan.get(taskFeedbackPlanId)
    || readStudentDraft(sessionStorage, profile.value?.userId || '', 'learning-notes', taskFeedbackPlanId) : {}
  hydratingTaskFeedback = true
  taskFeedback.value = Object.fromEntries((plan?.tasks || []).map((task) => [task.taskId, drafts[task.taskId] ?? task.feedback ?? '']))
  hydratingTaskFeedback = false
  taskErrors.value = {}
}

async function createPlan() {
  if (!validatePlanSchedule()) {
    return
  }
  planActionLoading.value = true
  const requestPath = route.fullPath
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
    if (route.fullPath === requestPath) {
      selectedPlanId.value = plan.planId
      await loadPlanVersions()
      await openPlanPage('tasks', plan.planId)
    }
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
  const requestPath = route.fullPath
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
    if (route.fullPath !== requestPath || selectedPlanId.value !== plan.planId) return
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
    await openPlanPage('tasks', revised.planId)
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
  void router.push('/student/resume/profile')
}

function openInterviewFollowUpFromEvidence(evidence: LearningEvidence) {
  const plan = selectedPlan.value
  if (plan) interviewTargetRole.value = plan.targetRole
  ElMessage.info('已带入当前目标岗位，请在模拟面试中开始追问练习。')
  void router.push({ path: '/student/interview', query: { taskId: evidence.taskId, planId: evidence.planId, evidenceId: evidence.evidenceId } })
}

function updateInterviewWorkspaceSession(session: InterviewSession) {
  const existing = interviewSessions.value.some(item => item.sessionId === session.sessionId)
  interviewSessions.value = existing
    ? interviewSessions.value.map(item => item.sessionId === session.sessionId ? session : item)
    : [session, ...interviewSessions.value]
  if (queryValue('sessionId') === session.sessionId || !queryValue('sessionId')) selectedSessionId.value = session.sessionId
}

async function loadInterviewSessions() {
  const requestId = ++interviewReadRequest
  interviewLoading.value = true
  try {
    const sessions = await listInterviewSessions()
    if (requestId !== interviewReadRequest) return
    interviewSessions.value = sessions
    if (activeModule.value === 'interview' && queryValue('sessionId')) {
      selectedSessionId.value = queryValue('sessionId')
    } else if (!selectedSessionId.value || !interviewSessions.value.some((session) => session.sessionId === selectedSessionId.value)) {
      selectedSessionId.value = interviewSessions.value[0]?.sessionId || ''
    }
  } catch (error) {
    if (requestId === interviewReadRequest) moduleLoadError.value = error instanceof Error ? error.message : '模拟面试会话加载失败'
    ElMessage.error(error instanceof Error ? error.message : '模拟面试会话加载失败')
  } finally {
    if (requestId === interviewReadRequest) interviewLoading.value = false
  }
}

async function selectSession() {
  if (!selectedSession.value) {
    return
  }
  const sessionId = selectedSessionId.value
  const requestId = ++interviewReadRequest
  interviewLoading.value = true
  try {
    const session = await getInterviewSession(sessionId)
    if (requestId !== interviewReadRequest || selectedSessionId.value !== sessionId) return
    interviewSessions.value = interviewSessions.value.map((item) => item.sessionId === session.sessionId ? session : item)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模拟面试会话读取失败')
  } finally {
    if (requestId === interviewReadRequest) interviewLoading.value = false
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
  knowledgeRestoreRequest++
  knowledgeRestoring.value = false
  await router.replace({ path: '/student/knowledge', query: { q: query, ai: useAi ? '1' : '0' } })
  knowledgeRestoreRequest++
  const requestKey = ++knowledgeRequestKey
  knowledgeSearchContext = JSON.stringify([query, useAi])
  knowledgeError.value = ''
  knowledgeAnswer.value = undefined
  knowledgeRetrieval.value = undefined
  knowledgeAnswerUsedAi.value = useAi
  knowledgeLoading.value = true
  try {
    const answer = await answerKnowledgeBase({ query, role: 'STUDENT', limit: 5, useAi })
    if (requestKey !== knowledgeRequestKey || activeModule.value !== 'knowledge' || queryValue('q') !== query
      || (queryValue('ai') === '1') !== useAi || knowledgeQuery.value.trim() !== query || knowledgeUseAi.value !== useAi) return
    knowledgeAnswer.value = { ...answer, citations: answer.citations || [] }
    knowledgeRetrieval.value = retrievalFromAnswer(answer)
    saveKnowledgeQuery(query)
    try { sessionStorage.setItem(knowledgeResultStorageKey(), JSON.stringify({ query, useAi, answer, savedAt: Date.now() })) }
    catch { /* Retrieval still works when browser storage is unavailable. */ }
    if (activeModule.value === 'knowledge' && knowledgePage.value === 'search' && useAi) await openKnowledgePage('answer')
    if (!answer.citations?.length) ElMessage.warning('没有找到可引用的知识资料')
  } catch (error) {
    if (requestKey === knowledgeRequestKey) knowledgeError.value = error instanceof Error ? error.message : '知识库检索失败'
  } finally {
    if (requestKey === knowledgeRequestKey) {
      knowledgeLoading.value = false
      knowledgeSearchContext = ''
    }
  }
}

function knowledgeResultStorageKey() { return `aicampus.knowledge-result.${profile.value?.userId || ''}` }

async function restoreKnowledgeResult() {
  const query = queryValue('q')
  const useAi = queryValue('ai') === '1'
  if (knowledgeLoading.value && knowledgeSearchContext !== JSON.stringify([query, useAi])) {
    knowledgeRequestKey++
    knowledgeLoading.value = false
    knowledgeSearchContext = ''
  }
  if (!query) {
    knowledgeAnswer.value = undefined
    knowledgeRetrieval.value = undefined
    knowledgeRestoring.value = false
    return
  }
  knowledgeQuery.value = query
  knowledgeUseAi.value = useAi
  if (knowledgeLoading.value) return
  knowledgeAnswer.value = undefined
  knowledgeRetrieval.value = undefined
  const request = ++knowledgeRestoreRequest
  try {
    const saved = JSON.parse(sessionStorage.getItem(knowledgeResultStorageKey()) || '{}')
    if (saved.query !== query || saved.useAi !== useAi || !Number.isFinite(saved.savedAt) || Date.now() - saved.savedAt > 600000) return
    const answer = saved.answer as KnowledgeAnswerResponse
    if (!answer || answer.query !== query || !Array.isArray(answer.citations)) return
    knowledgeRestoring.value = true
    // This read validates saved material and permissions without invoking any model.
    const revision = await getKnowledgeRevision()
    if (request !== knowledgeRestoreRequest || activeModule.value !== 'knowledge' || queryValue('q') !== query || (queryValue('ai') === '1') !== useAi) return
    if (!answer.permissionVersion || revision !== answer.permissionVersion) {
      sessionStorage.removeItem(knowledgeResultStorageKey())
      knowledgeError.value = '引用资料已更新或不可访问，请重新检索。'
      return
    }
    knowledgeAnswer.value = saved.answer
    knowledgeAnswerUsedAi.value = saved.useAi
    knowledgeRetrieval.value = retrievalFromAnswer(saved.answer)
    knowledgeError.value = ''
  } catch {
    if (request === knowledgeRestoreRequest && activeModule.value === 'knowledge') {
      knowledgeError.value = '暂时无法核对引用资料，请重新检索。'
    }
  } finally {
    if (request === knowledgeRestoreRequest) knowledgeRestoring.value = false
  }
}

function openPlanPage(page: string, planId = selectedPlanId.value, taskId?: string) {
  const path = page === 'today' ? '/student/plan' : page === 'task' ? `/student/plan/tasks/${encodeURIComponent(taskId || '')}` : `/student/plan/${page}`
  return router.push({ path, query: { ...(queryValue('matchId') ? { matchId: queryValue('matchId') } : {}), ...(planId ? { planId } : {}) } })
}

function openInterviewPage(page: string, sessionId = selectedSessionId.value) {
  return router.push({ path: page === 'start' ? '/student/interview' : `/student/interview/${page}`,
    query: { ...(queryValue('matchId') ? { matchId: queryValue('matchId') } : {}), ...(sessionId ? { sessionId } : {}) } })
}

function openOriginalResume(resumeId: string, diagnosis = false) {
  return router.push(`/student/resume/original/${encodeURIComponent(resumeId)}${diagnosis ? '/diagnosis' : ''}`)
}
async function changeOriginalResume() { await openOriginalResume(selectedResumeId.value, resumePage.value === 'originalDiagnosis') }

function openKnowledgePage(page: string, source?: string) {
  return router.push({ path: page === 'search' ? '/student/knowledge' : `/student/knowledge/${page}`,
    query: { ...moduleContext.value, ...(source ? { source } : {}) } })
}
async function repeatKnowledgeQuery(query: string) {
  knowledgeQuery.value = query
  knowledgeUseAi.value = false
  await router.push({ path: '/student/knowledge', query: { q: query, ai: '0' } })
  await runKnowledgeSearch()
}

async function restoreReplanRoute() {
  const requestId = ++replanRouteRequest
  const path = route.fullPath
  const sourceId = queryValue('planId')
  const revisionId = queryValue('revisionId')
  if (activeModule.value !== 'plan' || !moduleDataReady.plan || !sourceId || !revisionId) {
    restoredReplanRoute = ''
    return
  }
  if (restoredReplanRoute === path) return
  try {
    const draft = await getLearningPlan(revisionId)
    if (requestId !== replanRouteRequest || route.fullPath !== path) return
    if (draft.status !== 'DRAFT' || draft.revisionOfPlanId !== sourceId) {
      throw new Error('该调整草稿已切换或不属于当前计划，请在历史版本中查看')
    }
    replanPreview.value = draft
    replanPreviewSourceId.value = sourceId
    replanPreviewOpen.value = true
    restoredReplanRoute = path
  } catch (error) {
    if (requestId !== replanRouteRequest || route.fullPath !== path) return
    ElMessage.error(error instanceof Error ? error.message : '计划调整草稿加载失败')
  }
}

function syncModuleRoute() {
  if (['plan', 'interview'].includes(activeModule.value) && moduleDataReady[activeModule.value as 'plan' | 'interview']) {
    restoreRouteMatchContext()
  }
  if (activeModule.value === 'resume' && moduleDataReady.resume && typeof route.params.resumeId === 'string'
    && selectedResumeId.value !== route.params.resumeId) {
    selectedResumeId.value = route.params.resumeId
    void selectResume()
  }
  if (activeModule.value === 'plan' && moduleDataReady.plan && queryValue('planId') && selectedPlanId.value !== queryValue('planId')) {
    selectedPlanId.value = queryValue('planId')
    planListPage.value = 1
    planListWeek.value = undefined
    void loadPlanVersions()
  }
  void restoreReplanRoute()
  if (activeModule.value === 'interview' && moduleDataReady.interview && queryValue('sessionId') && selectedSessionId.value !== queryValue('sessionId')) {
    selectedSessionId.value = queryValue('sessionId')
    void selectSession()
  }
}

function restoreRouteMatchContext() {
  const matchId = queryValue('matchId')
  if (!matchId) return
  const match = matches.value.find(item => item.matchId === matchId)
  if (!match) {
    moduleLoadError.value = '该岗位匹配已不存在或不可访问，请从岗位匹配中重新选择'
    return
  }
  selectedResumeId.value = match.resumeId
  selectedJobId.value = match.jobId
  currentMatch.value = match
  const title = jobs.value.find(item => item.jobId === match.jobId)?.title
  if (activeModule.value === 'interview' && title) interviewTargetRole.value = title
  if (activeModule.value === 'plan' && title) planForm.targetRole = title
}

async function loadModule(module: string) {
  const request = ++moduleLoadRequest
  moduleLoadError.value = ''
  if (module in moduleDataReady) moduleDataReady[module as keyof typeof moduleDataReady] = false
  if (module === 'resume') {
    await loadResumeData()
    await loadJobsData()
  } else if (module === 'jobs') {
    jobsLoading.value = true
    await loadResumeData()
    await loadJobsData()
  } else if (module === 'plan') {
    await loadResumeData()
    await loadJobsData()
    restoreRouteMatchContext()
    await Promise.all([loadPlans(), loadInterviewSessions()])
  } else if (module === 'interview') {
    await loadResumeData()
    await loadJobsData()
    restoreRouteMatchContext()
    await loadInterviewSessions()
  } else if (module === 'knowledge') {
    await loadResumeData()
    await loadJobsData()
  }
  if (route.query.matchId && selectedJob.value) {
    if (module === 'plan') planForm.targetRole = selectedJob.value.title
    if (module === 'interview') interviewTargetRole.value = selectedJob.value.title
  }
  if (request === moduleLoadRequest && module in moduleDataReady) {
    moduleDataReady[module as keyof typeof moduleDataReady] = true
    syncModuleRoute()
  }
}

onMounted(() => { void loadModule(activeModule.value) })
watch(activeModule, (module) => { void loadModule(module) })
watch(() => route.fullPath, () => {
  syncModuleRoute()
  if (activeModule.value !== 'knowledge') {
    knowledgeRequestKey++
    knowledgeRestoreRequest++
    knowledgeLoading.value = false
    knowledgeRestoring.value = false
  }
  syncJobsRoute()
  if (jobsPage.value !== 'compare') {
    compareRequest += 1
    compareLoading.value = false
  }
})
watch(planListWeek, () => { planListPage.value = 1 })
watch(taskFeedback, () => {
  if (hydratingTaskFeedback || !selectedPlanIsActive.value || !taskFeedbackPlanId) return
  taskDraftsByPlan.set(taskFeedbackPlanId, { ...taskFeedback.value })
  writeStudentDraft(sessionStorage, profile.value?.userId || '', 'learning-notes', taskFeedbackPlanId, taskFeedback.value)
}, { deep: true, flush: 'sync' })
watch([jobSearch, jobCityFilter, jobSkillFilter], () => {
  if (!hydratingJobsState) jobPage.value = 1
  persistJobsState()
}, { flush: 'sync' })
watch([jobPage, () => selectedCompareJobIds.value.join('|')], persistJobsState, { flush: 'sync' })
watch(() => filteredJobs.value.length, (total) => {
  jobPage.value = Math.min(jobPage.value, Math.max(1, Math.ceil(total / jobPageSize)))
})
watch(compareContextKey, () => {
  compareRequest += 1
  compareResult.value = undefined
  compareLoading.value = false
}, { flush: 'sync' })
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
    <StudentModuleNav v-if="activeModule === 'resume'" module="resume" :page="resumePage" :context="moduleContext" />
    <StudentModuleNav v-else-if="activeModule === 'plan'" module="plan" :page="planPage" :context="moduleContext" />
    <StudentModuleNav v-else-if="activeModule === 'interview'" module="interview" :page="interviewPage" :context="moduleContext" />
    <StudentModuleNav v-else-if="activeModule === 'knowledge'" module="knowledge" :page="knowledgePage" :context="moduleContext" />
    <div v-if="moduleLoadError && activeModule !== 'jobs'" class="jobs-load-error" role="alert"><span>{{ moduleLoadError }}</span><el-button @click="loadModule(activeModule)">重新加载</el-button></div>
    <template v-if="activeModule === 'resume'">
      <ResumeBuilderPanel v-show="!['history', 'original', 'originalDiagnosis'].includes(resumePage)" :target-role="targetRole" :resume-id="selectedResumeId || undefined" @confirmed="syncWorkspaceResume" />

      <section v-if="resumePage === 'history'" data-testid="resume-history" v-loading="resumeLoading">
        <div class="module-actions"><label class="upload-control"><Upload :size="16" /><span>上传简历</span><input type="file" accept=".pdf,.doc,.docx" :disabled="resumeActionLoading" @change="handleResumeUpload" /></label></div>
        <div class="module-record-list"><button v-for="resume in resumes" :key="resume.resumeId" class="module-record" :data-resume-id="resume.resumeId" @click="openOriginalResume(resume.resumeId)"><div><strong>{{ resume.fileName }}</strong><small>{{ resume.sourceFormat || '原件' }} · {{ resume.parseStatus || '待解析' }}</small></div><ArrowUpRight :size="17" /></button></div>
        <p v-if="!resumeLoading && !resumes.length && !moduleLoadError" class="compact-empty">暂无上传记录。</p>
      </section>

      <div v-if="['original', 'originalDiagnosis'].includes(resumePage)" class="legacy-resume-history" data-testid="resume-legacy-history">
      <nav class="jobs-breadcrumb"><RouterLink to="/student/resume/history"><ArrowLeft :size="15" />上传记录</RouterLink><span>/</span><span>{{ selectedResume?.fileName || '原简历' }}</span><template v-if="resumePage === 'originalDiagnosis'"><span>/</span><span>历史诊断</span></template></nav>
      <p v-if="invalidOriginal && !moduleLoadError" class="compact-empty" data-testid="resume-unavailable">这份简历已不存在或不可访问。</p>
      <section v-else-if="resumePage === 'original'" class="resume-hero" data-testid="resume-original" v-loading="resumeLoading">
        <div class="section-heading">
          <div><h2>我的简历</h2></div>
          <label class="upload-control">
            <Upload :size="16" />
            <span>上传简历</span>
            <input type="file" accept=".pdf,.doc,.docx" :disabled="resumeActionLoading" @change="handleResumeUpload" />
          </label>
        </div>
        <div class="resume-hero-content">
          <div class="resume-picker">
            <span>当前版本</span>
            <el-select v-model="selectedResumeId" placeholder="选择简历" @change="changeOriginalResume">
              <el-option v-for="resume in resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" />
            </el-select>
            <el-button text :disabled="!selectedResume" :loading="resumeActionLoading" @click="removeResume">删除该版本</el-button>
          </div>
          <template v-if="selectedResume">
            <div class="resume-summary"><strong>{{ selectedResume.fileName }}</strong></div>
            <div class="resume-status">
              <span>文件解析</span>
              <div class="tag-row"><el-tag type="info">{{ selectedResume.sourceFormat || '未知格式' }}</el-tag><el-tag type="success">{{ selectedResume.parseStatus || '待解析' }}</el-tag><el-tag>{{ selectedResume.parsedTextLength || 0 }} 字符</el-tag></div>
            </div>
          </template>
          <p v-else class="compact-empty">暂无简历，请先上传。</p>
        </div>
        <el-button v-if="selectedResume" @click="openOriginalResume(selectedResume.resumeId, true)">查看历史诊断 <ArrowUpRight :size="15" /></el-button>
      </section>

      <section v-if="selectedResume && !invalidOriginal" :class="resumePage === 'originalDiagnosis' ? 'original-diagnosis-page' : 'original-profile-page'">
        <article v-if="resumePage === 'original'" class="profile-panel">
          <div class="section-heading"><div><h2>简历资料与诊断</h2></div><PencilLine :size="20" /></div>
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
        <aside v-else class="diagnosis-panel" data-testid="resume-original-diagnosis">
          <div class="section-heading"><div><h2>诊断记录</h2></div><RefreshCw :size="20" /></div>
          <p v-if="!diagnoses.length" class="compact-empty">暂无诊断记录。</p>
          <div v-else class="diagnosis-list">
            <article v-for="diagnosis in diagnoses" :key="diagnosis.diagnosisId" class="diagnosis-item">
              <div class="diagnosis-item-head"><div><strong>{{ diagnosis.targetJob || '通用诊断' }}</strong><span>{{ diagnosis.createdAt }}</span></div><b>{{ diagnosis.score }}<small>分</small></b></div>
              <el-tag :type="sourceTagType(diagnosis.source)">{{ sourceTagLabel(diagnosis.source) }}</el-tag>
              <details><summary>查看报告</summary><ResumeEvidencePanel v-if="diagnosis.details" :diagnosis="diagnosis.details" compact /><div class="diagnosis-copy" v-html="renderMarkdown(diagnosis.diagnosis)" /></details>
            </article>
          </div>
        </aside>
      </section>
      </div>
    </template>

    <template v-else-if="activeModule === 'jobs'">
      <nav class="jobs-subnav" aria-label="岗位匹配导航" data-testid="jobs-subnav">
        <RouterLink to="/student/jobs" data-testid="jobs-nav-list" :class="{ active: ['list', 'detail', 'match'].includes(jobsPage) }" :aria-current="['list', 'detail', 'match'].includes(jobsPage) ? 'page' : undefined"><List :size="16" />岗位列表</RouterLink>
        <RouterLink to="/student/jobs/compare" data-testid="jobs-nav-compare" :class="{ active: jobsPage === 'compare' }" :aria-current="jobsPage === 'compare' ? 'page' : undefined"><GitCompareArrows :size="16" />岗位比较</RouterLink>
        <RouterLink to="/student/jobs/history" data-testid="jobs-nav-history" :class="{ active: jobsPage === 'history' }" :aria-current="jobsPage === 'history' ? 'page' : undefined"><History :size="16" />匹配记录</RouterLink>
      </nav>
      <div v-if="jobsLoadError" class="jobs-load-error" role="alert"><span>{{ jobsLoadError }}</span><el-button data-testid="jobs-load-retry" @click="loadJobsData">重新加载</el-button></div>

      <div v-if="jobsPage === 'detail' || jobsPage === 'match'" class="jobs-breadcrumb">
        <RouterLink to="/student/jobs" data-testid="jobs-back"><ArrowLeft :size="15" />岗位列表</RouterLink>
        <span aria-hidden="true">/</span>
        <RouterLink v-if="jobsPage === 'match' && selectedJob" :to="`/student/jobs/${encodeURIComponent(selectedJob.jobId)}`">{{ matchJobTitle }}</RouterLink>
        <span v-else>{{ selectedJob?.title || matchJobTitle }}</span>
        <template v-if="jobsPage === 'match'"><span aria-hidden="true">/</span><span>匹配分析</span></template>
      </div>

      <section v-if="jobsPage === 'list'" class="jobs-list-page" data-testid="jobs-list" v-loading="jobsLoading">
        <div class="jobs-search-bar">
          <el-input v-model="jobSearch" clearable class="job-search" placeholder="搜索岗位、公司、城市或技能" aria-label="搜索岗位" data-testid="jobs-search"><template #prefix><Search :size="17" /></template></el-input>
          <el-select v-model="jobCityFilter" clearable placeholder="城市" aria-label="筛选城市" data-testid="jobs-city-filter"><el-option v-for="city in jobCities" :key="city" :label="city" :value="city" /></el-select>
          <el-select v-model="jobSkillFilter" clearable filterable placeholder="技能" aria-label="筛选技能" data-testid="jobs-skill-filter"><el-option v-for="skill in jobSkills" :key="skill" :label="skill" :value="skill" /></el-select>
        </div>
        <div class="jobs-list-caption"><span>{{ filteredJobs.length }} 个岗位</span><el-button v-if="jobSearch || jobCityFilter || jobSkillFilter" text size="small" @click="jobSearch = ''; jobCityFilter = ''; jobSkillFilter = ''">清除筛选</el-button></div>
        <div class="job-card-list">
          <button v-for="job in pagedJobs" :key="job.jobId" class="job-card" :data-job-id="job.jobId" @click="openJob(job.jobId)">
            <span class="job-card-mark">{{ job.companyName.slice(0, 1) }}</span>
            <span class="job-card-copy"><strong>{{ job.title }}</strong><small>{{ job.companyName }} · {{ job.city }}</small><em>{{ job.salaryRange }}</em></span>
            <ArrowUpRight :size="18" />
          </button>
        </div>
        <p v-if="!jobsLoading && !jobsLoadError && !filteredJobs.length" class="compact-empty">{{ jobs.length ? '没有符合条件的岗位。' : '暂无开放岗位。' }}</p>
        <el-pagination v-if="filteredJobs.length > jobPageSize" v-model:current-page="jobPage" class="jobs-pagination" data-testid="jobs-pagination" layout="prev, pager, next" :page-size="jobPageSize" :total="filteredJobs.length" :pager-count="5" />
      </section>

      <section v-else-if="jobsPage === 'detail'" class="jobs-detail-page" data-testid="jobs-detail" v-loading="jobsLoading">
        <template v-if="selectedJob">
          <header class="jobs-detail-heading"><h2>{{ selectedJob.title }}</h2><el-tag :type="selectedJob.status === 'CLOSED' ? 'info' : 'success'">{{ selectedJob.status === 'CLOSED' ? '已关闭' : '招聘中' }}</el-tag></header>
          <div class="job-detail-meta"><span><BriefcaseBusiness :size="15" />{{ selectedJob.companyName }}</span><span><MapPin :size="15" />{{ selectedJob.city }}</span><strong>{{ selectedJob.salaryRange }}</strong></div>
          <div class="job-description">{{ selectedJob.description || '岗位暂未提供详细说明。' }}</div>
          <div v-if="selectedJob.requiredSkills.length" class="tag-row"><el-tag v-for="skill in selectedJob.requiredSkills" :key="skill">{{ skill }}</el-tag></div>
          <div class="jobs-primary-action">
            <label class="form-field"><span>匹配简历</span><el-select v-model="selectedResumeId" placeholder="选择简历" aria-label="匹配简历" @change="selectResume"><el-option v-for="resume in resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" /></el-select></label>
            <el-button type="primary" :loading="matchLoading" :disabled="!selectedResume" data-testid="jobs-detail-match" @click="runMatch">匹配这个岗位 <ArrowUpRight :size="15" /></el-button>
          </div>
          <p v-if="!resumes.length" class="compact-empty">暂无可匹配的简历。<RouterLink to="/student/resume">去创建简历</RouterLink></p>
        </template>
        <div v-else-if="!jobsLoading && !jobsLoadError" class="jobs-unavailable" data-testid="jobs-unavailable"><p>这个岗位已下架或不存在。</p><RouterLink to="/student/jobs">返回岗位列表</RouterLink></div>
      </section>

      <section v-else-if="jobsPage === 'match'" class="jobs-analysis-page" data-testid="jobs-analysis" v-loading="jobsLoading">
        <div v-if="missingMatchRecord && !jobsLoading && !jobsLoadError" class="jobs-unavailable" data-testid="jobs-unavailable"><p>这条匹配记录已不存在或不可访问。</p><RouterLink to="/student/jobs/history">返回匹配记录</RouterLink></div>
        <template v-else>
          <div v-if="selectedJob && (!currentMatch || selectedResume)" class="jobs-primary-action match-launcher">
            <label class="form-field"><span>匹配简历</span><el-select v-model="selectedResumeId" placeholder="选择简历" aria-label="匹配简历" @change="changeMatchResume"><el-option v-for="resume in resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" /></el-select></label>
            <el-button type="primary" :disabled="!selectedResume" :loading="matchLoading" data-testid="jobs-run-match" @click="runMatch">{{ currentMatch ? '重新匹配' : '开始匹配' }}</el-button>
          </div>
          <p v-else-if="currentMatch && (!selectedJob || !selectedResume)" class="compact-empty">原{{ !selectedJob && !selectedResume ? '岗位与简历' : !selectedJob ? '岗位' : '简历' }}已不可用，下方保留历史匹配结果。</p>
          <article v-if="currentMatch" class="match-result">
            <div class="jobs-result-meta"><span>{{ resumes.find(resume => resume.resumeId === currentMatch?.resumeId)?.fileName || '原简历已不可用' }}</span></div>
            <div class="jobs-coverage-summary">
              <div><strong>{{ matchScoreLabel(currentMatch) }}</strong><span>技能覆盖率</span></div>
              <div v-if="currentMatch.details"><strong>{{ currentMatch.details.evidenceCoverage }}%</strong><span>材料证据覆盖率</span></div>
            </div>
            <p v-if="currentMatch.analysisSource === 'RULE_INSUFFICIENT_JOB_SKILLS'" class="compact-empty">岗位要求缺少可比技能，暂不生成覆盖率。</p>
            <div v-if="currentMatch.strengths.length || currentMatch.gaps.length" class="match-insights">
              <div v-if="currentMatch.strengths.length"><span>已覆盖</span><p>{{ currentMatch.strengths.join('；') }}</p></div>
              <div v-if="currentMatch.gaps.length"><span>待补材料或能力</span><p>{{ currentMatch.gaps.join('；') }}</p></div>
            </div>
            <details v-if="currentMatch.details" class="jobs-result-details" data-testid="jobs-match-evidence">
              <summary>逐项要求与材料证据</summary>
              <div v-for="item in currentMatch.details.requirements || []" :key="item.skill" class="jobs-evidence-row">
                <div><strong>{{ item.skill }}</strong><el-tag size="small" :type="item.declared ? 'success' : 'info'">{{ item.declared ? '已声明' : '未声明' }}</el-tag><el-tag size="small" :type="item.supported ? 'success' : 'warning'">{{ item.supported ? '已有材料支撑' : '待补材料' }}</el-tag></div>
                <blockquote v-if="item.evidence?.quote">{{ item.evidence.quote }}</blockquote>
                <p>{{ item.evidence?.explanation || '材料中尚未体现。' }}</p>
                <p>{{ item.suggestion }}</p>
                <small v-if="item.evidence?.sourceReference">来源：{{ item.evidence.sourceReference }}</small>
              </div>
            </details>
            <details v-if="currentMatch.details?.conditions?.length" class="jobs-result-details" data-testid="jobs-match-conditions">
              <summary>学历与实习条件</summary>
              <div v-for="condition in currentMatch.details.conditions" :key="`${condition.type}-${condition.requirement}`" class="jobs-evidence-row">
                <div><strong>{{ condition.requirement }}</strong><el-tag size="small" :type="condition.status === 'SATISFIED' ? 'success' : condition.status === 'NOT_SATISFIED' ? 'danger' : 'info'">{{ ({ SATISFIED: '满足', NOT_SATISFIED: '不满足', UNKNOWN: '信息不足' } as Record<string, string>)[condition.status] || '信息不足' }}</el-tag></div>
                <p>{{ condition.explanation }}</p><small>{{ condition.observed }}</small>
              </div>
            </details>
            <details v-if="currentMatch.suggestions.length" class="jobs-result-details" data-testid="jobs-match-suggestions"><summary>改善建议</summary><ul class="plain-list"><li v-for="suggestion in currentMatch.suggestions" :key="suggestion">{{ suggestion }}</li></ul></details>
            <details class="jobs-result-details"><summary>结果来源与历史快照</summary><div class="jobs-evidence-row"><p>{{ matchSourceLabel(currentMatch.analysisSource) }}</p><template v-if="currentMatch.details?.metadata"><p>{{ currentMatch.details.metadata.algorithmVersion }}<template v-if="currentMatch.details.metadata.generatedAt"> · {{ matchDateLabel(currentMatch.details.metadata.generatedAt) }}</template></p><p>{{ currentMatch.details.jobSnapshot?.title }} · {{ currentMatch.details.jobSnapshot?.requiredSkills?.join('、') }}</p><p>{{ currentMatch.details.profileSnapshot?.education }}</p><p>{{ currentMatch.details.profileSnapshot?.skills?.join('、') }}</p><p v-for="project in currentMatch.details.profileSnapshot?.projects || []" :key="project">{{ project }}</p></template></div></details>
            <el-alert v-if="currentMatchStale" title="来源简历或岗位已更新，或已不可用。重新匹配后才能用于学习计划和面试。" type="warning" :closable="false" show-icon />
            <div class="match-next-actions"><el-button :disabled="currentMatchStale" @click="openMatchWorkspace('plan')">生成学习计划 <ArrowUpRight :size="15" /></el-button><el-button type="primary" :disabled="currentMatchStale" @click="openMatchWorkspace('interview')">进入模拟面试 <ArrowUpRight :size="15" /></el-button></div>
          </article>
          <div v-else-if="!selectedJob && !jobsLoading && !jobsLoadError" class="jobs-unavailable" data-testid="jobs-unavailable"><p>这个岗位已下架或不存在。</p><RouterLink to="/student/jobs">返回岗位列表</RouterLink></div>
          <p v-else-if="!jobsLoading && !resumes.length" class="compact-empty">暂无可匹配的简历。<RouterLink to="/student/resume">去创建简历</RouterLink></p>
          <p v-else-if="!currentMatch && !jobsLoading && !jobsLoadError" class="compact-empty">这份简历尚未匹配此岗位。</p>
        </template>
      </section>

      <section v-else-if="jobsPage === 'compare'" class="jobs-compare-page" data-testid="jobs-compare" v-loading="jobsLoading">
        <div class="jobs-compare-controls">
          <label class="form-field"><span>使用简历</span><el-select v-model="selectedResumeId" placeholder="选择简历" aria-label="比较简历" data-testid="jobs-compare-resume" @change="selectResume"><el-option v-for="resume in resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" /></el-select></label>
          <label class="form-field"><span>比较岗位（2–3 个）</span><el-select v-model="selectedCompareJobIds" multiple filterable :multiple-limit="3" collapse-tags :max-collapse-tags="3" placeholder="选择岗位" aria-label="比较岗位" data-testid="jobs-compare-selection"><el-option v-for="job in jobs" :key="`compare-${job.jobId}`" :label="`${job.title} · ${job.companyName}`" :value="job.jobId" :disabled="selectedCompareJobIds.length >= 3 && !selectedCompareJobIds.includes(job.jobId)" /></el-select></label>
          <el-button type="primary" :loading="compareLoading" :disabled="!selectedResume || selectedCompareJobIds.length < 2 || selectedCompareJobIds.length > 3" data-testid="jobs-run-compare" @click="compareSelectedJobs">比较岗位</el-button>
        </div>
        <p v-if="!jobsLoading && !resumes.length" class="compact-empty">暂无可比较的简历。<RouterLink to="/student/resume">去创建简历</RouterLink></p>
        <div v-if="compareResult" class="comparison-grid" data-testid="jobs-compare-results">
          <article v-for="item in compareResult.jobs" :key="item.job.jobId">
            <RouterLink :to="`/student/jobs/${encodeURIComponent(item.job.jobId)}`"><strong>{{ item.job.title }}</strong></RouterLink><small>{{ item.job.companyName }} · {{ item.job.city }}</small>
            <div class="comparison-coverage"><span>技能覆盖 <b>{{ matchScoreLabel(item.match) }}</b></span><span>证据覆盖 <b>{{ item.match.details?.evidenceCoverage ?? '—' }}{{ item.match.details ? '%' : '' }}</b></span></div>
            <details class="comparison-requirement"><summary>岗位要求与材料证据</summary>
              <div v-for="req in item.requirements" :key="`${item.job.jobId}-${req.skill}`" class="jobs-evidence-row">
                <div><strong>{{ req.skill }}</strong><el-tag size="small" :type="req.tier === 'REQUIRED' ? 'danger' : req.tier === 'PREFERRED' ? 'warning' : 'info'">{{ ({ REQUIRED: '必需', PREFERRED: '优先', UNSPECIFIED: '未注明' } as Record<string, string>)[req.tier] || '未注明' }}</el-tag></div>
                <p>岗位原文：{{ req.quote || '正文未注明，来自技能清单' }}</p>
                <template v-for="evidence in item.availableEvidence.filter(e => e.skill === req.skill)" :key="evidence.skill">
                  <p>主资料：{{ evidence.declaredInMaster ? '已声明' : '尚未声明' }} · {{ evidence.supportedInMaster ? '已有材料支撑' : '尚未体现实践' }}</p>
                  <p>{{ evidence.shownInResume ? '当前简历已体现实践证据' : evidence.supportedInMaster ? '主资料已有，当前简历未体现，可补充经历表达。' : '材料中尚未体现，可核对经历或安排练习。' }}</p>
                  <p v-for="(source, index) in evidence.sources" :key="`${source.sourceId}-${index}`">依据：{{ source.quote || source.sourceId }}<span v-if="source.assessment"> · {{ source.assessment }}</span></p>
                </template>
              </div>
            </details>
            <details v-if="item.conditions.length" class="comparison-requirement"><summary>学历与实习条件</summary><div v-for="condition in item.conditions" :key="`${item.job.jobId}-${condition.type}`" class="comparison-condition"><b>{{ ({ EDUCATION: '学历', LOCATION: '地点', GRADUATION: '毕业时间', START_DATE: '到岗日期', WEEKLY_DAYS: '每周出勤', CONTINUOUS_MONTHS: '实习时长' } as Record<string, string>)[condition.type] || condition.type }}</b><span>{{ ({ SATISFIED: '满足', NOT_SATISFIED: '不满足', UNKNOWN: '信息不足' } as Record<string, string>)[condition.status] || '信息不足' }}</span><p>{{ condition.requirement }} · {{ condition.explanation }}</p></div></details>
          </article>
        </div>
      </section>

      <section v-else-if="jobsPage === 'history'" class="jobs-history-page" data-testid="jobs-history" v-loading="jobsLoading">
        <p v-if="!jobsLoading && !jobsLoadError && !matches.length" class="compact-empty">暂无匹配记录。</p>
        <div v-else class="match-records"><button v-for="match in matches" :key="match.matchId" class="match-record" :data-match-id="match.matchId" @click="openHistoricalMatch(match)"><div><strong>{{ jobs.find(job => job.jobId === match.jobId)?.title || match.details?.jobSnapshot?.title || '历史岗位' }}</strong><span>{{ resumes.find(resume => resume.resumeId === match.resumeId)?.fileName || '原简历已不可用' }}<template v-if="match.details?.metadata?.generatedAt"> · {{ matchDateLabel(match.details.metadata.generatedAt) }}</template></span></div><b>{{ matchScoreLabel(match) }}</b><ArrowUpRight :size="17" /></button></div>
      </section>
    </template>

    <template v-else-if="activeModule === 'plan'">
      <p v-if="selectedContextMatch" class="plan-context">已关联岗位匹配 · 技能覆盖 {{ matchScoreLabel(selectedContextMatch) }}</p>
      <div v-if="invalidPlan && !moduleLoadError" class="jobs-unavailable" data-testid="plan-unavailable"><p>这份学习计划已不存在或不可访问。</p><RouterLink to="/student/plan/history">查看学习计划</RouterLink></div>
      <div v-else-if="selectedPlan && planPage !== 'create' && planPage !== 'history'" class="module-context-bar"><strong>{{ selectedPlan.targetRole }}</strong><span>V{{ selectedPlan.version }} · {{ planStatusLabel(selectedPlan.status) }} · {{ selectedPlanCompletedTasks }}/{{ selectedPlan.tasks.length }} 已完成</span><el-button v-if="planPage === 'today'" text @click="openPlanPage('create')"><Plus :size="15" />新计划</el-button></div>

      <section v-if="planPage === 'create'" class="plan-builder module-form-page" data-testid="plan-create" v-loading="planLoading">
        <div class="module-actions"><el-button text @click="openPlanPage('today')"><ArrowLeft :size="15" />返回今日</el-button></div>
        <div class="plan-builder-fields">
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

      <section v-else-if="planPage === 'today'" data-testid="plan-today" v-loading="planLoading">
        <template v-if="selectedPlan && !invalidPlan">
          <div class="section-heading"><h2>今日任务</h2><span class="task-context">本周 {{ weekTasks.length }} 项 · {{ selectedPlanProgress }}%</span></div>
          <div class="module-record-list"><button v-for="task in visibleTodayTasks" :key="task.taskId" class="module-record" :data-task-id="task.taskId" @click="openPlanPage('task', selectedPlanId, task.taskId)"><div><strong>{{ task.title }}</strong><small>{{ taskActionLabel(task) }} · {{ formatLearningTaskDuration(task) }}</small></div><ArrowUpRight :size="17" /></button></div>
          <p v-if="!visibleTodayTasks.length" class="compact-empty">今天没有待完成任务。<el-button link @click="openPlanPage('tasks')">查看完整计划</el-button></p>
          <div v-if="overdueTasks.length || pendingEvidenceTasks.length" class="module-reminders"><h3>待处理</h3><button v-for="task in overdueTasks.slice(0, 3)" :key="task.taskId" @click="openPlanPage('task', selectedPlanId, task.taskId)"><Clock3 :size="15" /><span>已逾期 · {{ task.title }}</span><ArrowUpRight :size="15" /></button><button v-for="task in pendingEvidenceTasks" :key="task.taskId" @click="openPlanPage('task', selectedPlanId, task.taskId)"><FileText :size="15" /><span>待提交成果 · {{ task.title }}</span><ArrowUpRight :size="15" /></button></div>
          <div class="module-actions"><el-button @click="openPlanPage('tasks')"><List :size="15" />完整计划</el-button><el-button @click="openPlanPage('review')">本周复盘 <ArrowUpRight :size="15" /></el-button></div>
        </template>
        <div v-else-if="!planLoading && !invalidPlan && !moduleLoadError" class="module-empty"><span>还没有学习计划。</span><el-button type="primary" @click="openPlanPage('create', '')"><Plus :size="15" />创建学习计划</el-button></div>
      </section>

      <section v-else-if="planPage === 'tasks'" class="task-panel" data-testid="plan-tasks" v-loading="planLoading">
        <template v-if="selectedPlan && !invalidPlan">
          <div class="module-actions"><el-select v-model="planListWeek" clearable placeholder="全部周次" aria-label="筛选计划周次"><el-option v-for="week in selectedPlan.durationWeeks" :key="week" :label="`第 ${week} 周`" :value="week" /></el-select><span class="task-context">{{ filteredPlanTasks.length }} 项任务</span></div>
          <p v-if="!selectedPlanIsActive" class="form-dirty-note">当前为历史版本，任务和重新规划只读。</p>
          <div class="task-list"><button v-for="task in pagedPlanTasks" :key="task.taskId" class="task-row task-list-link" :data-task-id="task.taskId" @click="openPlanPage('task', selectedPlanId, task.taskId)"><div class="task-main"><span class="week-chip">W{{ task.week }}</span><div class="task-title-line"><strong>{{ task.title }}</strong><el-tag size="small" :type="task.status === 'COMPLETED' ? 'success' : 'info'">{{ taskActionLabel(task) }}</el-tag></div></div><span class="task-hours">{{ formatLearningTaskDuration(task) }}<ArrowUpRight :size="15" /></span></button></div>
          <el-pagination v-if="filteredPlanTasks.length > modulePageSize" v-model:current-page="planListPage" layout="prev, pager, next" :page-size="modulePageSize" :total="filteredPlanTasks.length" :pager-count="5" class="jobs-pagination" />
        </template>
        <div v-else-if="!planLoading && !invalidPlan && !moduleLoadError" class="module-empty"><span>暂无学习计划。</span><el-button @click="openPlanPage('create', '')">创建计划</el-button></div>
      </section>

      <section v-else-if="planPage === 'task'" class="module-detail-page" data-testid="plan-task" v-loading="planLoading">
        <div class="module-actions"><el-button text @click="openPlanPage('tasks')"><ArrowLeft :size="15" />完整计划</el-button></div>
        <article v-if="selectedPlan && selectedTask && !invalidPlan" class="task-row task-detail-row">
          <h2>{{ selectedTask.title }}</h2><div class="tag-row"><el-tag>{{ taskActionLabel(selectedTask) }}</el-tag><span>{{ taskSchedule(selectedTask).scheduledDate || `第 ${selectedTask.week} 周` }} · {{ formatLearningTaskDuration(selectedTask) }}</span></div>
          <p>{{ selectedTask.description }}</p>
          <dl class="task-facts"><template v-if="selectedTask.skillGap"><dt>能力缺口</dt><dd>{{ selectedTask.skillGap }}</dd></template><template v-if="selectedTask.acceptanceCriteria"><dt>验收标准</dt><dd>{{ selectedTask.acceptanceCriteria }}</dd></template><template v-if="selectedTask.practiceDeliverable"><dt>成果形式</dt><dd>{{ selectedTask.practiceDeliverable }}</dd></template><template v-if="taskSchedule(selectedTask).actualMinutes"><dt>实际投入</dt><dd>{{ taskSchedule(selectedTask).actualMinutes }} 分钟</dd></template></dl>
          <p v-if="!selectedPlanIsActive" class="form-dirty-note">当前为历史版本，任务只读。</p>
          <div v-if="selectedPlanIsActive && selectedTask.status !== 'COMPLETED'" class="task-actions"><el-button v-if="taskDisplayStatus(selectedTask) !== 'IN_PROGRESS'" :disabled="taskSaving(selectedTask.taskId)" @click="performTaskAction(selectedTask, 'START')">开始</el-button><el-button v-else :disabled="taskSaving(selectedTask.taskId)" @click="performTaskAction(selectedTask, 'PAUSE')">暂停</el-button><el-button type="success" plain :disabled="taskSaving(selectedTask.taskId)" @click="performTaskAction(selectedTask, 'COMPLETE')">完成</el-button><el-button :disabled="taskSaving(selectedTask.taskId)" @click="performTaskAction(selectedTask, 'DEFER')">延期一天</el-button></div>
          <div class="task-management-fields"><el-select class="task-status-select" :disabled="!selectedPlanIsActive || taskSaving(selectedTask.taskId)" :model-value="selectedTask.status === 'TODO' ? 'PENDING' : selectedTask.status" @update:model-value="saveTask(selectedTask.taskId, String($event))"><el-option label="待开始" value="PENDING" /><el-option label="进行中" value="IN_PROGRESS" /><el-option label="已完成" value="COMPLETED" /><el-option label="已跳过" value="SKIPPED" /></el-select><el-input v-model="taskFeedback[selectedTask.taskId]" :disabled="!selectedPlanIsActive || taskSaving(selectedTask.taskId)" placeholder="复盘备注" @change="saveTask(selectedTask.taskId, selectedTask.status)" /></div>
          <small v-if="taskSaving(selectedTask.taskId)" class="task-save-state">正在保存…</small><small v-else-if="taskErrors[selectedTask.taskId]" class="task-save-state error">{{ taskErrors[selectedTask.taskId] }} <el-button link type="primary" :disabled="!selectedPlanIsActive" @click="saveTask(selectedTask.taskId, taskRetryStatus[selectedTask.taskId] || selectedTask.status)">重试</el-button></small>
          <LearningEvidenceForm :key="`${selectedPlanId}:${selectedTask.taskId}`" :plan-id="selectedPlanId" :task="selectedTask" :expanded="true" :readonly="selectedPlan.status !== 'ACTIVE' && selectedPlan.status !== 'COMPLETED'" @saved="recordTaskEvidence" @resume-candidate="openResumeCandidateFromEvidence" @interview-follow-up="openInterviewFollowUpFromEvidence" />
        </article>
        <p v-else-if="!planLoading && !invalidPlan && !moduleLoadError" class="compact-empty" data-testid="plan-unavailable">这个任务不存在或不属于当前计划。</p>
      </section>

      <section v-else-if="planPage === 'history'" class="plan-sidebar" data-testid="plan-history" v-loading="planLoading">
        <div class="module-actions"><el-button @click="openPlanPage('create', '')"><Plus :size="15" />创建新计划</el-button></div>
        <div class="module-record-list version-actions"><button v-for="plan in plans" :key="plan.planId" class="module-record" :data-plan-id="plan.planId" @click="openPlanPage('tasks', plan.planId)"><div><strong>{{ plan.targetRole }} · V{{ plan.version }}</strong><small>{{ planStatusLabel(plan.status) }} · {{ plan.weeklyHours }} 小时/周 · {{ plan.durationWeeks }} 周</small></div><ArrowUpRight :size="17" /></button></div>
        <p v-if="!planLoading && !plans.length && !moduleLoadError" class="compact-empty">暂无学习计划历史。</p>
      </section>

      <section v-else-if="planPage === 'review'" class="module-form-page" data-testid="plan-review" v-loading="planLoading">
        <template v-if="selectedPlan && !invalidPlan">
          <p v-if="!selectedPlanIsActive" class="form-dirty-note">当前为历史版本，任务和重新规划只读。</p>
          <details v-if="selectedPlan.revisionReason" class="revision-reason"><summary>上次调整原因</summary><p>{{ selectedPlan.revisionReason }}</p></details>
          <div v-if="weeklyReviewData" class="schedule-summary"><small>本周实际 {{ weeklyReviewData.actualMinutes }} 分钟 · 完成 {{ weeklyReviewData.completedTasks }} 项</small></div>
          <div class="replan-form"><label class="form-field"><span>本周复盘</span><el-input v-model="weeklyReviewDraft" :disabled="!selectedPlanIsActive" type="textarea" :rows="4" placeholder="完成了什么、哪里卡住、下周准备怎么调整" aria-label="本周复盘" /></label><label class="form-field"><span>计划调整原因</span><el-input v-model="planForm.replanReason" :disabled="!selectedPlanIsActive" type="textarea" :rows="3" placeholder="计划变化或复盘原因" aria-label="计划调整原因" /></label><div class="plan-advanced-fields"><label class="form-field"><span>每周投入（小时）</span><el-input-number v-model="planForm.weeklyHours" :min="2" :max="40" :disabled="!selectedPlanIsActive" /></label><label class="form-field"><span>每日上限（分钟）</span><el-input-number v-model="planForm.dailyMinutesCap" :min="30" :max="480" :disabled="!selectedPlanIsActive" /></label><label class="form-field"><span>学习日</span><el-select v-model="planForm.studyDays" multiple collapse-tags :disabled="!selectedPlanIsActive"><el-option v-for="(day, index) in ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']" :key="day" :value="day" :label="['周一', '周二', '周三', '周四', '周五', '周六', '周日'][index]" /></el-select></label></div><el-select v-model="selectedCompletedSessionId" :disabled="!selectedPlanIsActive" clearable placeholder="同目标的已完成面试（可选）" aria-label="面试反馈来源"><el-option v-for="session in compatibleCompletedSessions" :key="session.sessionId" :label="`${session.targetRole} · ${session.completedAt || session.updatedAt}`" :value="session.sessionId" /></el-select><div class="schedule-actions"><el-button :disabled="!selectedPlanIsActive" @click="savePlanSchedule">保存复盘</el-button><el-button type="primary" :disabled="!selectedPlanIsActive" :loading="planActionLoading" @click="replan">预览调整后的计划</el-button></div><small v-if="weeklyReviewSaved" class="saved-note">复盘已保存</small></div>
        </template>
        <p v-else-if="!planLoading && !invalidPlan && !moduleLoadError" class="compact-empty">暂无可复盘的学习计划。</p>
      </section>
    </template>

    <template v-else-if="activeModule === 'interview'">
      <InterviewWorkspace v-if="moduleDataReady.interview && !moduleLoadError" :page="interviewPage" :user-id="profile?.userId || ''" :sessions="interviewSessions"
        :loading="interviewLoading" :selected-session-id="selectedSessionId" :resume-id="selectedResumeId"
        :job-id="selectedJobId" :match-id="selectedContextMatch?.matchId || queryValue('matchId') || undefined"
        :target-role="interviewTargetRole || targetRole" :resumes="resumes" :jobs="jobs"
        @session="updateInterviewWorkspaceSession" />
    </template>

    <template v-else-if="activeModule === 'knowledge'">
      <KnowledgeWorkspace v-if="profile?.userId" :user-id="profile.userId" :target-role="targetRole" :resume-id="queryValue('resumeId') || selectedResumeId || undefined" :job-id="queryValue('jobId') || undefined" :match-id="queryValue('matchId') || undefined" :plan-id="queryValue('planId') || undefined" :interview-session-id="queryValue('sessionId') || undefined" :jobs="jobs" :resumes="resumes" />
    </template>
    <el-dialog v-model="replanPreviewOpen" title="核对新计划并确认切换" width="min(760px, 94vw)">
      <template v-if="replanPreview"><p>确认后启用 V{{ replanPreview.version }}；原版本和已完成成果仍可查看。</p><p>调整原因：{{ replanPreview.revisionReason || planForm.replanReason }}</p><p>每周 {{ replanPreview.weeklyHours }} 小时 · {{ replanPreview.durationWeeks }} 周</p><div class="task-list"><article v-for="task in replanPreview.tasks" :key="task.taskId" class="preview-task"><strong>第 {{ task.week }} 周 · {{ task.title }} · {{ formatLearningTaskDuration(task) }}</strong><el-tag v-if="task.status === 'COMPLETED'" type="success">已完成成果保留</el-tag><p>{{ task.description }}</p><p>{{ task.acceptanceCriteria }}</p></article></div></template>
      <template #footer><el-button @click="replanPreviewOpen = false">继续当前计划</el-button><el-button type="primary" :loading="planActionLoading" @click="confirmReplan">确认切换计划版本</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.module-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.module-actions :deep(.el-button), .module-context-bar :deep(.el-button) { margin: 0; }
.module-actions :deep(.el-button > span) { gap: 6px; }
.module-actions :deep(.el-select) { width: 180px; max-width: 100%; }
.module-context-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 16px; min-width: 0; }
.module-context-bar strong { font-size: 14px; overflow-wrap: anywhere; }
.module-context-bar > span { color: var(--muted, #66716c); font-size: 12px; }
.module-context-bar :deep(.el-button) { margin-left: auto; }
.module-record-list { display: grid; width: 100%; }
.module-record-list.version-actions { gap: 0; }
.module-record { display: flex; align-items: center; justify-content: space-between; gap: 16px; width: 100%; padding: 16px 4px; border: 0; border-bottom: 1px solid var(--line, #e8ebea); background: transparent; text-align: left; font: inherit; color: var(--ink, #1f2724); cursor: pointer; min-width: 0; }
.module-record:hover { background: #f3f9f5; }
.module-record > div { display: grid; gap: 6px; min-width: 0; }
.module-record strong { font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.module-record small { color: var(--muted, #66716c); line-height: 1.6; overflow-wrap: anywhere; }
.module-record > svg { flex: none; color: var(--muted, #66716c); }
.module-empty { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; padding: 12px 0; font-size: 14px; color: var(--muted, #66716c); }
.module-form-page { width: 100%; max-width: 880px; }
.module-form-page :deep(.el-input-number), .module-form-page :deep(.el-select) { width: 100%; min-width: 0; }
.module-reminders { margin-block: 22px; }
.module-reminders h3 { font-size: 13px; margin-bottom: 8px; }
.module-reminders > button { display: flex; align-items: center; gap: 8px; width: 100%; padding: 9px 0; border: 0; background: transparent; font: inherit; font-size: 13px; color: var(--muted, #66716c); text-align: left; cursor: pointer; }
.module-reminders > button span { flex: 1; overflow-wrap: anywhere; }
.module-reminders > button svg { flex: none; }
.module-detail-page { min-width: 0; max-width: 940px; }
.task-row.task-list-link { width: 100%; border-inline: 0; border-bottom: 0; background: transparent; text-align: left; font: inherit; color: inherit; cursor: pointer; }
.task-row.task-list-link:hover { background: #f3f9f5; }
.task-row.task-list-link > :last-child { grid-column: auto; }
.task-row.task-detail-row { display: grid; grid-template-columns: 1fr; gap: 16px; border-top: 0; padding-top: 0; }
.task-detail-row h2 { margin: 0; font-size: 19px; line-height: 1.6; overflow-wrap: anywhere; }
.task-detail-row p { margin: 0; font-size: 14px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.task-facts { display: grid; grid-template-columns: 80px minmax(0, 1fr); gap: 10px 14px; font-size: 13px; line-height: 1.8; margin: 0; }
.task-facts dt { color: var(--muted, #66716c); }
.task-facts dd { margin: 0; overflow-wrap: anywhere; white-space: pre-wrap; }
.plan-sidebar[data-testid="plan-history"] { position: static; width: 100%; }
.interview-history .module-record.session-card { display: flex; border-radius: 0; }
.retrieval-open { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 0; width: 100%; border: 0; background: transparent; font: inherit; text-align: left; color: inherit; cursor: pointer; }
.retrieval-open svg { flex: none; }
.knowledge-history.module-record-list { display: grid; }
.original-profile-page { max-width: 880px; }
.original-diagnosis-page { width: 100%; }
.question-feedback > summary { cursor: pointer; color: var(--muted, #66716c); font-size: 13px; }
@media (max-width: 480px) {
  .module-context-bar { align-items: start; }
  .module-context-bar :deep(.el-button) { margin-left: 0; }
  .task-facts { grid-template-columns: 1fr; gap: 5px; }
  .task-facts dd { margin-bottom: 9px; }
  .task-row.task-list-link > :last-child { grid-column: auto; }
}
.legacy-resume-history{margin-top:20px;border-top:1px solid var(--line,#e5ebe7);padding-top:16px}
.legacy-resume-history>summary{color:var(--muted,#66716c);font-size:13px;cursor:pointer;width:fit-content;margin-bottom:18px}
.claim-list { display: grid; gap: 9px; margin: 0; font-size: 13px; }
.claim-list article { padding: 10px 0; border-top: 1px solid var(--line, #e8ebea); }
.claim-list p { margin: 0 0 4px; line-height: 1.7; white-space: pre-wrap; }
.claim-list small { color: #66716c; }
.claim-list blockquote { margin: 7px 0 0; padding-left: 10px; border-left: 3px solid #8db7a2; color: #66716c; white-space: pre-wrap; }
.preview-task { padding: 12px; border: 1px solid #e5ebe7; border-radius: 10px; font-size: 13px; line-height: 1.7; }
.preview-task .el-tag { margin-left: 10px; }
.student-workspace {
  display: grid;
  gap: 16px;
  min-width: 0;
  color: var(--ink, #1f2724);
}

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

.header-copy,
.section-heading > div,
.resume-summary,
.job-card-copy,
.task-main > div,
.diagnosis-item-head > div,
.report-score > div {
  min-width: 0;
}

.section-heading h2,
.report-score h2 {
  margin: 0;
  color: var(--ink, #1f2724);
  font-size: 28px;
  font-weight: 750;
  letter-spacing: 0;
  line-height: 1.2;
}

.section-heading p,
.job-detail p {
  margin: 7px 0 0;
  color: var(--muted, #66716c);
  font-size: 14px;
  line-height: 1.65;
}

.form-field,
.interview-launch-actions label {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.form-field > span,
.interview-launch-actions label > span {
  color: var(--muted, #66716c);
  font-size: 12px;
  font-weight: 700;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.overview-card,
.panel {
  border: 1px solid var(--line, #e8ebea);
  border-radius: 8px;
  background: var(--surface, #fff);
  box-shadow: 0 1px 2px rgba(32, 43, 38, 0.025);
}

.overview-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  min-height: 70px;
  gap: 8px;
  align-items: center;
  padding: 12px 16px;
}

.overview-card span {
  color: var(--muted, #66716c);
  font-size: 12px;
  font-weight: 650;
}

.overview-card strong {
  color: var(--ink, #1f2724);
  font-size: 24px;
  font-weight: 760;
  line-height: 1;
}

.overview-card strong small {
  margin-left: 2px;
  color: var(--muted, #66716c);
  font-size: 12px;
  font-weight: 650;
}

.overview-card > svg { display: none; }

.overview-card.accent-mint { background: #c8f1df; }
.overview-card.accent-lavender { background: #ebe8fa; }
.overview-card.accent-peach { background: #fff0e5; }
.overview-card.accent-plain { background: var(--surface, #fff); }

.resume-hero,
.plan-builder,
.interview-launch,
.knowledge-shell {
  padding: 20px;
}

.section-heading {
  align-items: flex-start;
}

.section-heading > svg {
  flex: 0 0 auto;
  color: var(--accent, #28664f);
}

.section-heading h2,
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
.plan-layout,
.interview-workspace {
  display: grid;
  gap: 18px;
}

.resume-workspace { grid-template-columns: minmax(0, 1.25fr) minmax(330px, 0.75fr); }
.profile-panel,
.diagnosis-panel,
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

.jobs-subnav { display: flex; gap: 4px; border-bottom: 1px solid var(--line, #e8ebea); }
.jobs-subnav a { display: flex; align-items: center; justify-content: center; gap: 7px; padding: 12px 16px; border-bottom: 2px solid transparent; color: var(--muted, #66716c); text-decoration: none; font-size: 13px; font-weight: 650; }
.jobs-subnav a:hover { color: var(--accent, #28664f); background: #f4faf6; }
.jobs-subnav a.active { border-bottom-color: var(--accent, #28664f); color: var(--accent, #28664f); }
.jobs-subnav svg { flex: none; }
.jobs-breadcrumb { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: var(--muted, #66716c); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.jobs-breadcrumb a { display: inline-flex; align-items: center; gap: 5px; color: inherit; text-decoration: none; }
.jobs-breadcrumb a:hover { color: var(--accent, #28664f); }
.jobs-breadcrumb > a:first-child { flex: none; }
.jobs-load-error { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 10px 0; color: #a54b3d; font-size: 13px; overflow-wrap: anywhere; }
.jobs-list-page, .jobs-detail-page, .jobs-analysis-page, .jobs-compare-page, .jobs-history-page { min-width: 0; }
.jobs-search-bar { display: grid; grid-template-columns: minmax(0, 1fr) 150px 170px; gap: 10px; }
.jobs-list-caption { display: flex; justify-content: space-between; align-items: center; min-height: 42px; color: var(--muted, #66716c); font-size: 12px; }
.job-card-list { display: grid; }
.job-card { display: grid; grid-template-columns: 36px minmax(0, 1fr) 18px; gap: 12px; align-items: center; width: 100%; padding: 14px 4px; border: 0; border-top: 1px solid var(--line, #e8ebea); background: transparent; color: var(--ink, #1f2724); cursor: pointer; text-align: left; }
.job-card:hover { background: #f3f9f5; }
.job-card-mark { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 6px; background: #e9f5ed; color: var(--accent, #28664f); font-size: 14px; font-weight: 700; }
.job-card-copy { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 5px 18px; min-width: 0; }
.job-card-copy strong { font-size: 14px; line-height: 1.5; overflow-wrap: anywhere; }
.job-card-copy small { grid-column: 1; color: var(--muted, #66716c); font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
.job-card-copy em { grid-column: 2; grid-row: 1 / 3; align-self: center; color: var(--accent, #28664f); font-size: 12px; font-style: normal; font-weight: 700; overflow-wrap: anywhere; }
.job-card > svg { color: var(--muted, #66716c); }
.jobs-pagination { display: flex; justify-content: center; padding-top: 18px; }
.jobs-detail-page { display: grid; align-content: start; gap: 18px; }
.jobs-detail-heading { display: flex; align-items: start; justify-content: space-between; gap: 12px; }
.jobs-detail-heading h2 { margin: 0; font-size: 20px; line-height: 1.5; overflow-wrap: anywhere; }
.jobs-detail-heading :deep(.el-tag) { flex: none; margin-top: 4px; }
.job-detail-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 9px 18px; color: var(--muted, #66716c); font-size: 13px; }
.job-detail-meta span { display: inline-flex; align-items: center; gap: 5px; overflow-wrap: anywhere; }
.job-detail-meta span svg { flex: none; }
.job-detail-meta strong { color: var(--accent, #28664f); }
.job-description { max-width: 850px; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 14px; line-height: 1.9; }
.jobs-primary-action { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 12px; align-items: end; width: 100%; max-width: 650px; padding-block: 16px; border-block: 1px solid var(--line, #e8ebea); }
.jobs-primary-action :deep(.el-select), .jobs-compare-controls :deep(.el-select) { min-width: 0; width: 100%; }
.jobs-primary-action :deep(.el-button) { margin: 0; }
.jobs-primary-action :deep(.el-button > span) { gap: 6px; }
.match-launcher { background: transparent; }
.match-result { display: grid; gap: 14px; padding-top: 16px; }
.jobs-result-meta { display: flex; flex-wrap: wrap; gap: 8px 16px; color: var(--muted, #66716c); font-size: 12px; overflow-wrap: anywhere; }
.jobs-coverage-summary { display: flex; flex-wrap: wrap; gap: 18px 42px; padding-block: 8px; }
.jobs-coverage-summary > div { display: grid; gap: 6px; }
.jobs-coverage-summary strong { color: var(--accent, #28664f); font-size: 30px; line-height: 1.25; }
.jobs-coverage-summary span { color: var(--muted, #66716c); font-size: 12px; }
.match-insights { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; padding-block: 12px; }
.match-insights span { font-size: 12px; font-weight: 700; }
.match-insights p { margin: 7px 0 0; color: var(--muted, #66716c); font-size: 13px; line-height: 1.8; overflow-wrap: anywhere; }
.jobs-result-details { min-width: 0; border-top: 1px solid var(--line, #e8ebea); }
.jobs-result-details > summary { padding: 10px 0; color: var(--muted, #66716c); cursor: pointer; font-size: 13px; }
.jobs-evidence-row { padding: 12px 0; border-top: 1px solid var(--line, #e8ebea); min-width: 0; }
.jobs-evidence-row > div { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; }
.jobs-evidence-row strong { font-size: 13px; }
.jobs-evidence-row blockquote { margin: 10px 0; padding-left: 10px; border-left: 2px solid #91b8a2; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 13px; line-height: 1.7; }
.jobs-evidence-row p, .jobs-evidence-row small { margin: 6px 0 0; color: var(--muted, #66716c); font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.match-next-actions { display: flex; flex-wrap: wrap; gap: 10px; padding-top: 8px; }
.match-next-actions :deep(.el-button) { margin: 0; }
.match-next-actions :deep(.el-button > span) { gap: 6px; }
.match-records { display: grid; }
.match-record { display: grid; grid-template-columns: minmax(0, 1fr) auto 17px; align-items: center; gap: 16px; width: 100%; padding: 16px 4px; border: 0; border-top: 1px solid var(--line, #e8ebea); background: transparent; color: inherit; cursor: pointer; text-align: left; }
.match-record:first-child { border-top: 0; }
.match-record:hover { background: #f4fbf6; }
.match-record div { display: grid; gap: 5px; min-width: 0; }
.match-record strong { font-size: 14px; line-height: 1.5; overflow-wrap: anywhere; }
.match-record span { color: var(--muted, #66716c); font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
.match-record b { color: var(--accent, #28664f); font-size: 20px; }
.match-record > svg { color: var(--muted, #66716c); }
.jobs-unavailable { padding: 12px 0; color: var(--muted, #66716c); font-size: 14px; }
.jobs-unavailable a, .jobs-detail-page .compact-empty a, .jobs-analysis-page .compact-empty a, .jobs-compare-page .compact-empty a { color: var(--accent, #28664f); margin-left: 5px; }
.jobs-compare-controls { display: grid; grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr) auto; gap: 12px; align-items: end; padding-bottom: 20px; }
.jobs-compare-controls :deep(.el-select__selected-item) { max-width: 100%; overflow: hidden; text-overflow: ellipsis; }
.jobs-compare-controls :deep(.el-select__tags-text) { overflow: hidden; text-overflow: ellipsis; }

.plan-builder { display: grid; gap: 14px; }
.plan-builder:has(.plan-builder-collapsed) { padding-block: 14px; }
.plan-builder-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 13px; align-items: end; }
.plan-builder-fields :deep(.el-input-number) { width: 100%; }
.plan-builder-fields :deep(.el-select__selected-item) { min-width: 0; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.plan-builder-fields :deep(.el-select__selected-item > span) { display: block; min-width: 0; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.plan-builder-collapsed { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.plan-builder-collapsed-copy { display: grid; gap: 3px; min-width: 0; }
.plan-builder-collapsed-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.plan-builder-collapsed-copy span { color: var(--muted, #66716c); font-size: 12px; }
.plan-builder-collapsed-copy small { color: var(--muted, #66716c); font-size: 11px; }
.plan-advanced-settings { grid-column: 1 / -1; padding-top: 3px; }
.plan-advanced-settings summary,
.plan-history-details > summary,
.revision-reason summary,
.task-details summary,
.task-management-details summary { color: var(--muted, #66716c); cursor: pointer; font-size: 12px; font-weight: 700; list-style: none; }
.plan-advanced-settings summary::-webkit-details-marker,
.plan-history-details > summary::-webkit-details-marker,
.revision-reason summary::-webkit-details-marker,
.task-details summary::-webkit-details-marker,
.task-management-details summary::-webkit-details-marker { display: none; }
.plan-advanced-settings summary::before,
.plan-history-details > summary::before,
.revision-reason summary::before,
.task-details summary::before,
.task-management-details summary::before { display: inline-block; margin-right: 5px; content: '＋'; color: var(--accent, #28664f); font-size: 14px; }
.plan-advanced-settings[open] summary::before,
.plan-history-details[open] > summary::before,
.revision-reason[open] summary::before,
.task-details[open] summary::before,
.task-management-details[open] summary::before { content: '−'; }
.plan-advanced-fields { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 13px; margin-top: 12px; padding: 12px; border-radius: 9px; background: #f7faf8; }
.plan-layout { grid-template-columns: minmax(0, 1fr); }
.plan-history-details { min-width: 0; padding: 14px 0; border-top: 1px solid var(--line, #e8ebea); }
.plan-history-body { display: grid; align-content: start; gap: 12px; margin-top: 14px; }
.revision-reason { padding: 9px 10px; border-radius: 8px; background: #f7f9f8; }
.revision-reason p { margin: 0; color: var(--muted, #66716c); font-size: 12px; line-height: 1.55; white-space: pre-wrap; }
.version-actions { display: flex; flex-wrap: wrap; gap: 7px; }
.replan-form { display: grid; gap: 10px; margin-top: 3px; padding-top: 15px; border-top: 1px solid var(--line, #e8ebea); }
.replan-form > span { color: var(--muted, #66716c); font-size: 12px; font-weight: 750; }
.task-panel { min-width: 0; }
.task-evidence-note { margin: 8px 0 0; color: var(--muted, #66716c); font-size: 12px; line-height: 1.6; }
.compact-empty { margin: 8px 0; color: var(--muted, #66716c); font-size: 13px; line-height: 1.6; }
.progress-text { display: grid; justify-items: end; gap: 2px; }
.progress-text strong { color: var(--accent, #28664f); font-size: 22px; }
.progress-text span { color: var(--muted, #66716c); font-size: 11px; }
.task-list { display: grid; margin-top: 14px; }
.task-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 12px; align-items: start; padding: 17px 0; border-top: 1px solid var(--line, #e8ebea); min-width: 0; }
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

.interview-launch { background: #f4fbf6; }
.interview-launch-actions { display: flex; flex-wrap: wrap; align-items: end; gap: 10px; }
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
.answer-card { display: grid; align-content: start; gap: 14px; }
.answer-label { font-size: 14px; font-weight: 700; }
.question-topline { padding-bottom: 13px; border-bottom: 1px solid var(--line, #e8ebea); color: var(--muted, #66716c); font-size: 12px; font-weight: 700; }
.question-text { margin: 0; color: var(--ink, #1f2724); font-size: 19px; font-weight: 650; line-height: 1.65; }
.question-feedback { padding: 12px; border-left: 3px solid #8ebea4; background: #f4fbf6; }
.reference-points { padding: 14px; border-radius: 11px; background: #f7f8f7; }
.reference-points > span { color: var(--muted, #66716c); font-size: 11px; font-weight: 800; }
.plain-list { display: grid; gap: 7px; margin: 10px 0 0; padding-left: 18px; color: var(--muted, #66716c); font-size: 13px; line-height: 1.55; }
.question-nav,
.answer-actions { display: flex; justify-content: space-between; gap: 10px; padding-top: 14px; border-top: 1px solid var(--line, #e8ebea); }
.answer-actions { justify-content: flex-end; }
.answer-input :deep(textarea) { resize: vertical; }
.answer-input :deep(textarea:not([readonly])) { min-height: 278px !important; }
.answer-input :deep(textarea[readonly]) { min-height: 140px !important; }
.interview-report { margin-top: 18px; }
.report-score { align-items: start; padding-bottom: 19px; border-bottom: 1px solid var(--line, #e8ebea); }
.report-score strong { color: var(--accent, #28664f); font-size: 55px; line-height: 1; }
.report-score strong small { margin-left: 2px; font-size: 16px; }
.report-columns { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; margin-top: 20px; }
.report-columns > div { padding: 14px; border-radius: 11px; background: #f7f8f7; }
.report-columns > div > span { color: var(--muted, #66716c); font-size: 11px; font-weight: 800; }

.knowledge-shell { display: grid; gap: 12px; }
.knowledge-search { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }
.knowledge-mode, .knowledge-history { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; margin: 0; color: var(--muted, #66716c); font-size: 12px; }
.knowledge-retrieval { display: grid; gap: 10px; padding-top: 14px; border-top: 1px solid var(--line, #e8ebea); }
.knowledge-retrieval > header { display: flex; justify-content: space-between; color: var(--muted, #66716c); font-size: 12px; }
.retrieval-result { padding: 12px 0; border-top: 1px solid var(--line, #e8ebea); }
.retrieval-result > div:first-child { display: flex; justify-content: space-between; gap: 10px; }
.retrieval-result > div:first-child span { color: var(--muted, #66716c); font-size: 11px; }
.retrieval-result p { margin: 7px 0; color: var(--muted, #66716c); line-height: 1.55; }
.rag-answer { display: grid; gap: 14px; padding-top: 14px; border-top: 1px solid var(--line, #e8ebea); }
.rag-answer > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.rag-answer > header strong { font-size: 18px; }
.knowledge-answer { color: var(--ink, #1f2724); font-size: 14px; line-height: 1.72; }
.knowledge-answer :deep(p) { margin: 0 0 10px; }
.citation-list { display: grid; gap: 9px; padding-top: 5px; }
.citation-title { color: var(--muted, #66716c); font-size: 11px; font-weight: 800; }
.citation-row { padding: 13px 0 0; border-top: 1px solid #dcece2; }
.citation-row summary { display: flex; align-items: center; gap: 8px; color: var(--ink, #1f2724); cursor: pointer; font-size: 13px; font-weight: 700; list-style: none; }
.citation-row summary::-webkit-details-marker { display: none; }
.citation-row summary b { color: var(--accent, #28664f); }
.citation-row summary svg { margin-left: auto; }
.citation-row p { margin: 0; color: var(--muted, #66716c); font-size: 11px; }
.citation-row div { color: var(--muted, #66716c); font-size: 12px; line-height: 1.6; }
.citation-row div :deep(p) { margin: 0; }

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
  .session-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 900px) {
  .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .resume-workspace,
  .plan-layout,
  .interview-workspace { grid-template-columns: 1fr; }
  .resume-hero-content { grid-template-columns: minmax(180px, 1fr) 112px; }
  .resume-summary { grid-column: 1 / -1; }
  .plan-builder-fields { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
  .plan-builder-fields > .el-button { justify-self: end; }
  .plan-advanced-fields { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
  .interview-launch-actions { justify-content: flex-start; }
}

@media (max-width: 600px) {
  .student-workspace { gap: 16px; }
  .overview-grid { gap: 10px; }
  .overview-card { min-height: 62px; padding: 10px 12px; }
  .overview-card strong { font-size: 21px; }
  .resume-hero,
  .plan-builder,
  .interview-launch,
  .knowledge-shell,
  .profile-panel,
  .diagnosis-panel,
  .task-panel,
  .interview-question-card,
  .answer-card,
  .interview-history,
  .interview-report { padding: 16px; border-radius: 8px; }
  .resume-hero-content,
  .profile-form,
  .plan-builder-fields,
  .plan-advanced-fields,
  .report-columns,
  .match-insights { grid-template-columns: 1fr; }
  .resume-score { padding: 15px 0 0; border-top: 1px solid var(--line, #e8ebea); border-left: 0; }
  .resume-status { grid-template-columns: 1fr; }
  .plan-builder-fields > :first-child,
  .plan-builder-fields > .el-button { grid-column: auto; justify-self: stretch; }
  .plan-builder-fields > .el-button { width: 100%; }
  .plan-builder-collapsed { align-items: flex-start; flex-direction: column; }
  .plan-builder-collapsed > .el-button { width: 100%; }
  .task-row { grid-template-columns: minmax(0, 1fr) auto; }
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

.comparison-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(250px, 100%), 1fr));
  gap: 20px;
}
.comparison-grid > article {
  min-width: 0;
  padding-top: 16px;
  border-top: 2px solid #bad5c6;
}
.comparison-grid a { color: var(--ink, #1f2724); text-decoration: none; font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.comparison-grid a:hover { color: var(--accent, #28664f); }
.comparison-grid small { display: block; margin-top: 4px; color: var(--muted, #75807c); }
.comparison-grid p { margin: 8px 0 0; color: var(--muted, #596560); font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.comparison-grid small { overflow-wrap: anywhere; }
.comparison-coverage { display: grid; gap: 8px; margin-top: 16px; font-size: 12px; }
.comparison-coverage > span { display: flex; justify-content: space-between; gap: 10px; color: var(--muted, #66716c); }
.comparison-coverage b { color: var(--accent, #28664f); font-size: 15px; }
.comparison-condition { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 4px 10px; margin-top: 12px; font-size: 12px; }
.comparison-condition span { color: var(--muted, #66716c); }
.comparison-condition p { grid-column: 1 / -1; }
.comparison-requirement { margin-top: 12px; }
.comparison-requirement summary { color: var(--muted, #66716c); cursor: pointer; font-size: 12px; line-height: 1.8; }
.comparison-requirement p { overflow-wrap: anywhere; }
@media (max-width: 700px) {
  .jobs-search-bar { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .jobs-search-bar .job-search { grid-column: 1 / -1; }
  .jobs-compare-controls { grid-template-columns: 1fr; }
  .jobs-compare-controls :deep(.el-button) { width: 100%; margin: 0; }
}
@media (max-width: 480px) {
  .jobs-subnav { gap: 0; }
  .jobs-subnav a { flex: 1; padding: 10px 3px; gap: 5px; font-size: 12px; white-space: nowrap; }
  .jobs-subnav svg { width: 14px; height: 14px; }
  .job-card-copy { grid-template-columns: minmax(0, 1fr); gap: 4px; }
  .job-card-copy em { grid-column: 1; grid-row: auto; }
  .jobs-primary-action { grid-template-columns: 1fr; }
  .jobs-primary-action :deep(.el-button) { width: 100%; }
  .match-next-actions { display: grid; grid-template-columns: 1fr; }
  .jobs-coverage-summary { gap: 18px 26px; }
  .jobs-detail-heading h2 { font-size: 18px; }
}
</style>
