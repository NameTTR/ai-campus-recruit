<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { Bot, ClipboardList, Plus, RefreshCw } from 'lucide-vue-next'
import {
  analyzeJob,
  createJob,
  listCandidateScreenRecords,
  listCompanyDeliveries,
  listJobs,
  matchResumeJob,
  screenCandidate,
  updateDeliveryStatus,
  type CandidateScreenRecord,
  type CandidateScreenResult,
  type DeliveryRecord,
  type DeliveryStatus,
  type JobSummary,
  type MatchResult
} from '../api/client'

const jobs = ref<JobSummary[]>([])
const candidate = ref<MatchResult>()
const deliveries = ref<DeliveryRecord[]>([])
const screeningRecords = ref<CandidateScreenRecord[]>([])
const screeningLoading = ref<Record<string, boolean>>({})
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
const screeningCards = computed(() => [...screeningRecords.value]
  .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()))

onMounted(async () => {
  const [jobList, deliveryList, screeningList] = await Promise.all([
    listJobs(),
    listCompanyDeliveries('C001'),
    listCandidateScreenRecords('C001')
  ])
  jobs.value = jobList
  deliveries.value = deliveryList
  screeningRecords.value = screeningList
})

async function publish() {
  const job = await createJob({
    companyId: 'C001',
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
  screeningLoading.value = { ...screeningLoading.value, [delivery.deliveryId]: true }
  try {
    const result = await screenCandidate({
      deliveryId: delivery.deliveryId,
      studentId: delivery.studentId,
      resumeId: delivery.resumeId,
      jobId: delivery.jobId,
      companyId: delivery.companyId,
      targetRole: job?.title || 'Java 后端实习生',
      skills: ['Java', 'Spring Boot', 'MySQL', 'Redis', 'Docker'],
      projects: ['校园二手交易系统', '在线考试平台'],
      jobRequirements: job?.requiredSkills || ['Java', 'Spring Boot', 'MySQL', 'Redis'],
      resumeSummary: '软件工程本科，具备 Java Web 项目、数据库设计和缓存实践经历。',
      jobDescription: job?.description || '参与招聘平台、数据看板和中台接口开发。'
    })
    const localRecord = upsertLocalScreeningRecord(delivery, result)
    await refreshCandidateScreenRecords(localRecord, requestedAt)
    ElMessage.success('AI 筛选建议已生成')
  } finally {
    screeningLoading.value = { ...screeningLoading.value, [delivery.deliveryId]: false }
  }
}

async function refreshCandidateScreenRecords(localRecord?: CandidateScreenRecord, requestedAt = 0) {
  const records = await listCandidateScreenRecords('C001')
  if (!localRecord) {
    screeningRecords.value = records
    return
  }
  const hasFreshServerRecord = records.some((record) =>
    record.deliveryId === localRecord.deliveryId && new Date(record.createdAt).getTime() >= requestedAt - 10_000)
  screeningRecords.value = hasFreshServerRecord ? records : [localRecord, ...records]
}

async function manualRefreshCandidateScreenRecords() {
  historyRefreshing.value = true
  try {
    await refreshCandidateScreenRecords()
    ElMessage.success('筛选历史已刷新')
  } finally {
    historyRefreshing.value = false
  }
}

function upsertLocalScreeningRecord(delivery: DeliveryRecord, result: CandidateScreenResult) {
  const record: CandidateScreenRecord = {
    screeningId: `CS-LOCAL-${Date.now()}`,
    companyId: delivery.companyId,
    ...result,
    createdAt: new Date().toISOString()
  }
  screeningRecords.value = [
    record,
    ...screeningRecords.value.filter((item) => item.screeningId !== record.screeningId)
  ]
  return record
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

    <div class="grid two">
      <section class="panel">
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

      <section class="panel">
        <h2 class="panel-title">
          候选人匹配
          <ClipboardList :size="19" />
        </h2>
        <div v-if="candidate" class="grid two" style="align-items: center">
          <div class="score">{{ candidate.score }}</div>
          <div>
            <strong>候选人 S001</strong>
            <ul class="plain-list">
              <li v-for="item in candidate.strengths" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>
        <el-empty v-else description="请选择岗位查看候选人" />
      </section>
    </div>

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

    <section class="panel">
      <h2 class="panel-title">
        投递审核
        <ClipboardList :size="19" />
      </h2>
      <el-table class="company-delivery-table" :data="deliveries" style="width: 100%">
        <el-table-column prop="deliveryId" label="编号" width="110" />
        <el-table-column prop="studentId" label="学生" width="110" />
        <el-table-column prop="jobId" label="岗位" width="110" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="430">
          <template #default="{ row }">
            <div class="actions">
              <el-button
                size="small"
                type="primary"
                :loading="screeningLoading[row.deliveryId]"
                @click="runCandidateScreen(row)"
              >
                <Bot :size="15" />
                AI 筛选
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
            <div>
              <strong>{{ delivery.deliveryId }}</strong>
              <span>{{ delivery.studentId }} · {{ delivery.jobId }}</span>
            </div>
            <el-tag :type="statusTagType(delivery.status)">{{ statusText(delivery.status) }}</el-tag>
          </div>
          <div class="actions">
            <el-button
              size="small"
              type="primary"
              :loading="screeningLoading[delivery.deliveryId]"
              @click="runCandidateScreen(delivery)"
            >
              <Bot :size="15" />
              AI 筛选
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

    <section class="panel">
      <h2 class="panel-title">
        AI 候选人筛选历史
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
      <el-empty v-if="screeningCards.length === 0" description="点击投递记录中的 AI 筛选生成历史记录" />
      <div v-else class="screening-grid">
        <article v-for="result in screeningCards" :key="result.screeningId" class="item-card screening-card">
          <div class="screening-head">
            <span class="screening-score">{{ result.score }}</span>
            <div>
              <strong>{{ result.recommendation }}</strong>
              <span>
                {{ result.deliveryId }} · {{ result.studentId }} · {{ result.jobId }} ·
                {{ result.mocked ? '演示' : '真实 AI' }} · {{ formatDateTime(result.createdAt) }}
              </span>
            </div>
          </div>
          <div class="screening-columns">
            <div>
              <strong>优势</strong>
              <ul class="plain-list">
                <li v-for="item in result.strengths" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div>
              <strong>风险</strong>
              <ul class="plain-list">
                <li v-for="item in result.risks" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div>
              <strong>面试追问</strong>
              <ul class="plain-list">
                <li v-for="item in result.interviewQuestions" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div>
              <strong>下一步</strong>
              <ul class="plain-list">
                <li v-for="item in result.nextActions" :key="item">{{ item }}</li>
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
  padding: 12px;
  border: 1px solid #dde5ed;
  border-radius: 8px;
  background: #ffffff;
}

.delivery-review-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
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

.screening-card {
  min-width: 0;
}

.screening-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.screening-head strong,
.screening-head span {
  display: block;
}

.screening-head span {
  margin-top: 2px;
  color: #667085;
  font-size: 13px;
}

.screening-score {
  display: grid;
  width: 48px;
  height: 42px;
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
}
</style>
