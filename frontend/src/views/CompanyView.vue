<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { BriefcaseBusiness, CheckCircle2, CircleX, FilePenLine, MapPin, Plus, RefreshCw, Save } from 'lucide-vue-next'
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
const openJobs = computed(() => ownJobs.value.filter((job) => job.status === 'OPEN'))
const closedJobs = computed(() => ownJobs.value.filter((job) => job.status === 'CLOSED'))
const requiredSkillCount = computed(() => ownJobs.value.reduce((count, job) => count + job.requiredSkills.length, 0))

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
  <section class="page company-page">
    <header class="dashboard-header">
      <div class="title-block">
        <h1 class="page-title">{{ pageTitle }}</h1>
      </div>
      <el-button v-if="activeModule === 'jobs'" type="primary" class="primary-command" @click="router.push('/company/publish')">
        <Plus :size="16" />发布岗位
      </el-button>
    </header>

    <template v-if="activeModule === 'publish'">
      <section class="panel module-panel job-editor">
        <header class="section-heading">
          <h2>{{ editingJobId ? '编辑岗位' : '岗位信息' }}</h2>
        </header>
        <div class="job-form-grid">
          <label class="field"><span>岗位名称 <b>*</b></span><el-input v-model="form.title" placeholder="例如：Java 后端实习生" /></label>
          <label class="field"><span>工作城市</span><el-input v-model="form.city" placeholder="例如：上海" /></label>
          <label class="field"><span>薪资范围</span><el-input v-model="form.salaryRange" placeholder="例如：5K - 8K" /></label>
          <label class="field"><span>技能要求</span><el-input v-model="form.requiredSkills" placeholder="Java, Spring Boot, MySQL" /></label>
          <label class="field field-wide"><span>岗位职责与任职要求 <b>*</b></span><el-input v-model="form.description" class="job-description" type="textarea" :rows="8" placeholder="说明岗位职责、任职要求与工作内容" /></label>
        </div>
        <div class="actions editor-actions">
          <el-button type="primary" :loading="submitting" @click="submitJob"><component :is="editingJobId ? Save : Plus" :size="15" />{{ editingJobId ? '保存岗位' : '发布岗位' }}</el-button>
          <el-button @click="resetForm(); router.push('/company/jobs')">取消</el-button>
        </div>
      </section>
    </template>

    <template v-else>
      <section class="summary-row metric-grid" aria-label="岗位数据概览">
        <div><span>全部岗位</span><strong>{{ ownJobs.length }}</strong></div>
        <div><span>招聘中</span><strong class="status-number">{{ openJobs.length }}</strong></div>
        <div><span>已关闭</span><strong>{{ closedJobs.length }}</strong></div>
        <div><span>技能要求</span><strong>{{ requiredSkillCount }}</strong></div>
      </section>

      <section class="panel module-panel jobs-panel" v-loading="loading">
        <header class="section-heading">
          <h2>我的岗位 <span class="section-count">{{ ownJobs.length }}</span></h2>
          <div class="heading-actions"><el-button circle size="small" title="刷新岗位" aria-label="刷新岗位" @click="loadJobs"><RefreshCw :size="15" /></el-button></div>
        </header>
        <el-empty v-if="!ownJobs.length" description="暂无岗位，发布后将显示在这里" />
        <div v-else class="job-list">
          <div class="job-list-head"><span>岗位信息</span><span>技能要求</span><span>状态</span><span>操作</span></div>
          <article v-for="job in ownJobs" :key="job.jobId" class="job-row">
            <div class="job-identity"><span class="job-avatar"><BriefcaseBusiness :size="17" /></span><div><strong>{{ job.title }}</strong><span><MapPin :size="13" />{{ job.city }} · {{ job.salaryRange }}</span><p>{{ job.description }}</p></div></div>
            <div class="tag-row"><el-tag v-for="skill in job.requiredSkills" :key="skill" type="info">{{ skill }}</el-tag><span v-if="!job.requiredSkills.length" class="muted-copy">未填写</span></div>
            <div><el-tag :type="job.status === 'CLOSED' ? 'info' : 'primary'" effect="light">{{ job.status === 'CLOSED' ? '已关闭' : '招聘中' }}</el-tag></div>
            <div class="actions compact-actions"><el-button size="small" title="编辑岗位" aria-label="编辑岗位" @click="openEdit(job)"><FilePenLine :size="14" /></el-button><el-button size="small" :loading="submitting" @click="changeStatus(job, job.status === 'CLOSED' ? 'OPEN' : 'CLOSED')"><component :is="job.status === 'CLOSED' ? CheckCircle2 : CircleX" :size="14" />{{ job.status === 'CLOSED' ? '启用' : '关闭' }}</el-button></div>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.company-page { display: grid; gap: 16px; min-width: 0; color: var(--ink, #25302b); font-size: 14px; letter-spacing: 0; }
.dashboard-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.title-block { min-width: 0; }
.page-title { margin: 0; font-size: 22px; font-weight: 650; line-height: 1.4; }
.primary-command { min-height: 34px; }
.summary-row { display: flex; flex-wrap: wrap; gap: 12px 26px; padding: 12px 0; border-top: 1px solid var(--line, #dce3df); border-bottom: 1px solid var(--line, #dce3df); }
.summary-row > div { display: flex; align-items: baseline; gap: 10px; color: var(--muted, #59665e); font-size: 13px; }
.summary-row strong { color: var(--ink, #25302b); font: 600 18px/1.3 'Consolas', monospace; }
.summary-row .status-number { color: #2563a6; }
.company-page .panel { border: 0; border-top: 1px solid var(--line, #dce3df); border-radius: 0; box-shadow: none; background: var(--surface, #fff); }
.module-panel { padding: 18px 20px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.section-heading h2 { margin: 0; font-size: 16px; line-height: 1.4; font-weight: 650; }
.section-count { margin-left: 6px; color: var(--muted, #59665e); font: 500 13px/1 'Consolas', monospace; }
.heading-actions, .actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.actions :deep(.el-button + .el-button) { margin-left: 0; }
.job-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.field { display: grid; min-width: 0; gap: 7px; font-size: 13px; font-weight: 600; }
.field b { color: #a23a32; }
.field-wide { grid-column: 1 / -1; }
.editor-actions { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--line, #dce3df); }
.job-list { display: grid; min-width: 0; }
.job-list-head, .job-row { display: grid; grid-template-columns: minmax(220px, 1.4fr) minmax(130px, 1fr) 80px 112px; gap: 18px; align-items: center; }
.job-list-head { padding: 10px 0; border-top: 1px solid var(--line, #dce3df); color: var(--muted, #59665e); font-size: 13px; }
.job-row { padding: 16px 0; border-top: 1px solid var(--line, #dce3df); }
.job-row:hover { background: #f8faf9; }
.job-identity { display: flex; align-items: flex-start; min-width: 0; gap: 10px; }
.job-identity > div { min-width: 0; }
.job-avatar { display: grid; place-items: center; width: 30px; height: 30px; flex: 0 0 30px; color: #496157; border: 1px solid var(--line, #dce3df); border-radius: 4px; }
.job-identity strong { display: block; overflow-wrap: anywhere; font-size: 14px; font-weight: 600; }
.job-identity span { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; margin-top: 5px; overflow-wrap: anywhere; color: var(--muted, #59665e); font-size: 13px; }
.job-identity p { display: -webkit-box; margin: 6px 0 0; overflow: hidden; overflow-wrap: anywhere; color: var(--muted, #59665e); font-size: 13px; line-height: 1.6; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.tag-row { display: flex; flex-wrap: wrap; gap: 5px; min-width: 0; }
.tag-row :deep(.el-tag) { display: inline-flex; align-items: center; max-width: 100%; height: auto; min-height: 24px; white-space: normal; border-radius: 3px; }
.tag-row :deep(.el-tag__content) { overflow-wrap: anywhere; word-break: break-word; white-space: normal; }
.muted-copy { color: var(--muted, #59665e); font-size: 13px; }
.compact-actions { flex-wrap: nowrap; margin: 0; }
@media (max-width: 1120px) {
  .job-list-head { display: none; }
  .job-row { grid-template-columns: minmax(0, 1fr) 112px; gap: 12px; }
  .job-row > .job-identity { grid-column: 1 / -1; }
  .job-row > .tag-row { grid-column: 1 / -1; }
}
@media (max-width: 640px) {
  .module-panel { padding: 16px 12px; }
  .job-form-grid { grid-template-columns: minmax(0, 1fr); gap: 14px; }
  .field-wide { grid-column: auto; }
  .summary-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .summary-row > div { gap: 8px; }
  .job-row { gap: 10px; }
  .page-title { font-size: 20px; }
}
</style>
