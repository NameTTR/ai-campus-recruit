<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { BriefcaseBusiness, FilePenLine, Plus, RefreshCw } from 'lucide-vue-next'
import {
  createJob,
  currentCompanyId,
  getAuthSession,
  listJobs,
  updateJob,
  updateJobStatus,
  type JobSummary
} from '../api/client'

const route = useRoute()
const router = useRouter()
const activeModule = computed(() => typeof route.params.module === 'string' ? route.params.module : 'jobs')
const jobs = ref<JobSummary[]>([])
const loading = ref(false)
const submitting = ref(false)
const editingJobId = ref('')
const form = reactive({
  title: '',
  city: '',
  salaryRange: '',
  requiredSkills: '',
  description: ''
})

const pageTitle = computed(() => activeModule.value === 'publish' ? '发布岗位' : '岗位管理')
const ownJobs = computed(() => jobs.value.filter((job) => job.companyId === currentCompanyId()))

function splitSkills(value: string) {
  return value.split(/[\n,，]/).map((item) => item.trim()).filter(Boolean)
}

function resetForm() {
  editingJobId.value = ''
  form.title = ''
  form.city = ''
  form.salaryRange = ''
  form.requiredSkills = ''
  form.description = ''
}

function openEdit(job: JobSummary) {
  editingJobId.value = job.jobId
  form.title = job.title
  form.city = job.city
  form.salaryRange = job.salaryRange
  form.requiredSkills = job.requiredSkills.join(', ')
  form.description = job.description
  router.push('/company/publish')
}

async function loadJobs() {
  loading.value = true
  try {
    jobs.value = await listJobs()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '岗位列表加载失败')
  } finally {
    loading.value = false
  }
}

async function submitJob() {
  const title = form.title.trim()
  const description = form.description.trim()
  if (!title || !description) {
    ElMessage.warning('请填写岗位名称和岗位描述')
    return
  }
  submitting.value = true
  try {
    const payload = {
      companyId: currentCompanyId(),
      companyName: getAuthSession()?.displayName || '企业账号',
      title,
      city: form.city.trim() || '待定',
      salaryRange: form.salaryRange.trim() || '面议',
      requiredSkills: splitSkills(form.requiredSkills),
      description,
      aiSummary: '',
      status: 'OPEN'
    }
    const job = editingJobId.value
      ? await updateJob(editingJobId.value, payload)
      : await createJob(payload)
    jobs.value = [job, ...jobs.value.filter((item) => item.jobId !== job.jobId)]
    ElMessage.success(editingJobId.value ? '岗位已更新' : '岗位已发布')
    resetForm()
    router.push('/company/jobs')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '岗位保存失败')
  } finally {
    submitting.value = false
  }
}

async function changeStatus(job: JobSummary, status: string) {
  submitting.value = true
  try {
    const updated = await updateJobStatus(job.jobId, status)
    jobs.value = jobs.value.map((item) => item.jobId === updated.jobId ? updated : item)
    ElMessage.success(status === 'OPEN' ? '岗位已启用' : '岗位已关闭')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '岗位状态更新失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => { void loadJobs() })
watch(activeModule, () => { void loadJobs() })
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1 class="page-title">{{ pageTitle }}</h1>
        <p class="page-subtitle">维护企业岗位信息，学生侧会依据已发布岗位进行匹配</p>
      </div>
      <el-button v-if="activeModule === 'jobs'" type="primary" @click="router.push('/company/publish')"><Plus :size="16" />发布岗位</el-button>
    </header>

    <section v-if="activeModule === 'publish'" class="panel module-panel">
      <h2 class="panel-title"><span>{{ editingJobId ? '编辑岗位' : '岗位信息' }}</span><FilePenLine :size="19" /></h2>
      <div class="job-form-grid">
        <el-input v-model="form.title" placeholder="岗位名称" />
        <el-input v-model="form.city" placeholder="工作城市" />
        <el-input v-model="form.salaryRange" placeholder="薪资范围" />
        <el-input v-model="form.requiredSkills" placeholder="技能要求，使用逗号分隔" />
        <el-input v-model="form.description" class="job-description" type="textarea" :rows="8" placeholder="岗位职责、任职要求与工作内容" />
      </div>
      <div class="actions">
        <el-button type="primary" :loading="submitting" @click="submitJob">{{ editingJobId ? '保存岗位' : '发布岗位' }}</el-button>
        <el-button @click="resetForm(); router.push('/company/jobs')">取消</el-button>
      </div>
    </section>

    <section v-else class="panel module-panel" v-loading="loading">
      <h2 class="panel-title"><span>我的岗位</span><span class="panel-title-actions"><el-button circle size="small" @click="loadJobs"><RefreshCw :size="15" /></el-button><BriefcaseBusiness :size="19" /></span></h2>
      <el-empty v-if="!ownJobs.length" description="暂无岗位，发布后将显示在这里" />
      <div v-else class="job-list">
        <article v-for="job in ownJobs" :key="job.jobId" class="item-card job-card">
          <header>
            <div><strong>{{ job.title }}</strong><span>{{ job.city }} · {{ job.salaryRange }}</span></div>
            <el-tag :type="job.status === 'CLOSED' ? 'info' : 'success'">{{ job.status === 'CLOSED' ? '已关闭' : '招聘中' }}</el-tag>
          </header>
          <p>{{ job.description }}</p>
          <div class="tag-row"><el-tag v-for="skill in job.requiredSkills" :key="skill" type="info">{{ skill }}</el-tag></div>
          <div class="actions">
            <el-button size="small" @click="openEdit(job)">编辑</el-button>
            <el-button size="small" :loading="submitting" @click="changeStatus(job, job.status === 'CLOSED' ? 'OPEN' : 'CLOSED')">{{ job.status === 'CLOSED' ? '启用' : '关闭' }}</el-button>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.panel-title-actions,.actions{display:flex;align-items:center;gap:10px}.job-form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.job-description{grid-column:1 / -1}.actions{margin-top:16px}.job-list{display:grid;gap:12px}.job-card header{display:flex;justify-content:space-between;gap:12px}.job-card header span{display:block;margin-top:4px;color:#667085;font-size:13px}.job-card p{margin:0;color:#475467}@media (max-width:720px){.job-form-grid{grid-template-columns:1fr}}
</style>
