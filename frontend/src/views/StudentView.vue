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
  RefreshCw,
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
  type LearningEvidence,
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
  replanReason: ''
})
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
  }
  syncTaskFeedback(plan)
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
      durationWeeks: planForm.durationWeeks
    })
    plans.value = [plan, ...plans.value.filter((item) => item.planId !== plan.planId)]
    selectedPlanId.value = plan.planId
    await loadPlanVersions()
    ElMessage.success('学习计划已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学习计划生成失败')
  } finally {
    planActionLoading.value = false
  }
}

async function saveTask(taskId: string, status: string) {
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
      feedback: taskFeedback.value[taskId] ?? currentTask?.feedback
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
          <div><h2>原件资料</h2></div>
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
            <div class="resume-summary"><strong>{{ selectedResume.fileName }}</strong></div>
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
          <div class="section-heading"><div><h2>简历资料与诊断</h2></div></div>
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
          <div class="section-heading"><div><h2>诊断记录</h2></div></div>
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
          <div class="section-heading"><div><h2>岗位与匹配</h2></div><span class="result-count">{{ filteredJobs.length }} 个结果</span></div>
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
            <div class="section-heading"><div><h2>岗位条件与证据比较</h2></div><el-button text @click="compareResult = undefined">关闭</el-button></div>
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
              <div class="section-heading"><div><h2>{{ selectedJob.title }}</h2></div><el-tag type="success">{{ selectedJob.status || 'OPEN' }}</el-tag></div>
              <div class="job-detail-meta"><span><BriefcaseBusiness :size="15" />{{ selectedJob.companyName }}</span><span><MapPin :size="15" />{{ selectedJob.city }}</span><strong>{{ selectedJob.salaryRange }}</strong></div>
              <p>{{ selectedJob.description }}</p>
              <div class="tag-row"><el-tag v-for="skill in selectedJob.requiredSkills" :key="skill">{{ skill }}</el-tag></div>
            </template>
            <el-empty v-else description="请选择一个岗位" :image-size="84" />
          </article>
          <article class="panel match-launcher">
            <div><h2>开始匹配</h2></div>
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
          <div class="section-heading"><div><h2>本次匹配结果</h2></div></div>
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
          <div class="section-heading"><div><h2>匹配覆盖</h2></div></div>
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
        <div class="section-heading"><div><h2>学习计划</h2></div></div>
        <div class="plan-builder-fields">
          <label class="form-field"><span>简历来源</span><el-select v-model="selectedResumeId" placeholder="选择简历" @change="selectResume"><el-option v-for="resume in resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" /></el-select></label>
          <label class="form-field"><span>岗位来源</span><el-select v-model="selectedJobId" placeholder="选择岗位"><el-option v-for="job in jobs" :key="job.jobId" :label="`${job.title} · ${job.companyName}`" :value="job.jobId" /></el-select></label>
          <label class="form-field"><span>目标岗位</span><el-input v-model="planForm.targetRole" placeholder="目标岗位" /></label>
          <label class="form-field"><span>每周投入（小时）</span><el-input-number v-model="planForm.weeklyHours" :min="2" :max="40" controls-position="right" /></label>
          <label class="form-field"><span>计划周期（周）</span><el-input-number v-model="planForm.durationWeeks" :min="1" :max="24" controls-position="right" /></label>
          <el-button type="primary" :loading="planActionLoading" @click="createPlan">生成学习计划 <ArrowUpRight :size="16" /></el-button>
        </div>
      </section>

      <p class="plan-context">{{ selectedContextMatch ? `已关联匹配：${matchScoreLabel(selectedContextMatch)} · ${selectedContextMatch.matchId}` : '无当前技能对应的匹配记录，计划将基于已选简历、岗位和目标岗位生成。' }}</p>
      <section v-if="plans.length" class="plan-layout">
        <aside class="panel plan-sidebar">
          <div class="section-heading"><div><h2>计划版本</h2></div></div>
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
          <p v-if="selectedPlan?.revisionReason" class="form-dirty-note">调整原因：{{ selectedPlan.revisionReason }}</p>
          <div class="replan-form"><span>需要调整节奏？</span><el-input v-model="planForm.replanReason" :disabled="!selectedPlanIsActive" type="textarea" :rows="3" placeholder="计划变化或复盘原因" /><el-select v-model="selectedCompletedSessionId" :disabled="!selectedPlanIsActive" clearable placeholder="选择同目标的已完成面试会话（可选)"><el-option v-for="session in compatibleCompletedSessions" :key="session.sessionId" :label="`${session.targetRole} · ${session.completedAt || session.updatedAt}`" :value="session.sessionId" /></el-select><el-button :disabled="!selectedPlanIsActive" :loading="planActionLoading" @click="replan">重新规划并预览</el-button></div>
        </aside>
        <article class="panel task-panel">
          <el-alert title="完成状态为自报进度；成果评价单独记录，不会自动更新已掌握技能。" type="info" :closable="false" />
          <div class="section-heading"><div><h2>任务进度</h2></div><div class="progress-text"><strong>{{ selectedPlanProgress }}%</strong><span>{{ selectedPlanCompletedTasks }}/{{ selectedPlan?.tasks.length || 0 }} 已完成</span></div></div>
          <el-empty v-if="!selectedPlan" description="请选择学习计划" :image-size="88" />
          <div v-else class="task-list">
            <div v-for="task in selectedPlan.tasks" :key="task.taskId" class="task-row">
              <div class="task-main"><span class="week-chip">W{{ task.week }}</span><div><strong>{{ task.title }}</strong><p>{{ task.description }}</p><div class="task-detail-lines"><small v-if="task.stage">{{ learningStageLabel(task.stage) }}</small><small v-if="task.skillGap">缺口：{{ task.skillGap }}</small><small v-if="task.acceptanceCriteria">验收：{{ task.acceptanceCriteria }}</small><small v-if="task.practiceDeliverable">交付：{{ task.practiceDeliverable }}</small></div></div></div>
              <span class="task-hours"><Clock3 :size="14" />{{ task.estimatedHours }}h</span>
              <el-select :disabled="!selectedPlanIsActive || taskSaving(task.taskId)" :model-value="task.status" @update:model-value="saveTask(task.taskId, String($event))"><el-option label="待开始" value="PENDING" /><el-option label="进行中" value="IN_PROGRESS" /><el-option label="已完成" value="COMPLETED" /><el-option label="已跳过" value="SKIPPED" /></el-select>
              <el-input v-model="taskFeedback[task.taskId]" :disabled="!selectedPlanIsActive || taskSaving(task.taskId)" placeholder="复盘备注" @change="saveTask(task.taskId, task.status)" />
              <LearningEvidenceForm :plan-id="selectedPlan.planId" :task="task" :readonly="selectedPlan.status !== 'ACTIVE' && selectedPlan.status !== 'COMPLETED'" @saved="recordTaskEvidence" />
              <small v-if="taskSaving(task.taskId)" class="task-save-state">正在保存…</small><small v-else-if="taskErrors[task.taskId]" class="task-save-state error">{{ taskErrors[task.taskId] }} <el-button link type="primary" :disabled="!selectedPlanIsActive" @click="saveTask(task.taskId, taskRetryStatus[task.taskId] || task.status)">重试</el-button></small>
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
        <div><h2>模拟面试会话</h2></div>
        <div class="interview-launch-actions"><label class="target-role-editor"><span>本次目标岗位</span><el-input v-model="interviewTargetRole" placeholder="例如 Java 后端" /></label><label><span>题目数量</span><el-input-number v-model="interviewQuestionCount" :min="1" :max="8" controls-position="right" aria-label="面试题数" /></label><el-button type="primary" :loading="interviewActionLoading" @click="startInterview"><Bot :size="16" />开始模拟面试</el-button><el-button @click="router.push({ path: '/student/interview', query: { tab: 'history' } })">会话历史</el-button><el-button @click="router.push('/student/interview')">当前会话</el-button></div>
      </section>

      <section v-if="interviewHistoryOpen" class="panel interview-history">
        <div class="section-heading"><div><h2>面试记录</h2></div></div>
        <el-empty v-if="!interviewSessions.length" description="暂无模拟面试记录" :image-size="92" />
        <div v-else class="session-grid"><button v-for="session in interviewSessions" :key="session.sessionId" class="session-card" :data-session-id="session.sessionId" :class="{ selected: session.sessionId === selectedSessionId }" @click="selectedSessionId = session.sessionId; selectSession(); router.push('/student/interview')"><div><span class="session-icon"><Bot :size="18" /></span><strong>{{ session.targetRole }}</strong></div><span>{{ session.status }} · {{ session.answers.length }}/{{ session.questions.length }} 题</span><div class="session-card-foot"><el-tag :type="sourceTagType(undefined, session.mocked)">{{ sourceTagLabel(undefined, session.mocked) }}</el-tag><ArrowUpRight :size="17" /></div></button></div>
      </section>
      <section v-else-if="selectedSession && activeQuestion" class="interview-workspace">
        <article class="panel interview-question-card">
          <div class="question-topline"><span>问题 {{ activeQuestionIndex + 1 }} / {{ selectedSession.questions.length }}</span><span>{{ selectedSessionProgress }}% 已作答</span></div>
          <div class="section-heading"><div><h2>模拟面试</h2></div></div>
          <div class="tag-row"><el-tag type="info">{{ activeQuestion.category || '综合' }}</el-tag><el-tag>{{ activeQuestion.difficulty || '普通' }}</el-tag><el-tag :type="sourceTagType(activeQuestion.source || activeQuestion.generationSource, selectedSession.mocked)">{{ sourceTagLabel(activeQuestion.source || activeQuestion.generationSource, selectedSession.mocked) }}</el-tag></div>
          <p class="question-text">{{ activeQuestion.question }}</p>
          <div v-if="activeQuestion.referencePoints?.length" class="reference-points"><span>答题参考</span><ul class="plain-list"><li v-for="point in activeQuestion.referencePoints" :key="point">{{ point }}</li></ul></div>
          <div class="question-nav"><el-button v-for="(_, index) in selectedSession.questions" :key="index" size="small" :type="index === activeQuestionIndex ? 'primary' : 'default'" @click="activeQuestionIndex = index">第 {{ index + 1 }} 题</el-button></div>
        </article>
        <article class="panel answer-card">
          <div class="section-heading"><div><h2>我的回答</h2></div></div>
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
        <div class="report-score"><div><h2>面试报告</h2><el-tag :type="sourceTagType(undefined, sessionReport.mocked)">{{ sourceTagLabel(undefined, sessionReport.mocked) }}</el-tag></div><strong>{{ sessionReport.overallScore }}<small>分</small></strong></div>
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
        <article class="overview-card accent-peach"><span>回答来源</span><strong class="provider-value">{{ knowledgeAnswer ? knowledgeAnswerLabel() : '—' }}</strong><Sparkles :size="22" /></article>
        <article class="overview-card accent-plain"><span>生成状态</span><strong>{{ knowledgeLoading ? '生成中' : knowledgeAnswer ? '已就绪' : '待提问' }}</strong><Compass :size="22" /></article>
      </section>
      <section class="panel knowledge-shell">
        <h2 class="panel-title"><span>知识库问答</span></h2>
        <div class="knowledge-mode"><span>回答模式</span><el-switch v-model="knowledgeUseAi" active-text="AI 回答" inactive-text="仅检索" /></div>
        <div class="knowledge-search">
          <el-input v-model="knowledgeQuery" placeholder="搜索 Java、Redis、面试或简历证据" @keyup.enter="runKnowledgeSearch" />
          <el-button type="primary" :loading="knowledgeLoading" @click="runKnowledgeSearch"><Search :size="17" />检索</el-button>
        </div>
        <div v-if="knowledgeRecentQueries.length" class="knowledge-history"><span>最近查询</span><el-button v-for="query in knowledgeRecentQueries" :key="query" text @click="knowledgeQuery = query; runKnowledgeSearch()">{{ query }}</el-button></div>
        <el-alert v-if="knowledgeError" class="knowledge-error" type="warning" :title="knowledgeError" :closable="false" show-icon><template #default><el-button link type="primary" @click="runKnowledgeSearch">重试</el-button></template></el-alert>
        <div v-if="knowledgeAnswer" class="knowledge-history"><el-tag type="info">{{ retrievalModeLabel(knowledgeAnswer.retrievalMode) }}</el-tag><el-tag>{{ knowledgeAnswerUsedAi ? 'AI 回答' : '检索资料' }}</el-tag></div>
        <details v-if="knowledgeAnswer" class="knowledge-source-details"><summary>来源详情</summary><p>回答来源: {{ knowledgeAnswer.provider || '未记录' }} · 分析版本: {{ knowledgeAnswer.algorithmVersion || '历史记录' }}</p></details>
        <el-alert v-if="knowledgeAnswer && ['NO_EVIDENCE', 'INSUFFICIENT_EVIDENCE', 'INSUFFICIENT'].includes(knowledgeAnswer.evidenceStatus || '')" title="现有资料不足以支持完整回答，请核对检索资料或补充知识库。" type="info" :closable="false" />
        <section v-if="knowledgeRetrieval" class="knowledge-retrieval"><header><strong>检索摘要</strong><span>{{ knowledgeRetrieval.results.length }} 条</span></header><el-empty v-if="!knowledgeRetrieval.results.length" description="未检索到可引用资料" :image-size="64" /><article v-for="result in knowledgeRetrieval.results" v-else :key="result.id" class="retrieval-result"><div><strong>{{ result.title }}</strong><span>{{ result.type }} · {{ result.owner }} · {{ result.score }} 分</span></div><p>{{ result.summary }}</p><small v-if="result.citation">{{ citationLocation(result.citation) }} · {{ result.citation.source }}</small><div class="tag-row"><el-tag v-for="highlight in result.highlights" :key="highlight" type="info">{{ highlight }}</el-tag></div></article></section>
        <div v-if="knowledgeAnswer" class="rag-answer">
          <header><strong>{{ knowledgeAnswerLabel() }}</strong><el-tag :type="knowledgeAnswer.mocked ? 'warning' : 'success'">{{ knowledgeAnswerUsedAi ? 'AI 回答' : '仅检索' }}</el-tag></header>
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
.student-workspace { display: grid; gap: 18px; min-width: 0; color: var(--ink, #20302b); font-size: 14px; }
.student-workspace :where(h2, h3, p, strong, span, small) { overflow-wrap: anywhere; }
.workspace-header, .section-heading, .result-header, .job-detail-meta, .question-topline, .report-score, .diagnosis-item-head, .match-record, .session-card-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.workspace-header.compact-workspace-header { justify-content: flex-end; padding: 0; }
.workspace-header.compact-workspace-header .target-role-control { display: block; width: min(300px, 100%); }
.section-heading { align-items: center; }
.section-heading > div, .resume-summary, .job-card-copy, .task-main > div, .diagnosis-item-head > div, .report-score > div { min-width: 0; }
.section-heading h2, .report-score h2, .knowledge-shell h2, .match-launcher h2, .interview-launch h2 { margin: 0; color: var(--ink, #20302b); font-size: 17px; font-weight: 700; line-height: 1.45; }
.section-heading p, .job-detail p, .resume-summary p { margin: 7px 0 0; color: var(--muted, #64716b); font-size: 14px; line-height: 1.7; }
.target-role-control, .form-field, .interview-launch-actions label { display: grid; gap: 6px; min-width: 0; }
.form-field > span, .interview-launch-actions label > span { color: var(--muted, #64716b); font-size: 13px; font-weight: 600; }
.overview-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0; padding: 6px 0; border-block: 1px solid var(--line, #dce3df); }
.overview-card { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-width: 0; min-height: 48px; padding: 6px 18px; border-right: 1px solid var(--line, #dce3df); background: transparent; }
.overview-card:first-child { padding-left: 0; }
.overview-card:last-child { border-right: 0; padding-right: 0; }
.overview-card span { color: var(--muted, #64716b); font-size: 13px; font-weight: 500; }
.overview-card strong { color: var(--ink, #20302b); font: 600 21px/1.2 "IBM Plex Mono", Consolas, monospace; white-space: nowrap; }
.overview-card strong small { margin-left: 3px; color: var(--muted, #64716b); font-family: inherit; font-size: 13px; font-weight: 500; }
.overview-card > svg { display: none; }
.overview-card .provider-value { font-size: 15px; white-space: normal; }
.knowledge-overview .overview-card strong { font-size: 15px; white-space: normal; }
.panel { min-width: 0; padding: 16px 0; border: 0; border-top: 1px solid var(--line, #dce3df); border-radius: 0; background: transparent; box-shadow: none; }
.resume-workspace, .jobs-layout, .match-history-grid, .plan-layout, .interview-workspace { display: grid; gap: 24px; min-width: 0; }
.resume-workspace { grid-template-columns: minmax(0, 1.2fr) minmax(0, .8fr); }
.legacy-resume-history { margin-top: 8px; border-top: 1px solid var(--line, #dce3df); padding-top: 14px; }
.legacy-resume-history > summary { color: var(--muted, #64716b); font-size: 13px; cursor: pointer; width: fit-content; margin-bottom: 16px; }
.upload-control { display: inline-flex; align-items: center; gap: 7px; min-height: 34px; padding: 0 12px; border: 1px solid var(--line, #dce3df); border-radius: 5px; color: var(--ink, #20302b); background: #fff; cursor: pointer; font-size: 13px; font-weight: 600; }
.upload-control input { display: none; }
.resume-hero-content { display: grid; grid-template-columns: minmax(160px, .75fr) 105px minmax(180px, 1.2fr) minmax(170px, .8fr); gap: 18px; align-items: center; margin-top: 18px; }
.resume-picker, .resume-status { display: grid; gap: 7px; min-width: 0; }
.resume-picker > span, .resume-status > span { color: var(--muted, #64716b); font-size: 13px; }
.resume-picker :deep(.el-button) { justify-self: start; padding: 0; }
.resume-score { display: grid; grid-template-columns: auto auto; align-items: baseline; gap: 3px; padding-left: 16px; border-left: 1px solid var(--line, #dce3df); }
.resume-score span { grid-column: 1 / -1; color: var(--muted, #64716b); font-size: 13px; }
.resume-score strong { color: var(--accent, #28664f); font: 600 30px/1.2 "IBM Plex Mono", Consolas, monospace; }
.resume-score small { font-size: 13px; color: var(--muted, #64716b); }
.resume-summary strong { font-size: 14px; }
.resume-diagnosis { margin-top: 18px; border-top: 1px solid var(--line, #dce3df); padding-top: 12px; }
.resume-diagnosis summary { color: var(--muted, #64716b); font-size: 13px; cursor: pointer; }
.diagnosis-report-copy { margin-top: 12px; color: var(--muted, #64716b); font-size: 14px; line-height: 1.8; }
.profile-panel, .diagnosis-panel, .job-browser, .job-detail, .plan-sidebar, .task-panel, .interview-question-card, .answer-card, .interview-history, .interview-report { min-width: 0; }
.profile-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 13px; margin-top: 18px; }
.profile-form .wide { grid-column: 1 / -1; }
.action-bar { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.rewrite-result { display: grid; gap: 12px; margin-top: 18px; padding: 14px 0; border-top: 1px solid var(--line, #dce3df); color: var(--muted, #64716b); font-size: 14px; line-height: 1.7; }
.rewrite-result :deep(p) { margin: 0; }
.rewrite-detail > strong { color: var(--ink, #20302b); }
.form-dirty-note, .plan-context { margin: 0; color: #9a651b; font-size: 13px; line-height: 1.7; }
.plan-context { color: var(--muted, #64716b); }
.tag-row { display: flex; flex-wrap: wrap; gap: 6px; }
.diagnosis-list { display: grid; gap: 12px; margin-top: 16px; }
.diagnosis-item { display: grid; gap: 8px; padding-top: 14px; border-top: 1px solid var(--line, #dce3df); }
.diagnosis-item:first-child { padding-top: 0; border-top: 0; }
.diagnosis-item-head strong { display: block; font-size: 14px; }
.diagnosis-item-head span { display: block; margin-top: 4px; color: var(--muted, #64716b); font-size: 12px; }
.diagnosis-item-head b { color: var(--accent, #28664f); font-size: 20px; white-space: nowrap; }
.diagnosis-item-head b small { margin-left: 2px; font-size: 12px; }
.diagnosis-copy { max-height: 160px; overflow: auto; color: var(--muted, #64716b); font-size: 13px; line-height: 1.7; }
.diagnosis-copy :deep(p) { margin: 0; }
.jobs-layout { grid-template-columns: minmax(280px, .75fr) minmax(0, 1.25fr); }
.job-browser, .job-detail, .match-result, .match-history, .plan-sidebar, .interview-question-card, .answer-card { display: grid; align-content: start; gap: 16px; }
.result-count { color: var(--muted, #64716b); font-size: 13px; white-space: nowrap; }
.job-card-list { display: grid; gap: 2px; max-height: 480px; overflow: auto; padding-right: 3px; }
.job-card { display: grid; grid-template-columns: 34px minmax(0, 1fr) 16px; gap: 10px; align-items: center; width: 100%; padding: 12px 8px; border: 0; border-left: 2px solid transparent; border-bottom: 1px solid var(--line, #dce3df); border-radius: 0; background: transparent; color: var(--ink, #20302b); cursor: pointer; text-align: left; }
.job-card:hover { background: #f3f6f4; }
.job-card.selected { border-left-color: var(--accent, #28664f); background: #edf5f0; }
.job-card:focus-visible { outline: 2px solid var(--accent, #28664f); outline-offset: -2px; }
.job-card-mark { display: grid; width: 34px; height: 34px; place-items: center; border: 1px solid var(--line, #dce3df); border-radius: 5px; background: #fff; color: var(--muted, #64716b); font-size: 14px; font-weight: 700; }
.job-card-copy { display: grid; gap: 4px; }
.job-card-copy strong { font-size: 14px; line-height: 1.45; }
.job-card-copy small { color: var(--muted, #64716b); font-size: 12px; }
.job-card-copy em { color: var(--accent, #28664f); font-size: 13px; font-style: normal; font-weight: 600; }
.job-card > svg { color: var(--muted, #64716b); }
.job-filter-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.job-detail-stack { display: grid; align-content: start; gap: 18px; min-width: 0; }
.job-detail { min-height: 200px; }
.job-detail-meta { justify-content: flex-start; flex-wrap: wrap; gap: 12px; color: var(--muted, #64716b); font-size: 13px; }
.job-detail-meta span { display: inline-flex; align-items: center; gap: 5px; }
.job-detail-meta strong { color: var(--accent, #28664f); }
.match-launcher { display: grid; gap: 14px; }
.match-controls { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; gap: 8px; align-items: center; }
.match-history-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.coverage-score { display: flex; align-items: center; gap: 16px; }
.coverage-score > strong { color: var(--accent, #28664f); font: 600 36px/1.2 "IBM Plex Mono", Consolas, monospace; }
.coverage-score > strong small { font-size: 16px; }
.coverage-score b, .coverage-score span { display: block; }
.coverage-score b { font-size: 14px; }
.coverage-score span { margin-top: 4px; color: var(--muted, #64716b); font-size: 13px; }
.match-insights { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; border-top: 1px solid var(--line, #dce3df); padding-top: 14px; }
.match-insights > div { min-width: 0; }
.match-insights span { color: var(--muted, #64716b); font-size: 13px; font-weight: 600; }
.match-insights p { margin: 6px 0 0; font-size: 14px; line-height: 1.7; }
.match-next-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.match-records { display: grid; }
.match-record { width: 100%; padding: 12px 0; border: 0; border-top: 1px solid var(--line, #dce3df); background: transparent; color: inherit; cursor: pointer; text-align: left; }
.match-record:hover { background: #f3f6f4; }
.match-record div { display: grid; gap: 4px; }
.match-record strong { font-size: 14px; }
.match-record span { color: var(--muted, #64716b); font-size: 12px; }
.match-record b { color: var(--accent, #28664f); font-size: 20px; white-space: nowrap; }
.plan-builder { display: grid; gap: 16px; }
.plan-builder-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 13px; align-items: end; }
.plan-builder-fields :deep(.el-input-number) { width: 100%; }
.plan-layout { grid-template-columns: minmax(260px, .65fr) minmax(0, 1.35fr); }
.plan-summary-card { display: grid; gap: 10px; padding: 12px 0; border-block: 1px solid var(--line, #dce3df); }
.plan-summary-card > div:first-child { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.plan-summary-card span { font-size: 14px; font-weight: 600; }
.plan-summary-card strong { color: var(--accent, #28664f); }
.plan-summary-card p { margin: 0; color: var(--muted, #64716b); font-size: 13px; }
.version-rail, .version-actions { display: flex; flex-wrap: wrap; gap: 7px; }
.version-rail span { padding: 5px 8px; border-bottom: 2px solid var(--line, #dce3df); color: var(--muted, #64716b); font-size: 12px; }
.version-rail span.current { border-color: var(--accent, #28664f); color: var(--accent, #28664f); }
.replan-form { display: grid; gap: 10px; padding-top: 14px; border-top: 1px solid var(--line, #dce3df); }
.replan-form > span { color: var(--muted, #64716b); font-size: 13px; font-weight: 600; }
.progress-text { display: grid; justify-items: end; gap: 2px; }
.progress-text strong { color: var(--accent, #28664f); font: 600 20px/1.2 "IBM Plex Mono", Consolas, monospace; }
.progress-text span { color: var(--muted, #64716b); font-size: 12px; }
.task-list { display: grid; margin-top: 14px; }
.task-row { display: grid; grid-template-columns: minmax(0, 1fr) 56px 118px; gap: 12px; align-items: start; padding: 16px 0; border-top: 1px solid var(--line, #dce3df); min-width: 0; }
.task-main { display: grid; grid-template-columns: 32px minmax(0, 1fr); gap: 10px; min-width: 0; }
.week-chip { display: grid; width: 32px; height: 32px; place-items: center; border: 1px solid var(--line, #dce3df); border-radius: 4px; color: var(--accent, #28664f); font: 600 12px/1 "IBM Plex Mono", Consolas, monospace; }
.task-main strong { display: block; font-size: 14px; line-height: 1.55; }
.task-main p { margin: 4px 0 0; color: var(--muted, #64716b); font-size: 13px; line-height: 1.7; }
.task-detail-lines { display: flex; flex-wrap: wrap; gap: 4px 8px; margin-top: 7px; }
.task-detail-lines small { color: var(--muted, #64716b); font-size: 12px; line-height: 1.6; }
.task-hours { display: inline-flex; align-items: center; gap: 3px; color: var(--muted, #64716b); font-size: 13px; white-space: nowrap; padding-top: 5px; }
.task-row > .el-input, .task-row > :last-child { grid-column: 1 / -1; min-width: 0; }
.task-row :deep(.el-select), .task-row :deep(.el-input) { min-width: 0; width: 100%; }
.task-save-state { grid-column: 1 / -1; color: var(--accent, #28664f); font-size: 13px; }
.task-save-state.error { color: #b44339; }
.interview-launch { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.interview-launch-actions { display: flex; flex-wrap: wrap; align-items: end; justify-content: flex-end; gap: 8px; }
.interview-launch-actions label { width: 112px; }
.interview-launch-actions .target-role-editor { width: 190px; }
.interview-launch-actions :deep(.el-input-number) { width: 100%; }
.session-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 16px; }
.session-card { display: grid; gap: 12px; padding: 14px; border: 1px solid var(--line, #dce3df); border-radius: 6px; background: #fff; color: var(--ink, #20302b); cursor: pointer; text-align: left; }
.session-card:hover, .session-card.selected { border-color: var(--accent, #28664f); }
.session-card > div:first-child { display: flex; align-items: center; gap: 8px; }
.session-icon { display: grid; width: 28px; height: 28px; place-items: center; color: var(--accent, #28664f); }
.session-card strong { font-size: 14px; }
.session-card > span { color: var(--muted, #64716b); font-size: 13px; }
.interview-workspace { grid-template-columns: minmax(0, 1fr) minmax(0, .92fr); }
.question-topline { padding-bottom: 12px; border-bottom: 1px solid var(--line, #dce3df); color: var(--muted, #64716b); font-size: 13px; }
.question-text { margin: 0; color: var(--ink, #20302b); font-size: 18px; font-weight: 600; line-height: 1.7; }
.question-feedback { padding: 12px 0 12px 12px; border-left: 2px solid var(--accent, #28664f); }
.reference-points { padding: 14px 0; border-top: 1px solid var(--line, #dce3df); }
.reference-points > span { color: var(--muted, #64716b); font-size: 13px; font-weight: 600; }
.plain-list { display: grid; gap: 6px; margin: 8px 0 0; padding-left: 18px; color: var(--muted, #64716b); font-size: 14px; line-height: 1.7; }
.question-nav, .answer-actions { display: flex; justify-content: space-between; gap: 8px; padding-top: 14px; border-top: 1px solid var(--line, #dce3df); }
.answer-actions { justify-content: flex-end; }
.answer-input :deep(textarea) { min-height: 260px !important; resize: vertical; }
.report-score { align-items: start; padding-bottom: 16px; border-bottom: 1px solid var(--line, #dce3df); }
.report-score strong { color: var(--accent, #28664f); font: 600 38px/1.2 "IBM Plex Mono", Consolas, monospace; }
.report-score strong small { margin-left: 3px; font-size: 14px; }
.report-columns { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; margin-top: 16px; }
.report-columns > div { min-width: 0; }
.report-columns > div > span { color: var(--muted, #64716b); font-size: 13px; font-weight: 600; }
.knowledge-shell { display: grid; gap: 16px; }
.knowledge-shell .panel-title { font-size: 17px; }
.knowledge-search { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.knowledge-mode, .knowledge-history { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 4px 0; color: var(--muted, #64716b); font-size: 13px; }
.knowledge-retrieval { display: grid; gap: 10px; margin-top: 8px; padding-top: 14px; border-top: 1px solid var(--line, #dce3df); }
.knowledge-retrieval > header { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; color: var(--muted, #64716b); font-size: 13px; }
.retrieval-result { padding: 12px 0; border-top: 1px solid var(--line, #dce3df); }
.retrieval-result > div:first-child { display: flex; justify-content: space-between; gap: 10px; }
.retrieval-result > div:first-child span { color: var(--muted, #64716b); font-size: 12px; }
.retrieval-result p { margin: 7px 0; color: var(--muted, #64716b); font-size: 14px; line-height: 1.7; }
.rag-answer { display: grid; gap: 16px; padding-top: 16px; border-top: 2px solid var(--accent, #28664f); }
.rag-answer > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.rag-answer > header strong { font-size: 16px; }
.knowledge-answer { color: var(--ink, #20302b); font-size: 14px; line-height: 1.8; }
.knowledge-answer :deep(p) { margin: 0 0 10px; }
.claim-list { display: grid; gap: 10px; font-size: 14px; }
.claim-list article { padding: 10px 0; border-top: 1px solid var(--line, #dce3df); }
.claim-list p { margin: 0 0 5px; line-height: 1.7; white-space: pre-wrap; }
.claim-list small { color: var(--muted, #64716b); font-size: 12px; }
.claim-list blockquote { margin: 8px 0 0; padding-left: 10px; border-left: 2px solid var(--accent, #28664f); color: var(--muted, #64716b); white-space: pre-wrap; }
.citation-list { display: grid; gap: 9px; }
.citation-row { padding-top: 12px; border-top: 1px solid var(--line, #dce3df); }
.citation-row summary { color: var(--ink, #20302b); cursor: pointer; font-size: 14px; font-weight: 600; line-height: 1.65; }
.citation-row p { margin: 6px 0; color: var(--muted, #64716b); font-size: 12px; }
.citation-row div { color: var(--muted, #64716b); font-size: 14px; line-height: 1.7; }
.citation-row div :deep(p) { margin: 0; }
.preview-task { padding: 12px 0; border-top: 1px solid var(--line, #dce3df); font-size: 14px; line-height: 1.7; }
.preview-task .el-tag { margin-left: 10px; }
.compare-toolbar { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; align-items: center; }
.compare-toolbar :deep(.el-select) { min-width: 0; }
.comparison-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-top: 14px; }
.comparison-grid > article { min-width: 0; padding: 0 16px 0 0; border-right: 1px solid var(--line, #dce3df); }
.comparison-grid > article:last-child { border-right: 0; }
.comparison-grid small { display: block; margin-top: 4px; color: var(--muted, #64716b); font-size: 12px; }
.comparison-grid p { margin: 8px 0 0; color: var(--muted, #64716b); font-size: 14px; line-height: 1.7; }
.comparison-requirement { margin-top: 12px; }
.comparison-requirement summary { cursor: pointer; line-height: 1.8; }
.comparison-evidence { font-weight: 600; color: var(--ink, #20302b) !important; }
:deep(.el-input__wrapper), :deep(.el-textarea__inner), :deep(.el-select__wrapper) { border-radius: 5px; }
:deep(.el-button) { border-radius: 5px; font-weight: 600; }
:deep(.el-button + .el-button) { margin-left: 0; }
:deep(.el-tag) { border-radius: 4px; font-size: 12px; }
@media (max-width: 1180px) {
  .resume-hero-content { grid-template-columns: minmax(160px, 1fr) 105px minmax(180px, 1fr); }
  .resume-status { grid-column: 1 / -1; grid-template-columns: auto 1fr; align-items: center; }
  .session-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .match-controls { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .match-controls > :last-child { grid-column: 1 / -1; }
}
@media (max-width: 900px) {
  .resume-workspace, .jobs-layout, .plan-layout, .interview-workspace, .match-history-grid { grid-template-columns: 1fr; }
  .overview-card { padding-inline: 10px; }
  .plan-builder-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .interview-launch { align-items: start; flex-direction: column; }
  .interview-launch-actions { justify-content: flex-start; }
}
@media (max-width: 600px) {
  .student-workspace { gap: 16px; }
  .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .overview-card { min-height: 43px; padding: 6px 10px; }
  .overview-card:nth-child(2n) { border-right: 0; padding-right: 0; }
  .overview-card:nth-child(2n + 1) { padding-left: 0; }
  .overview-card strong { font-size: 19px; }
  .overview-card span { font-size: 12px; }
  .resume-hero-content, .profile-form, .plan-builder-fields, .match-controls, .report-columns, .match-insights, .compare-toolbar, .comparison-grid { grid-template-columns: 1fr; }
  .resume-score { padding: 0; border-left: 0; }
  .resume-status { grid-template-columns: 1fr; }
  .profile-form .wide { grid-column: auto; }
  .plan-builder-fields > .el-button { width: 100%; }
  .task-row { grid-template-columns: minmax(0, 1fr) 56px; }
  .task-row > :nth-child(3) { grid-column: 1 / -1; }
  .task-hours { justify-self: end; }
  .session-grid { grid-template-columns: 1fr; }
  .question-nav, .answer-actions { flex-wrap: wrap; }
  .question-nav :deep(.el-button), .answer-actions :deep(.el-button) { flex: 1; }
  .knowledge-search { grid-template-columns: 1fr; }
  .knowledge-search :deep(.el-button), .compare-toolbar :deep(.el-button) { width: 100%; }
  .comparison-grid > article { padding: 0 0 14px; border: 0; border-bottom: 1px solid var(--line, #dce3df); }
  .coverage-score { align-items: flex-start; }
  .coverage-score > strong { font-size: 30px; }
}
</style>
