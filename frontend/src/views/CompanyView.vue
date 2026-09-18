<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { BriefcaseBusiness, CheckCircle2, CircleX, FilePenLine, MapPin, Plus, RefreshCw, Tags } from 'lucide-vue-next'
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
        <p class="eyebrow">企业招聘工作台</p>
        <h1 class="page-title">{{ pageTitle }}</h1>
        <p class="page-subtitle">维护企业岗位信息，学生侧会依据已发布岗位进行匹配</p>
      </div>
      <el-button v-if="activeModule === 'jobs'" type="primary" class="primary-command" @click="router.push('/company/publish')">
        <Plus :size="16" />发布岗位
      </el-button>
    </header>

    <template v-if="activeModule === 'publish'">
      <section class="publish-context">
        <div><span class="context-label">招聘配置</span><strong>{{ editingJobId ? '正在编辑岗位' : '创建新的招聘岗位' }}</strong></div>
        <span>带 <b>*</b> 的内容将在学生匹配中使用</span>
      </section>
      <section class="panel module-panel job-editor">
        <header class="section-heading">
          <div><p class="section-kicker">岗位资料</p><h2>{{ editingJobId ? '编辑岗位' : '岗位信息' }}</h2></div>
          <span class="heading-icon"><FilePenLine :size="19" /></span>
        </header>
        <div class="job-form-grid">
          <label class="field"><span>岗位名称 <b>*</b></span><el-input v-model="form.title" placeholder="例如：Java 后端实习生" /></label>
          <label class="field"><span>工作城市</span><el-input v-model="form.city" placeholder="例如：上海" /></label>
          <label class="field"><span>薪资范围</span><el-input v-model="form.salaryRange" placeholder="例如：5K - 8K" /></label>
          <label class="field"><span>技能要求</span><el-input v-model="form.requiredSkills" placeholder="使用逗号分隔，例如 Java, Spring Boot" /></label>
          <label class="field field-wide"><span>岗位职责与任职要求 <b>*</b></span><el-input v-model="form.description" class="job-description" type="textarea" :rows="8" placeholder="说明岗位职责、任职要求与工作内容" /></label>
        </div>
        <div class="actions editor-actions">
          <el-button type="primary" :loading="submitting" @click="submitJob">{{ editingJobId ? '保存岗位' : '发布岗位' }}</el-button>
          <el-button @click="resetForm(); router.push('/company/jobs')">取消</el-button>
        </div>
      </section>
    </template>

    <template v-else>
      <section class="metric-grid" aria-label="岗位数据概览">
        <article class="metric-card metric-mint"><span class="metric-icon"><BriefcaseBusiness :size="18" /></span><div><span>岗位总数</span><strong>{{ ownJobs.length }}</strong><small>当前企业已创建的岗位</small></div></article>
        <article class="metric-card metric-lavender"><span class="metric-icon"><CheckCircle2 :size="18" /></span><div><span>招聘中</span><strong>{{ openJobs.length }}</strong><small>当前处于活跃招聘状态</small></div></article>
        <article class="metric-card metric-peach"><span class="metric-icon"><CircleX :size="18" /></span><div><span>已关闭</span><strong>{{ closedJobs.length }}</strong><small>当前暂停招聘的岗位</small></div></article>
        <article class="metric-card metric-blue"><span class="metric-icon"><Tags :size="18" /></span><div><span>要求技能项</span><strong>{{ requiredSkillCount }}</strong><small>岗位技能要求累计项</small></div></article>
      </section>

      <section class="panel module-panel jobs-panel" v-loading="loading">
        <header class="section-heading">
          <div><p class="section-kicker">岗位管理</p><h2>我的岗位</h2><span class="section-note">{{ ownJobs.length }} 个岗位已加载</span></div>
          <div class="heading-actions"><el-button circle size="small" aria-label="刷新岗位" @click="loadJobs"><RefreshCw :size="15" /></el-button><span class="heading-icon"><BriefcaseBusiness :size="19" /></span></div>
        </header>
        <el-empty v-if="!ownJobs.length" description="暂无岗位，发布后将显示在这里" />
        <div v-else class="job-list">
          <div class="job-list-head"><span>岗位信息</span><span>技能要求</span><span>状态</span><span>操作</span></div>
          <article v-for="job in ownJobs" :key="job.jobId" class="job-row">
            <div class="job-identity"><span class="job-avatar"><BriefcaseBusiness :size="17" /></span><div><strong>{{ job.title }}</strong><span><MapPin :size="13" />{{ job.city }} · {{ job.salaryRange }}</span><p>{{ job.description }}</p></div></div>
            <div class="tag-row"><el-tag v-for="skill in job.requiredSkills" :key="skill" type="info">{{ skill }}</el-tag><span v-if="!job.requiredSkills.length" class="muted-copy">未填写</span></div>
            <div><el-tag :type="job.status === 'CLOSED' ? 'info' : 'success'" effect="light">{{ job.status === 'CLOSED' ? '已关闭' : '招聘中' }}</el-tag></div>
            <div class="actions compact-actions"><el-button size="small" @click="openEdit(job)">编辑</el-button><el-button size="small" :loading="submitting" @click="changeStatus(job, job.status === 'CLOSED' ? 'OPEN' : 'CLOSED')">{{ job.status === 'CLOSED' ? '启用' : '关闭' }}</el-button></div>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.company-page{display:grid;gap:20px}.dashboard-header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px}.title-block{min-width:0}.eyebrow,.section-kicker{margin:0 0 7px;color:var(--accent,#28664f);font-size:12px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.page-title{margin:0;color:var(--ink,#1c2420);font-size:28px;line-height:1.15;letter-spacing:0}.page-subtitle{max-width:640px;margin:8px 0 0;color:var(--muted,#667085);font-size:14px;line-height:1.6}.primary-command{min-height:38px}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.metric-card{display:flex;align-items:flex-start;gap:12px;min-height:132px;padding:18px;border:1px solid transparent;border-radius:16px}.metric-card>div{display:grid;gap:3px;min-width:0}.metric-card span:not(.metric-icon),.metric-card small{color:#52645c;font-size:12px;line-height:1.4}.metric-card strong{color:var(--ink,#1c2420);font-size:28px;line-height:1.1}.metric-icon,.heading-icon,.job-avatar{display:grid;flex:0 0 auto;place-items:center;color:var(--accent,#28664f);background:rgba(255,255,255,.72);border:1px solid rgba(30,59,46,.08);border-radius:10px}.metric-icon{width:36px;height:36px}.metric-mint{background:#c8f1df}.metric-lavender{background:#ece7fb}.metric-peach{background:#ffeadf}.metric-blue{background:#e4f0ff}.panel{border-radius:16px!important;background:var(--surface,#fff)}.module-panel{padding:22px}.publish-context{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:0 2px;color:var(--muted,#667085);font-size:13px}.publish-context>div{display:flex;align-items:center;gap:10px;color:var(--ink,#1c2420)}.context-label{padding:4px 8px;border-radius:999px;background:var(--accent-soft,#c8f1df);color:var(--accent,#28664f);font-size:12px;font-weight:700}.publish-context b,.field b{color:#bb553e}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:20px}.section-heading h2{margin:0;color:var(--ink,#1c2420);font-size:18px;line-height:1.35}.section-note{display:block;margin-top:5px;color:var(--muted,#667085);font-size:13px}.heading-actions,.actions{display:flex;align-items:center;gap:8px}.job-form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.field{display:grid;gap:8px;color:var(--ink,#1c2420);font-size:13px;font-weight:700}.field-wide{grid-column:1 / -1}.editor-actions{margin-top:22px;padding-top:18px;border-top:1px solid var(--line,#e5e7eb)}.job-list{display:grid;min-width:0}.job-list-head,.job-row{display:grid;grid-template-columns:minmax(260px,1.4fr) minmax(160px,1fr) 84px 142px;gap:18px;align-items:center}.job-list-head{padding:0 14px 10px;color:var(--muted,#667085);font-size:12px;font-weight:700}.job-row{padding:16px 14px;border-top:1px solid var(--line,#e5e7eb)}.job-row:hover{background:#fbfcfc}.job-identity{display:flex;align-items:flex-start;gap:11px;min-width:0}.job-identity>div{min-width:0}.job-avatar{width:34px;height:34px;background:var(--accent-soft,#c8f1df)}.job-identity strong{display:block;overflow-wrap:anywhere;color:var(--ink,#1c2420);font-size:14px}.job-identity span{display:flex;align-items:center;flex-wrap:wrap;gap:4px;margin-top:5px;overflow-wrap:anywhere;color:var(--muted,#667085);font-size:12px}.job-identity p{display:-webkit-box;margin:6px 0 0;overflow:hidden;overflow-wrap:anywhere;color:#617168;font-size:12px;line-height:1.45;-webkit-box-orient:vertical;-webkit-line-clamp:2}.tag-row{display:flex;flex-wrap:wrap;gap:5px;min-width:0}.tag-row :deep(.el-tag){display:inline-flex;align-items:center;max-width:100%;height:auto;min-height:24px;white-space:normal}.tag-row :deep(.el-tag__content){overflow-wrap:anywhere;word-break:break-word;white-space:normal}.muted-copy{color:var(--muted,#667085);font-size:12px}.compact-actions{margin:0;justify-content:flex-start}.compact-actions :deep(.el-button+.el-button){margin-left:0}@media (max-width:1100px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.job-list-head{display:none}.job-row{grid-template-columns:minmax(0,1fr) auto;gap:14px}.job-row>.tag-row{grid-column:1 / -1}.job-row>.actions{grid-column:2;grid-row:2}.job-row>div:nth-child(3){grid-column:1;grid-row:2}}@media (max-width:640px){.dashboard-header,.publish-context{align-items:flex-start;flex-direction:column}.dashboard-header{gap:14px}.metric-grid{grid-template-columns:1fr 1fr;gap:10px}.metric-card{min-height:126px;padding:14px;gap:9px}.metric-card strong{font-size:24px}.module-panel{padding:16px}.job-form-grid{grid-template-columns:1fr;gap:14px}.field-wide{grid-column:auto}.job-row{grid-template-columns:1fr;padding:14px 0}.job-row>.tag-row,.job-row>.actions,.job-row>div:nth-child(3){grid-column:auto;grid-row:auto}.compact-actions{justify-content:flex-start}.publish-context>div{align-items:flex-start;flex-direction:column;gap:6px}}
</style>
