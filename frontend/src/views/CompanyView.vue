<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bot, ClipboardList, Plus } from 'lucide-vue-next'
import {
  analyzeJob,
  createJob,
  listCompanyDeliveries,
  listJobs,
  matchResumeJob,
  updateDeliveryStatus,
  type DeliveryRecord,
  type DeliveryStatus,
  type JobSummary,
  type MatchResult
} from '../api/client'

const jobs = ref<JobSummary[]>([])
const candidate = ref<MatchResult>()
const deliveries = ref<DeliveryRecord[]>([])
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

onMounted(async () => {
  jobs.value = await listJobs()
  deliveries.value = await listCompanyDeliveries('C001')
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
      <el-table :data="deliveries" style="width: 100%">
        <el-table-column prop="deliveryId" label="编号" width="110" />
        <el-table-column prop="studentId" label="学生" width="110" />
        <el-table-column prop="jobId" label="岗位" width="110" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="推进状态" min-width="320">
          <template #default="{ row }">
            <div class="actions">
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
    </section>
  </section>
</template>
