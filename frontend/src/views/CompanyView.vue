<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { Bot, ClipboardList, Plus, RefreshCw } from 'lucide-vue-next'
import {
  analyzeJob,
  createJob,
  createCandidateScreenTask,
  currentCompanyId,
  getCandidateScreenTask,
  listCandidateScreenTasks,
  listCompanyDeliveries,
  listJobs,
  matchResumeJob,
  retryCandidateScreenTask,
  updateDeliveryStatus,
  type CandidateScreenTask,
  type CandidateScreenTaskStatus,
  type DeliveryRecord,
  type DeliveryStatus,
  type JobSummary,
  type MatchResult,
  type ResumeParseMetadata
} from '../api/client'

type ResumeParseTag = {
  key: string
  text: string
  type: 'success' | 'info' | 'warning'
}

const route = useRoute()
const jobs = ref<JobSummary[]>([])
const candidate = ref<MatchResult>()
const deliveries = ref<DeliveryRecord[]>([])
const screeningTasks = ref<CandidateScreenTask[]>([])
const screeningLoading = ref<Record<string, boolean>>({})
const taskActionLoading = ref<Record<string, boolean>>({})
const historyRefreshing = ref(false)
const form = reactive({
  title: 'Java 后端实习生',
  city: '杭州',
  salaryRange: '180-260/天',
  requiredSkills: 'Java,Spring Boot,MySQL,Redis',
  description: '参与招聘平台、数据看板和中台接口开发。'
})
const reviewStatuses: Array<{ status: Exclude<DeliveryStatus, 'SUBMITTED'>, label: string }> = [
  { status: 'VIEWED', label: '已查看' },
  { status: 'INTERVIEW', label: '面试中' },
  { status: 'OFFER', label: '已录用' },
  { status: 'REJECTED', label: '未通过' }
]
const activeModule = computed(() => typeof route.params.module === 'string' ? route.params.module : 'publish')
const screeningCards = computed(() => [...screeningTasks.value]
  .sort((left, right) => new Date(right.updatedAt || right.createdAt).getTime() - new Date(left.updatedAt || left.createdAt).getTime()))
const hasAnyDeliveryParseMetadata = computed(() =>
  deliveries.value.some((delivery) => hasResumeParseMetadata(delivery)))

onMounted(async () => {
  const [jobList, deliveryList, taskList] = await Promise.all([
    listJobs(),
    listCompanyDeliveries(),
    listCandidateScreenTasks()
  ])
  jobs.value = jobList
  deliveries.value = deliveryList
  screeningTasks.value = taskList
})

async function publish() {
  const job = await createJob({
    companyId: currentCompanyId(),
    title: form.title,
    city: form.city,
    salaryRange: form.salaryRange,
    requiredSkills: form.requiredSkills.split(',').map((item) => item.trim()).filter(Boolean),
    description: form.description
  })
  jobs.value = [job, ...jobs.value]
  ElMessage.success('岗位已发布')
}

async function runAnalyze(jobId: string) {
  const analyzed = await analyzeJob(jobId)
  jobs.value = jobs.value.map((job) => job.jobId === analyzed.jobId ? analyzed : job)
  ElMessage.success('岗位分析已生成')
}

async function loadCandidate(jobId: string) {
  candidate.value = await matchResumeJob('R001', jobId)
}

async function changeDeliveryStatus(delivery: DeliveryRecord, status: DeliveryStatus) {
  const updated = await updateDeliveryStatus(delivery, status)
  deliveries.value = deliveries.value.map((item) => item.deliveryId === updated.deliveryId ? updated : item)
  ElMessage.success('投递状态已更新')
}

async function runCandidateScreen(delivery: DeliveryRecord) {
  const job = jobs.value.find((item) => item.jobId === delivery.jobId)
  const requestedAt = Date.now()
  const parseMetadata = resumeParseMetadata(delivery)
  screeningLoading.value = { ...screeningLoading.value, [delivery.deliveryId]: true }
  try {
    const task = await createCandidateScreenTask({
      deliveryId: delivery.deliveryId,
      studentId: delivery.studentId,
      resumeId: delivery.resumeId,
      jobId: delivery.jobId,
      companyId: delivery.companyId,
      resumeSourceFormat: parseMetadata.resumeSourceFormat,
      resumeParseStatus: parseMetadata.resumeParseStatus,
      resumeParsedTextLength: parseMetadata.resumeParsedTextLength,
      targetRole: job?.title || 'Java 后端实习生',
      skills: ['Java', 'Spring Boot', 'MySQL', 'Redis', 'Docker'],
      projects: ['校园二手交易系统', '在线考试平台'],
      jobRequirements: job?.requiredSkills || ['Java', 'Spring Boot', 'MySQL', 'Redis'],
      resumeSummary: '软件工程本科，具备 Java Web 项目、数据库设计和缓存实践经历。',
      jobDescription: job?.description || '参与招聘平台、数据看板和中台接口开发。'
    })
    const localTask = upsertLocalScreeningTask(delivery, task)
    await refreshCandidateScreenTasks(localTask, requestedAt)
    ElMessage.success('异步初筛任务已提交')
  } finally {
    screeningLoading.value = { ...screeningLoading.value, [delivery.deliveryId]: false }
  }
}

async function refreshCandidateScreenTasks(localTask?: CandidateScreenTask, requestedAt = 0) {
  const records = await listCandidateScreenTasks()
  if (!localTask) {
    screeningTasks.value = records
    return
  }
  const hasFreshServerRecord = records.some((record) =>
    record.deliveryId === localTask.deliveryId && new Date(record.createdAt).getTime() >= requestedAt - 10_000)
  screeningTasks.value = hasFreshServerRecord ? records : [localTask, ...records]
}

async function manualRefreshCandidateScreenRecords() {
  historyRefreshing.value = true
  try {
    await refreshCandidateScreenTasks()
    ElMessage.success('异步初筛任务已刷新')
  } finally {
    historyRefreshing.value = false
  }
}

async function refreshCandidateScreenTask(task: CandidateScreenTask) {
  taskActionLoading.value = { ...taskActionLoading.value, [task.taskId]: true }
  try {
    upsertTask(await getCandidateScreenTask(task.taskId, task.companyId))
    ElMessage.success('任务状态已刷新')
  } finally {
    taskActionLoading.value = { ...taskActionLoading.value, [task.taskId]: false }
  }
}

async function retryCandidateScreen(task: CandidateScreenTask) {
  taskActionLoading.value = { ...taskActionLoading.value, [task.taskId]: true }
  try {
    const retried = await retryCandidateScreenTask(task.taskId, task.companyId)
    upsertTask(retried)
    ElMessage.success(retried.status === 'PENDING' ? '重试任务已提交' : '任务状态已更新')
  } finally {
    taskActionLoading.value = { ...taskActionLoading.value, [task.taskId]: false }
  }
}

function upsertLocalScreeningTask(delivery: DeliveryRecord, task: CandidateScreenTask) {
  const now = new Date().toISOString()
  const record: CandidateScreenTask = {
    ...task,
    ...resumeParseMetadata(delivery),
    taskId: task.taskId || `TASK-LOCAL-${Date.now()}`,
    companyId: task.companyId || delivery.companyId,
    deliveryId: task.deliveryId || delivery.deliveryId,
    studentId: task.studentId || delivery.studentId,
    resumeId: task.resumeId || delivery.resumeId,
    jobId: task.jobId || delivery.jobId,
    createdAt: task.createdAt || now,
    updatedAt: task.updatedAt || now
  }
  upsertTask(record)
  return record
}

function upsertTask(record: CandidateScreenTask) {
  screeningTasks.value = [
    record,
    ...screeningTasks.value.filter((item) => item.taskId !== record.taskId)
  ]
}

function statusText(status: DeliveryStatus) {
  const labels: Record<DeliveryStatus, string> = {
    SUBMITTED: '已投递',
    VIEWED: '已查看',
    INTERVIEW: '面试中',
    OFFER: '已录用',
    REJECTED: '未通过'
  }
  return labels[status]
}

function statusTagType(status: DeliveryStatus) {
  const types: Record<DeliveryStatus, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    SUBMITTED: 'info',
    VIEWED: 'primary',
    INTERVIEW: 'warning',
    OFFER: 'success',
    REJECTED: 'danger'
  }
  return types[status]
}

function taskStatusText(status: CandidateScreenTaskStatus) {
  const labels: Record<CandidateScreenTaskStatus, string> = {
    PENDING: '待处理',
    RUNNING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return labels[status]
}

function taskStatusTagType(status: CandidateScreenTaskStatus) {
  const types: Record<CandidateScreenTaskStatus, 'success' | 'warning' | 'info' | 'danger'> = {
    PENDING: 'info',
    RUNNING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger'
  }
  return types[status]
}

function resumeParseMetadata(source?: ResumeParseMetadata | null): ResumeParseMetadata {
  if (!source) {
    return {}
  }
  const sourceFormat = normalizeMetadataText(source.resumeSourceFormat || source.sourceFormat)
  const parseStatus = normalizeMetadataText(source.resumeParseStatus || source.parseStatus)
  const parsedTextLength = normalizeParsedTextLength(source.resumeParsedTextLength ?? source.parsedTextLength)
  return {
    ...(sourceFormat ? { sourceFormat } : {}),
    ...(sourceFormat ? { resumeSourceFormat: sourceFormat } : {}),
    ...(parseStatus ? { parseStatus } : {}),
    ...(parseStatus ? { resumeParseStatus: parseStatus } : {}),
    ...(parsedTextLength > 0 ? { parsedTextLength } : {}),
    ...(parsedTextLength > 0 ? { resumeParsedTextLength: parsedTextLength } : {})
  }
}

function hasResumeParseMetadata(source?: ResumeParseMetadata | null) {
  return resumeParseTags(source).length > 0
}

function resumeParseTags(source?: ResumeParseMetadata | null): ResumeParseTag[] {
  const metadata = resumeParseMetadata(source)
  const sourceFormat = normalizeMetadataText(metadata.sourceFormat)
  const parseStatus = normalizeMetadataText(metadata.parseStatus)
  const parsedTextLength = normalizeParsedTextLength(metadata.parsedTextLength)
  const tags: ResumeParseTag[] = []

  if (sourceFormat || parseStatus) {
    const text = [sourceFormat, parseStatusText(parseStatus)].filter(Boolean).join(' · ')
    tags.push({
      key: 'parse-status',
      text,
      type: parseStatus === 'UNPARSED' || parseStatus === 'UNKNOWN' ? 'warning' : 'success'
    })
  }
  if (parsedTextLength > 0) {
    tags.push({
      key: 'parsed-text-length',
      text: `${parsedTextLength} 字`,
      type: 'info'
    })
  }
  return tags
}

function normalizeMetadataText(value?: string | null) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeParsedTextLength(value?: number | null) {
  const parsed = Number(value || 0)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}

function parseStatusText(status: string) {
  const labels: Record<string, string> = {
    TEXT_EXTRACTED: '已读正文',
    UNPARSED: '未读正文',
    SEEDED: '演示数据'
  }
  return labels[status] || status
}

function formatDateTime(value: string) {
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
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1 class="page-title">企业招聘台</h1>
        <p class="page-subtitle">星河科技 · 校园招聘批次</p>
      </div>
    </header>

    <section v-if="activeModule === 'publish'" class="panel module-panel">
      <h2 class="panel-title">
        发布岗位
        <Plus :size="19" />
      </h2>
      <el-form label-position="top">
        <el-form-item label="岗位名称">
          <el-input v-model="form.title" />
        </el-form-item>
        <div class="grid two">
          <el-form-item label="城市">
            <el-input v-model="form.city" />
          </el-form-item>
          <el-form-item label="薪资">
            <el-input v-model="form.salaryRange" />
          </el-form-item>
        </div>
        <el-form-item label="技能要求">
          <el-input v-model="form.requiredSkills" />
        </el-form-item>
        <el-form-item label="岗位描述">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-button type="primary" @click="publish">发布</el-button>
      </el-form>
    </section>

    <div v-if="activeModule === 'jobs'" class="module-stack">
      <section class="panel">
        <h2 class="panel-title">
          候选人匹配
          <ClipboardList :size="19" />
        </h2>
        <div v-if="candidate" class="grid two" style="align-items: center">
          <div class="score">{{ candidate.score }}</div>
          <div>
            <strong>候选人 {{ candidate.studentId }}</strong>
            <div v-if="hasResumeParseMetadata(candidate)" class="resume-parse-tags">
              <el-tag
                v-for="tag in resumeParseTags(candidate)"
                :key="tag.key"
                size="small"
                :type="tag.type"
              >
                {{ tag.text }}
              </el-tag>
            </div>
            <ul class="plain-list">
              <li v-for="item in candidate.strengths" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>
        <el-empty v-else description="请选择岗位查看候选人" />
      </section>

      <section class="panel">
        <h2 class="panel-title">岗位列表</h2>
        <div class="grid two">
          <article v-for="job in jobs" :key="job.jobId" class="item-card">
            <strong>{{ job.title }}</strong>
            <span>{{ job.city }} · {{ job.salaryRange }}</span>
            <div class="tag-row">
              <el-tag v-for="skill in job.requiredSkills" :key="skill">{{ skill }}</el-tag>
            </div>
            <p>{{ job.aiSummary }}</p>
            <div class="actions">
              <el-button @click="loadCandidate(job.jobId)">候选人</el-button>
              <el-button type="primary" @click="runAnalyze(job.jobId)">
                <Bot :size="17" />
                分析
              </el-button>
            </div>
          </article>
        </div>
      </section>
    </div>

    <section v-if="activeModule === 'deliveries'" class="panel module-panel">
      <h2 class="panel-title">
        投递审核
        <ClipboardList :size="19" />
      </h2>
      <el-table class="company-delivery-table" :data="deliveries" style="width: 100%">
        <el-table-column prop="deliveryId" label="编号" min-width="96" />
        <el-table-column prop="studentId" label="学生" min-width="82" />
        <el-table-column prop="jobId" label="岗位" min-width="82" />
        <el-table-column label="状态" min-width="96">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="hasAnyDeliveryParseMetadata" label="简历解析" min-width="156">
          <template #default="{ row }">
            <div v-if="hasResumeParseMetadata(row)" class="resume-parse-tags">
              <el-tag
                v-for="tag in resumeParseTags(row)"
                :key="tag.key"
                size="small"
                :type="tag.type"
              >
                {{ tag.text }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="320">
          <template #default="{ row }">
            <div class="actions">
              <el-button
                size="small"
                type="primary"
                :loading="screeningLoading[row.deliveryId]"
                @click="runCandidateScreen(row)"
              >
                <Bot :size="15" />
                异步初筛
              </el-button>
              <el-button
                v-for="item in reviewStatuses"
                :key="item.status"
                size="small"
                :plain="row.status !== item.status"
                :type="statusTagType(item.status)"
                @click="changeDeliveryStatus(row, item.status)"
              >
                {{ item.label }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="delivery-review-cards">
        <article v-for="delivery in deliveries" :key="delivery.deliveryId" class="delivery-review-card">
          <div class="delivery-review-head">
            <div class="screening-main">
              <strong>{{ delivery.deliveryId }}</strong>
              <span>{{ delivery.studentId }} · {{ delivery.jobId }}</span>
            </div>
            <el-tag class="delivery-status-tag" :type="statusTagType(delivery.status)">{{ statusText(delivery.status) }}</el-tag>
          </div>
          <div v-if="hasResumeParseMetadata(delivery)" class="resume-parse-tags">
            <el-tag
              v-for="tag in resumeParseTags(delivery)"
              :key="tag.key"
              size="small"
              :type="tag.type"
            >
              {{ tag.text }}
            </el-tag>
          </div>
          <div class="actions delivery-review-actions">
            <el-button
              size="small"
              type="primary"
              :loading="screeningLoading[delivery.deliveryId]"
              @click="runCandidateScreen(delivery)"
            >
              <Bot :size="15" />
              异步初筛
            </el-button>
            <el-button
              v-for="item in reviewStatuses"
              :key="item.status"
              size="small"
              :plain="delivery.status !== item.status"
              :type="statusTagType(item.status)"
              @click="changeDeliveryStatus(delivery, item.status)"
            >
              {{ item.label }}
            </el-button>
          </div>
        </article>
      </div>
    </section>

    <section v-if="activeModule === 'screening'" class="panel module-panel">
      <h2 class="panel-title">
        AI 异步初筛任务
        <span class="panel-title-actions">
          <el-button
            circle
            size="small"
            :loading="historyRefreshing"
            @click="manualRefreshCandidateScreenRecords"
          >
            <RefreshCw :size="15" />
          </el-button>
          <Bot :size="19" />
        </span>
      </h2>
      <el-empty v-if="screeningCards.length === 0" description="点击投递记录中的异步初筛创建任务" />
      <div v-else class="screening-grid">
        <article v-for="task in screeningCards" :key="task.taskId" class="item-card screening-card">
          <div class="screening-head">
            <span class="screening-score">{{ task.result?.score ?? '--' }}</span>
            <div class="screening-main">
              <div class="screening-title-row">
                <strong>{{ task.result?.recommendation || task.message || task.taskId }}</strong>
                <el-tag size="small" :type="taskStatusTagType(task.status)">
                  {{ taskStatusText(task.status) }}
                </el-tag>
              </div>
              <div class="screening-meta">
                <span>{{ task.taskId }}</span>
                <span>{{ task.deliveryId }}</span>
                <span>{{ task.studentId }}</span>
                <span>{{ task.jobId }}</span>
                <span>{{ task.source }}</span>
                <span>{{ formatDateTime(task.updatedAt || task.createdAt) }}</span>
              </div>
              <div v-if="hasResumeParseMetadata(task.result || task)" class="resume-parse-tags">
                <el-tag
                  v-for="tag in resumeParseTags(task.result || task)"
                  :key="tag.key"
                  size="small"
                  :type="tag.type"
                >
                  {{ tag.text }}
                </el-tag>
              </div>
              <div class="screening-task-actions">
                <el-button
                  size="small"
                  :loading="taskActionLoading[task.taskId]"
                  @click="refreshCandidateScreenTask(task)"
                >
                  刷新
                </el-button>
                <el-button
                  v-if="task.status === 'FAILED'"
                  size="small"
                  type="primary"
                  :loading="taskActionLoading[task.taskId]"
                  @click="retryCandidateScreen(task)"
                >
                  重试
                </el-button>
              </div>
            </div>
          </div>
          <p v-if="task.status !== 'COMPLETED' || !task.result" class="screening-task-message">
            {{ task.message || '异步初筛任务已进入队列，刷新后查看最新状态。' }}
          </p>
          <div v-else class="screening-columns">
            <div>
              <strong>优势</strong>
              <ul class="plain-list">
                <li v-for="item in task.result.strengths" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div>
              <strong>风险</strong>
              <ul class="plain-list">
                <li v-for="item in task.result.risks" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div>
              <strong>面试追问</strong>
              <ul class="plain-list">
                <li v-for="item in task.result.interviewQuestions" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div>
              <strong>下一步</strong>
              <ul class="plain-list">
                <li v-for="item in task.result.nextActions" :key="item">{{ item }}</li>
              </ul>
            </div>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.company-delivery-table {
  min-width: 0;
}

.panel-title-actions {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

:deep(.company-delivery-table .cell) {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.resume-parse-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
  margin-top: 6px;
}

.company-delivery-table .resume-parse-tags {
  margin-top: 0;
}

.screening-grid {
  display: grid;
  gap: 14px;
}

.delivery-review-cards {
  display: none;
}

.delivery-review-card {
  display: grid;
  gap: 12px;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding: 12px;
  overflow: hidden;
  border: 1px solid #dde5ed;
  border-radius: 8px;
  background: #ffffff;
}

.delivery-review-head {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  align-items: flex-start;
}

.delivery-review-head strong,
.delivery-review-head span {
  display: block;
}

.delivery-review-head span {
  margin-top: 2px;
  color: #667085;
  font-size: 13px;
}

.delivery-status-tag {
  justify-self: flex-start;
  max-width: 100%;
}

.delivery-review-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.delivery-review-actions :deep(.el-button) {
  justify-content: center;
  width: 100%;
  min-width: 0;
  margin-left: 0 !important;
  padding-right: 8px;
  padding-left: 8px;
}

.screening-card {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.screening-head {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 12px;
  align-items: flex-start;
  min-width: 0;
}

.screening-head strong,
.screening-head span {
  display: block;
}

.screening-main {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.screening-title-row {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.screening-title-row strong {
  flex: 1 1 160px;
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
  line-height: 1.35;
}

.screening-head > span {
  margin-top: 2px;
  color: #667085;
  font-size: 13px;
}

.screening-meta {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin-top: 4px;
  color: #667085;
  font-size: 13px;
}

.screening-meta span {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.screening-task-message {
  margin: 0;
  color: #667085;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.screening-task-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.screening-task-actions :deep(.el-button) {
  margin-left: 0 !important;
}

.screening-score {
  display: grid;
  width: 48px;
  height: 42px;
  max-width: 100%;
  place-items: center;
  border-radius: 8px;
  background: #e6f2ef;
  color: #0f766e;
  font-size: 18px;
  font-weight: 800;
}

.screening-columns {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.screening-columns > div {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.screening-columns strong {
  display: block;
  margin-bottom: 8px;
}

@media (max-width: 920px) {
  .screening-columns {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .company-delivery-table {
    display: none;
  }

  .delivery-review-cards {
    display: grid;
    gap: 10px;
  }

  .screening-columns {
    grid-template-columns: 1fr;
  }

  .screening-head {
    grid-template-columns: 40px minmax(0, 1fr);
    gap: 10px;
  }

  .screening-score {
    width: 40px;
    height: 38px;
    font-size: 15px;
  }

  .screening-title-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .screening-title-row strong {
    flex-basis: auto;
    width: 100%;
    font-size: 15px;
  }
}
</style>
