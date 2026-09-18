<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { KeyRound, Library, Plus, RefreshCw, ShieldCheck, Trash2, Upload, Users } from 'lucide-vue-next'
import {
  batchDeleteKnowledgeDocuments,
  changeAccountPassword,
  createAccount,
  createKnowledgeDocument,
  deleteKnowledgeDocument,
  getKnowledgeBaseStats,
  listAccounts,
  listKnowledgeDocuments,
  listKnowledgeIngestions,
  updateAccountStatus,
  updateKnowledgeDocumentRoles,
  uploadKnowledgeFile,
  type AccountStatus,
  type AccountSummary,
  type KnowledgeBaseStats,
  type KnowledgeDocument,
  type KnowledgeIngestionJob,
  type Role
} from '../api/client'

const route = useRoute()
const activeModule = computed(() => typeof route.params.module === 'string' ? route.params.module : 'ai')
const accounts = ref<AccountSummary[]>([])
const documents = ref<KnowledgeDocument[]>([])
const ingestions = ref<KnowledgeIngestionJob[]>([])
const knowledgeStats = ref<KnowledgeBaseStats>()
const accountsLoading = ref(false)
const knowledgeLoading = ref(false)
const actionLoading = ref(false)
const roleDrafts = ref<Record<string, string>>({})
const selectedDocumentIds = ref<string[]>([])
const fileInput = ref<HTMLInputElement>()
const selectedFile = ref<File | null>(null)

const accountFilters = reactive({ role: '' as Role | '', status: '' as AccountStatus | '', keyword: '' })
const accountForm = reactive({ username: '', password: '123456', displayName: '', role: 'STUDENT' as Role })
const passwordForm = reactive({ accountId: '', newPassword: '123456' })
const knowledgeFilters = reactive({ keyword: '', role: 'ADMIN', limit: 20 })
const knowledgeForm = reactive({ title: '', content: '', category: 'general', source: 'admin-console', tags: '', roles: 'STUDENT,COMPANY' })
const uploadForm = reactive({ title: '', category: 'general', source: 'admin-upload', tags: '', roles: 'STUDENT,COMPANY' })

const pageTitle = computed(() => activeModule.value === 'accounts' ? '账号管理' : '知识库管理')
const activeAccountCount = computed(() => accounts.value.filter((account) => account.status === 'ACTIVE').length)
const restrictedAccountCount = computed(() => accounts.value.filter((account) => account.status !== 'ACTIVE').length)
const adminAccountCount = computed(() => accounts.value.filter((account) => account.role === 'ADMIN').length)
const visibleDocumentCount = computed(() => documents.value.length)
const recentIngestionCount = computed(() => ingestions.value.length)

function splitValues(value: string) {
  return value.split(/[\n,，;]/).map((item) => item.trim()).filter(Boolean)
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date)
}

async function loadAccounts() {
  accountsLoading.value = true
  try {
    accounts.value = await listAccounts({
      role: accountFilters.role || undefined,
      status: accountFilters.status || undefined,
      keyword: accountFilters.keyword.trim() || undefined
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '账号列表加载失败')
  } finally {
    accountsLoading.value = false
  }
}

async function submitAccount() {
  if (!accountForm.username.trim() || !accountForm.password || !accountForm.displayName.trim()) {
    ElMessage.warning('请填写账号、密码和显示名称')
    return
  }
  actionLoading.value = true
  try {
    const created = await createAccount({
      username: accountForm.username.trim(),
      password: accountForm.password,
      displayName: accountForm.displayName.trim(),
      role: accountForm.role,
      status: 'ACTIVE'
    })
    accounts.value = [created, ...accounts.value.filter((account) => account.accountId !== created.accountId)]
    passwordForm.accountId = created.accountId
    accountForm.username = ''
    accountForm.displayName = ''
    ElMessage.success('账号已创建')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '账号创建失败')
  } finally {
    actionLoading.value = false
  }
}

async function changeAccountStatus(account: AccountSummary, status: AccountStatus) {
  actionLoading.value = true
  try {
    const updated = await updateAccountStatus(account.accountId, status)
    accounts.value = accounts.value.map((item) => item.accountId === updated.accountId ? updated : item)
    ElMessage.success(status === 'ACTIVE' ? '账号已启用' : '账号已停用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '账号状态更新失败')
  } finally {
    actionLoading.value = false
  }
}

async function resetPassword() {
  if (!passwordForm.accountId.trim() || !passwordForm.newPassword) {
    ElMessage.warning('请选择账号并输入新密码')
    return
  }
  actionLoading.value = true
  try {
    await changeAccountPassword({ accountId: passwordForm.accountId.trim(), newPassword: passwordForm.newPassword })
    ElMessage.success('密码已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '密码更新失败')
  } finally {
    actionLoading.value = false
  }
}

async function loadKnowledge() {
  knowledgeLoading.value = true
  try {
    const [stats, documentList, ingestionList] = await Promise.all([
      getKnowledgeBaseStats(),
      listKnowledgeDocuments(knowledgeFilters.keyword, knowledgeFilters.role, knowledgeFilters.limit),
      listKnowledgeIngestions({ limit: 10 })
    ])
    knowledgeStats.value = stats
    documents.value = documentList
    ingestions.value = ingestionList
    selectedDocumentIds.value = selectedDocumentIds.value.filter((id) => documentList.some((document) => document.documentId === id))
    roleDrafts.value = Object.fromEntries(documentList.map((document) => [document.documentId, document.roles.join(', ')]))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识文档加载失败')
  } finally {
    knowledgeLoading.value = false
  }
}

async function submitKnowledgeDocument() {
  if (!knowledgeForm.title.trim() || !knowledgeForm.content.trim()) {
    ElMessage.warning('请填写知识文档标题和内容')
    return
  }
  actionLoading.value = true
  try {
    const document = await createKnowledgeDocument({
      title: knowledgeForm.title.trim(),
      content: knowledgeForm.content.trim(),
      category: knowledgeForm.category.trim() || 'general',
      source: knowledgeForm.source.trim() || 'admin-console',
      tags: splitValues(knowledgeForm.tags),
      roles: splitValues(knowledgeForm.roles)
    })
    documents.value = [document, ...documents.value.filter((item) => item.documentId !== document.documentId)]
    roleDrafts.value = { ...roleDrafts.value, [document.documentId]: document.roles.join(', ') }
    knowledgeForm.title = ''
    knowledgeForm.content = ''
    knowledgeForm.tags = ''
    ElMessage.success('知识文档已创建')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识文档创建失败')
  } finally {
    actionLoading.value = false
  }
}

function chooseFile(event: Event) {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0] || null
  if (selectedFile.value && !uploadForm.title.trim()) {
    uploadForm.title = selectedFile.value.name.replace(/\.[^.]+$/, '')
  }
}

async function submitKnowledgeFile() {
  const file = selectedFile.value
  if (!file) {
    ElMessage.warning('请选择要上传的知识文件')
    return
  }
  actionLoading.value = true
  try {
    await uploadKnowledgeFile({
      file,
      title: uploadForm.title.trim() || file.name,
      category: uploadForm.category.trim() || 'general',
      source: uploadForm.source.trim() || 'admin-upload',
      tags: splitValues(uploadForm.tags),
      roles: splitValues(uploadForm.roles)
    })
    selectedFile.value = null
    uploadForm.title = ''
    uploadForm.tags = ''
    if (fileInput.value) fileInput.value.value = ''
    await loadKnowledge()
    ElMessage.success('知识文件已提交导入')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识文件上传失败')
  } finally {
    actionLoading.value = false
  }
}

async function saveDocumentRoles(document: KnowledgeDocument) {
  const roles = splitValues(roleDrafts.value[document.documentId] || '')
  if (!roles.length) {
    ElMessage.warning('请至少保留一个可读取角色')
    return
  }
  actionLoading.value = true
  try {
    const updated = await updateKnowledgeDocumentRoles(document.documentId, roles)
    documents.value = documents.value.map((item) => item.documentId === updated.documentId ? updated : item)
    ElMessage.success('文档角色权限已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文档权限更新失败')
  } finally {
    actionLoading.value = false
  }
}

async function removeDocument(document: KnowledgeDocument) {
  if (!window.confirm(`确定删除知识文档「${document.title}」吗？删除后不可恢复。`)) {
    return
  }
  actionLoading.value = true
  try {
    await deleteKnowledgeDocument(document.documentId)
    documents.value = documents.value.filter((item) => item.documentId !== document.documentId)
    selectedDocumentIds.value = selectedDocumentIds.value.filter((id) => id !== document.documentId)
    ElMessage.success('知识文档已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识文档删除失败')
  } finally {
    actionLoading.value = false
  }
}

async function removeSelectedDocuments() {
  const documentIds = selectedDocumentIds.value
  if (!documentIds.length) {
    ElMessage.warning('请先选择要删除的知识文档')
    return
  }
  if (!window.confirm(`确定批量删除选中的 ${documentIds.length} 个知识文档吗？删除后不可恢复。`)) {
    return
  }
  actionLoading.value = true
  try {
    const result = await batchDeleteKnowledgeDocuments(documentIds)
    const deleted = new Set(result.deletedDocumentIds)
    documents.value = documents.value.filter((document) => !deleted.has(document.documentId))
    selectedDocumentIds.value = selectedDocumentIds.value.filter((id) => !deleted.has(id))
    ElMessage.success(`已删除 ${result.deletedCount} 个知识文档`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识文档批量删除失败')
  } finally {
    actionLoading.value = false
  }
}

function loadModule(module: string) {
  if (module === 'accounts') {
    void loadAccounts()
  } else {
    void loadKnowledge()
  }
}

onMounted(() => loadModule(activeModule.value))
watch(activeModule, loadModule)
</script>

<template>
  <section class="page admin-page">
    <header class="dashboard-header">
      <div class="title-block"><p class="eyebrow">平台治理中心</p><h1 class="page-title">{{ pageTitle }}</h1><p class="page-subtitle">维护账号权限与可检索的校园招聘知识资料</p></div>
      <span class="header-role"><ShieldCheck :size="16" />管理员</span>
    </header>

    <template v-if="activeModule === 'accounts'">
      <section class="metric-grid" aria-label="账号数据概览">
        <article class="metric-card metric-mint"><span class="metric-icon"><Users :size="18" /></span><div><span>当前账号列表</span><strong>{{ accounts.length }}</strong><small>按当前筛选条件已加载</small></div></article>
        <article class="metric-card metric-lavender"><span class="metric-icon"><ShieldCheck :size="18" /></span><div><span>启用账号</span><strong>{{ activeAccountCount }}</strong><small>当前列表中已启用的账号</small></div></article>
        <article class="metric-card metric-peach"><span class="metric-icon"><KeyRound :size="18" /></span><div><span>受限账号</span><strong>{{ restrictedAccountCount }}</strong><small>当前列表中已停用或锁定的账号</small></div></article>
        <article class="metric-card metric-blue"><span class="metric-icon"><Users :size="18" /></span><div><span>管理员账号</span><strong>{{ adminAccountCount }}</strong><small>当前列表中拥有管理权限的账号</small></div></article>
      </section>

      <section class="panel module-panel filter-panel">
        <header class="section-heading"><div><p class="section-kicker">账号目录</p><h2>账号筛选</h2></div><span class="heading-icon"><Users :size="19" /></span></header>
        <div class="toolbar">
          <label class="field"><span>关键词</span><el-input v-model="accountFilters.keyword" clearable placeholder="账号或名称" @keyup.enter="loadAccounts" /></label>
          <label class="field"><span>角色</span><el-select v-model="accountFilters.role" clearable placeholder="全部角色"><el-option label="学生" value="STUDENT" /><el-option label="企业" value="COMPANY" /><el-option label="管理员" value="ADMIN" /></el-select></label>
          <label class="field"><span>状态</span><el-select v-model="accountFilters.status" clearable placeholder="全部状态"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /><el-option label="锁定" value="LOCKED" /></el-select></label>
          <el-button type="primary" class="filter-button" :loading="accountsLoading" @click="loadAccounts">查询</el-button>
        </div>
      </section>
      <section class="admin-grid">
        <article class="panel module-panel form-panel">
          <header class="section-heading"><div><p class="section-kicker">账户配置</p><h2>创建账号</h2></div><span class="heading-icon"><Plus :size="19" /></span></header>
          <div class="form-stack"><label class="field"><span>账号</span><el-input v-model="accountForm.username" placeholder="输入登录账号" /></label><label class="field"><span>显示名称</span><el-input v-model="accountForm.displayName" placeholder="输入显示名称" /></label><label class="field"><span>初始密码</span><el-input v-model="accountForm.password" type="password" show-password placeholder="设置初始密码" /></label><label class="field"><span>角色</span><el-select v-model="accountForm.role"><el-option label="学生" value="STUDENT" /><el-option label="企业" value="COMPANY" /><el-option label="管理员" value="ADMIN" /></el-select></label></div>
          <div class="actions"><el-button type="primary" :loading="actionLoading" @click="submitAccount">创建账号</el-button></div>
        </article>
        <article class="panel module-panel form-panel reset-panel">
          <header class="section-heading"><div><p class="section-kicker">安全操作</p><h2>重置密码</h2></div><span class="heading-icon"><KeyRound :size="19" /></span></header>
          <p class="panel-note">选择当前已加载的账号后设置新密码。</p>
          <div class="form-stack"><label class="field"><span>账号</span><el-select v-model="passwordForm.accountId" clearable placeholder="选择账号"><el-option v-for="account in accounts" :key="account.accountId" :label="`${account.username} · ${account.displayName}`" :value="account.accountId" /></el-select></label><label class="field"><span>新密码</span><el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="输入新密码" /></label></div>
          <div class="actions"><el-button :loading="actionLoading" @click="resetPassword">更新密码</el-button></div>
        </article>
      </section>
      <section class="panel module-panel list-panel" v-loading="accountsLoading">
        <header class="section-heading"><div><p class="section-kicker">访问控制</p><h2>账号列表</h2><span class="section-note">显示当前筛选条件下的 {{ accounts.length }} 条结果</span></div><span class="heading-icon"><ShieldCheck :size="19" /></span></header>
        <el-empty v-if="!accounts.length" description="暂无账号" />
        <div v-else class="account-list"><div class="list-head"><span>账号</span><span>角色</span><span>状态</span><span>操作</span></div><article v-for="account in accounts" :key="account.accountId" class="account-row"><div class="account-identity"><span class="account-avatar">{{ account.displayName.slice(0, 1) }}</span><div><strong>{{ account.displayName }}</strong><span>{{ account.username }} · 更新于 {{ formatDateTime(account.updatedAt) }}</span></div></div><span class="role-chip">{{ account.role }}</span><el-tag :type="account.status === 'ACTIVE' ? 'success' : 'warning'" effect="light">{{ account.status }}</el-tag><div class="actions compact-actions"><el-button size="small" :loading="actionLoading" @click="changeAccountStatus(account, 'ACTIVE')">启用</el-button><el-button size="small" :loading="actionLoading" @click="changeAccountStatus(account, 'DISABLED')">停用</el-button></div></article></div>
      </section>
    </template>

    <template v-else>
      <section class="metric-grid" aria-label="知识库数据概览">
        <article class="metric-card metric-mint"><span class="metric-icon"><Library :size="18" /></span><div><span>知识文档</span><strong>{{ knowledgeStats?.documentCount || 0 }}</strong><small>知识库统计文档数</small></div></article>
        <article class="metric-card metric-lavender"><span class="metric-icon"><Library :size="18" /></span><div><span>检索分块</span><strong>{{ knowledgeStats?.chunkCount || 0 }}</strong><small>知识库统计知识块数</small></div></article>
        <article class="metric-card metric-peach"><span class="metric-icon"><ShieldCheck :size="18" /></span><div><span>当前文档列表</span><strong>{{ visibleDocumentCount }}</strong><small>按当前检索条件已加载</small></div></article>
        <article class="metric-card metric-blue"><span class="metric-icon"><Upload :size="18" /></span><div><span>最近导入任务</span><strong>{{ recentIngestionCount }}</strong><small>当前已加载的任务列表</small></div></article>
      </section>
      <section class="panel module-panel filter-panel" v-loading="knowledgeLoading">
        <header class="section-heading"><div><p class="section-kicker">知识库管理</p><h2>知识文档</h2><span class="section-note">{{ knowledgeStats?.corpusVersion || '等待加载' }}</span></div><div class="heading-actions"><el-button circle size="small" aria-label="刷新知识库" @click="loadKnowledge"><RefreshCw :size="15" /></el-button><span class="heading-icon"><Library :size="19" /></span></div></header>
        <div class="toolbar"><label class="field"><span>检索内容</span><el-input v-model="knowledgeFilters.keyword" clearable placeholder="搜索标题、标签或内容" @keyup.enter="loadKnowledge" /></label><label class="field"><span>读取角色</span><el-select v-model="knowledgeFilters.role"><el-option label="管理员" value="ADMIN" /><el-option label="学生" value="STUDENT" /><el-option label="企业" value="COMPANY" /></el-select></label><el-button type="primary" class="filter-button" :loading="knowledgeLoading" @click="loadKnowledge">查询</el-button></div>
      </section>
      <section class="admin-grid knowledge-editor-grid">
        <article class="panel module-panel form-panel">
          <header class="section-heading"><div><p class="section-kicker">内容录入</p><h2>手工新增</h2></div><span class="heading-icon"><Plus :size="19" /></span></header>
          <div class="form-stack"><label class="field"><span>标题</span><el-input v-model="knowledgeForm.title" placeholder="输入文档标题" /></label><label class="field"><span>分类</span><el-input v-model="knowledgeForm.category" placeholder="例如 interview" /></label><label class="field"><span>标签</span><el-input v-model="knowledgeForm.tags" placeholder="使用逗号分隔" /></label><label class="field"><span>可读取角色</span><el-input v-model="knowledgeForm.roles" placeholder="例如 STUDENT,COMPANY" /></label><label class="field"><span>文档内容</span><el-input v-model="knowledgeForm.content" type="textarea" :rows="7" placeholder="输入可被检索的正文内容" /></label></div>
          <div class="actions"><el-button type="primary" :loading="actionLoading" @click="submitKnowledgeDocument">创建文档</el-button></div>
        </article>
        <article class="panel module-panel form-panel import-panel">
          <header class="section-heading"><div><p class="section-kicker">文件入库</p><h2>上传导入</h2></div><span class="heading-icon"><Upload :size="19" /></span></header>
          <label class="file-control" :class="{ 'has-file': selectedFile }"><span class="upload-mark"><Upload :size="20" /></span><span class="file-copy"><strong>{{ selectedFile?.name || '选择知识文件' }}</strong><small>{{ selectedFile ? '已选择，提交后将进入导入任务' : '支持 TXT、MD、PDF、DOC、DOCX' }}</small></span><span class="file-action">选择文件</span><input ref="fileInput" type="file" accept=".txt,.md,.pdf,.doc,.docx" @change="chooseFile" /></label>
          <div class="form-stack"><label class="field"><span>文档标题</span><el-input v-model="uploadForm.title" placeholder="未填写时使用文件名" /></label><label class="field"><span>标签</span><el-input v-model="uploadForm.tags" placeholder="使用逗号分隔" /></label><label class="field"><span>可读取角色</span><el-input v-model="uploadForm.roles" placeholder="例如 STUDENT,COMPANY" /></label></div>
          <div class="actions"><el-button type="primary" :loading="actionLoading" @click="submitKnowledgeFile">上传到知识库</el-button></div>
        </article>
      </section>
      <section class="panel module-panel list-panel" v-loading="knowledgeLoading">
        <header class="section-heading"><div><p class="section-kicker">文件处理</p><h2>导入任务</h2><span class="section-note">显示当前已加载的 {{ ingestions.length }} 条任务</span></div><span class="heading-icon"><Upload :size="19" /></span></header>
        <el-empty v-if="!ingestions.length" description="暂无导入任务" />
        <div v-else class="ingestion-list"><article v-for="ingestion in ingestions" :key="ingestion.jobId" class="ingestion-row"><div class="ingestion-file"><span class="file-glyph"><Upload :size="16" /></span><div><strong>{{ ingestion.title }}</strong><span>{{ ingestion.fileName }} · {{ ingestion.source }}</span></div></div><el-tag :type="ingestion.status === 'READY' ? 'success' : ingestion.status === 'FAILED' ? 'danger' : 'warning'" effect="light">{{ ingestion.status }}</el-tag><span class="ingestion-meta">{{ ingestion.chunkCount }} 块 · {{ formatDateTime(ingestion.updatedAt) }}</span></article></div>
      </section>
      <section class="panel module-panel list-panel" v-loading="knowledgeLoading">
        <header class="section-heading"><div><p class="section-kicker">检索资产</p><h2>已入库文档</h2><span class="section-note">显示当前筛选条件下的 {{ documents.length }} 条结果</span></div><div class="heading-actions"><el-button size="small" type="danger" plain :disabled="!selectedDocumentIds.length" :loading="actionLoading" @click="removeSelectedDocuments">批量删除</el-button><span class="heading-icon"><Library :size="19" /></span></div></header>
        <el-empty v-if="!documents.length" description="暂无知识文档" />
        <div v-else class="document-list"><div class="document-list-head"><span>选择</span><span>文档信息</span><span>读取权限</span></div><article v-for="document in documents" :key="document.documentId" class="document-row"><label class="document-select"><input v-model="selectedDocumentIds" type="checkbox" :value="document.documentId" /><span>选择</span></label><div class="document-identity"><strong>{{ document.title }}</strong><span>{{ document.category }} · {{ document.source }} · {{ formatDateTime(document.createdAt) }}</span><div class="tag-row"><el-tag v-for="tag in document.tags" :key="tag" type="info">{{ tag }}</el-tag></div></div><div class="document-actions"><label class="field inline-field"><span>角色</span><el-input v-model="roleDrafts[document.documentId]" size="small" placeholder="角色" /></label><div class="actions compact-actions"><el-button size="small" :loading="actionLoading" @click="saveDocumentRoles(document)">保存权限</el-button><el-button size="small" type="danger" plain :loading="actionLoading" @click="removeDocument(document)"><Trash2 :size="14" />删除</el-button></div></div></article></div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.admin-page{display:grid;gap:20px}.dashboard-header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px}.title-block{min-width:0}.eyebrow,.section-kicker{margin:0 0 7px;color:var(--accent,#28664f);font-size:12px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.page-title{margin:0;color:var(--ink,#1c2420);font-size:28px;line-height:1.15;letter-spacing:0}.page-subtitle{max-width:650px;margin:8px 0 0;color:var(--muted,#667085);font-size:14px;line-height:1.6}.header-role{display:inline-flex;align-items:center;gap:7px;padding:8px 10px;border:1px solid var(--line,#e5e7eb);border-radius:999px;color:var(--accent,#28664f);background:var(--surface,#fff);font-size:13px;font-weight:700}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.metric-card{display:flex;align-items:flex-start;gap:12px;min-height:132px;padding:18px;border:1px solid transparent;border-radius:16px}.metric-card>div{display:grid;gap:3px;min-width:0}.metric-card span:not(.metric-icon),.metric-card small{color:#52645c;font-size:12px;line-height:1.4}.metric-card strong{color:var(--ink,#1c2420);font-size:28px;line-height:1.1}.metric-icon,.heading-icon,.account-avatar,.file-glyph{display:grid;flex:0 0 auto;place-items:center;color:var(--accent,#28664f);background:rgba(255,255,255,.72);border:1px solid rgba(30,59,46,.08);border-radius:10px}.metric-icon{width:36px;height:36px}.metric-mint{background:#c8f1df}.metric-lavender{background:#ece7fb}.metric-peach{background:#ffeadf}.metric-blue{background:#e4f0ff}.panel{border-radius:16px!important;background:var(--surface,#fff)}.module-panel{padding:22px}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:20px}.section-heading h2{margin:0;color:var(--ink,#1c2420);font-size:18px;line-height:1.35}.section-note{display:block;margin-top:5px;color:var(--muted,#667085);font-size:13px}.heading-actions,.actions{display:flex;align-items:center;gap:8px}.toolbar{display:grid;grid-template-columns:minmax(220px,1fr) minmax(140px,.42fr) minmax(140px,.42fr) auto;gap:12px;align-items:end}.toolbar .field{min-width:0}.toolbar :deep(.el-select){width:100%}.filter-button{min-height:36px}.field{display:grid;gap:7px;color:var(--ink,#1c2420);font-size:13px;font-weight:700}.form-stack{display:grid;gap:14px}.admin-grid{display:grid;grid-template-columns:1.15fr .85fr;gap:16px}.knowledge-editor-grid{grid-template-columns:1fr 1fr}.form-panel{min-height:100%}.panel-note{margin:-4px 0 16px;color:var(--muted,#667085);font-size:13px;line-height:1.55}.actions{margin-top:20px}.list-panel{overflow:hidden}.list-head,.account-row{display:grid;grid-template-columns:minmax(230px,1.3fr) 105px 100px 145px;gap:16px;align-items:center}.list-head{padding:0 14px 10px;color:var(--muted,#667085);font-size:12px;font-weight:700}.account-row{padding:15px 14px;border-top:1px solid var(--line,#e5e7eb)}.account-row:hover,.document-row:hover{background:#fbfcfc}.account-identity,.ingestion-file{display:flex;align-items:center;gap:11px;min-width:0}.account-avatar{width:34px;height:34px;background:var(--accent-soft,#c8f1df);font-size:13px;font-weight:800}.account-identity strong,.ingestion-file strong,.document-identity strong{display:block;color:var(--ink,#1c2420);font-size:14px}.account-identity span,.ingestion-file span,.document-identity>span{display:block;margin-top:4px;color:var(--muted,#667085);font-size:12px;line-height:1.45}.role-chip{width:max-content;padding:4px 7px;border-radius:6px;background:#f1f3f5;color:#52645c;font-size:11px;font-weight:800}.compact-actions{margin:0;justify-content:flex-start}.compact-actions :deep(.el-button+.el-button){margin-left:0}.file-control{display:flex;align-items:center;gap:12px;min-height:106px;margin-bottom:16px;padding:16px;border:1.5px dashed #aab7b0;border-radius:12px;background:#fbfcfc;color:var(--muted,#667085);cursor:pointer}.file-control:hover,.file-control.has-file{border-color:var(--accent,#28664f);background:#f1fbf6}.file-control input{display:none}.upload-mark{display:grid;place-items:center;width:38px;height:38px;border-radius:10px;color:var(--accent,#28664f);background:var(--accent-soft,#c8f1df)}.file-copy{display:grid;gap:4px;flex:1;min-width:0}.file-copy strong{overflow:hidden;color:var(--ink,#1c2420);font-size:13px;text-overflow:ellipsis;white-space:nowrap}.file-copy small{font-size:12px;line-height:1.4}.file-action{padding:6px 8px;border:1px solid var(--line,#e5e7eb);border-radius:6px;background:#fff;color:var(--ink,#1c2420);font-size:12px;font-weight:700;white-space:nowrap}.ingestion-list{display:grid}.ingestion-row{display:grid;grid-template-columns:minmax(0,1fr) auto 160px;gap:16px;align-items:center;padding:14px 0;border-top:1px solid var(--line,#e5e7eb)}.file-glyph{width:32px;height:32px;background:#e4f0ff}.ingestion-meta{color:var(--muted,#667085);font-size:12px;text-align:right}.document-list{display:grid}.document-list-head,.document-row{display:grid;grid-template-columns:84px minmax(240px,1fr) minmax(260px,.75fr);gap:16px;align-items:center}.document-list-head{padding:0 14px 10px;color:var(--muted,#667085);font-size:12px;font-weight:700}.document-row{padding:15px 14px;border-top:1px solid var(--line,#e5e7eb)}.document-select{display:inline-flex;align-items:center;gap:6px;color:var(--muted,#667085);font-size:12px}.tag-row{display:flex;flex-wrap:wrap;gap:5px;margin-top:9px}.document-actions{display:grid;gap:10px;min-width:0}.inline-field{grid-template-columns:34px minmax(0,1fr);align-items:center;gap:8px;font-size:12px}.inline-field :deep(.el-input){min-width:0}@media (max-width:1100px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.toolbar{grid-template-columns:minmax(200px,1fr) minmax(140px,.5fr) minmax(140px,.5fr)}.toolbar .filter-button{justify-self:start}.account-row,.list-head{grid-template-columns:minmax(0,1fr) 90px 100px}.account-row>.actions{grid-column:1 / -1}.list-head span:last-child{display:none}.document-row,.document-list-head{grid-template-columns:70px minmax(0,1fr)}.document-actions{grid-column:2}.document-list-head span:last-child{display:none}.ingestion-row{grid-template-columns:minmax(0,1fr) auto}.ingestion-meta{grid-column:1;text-align:left}.admin-grid{grid-template-columns:1fr}}@media (max-width:640px){.dashboard-header{align-items:flex-start;flex-direction:column;gap:12px}.metric-grid{grid-template-columns:1fr 1fr;gap:10px}.metric-card{min-height:126px;padding:14px;gap:9px}.metric-card strong{font-size:24px}.module-panel{padding:16px}.toolbar{grid-template-columns:1fr}.toolbar .filter-button{width:100%}.account-row,.list-head{grid-template-columns:1fr}.list-head{display:none}.account-row>.actions{grid-column:auto}.account-row>*,.document-row>*,.ingestion-row>*{justify-self:start}.knowledge-editor-grid{grid-template-columns:1fr}.file-control{align-items:flex-start;flex-wrap:wrap}.file-action{margin-left:50px}.ingestion-row{grid-template-columns:1fr}.document-list-head{display:none}.document-row{grid-template-columns:1fr;padding:15px 0}.document-actions{grid-column:auto;width:100%}.document-select{order:3}.inline-field{grid-template-columns:42px minmax(0,1fr)}}
@media (min-width:1101px){.list-head,.account-row{grid-template-columns:minmax(0,1fr) 76px 80px 136px;gap:12px}}
.account-list,.account-row,.account-identity,.account-identity>div,.document-identity{min-width:0}.account-identity strong,.account-identity span,.document-identity strong,.document-identity>span{overflow-wrap:anywhere;word-break:break-word}.tag-row{min-width:0}.tag-row :deep(.el-tag){display:inline-flex;align-items:center;max-width:100%;height:auto;min-height:24px;white-space:normal}.tag-row :deep(.el-tag__content){overflow-wrap:anywhere;word-break:break-word;white-space:normal}
</style>
