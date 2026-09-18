<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import MarkdownIt from 'markdown-it'
import {
  Bot,
  BrainCircuit,
  BriefcaseBusiness,
  CheckCircle2,
  FileText,
  Library,
  RefreshCw,
  Route,
  Search,
  Sparkles,
  Upload
} from 'lucide-vue-next'
import {
  analyzeResume,
  answerKnowledgeBase,
  createInterviewSession,
  createLearningPlan,
  deleteResume,
  finishInterviewSession,
  getProfile,
  getInterviewSession,
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
  searchKnowledgeBase,
  updateLearningTask,
  updateResumeProfile,
  uploadResume,
  type InterviewSession,
  type InterviewSessionReport,
  type JobSummary,
  type KnowledgeAnswerResponse,
  type LearningPlan,
  type MatchResult,
  type ResumeDiagnosis,
  type ResumeSummary,
  type ResumeRewriteResponse,
  type UserProfile
} from '../api/client'

const route = useRoute()
const router = useRouter()
const markdown = new MarkdownIt({ breaks: true, linkify: true })
const activeModule = computed(() => typeof route.params.module === 'string' ? route.params.module : 'resume')
const moduleTitle: Record<string, string> = {
  resume: '简历管理',
  jobs: '岗位匹配',
  plan: '学习路径',
  interview: '模拟面试',
  knowledge: '知识库问答'
}

const profile = ref<UserProfile>()
const targetRole = ref('')
const resumes = ref<ResumeSummary[]>([])
const selectedResumeId = ref('')
const diagnoses = ref<ResumeDiagnosis[]>([])
const resumeRewrite = ref<ResumeRewriteResponse>()
const resumeLoading = ref(false)
const resumeActionLoading = ref(false)
const resumeForm = reactive({
  education: '',
  skills: '',
  projects: '',
  targetJob: ''
})

const jobs = ref<JobSummary[]>([])
const matches = ref<MatchResult[]>([])
const selectedJobId = ref('')
const currentMatch = ref<MatchResult>()
const jobsLoading = ref(false)
const matchLoading = ref(false)

const plans = ref<LearningPlan[]>([])
const selectedPlanId = ref('')
const planVersions = ref<LearningPlan[]>([])
const planLoading = ref(false)
const planActionLoading = ref(false)
const planForm = reactive({
  targetRole: '',
  weeklyHours: 6,
  durationWeeks: 8,
  replanReason: ''
})
const taskFeedback = ref<Record<string, string>>({})

const interviewSessions = ref<InterviewSession[]>([])
const selectedSessionId = ref('')
const selectedCompletedSessionId = ref('')
const sessionReport = ref<InterviewSessionReport>()
const interviewLoading = ref(false)
const interviewActionLoading = ref(false)
const activeQuestionIndex = ref(0)
const answerDrafts = ref<Record<string, string>>({})
const interviewQuestionCount = ref(5)

const knowledgeQuery = ref('Java Redis 面试')
const knowledgeAnswer = ref<KnowledgeAnswerResponse>()
const knowledgeLoading = ref(false)

const selectedResume = computed(() => resumes.value.find((resume) => resume.resumeId === selectedResumeId.value))
const selectedJob = computed(() => jobs.value.find((job) => job.jobId === selectedJobId.value))
const selectedPlan = computed(() => plans.value.find((plan) => plan.planId === selectedPlanId.value))
const selectedPlanIsActive = computed(() => selectedPlan.value?.status === 'ACTIVE')
const selectedSession = computed(() => interviewSessions.value.find((session) => session.sessionId === selectedSessionId.value))
const activeQuestion = computed(() => selectedSession.value?.questions[activeQuestionIndex.value])
const interviewHistoryOpen = computed(() => route.query.tab === 'history')
const compatibleCompletedSessions = computed(() => {
  const plan = selectedPlan.value
  return plan
    ? interviewSessions.value.filter((session) => session.status === 'COMPLETED' && session.targetRole === plan.targetRole)
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
    if (activeQuestion.value) {
      answerDrafts.value = { ...answerDrafts.value, [activeQuestion.value.questionId]: value }
    }
  }
})

function splitLines(value: string) {
  return value.split(/[\n,，]/).map((item) => item.trim()).filter(Boolean)
}

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
  resumeForm.education = resume?.education || ''
  resumeForm.skills = resume?.skills.join(', ') || profile.value?.skills.join(', ') || ''
  resumeForm.projects = resume?.projects.join('\n') || ''
  resumeForm.targetJob = targetRole.value
}

function syncTargetRole() {
  const userId = profile.value?.userId
  if (!userId) {
    return
  }
  const saved = localStorage.getItem(`aicampus.target-role.${userId}`)?.trim()
  targetRole.value = saved || profile.value?.targetPosition || ''
  planForm.targetRole = planForm.targetRole || targetRole.value
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
    return JSON.parse(localStorage.getItem(key) || '{}') as { resumeId?: string, jobId?: string }
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
      jobId: jobs.value.length ? selectedJobId.value : saved.jobId
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
    const [profileData, resumeList] = await Promise.all([getProfile(), listResumes()])
    profile.value = profileData
    syncTargetRole()
    resumes.value = resumeList
    syncSelectedResume()
    await loadDiagnoses()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '简历数据加载失败')
  } finally {
    resumeLoading.value = false
  }
}

async function loadDiagnoses() {
  if (!selectedResumeId.value) {
    diagnoses.value = []
    return
  }
  try {
    diagnoses.value = await listResumeDiagnoses(selectedResumeId.value)
  } catch (error) {
    diagnoses.value = []
    ElMessage.error(error instanceof Error ? error.message : '诊断记录加载失败')
  }
}

async function selectResume() {
  hydrateResumeForm(selectedResume.value)
  await loadDiagnoses()
}

async function handleResumeUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) {
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
  resumeActionLoading.value = true
  try {
    const updated = await updateResumeProfile(selectedResume.value.resumeId, {
      education: resumeForm.education.trim(),
      skills: splitLines(resumeForm.skills),
      projects: splitLines(resumeForm.projects)
    })
    resumes.value = resumes.value.map((resume) => resume.resumeId === updated.resumeId ? updated : resume)
    hydrateResumeForm(updated)
    currentMatch.value = undefined
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
  resumeActionLoading.value = true
  try {
    const analyzed = await analyzeResume(selectedResume.value.resumeId, { targetJob: targetRole.value.trim() })
    resumes.value = resumes.value.map((resume) => resume.resumeId === analyzed.resumeId ? analyzed : resume)
    currentMatch.value = undefined
    await selectResume()
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
  resumeActionLoading.value = true
  try {
    resumeRewrite.value = await rewriteResume({
      studentId: profile.value?.userId || '',
      resumeId: resume.resumeId,
      targetRole: targetRole.value.trim() || profile.value?.targetPosition || '目标岗位',
      resumeSummary: resume.diagnosis,
      skills: splitLines(resumeForm.skills),
      projects: splitLines(resumeForm.projects)
    })
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
  resumeActionLoading.value = true
  try {
    await deleteResume(resume.resumeId)
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
    ElMessage.warning('请先选择简历和岗位')
    return
  }
  matchLoading.value = true
  try {
    currentMatch.value = await matchResumeJob(resume.resumeId, job.jobId)
    matches.value = [currentMatch.value, ...matches.value.filter((match) => match.matchId !== currentMatch.value?.matchId)]
    ElMessage.success('岗位匹配已完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '岗位匹配失败')
  } finally {
    matchLoading.value = false
  }
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
  const plan = selectedPlan.value
  if (plan) {
    planForm.weeklyHours = plan.weeklyHours
    planForm.durationWeeks = plan.durationWeeks
  }
  syncTaskFeedback(plan)
  if (!selectedPlanId.value) {
    planVersions.value = []
    return
  }
  try {
    planVersions.value = await listLearningPlanVersions(selectedPlanId.value)
  } catch (error) {
    planVersions.value = []
    ElMessage.error(error instanceof Error ? error.message : '计划版本加载失败')
  }
}

function syncTaskFeedback(plan?: LearningPlan) {
  taskFeedback.value = Object.fromEntries((plan?.tasks || []).map((task) => [task.taskId, task.feedback || '']))
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
      matchId: currentMatch.value?.matchId,
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
  planActionLoading.value = true
  try {
    const currentTask = plan.tasks.find((task) => task.taskId === taskId)
    const updated = await updateLearningTask(plan.planId, taskId, {
      status,
      feedback: taskFeedback.value[taskId] ?? currentTask?.feedback
    })
    taskFeedback.value = { ...taskFeedback.value, [taskId]: updated.feedback || '' }
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
    ElMessage.error(error instanceof Error ? error.message : '任务进度保存失败')
  } finally {
    planActionLoading.value = false
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
      interviewSessionId: compatibleInterviewSessionId.value
    })
    plans.value = [
      revised,
      ...plans.value
        .filter((item) => item.planId !== revised.planId)
        .map((item) => item.planId === plan.planId
          ? { ...item, status: 'SUPERSEDED', updatedAt: revised.createdAt }
          : item)
    ]
    selectedPlanId.value = revised.planId
    planForm.replanReason = ''
    await loadPlanVersions()
    ElMessage.success(`已生成 V${revised.version} 学习计划`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学习计划重规划失败')
  } finally {
    planActionLoading.value = false
  }
}

function syncSessionDrafts(session?: InterviewSession) {
  answerDrafts.value = Object.fromEntries((session?.answers || []).map((answer) => [answer.questionId, answer.answer]))
  sessionReport.value = session?.report
  const answeredQuestionIds = new Set((session?.answers || [])
    .filter((answer) => answer.answer.trim())
    .map((answer) => answer.questionId))
  const firstUnansweredIndex = session?.questions.findIndex((question) => !answeredQuestionIds.has(question.questionId)) ?? -1
  activeQuestionIndex.value = firstUnansweredIndex >= 0
    ? firstUnansweredIndex
    : Math.max(0, (session?.questions.length || 1) - 1)
}

async function loadInterviewSessions() {
  interviewLoading.value = true
  try {
    interviewSessions.value = await listInterviewSessions()
    if (!selectedSessionId.value || !interviewSessions.value.some((session) => session.sessionId === selectedSessionId.value)) {
      selectedSessionId.value = interviewSessions.value[0]?.sessionId || ''
    }
    syncSessionDrafts(selectedSession.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模拟面试会话加载失败')
  } finally {
    interviewLoading.value = false
  }
}

async function selectSession() {
  if (!selectedSessionId.value) {
    return
  }
  interviewLoading.value = true
  try {
    const session = await getInterviewSession(selectedSessionId.value)
    interviewSessions.value = interviewSessions.value.map((item) => item.sessionId === session.sessionId ? session : item)
    syncSessionDrafts(session)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模拟面试会话读取失败')
  } finally {
    interviewLoading.value = false
  }
}

async function startInterview() {
  if (!validateInterviewQuestionCount()) {
    return
  }
  interviewActionLoading.value = true
  try {
    const session = await createInterviewSession({
      studentId: profile.value?.userId,
      resumeId: selectedResume.value?.resumeId,
      jobId: selectedJob.value?.jobId,
      matchId: currentMatch.value?.matchId,
      targetRole: selectedJob.value?.title || profile.value?.targetPosition,
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
  interviewActionLoading.value = true
  try {
    const updated = await saveInterviewSessionAnswer(session.sessionId, question.questionId, currentAnswer.value)
    interviewSessions.value = interviewSessions.value.map((item) => item.sessionId === updated.sessionId ? updated : item)
    syncSessionDrafts(updated)
    ElMessage.success('回答已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '回答保存失败')
  } finally {
    interviewActionLoading.value = false
  }
}

async function finishInterview() {
  const session = selectedSession.value
  if (!session) {
    return
  }
  interviewActionLoading.value = true
  try {
    sessionReport.value = await finishInterviewSession(session.sessionId)
    await selectSession()
    ElMessage.success('模拟面试报告已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模拟面试完成失败')
  } finally {
    interviewActionLoading.value = false
  }
}

async function runKnowledgeSearch() {
  if (!knowledgeQuery.value.trim()) {
    ElMessage.warning('请输入检索关键词')
    return
  }
  knowledgeLoading.value = true
  try {
    const [retrieval, answer] = await Promise.all([
      searchKnowledgeBase({ query: knowledgeQuery.value.trim(), role: 'STUDENT', limit: 6 }),
      answerKnowledgeBase({ query: knowledgeQuery.value.trim(), role: 'STUDENT', limit: 8, useAi: true })
    ])
    knowledgeAnswer.value = answer
    if (!answer.citations.length && !retrieval.results.length) {
      ElMessage.warning('没有找到相关知识资料')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库检索失败')
  } finally {
    knowledgeLoading.value = false
  }
}

async function loadModule(module: string) {
  if (module === 'resume') {
    await loadResumeData()
  } else if (module === 'jobs') {
    await Promise.all([loadResumeData(), loadJobsData()])
  } else if (module === 'plan') {
    await Promise.all([loadResumeData(), loadJobsData(), loadPlans(), loadInterviewSessions()])
  } else if (module === 'interview') {
    await Promise.all([loadResumeData(), loadJobsData(), loadInterviewSessions()])
  }
}

onMounted(() => { void loadModule(activeModule.value) })
watch(activeModule, (module) => { void loadModule(module) })
watch([selectedResumeId, selectedJobId], () => {
  if (currentMatch.value && (currentMatch.value.resumeId !== selectedResumeId.value || currentMatch.value.jobId !== selectedJobId.value)) {
    currentMatch.value = undefined
  }
  persistSelection()
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
  <section class="page">
    <header class="page-header">
      <div>
        <h1 class="page-title">{{ moduleTitle[activeModule] || '学生工作台' }}</h1>
        <p class="page-subtitle">围绕简历、岗位、学习和面试完成求职准备</p>
      </div>
      <div class="target-role-control">
        <span>目标岗位</span>
        <el-input v-model="targetRole" placeholder="例如 Java 后端实习生" />
      </div>
    </header>

    <template v-if="activeModule === 'resume'">
      <section class="panel module-panel" v-loading="resumeLoading">
        <h2 class="panel-title"><span>我的简历</span><FileText :size="19" /></h2>
        <div class="resume-toolbar">
          <el-select v-model="selectedResumeId" placeholder="选择简历" @change="selectResume">
            <el-option v-for="resume in resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" />
          </el-select>
          <label class="upload-control">
            <Upload :size="16" />
            <span>上传简历</span>
            <input type="file" accept=".pdf,.doc,.docx" :disabled="resumeActionLoading" @change="handleResumeUpload" />
          </label>
          <el-button :loading="resumeActionLoading" @click="removeResume">删除</el-button>
        </div>
        <div v-if="selectedResume" class="grid two resume-summary-grid">
          <div class="item-card">
            <div class="score">{{ selectedResume.score }}</div>
            <div>
              <strong>{{ selectedResume.fileName }}</strong>
              <div v-html="renderMarkdown(selectedResume.diagnosis)" />
            </div>
          </div>
          <div class="item-card">
            <strong>解析状态</strong>
            <div class="tag-row">
              <el-tag type="info">{{ selectedResume.sourceFormat || '未知格式' }}</el-tag>
              <el-tag type="success">{{ selectedResume.parseStatus || '待解析' }}</el-tag>
              <el-tag>{{ selectedResume.parsedTextLength || 0 }} 字符</el-tag>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无简历，请先上传" />
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title"><span>简历资料与诊断</span><RefreshCw :size="19" /></h2>
        <div class="form-grid">
          <el-input v-model="resumeForm.education" placeholder="学历与专业" />
          <el-input v-model="targetRole" placeholder="目标岗位" />
          <el-input v-model="resumeForm.skills" type="textarea" :rows="3" placeholder="技能，使用逗号或换行分隔" />
          <el-input v-model="resumeForm.projects" type="textarea" :rows="3" placeholder="项目，使用逗号或换行分隔" />
        </div>
        <div class="actions">
          <el-button type="primary" :loading="resumeActionLoading" @click="saveResumeProfile">保存资料</el-button>
          <el-button :loading="resumeActionLoading" @click="runResumeAnalysis">重新诊断</el-button>
          <el-button :loading="resumeActionLoading" @click="runResumeRewrite">生成改写</el-button>
        </div>
        <div v-if="diagnoses.length" class="history-list">
          <article v-for="diagnosis in diagnoses" :key="diagnosis.diagnosisId" class="history-row">
            <strong>{{ diagnosis.targetJob || '通用诊断' }}</strong>
            <span>{{ diagnosis.score }} 分 · {{ diagnosis.createdAt }}</span>
            <div class="tag-row"><el-tag :type="sourceTagType(diagnosis.source)">{{ sourceTagLabel(diagnosis.source) }}</el-tag></div>
            <div v-html="renderMarkdown(diagnosis.diagnosis)" />
          </article>
        </div>
        <div v-if="resumeRewrite" class="rewrite-result">
          <div class="result-header"><strong>改写摘要</strong><el-tag :type="sourceTagType(undefined, resumeRewrite.mocked)">{{ sourceTagLabel(undefined, resumeRewrite.mocked) }}</el-tag></div>
          <div v-html="renderMarkdown(resumeRewrite.improvedSummary)" />
          <div class="tag-row"><el-tag v-for="keyword in resumeRewrite.keywordSuggestions" :key="keyword">{{ keyword }}</el-tag></div>
        </div>
      </section>
    </template>

    <template v-else-if="activeModule === 'jobs'">
      <section class="panel module-panel" v-loading="jobsLoading">
        <h2 class="panel-title"><span>岗位与匹配</span><BriefcaseBusiness :size="19" /></h2>
        <div class="match-toolbar">
          <el-select v-model="selectedResumeId" placeholder="选择简历" @change="selectResume">
            <el-option v-for="resume in resumes" :key="resume.resumeId" :label="resume.fileName" :value="resume.resumeId" />
          </el-select>
          <el-select v-model="selectedJobId" placeholder="选择岗位">
            <el-option v-for="job in jobs" :key="job.jobId" :label="`${job.title} · ${job.companyName}`" :value="job.jobId" />
          </el-select>
          <el-button type="primary" :loading="matchLoading" @click="runMatch">匹配</el-button>
        </div>
        <article v-if="selectedJob" class="item-card selected-job-card">
          <div>
            <strong>{{ selectedJob.title }}</strong>
            <span>{{ selectedJob.companyName }} · {{ selectedJob.city }} · {{ selectedJob.salaryRange }}</span>
          </div>
          <p>{{ selectedJob.description }}</p>
          <div class="tag-row"><el-tag v-for="skill in selectedJob.requiredSkills" :key="skill">{{ skill }}</el-tag></div>
        </article>
      </section>
      <section class="grid two">
        <article v-if="currentMatch" class="panel module-panel match-result">
          <h2 class="panel-title"><span>本次匹配结果</span><CheckCircle2 :size="19" /></h2>
          <strong class="match-score">技能覆盖率 {{ currentMatch.score }}%</strong>
          <div class="tag-row"><el-tag :type="sourceTagType(currentMatch.analysisSource)">{{ sourceTagLabel(currentMatch.analysisSource) }}</el-tag></div>
          <p>优势：{{ currentMatch.strengths.join('；') }}</p>
          <p>待补齐：{{ currentMatch.gaps.join('；') }}</p>
        </article>
        <article class="panel module-panel">
          <h2 class="panel-title"><span>匹配覆盖</span><Sparkles :size="19" /></h2>
          <el-empty v-if="!matches.length" description="尚无匹配记录" />
          <div v-else class="history-list compact">
            <div v-for="match in matches" :key="match.matchId" class="history-row">
              <strong>{{ jobs.find((job) => job.jobId === match.jobId)?.title || match.jobId }}</strong>
              <span>技能覆盖率 {{ match.score }}%</span>
              <div class="tag-row"><el-tag :type="sourceTagType(match.analysisSource)">{{ sourceTagLabel(match.analysisSource) }}</el-tag></div>
            </div>
          </div>
        </article>
      </section>
    </template>

    <template v-else-if="activeModule === 'plan'">
      <section class="panel module-panel" v-loading="planLoading">
        <h2 class="panel-title"><span>学习计划</span><Route :size="19" /></h2>
        <div class="form-grid three-fields">
          <el-input v-model="planForm.targetRole" placeholder="目标岗位" />
          <label class="number-field">
            <span>每周投入（小时）</span>
            <el-input-number v-model="planForm.weeklyHours" :min="2" :max="40" controls-position="right" />
          </label>
          <label class="number-field">
            <span>计划周期（周）</span>
            <el-input-number v-model="planForm.durationWeeks" :min="1" :max="24" controls-position="right" />
          </label>
        </div>
        <div class="actions"><el-button type="primary" :loading="planActionLoading" @click="createPlan">生成学习计划</el-button></div>
      </section>
      <section v-if="plans.length" class="grid two">
        <article class="panel module-panel">
          <h2 class="panel-title"><span>计划版本</span><RefreshCw :size="19" /></h2>
          <el-select v-model="selectedPlanId" placeholder="选择学习计划" @change="loadPlanVersions">
            <el-option v-for="plan in plans" :key="plan.planId" :label="`${plan.targetRole} · V${plan.version} · ${plan.status === 'ACTIVE' ? '当前' : '只读'}`" :value="plan.planId" />
          </el-select>
          <div v-if="selectedPlan" class="plan-meta">
            <strong>{{ selectedPlan.targetRole }}</strong>
            <span>V{{ selectedPlan.version }} · {{ selectedPlan.weeklyHours }} 小时/周 · {{ selectedPlan.durationWeeks }} 周</span>
            <div class="tag-row">
              <el-tag :type="selectedPlanIsActive ? 'success' : 'info'">{{ selectedPlanIsActive ? '当前可编辑版本' : '历史版本（只读）' }}</el-tag>
              <el-tag :type="sourceTagType(undefined, selectedPlan.mocked)">{{ sourceTagLabel(undefined, selectedPlan.mocked) }}</el-tag>
              <el-tag v-for="version in planVersions" :key="version.planId" type="info">V{{ version.version }}</el-tag>
            </div>
          </div>
          <el-alert v-if="selectedPlan && !selectedPlanIsActive" title="当前选择的是历史版本，任务和重新规划均为只读。" type="info" :closable="false" show-icon />
          <el-input v-model="planForm.replanReason" :disabled="!selectedPlanIsActive" type="textarea" :rows="3" placeholder="计划变化或复盘原因" />
          <el-select v-model="selectedCompletedSessionId" :disabled="!selectedPlanIsActive" clearable placeholder="选择同目标的已完成面试会话（可选）">
            <el-option
              v-for="session in compatibleCompletedSessions"
              :key="session.sessionId"
              :label="`${session.targetRole} · ${session.completedAt || session.updatedAt}`"
              :value="session.sessionId"
            />
          </el-select>
          <div class="actions"><el-button :disabled="!selectedPlanIsActive" :loading="planActionLoading" @click="replan">重新规划</el-button></div>
        </article>
        <article class="panel module-panel">
          <h2 class="panel-title"><span>任务进度</span><CheckCircle2 :size="19" /></h2>
          <el-empty v-if="!selectedPlan" description="请选择学习计划" />
          <div v-else class="task-list">
            <div v-for="task in selectedPlan.tasks" :key="task.taskId" class="task-row">
              <div>
                <strong>第 {{ task.week }} 周 · {{ task.title }}</strong>
                <p>{{ task.description }}</p>
                <small v-if="task.stage">阶段：{{ learningStageLabel(task.stage) }}</small>
                <small v-if="task.skillGap">技能缺口：{{ task.skillGap }}</small>
                <small v-if="task.acceptanceCriteria">验收：{{ task.acceptanceCriteria }}</small>
                <small v-if="task.practiceDeliverable">练习交付物：{{ task.practiceDeliverable }}</small>
              </div>
              <span>{{ task.estimatedHours }} 小时</span>
              <el-select :disabled="!selectedPlanIsActive" :model-value="task.status" @update:model-value="saveTask(task.taskId, String($event))">
                <el-option label="待开始" value="PENDING" />
                <el-option label="进行中" value="IN_PROGRESS" />
                <el-option label="已完成" value="COMPLETED" />
                <el-option label="已跳过" value="SKIPPED" />
              </el-select>
              <el-input v-model="taskFeedback[task.taskId]" :disabled="!selectedPlanIsActive" placeholder="复盘备注" @change="saveTask(task.taskId, task.status)" />
            </div>
          </div>
        </article>
      </section>
      <el-empty v-else description="尚未生成学习计划" />
    </template>

    <template v-else-if="activeModule === 'interview'">
      <section class="panel module-panel" v-loading="interviewLoading">
        <h2 class="panel-title"><span>模拟面试会话</span><Bot :size="19" /></h2>
        <div class="actions">
          <el-input-number v-model="interviewQuestionCount" :min="1" :max="8" controls-position="right" aria-label="面试题数" />
          <el-button type="primary" :loading="interviewActionLoading" @click="startInterview">开始模拟面试</el-button>
          <el-button @click="router.push({ path: '/student/interview', query: { tab: 'history' } })">会话历史</el-button>
          <el-button @click="router.push('/student/interview')">当前会话</el-button>
        </div>
      </section>
      <section v-if="interviewHistoryOpen" class="panel module-panel">
        <h2 class="panel-title"><span>面试记录</span><RefreshCw :size="19" /></h2>
        <el-empty v-if="!interviewSessions.length" description="暂无模拟面试记录" />
        <div v-else class="history-list">
          <button v-for="session in interviewSessions" :key="session.sessionId" class="history-row selectable" @click="selectedSessionId = session.sessionId; selectSession()">
            <strong>{{ session.targetRole }}</strong><span>{{ session.status }} · {{ session.answers.length }}/{{ session.questions.length }} 题</span>
            <div class="tag-row"><el-tag :type="sourceTagType(undefined, session.mocked)">{{ sourceTagLabel(undefined, session.mocked) }}</el-tag></div>
          </button>
        </div>
      </section>
      <section v-else-if="selectedSession && activeQuestion" class="grid two">
        <article class="panel module-panel">
          <h2 class="panel-title"><span>第 {{ activeQuestionIndex + 1 }} 题 / {{ selectedSession.questions.length }}</span><BrainCircuit :size="19" /></h2>
          <div class="tag-row">
            <el-tag type="info">{{ activeQuestion.category || '综合' }}</el-tag>
            <el-tag>{{ activeQuestion.difficulty || '普通' }}</el-tag>
            <el-tag :type="sourceTagType(activeQuestion.source || activeQuestion.generationSource, selectedSession.mocked)">{{ sourceTagLabel(activeQuestion.source || activeQuestion.generationSource, selectedSession.mocked) }}</el-tag>
          </div>
          <p class="question-text">{{ activeQuestion.question }}</p>
          <ul v-if="activeQuestion.referencePoints?.length" class="plain-list"><li v-for="point in activeQuestion.referencePoints" :key="point">{{ point }}</li></ul>
          <div class="actions question-nav">
            <el-button :disabled="activeQuestionIndex === 0" @click="activeQuestionIndex -= 1">上一题</el-button>
            <el-button :disabled="activeQuestionIndex >= selectedSession.questions.length - 1" @click="activeQuestionIndex += 1">下一题</el-button>
          </div>
        </article>
        <article class="panel module-panel">
          <h2 class="panel-title"><span>我的回答</span><FileText :size="19" /></h2>
          <el-input v-model="currentAnswer" type="textarea" :rows="10" placeholder="输入回答，保存后可在会话中恢复" />
          <div class="actions">
            <el-button type="primary" :loading="interviewActionLoading" @click="saveCurrentAnswer">保存回答</el-button>
            <el-button :loading="interviewActionLoading" @click="finishInterview">完成并生成报告</el-button>
          </div>
        </article>
      </section>
      <el-empty v-else-if="!interviewHistoryOpen" description="开始一次模拟面试后可在此继续作答" />
      <section v-if="sessionReport" class="panel module-panel interview-report">
        <h2 class="panel-title"><span>面试报告</span><CheckCircle2 :size="19" /></h2>
        <strong class="match-score">{{ sessionReport.overallScore }} 分</strong>
        <div class="tag-row"><el-tag :type="sourceTagType(undefined, sessionReport.mocked)">{{ sourceTagLabel(undefined, sessionReport.mocked) }}</el-tag></div>
        <div class="grid three report-columns">
          <div><strong>优势</strong><ul class="plain-list"><li v-for="item in sessionReport.strengths" :key="item">{{ item }}</li></ul></div>
          <div><strong>待改进</strong><ul class="plain-list"><li v-for="item in sessionReport.gaps" :key="item">{{ item }}</li></ul></div>
          <div><strong>建议</strong><ul class="plain-list"><li v-for="item in sessionReport.recommendations" :key="item">{{ item }}</li></ul></div>
        </div>
      </section>
    </template>

    <template v-else-if="activeModule === 'knowledge'">
      <section class="panel module-panel">
        <h2 class="panel-title"><span>RAG 知识库问答</span><Library :size="19" /></h2>
        <div class="knowledge-search">
          <el-input v-model="knowledgeQuery" placeholder="搜索 Java、Redis、面试或简历证据" @keyup.enter="runKnowledgeSearch" />
          <el-button type="primary" :loading="knowledgeLoading" @click="runKnowledgeSearch"><Search :size="17" />检索</el-button>
        </div>
        <div v-if="knowledgeAnswer" class="rag-answer">
          <header><strong>AI 引用回答</strong><el-tag :type="knowledgeAnswer.mocked ? 'warning' : 'success'">{{ knowledgeAnswer.provider }}</el-tag></header>
          <div class="knowledge-answer" v-html="renderMarkdown(knowledgeAnswer.answer)" />
          <div v-if="knowledgeAnswer.citations.length" class="citation-list">
            <details v-for="(citation, index) in knowledgeAnswer.citations" :key="citation.chunkId" class="citation-row">
              <summary>[{{ index + 1 }}] {{ citation.title }}</summary>
              <p>{{ citation.source }} · {{ citation.score }} 分</p>
              <div v-html="renderMarkdown(citation.snippet)" />
            </details>
          </div>
        </div>
        <el-empty v-else description="输入关键词后检索知识库" />
      </section>
    </template>
  </section>
</template>

<style scoped>
.target-role-control{display:grid;gap:4px;width:min(320px,100%)}.target-role-control span,.number-field span{color:#667085;font-size:13px}.resume-toolbar,.match-toolbar,.knowledge-search,.actions,.result-header{display:flex;flex-wrap:wrap;gap:10px;align-items:center}.resume-toolbar :deep(.el-select),.match-toolbar :deep(.el-select){min-width:220px;flex:1}.upload-control{display:inline-flex;align-items:center;gap:6px;min-height:32px;padding:0 12px;border:1px solid #d0d5dd;border-radius:6px;color:#344054;cursor:pointer}.upload-control input{display:none}.resume-summary-grid{margin-top:16px}.item-card p,.history-row p,.selected-job-card p,.plan-meta p{margin:6px 0 0;color:#475467}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.three-fields{grid-template-columns:minmax(0,1fr) 148px 148px}.number-field{display:grid;gap:4px;min-width:0}.number-field :deep(.el-input-number){width:100%}.actions{margin-top:14px}.history-list,.task-list{display:grid;gap:10px;margin-top:14px}.history-row{display:grid;gap:4px;padding:10px 0;border-bottom:1px solid #eaecf0;color:#344054;background:transparent;text-align:left}.history-row span{color:#667085;font-size:13px}.history-row.selectable{width:100%;border:0;border-bottom:1px solid #eaecf0;cursor:pointer}.history-list.compact{margin-top:0}.rewrite-result,.plan-meta,.rag-answer{display:grid;gap:10px;margin-top:16px;padding:14px;border:1px solid #dbe5ef;border-radius:6px;background:#f8fafc}.match-score{font-size:30px;color:#0f766e}.task-row{display:grid;grid-template-columns:minmax(0,1fr) 70px minmax(120px,150px);gap:10px;align-items:center;padding:10px 0;border-bottom:1px solid #eaecf0;min-width:0}.task-row p{margin:4px 0 0;color:#667085;font-size:13px}.task-row small{display:block;margin-top:3px;overflow-wrap:anywhere}.task-row>:last-child{grid-column:1/-1;min-width:0}.task-row :deep(.el-select),.task-row :deep(.el-input){min-width:0;width:100%}.question-text{font-size:16px;line-height:1.7}.question-nav{justify-content:space-between}.report-columns{margin-top:16px}.citation-list{display:grid;gap:10px}.citation-row{display:flex;gap:10px;padding-top:10px;border-top:1px solid #dbe5ef}.citation-row p{margin:4px 0 0;color:#475467}@media (max-width:760px){.form-grid,.three-fields,.task-row{grid-template-columns:1fr}.task-row>:last-child{grid-column:auto}.resume-toolbar :deep(.el-select),.match-toolbar :deep(.el-select){width:100%;min-width:0}}
</style>
