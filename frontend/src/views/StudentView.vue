<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Brain, BriefcaseBusiness, FileUp, Send } from 'lucide-vue-next'
import {
  analyzeResume,
  createDelivery,
  getProfile,
  listDeliveries,
  listJobs,
  matchResumeJob,
  uploadResume,
  type DeliveryRecord,
  type DeliveryStatus,
  type JobSummary,
  type MatchResult,
  type ResumeSummary,
  type UserProfile
} from '../api/client'

const profile = ref<UserProfile>()
const resume = ref<ResumeSummary>()
const jobs = ref<JobSummary[]>([])
const match = ref<MatchResult>()
const deliveries = ref<DeliveryRecord[]>([])
const selectedFile = ref<File>()

onMounted(async () => {
  profile.value = await getProfile()
  jobs.value = await listJobs()
  deliveries.value = await listDeliveries()
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
  ElMessage.success('匹配结果已生成')
}

async function deliver(jobId: string) {
  const record = await createDelivery(resume.value?.resumeId || 'R001', jobId)
  deliveries.value = [record, ...deliveries.value]
  ElMessage.success('投递成功')
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
        <h1 class="page-title">学生求职台</h1>
        <p class="page-subtitle">{{ profile?.school }} · {{ profile?.major }} · {{ profile?.targetPosition }}</p>
      </div>
      <div class="tag-row">
        <el-tag v-for="skill in profile?.skills" :key="skill">{{ skill }}</el-tag>
      </div>
    </header>

    <div class="grid two">
      <section class="panel">
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
          <strong>{{ resume.fileName }}</strong>
          <span>{{ resume.education }}</span>
          <div class="tag-row">
            <el-tag v-for="skill in resume.skills" :key="skill" type="success">{{ skill }}</el-tag>
          </div>
          <p>{{ resume.diagnosis }}</p>
        </div>
      </section>

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
    </div>

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

    <section class="panel">
      <h2 class="panel-title">投递记录</h2>
      <el-table :data="deliveries" style="width: 100%">
        <el-table-column prop="deliveryId" label="编号" width="120" />
        <el-table-column prop="jobId" label="岗位" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" />
      </el-table>
    </section>
  </section>
</template>
