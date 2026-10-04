<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ChevronDown, KeyRound, Library, Plus, RefreshCw, Search, ShieldCheck, Trash2, Upload, Users } from 'lucide-vue-next'
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

import KnowledgeIndexPanel from '../features/student/KnowledgeIndexPanel.vue'

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
      <h1 class="page-title">{{ pageTitle }}</h1>
      <span class="header-role"><ShieldCheck :size="15" />管理员</span>
    </header>

    <template v-if="activeModule === 'accounts'">
      <section class="summary-row metric-grid" aria-label="账号数据概览">
        <div><span>当前列表</span><strong>{{ accounts.length }}</strong></div>
        <div><span>启用</span><strong class="status-number">{{ activeAccountCount }}</strong></div>
        <div><span>受限</span><strong>{{ restrictedAccountCount }}</strong></div>
        <div><span>管理员</span><strong>{{ adminAccountCount }}</strong></div>
      </section>
      <section class="panel module-panel filter-panel">
        <div class="toolbar">
          <label class="field"><span>关键词</span><el-input v-model="accountFilters.keyword" clearable placeholder="账号或名称" @keyup.enter="loadAccounts" /></label>
          <label class="field"><span>角色</span><el-select v-model="accountFilters.role" clearable placeholder="全部角色"><el-option label="学生" value="STUDENT" /><el-option label="企业" value="COMPANY" /><el-option label="管理员" value="ADMIN" /></el-select></label>
          <label class="field"><span>状态</span><el-select v-model="accountFilters.status" clearable placeholder="全部状态"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /><el-option label="锁定" value="LOCKED" /></el-select></label>
          <el-button type="primary" class="filter-button" :loading="accountsLoading" @click="loadAccounts"><Search :size="15" />查询</el-button>
        </div>
      </section>
      <details class="admin-editor">
        <summary><Users :size="16" /><span>创建账号与重置密码</span><ChevronDown :size="16" /></summary>
        <section class="admin-grid">
          <article class="panel module-panel form-panel">
            <header class="section-heading"><h2>创建账号</h2></header>
            <div class="form-stack">
              <label class="field"><span>账号</span><el-input v-model="accountForm.username" placeholder="登录账号" /></label>
              <label class="field"><span>显示名称</span><el-input v-model="accountForm.displayName" placeholder="显示名称" /></label>
              <label class="field"><span>初始密码</span><el-input v-model="accountForm.password" type="password" show-password placeholder="初始密码" /></label>
              <label class="field"><span>角色</span><el-select v-model="accountForm.role"><el-option label="学生" value="STUDENT" /><el-option label="企业" value="COMPANY" /><el-option label="管理员" value="ADMIN" /></el-select></label>
            </div>
            <div class="actions"><el-button type="primary" :loading="actionLoading" @click="submitAccount"><Plus :size="15" />创建账号</el-button></div>
          </article>
          <article class="panel module-panel form-panel reset-panel">
            <header class="section-heading"><h2>重置密码</h2></header>
            <div class="form-stack">
              <label class="field"><span>账号</span><el-select v-model="passwordForm.accountId" clearable filterable placeholder="选择账号"><el-option v-for="account in accounts" :key="account.accountId" :label="`${account.username} · ${account.displayName}`" :value="account.accountId" /></el-select></label>
              <label class="field"><span>新密码</span><el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="新密码" /></label>
            </div>
            <div class="actions"><el-button :loading="actionLoading" @click="resetPassword"><KeyRound :size="15" />更新密码</el-button></div>
          </article>
        </section>
      </details>
      <section class="panel module-panel list-panel" v-loading="accountsLoading">
        <header class="section-heading"><h2>账号列表 <span class="section-count">{{ accounts.length }}</span></h2><el-button circle size="small" title="刷新账号" aria-label="刷新账号" :loading="accountsLoading" @click="loadAccounts"><RefreshCw :size="15" /></el-button></header>
        <el-empty v-if="!accounts.length" description="暂无账号" />
        <div v-else class="account-list">
          <div class="list-head"><span>账号</span><span>角色</span><span>状态</span><span>操作</span></div>
          <article v-for="account in accounts" :key="account.accountId" class="account-row">
            <div class="account-identity"><span class="account-avatar">{{ account.displayName.slice(0, 1) }}</span><div><strong>{{ account.displayName }}</strong><span>{{ account.username }} · {{ formatDateTime(account.updatedAt) }}</span></div></div>
            <span class="role-chip">{{ account.role === 'ADMIN' ? '管理员' : account.role === 'COMPANY' ? '企业' : '学生' }}</span>
            <el-tag :type="account.status === 'ACTIVE' ? 'primary' : 'warning'" effect="light">{{ account.status === 'ACTIVE' ? '启用' : account.status === 'LOCKED' ? '锁定' : '停用' }}</el-tag>
            <div class="actions compact-actions"><el-button size="small" :loading="actionLoading" @click="changeAccountStatus(account, 'ACTIVE')">启用</el-button><el-button size="small" :loading="actionLoading" @click="changeAccountStatus(account, 'DISABLED')">停用</el-button></div>
          </article>
        </div>
      </section>
    </template>

    <template v-else>
      <section class="summary-row metric-grid" aria-label="知识库数据概览">
        <div><span>知识文档</span><strong>{{ knowledgeStats?.documentCount || 0 }}</strong></div>
        <div><span>检索分块</span><strong>{{ knowledgeStats?.chunkCount || 0 }}</strong></div>
        <div><span>当前列表</span><strong>{{ visibleDocumentCount }}</strong></div>
        <div><span>导入任务</span><strong>{{ recentIngestionCount }}</strong></div>
      </section>
      <section class="panel module-panel filter-panel" v-loading="knowledgeLoading">
        <div class="toolbar knowledge-toolbar">
          <label class="field"><span>检索内容</span><el-input v-model="knowledgeFilters.keyword" clearable placeholder="标题、标签或内容" @keyup.enter="loadKnowledge" /></label>
          <label class="field"><span>读取角色</span><el-select v-model="knowledgeFilters.role"><el-option label="管理员" value="ADMIN" /><el-option label="学生" value="STUDENT" /><el-option label="企业" value="COMPANY" /></el-select></label>
          <el-button type="primary" class="filter-button" :loading="knowledgeLoading" @click="loadKnowledge"><Search :size="15" />查询</el-button>
        </div>
      </section>
      <details class="admin-editor">
        <summary><Plus :size="16" /><span>新增文档 / 上传文件</span><ChevronDown :size="16" /></summary>
        <section class="admin-grid knowledge-editor-grid">
          <article class="panel module-panel form-panel">
            <header class="section-heading"><h2>新增文档</h2></header>
            <div class="form-stack">
              <label class="field"><span>标题</span><el-input v-model="knowledgeForm.title" placeholder="文档标题" /></label>
              <label class="field"><span>分类</span><el-input v-model="knowledgeForm.category" placeholder="interview" /></label>
              <label class="field"><span>标签</span><el-input v-model="knowledgeForm.tags" placeholder="Java, 面试" /></label>
              <label class="field"><span>可读取角色</span><el-input v-model="knowledgeForm.roles" placeholder="STUDENT,COMPANY" /></label>
              <label class="field field-wide"><span>文档内容</span><el-input v-model="knowledgeForm.content" type="textarea" :rows="7" placeholder="文档正文" /></label>
            </div>
            <div class="actions"><el-button type="primary" :loading="actionLoading" @click="submitKnowledgeDocument"><Plus :size="15" />创建文档</el-button></div>
          </article>
          <article class="panel module-panel form-panel import-panel">
            <header class="section-heading"><h2>上传文件</h2></header>
            <label class="file-control" :class="{ 'has-file': selectedFile }">
              <Upload :size="18" /><span class="file-copy"><strong>{{ selectedFile?.name || '选择知识文件' }}</strong><small>TXT / MD / PDF / DOC / DOCX</small></span>
              <input ref="fileInput" type="file" accept=".txt,.md,.pdf,.doc,.docx" aria-label="选择知识文件" @change="chooseFile" />
            </label>
            <div class="form-stack">
              <label class="field field-wide"><span>文档标题</span><el-input v-model="uploadForm.title" placeholder="文档标题" /></label>
              <label class="field"><span>标签</span><el-input v-model="uploadForm.tags" placeholder="Java, 面试" /></label>
              <label class="field"><span>可读取角色</span><el-input v-model="uploadForm.roles" placeholder="STUDENT,COMPANY" /></label>
            </div>
            <div class="actions"><el-button type="primary" :loading="actionLoading" @click="submitKnowledgeFile"><Upload :size="15" />上传到知识库</el-button></div>
          </article>
        </section>
      </details>
      <section class="panel module-panel list-panel" v-loading="knowledgeLoading">
        <header class="section-heading"><h2>已入库文档 <span class="section-count">{{ documents.length }}</span></h2><div class="heading-actions"><el-button circle size="small" title="刷新知识库" aria-label="刷新知识库" @click="loadKnowledge"><RefreshCw :size="15" /></el-button><el-button size="small" type="danger" plain :disabled="!selectedDocumentIds.length" :loading="actionLoading" @click="removeSelectedDocuments"><Trash2 :size="14" />批量删除</el-button></div></header>
        <el-empty v-if="!documents.length" description="暂无知识文档" />
        <div v-else class="document-list">
          <div class="document-list-head"><span>选择</span><span>文档信息</span><span>读取权限</span></div>
          <article v-for="document in documents" :key="document.documentId" class="document-row">
            <label class="document-select"><input v-model="selectedDocumentIds" type="checkbox" :value="document.documentId" :aria-label="`选择文档 ${document.title}`" /><span>选择</span></label>
            <div class="document-identity"><strong>{{ document.title }}</strong><span>{{ document.category }} · {{ document.source }} · {{ formatDateTime(document.createdAt) }}</span><div class="tag-row"><el-tag v-for="tag in document.tags" :key="tag" type="info">{{ tag }}</el-tag></div></div>
            <div class="document-actions"><label class="field inline-field"><span>角色</span><el-input v-model="roleDrafts[document.documentId]" size="small" placeholder="角色" /></label><div class="actions compact-actions"><el-button size="small" :loading="actionLoading" @click="saveDocumentRoles(document)">保存权限</el-button><el-button size="small" type="danger" plain title="删除文档" aria-label="删除文档" :loading="actionLoading" @click="removeDocument(document)"><Trash2 :size="14" /></el-button></div></div>
          </article>
        </div>
      </section>
      <details class="admin-editor ingestion-details">
        <summary><Upload :size="16" /><span>导入任务</span><span class="section-count">{{ ingestions.length }}</span><ChevronDown :size="16" /></summary>
        <section class="panel module-panel list-panel" v-loading="knowledgeLoading">
          <el-empty v-if="!ingestions.length" description="暂无导入任务" />
          <div v-else class="ingestion-list">
            <article v-for="ingestion in ingestions" :key="ingestion.jobId" class="ingestion-row">
              <div class="ingestion-file"><Upload :size="16" /><div><strong>{{ ingestion.title }}</strong><span>{{ ingestion.fileName }} · {{ ingestion.source }}</span></div></div>
              <el-tag :type="ingestion.status === 'READY' ? 'primary' : ingestion.status === 'FAILED' ? 'danger' : 'warning'" effect="light">{{ ingestion.status === 'READY' ? '已入库' : ingestion.status === 'FAILED' ? '失败' : ingestion.status === 'PARSING' ? '解析中' : ingestion.status === 'INDEXING' ? '建索引' : ingestion.status === 'DUPLICATE' ? '重复文件' : '等待处理' }}</el-tag>
              <span class="ingestion-meta">{{ ingestion.chunkCount }} 块 · {{ formatDateTime(ingestion.updatedAt) }}</span>
            </article>
          </div>
        </section>
      </details>
      <details class="admin-editor index-details">
        <summary><Library :size="16" /><span>知识索引</span><ChevronDown :size="16" /></summary>
        <KnowledgeIndexPanel />
      </details>
    </template>
  </section>
</template>

<style scoped>
.admin-page { display: grid; gap: 16px; min-width: 0; color: var(--ink, #25302b); font-size: 14px; letter-spacing: 0; }
.dashboard-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.page-title { margin: 0; font-size: 22px; line-height: 1.4; font-weight: 650; }
.header-role { display: inline-flex; align-items: center; gap: 6px; color: var(--muted, #59665e); font-size: 13px; }
.summary-row { display: flex; flex-wrap: wrap; gap: 12px 26px; padding: 12px 0; border-top: 1px solid var(--line, #dce3df); border-bottom: 1px solid var(--line, #dce3df); }
.summary-row > div { display: flex; align-items: baseline; gap: 10px; color: var(--muted, #59665e); font-size: 13px; }
.summary-row strong { color: var(--ink, #25302b); font: 600 18px/1.3 'Consolas', monospace; }
.summary-row .status-number { color: #2563a6; }
.admin-page .panel { border: 0; border-top: 1px solid var(--line, #dce3df); border-radius: 0; box-shadow: none; background: var(--surface, #fff); }
.module-panel { padding: 18px 20px; min-width: 0; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.section-heading h2 { margin: 0; font-size: 16px; line-height: 1.4; font-weight: 650; }
.section-count { margin-left: 6px; color: var(--muted, #59665e); font: 500 13px/1 'Consolas', monospace; }
.heading-actions, .actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.actions { margin-top: 18px; }
.actions :deep(.el-button + .el-button), .heading-actions :deep(.el-button + .el-button) { margin-left: 0; }
.toolbar { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(110px, .4fr) minmax(110px, .4fr) auto; gap: 12px; align-items: end; }
.knowledge-toolbar { grid-template-columns: minmax(180px, 1fr) minmax(140px, .4fr) auto; }
.field { display: grid; min-width: 0; gap: 7px; font-size: 13px; font-weight: 600; }
.field :deep(.el-select) { width: 100%; min-width: 0; }
.filter-button { min-height: 32px; }
.admin-editor { min-width: 0; border-top: 1px solid var(--line, #dce3df); border-bottom: 1px solid var(--line, #dce3df); }
.admin-editor > summary { display: flex; align-items: center; gap: 8px; min-height: 43px; padding: 8px 0; color: var(--ink, #25302b); font-size: 14px; font-weight: 600; cursor: pointer; list-style: none; }
.admin-editor > summary::-webkit-details-marker { display: none; }
.admin-editor > summary > svg:last-child { margin-left: auto; color: var(--muted, #59665e); transition: transform 120ms ease; }
.admin-editor[open] > summary > svg:last-child { transform: rotate(180deg); }
.admin-editor > summary:focus-visible { outline: 2px solid var(--accent, #28664f); outline-offset: 3px; }
.admin-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); min-width: 0; border-top: 1px solid var(--line, #dce3df); }
.admin-grid > .panel { border: 0; padding: 18px 0; background: transparent; }
.admin-grid > .panel + .panel { margin-left: 22px; padding-left: 22px; border-left: 1px solid var(--line, #dce3df); }
.form-stack { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.field-wide { grid-column: 1 / -1; }
.reset-panel .form-stack { grid-template-columns: minmax(0, 1fr); }
.list-head, .account-row { display: grid; grid-template-columns: minmax(200px, 1fr) 70px 72px 126px; gap: 16px; align-items: center; }
.list-head, .document-list-head { padding: 10px 0; border-top: 1px solid var(--line, #dce3df); color: var(--muted, #59665e); font-size: 13px; }
.account-row, .document-row { padding: 15px 0; border-top: 1px solid var(--line, #dce3df); }
.account-row:hover, .document-row:hover { background: #f8faf9; }
.account-identity, .ingestion-file { display: flex; align-items: center; gap: 10px; min-width: 0; }
.account-avatar { display: grid; place-items: center; width: 30px; height: 30px; flex: 0 0 30px; color: #496157; border: 1px solid var(--line, #dce3df); border-radius: 4px; font-size: 13px; font-weight: 600; }
.account-identity > div, .ingestion-file > div, .document-identity { min-width: 0; }
.account-identity strong, .ingestion-file strong, .document-identity strong { display: block; overflow-wrap: anywhere; font-size: 14px; font-weight: 600; }
.account-identity span, .ingestion-file span, .document-identity > span { display: block; margin-top: 4px; color: var(--muted, #59665e); overflow-wrap: anywhere; font-size: 13px; line-height: 1.5; }
.role-chip { color: #45594f; font-size: 13px; }
.compact-actions { margin: 0; }
.file-control { position: relative; display: flex; align-items: center; gap: 10px; min-width: 0; min-height: 68px; padding: 12px; margin-bottom: 16px; border: 1px dashed #a2afa7; border-radius: 4px; background: #f8faf9; color: var(--accent, #28664f); cursor: pointer; }
.file-control:hover, .file-control:focus-within, .file-control.has-file { border-color: var(--accent, #28664f); background: #eef4f0; }
.file-control input { position: absolute; inset: 0; width: 100%; opacity: 0; cursor: pointer; }
.file-copy { display: grid; min-width: 0; gap: 4px; }
.file-copy strong { overflow-wrap: anywhere; color: var(--ink, #25302b); font-size: 14px; font-weight: 600; }
.file-copy small { color: var(--muted, #59665e); font-size: 13px; }
.ingestion-details > .panel { border: 0; padding: 0 0 12px; background: transparent; }
.ingestion-row { display: grid; grid-template-columns: minmax(0, 1fr) auto 154px; gap: 14px; align-items: center; padding: 14px 0; border-top: 1px solid var(--line, #dce3df); }
.ingestion-meta { color: var(--muted, #59665e); font-size: 13px; text-align: right; }
.document-list-head, .document-row { display: grid; grid-template-columns: 54px minmax(180px, 1fr) minmax(220px, .65fr); gap: 16px; align-items: center; }
.document-select { display: inline-flex; align-items: center; gap: 5px; color: var(--muted, #59665e); font-size: 13px; }
.document-select input { width: 15px; height: 15px; accent-color: var(--accent, #28664f); }
.tag-row { display: flex; flex-wrap: wrap; min-width: 0; gap: 5px; margin-top: 8px; }
.tag-row :deep(.el-tag) { display: inline-flex; align-items: center; max-width: 100%; height: auto; min-height: 24px; white-space: normal; border-radius: 3px; }
.tag-row :deep(.el-tag__content) { overflow-wrap: anywhere; word-break: break-word; white-space: normal; }
.document-actions { display: grid; gap: 10px; min-width: 0; }
.inline-field { grid-template-columns: 34px minmax(0, 1fr); align-items: center; gap: 8px; }
.index-details :deep(.panel) { margin-top: 4px; border-radius: 0; box-shadow: none; }
@media (max-width: 1120px) {
  .toolbar { grid-template-columns: minmax(0, 1fr) minmax(0, .6fr) minmax(0, .6fr); }
  .toolbar .filter-button { justify-self: start; }
  .knowledge-toolbar { grid-template-columns: minmax(0, 1fr) minmax(0, .6fr) auto; }
  .list-head, .account-row { grid-template-columns: minmax(0, 1fr) 70px 72px; }
  .list-head span:last-child { display: none; }
  .account-row > .actions { grid-column: 1 / -1; }
  .document-list-head, .document-row { grid-template-columns: 40px minmax(0, 1fr); }
  .document-list-head span:last-child { display: none; }
  .document-actions { grid-column: 2; }
  .admin-grid { grid-template-columns: minmax(0, 1fr); }
  .admin-grid > .panel + .panel { margin-left: 0; padding-left: 0; border-left: 0; border-top: 1px solid var(--line, #dce3df); }
}
@media (max-width: 640px) {
  .page-title { font-size: 20px; }
  .summary-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .summary-row > div { gap: 8px; }
  .module-panel { padding: 16px 12px; }
  .section-heading { align-items: flex-start; flex-wrap: wrap; }
  .toolbar, .knowledge-toolbar { grid-template-columns: minmax(0, 1fr); }
  .toolbar .filter-button { width: 100%; }
  .form-stack { grid-template-columns: minmax(0, 1fr); }
  .field-wide { grid-column: auto; }
  .list-head, .document-list-head { display: none; }
  .account-row { grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }
  .account-identity { grid-column: 1 / -1; }
  .account-row > .el-tag { justify-self: end; }
  .account-row > .actions { grid-column: 1 / -1; }
  .document-row { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .document-actions { grid-column: auto; }
  .document-select { order: 3; }
  .ingestion-row { grid-template-columns: minmax(0, 1fr) auto; gap: 12px; }
  .ingestion-meta { grid-column: 1 / -1; text-align: left; }
}
</style>
