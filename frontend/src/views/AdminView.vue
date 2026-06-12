<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import {
  AlertTriangle,
  BarChart3,
  Bot,
  BriefcaseBusiness,
  Building2,
  CheckCircle2,
  ClipboardCheck,
  Database,
  GraduationCap,
  HardDrive,
  LockKeyhole,
  RefreshCw,
  Rocket,
  Search,
  Send,
  ServerCog,
  ShieldCheck,
  Timer
} from 'lucide-vue-next'
import {
  changeAccountPassword,
  createAccount,
  exportAdminAudit,
  getAdminAuditOverview,
  getAiObservabilitySummary,
  getDeploymentTopology,
  getDeploymentGuide,
  getDashboard,
  getCurrentPermissions,
  getKnowledgeBaseStats,
  getKnowledgeVectorStatus,
  getSystemStatus,
  createKnowledgeDocument,
  listAiCallRecords,
  listAccounts,
  listKnowledgeIngestions,
  listKnowledgeDocuments,
  searchAiKnowledge,
  uploadKnowledgeFile,
  updateAccountStatus,
  type AccountStatus,
  type AdminAuditEntityType,
  type AdminAuditExportResult,
  type AdminAuditOverview,
  type AdminAuditRecord,
  type AiCallRecord,
  type AiObservabilitySummary,
  type AiSearchResponse,
  type AccountSummary,
  type CurrentPermissions,
  type DashboardStats,
  type DeploymentGuide,
  type DeploymentTopology,
  type DeliveryStatus,
  type KnowledgeBaseStats,
  type KnowledgeDocument,
  type KnowledgeIngestionJob,
  type KnowledgeIngestionStatus,
  type KnowledgeVectorStatus,
  type Role,
  type SystemServiceStatus,
  type SystemStatus
} from '../api/client'

const route = useRoute()
const stats = ref<DashboardStats>()
const systemStatus = ref<SystemStatus>()
const deploymentTopology = ref<DeploymentTopology>()
const deploymentGuide = ref<DeploymentGuide>()
const aiObservabilitySummary = ref<AiObservabilitySummary>()
const aiCallRecords = ref<AiCallRecord[]>([])
const aiSearchResponse = ref<AiSearchResponse>()
const knowledgeStats = ref<KnowledgeBaseStats>()
const knowledgeDocuments = ref<KnowledgeDocument[]>([])
const knowledgeIngestionJobs = ref<KnowledgeIngestionJob[]>([])
const knowledgeVectorStatus = ref<KnowledgeVectorStatus>()
const auditOverview = ref<AdminAuditOverview>()
const auditExport = ref<AdminAuditExportResult>()
const accounts = ref<AccountSummary[]>([])
const currentPermissions = ref<CurrentPermissions>()
const systemStatusLoading = ref(false)
const aiLoading = ref(false)
const aiSearchLoading = ref(false)
const knowledgeLoading = ref(false)
const knowledgeCreating = ref(false)
const knowledgeUploadLoading = ref(false)
const knowledgeIngestionLoading = ref(false)
const auditLoading = ref(false)
const auditExportLoading = ref(false)
const accountsLoading = ref(false)
const aiCallFilters = reactive({
  provider: '',
  success: '',
  limit: 20
})
const aiSearchForm = reactive({
  query: 'Java backend',
  role: 'ADMIN',
  limit: 5
})
const knowledgeFilters = reactive({
  keyword: '',
  role: 'ADMIN',
  limit: 20
})
const knowledgeIngestionFilters = reactive({
  status: '' as KnowledgeIngestionStatus | '',
  limit: 10
})
const knowledgeFileInput = ref<HTMLInputElement>()
const knowledgeFile = ref<File | null>(null)
const lastKnowledgeUploadJob = ref<KnowledgeIngestionJob>()
const knowledgeFileForm = reactive({
  title: '',
  category: 'rag',
  source: 'admin-upload',
  tags: '',
  roles: ['ADMIN'] as string[]
})
const knowledgeForm = reactive({
  title: '',
  category: 'guidance',
  source: 'admin-console',
  tags: '',
  roles: ['ADMIN'] as string[],
  content: ''
})
const auditFilters = reactive({
  keyword: '',
  entityType: '' as AdminAuditEntityType | '',
  studentId: '',
  companyId: '',
  jobId: '',
  limit: 20
})
const accountFilters = reactive({
  role: '',
  status: '',
  keyword: ''
})
const accountForm = reactive({
  username: 'student02',
  password: '123456',
  displayName: 'Student 02',
  role: 'STUDENT' as Role,
  status: 'ACTIVE' as AccountStatus
})
const passwordForm = reactive({
  accountId: '',
  newPassword: '123456'
})
const statusLabels: Record<DeliveryStatus, string> = {
  SUBMITTED: '已投递',
  VIEWED: '已查看',
  INTERVIEW: '面试中',
  OFFER: '已录用',
  REJECTED: '未通过'
}
const statusTypes: Record<DeliveryStatus, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  SUBMITTED: 'info',
  VIEWED: 'primary',
  INTERVIEW: 'warning',
  OFFER: 'success',
  REJECTED: 'danger'
}
const auditEntityLabels: Record<AdminAuditEntityType, string> = {
  STUDENT: '学生',
  JOB: '岗位',
  DELIVERY: '投递',
  AI_SCREENING: 'AI 初筛',
  AI_INTERVIEW: 'AI 面试'
}
const statusRows = computed(() => {
  const counts = stats.value?.deliveryStatusCounts
  if (!counts) {
    return []
  }
  return (Object.keys(statusLabels) as DeliveryStatus[]).map((status) => ({
    status,
    label: statusLabels[status],
    count: counts[status] || 0,
    type: statusTypes[status]
  }))
})
const trendRows = computed(() => stats.value?.weeklyDeliveryTrend || [])
const skillDemandRows = computed(() => stats.value?.skillDemandTop || [])
const funnelRows = computed(() => stats.value?.conversionFunnel || [])
const dashboardRiskAlerts = computed(() => stats.value?.riskAlerts || [])
const maxTrendDelivery = computed(() => Math.max(1, ...trendRows.value.map((row) => row.deliveryCount)))
const maxSkillDemandScore = computed(() => Math.max(1, ...skillDemandRows.value.map((row) => row.demandScore)))
const activeModule = computed(() => typeof route.params.module === 'string' ? route.params.module : 'overview')
const serviceRows = computed(() => systemStatus.value?.services || [])
const persistenceRows = computed(() => systemStatus.value?.persistence || [])
const infrastructureRows = computed(() => systemStatus.value?.infrastructure || [])
const warningRows = computed(() => systemStatus.value?.warnings || [])
const topologyNodes = computed(() => deploymentTopology.value?.nodes || [])
const topologyWarnings = computed(() => deploymentTopology.value?.warnings || [])
const deploymentSteps = computed(() => deploymentGuide.value?.steps || [])
const acceptanceChecks = computed(() => deploymentGuide.value?.acceptanceChecks || [])
const deploymentWarnings = computed(() => deploymentGuide.value?.warnings || [])
const permissionTags = computed(() => currentPermissions.value?.permissions || [])
const aiProviderRows = computed(() => aiCallBreakdown('provider'))
const aiTaskRows = computed(() => aiCallBreakdown('operation'))
const aiSearchResults = computed(() => aiSearchResponse.value?.results || [])
const knowledgeTopCategories = computed(() => topCountRows(knowledgeStats.value?.categoryCounts))
const knowledgeTopSources = computed(() => topCountRows(knowledgeStats.value?.sourceCounts))
const knowledgeDocumentRows = computed(() => knowledgeDocuments.value)
const knowledgeIngestionRows = computed(() => knowledgeIngestionJobs.value)
const vectorIndexStatus = computed(() => knowledgeVectorStatus.value?.indexStatus || knowledgeVectorStatus.value?.status || 'UNKNOWN')
const vectorWarnings = computed(() => knowledgeVectorStatus.value?.warnings || [])
const auditMetrics = computed(() => auditOverview.value?.metrics || [])
const auditRows = computed(() => auditOverview.value?.records || [])
const auditWarnings = computed(() => auditOverview.value?.warnings || [])

function aiCallBreakdown(field: 'provider' | 'operation') {
  const counts = new Map<string, number>()
  for (const record of aiCallRecords.value) {
    const key = record[field] || 'unknown'
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  return Array.from(counts.entries()).map(([name, count]) => ({ name, count }))
}

function topCountRows(counts?: Record<string, number>) {
  return Object.entries(counts || {})
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
    .slice(0, 5)
    .map(([name, count]) => ({ name, count }))
}

onMounted(async () => {
  const [
    dashboard,
    status,
    topology,
    guide,
    accountList,
    permissions,
    aiSummary,
    aiCalls,
    aiSearch,
    kbStats,
    knowledgeDocs,
    ingestionJobs,
    vectorStatus,
    audit
  ] = await Promise.all([
    getDashboard(),
    getSystemStatus(),
    getDeploymentTopology(),
    getDeploymentGuide(),
    listAccounts(),
    getCurrentPermissions(),
    getAiObservabilitySummary(),
    listAiCallRecords(),
    searchAiKnowledge(aiSearchForm),
    getKnowledgeBaseStats(),
    listKnowledgeDocuments(knowledgeFilters.keyword, knowledgeFilters.role, knowledgeFilters.limit),
    listKnowledgeIngestions(knowledgeIngestionFilters),
    getKnowledgeVectorStatus(),
    getAdminAuditOverview(auditFilters)
  ])
  stats.value = dashboard
  systemStatus.value = status
  deploymentTopology.value = topology
  deploymentGuide.value = guide
  accounts.value = accountList
  currentPermissions.value = permissions
  aiObservabilitySummary.value = aiSummary
  aiCallRecords.value = aiCalls
  aiSearchResponse.value = aiSearch
  knowledgeStats.value = kbStats
  knowledgeDocuments.value = knowledgeDocs
  knowledgeIngestionJobs.value = ingestionJobs
  knowledgeVectorStatus.value = vectorStatus
  auditOverview.value = audit
})

function statusPercent(count: number) {
  if (!stats.value?.deliveryCount) {
    return 0
  }
  return Math.round((count / stats.value.deliveryCount) * 100)
}

async function refreshSystemStatus() {
  systemStatusLoading.value = true
  try {
    const [status, topology] = await Promise.all([getSystemStatus(), getDeploymentTopology()])
    systemStatus.value = status
    deploymentTopology.value = topology
  } finally {
    systemStatusLoading.value = false
  }
}

async function refreshAccounts() {
  accountsLoading.value = true
  try {
    accounts.value = await listAccounts({
      role: accountFilters.role as Role || undefined,
      status: accountFilters.status as AccountStatus || undefined,
      keyword: accountFilters.keyword
    })
    currentPermissions.value = await getCurrentPermissions()
  } finally {
    accountsLoading.value = false
  }
}

async function refreshAiObservability() {
  aiLoading.value = true
  try {
    const success = aiCallFilters.success === '' ? undefined : aiCallFilters.success === 'true'
    const [summary, calls] = await Promise.all([
      getAiObservabilitySummary(),
      listAiCallRecords({
        provider: aiCallFilters.provider,
        success,
        limit: aiCallFilters.limit
      })
    ])
    aiObservabilitySummary.value = summary
    aiCallRecords.value = calls
  } finally {
    aiLoading.value = false
  }
}

async function runAiSearch() {
  const query = aiSearchForm.query.trim()
  if (!query) {
    ElMessage.warning('Please enter a search query')
    return
  }
  aiSearchLoading.value = true
  try {
    aiSearchResponse.value = await searchAiKnowledge({
      query,
      role: aiSearchForm.role,
      limit: aiSearchForm.limit
    })
  } finally {
    aiSearchLoading.value = false
  }
}

async function refreshKnowledgeDocuments() {
  knowledgeLoading.value = true
  knowledgeIngestionLoading.value = true
  try {
    const [stats, documents, ingestionJobs, vectorStatus] = await Promise.all([
      getKnowledgeBaseStats(),
      listKnowledgeDocuments(
        knowledgeFilters.keyword,
        knowledgeFilters.role,
        knowledgeFilters.limit
      ),
      listKnowledgeIngestions(knowledgeIngestionFilters),
      getKnowledgeVectorStatus()
    ])
    knowledgeStats.value = stats
    knowledgeDocuments.value = documents
    knowledgeIngestionJobs.value = ingestionJobs
    knowledgeVectorStatus.value = vectorStatus
  } finally {
    knowledgeLoading.value = false
    knowledgeIngestionLoading.value = false
  }
}

async function refreshKnowledgeIngestions() {
  knowledgeIngestionLoading.value = true
  try {
    const [jobs, vectorStatus] = await Promise.all([
      listKnowledgeIngestions(knowledgeIngestionFilters),
      getKnowledgeVectorStatus()
    ])
    knowledgeIngestionJobs.value = jobs
    knowledgeVectorStatus.value = vectorStatus
  } finally {
    knowledgeIngestionLoading.value = false
  }
}

function handleKnowledgeFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] || null
  knowledgeFile.value = file
  if (file && !knowledgeFileForm.title.trim()) {
    knowledgeFileForm.title = file.name.replace(/\.[^.]+$/, '')
  }
}

function parseKnowledgeTags(value: string) {
  return value
    .split(/[,;\n]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

async function submitKnowledgeFile() {
  const file = knowledgeFile.value
  const roles = knowledgeFileForm.roles.map((role) => role.trim()).filter(Boolean)
  if (!file) {
    ElMessage.warning('请先选择要上传到 RAG 知识库的文件，支持 TXT、MD、PDF、DOC、DOCX')
    return
  }
  if (!roles.length) {
    ElMessage.warning('请至少选择一个可读取该文档的角色')
    return
  }

  knowledgeUploadLoading.value = true
  try {
    const job = await uploadKnowledgeFile({
      file,
      title: knowledgeFileForm.title.trim() || file.name,
      category: knowledgeFileForm.category.trim() || 'rag',
      source: knowledgeFileForm.source.trim() || 'admin-upload',
      tags: parseKnowledgeTags(knowledgeFileForm.tags),
      roles
    })
    knowledgeIngestionJobs.value = [
      job,
      ...knowledgeIngestionJobs.value.filter((item) => item.jobId !== job.jobId)
    ].slice(0, knowledgeIngestionFilters.limit)
    lastKnowledgeUploadJob.value = job
    const [stats, documents, vectorStatus] = await Promise.all([
      getKnowledgeBaseStats(),
      listKnowledgeDocuments(knowledgeFilters.keyword, knowledgeFilters.role, knowledgeFilters.limit),
      getKnowledgeVectorStatus()
    ])
    knowledgeStats.value = stats
    knowledgeDocuments.value = documents
    knowledgeVectorStatus.value = vectorStatus
    knowledgeFile.value = null
    knowledgeFileForm.title = ''
    knowledgeFileForm.tags = ''
    if (knowledgeFileInput.value) {
      knowledgeFileInput.value.value = ''
    }
    ElMessage.success(`RAG 导入任务已创建：${job.jobId}（${job.status}）`)
  } finally {
    knowledgeUploadLoading.value = false
  }
}

function knowledgeDocumentMatchesFilters(document: KnowledgeDocument) {
  const role = knowledgeFilters.role.trim()
  const keyword = knowledgeFilters.keyword.trim().toLowerCase()
  const roleMatches = !role || document.roles.includes(role) || document.roles.includes('ALL')
  const keywordSource = [
    document.title,
    document.content,
    document.category,
    document.source,
    document.createdBy,
    ...document.tags
  ].join(' ').toLowerCase()
  return roleMatches && (!keyword || keywordSource.includes(keyword))
}

async function submitKnowledgeDocument() {
  const title = knowledgeForm.title.trim()
  const content = knowledgeForm.content.trim()
  const roles = knowledgeForm.roles.map((role) => role.trim()).filter(Boolean)
  if (!title || !content) {
    ElMessage.warning('Please enter a knowledge document title and content')
    return
  }
  if (!roles.length) {
    ElMessage.warning('Please select at least one readable role')
    return
  }

  knowledgeCreating.value = true
  try {
    const created = await createKnowledgeDocument({
      title,
      content,
      category: knowledgeForm.category.trim() || 'general',
      source: knowledgeForm.source.trim() || 'admin-console',
      tags: parseKnowledgeTags(knowledgeForm.tags),
      roles
    })
    if (knowledgeDocumentMatchesFilters(created)) {
      knowledgeDocuments.value = [
        created,
        ...knowledgeDocuments.value.filter((item) => item.documentId !== created.documentId)
      ].slice(0, knowledgeFilters.limit)
    }
    knowledgeStats.value = await getKnowledgeBaseStats()
    knowledgeForm.title = ''
    knowledgeForm.content = ''
    knowledgeForm.tags = ''
    ElMessage.success('Knowledge document created')
  } finally {
    knowledgeCreating.value = false
  }
}

async function refreshAuditOverview() {
  auditLoading.value = true
  auditExport.value = undefined
  try {
    auditOverview.value = await getAdminAuditOverview(auditFilters)
  } finally {
    auditLoading.value = false
  }
}

async function runAuditExport() {
  auditExportLoading.value = true
  try {
    auditExport.value = await exportAdminAudit(auditFilters)
    ElMessage.success('审计导出任务已准备')
  } finally {
    auditExportLoading.value = false
  }
}

async function submitAccount() {
  const account = await createAccount({
    username: accountForm.username,
    password: accountForm.password,
    displayName: accountForm.displayName,
    role: accountForm.role,
    status: accountForm.status
  })
  accounts.value = [account, ...accounts.value.filter((item) => item.accountId !== account.accountId)]
  passwordForm.accountId = account.accountId
  ElMessage.success('账号已创建')
}

async function setAccountStatus(account: AccountSummary, status: AccountStatus) {
  const updated = await updateAccountStatus(account.accountId, status)
  accounts.value = accounts.value.map((item) => item.accountId === updated.accountId ? updated : item)
  ElMessage.success('账号状态已更新')
}

async function resetAccountPassword() {
  if (!passwordForm.accountId.trim()) {
    ElMessage.warning('请选择账号')
    return
  }
  await changeAccountPassword({
    accountId: passwordForm.accountId.trim(),
    newPassword: passwordForm.newPassword
  })
  ElMessage.success('密码已重置')
}

function systemTagType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  const normalized = status.toUpperCase()
  if (['UP', 'ONLINE', 'CONFIGURED', 'ENABLED', 'READY', 'COMPLETED', 'CONNECTED'].includes(normalized)) {
    return 'success'
  }
  if (['DOWN', 'FAILED', 'ERROR'].includes(normalized)) {
    return 'danger'
  }
  if (['BUILDING', 'DEMO', 'DISABLED', 'INDEXING', 'OPTIONAL', 'PARSING', 'PENDING', 'RUNNING', 'UPLOADED', 'UNKNOWN'].includes(normalized)) {
    return 'warning'
  }
  return 'info'
}

function enabledTagType(enabled: boolean): 'success' | 'warning' {
  return enabled ? 'success' : 'warning'
}

function formatDateTime(value?: string) {
  if (!value) {
    return '待生成'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function servicePort(row: SystemServiceStatus) {
  if (row.defaultPort && row.defaultPort !== row.port) {
    return `${row.port} / ${row.defaultPort}`
  }
  return String(row.port)
}

function ingestionStatusType(status: KnowledgeIngestionStatus | string): 'success' | 'warning' | 'info' | 'danger' {
  const normalized = status.toUpperCase()
  if (['COMPLETED', 'SUCCESS', 'READY'].includes(normalized)) {
    return 'success'
  }
  if (['DUPLICATE'].includes(normalized)) {
    return 'info'
  }
  if (['FAILED', 'ERROR'].includes(normalized)) {
    return 'danger'
  }
  if (['INDEXING', 'PARSING', 'PROCESSING', 'RUNNING', 'BUILDING'].includes(normalized)) {
    return 'warning'
  }
  return 'info'
}

function stepTagType(nodeId: string): 'success' | 'warning' | 'info' {
  if (nodeId === 'vm3') {
    return 'success'
  }
  if (['acceptance', 'all'].includes(nodeId)) {
    return 'warning'
  }
  return 'info'
}

function auditEntityLabel(value: AdminAuditEntityType) {
  return auditEntityLabels[value] || value
}

function auditRiskType(row: AdminAuditRecord): 'success' | 'warning' | 'danger' {
  if (row.riskLevel === 'HIGH') {
    return 'danger'
  }
  if (row.riskLevel === 'MEDIUM') {
    return 'warning'
  }
  return 'success'
}
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1 class="page-title">学校就业看板</h1>
        <p class="page-subtitle">就业办 · 2026 届校园招聘</p>
      </div>
    </header>

    <div v-if="activeModule === 'overview'" class="module-stack dashboard-overview">
      <div class="grid three">
        <div class="metric">
          <GraduationCap :size="22" />
          <span>学生数</span>
          <strong>{{ stats?.studentCount }}</strong>
        </div>
        <div class="metric">
          <Building2 :size="22" />
          <span>企业数</span>
          <strong>{{ stats?.companyCount }}</strong>
        </div>
        <div class="metric">
          <BriefcaseBusiness :size="22" />
          <span>岗位数</span>
          <strong>{{ stats?.jobCount }}</strong>
        </div>
        <div class="metric">
          <Send :size="22" />
          <span>投递数</span>
          <strong>{{ stats?.deliveryCount }}</strong>
        </div>
        <div class="metric">
          <Timer :size="22" />
          <span>待处理投递</span>
          <strong>{{ stats?.pendingDeliveryCount }}</strong>
        </div>
        <div class="metric">
          <BarChart3 :size="22" />
          <span>平均匹配度</span>
          <strong>{{ stats?.averageMatchScore }}</strong>
        </div>
        <div class="metric">
          <BarChart3 :size="22" />
          <span>面试转化率</span>
          <strong>{{ stats?.interviewRate || 0 }}%</strong>
        </div>
        <div class="metric">
          <CheckCircle2 :size="22" />
          <span>Offer 转化率</span>
          <strong>{{ stats?.offerRate || 0 }}%</strong>
        </div>
        <div class="metric">
          <GraduationCap :size="22" />
          <span>活跃学生</span>
          <strong>{{ stats?.activeStudentCount || 0 }}</strong>
        </div>
        <div class="metric">
          <ShieldCheck :size="22" />
          <span>高潜候选人</span>
          <strong>{{ stats?.highPotentialCandidateCount || 0 }}</strong>
        </div>
      </div>

      <section class="panel module-panel analytics-panel">
        <h2 class="panel-title">
          周投递趋势
          <BarChart3 :size="19" />
        </h2>
        <div v-if="trendRows.length" class="trend-chart">
          <article v-for="row in trendRows" :key="row.label" class="trend-row">
            <span class="trend-label">{{ row.label }}</span>
            <div class="trend-bars">
              <div class="trend-track">
                <span class="trend-bar delivery" :style="{ width: `${Math.max(8, Math.round(row.deliveryCount / maxTrendDelivery * 100))}%` }" />
              </div>
              <div class="trend-track compact">
                <span class="trend-bar interview" :style="{ width: `${Math.max(8, Math.round(row.interviewCount / maxTrendDelivery * 100))}%` }" />
              </div>
              <div class="trend-track compact">
                <span class="trend-bar offer" :style="{ width: `${Math.max(8, Math.round(row.offerCount / maxTrendDelivery * 100))}%` }" />
              </div>
            </div>
            <div class="trend-values">
              <strong>{{ row.deliveryCount }}</strong>
              <span>面试 {{ row.interviewCount }}</span>
              <span>Offer {{ row.offerCount }}</span>
            </div>
          </article>
        </div>
        <el-empty v-else class="compact-empty" description="暂无趋势数据" />
      </section>

      <div class="analytics-grid">
        <section class="panel module-panel analytics-panel">
          <h2 class="panel-title">
            转化漏斗
            <Send :size="19" />
          </h2>
          <div v-if="funnelRows.length" class="funnel-list">
            <article v-for="row in funnelRows" :key="row.stage" class="funnel-row">
              <div>
                <strong>{{ row.label }}</strong>
                <span>{{ row.count }} 人次</span>
              </div>
              <el-progress :percentage="row.conversionRate" :stroke-width="10" />
            </article>
          </div>
          <el-empty v-else class="compact-empty" description="暂无漏斗数据" />
        </section>

        <section class="panel module-panel analytics-panel">
          <h2 class="panel-title">
            技能需求 Top
            <BriefcaseBusiness :size="19" />
          </h2>
          <div v-if="skillDemandRows.length" class="skill-demand-list">
            <article v-for="row in skillDemandRows" :key="row.skill" class="skill-demand-row">
              <div class="skill-demand-main">
                <strong>{{ row.skill }}</strong>
                <span>{{ row.jobCount }} 个岗位 / {{ row.matchedStudentCount }} 名匹配学生</span>
              </div>
              <div class="skill-demand-score">
                <span :style="{ width: `${Math.round(row.demandScore / maxSkillDemandScore * 100)}%` }" />
              </div>
              <strong class="skill-demand-number">{{ row.demandScore }}</strong>
            </article>
          </div>
          <el-empty v-else class="compact-empty" description="暂无技能需求数据" />
        </section>
      </div>

      <section class="panel module-panel analytics-panel">
        <h2 class="panel-title">
          风险告警
          <AlertTriangle :size="19" />
        </h2>
        <div v-if="dashboardRiskAlerts.length" class="warning-list">
          <el-alert
            v-for="alert in dashboardRiskAlerts"
            :key="alert"
            :title="alert"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
        <el-empty v-else class="compact-empty" description="暂无风险告警" />
      </section>
    </div>

    <section v-if="activeModule === 'status'" class="panel module-panel">
      <h2 class="panel-title">投递状态分布</h2>
      <el-table :data="statusRows" style="width: 100%">
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="row.type">{{ row.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="数量" width="120" />
        <el-table-column label="占比">
          <template #default="{ row }">
            <el-progress :percentage="statusPercent(row.count)" :stroke-width="10" />
          </template>
        </el-table-column>
      </el-table>
    </section>

    <div v-if="activeModule === 'accounts'" class="module-stack">
      <section class="panel module-panel">
        <h2 class="panel-title">
          当前权限
          <ShieldCheck :size="19" />
        </h2>
        <div class="permission-summary">
          <div>
            <strong>{{ currentPermissions?.userId || 'A001' }}</strong>
            <span>{{ currentPermissions?.role || 'ADMIN' }}</span>
          </div>
          <div class="permission-tags">
            <span v-for="permission in permissionTags" :key="permission" class="permission-chip">
              {{ permission }}
            </span>
          </div>
        </div>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          用户账号
          <span class="panel-title-actions">
            <el-button circle size="small" :loading="accountsLoading" @click="refreshAccounts">
              <RefreshCw :size="15" />
            </el-button>
            <ShieldCheck :size="19" />
          </span>
        </h2>
        <div class="account-toolbar">
          <el-select v-model="accountFilters.role" clearable placeholder="角色" style="width: 100%">
            <el-option label="STUDENT" value="STUDENT" />
            <el-option label="COMPANY" value="COMPANY" />
            <el-option label="ADMIN" value="ADMIN" />
          </el-select>
          <el-select v-model="accountFilters.status" clearable placeholder="状态" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DISABLED" value="DISABLED" />
            <el-option label="LOCKED" value="LOCKED" />
          </el-select>
          <el-input v-model="accountFilters.keyword" clearable placeholder="账号 / 姓名 / ID" />
          <el-button :loading="accountsLoading" @click="refreshAccounts">筛选</el-button>
        </div>

        <el-table v-loading="accountsLoading" class="account-table" :data="accounts" style="width: 100%">
          <el-table-column prop="accountId" label="ID" min-width="86" />
          <el-table-column prop="username" label="账号" min-width="120" />
          <el-table-column prop="displayName" label="名称" min-width="140" />
          <el-table-column label="角色" width="104">
            <template #default="{ row }">
              <span class="account-badge role">{{ row.role }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <span class="account-badge" :class="row.status.toLowerCase()">{{ row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column label="权限" min-width="260">
            <template #default="{ row }">
              <div class="permission-tags compact">
                <span v-for="permission in row.permissions" :key="permission" class="permission-chip compact">
                  {{ permission }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="260">
            <template #default="{ row }">
              <div class="account-actions">
                <el-button size="small" :type="row.status === 'ACTIVE' ? 'success' : 'default'" @click="setAccountStatus(row, 'ACTIVE')">启用</el-button>
                <el-button size="small" :type="row.status === 'DISABLED' ? 'warning' : 'default'" @click="setAccountStatus(row, 'DISABLED')">禁用</el-button>
                <el-button size="small" :type="row.status === 'LOCKED' ? 'danger' : 'default'" @click="setAccountStatus(row, 'LOCKED')">锁定</el-button>
                <el-button size="small" @click="passwordForm.accountId = row.accountId">重置密码</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <div class="account-grid">
        <section class="panel module-panel">
          <h2 class="panel-title">
            创建账号
            <ShieldCheck :size="19" />
          </h2>
          <el-form label-position="top">
            <div class="grid two">
              <el-form-item label="账号">
                <el-input v-model="accountForm.username" />
              </el-form-item>
              <el-form-item label="初始密码">
                <el-input v-model="accountForm.password" type="password" show-password />
              </el-form-item>
            </div>
            <el-form-item label="显示名称">
              <el-input v-model="accountForm.displayName" />
            </el-form-item>
            <div class="grid two">
              <el-form-item label="角色">
                <el-select v-model="accountForm.role">
                  <el-option label="STUDENT" value="STUDENT" />
                  <el-option label="COMPANY" value="COMPANY" />
                  <el-option label="ADMIN" value="ADMIN" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="accountForm.status">
                  <el-option label="ACTIVE" value="ACTIVE" />
                  <el-option label="DISABLED" value="DISABLED" />
                  <el-option label="LOCKED" value="LOCKED" />
                </el-select>
              </el-form-item>
            </div>
            <el-button type="primary" @click="submitAccount">创建</el-button>
          </el-form>
        </section>

        <section class="panel module-panel">
          <h2 class="panel-title">
            密码重置
            <LockKeyhole :size="19" />
          </h2>
          <el-form label-position="top">
            <el-form-item label="账号 ID">
              <el-input v-model="passwordForm.accountId" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-button type="primary" @click="resetAccountPassword">重置</el-button>
          </el-form>
        </section>
      </div>
    </div>

    <div v-if="activeModule === 'ai'" class="module-stack">
      <div class="grid three">
        <div class="metric ai-metric">
          <Bot :size="22" />
          <span>AI Calls</span>
          <strong>{{ aiObservabilitySummary?.totalCalls || 0 }}</strong>
        </div>
        <div class="metric ai-metric">
          <CheckCircle2 :size="22" />
          <span>Success Rate</span>
          <strong>{{ aiObservabilitySummary?.successRate || 0 }}%</strong>
        </div>
        <div class="metric ai-metric">
          <Timer :size="22" />
          <span>Avg Latency</span>
          <strong>{{ aiObservabilitySummary?.averageLatencyMs || 0 }} ms</strong>
        </div>
      </div>

      <div class="grid three">
        <div class="metric ai-metric">
          <Database :size="22" />
          <span>RAG Documents</span>
          <strong>{{ knowledgeStats?.documentCount || 0 }}</strong>
        </div>
        <div class="metric ai-metric">
          <HardDrive :size="22" />
          <span>RAG Chunks</span>
          <strong>{{ knowledgeStats?.chunkCount || 0 }}</strong>
        </div>
        <div class="metric ai-metric">
          <ShieldCheck :size="22" />
          <span>Knowledge Store</span>
          <strong>{{ knowledgeStats?.persistentStore ? 'MySQL' : 'Memory' }}</strong>
        </div>
        <div class="metric ai-metric">
          <Database :size="22" />
          <span>Vector Index</span>
          <strong>{{ vectorIndexStatus }}</strong>
        </div>
        <div class="metric ai-metric">
          <ServerCog :size="22" />
          <span>Milvus</span>
          <strong>{{ knowledgeVectorStatus?.connected ? 'Connected' : 'Offline' }}</strong>
        </div>
        <div class="metric ai-metric">
          <HardDrive :size="22" />
          <span>Vectors</span>
          <strong>{{ knowledgeVectorStatus?.vectorCount || 0 }}</strong>
        </div>
      </div>

      <section class="panel module-panel">
        <h2 class="panel-title">
          AI Observability
          <span class="panel-title-actions">
            <span class="generated-at">{{ formatDateTime(aiObservabilitySummary?.generatedAt) }}</span>
            <el-button circle size="small" :loading="aiLoading" @click="refreshAiObservability">
              <RefreshCw :size="15" />
            </el-button>
            <Bot :size="19" />
          </span>
        </h2>

        <div class="ai-observability-grid">
          <div class="ai-breakdown">
            <h3>Providers</h3>
            <div v-for="row in aiProviderRows" :key="row.name" class="ai-breakdown-row">
              <span>{{ row.name }}</span>
              <strong>{{ row.count }}</strong>
            </div>
          </div>
          <div class="ai-breakdown">
            <h3>Tasks</h3>
            <div v-for="row in aiTaskRows" :key="row.name" class="ai-breakdown-row">
              <span>{{ row.name }}</span>
              <strong>{{ row.count }}</strong>
            </div>
          </div>
        </div>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          AI Call Records
          <Bot :size="19" />
        </h2>
        <div class="ai-call-toolbar">
          <el-input v-model="aiCallFilters.provider" clearable placeholder="provider" />
          <el-select v-model="aiCallFilters.success" clearable placeholder="success">
            <el-option label="success" value="true" />
            <el-option label="failed" value="false" />
          </el-select>
          <el-select v-model="aiCallFilters.limit" placeholder="limit">
            <el-option label="10" :value="10" />
            <el-option label="20" :value="20" />
            <el-option label="50" :value="50" />
          </el-select>
          <el-button :loading="aiLoading" @click="refreshAiObservability">Apply</el-button>
        </div>

        <el-table v-loading="aiLoading" class="ai-table" :data="aiCallRecords" style="width: 100%">
          <el-table-column prop="callId" label="Call ID" min-width="128" />
          <el-table-column prop="provider" label="Provider" width="116" />
          <el-table-column prop="model" label="Model" min-width="126" />
          <el-table-column prop="operation" label="Task" min-width="156" />
          <el-table-column label="Status" width="104">
            <template #default="{ row }">
              <span class="ai-status-badge" :class="row.mocked ? 'mocked' : row.success ? 'ok' : 'failed'">
                {{ row.mocked ? 'MOCK' : row.success ? 'OK' : 'FAILED' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="Latency" width="104">
            <template #default="{ row }">{{ row.durationMs }} ms</template>
          </el-table-column>
          <el-table-column label="Created" width="128">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="fallbackReason" label="Fallback" min-width="180" />
        </el-table>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          Intelligent Search
          <Search :size="19" />
        </h2>
        <div class="ai-search-toolbar">
          <el-input v-model="aiSearchForm.query" clearable placeholder="Search students, jobs, deliveries" @keyup.enter="runAiSearch" />
          <el-select v-model="aiSearchForm.role" placeholder="role">
            <el-option label="ADMIN" value="ADMIN" />
            <el-option label="STUDENT" value="STUDENT" />
            <el-option label="COMPANY" value="COMPANY" />
          </el-select>
          <el-select v-model="aiSearchForm.limit" placeholder="limit">
            <el-option label="5" :value="5" />
            <el-option label="10" :value="10" />
            <el-option label="20" :value="20" />
          </el-select>
          <el-button type="primary" :loading="aiSearchLoading" @click="runAiSearch">Search</el-button>
        </div>

        <div v-if="aiSearchResults.length" class="ai-search-results">
          <article v-for="result in aiSearchResults" :key="result.id" class="ai-search-result">
            <header>
              <div>
                <strong>{{ result.title }}</strong>
                <span>{{ result.id }} / {{ result.owner }}</span>
              </div>
              <div class="ai-search-score">
                <span class="ai-type-badge">{{ result.type }}</span>
                <strong>{{ result.score }}</strong>
              </div>
            </header>
            <p>{{ result.summary }}</p>
            <div class="ai-highlight-list">
              <span v-for="highlight in result.highlights" :key="highlight">{{ highlight }}</span>
            </div>
          </article>
        </div>
        <el-empty v-else description="No search results yet" />
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          Milvus / Vector Index
          <span class="panel-title-actions">
            <span class="generated-at">{{ formatDateTime(knowledgeVectorStatus?.generatedAt) }}</span>
            <el-button circle size="small" :loading="knowledgeIngestionLoading" @click="refreshKnowledgeIngestions">
              <RefreshCw :size="15" />
            </el-button>
            <Database :size="19" />
          </span>
        </h2>
        <div class="vector-status-grid">
          <div class="vector-status-card">
            <span>Provider</span>
            <strong>{{ knowledgeVectorStatus?.provider || 'unknown' }}</strong>
          </div>
          <div class="vector-status-card">
            <span>Connection</span>
            <el-tag :type="knowledgeVectorStatus?.connected ? 'success' : 'warning'">
              {{ knowledgeVectorStatus?.connected ? 'CONNECTED' : 'OFFLINE' }}
            </el-tag>
          </div>
          <div class="vector-status-card">
            <span>Index Status</span>
            <el-tag :type="systemTagType(vectorIndexStatus)">{{ vectorIndexStatus }}</el-tag>
          </div>
          <div class="vector-status-card">
            <span>Collection</span>
            <strong>{{ knowledgeVectorStatus?.collectionName || '-' }}</strong>
          </div>
          <div class="vector-status-card">
            <span>Index</span>
            <strong>{{ knowledgeVectorStatus?.indexName || '-' }}</strong>
          </div>
          <div class="vector-status-card">
            <span>Metric / Dimension</span>
            <strong>{{ knowledgeVectorStatus?.metricType || '-' }} / {{ knowledgeVectorStatus?.dimension || 0 }}</strong>
          </div>
          <div class="vector-status-card">
            <span>Chunks</span>
            <strong>{{ knowledgeVectorStatus?.chunkCount || 0 }}</strong>
          </div>
          <div class="vector-status-card">
            <span>Last Ingested</span>
            <strong>{{ formatDateTime(knowledgeVectorStatus?.lastIngestedAt || undefined) }}</strong>
          </div>
        </div>
        <div v-if="vectorWarnings.length" class="warning-list vector-warning-list">
          <el-alert
            v-for="warning in vectorWarnings"
            :key="warning"
            :title="warning"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          RAG 知识库文档上传（TXT/MD/PDF/DOC/DOCX）
          <ClipboardCheck :size="19" />
        </h2>
        <p class="knowledge-upload-hint">
          在这里上传文档喂给 RAG 知识库，支持 TXT、MD、PDF、DOC、DOCX。上传后系统会创建导入任务，解析并写入向量索引。
        </p>
        <el-form label-position="top">
          <div class="knowledge-form-grid">
            <el-form-item label="文档标题">
              <el-input v-model="knowledgeFileForm.title" maxlength="160" show-word-limit placeholder="不填时自动使用文件名" />
            </el-form-item>
            <el-form-item label="知识分类">
              <el-input v-model="knowledgeFileForm.category" placeholder="例如：校招政策、面试题库" />
            </el-form-item>
            <el-form-item label="来源标识">
              <el-input v-model="knowledgeFileForm.source" placeholder="例如：admin-upload、career-office" />
            </el-form-item>
            <el-form-item label="可读取角色">
              <el-select v-model="knowledgeFileForm.roles" multiple collapse-tags collapse-tags-tooltip>
                <el-option label="ADMIN" value="ADMIN" />
                <el-option label="STUDENT" value="STUDENT" />
                <el-option label="COMPANY" value="COMPANY" />
                <el-option label="ALL" value="ALL" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="标签">
            <el-input v-model="knowledgeFileForm.tags" placeholder="多个标签用逗号、分号或换行分隔，例如：校招,RAG,手册" />
          </el-form-item>
          <div class="knowledge-upload-row">
            <label class="knowledge-file-picker">
              <span>选择 TXT / MD / PDF / DOC / DOCX 文档</span>
              <input
                ref="knowledgeFileInput"
                class="knowledge-file-input"
                type="file"
                accept=".pdf,.doc,.docx,.txt,.md"
                @change="handleKnowledgeFileChange"
              >
            </label>
            <el-button type="primary" :loading="knowledgeUploadLoading" @click="submitKnowledgeFile">上传到 RAG 知识库</el-button>
          </div>
          <div class="knowledge-upload-meta">
            <span>当前文件：{{ knowledgeFile?.name || '未选择文件' }}</span>
            <span>支持格式：TXT、MD、PDF、DOC、DOCX</span>
          </div>
          <el-alert
            v-if="lastKnowledgeUploadJob"
            class="knowledge-upload-result"
            type="success"
            :closable="false"
            show-icon
            :title="`导入任务已创建：${lastKnowledgeUploadJob.jobId}`"
            :description="`当前状态：${lastKnowledgeUploadJob.status}；可在下方导入任务列表继续刷新查看解析和入库进度。`"
          />
        </el-form>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          Ingestion Jobs
          <span class="panel-title-actions">
            <el-button circle size="small" :loading="knowledgeIngestionLoading" @click="refreshKnowledgeIngestions">
              <RefreshCw :size="15" />
            </el-button>
            <Timer :size="19" />
          </span>
        </h2>
        <div class="knowledge-ingestion-toolbar">
          <el-select v-model="knowledgeIngestionFilters.status" clearable placeholder="status">
            <el-option label="UPLOADED" value="UPLOADED" />
            <el-option label="PARSING" value="PARSING" />
            <el-option label="INDEXING" value="INDEXING" />
            <el-option label="READY" value="READY" />
            <el-option label="DUPLICATE" value="DUPLICATE" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
          <el-select v-model="knowledgeIngestionFilters.limit" placeholder="limit">
            <el-option label="10" :value="10" />
            <el-option label="20" :value="20" />
            <el-option label="50" :value="50" />
          </el-select>
          <el-button :loading="knowledgeIngestionLoading" @click="refreshKnowledgeIngestions">Apply</el-button>
        </div>
        <el-table v-loading="knowledgeIngestionLoading" class="knowledge-table" :data="knowledgeIngestionRows" style="width: 100%">
          <el-table-column label="Job" min-width="230">
            <template #default="{ row }">
              <div class="knowledge-title">
                <strong>{{ row.title }}</strong>
                <span>{{ row.jobId }} / {{ row.fileName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Source" min-width="140">
            <template #default="{ row }">
              <div class="knowledge-source">
                <strong>{{ row.source }}</strong>
                <span>{{ row.category }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Status" width="118">
            <template #default="{ row }">
              <el-tag :type="ingestionStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Chunks" width="94">
            <template #default="{ row }">{{ row.chunkCount }}</template>
          </el-table-column>
          <el-table-column label="Vectors" width="94">
            <template #default="{ row }">{{ row.vectorCount }}</template>
          </el-table-column>
          <el-table-column label="Updated" width="128">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column prop="message" label="Message" min-width="220" />
        </el-table>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          Knowledge Documents
          <span class="panel-title-actions">
            <span class="generated-at">{{ knowledgeStats?.corpusVersion || 'unknown corpus' }}</span>
            <el-button circle size="small" :loading="knowledgeLoading" @click="refreshKnowledgeDocuments">
              <RefreshCw :size="15" />
            </el-button>
            <Database :size="19" />
          </span>
        </h2>
        <div class="knowledge-summary">
          <div class="ai-breakdown">
            <h3>Categories</h3>
            <div v-for="row in knowledgeTopCategories" :key="row.name" class="ai-breakdown-row">
              <span>{{ row.name }}</span>
              <strong>{{ row.count }}</strong>
            </div>
          </div>
          <div class="ai-breakdown">
            <h3>Sources</h3>
            <div v-for="row in knowledgeTopSources" :key="row.name" class="ai-breakdown-row">
              <span>{{ row.name }}</span>
              <strong>{{ row.count }}</strong>
            </div>
          </div>
        </div>
        <div class="knowledge-toolbar">
          <el-input v-model="knowledgeFilters.keyword" clearable placeholder="Keyword, source, tag" @keyup.enter="refreshKnowledgeDocuments" />
          <el-select v-model="knowledgeFilters.role" clearable placeholder="role">
            <el-option label="ADMIN" value="ADMIN" />
            <el-option label="STUDENT" value="STUDENT" />
            <el-option label="COMPANY" value="COMPANY" />
          </el-select>
          <el-select v-model="knowledgeFilters.limit" placeholder="limit">
            <el-option label="10" :value="10" />
            <el-option label="20" :value="20" />
            <el-option label="50" :value="50" />
          </el-select>
          <el-button :loading="knowledgeLoading" @click="refreshKnowledgeDocuments">Apply</el-button>
        </div>

        <el-table v-loading="knowledgeLoading" class="knowledge-table" :data="knowledgeDocumentRows" style="width: 100%">
          <el-table-column label="Document" min-width="220">
            <template #default="{ row }">
              <div class="knowledge-title">
                <strong>{{ row.title }}</strong>
                <span>{{ row.documentId }} / {{ row.category }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Source" min-width="150">
            <template #default="{ row }">
              <div class="knowledge-source">
                <strong>{{ row.source }}</strong>
                <span>{{ row.createdBy }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Roles" min-width="150">
            <template #default="{ row }">
              <div class="knowledge-tag-list">
                <el-tag v-for="role in row.roles" :key="role" size="small">{{ role }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Tags" min-width="190">
            <template #default="{ row }">
              <div class="knowledge-tag-list">
                <span v-for="tag in row.tags" :key="tag" class="knowledge-chip">{{ tag }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Created" width="128">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          Add Knowledge Document
          <ClipboardCheck :size="19" />
        </h2>
        <el-form label-position="top">
          <div class="knowledge-form-grid">
            <el-form-item label="Title">
              <el-input v-model="knowledgeForm.title" maxlength="120" show-word-limit />
            </el-form-item>
            <el-form-item label="Category">
              <el-input v-model="knowledgeForm.category" />
            </el-form-item>
            <el-form-item label="Source">
              <el-input v-model="knowledgeForm.source" />
            </el-form-item>
            <el-form-item label="Readable Roles">
              <el-select v-model="knowledgeForm.roles" multiple collapse-tags collapse-tags-tooltip>
                <el-option label="ADMIN" value="ADMIN" />
                <el-option label="STUDENT" value="STUDENT" />
                <el-option label="COMPANY" value="COMPANY" />
                <el-option label="ALL" value="ALL" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="Tags">
            <el-input v-model="knowledgeForm.tags" placeholder="Java, resume, interview" />
          </el-form-item>
          <el-form-item label="Content">
            <el-input v-model="knowledgeForm.content" type="textarea" :rows="5" maxlength="2000" show-word-limit />
          </el-form-item>
          <el-button type="primary" :loading="knowledgeCreating" @click="submitKnowledgeDocument">Create Document</el-button>
        </el-form>
      </section>
    </div>

    <div v-if="activeModule === 'audit'" class="module-stack">
      <section class="panel module-panel audit-hero">
        <div>
          <h2 class="panel-title">
            审计数据中心
            <Database :size="20" />
          </h2>
          <p>按学生、岗位、投递、AI 初筛和 AI 面试记录聚合跨服务查询结果。</p>
        </div>
        <span class="generated-at">{{ formatDateTime(auditOverview?.generatedAt) }}</span>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          查询条件
          <span class="panel-title-actions">
            <el-button circle size="small" :loading="auditLoading" @click="refreshAuditOverview">
              <RefreshCw :size="15" />
            </el-button>
            <Search :size="19" />
          </span>
        </h2>
        <div class="audit-toolbar">
          <el-input v-model="auditFilters.keyword" clearable placeholder="关键词 / ID / 摘要" @keyup.enter="refreshAuditOverview" />
          <el-select v-model="auditFilters.entityType" clearable placeholder="数据类型">
            <el-option label="学生" value="STUDENT" />
            <el-option label="岗位" value="JOB" />
            <el-option label="投递" value="DELIVERY" />
            <el-option label="AI 初筛" value="AI_SCREENING" />
            <el-option label="AI 面试" value="AI_INTERVIEW" />
          </el-select>
          <el-input v-model="auditFilters.studentId" clearable placeholder="studentId" @keyup.enter="refreshAuditOverview" />
          <el-input v-model="auditFilters.companyId" clearable placeholder="companyId" @keyup.enter="refreshAuditOverview" />
          <el-input v-model="auditFilters.jobId" clearable placeholder="jobId" @keyup.enter="refreshAuditOverview" />
          <el-select v-model="auditFilters.limit" placeholder="limit">
            <el-option label="20" :value="20" />
            <el-option label="50" :value="50" />
            <el-option label="100" :value="100" />
          </el-select>
          <el-button type="primary" :loading="auditLoading" @click="refreshAuditOverview">查询</el-button>
          <el-button :loading="auditExportLoading" @click="runAuditExport">导出</el-button>
        </div>
      </section>

      <div class="grid three audit-metrics">
        <div v-for="metric in auditMetrics" :key="metric.key" class="metric audit-metric">
          <Database :size="22" />
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}{{ metric.unit || '' }}</strong>
        </div>
      </div>

      <section v-if="auditExport" class="panel module-panel audit-export-panel">
        <h2 class="panel-title">
          导出结果
          <ClipboardCheck :size="19" />
        </h2>
        <div class="audit-export-grid">
          <div>
            <span>文件</span>
            <strong>{{ auditExport.fileName }}</strong>
          </div>
          <div>
            <span>行数</span>
            <strong>{{ auditExport.rowCount }}</strong>
          </div>
          <div>
            <span>有效期</span>
            <strong>{{ formatDateTime(auditExport.expiresAt) }}</strong>
          </div>
          <div>
            <span>下载地址</span>
            <code>{{ auditExport.downloadUrl }}</code>
          </div>
        </div>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          跨服务审计记录
          <span class="panel-title-actions">
            <span class="generated-at">{{ auditOverview?.source || 'frontend-demo' }}</span>
            <Database :size="19" />
          </span>
        </h2>

        <el-table v-loading="auditLoading" class="audit-table" :data="auditRows" style="width: 100%">
          <el-table-column label="对象" min-width="190">
            <template #default="{ row }">
              <div class="audit-record-title">
                <strong>{{ row.title }}</strong>
                <span>{{ row.auditId }} / {{ row.entityId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag type="info">{{ auditEntityLabel(row.entityType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="关联" min-width="160">
            <template #default="{ row }">
              <div class="audit-links">
                <span v-if="row.studentId">S: {{ row.studentId }}</span>
                <span v-if="row.companyId">C: {{ row.companyId }}</span>
                <span v-if="row.jobId">J: {{ row.jobId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="service" label="服务" min-width="130" />
          <el-table-column prop="status" label="状态" width="112" />
          <el-table-column label="风险" width="96">
            <template #default="{ row }">
              <el-tag :type="auditRiskType(row)">{{ row.riskLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分数" width="86">
            <template #default="{ row }">{{ row.score ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="240" />
          <el-table-column label="标签" min-width="180">
            <template #default="{ row }">
              <div class="audit-tags">
                <span v-for="tag in row.tags" :key="`${row.auditId}-${tag}`">{{ tag }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="128">
            <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
          </el-table-column>
        </el-table>
        <div v-if="auditRows.length" v-loading="auditLoading" class="audit-cards">
          <article v-for="row in auditRows" :key="row.auditId" class="audit-card">
            <header>
              <div class="audit-record-title">
                <strong>{{ row.title }}</strong>
                <span>{{ row.auditId }} / {{ row.entityId }}</span>
              </div>
              <span class="audit-pill info">{{ auditEntityLabel(row.entityType) }}</span>
            </header>
            <div class="audit-card-grid">
              <div>
                <span>Service</span>
                <strong>{{ row.service }}</strong>
              </div>
              <div>
                <span>Status</span>
                <strong>{{ row.status }}</strong>
              </div>
              <div>
                <span>Risk</span>
                <strong class="audit-pill" :class="auditRiskType(row)">{{ row.riskLevel }}</strong>
              </div>
              <div>
                <span>Score</span>
                <strong>{{ row.score ?? '-' }}</strong>
              </div>
            </div>
            <div class="audit-links">
              <span v-if="row.studentId">S: {{ row.studentId }}</span>
              <span v-if="row.companyId">C: {{ row.companyId }}</span>
              <span v-if="row.jobId">J: {{ row.jobId }}</span>
            </div>
            <p>{{ row.summary }}</p>
            <div class="audit-tags">
              <span v-for="tag in row.tags" :key="`${row.auditId}-card-${tag}`">{{ tag }}</span>
            </div>
            <time>{{ formatDateTime(row.occurredAt) }}</time>
          </article>
        </div>
        <el-empty v-if="!auditRows.length && !auditLoading" description="暂无审计记录" />
      </section>

      <div v-if="auditWarnings.length" class="warning-list">
        <el-alert
          v-for="warning in auditWarnings"
          :key="warning"
          :title="warning"
          type="info"
          :closable="false"
          show-icon
        />
      </div>
    </div>

    <div v-if="activeModule === 'system'" class="module-stack">
      <div class="grid three">
        <div class="metric system-metric">
          <ServerCog :size="22" />
          <span>状态来源</span>
          <strong>{{ systemStatus?.applicationName || 'user-service' }}</strong>
        </div>
        <div class="metric system-metric">
          <HardDrive :size="22" />
          <span>运行环境</span>
          <strong>{{ systemStatus?.environment || 'unknown' }}</strong>
        </div>
        <div class="metric system-metric">
          <AlertTriangle :size="22" />
          <span>告警数</span>
          <strong>{{ warningRows.length }}</strong>
        </div>
      </div>

      <section class="panel module-panel">
        <h2 class="panel-title">
          部署拓扑
          <HardDrive :size="19" />
        </h2>
        <div class="topology-list">
          <article v-for="node in topologyNodes" :key="node.id" class="topology-node">
            <header class="topology-node-header">
              <div>
                <strong>{{ node.name }}</strong>
                <span>{{ node.host }}</span>
              </div>
              <el-tag type="info">{{ node.role }}</el-tag>
            </header>
            <div class="topology-services">
              <div v-for="service in node.services" :key="`${node.id}-${service.name}`" class="topology-service">
                <div class="topology-service-main">
                  <strong>{{ service.displayName }}</strong>
                  <span>{{ service.name }}</span>
                </div>
                <span class="topology-port">{{ service.port }}</span>
                <el-tag :type="systemTagType(service.status)">{{ service.status }}</el-tag>
                <span class="topology-health">{{ service.healthUrl }}</span>
                <span v-if="service.note" class="topology-note">{{ service.note }}</span>
              </div>
            </div>
          </article>
        </div>
        <div v-if="topologyWarnings.length" class="topology-warnings">
          <el-alert
            v-for="warning in topologyWarnings"
            :key="warning"
            :title="warning"
            type="info"
            :closable="false"
            show-icon
          />
        </div>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          服务运行状态
          <span class="panel-title-actions">
            <span class="generated-at">{{ formatDateTime(systemStatus?.generatedAt) }}</span>
            <el-button circle size="small" :loading="systemStatusLoading" @click="refreshSystemStatus">
              <RefreshCw :size="15" />
            </el-button>
            <ServerCog :size="19" />
          </span>
        </h2>
        <el-table class="system-table" :data="serviceRows" style="width: 100%">
          <el-table-column label="服务" min-width="168">
            <template #default="{ row }">
              <div class="system-name">
                <strong>{{ row.displayName }}</strong>
                <span>{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="端口" width="104">
            <template #default="{ row }">{{ servicePort(row) }}</template>
          </el-table-column>
          <el-table-column prop="healthPath" label="健康检查" min-width="150" />
          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <el-tag :type="systemTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="说明" min-width="180" />
        </el-table>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          持久化与缓存
          <Database :size="19" />
        </h2>
        <el-table class="system-table" :data="persistenceRows" style="width: 100%">
          <el-table-column prop="module" label="模块" min-width="120" />
          <el-table-column label="启用" width="92">
            <template #default="{ row }">
              <el-tag :type="enabledTagType(row.enabled)">{{ row.enabled ? '已启用' : '未启用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="database" label="数据库" min-width="160" />
          <el-table-column prop="cacheKeyPrefix" label="缓存前缀" min-width="180" />
          <el-table-column prop="note" label="说明" min-width="180" />
        </el-table>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          基础设施
          <HardDrive :size="19" />
        </h2>
        <el-table class="system-table" :data="infrastructureRows" style="width: 100%">
          <el-table-column prop="name" label="组件" min-width="120" />
          <el-table-column label="地址" min-width="160">
            <template #default="{ row }">{{ row.host }}:{{ row.port }}</template>
          </el-table-column>
          <el-table-column label="配置" width="92">
            <template #default="{ row }">
              <el-tag :type="enabledTagType(row.configured)">{{ row.configured ? '已配置' : '未配置' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <el-tag :type="systemTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="说明" min-width="180" />
        </el-table>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          运维提示
          <AlertTriangle :size="19" />
        </h2>
        <div v-if="warningRows.length" class="warning-list">
          <el-alert
            v-for="warning in warningRows"
            :key="warning"
            :title="warning"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
        <el-empty v-else description="暂无运维提示" />
      </section>
    </div>

    <div v-if="activeModule === 'deploy'" class="module-stack">
      <section class="panel module-panel deploy-hero">
        <div>
          <h2 class="panel-title">
            部署启动向导
            <Rocket :size="20" />
          </h2>
          <p>{{ deploymentGuide?.summary || '按三虚拟机顺序启动并验收校园招聘系统。' }}</p>
        </div>
        <el-tag type="info">{{ deploymentGuide?.environment || 'frontend-demo' }}</el-tag>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          启动顺序
          <ClipboardCheck :size="19" />
        </h2>
        <div class="deploy-steps">
          <article v-for="step in deploymentSteps" :key="step.order" class="deploy-step">
            <div class="deploy-step-index">{{ step.order }}</div>
            <div class="deploy-step-body">
              <header class="deploy-step-header">
                <div>
                  <strong>{{ step.title }}</strong>
                  <span>{{ step.nodeName }}</span>
                </div>
                <el-tag :type="stepTagType(step.nodeId)">{{ step.nodeId }}</el-tag>
              </header>
              <p>{{ step.purpose }}</p>

              <div class="deploy-command-list">
                <code v-for="command in step.commands" :key="command">{{ command }}</code>
              </div>

              <div class="deploy-check-grid">
                <div>
                  <span class="deploy-label">检查地址</span>
                  <span v-for="url in step.verifyUrls" :key="url" class="deploy-url">{{ url }}</span>
                </div>
                <div>
                  <span class="deploy-label">期望结果</span>
                  <strong>{{ step.expectedResult }}</strong>
                </div>
              </div>

              <div class="deploy-troubleshooting">
                <span class="deploy-label">排障提示</span>
                <span v-for="item in step.troubleshooting" :key="item">{{ item }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          验收命令
          <CheckCircle2 :size="19" />
        </h2>
        <div class="acceptance-list">
          <article v-for="check in acceptanceChecks" :key="check.name" class="acceptance-item">
            <div>
              <strong>{{ check.name }}</strong>
              <code>{{ check.command }}</code>
            </div>
            <span>{{ check.expectedResult }}</span>
          </article>
        </div>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          向导提示
          <AlertTriangle :size="19" />
        </h2>
        <div v-if="deploymentWarnings.length" class="warning-list">
          <el-alert
            v-for="warning in deploymentWarnings"
            :key="warning"
            :title="warning"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
        <el-empty v-else description="暂无向导提示" />
      </section>
    </div>

    <section v-if="activeModule === 'guidance'" class="panel module-panel">
      <h2 class="panel-title">就业指导关注点</h2>
      <el-timeline>
        <el-timeline-item timestamp="简历质量">项目经历需要补充量化指标和部署信息</el-timeline-item>
        <el-timeline-item timestamp="岗位需求">Java 后端、测试开发、数据分析岗位热度较高</el-timeline-item>
        <el-timeline-item timestamp="辅导安排">建议组织 Spring Cloud、Redis、MySQL 面试专题</el-timeline-item>
      </el-timeline>
    </section>
  </section>
</template>

<style scoped>
.panel-title-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.generated-at {
  color: #667085;
  font-size: 13px;
  font-weight: 500;
}

.compact-empty {
  --el-empty-padding: 18px 0 22px;
}

.compact-empty :deep(.el-empty__image) {
  width: 112px;
}

.dashboard-overview .metric strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.analytics-panel {
  min-width: 0;
}

.trend-chart {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.trend-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) minmax(112px, 0.35fr);
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.trend-label {
  color: #344054;
  font-size: 13px;
  font-weight: 800;
}

.trend-bars {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.trend-track {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf2f7;
}

.trend-track.compact {
  height: 7px;
}

.trend-bar {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.trend-bar.delivery {
  background: #2563eb;
}

.trend-bar.interview {
  background: #0f766e;
}

.trend-bar.offer {
  background: #d97706;
}

.trend-values {
  display: grid;
  gap: 2px;
  min-width: 0;
  text-align: right;
}

.trend-values strong {
  color: #18212f;
}

.trend-values span {
  color: #667085;
  font-size: 12px;
}

.analytics-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  min-width: 0;
}

.funnel-list,
.skill-demand-list {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.funnel-row {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.funnel-row > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.funnel-row strong,
.skill-demand-main strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.funnel-row span,
.skill-demand-main span {
  color: #667085;
  font-size: 13px;
}

.skill-demand-row {
  display: grid;
  grid-template-columns: minmax(124px, 0.8fr) minmax(0, 1fr) 42px;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.skill-demand-main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.skill-demand-score {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf2f7;
}

.skill-demand-score span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #0f766e;
}

.skill-demand-number {
  color: #18212f;
  text-align: right;
}

.audit-hero {
  align-items: flex-start;
  display: flex;
  gap: 16px;
  justify-content: space-between;
}

.audit-hero p {
  color: #475467;
  margin: 8px 0 0;
}

.audit-toolbar {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(220px, 1.4fr) 132px repeat(3, minmax(110px, 0.8fr)) 96px auto auto;
  min-width: 0;
}

.audit-toolbar :deep(.el-select),
.audit-toolbar :deep(.el-input) {
  width: 100%;
}

.audit-metric strong {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 22px;
  line-height: 1.2;
}

.audit-export-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1.1fr) minmax(96px, 0.45fr) minmax(140px, 0.6fr) minmax(0, 1.4fr);
}

.audit-export-grid > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.audit-export-grid span {
  color: #667085;
  font-size: 13px;
  font-weight: 700;
}

.audit-export-grid strong,
.audit-export-grid code {
  min-width: 0;
  overflow-wrap: anywhere;
}

.audit-export-grid code {
  background: #f2f4f7;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  color: #344054;
  padding: 6px 8px;
  white-space: normal;
}

.audit-table {
  min-width: 0;
}

.audit-cards {
  display: none;
}

:deep(.audit-table .cell) {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.audit-record-title {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.audit-record-title span,
.audit-links span {
  color: #667085;
  font-size: 13px;
}

.audit-links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  min-width: 0;
}

.audit-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.audit-tags span {
  background: #f2f4f7;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  color: #344054;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  max-width: 100%;
  overflow-wrap: anywhere;
  padding: 3px 7px;
}

.audit-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-height: 24px;
  padding: 0 9px;
  border: 1px solid #5eead4;
  border-radius: 6px;
  color: #0f766e;
  background: #ccfbf1;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
}

.audit-pill.info {
  color: #334155;
  border-color: #cbd5e1;
  background: #e2e8f0;
}

.audit-pill.warning {
  color: #78350f;
  border-color: #f59e0b;
  background: #fde68a;
}

.audit-pill.danger {
  color: #b42318;
  border-color: #fecdca;
  background: #fee4e2;
}

.audit-card {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid #dde5ed;
  border-radius: 8px;
  background: #ffffff;
}

.audit-card header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  min-width: 0;
}

.audit-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  min-width: 0;
}

.audit-card-grid > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.audit-card-grid span,
.audit-card time {
  color: #667085;
  font-size: 13px;
}

.audit-card-grid strong,
.audit-card p {
  min-width: 0;
  overflow-wrap: anywhere;
}

.audit-card p {
  margin: 0;
  color: #475467;
  line-height: 1.55;
}

.system-metric strong {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 22px;
  line-height: 1.2;
}

.system-table {
  min-width: 0;
}

:deep(.system-table .cell) {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.system-name {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.system-name span {
  color: #667085;
  font-size: 13px;
}

.warning-list {
  display: grid;
  gap: 10px;
}

.permission-summary {
  align-items: start;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(160px, 0.35fr) minmax(0, 1fr);
  min-width: 0;
}

.permission-summary > div:first-child {
  display: grid;
  gap: 4px;
}

.permission-summary span {
  color: #667085;
}

.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.permission-tags.compact {
  gap: 6px;
}

.permission-chip {
  border: 1px solid #b7d6ff;
  border-radius: 6px;
  background: #eef6ff;
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  max-width: 100%;
  padding: 3px 8px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.permission-chip.compact {
  min-height: 22px;
  padding: 2px 6px;
  font-size: 11px;
}

.account-toolbar {
  display: grid;
  gap: 10px;
  grid-template-columns: 150px 150px minmax(180px, 1fr) auto;
  margin-bottom: 14px;
  min-width: 0;
}

.account-toolbar :deep(.el-select),
.account-toolbar :deep(.el-input) {
  width: 100%;
}

.account-table {
  min-width: 0;
}

:deep(.account-table .cell) {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.account-badge {
  border: 1px solid #d0d5dd;
  border-radius: 5px;
  color: #344054;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 22px;
  padding: 2px 7px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.account-badge.role {
  background: #f8fafc;
}

.account-badge.active {
  border-color: #86efac;
  background: #f0fdf4;
  color: #15803d;
}

.account-badge.disabled {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.account-badge.locked {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.account-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.account-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.65fr);
}

.topology-list {
  display: grid;
  gap: 0;
}

.topology-node {
  border-bottom: 1px solid #e4e7ec;
  display: grid;
  gap: 14px;
  padding: 16px 0;
}

.topology-node:first-child {
  padding-top: 0;
}

.topology-node:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.topology-node-header {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-width: 0;
}

.topology-node-header div,
.topology-service-main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.topology-node-header span,
.topology-service-main span,
.topology-health,
.topology-note {
  color: #667085;
  font-size: 13px;
}

.topology-services {
  display: grid;
  gap: 10px;
}

.topology-service {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(138px, 1fr) 64px 112px minmax(180px, 1.4fr);
  min-width: 0;
}

.topology-port {
  color: #101828;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.topology-health,
.topology-note {
  min-width: 0;
  overflow-wrap: anywhere;
}

.topology-note {
  grid-column: 4;
}

.topology-warnings {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.deploy-hero {
  align-items: flex-start;
  display: flex;
  gap: 16px;
  justify-content: space-between;
}

.deploy-hero p {
  color: #475467;
  margin: 8px 0 0;
}

.deploy-steps {
  display: grid;
  gap: 18px;
}

.deploy-step {
  display: grid;
  gap: 14px;
  grid-template-columns: 36px minmax(0, 1fr);
}

.deploy-step-index {
  align-items: center;
  background: #0f766e;
  border-radius: 8px;
  color: #fff;
  display: inline-flex;
  font-weight: 800;
  height: 36px;
  justify-content: center;
  width: 36px;
}

.deploy-step-body {
  border-bottom: 1px solid #e4e7ec;
  display: grid;
  gap: 12px;
  min-width: 0;
  padding-bottom: 18px;
}

.deploy-step:last-child .deploy-step-body {
  border-bottom: 0;
  padding-bottom: 0;
}

.deploy-step-header {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-width: 0;
}

.deploy-step-header div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.deploy-step-header span,
.deploy-step-body p,
.deploy-label,
.deploy-troubleshooting span,
.acceptance-item span {
  color: #667085;
}

.deploy-step-body p {
  margin: 0;
}

.deploy-command-list,
.deploy-troubleshooting,
.acceptance-list {
  display: grid;
  gap: 10px;
}

.deploy-command-list code,
.acceptance-item code {
  background: #101828;
  border-radius: 6px;
  color: #f8fafc;
  display: block;
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
  padding: 10px 12px;
  white-space: normal;
}

.deploy-check-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
}

.deploy-check-grid > div {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.deploy-label {
  font-size: 13px;
  font-weight: 700;
}

.deploy-url {
  color: #344054;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.deploy-troubleshooting span:not(.deploy-label) {
  font-size: 13px;
}

.acceptance-item {
  align-items: start;
  border-bottom: 1px solid #e4e7ec;
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  padding-bottom: 14px;
}

.acceptance-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.acceptance-item > div {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.ai-metric strong {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 22px;
  line-height: 1.2;
}

.ai-observability-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.knowledge-summary {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 14px;
  min-width: 0;
}

.ai-breakdown {
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 14px;
}

.ai-breakdown h3 {
  color: #344054;
  font-size: 14px;
  margin: 0;
}

.ai-breakdown-row {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-width: 0;
}

.ai-breakdown-row span {
  color: #667085;
  min-width: 0;
  overflow-wrap: anywhere;
}

.ai-call-toolbar,
.ai-search-toolbar,
.knowledge-toolbar {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
  min-width: 0;
}

.ai-call-toolbar {
  grid-template-columns: minmax(160px, 1fr) 140px 112px auto;
}

.ai-search-toolbar {
  grid-template-columns: minmax(220px, 1fr) 132px 112px auto;
}

.knowledge-toolbar {
  grid-template-columns: minmax(220px, 1fr) 132px 112px auto;
}

.knowledge-form-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(220px, 1fr) repeat(3, minmax(140px, 0.65fr));
  min-width: 0;
}

.knowledge-form-grid :deep(.el-select),
.knowledge-form-grid :deep(.el-input) {
  width: 100%;
}

.knowledge-ingestion-toolbar {
  display: grid;
  gap: 10px;
  grid-template-columns: 150px 112px max-content;
  margin-bottom: 14px;
  min-width: 0;
}

.knowledge-ingestion-toolbar :deep(.el-select) {
  width: 100%;
}

.knowledge-ingestion-toolbar .el-button {
  width: 88px;
}

.knowledge-upload-row {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(220px, 1fr) auto;
  min-width: 0;
}

.knowledge-upload-hint,
.knowledge-upload-meta {
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
  margin: 0 0 14px;
  overflow-wrap: anywhere;
}

.knowledge-file-picker {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.knowledge-file-picker span {
  color: #344054;
  font-size: 13px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.knowledge-file-input {
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  color: #344054;
  min-width: 0;
  padding: 8px;
  width: 100%;
}

.knowledge-upload-row .el-button {
  min-height: 38px;
  white-space: normal;
}

.knowledge-upload-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin: 10px 0 0;
}

.knowledge-upload-result {
  margin-top: 12px;
}

.vector-status-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  min-width: 0;
}

.vector-status-card {
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
}

.vector-status-card span {
  color: #667085;
  font-size: 12px;
  font-weight: 700;
}

.vector-status-card strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.vector-warning-list {
  margin-top: 12px;
}

.knowledge-table {
  min-width: 0;
}

:deep(.knowledge-table .cell) {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.knowledge-title,
.knowledge-source {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.knowledge-title strong,
.knowledge-source strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.knowledge-title span,
.knowledge-source span {
  color: #667085;
  font-size: 13px;
}

.knowledge-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.knowledge-chip {
  background: #f2f4f7;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  color: #344054;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  max-width: 100%;
  overflow-wrap: anywhere;
  padding: 3px 7px;
}

.ai-table {
  min-width: 0;
}

:deep(.ai-table .cell) {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.ai-status-badge {
  border: 1px solid #d0d5dd;
  border-radius: 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 22px;
  min-width: 42px;
  padding: 2px 7px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.ai-status-badge.ok {
  border-color: #86efac;
  background: #f0fdf4;
  color: #15803d;
}

.ai-status-badge.mocked {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.ai-status-badge.failed {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.ai-search-results {
  display: grid;
  gap: 12px;
}

.ai-search-result {
  border-bottom: 1px solid #e4e7ec;
  display: grid;
  gap: 10px;
  min-width: 0;
  padding-bottom: 14px;
}

.ai-search-result:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.ai-search-result header {
  align-items: start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-width: 0;
}

.ai-search-result header > div:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.ai-search-result strong,
.ai-search-result span,
.ai-search-result p {
  overflow-wrap: anywhere;
}

.ai-search-result header span,
.ai-search-result p {
  color: #667085;
}

.ai-search-result p {
  margin: 0;
}

.ai-search-score {
  align-items: center;
  display: inline-flex;
  flex-shrink: 0;
  gap: 8px;
}

.ai-type-badge {
  border: 1px solid #d0d5dd;
  border-radius: 5px;
  background: #f8fafc;
  color: #344054;
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 2px 7px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.ai-highlight-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ai-highlight-list span {
  background: #f2f4f7;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  color: #344054;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  padding: 3px 7px;
}

@media (max-width: 640px) {
  .panel-title-actions {
    align-items: flex-start;
    flex-direction: column-reverse;
  }

  .topology-node-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .permission-summary,
  .account-toolbar,
  .account-grid,
  .analytics-grid,
  .audit-toolbar,
  .audit-export-grid,
  .ai-observability-grid,
  .ai-call-toolbar,
  .ai-search-toolbar,
  .knowledge-toolbar,
  .knowledge-form-grid,
  .knowledge-ingestion-toolbar,
  .knowledge-upload-row,
  .vector-status-grid {
    grid-template-columns: 1fr;
  }

  .trend-row,
  .skill-demand-row {
    align-items: stretch;
    grid-template-columns: 1fr;
  }

  .trend-values,
  .skill-demand-number {
    text-align: left;
  }

  .funnel-row > div {
    align-items: flex-start;
    flex-direction: column;
  }

  .topology-service {
    align-items: start;
    gap: 8px;
    grid-template-columns: minmax(0, 1fr) 52px 96px;
  }

  .topology-health,
  .topology-note {
    grid-column: 1 / -1;
  }

  .deploy-hero,
  .audit-hero,
  .deploy-step-header,
  .ai-search-result header {
    align-items: flex-start;
    flex-direction: column;
  }

  .ai-search-score {
    align-self: flex-start;
  }

  .deploy-check-grid,
  .acceptance-item {
    grid-template-columns: 1fr;
  }

  .deploy-step {
    grid-template-columns: 30px minmax(0, 1fr);
  }

  .deploy-step-index {
    border-radius: 7px;
    height: 30px;
    width: 30px;
  }

  .audit-table {
    display: none;
  }

  .audit-cards {
    display: grid;
    gap: 10px;
  }

  .audit-card header,
  .audit-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
