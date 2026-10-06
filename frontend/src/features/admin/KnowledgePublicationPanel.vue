<script setup lang="ts">
import { ElCheckbox, ElCheckboxGroup } from 'element-plus/es/components/checkbox/index'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, BookOpen, Check, FileText, Plus, RefreshCw, Search, Settings2, Trash2, Upload } from 'lucide-vue-next'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import {
  batchDeleteKnowledgeDocuments, deleteKnowledgeDocument, listKnowledgeIngestions,
  uploadKnowledgeFile, type KnowledgeIngestionJob
} from '../../api/client'
import {
  createKnowledgePublication, listKnowledgePublications, publishKnowledgePublication, reparseKnowledgePublication,
  unpublishKnowledgePublication, updateKnowledgePublication, type KnowledgePublication
} from '../../api/knowledge'
import KnowledgeIndexPanel from '../student/KnowledgeIndexPanel.vue'
import { knowledgeDateLabel, knowledgeErrorMessage } from '../student/knowledgeWorkspace'

const route = useRoute(); const router = useRouter()
const tab = computed(() => ['documents', 'imports', 'maintenance'].includes(String(route.query.tab)) ? String(route.query.tab) : 'documents')
const view = computed(() => String(route.query.view || 'list'))
const documents = ref<KnowledgePublication[]>([])
const ingestions = ref<KnowledgeIngestionJob[]>([])
const selectedIds = ref<string[]>([])
const keyword = ref('')
const statusFilter = ref('')
const loading = ref(false)
const busy = ref('')
const error = ref('')
const notice = ref('')
const selectedId = ref('')
const file = ref<File>()
const upload = reactive({ title: '', tags: '', roles: ['STUDENT'] as string[] })
const editor = reactive({ title: '', content: '', category: 'general', source: '', tags: '', roles: ['STUDENT'] as string[], version: 0 })
const selectedDocument = computed(() => documents.value.find(item => item.documentId === selectedId.value))
const filtered = computed(() => documents.value.filter(item => (!keyword.value || `${item.title} ${item.content} ${item.tags?.join(' ')}`.toLowerCase().includes(keyword.value.trim().toLowerCase())) && (!statusFilter.value || item.status === statusFilter.value)))
let readRequest = 0
let poll: ReturnType<typeof setTimeout> | undefined
let disposed = false

function navigate(nextTab: string, nextView = 'list', documentId?: string) { return router.push({ path: '/admin/ai', query: { tab: nextTab, ...(nextView !== 'list' ? { view: nextView } : {}), ...(documentId ? { documentId } : {}) } }) }
function fail(cause: unknown) { error.value = knowledgeErrorMessage(cause) }
function status(value?: string) { return ({ DRAFT: '草稿', PUBLISHED: '已发布', UNPUBLISHED: '已下架', LEGACY: '历史资料' } as Record<string, string>)[value || ''] || value || '历史资料' }
function ingestionStatus(value: string) { return ({ UPLOADED: '已上传', PARSING: '解析中', INDEXING: '索引中', READY: '已解析（草稿）', FAILED: '失败', DUPLICATE: '重复文件' } as Record<string, string>)[value] || value }
function toggleSelection(documentId: string) {
  selectedIds.value = selectedIds.value.includes(documentId) ? selectedIds.value.filter(id => id !== documentId) : [...selectedIds.value, documentId]
}
async function load() {
  const token = ++readRequest; loading.value = true; error.value = ''
  if (poll) clearTimeout(poll)
  try {
    if (tab.value === 'documents') {
      const next = await listKnowledgePublications()
      if (token !== readRequest) return
      documents.value = next.map(item => ({ ...item, roles: item.roles || [] }))
      selectedIds.value = selectedIds.value.filter(id => documents.value.some(item => item.documentId === id))
      if (view.value === 'edit' && selectedId.value !== String(route.query.documentId || '')) hydrate()
    } else if (tab.value === 'imports') {
      const next = await listKnowledgeIngestions({ limit: 50 }); if (token !== readRequest) return
      ingestions.value = next
      if (!disposed && next.some(item => ['UPLOADED', 'PARSING', 'INDEXING'].includes(item.status))) poll = setTimeout(load, 5000)
    }
  } catch (cause) { if (token === readRequest) fail(cause) } finally { if (token === readRequest) loading.value = false }
}
function hydrate() {
  selectedId.value = String(route.query.documentId || '')
  const document = selectedDocument.value
  editor.title = document?.title || ''; editor.content = document?.content || ''; editor.category = document?.category || 'general'
  editor.source = document?.source || ''; editor.tags = document?.tags?.join('，') || ''; editor.roles = document?.roles?.length ? [...document.roles] : ['STUDENT']; editor.version = document?.version || 0
}
function split(value: string) { return value.split(/[,，\n;；]/).map(item => item.trim()).filter(Boolean) }
async function save() {
  if (!editor.title.trim() || !editor.content.trim() || !editor.roles.length) { error.value = '请填写标题、正文和可阅读角色'; return }
  busy.value = 'save'; error.value = ''
  const payload = { title: editor.title.trim(), content: editor.content.trim(), category: editor.category || 'general', source: editor.source.trim() || '自编资料', tags: split(editor.tags), roles: editor.roles, expectedRevision: editor.version }
  try {
    const saved = selectedId.value ? await updateKnowledgePublication(selectedId.value, payload) : await createKnowledgePublication(payload)
    documents.value = documents.value.filter(item => item.documentId !== saved.documentId).concat({ ...saved, roles: editor.roles })
    selectedId.value = saved.documentId; editor.version = saved.version || 1; notice.value = '草稿已保存，发布后才会进入学生检索'
    await navigate('documents', 'edit', saved.documentId)
  } catch (cause) { fail(cause) } finally { busy.value = '' }
}
async function publish(document: KnowledgePublication, enabled: boolean) {
  busy.value = document.documentId; error.value = ''
  try { const next = enabled ? await publishKnowledgePublication(document.documentId) : await unpublishKnowledgePublication(document.documentId); documents.value = documents.value.map(item => item.documentId === next.documentId ? { ...next, roles: document.roles } : item); notice.value = enabled ? '资料已发布' : '资料已下架'; if (selectedId.value === next.documentId) editor.version = next.version || editor.version }
  catch (cause) { fail(cause) } finally { busy.value = '' }
}
async function remove(document: KnowledgePublication) {
  try { await ElMessageBox.confirm(`删除“${document.title}”？学生的历史引用将显示资料不可访问。`, '删除资料', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  busy.value = document.documentId; error.value = ''
  try { await deleteKnowledgeDocument(document.documentId); documents.value = documents.value.filter(item => item.documentId !== document.documentId); selectedIds.value = selectedIds.value.filter(id => id !== document.documentId) }
  catch (cause) { fail(cause) } finally { busy.value = '' }
}
async function batchRemove() {
  if (!selectedIds.value.length) return
  try { await ElMessageBox.confirm(`删除已选择的 ${selectedIds.value.length} 条资料？`, '批量删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  busy.value = 'batch'; try { const result = await batchDeleteKnowledgeDocuments(selectedIds.value); documents.value = documents.value.filter(item => !result.deletedDocumentIds.includes(item.documentId)); selectedIds.value = [] } catch (cause) { fail(cause) } finally { busy.value = '' }
}
function chooseFile(event: Event) { file.value = (event.target as HTMLInputElement).files?.[0]; if (file.value && !upload.title) upload.title = file.value.name.replace(/\.[^.]+$/, '') }
async function submitFile() {
  if (!file.value || !upload.roles.length) { error.value = '请选择文件和可阅读角色'; return }
  busy.value = 'upload'; error.value = ''
  try { await uploadKnowledgeFile({ file: file.value, title: upload.title, category: 'general', source: file.value.name, tags: split(upload.tags), roles: upload.roles }); file.value = undefined; upload.title = ''; notice.value = '文件已上传，解析完成后请在资料页审核并发布'; await navigate('imports'); await load() }
  catch (cause) { fail(cause) } finally { busy.value = '' }
}
async function reparse(document: KnowledgePublication) { busy.value = document.documentId; try { await reparseKnowledgePublication(document.documentId); notice.value = '已重新解析原件'; await load() } catch (cause) { fail(cause) } finally { busy.value = '' } }
watch(() => route.fullPath, () => { if (view.value === 'new') { selectedId.value = ''; hydrate() }; void load() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; readRequest++; if (poll) clearTimeout(poll) })
</script>

<template>
  <section class="publication-workspace" data-testid="knowledge-publications">
    <nav class="publication-nav" aria-label="知识管理"><button :class="{ active: tab === 'documents' }" @click="navigate('documents')"><BookOpen :size="16" />资料</button><button :class="{ active: tab === 'imports' }" @click="navigate('imports')"><Upload :size="16" />导入任务</button><button :class="{ active: tab === 'maintenance' }" @click="navigate('maintenance')"><Settings2 :size="16" />维护</button></nav>
    <div v-if="error" class="feedback warning" role="alert">{{ error }}<el-button text @click="load">重试</el-button></div><p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
    <template v-if="tab === 'documents' && view === 'list'"><div class="toolbar"><el-input v-model="keyword" clearable placeholder="搜索标题或正文"><template #prefix><Search :size="16" /></template></el-input><el-select v-model="statusFilter" clearable placeholder="全部状态"><el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /><el-option label="已下架" value="UNPUBLISHED" /><el-option label="历史资料" value="LEGACY" /></el-select><el-button type="primary" data-testid="knowledge-publication-new" @click="navigate('documents', 'new')"><Plus :size="16" />新增</el-button><el-button :loading="loading" aria-label="刷新资料" @click="load"><RefreshCw :size="16" /></el-button></div><div v-if="selectedIds.length" class="selection"><span>已选择 {{ selectedIds.length }} 条</span><el-button text type="danger" :loading="busy === 'batch'" @click="batchRemove"><Trash2 :size="14" />删除</el-button></div><article v-for="document in filtered" :key="document.documentId" class="document-row"><el-checkbox :model-value="selectedIds.includes(document.documentId)" :aria-label="`选择${document.title}`" @change="toggleSelection(document.documentId)" /><button class="document-title" @click="navigate('documents', 'edit', document.documentId)"><strong>{{ document.title }}</strong><small>{{ document.source }} · {{ document.roles.join('、') }}</small></button><span class="status">{{ status(document.status) }}</span><div class="row-actions"><el-button size="small" :loading="busy === document.documentId" @click="publish(document, document.status !== 'PUBLISHED')">{{ document.status === 'PUBLISHED' ? '下架' : '发布' }}</el-button><el-button text aria-label="删除资料" @click="remove(document)"><Trash2 :size="15" /></el-button></div></article><p v-if="!loading && !filtered.length" class="empty">暂无符合条件的资料。</p></template>
    <template v-else-if="tab === 'documents'"><div class="editor-actions"><el-button text @click="navigate('documents')"><ArrowLeft :size="16" />资料</el-button><el-button type="primary" :loading="busy === 'save'" data-testid="knowledge-publication-save" @click="save"><Check :size="15" />保存草稿</el-button><el-button v-if="selectedDocument" :loading="busy === selectedId" @click="publish(selectedDocument, selectedDocument.status !== 'PUBLISHED')">{{ selectedDocument.status === 'PUBLISHED' ? '下架' : '发布' }}</el-button></div><div class="editor"><label>标题<el-input v-model="editor.title" maxlength="200" /></label><div class="editor-meta"><label>来源<el-input v-model="editor.source" maxlength="128" /></label><label>分类<el-input v-model="editor.category" maxlength="100" /></label><label>标签<el-input v-model="editor.tags" maxlength="500" /></label></div><label>可阅读角色<el-checkbox-group v-model="editor.roles"><el-checkbox value="STUDENT">学生</el-checkbox><el-checkbox value="COMPANY">企业</el-checkbox><el-checkbox value="ADMIN">管理员</el-checkbox></el-checkbox-group></label><label>正文<el-input v-model="editor.content" type="textarea" :rows="15" /></label><details><summary>预览正文</summary><article class="content-preview">{{ editor.content }}</article></details><el-button v-if="selectedDocument" text :loading="busy === selectedId" @click="reparse(selectedDocument)"><FileText :size="15" />从原件补充页码</el-button></div></template>
    <template v-else-if="tab === 'imports'"><details class="import-form"><summary><Upload :size="16" />导入新文件</summary><div class="upload-form"><label class="file-picker"><input type="file" accept=".txt,.md,.pdf,.doc,.docx" @change="chooseFile" /><span>{{ file?.name || '选择文件' }}</span></label><label>标题<el-input v-model="upload.title" /></label><label>标签<el-input v-model="upload.tags" /></label><el-checkbox-group v-model="upload.roles"><el-checkbox value="STUDENT">学生</el-checkbox><el-checkbox value="COMPANY">企业</el-checkbox><el-checkbox value="ADMIN">管理员</el-checkbox></el-checkbox-group><el-button type="primary" :loading="busy === 'upload'" @click="submitFile">上传并解析</el-button></div></details><article v-for="item in ingestions" :key="item.jobId" class="ingestion-row"><div><strong>{{ item.title || item.fileName }}</strong><small>{{ item.fileName }} · {{ knowledgeDateLabel(item.updatedAt) }}</small><p v-if="item.error || item.status === 'FAILED'" class="ingestion-error">{{ item.error || item.message }}</p></div><span class="status">{{ ingestionStatus(item.status) }}</span><el-button v-if="item.documentId" size="small" @click="navigate('documents', 'edit', item.documentId)">审核资料</el-button></article><p v-if="!loading && !ingestions.length" class="empty">暂无导入任务。</p></template>
    <KnowledgeIndexPanel v-else />
  </section>
</template>

<style scoped>
.publication-workspace{display:grid;gap:14px;min-width:0;color:var(--ink,#1f2724);font-size:13px;overflow-wrap:anywhere}.publication-nav{display:flex;gap:4px;border-bottom:1px solid var(--line,#e4ebe7)}.publication-nav button{display:flex;align-items:center;gap:6px;padding:11px 14px;border:0;border-bottom:2px solid transparent;background:none;color:var(--muted,#66716c);font-size:13px;cursor:pointer}.publication-nav button.active{color:var(--accent,#28664f);border-bottom-color:var(--accent,#28664f)}.toolbar{display:grid;grid-template-columns:minmax(0,1fr) 130px auto auto;gap:8px}.document-row{display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid var(--line,#e4ebe7)}.document-title{display:grid;gap:5px;flex:1;min-width:0;border:0;background:none;padding:0;text-align:left;color:inherit;cursor:pointer}.document-title strong,.ingestion-row strong{font-size:14px;line-height:1.5}.document-title small,.ingestion-row small{color:var(--muted,#66716c);font-size:12px;line-height:1.5}.row-actions,.editor-actions,.selection{display:flex;gap:7px;align-items:center;flex-wrap:wrap}.status{flex:none;font-size:12px;color:#66716c;min-width:40px}.editor{display:grid;gap:13px}.editor>label,.editor-meta>label,.upload-form>label{display:grid;gap:6px;font-size:12px;color:#53615a}.editor-meta{display:grid;grid-template-columns:2fr 1fr 1fr;gap:10px}.editor details summary,.import-form summary{display:flex;align-items:center;gap:6px;font-size:13px;color:var(--accent,#28664f);cursor:pointer;padding:10px 0}.content-preview{white-space:pre-wrap;line-height:1.8;font-size:14px;margin:10px 0;padding:10px 0;border-top:1px solid var(--line,#e4ebe7)}.upload-form{display:grid;gap:10px;max-width:640px;margin-top:8px}.file-picker input{max-width:100%}.file-picker>span{font-size:12px;color:#66716c}.upload-form>.el-button{justify-self:start}.ingestion-row{display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid var(--line,#e4ebe7)}.ingestion-row>div{display:grid;gap:5px;flex:1;min-width:0}.ingestion-error{margin:0;color:#97622e;font-size:12px}.feedback{display:flex;align-items:center;justify-content:space-between;gap:10px;margin:0;padding:10px 12px;border-radius:5px;font-size:12px;line-height:1.7}.feedback.warning{background:#fff6e8;color:#885826}.feedback.success{background:#edf8f1;color:#28664f}.empty{margin:0;padding:14px 0;color:var(--muted,#66716c);font-size:13px}.publication-workspace :deep(.el-button){margin-left:0}.publication-workspace :deep(.el-select){min-width:0}.publication-workspace :deep(.el-checkbox-group){display:flex;flex-wrap:wrap}.publication-workspace :deep(.el-checkbox){margin-right:15px}
@media(max-width:640px){.publication-nav button{flex:1;justify-content:center;padding:10px 2px;font-size:12px}.toolbar{grid-template-columns:minmax(0,1fr) 92px}.toolbar>.el-button{justify-self:start}.document-row{gap:8px;align-items:flex-start;flex-wrap:wrap}.document-title{flex:1 1 calc(100% - 90px)}.document-row>.row-actions{margin-left:30px}.editor-meta{grid-template-columns:1fr}.ingestion-row{align-items:flex-start;flex-wrap:wrap}.ingestion-row>div{flex:1 1 calc(100% - 70px)}.ingestion-row>.el-button{margin-top:4px}.editor-actions{gap:6px}}
</style>
