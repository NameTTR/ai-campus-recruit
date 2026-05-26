<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bot, ClipboardList, Plus } from 'lucide-vue-next'
import { analyzeJob, createJob, listJobs, matchResumeJob, type JobSummary, type MatchResult } from '../api/client'

const jobs = ref<JobSummary[]>([])
const candidate = ref<MatchResult>()
const form = reactive({
  title: 'Java 后端实习生',
  city: '杭州',
  salaryRange: '180-260/天',
  requiredSkills: 'Java,Spring Boot,MySQL,Redis',
  description: '参与招聘平台、数据看板和中台接口开发。'
})

onMounted(async () => {
  jobs.value = await listJobs()
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
  </section>
</template>

