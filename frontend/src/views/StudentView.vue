<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { Brain, BriefcaseBusiness, Clock3, FileUp, RefreshCw, Send } from 'lucide-vue-next'
import {
  analyzeResume,
  createDelivery,
  generateInterviewQuestions,
  getAiStatus,
  getProfile,
  getResume,
  listDeliveries,
  listInterviewRecords,
  listJobs,
  matchResumeJob,
  submitInterviewFeedback,
  uploadResume,
  type AiModuleStatus,
  type DeliveryRecord,
  type DeliveryStatus,
  type InterviewFeedback,
  type InterviewQuestion,
  type InterviewRecord,
  type JobSummary,
  type MatchResult,
  type ResumeSummary,
  type UserProfile
} from '../api/client'

const route = useRoute()
const profile = ref<UserProfile>()
const resume = ref<ResumeSummary>()
const jobs = ref<JobSummary[]>([])
const match = ref<MatchResult>()
const deliveries = ref<DeliveryRecord[]>([])
const selectedFile = ref<File>()
const interviewQuestions = ref<InterviewQuestion[]>([])
const selectedQuestionId = ref('')
const interviewAnswer = ref('')
const interviewFeedback = ref<InterviewFeedback>()
const interviewQuestionsLoading = ref(false)
const interviewFeedbackLoading = ref(false)
const aiStatus = ref<AiModuleStatus>()
const aiStatusLoading = ref(false)
const interviewRecords = ref<InterviewRecord[]>([])
const interviewRecordsLoading = ref(false)

const capabilityLabels: Record<string, string> = {
  'resume-analysis': '简历诊断',
  'job-analysis': '岗位分析',
  'match-analysis': '人岗匹配',
  'interview-question-generation': '面试出题',
  'interview-feedback': '回答反馈'
}

const activeModule = computed(() => typeof route.params.module === 'string' ? route.params.module : 'resume')
const hasInterviewContext = computed(() => Boolean(match.value || deliveries.value.length))
const interviewJobId = computed(() => match.value?.jobId || deliveries.value[0]?.jobId || jobs.value[0]?.jobId || 'J001')
const interviewJob = computed(() => jobs.value.find((job) => job.jobId === interviewJobId.value))
const interviewRole = computed(() => interviewJob.value?.title || profile.value?.targetPosition || 'Java 后端实习生')
const selectedQuestion = computed(() => interviewQuestions.value.find((question) => question.questionId === selectedQuestionId.value))
const aiStatusTagType = computed<'success' | 'warning'>(() => (aiStatus.value?.configured ? 'success' : 'warning'))
const aiStatusText = computed(() => (aiStatus.value?.configured ? '真实 AI' : '离线演示'))
const aiProviderText = computed(() => {
  if (!aiStatus.value) {
    return '状态检测中'
  }
  return `${aiStatus.value.provider} · ${aiStatus.value.model}`
})

onMounted(async () => {
  profile.value = await getProfile()
  resume.value = await getResume()
  jobs.value = await listJobs()
  deliveries.value = await listDeliveries()
  await Promise.all([refreshAiStatus(), refreshInterviewRecords()])
})

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0]
}

async function submitResume() {
  if (!selectedFile.value) {
    ElMessage.warning('请选择简历文件')
    return
  }
  resume.value = await uploadResume(selectedFile.value)
  ElMessage.success('简历已上传')
}

async function runAnalyze() {
  resume.value = await analyzeResume(resume.value?.resumeId || 'R001')
  ElMessage.success('诊断已生成')
}

async function runMatch(jobId: string) {
  match.value = await matchResumeJob(resume.value?.resumeId || 'R001', jobId)
  resetInterview()
  ElMessage.success('匹配结果已生成')
}

async function deliver(jobId: string) {
  const record = await createDelivery(resume.value?.resumeId || 'R001', jobId)
  deliveries.value = [record, ...deliveries.value]
  resetInterview()
  ElMessage.success('投递成功')
}

function resetInterview() {
  interviewQuestions.value = []
  selectedQuestionId.value = ''
  interviewAnswer.value = ''
  interviewFeedback.value = undefined
}

function selectInterviewQuestion() {
  interviewAnswer.value = ''
  interviewFeedback.value = undefined
}

async function runInterviewQuestions() {
  if (!hasInterviewContext.value) {
    ElMessage.warning('请先完成岗位匹配或投递')
    return
  }
  interviewQuestionsLoading.value = true
  interviewFeedback.value = undefined
  interviewAnswer.value = ''
  try {
    interviewQuestions.value = await generateInterviewQuestions({
      studentId: profile.value?.userId || 'S001',
      resumeId: resume.value?.resumeId || 'R001',
      jobId: interviewJobId.value,
      targetRole: interviewRole.value,
      skills: resume.value?.skills || profile.value?.skills || []
    })
    selectedQuestionId.value = interviewQuestions.value[0]?.questionId || ''
    ElMessage.success('模拟面试题已生成')
  } finally {
    interviewQuestionsLoading.value = false
  }
}

async function submitInterviewAnswer() {
  if (!selectedQuestion.value) {
    ElMessage.warning('请选择面试题')
    return
  }
  if (!interviewAnswer.value.trim()) {
    ElMessage.warning('请输入回答内容')
    return
  }
  interviewFeedbackLoading.value = true
  try {
    interviewFeedback.value = await submitInterviewFeedback({
      studentId: profile.value?.userId || 'S001',
      questionId: selectedQuestion.value.questionId,
      question: selectedQuestion.value.question,
      answer: interviewAnswer.value.trim(),
      targetRole: interviewRole.value
    })
    await refreshInterviewRecords()
    ElMessage.success('回答反馈已生成')
  } finally {
    interviewFeedbackLoading.value = false
  }
}

async function refreshAiStatus() {
  aiStatusLoading.value = true
  try {
    aiStatus.value = await getAiStatus()
  } finally {
    aiStatusLoading.value = false
  }
}

async function refreshInterviewRecords() {
  interviewRecordsLoading.value = true
  try {
    interviewRecords.value = await listInterviewRecords(profile.value?.userId || 'S001')
  } finally {
    interviewRecordsLoading.value = false
  }
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

function capabilityText(capability: string) {
  return capabilityLabels[capability] || capability
}

function formatTime(value: string) {
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
        <h1 class="page-title">学生求职台</h1>
        <p class="page-subtitle">{{ profile?.school }} · {{ profile?.major }} · {{ profile?.targetPosition }}</p>
      </div>
      <div class="tag-row">
        <el-tag v-for="skill in profile?.skills" :key="skill">{{ skill }}</el-tag>
      </div>
    </header>

    <section v-if="activeModule === 'resume'" class="panel module-panel">
      <h2 class="panel-title">
        简历诊断
        <Brain :size="19" />
      </h2>
      <div class="actions">
        <input type="file" accept=".pdf,.doc,.docx" @change="onFileChange" />
        <el-button type="primary" @click="submitResume">
          <FileUp :size="17" />
          上传
        </el-button>
        <el-button @click="runAnalyze">
          <Brain :size="17" />
          诊断
        </el-button>
      </div>
      <div v-if="resume" class="item-card" style="margin-top: 14px">
        <div class="resume-card-header">
          <strong>{{ resume.fileName }}</strong>
          <el-tag size="small" type="info">{{ resume.storageProvider }} · {{ resume.storageStatus }}</el-tag>
        </div>
        <span>{{ resume.education }}</span>
        <div class="tag-row">
          <el-tag v-for="skill in resume.skills" :key="skill" type="success">{{ skill }}</el-tag>
        </div>
        <p>{{ resume.diagnosis }}</p>
      </div>
    </section>

    <div v-if="activeModule === 'jobs'" class="module-stack">
      <section class="panel">
        <h2 class="panel-title">
          匹配结果
          <BriefcaseBusiness :size="19" />
        </h2>
        <div v-if="match" class="grid two" style="align-items: center">
          <div class="score">{{ match.score }}</div>
          <div>
            <strong>建议</strong>
            <ul class="plain-list">
              <li v-for="item in match.suggestions" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>
        <el-empty v-else description="暂无匹配结果" />
      </section>

      <section class="panel">
        <h2 class="panel-title">推荐岗位</h2>
        <div class="grid two">
          <article v-for="job in jobs" :key="job.jobId" class="item-card">
            <strong>{{ job.title }}</strong>
            <span>{{ job.companyName }} · {{ job.city }} · {{ job.salaryRange }}</span>
            <div class="tag-row">
              <el-tag v-for="skill in job.requiredSkills" :key="skill">{{ skill }}</el-tag>
            </div>
            <p>{{ job.aiSummary }}</p>
            <div class="actions">
              <el-button @click="runMatch(job.jobId)">匹配</el-button>
              <el-button type="primary" @click="deliver(job.jobId)">
                <Send :size="17" />
                投递
              </el-button>
            </div>
          </article>
        </div>
      </section>
    </div>

    <section v-if="activeModule === 'interview'" class="panel interview-panel module-panel">
      <h2 class="panel-title">
        AI 模拟面试
        <Brain :size="19" />
      </h2>
      <div class="ai-status-strip">
        <div class="ai-status-main">
          <span class="status-dot" :class="{ active: aiStatus?.configured }" />
          <div>
            <strong>{{ aiStatusText }}</strong>
            <span>{{ aiProviderText }}</span>
          </div>
        </div>
        <div class="ai-status-meta">
          <el-tag :type="aiStatusTagType">{{ aiStatus?.configured ? '模型在线' : '降级可用' }}</el-tag>
          <el-button size="small" :loading="aiStatusLoading" @click="refreshAiStatus">
            <RefreshCw :size="15" />
            刷新
          </el-button>
        </div>
        <div class="capability-row">
          <el-tag
            v-for="capability in aiStatus?.capabilities || []"
            :key="capability"
            size="small"
            effect="plain"
          >
            {{ capabilityText(capability) }}
          </el-tag>
        </div>
        <p v-if="aiStatus?.fallbackReason" class="fallback-reason">{{ aiStatus.fallbackReason }}</p>
      </div>

      <div class="interview-toolbar">
        <span class="interview-target">目标岗位：{{ interviewRole }}</span>
        <el-button
          type="primary"
          :disabled="!hasInterviewContext"
          :loading="interviewQuestionsLoading"
          @click="runInterviewQuestions"
        >
          <Brain :size="17" />
          生成面试题
        </el-button>
      </div>

      <el-empty v-if="!hasInterviewContext" description="完成岗位匹配或投递后可开始模拟面试" />
      <el-empty v-else-if="interviewQuestions.length === 0" class="compact-empty" description="暂无模拟面试题" />
      <div v-else class="interview-grid">
        <el-radio-group v-model="selectedQuestionId" class="question-options" @change="selectInterviewQuestion">
          <el-radio
            v-for="question in interviewQuestions"
            :key="question.questionId"
            :value="question.questionId"
            class="question-option"
          >
            <span class="question-copy">
              <span class="question-labels">
                <el-tag size="small">{{ question.category }}</el-tag>
                <el-tag size="small" type="info">{{ question.difficulty }}</el-tag>
              </span>
              <strong>{{ question.question }}</strong>
            </span>
          </el-radio>
        </el-radio-group>

        <div v-if="selectedQuestion" class="answer-column">
          <div class="reference-points">
            <strong>答题要点</strong>
            <ul class="plain-list">
              <li v-for="item in selectedQuestion.referencePoints" :key="item">{{ item }}</li>
            </ul>
          </div>
          <el-input
            v-model="interviewAnswer"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="输入你的回答"
          />
          <div class="actions">
            <el-button
              type="primary"
              :disabled="!interviewAnswer.trim()"
              :loading="interviewFeedbackLoading"
              @click="submitInterviewAnswer"
            >
              <Send :size="17" />
              提交回答
            </el-button>
          </div>

          <div v-if="interviewFeedback" class="feedback">
            <div class="feedback-head">
              <div class="feedback-score">{{ interviewFeedback.score }}</div>
              <p>{{ interviewFeedback.summary }}</p>
            </div>
            <div class="feedback-lists">
              <div>
                <strong>优势</strong>
                <ul class="plain-list">
                  <li v-for="item in interviewFeedback.strengths" :key="item">{{ item }}</li>
                </ul>
              </div>
              <div>
                <strong>不足</strong>
                <ul class="plain-list">
                  <li v-for="item in interviewFeedback.gaps" :key="item">{{ item }}</li>
                </ul>
              </div>
              <div>
                <strong>建议</strong>
                <ul class="plain-list">
                  <li v-for="item in interviewFeedback.suggestions" :key="item">{{ item }}</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="activeModule === 'history'" class="panel interview-history-panel module-panel">
      <div class="panel-title history-title">
        <span>
          面试记录
          <Clock3 :size="19" />
        </span>
        <el-button size="small" :loading="interviewRecordsLoading" @click="refreshInterviewRecords">
          <RefreshCw :size="15" />
          刷新
        </el-button>
      </div>
      <el-table
        v-loading="interviewRecordsLoading"
        class="history-table"
        :data="interviewRecords"
        style="width: 100%"
        empty-text="暂无面试记录"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="record-detail">
              <div>
                <strong>题目</strong>
                <p>{{ row.question }}</p>
              </div>
              <div>
                <strong>回答</strong>
                <p>{{ row.answer }}</p>
              </div>
              <div>
                <strong>建议</strong>
                <ul class="plain-list">
                  <li v-for="item in row.suggestions" :key="item">{{ item }}</li>
                </ul>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="108">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="targetRole" label="岗位" min-width="120" />
        <el-table-column label="评分" width="72">
          <template #default="{ row }">
            <span class="history-score">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column label="模式" width="84">
          <template #default="{ row }">
            <el-tag :type="row.mocked ? 'warning' : 'success'">{{ row.mocked ? '演示' : '真实' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="总结" min-width="180" />
      </el-table>
      <div v-loading="interviewRecordsLoading" class="history-cards">
        <el-empty v-if="interviewRecords.length === 0" class="compact-empty" description="暂无面试记录" />
        <article v-for="record in interviewRecords" v-else :key="record.recordId" class="history-card">
          <div class="history-card-head">
            <span class="history-score">{{ record.score }}</span>
            <div>
              <strong>{{ record.targetRole }}</strong>
              <span>{{ formatTime(record.createdAt) }}</span>
            </div>
            <el-tag :type="record.mocked ? 'warning' : 'success'">{{ record.mocked ? '演示' : '真实' }}</el-tag>
          </div>
          <p>{{ record.summary }}</p>
          <details>
            <summary>查看题目与建议</summary>
            <div class="record-detail compact">
              <div>
                <strong>题目</strong>
                <p>{{ record.question }}</p>
              </div>
              <div>
                <strong>回答</strong>
                <p>{{ record.answer }}</p>
              </div>
              <div>
                <strong>建议</strong>
                <ul class="plain-list">
                  <li v-for="item in record.suggestions" :key="item">{{ item }}</li>
                </ul>
              </div>
            </div>
          </details>
        </article>
      </div>
    </section>

    <section v-if="activeModule === 'deliveries'" class="panel module-panel">
      <h2 class="panel-title">投递记录</h2>
      <el-table class="delivery-table" :data="deliveries" style="width: 100%">
        <el-table-column prop="deliveryId" label="编号" width="96" />
        <el-table-column prop="jobId" label="岗位" min-width="90" />
        <el-table-column label="状态" width="104">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" min-width="150" />
      </el-table>
    </section>
  </section>
</template>

<style scoped>
.ai-status-strip {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto;
  gap: 12px 16px;
  align-items: center;
  min-width: 0;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid #dde5ed;
  border-radius: 8px;
  background: #f8fafb;
}

.ai-status-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.ai-status-main strong,
.ai-status-main span {
  display: block;
}

.ai-status-main span {
  margin-top: 2px;
  color: #667085;
  font-size: 13px;
  word-break: break-word;
}

.status-dot {
  flex: 0 0 10px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #d97706;
}

.status-dot.active {
  background: #0f766e;
}

.ai-status-meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
}

.capability-row {
  display: flex;
  grid-column: 1 / -1;
  flex-wrap: wrap;
  gap: 8px;
}

.fallback-reason {
  grid-column: 1 / -1;
  margin: 0;
  color: #b45309;
  font-size: 13px;
}

.interview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-width: 0;
  margin-bottom: 16px;
}

.interview-target {
  color: #475467;
  font-weight: 600;
  word-break: break-word;
}

.interview-grid {
  display: grid;
  grid-template-columns: minmax(220px, 0.9fr) minmax(280px, 1.1fr);
  gap: 18px;
  min-width: 0;
}

.question-options {
  display: grid;
  gap: 10px;
  width: 100%;
  min-width: 0;
  align-content: start;
}

.question-option {
  width: 100%;
  height: auto;
  min-height: 84px;
  min-width: 0;
  margin: 0;
  padding: 12px;
  border: 1px solid #dde5ed;
  border-radius: 8px;
  background: #ffffff;
  align-items: flex-start;
}

.question-option.is-checked {
  border-color: #0f766e;
  background: #f1f8f6;
}

:deep(.question-option .el-radio__input) {
  margin-top: 3px;
}

:deep(.question-option .el-radio__label) {
  display: block;
  width: calc(100% - 24px);
  min-width: 0;
  padding-left: 10px;
  color: #18212f;
  white-space: normal;
}

.question-copy {
  display: grid;
  gap: 8px;
  min-width: 0;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.question-labels {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.answer-column {
  display: grid;
  gap: 14px;
  min-width: 0;
  align-content: start;
}

.reference-points {
  display: grid;
  gap: 8px;
}

.feedback {
  display: grid;
  gap: 16px;
  min-width: 0;
  overflow-wrap: anywhere;
  padding-top: 16px;
  border-top: 1px solid #dde5ed;
}

.feedback-head {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.feedback-head p {
  min-width: 0;
  margin: 0;
  color: #475467;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.feedback-score {
  display: grid;
  flex: 0 0 56px;
  width: 56px;
  height: 56px;
  place-items: center;
  border-radius: 8px;
  background: #e6f2ef;
  color: #0f766e;
  font-size: 22px;
  font-weight: 700;
}

.feedback-lists {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  min-width: 0;
}

.feedback-lists > div {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.feedback-lists strong {
  display: block;
  margin-bottom: 8px;
}

.feedback-lists .plain-list {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.compact-empty {
  --el-empty-padding: 18px 0 22px;
}

.compact-empty :deep(.el-empty__image) {
  width: 112px;
}

.history-title span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.history-score {
  display: inline-grid;
  width: 38px;
  height: 30px;
  place-items: center;
  border-radius: 8px;
  background: #e6f2ef;
  color: #0f766e;
  font-weight: 700;
}

.record-detail {
  display: grid;
  gap: 12px;
  padding: 8px 18px 12px 48px;
}

.record-detail strong {
  display: block;
  margin-bottom: 6px;
}

.record-detail p {
  margin: 0;
  color: #475467;
  line-height: 1.6;
  word-break: break-word;
}

.history-cards {
  display: none;
}

.history-table,
.delivery-table {
  min-width: 0;
}

:deep(.history-table .cell),
:deep(.delivery-table .cell) {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.history-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #dde5ed;
  border-radius: 8px;
  background: #ffffff;
}

.history-card + .history-card {
  margin-top: 10px;
}

.history-card-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.history-card-head strong,
.history-card-head span {
  display: block;
}

.history-card-head span {
  margin-top: 2px;
  color: #667085;
  font-size: 13px;
}

.history-card p {
  margin: 0;
  color: #475467;
  line-height: 1.55;
}

.history-card summary {
  color: #0f766e;
  cursor: pointer;
  font-weight: 600;
}

.record-detail.compact {
  padding: 10px 0 0;
}

@media (max-width: 920px) {
  .ai-status-strip {
    grid-template-columns: 1fr;
  }

  .ai-status-meta {
    justify-content: flex-start;
  }

  .interview-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .interview-grid,
  .feedback-lists {
    grid-template-columns: 1fr;
  }

  .history-table {
    display: none;
  }

  .history-cards {
    display: grid;
    gap: 10px;
  }

  .record-detail {
    padding-left: 12px;
  }
}

@media (max-width: 640px) {
  .ai-status-meta,
  .feedback-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
