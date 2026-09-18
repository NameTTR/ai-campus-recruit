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
  <section class="page">
    <header class="page-header">
      <div><h1 class="page-title">{{ pageTitle }}</h1><p class="page-subtitle">维护账号权限与可检索的校园招聘知识资料</p></div>
    </header>

    <template v-if="activeModule === 'accounts'">
      <section class="panel module-panel">
        <h2 class="panel-title"><span>账号筛选</span><Users :size="19" /></h2>
        <div class="toolbar">
          <el-input v-model="accountFilters.keyword" clearable placeholder="账号或名称" @keyup.enter="loadAccounts" />
          <el-select v-model="accountFilters.role" clearable placeholder="角色"><el-option label="学生" value="STUDENT" /><el-option label="企业" value="COMPANY" /><el-option label="管理员" value="ADMIN" /></el-select>
          <el-select v-model="accountFilters.status" clearable placeholder="状态"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /><el-option label="锁定" value="LOCKED" /></el-select>
          <el-button type="primary" :loading="accountsLoading" @click="loadAccounts">查询</el-button>
        </div>
      </section>
      <section class="grid two">
        <article class="panel module-panel">
          <h2 class="panel-title"><span>创建账号</span><Plus :size="19" /></h2>
          <div class="form-stack"><el-input v-model="accountForm.username" placeholder="账号" /><el-input v-model="accountForm.displayName" placeholder="显示名称" /><el-input v-model="accountForm.password" type="password" show-password placeholder="初始密码" /><el-select v-model="accountForm.role"><el-option label="学生" value="STUDENT" /><el-option label="企业" value="COMPANY" /><el-option label="管理员" value="ADMIN" /></el-select></div>
          <div class="actions"><el-button type="primary" :loading="actionLoading" @click="submitAccount">创建账号</el-button></div>
        </article>
        <article class="panel module-panel">
          <h2 class="panel-title"><span>重置密码</span><KeyRound :size="19" /></h2>
          <div class="form-stack"><el-select v-model="passwordForm.accountId" clearable placeholder="选择账号"><el-option v-for="account in accounts" :key="account.accountId" :label="`${account.username} · ${account.displayName}`" :value="account.accountId" /></el-select><el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="新密码" /></div>
          <div class="actions"><el-button :loading="actionLoading" @click="resetPassword">更新密码</el-button></div>
        </article>
      </section>
      <section class="panel module-panel" v-loading="accountsLoading">
        <h2 class="panel-title"><span>账号列表</span><ShieldCheck :size="19" /></h2>
        <el-empty v-if="!accounts.length" description="暂无账号" />
        <div v-else class="account-list"><article v-for="account in accounts" :key="account.accountId" class="account-row"><div><strong>{{ account.displayName }}</strong><span>{{ account.username }} · {{ account.role }} · {{ formatDateTime(account.updatedAt) }}</span></div><el-tag :type="account.status === 'ACTIVE' ? 'success' : 'warning'">{{ account.status }}</el-tag><div class="actions compact"><el-button size="small" :loading="actionLoading" @click="changeAccountStatus(account, 'ACTIVE')">启用</el-button><el-button size="small" :loading="actionLoading" @click="changeAccountStatus(account, 'DISABLED')">停用</el-button></div></article></div>
      </section>
    </template>

    <template v-else>
      <section class="panel module-panel" v-loading="knowledgeLoading">
        <h2 class="panel-title"><span>知识文档</span><span class="panel-title-actions"><el-button circle size="small" @click="loadKnowledge"><RefreshCw :size="15" /></el-button><Library :size="19" /></span></h2>
        <div class="knowledge-metrics"><span>文档 {{ knowledgeStats?.documentCount || 0 }}</span><span>知识块 {{ knowledgeStats?.chunkCount || 0 }}</span><span>{{ knowledgeStats?.corpusVersion || '等待加载' }}</span></div>
        <div class="toolbar"><el-input v-model="knowledgeFilters.keyword" clearable placeholder="搜索标题、标签或内容" @keyup.enter="loadKnowledge" /><el-select v-model="knowledgeFilters.role"><el-option label="管理员" value="ADMIN" /><el-option label="学生" value="STUDENT" /><el-option label="企业" value="COMPANY" /></el-select><el-button type="primary" :loading="knowledgeLoading" @click="loadKnowledge">查询</el-button></div>
      </section>
      <section class="grid two">
        <article class="panel module-panel">
          <h2 class="panel-title"><span>手工新增</span><Plus :size="19" /></h2>
          <div class="form-stack"><el-input v-model="knowledgeForm.title" placeholder="标题" /><el-input v-model="knowledgeForm.category" placeholder="分类" /><el-input v-model="knowledgeForm.tags" placeholder="标签，使用逗号分隔" /><el-input v-model="knowledgeForm.roles" placeholder="可读取角色，例如 STUDENT,COMPANY" /><el-input v-model="knowledgeForm.content" type="textarea" :rows="7" placeholder="文档内容" /></div>
          <div class="actions"><el-button type="primary" :loading="actionLoading" @click="submitKnowledgeDocument">创建文档</el-button></div>
        </article>
        <article class="panel module-panel">
          <h2 class="panel-title"><span>上传导入</span><Upload :size="19" /></h2>
          <div class="form-stack"><label class="file-control"><Upload :size="16" /><span>{{ selectedFile?.name || '选择 TXT、MD、PDF、DOC、DOCX 文件' }}</span><input ref="fileInput" type="file" accept=".txt,.md,.pdf,.doc,.docx" @change="chooseFile" /></label><el-input v-model="uploadForm.title" placeholder="文档标题" /><el-input v-model="uploadForm.tags" placeholder="标签，使用逗号分隔" /><el-input v-model="uploadForm.roles" placeholder="可读取角色" /></div>
          <div class="actions"><el-button type="primary" :loading="actionLoading" @click="submitKnowledgeFile">上传到知识库</el-button></div>
        </article>
      </section>
      <section class="panel module-panel" v-loading="knowledgeLoading">
        <h2 class="panel-title"><span>导入任务</span><Upload :size="19" /></h2>
        <el-empty v-if="!ingestions.length" description="暂无导入任务" />
        <div v-else class="ingestion-list"><article v-for="ingestion in ingestions" :key="ingestion.jobId" class="ingestion-row"><div><strong>{{ ingestion.title }}</strong><span>{{ ingestion.fileName }} · {{ ingestion.source }}</span></div><el-tag :type="ingestion.status === 'READY' ? 'success' : ingestion.status === 'FAILED' ? 'danger' : 'warning'">{{ ingestion.status }}</el-tag><span>{{ ingestion.chunkCount }} 块 · {{ formatDateTime(ingestion.updatedAt) }}</span></article></div>
      </section>
      <section class="panel module-panel" v-loading="knowledgeLoading">
        <h2 class="panel-title"><span>已入库文档</span><span class="panel-title-actions"><el-button size="small" type="danger" plain :disabled="!selectedDocumentIds.length" :loading="actionLoading" @click="removeSelectedDocuments">批量删除</el-button><Library :size="19" /></span></h2>
        <el-empty v-if="!documents.length" description="暂无知识文档" />
        <div v-else class="document-list"><article v-for="document in documents" :key="document.documentId" class="document-row"><label class="document-select"><input v-model="selectedDocumentIds" type="checkbox" :value="document.documentId" />选择</label><div><strong>{{ document.title }}</strong><span>{{ document.category }} · {{ document.source }} · {{ formatDateTime(document.createdAt) }}</span><div class="tag-row"><el-tag v-for="tag in document.tags" :key="tag" type="info">{{ tag }}</el-tag></div></div><div class="document-actions"><el-input v-model="roleDrafts[document.documentId]" size="small" placeholder="角色" /><div class="actions compact"><el-button size="small" :loading="actionLoading" @click="saveDocumentRoles(document)">保存权限</el-button><el-button size="small" type="danger" plain :loading="actionLoading" @click="removeDocument(document)"><Trash2 :size="14" />删除</el-button></div></div></article></div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.toolbar,.actions,.panel-title-actions{display:flex;flex-wrap:wrap;align-items:center;gap:10px}.toolbar :deep(.el-input){flex:1;min-width:180px}.toolbar :deep(.el-select){min-width:130px}.form-stack{display:grid;gap:10px}.actions{margin-top:14px}.actions.compact{margin-top:0}.account-list,.document-list,.ingestion-list{display:grid;gap:10px}.account-row,.document-row,.ingestion-row{display:grid;grid-template-columns:minmax(0,1fr) auto auto;gap:12px;align-items:center;padding:12px 0;border-bottom:1px solid #eaecf0}.document-row{grid-template-columns:auto minmax(0,1fr) auto}.account-row span,.document-row span,.ingestion-row span{display:block;margin-top:4px;color:#667085;font-size:13px}.knowledge-metrics{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:14px;color:#475467;font-size:13px}.knowledge-metrics span{padding:5px 8px;border:1px solid #dbe5ef;border-radius:6px}.file-control{display:flex;align-items:center;gap:8px;min-height:38px;padding:0 10px;border:1px dashed #98a2b3;border-radius:6px;color:#475467;cursor:pointer}.file-control input{display:none}.document-select{display:flex;align-items:center;gap:4px;color:#667085;font-size:12px}.document-actions{display:grid;gap:8px;min-width:230px}@media (max-width:760px){.account-row,.document-row,.ingestion-row{grid-template-columns:1fr;align-items:start}.document-actions{min-width:0}.toolbar :deep(.el-input),.toolbar :deep(.el-select){width:100%}}
</style>
