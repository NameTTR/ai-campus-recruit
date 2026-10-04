<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, ChevronDown, ChevronUp, Download, History, Plus, RefreshCw, Save, SearchCheck, Undo2 } from 'lucide-vue-next'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElCheckbox } from 'element-plus/es/components/checkbox/index'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index'
import { ElConfigProvider } from 'element-plus/es/components/config-provider/index'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import {
  applyResumeSuggestion, createResumeDraft, createResumeExport, diagnoseResumeDraft,
  getMasterResumeProfile, getResumeDraft, getResumeExport, getResumeExportFile, importResumeProfile,
  listJobs, listLearningPlans, listResumeDraftRevisions, listResumeDrafts,
  listResumes, listResumeTemplates, restoreResumeDraft, saveMasterResumeProfile,
  updateResumeDraft, uploadResume, uploadResumePhoto,
  type JobSummary, type ResumeDraft, type ResumeDraftEntry, type ResumeDraftRevision,
  type ResumeDraftSuggestion, type ResumeExportStatus, type ResumeImportCandidate,
  type ResumeMasterProfile, type ResumeSummary, type ResumeTemplateInfo,
  type ResumeWorkspaceExperience, type ResumeWorkspaceProfileData
} from '../../api/client'
import {
  confirmedResumeProfile, copyResumeProfile, isResumeRevisionConflict, mergeResumeCandidate,
  moveResumeItem, resumeDraftCanAutoPreview, resumeDraftFingerprint, resumeExportStatusLabel, resumeLearningCandidates,
  resumeSourceLabel, resumeWorkspaceMessage,
  studentSource, suggestionNeedsProfile, suggestionMatchesDraft, editableResumeLines, editableResumeValues, type ResumeLearningCandidate
} from './resumeWorkspace'
import ResumePdfPreview from './ResumePdfPreview.vue'

const props = defineProps<{ targetRole?: string; resumeId?: string }>()
const emit = defineEmits<{ confirmed: [resumeId: string] }>()
const step = ref<'profile' | 'template' | 'resume'>('profile')
const profileSection = ref<'basics' | 'experience' | 'optional'>('basics')
const workspaceElement = ref<HTMLElement>()
const diagnosisOpen = ref(false)
const previewError = ref('')
const profile = ref<ResumeMasterProfile>()
const profileInput = ref<ResumeWorkspaceProfileData>(copyResumeProfile())
const profileSourceResumeId = ref<string>()
const profileConfirmed = ref(false)
const profileConflict = ref<ResumeMasterProfile>()
const profileInputBackup = ref<ResumeWorkspaceProfileData>()
const templates = ref<ResumeTemplateInfo[]>([])
const jobs = ref<JobSummary[]>([])
const availableResumes = ref<ResumeSummary[]>([])
const drafts = ref<ResumeDraft[]>([])
const localDrafts = ref<Record<string, ResumeDraft>>({})
const draftInputBackups = ref<Record<string, ResumeDraft>>({})
const savedFingerprints = ref<Record<string, string>>({})
const selectedDraftId = ref('')
const selectedDraft = computed(() => localDrafts.value[selectedDraftId.value])
const draftDirty = computed(() => Boolean(selectedDraft.value && resumeDraftFingerprint(selectedDraft.value) !== savedFingerprints.value[selectedDraft.value.id]))
const profileDirty = computed(() => JSON.stringify(profileInput.value) !== JSON.stringify(copyResumeProfile(profile.value?.data)))
const loading = ref(false)
const profileSaving = ref(false)
const draftSaving = ref(false)
const generating = ref(false)
const diagnosing = ref(false)
const importing = ref(false)
const photoUploading = ref(false)
const exportLoading = ref(false)
const historyLoading = ref(false)
const importId = ref('')
const importCandidate = ref<ResumeImportCandidate>()
const importConfirmed = ref(false)
const generationTemplateId = ref('T01')
const targetJobId = ref('')
const customTargetRole = ref(props.targetRole?.trim() || '')
const chosenJob = computed(() => jobs.value.find(job => job.jobId === targetJobId.value))
const targetRole = computed(() => chosenJob.value?.title || customTargetRole.value.trim() || '通用岗位')
const revisions = ref<ResumeDraftRevision[]>([])
const historyOpen = ref(false)
const previewRevision = ref<ResumeDraftRevision>()
const draftConflict = ref<ResumeDraft>()
const conflictOpen = ref(false)
const exportStatus = ref<ResumeExportStatus>()
const exportDraftId = ref('')
const exportUrls = ref<{ docx: string; pdf: string }>({ docx: '', pdf: '' })
const exportExpired = ref(false)
const photoPreviewUrl = ref('')
const learningCandidates = ref<ResumeLearningCandidate[]>([])
const learningOpen = ref(false)
const learningLoading = ref(false)
const resumeFileInput = ref<HTMLInputElement>()
const photoFileInput = ref<HTMLInputElement>()
const expandedSuggestions = ref(false)
const disposed = ref(false)
let exportRequest = 0
let workspaceRequest = 0
let historyRequest = 0
let photoObjectUrl = ''
const generatedSource = computed(() => !selectedDraft.value?.data.generationSource?.startsWith('AI_DASHSCOPE') ? '根据已确认资料整理（基础模式）' : 'AI 整理，事实来源可核对')
const availableSuggestions = computed(() => selectedDraft.value?.data.suggestions.filter(item => item.status !== 'APPLIED') || [])
const displaySuggestions = computed(() => expandedSuggestions.value ? selectedDraft.value?.data.suggestions || [] : availableSuggestions.value.slice(0, 3))
const profileSummary = computed(() => [profileInput.value.basics.name || '未填写姓名', `${profileInput.value.education.length} 段教育`, `${profileInput.value.experiences.length} 段经历`].join(' · '))
const hasDraftContent = computed(() => Boolean(selectedDraft.value?.data.blocks.some(block => block.visible && block.entries.some(entry => entry.visible && (entry.title.trim() || entry.bullets.some(line => line.trim()))))))

async function goToStep(value: 'profile' | 'template' | 'resume') {
  step.value = value
  await nextTick()
  workspaceElement.value?.scrollIntoView({ block: 'start', behavior: 'smooth' })
}
async function continueFromProfile() {
  if ((!profile.value?.revision || profileDirty.value) && !await saveProfile(false)) return
  await goToStep('template')
}

function cloneDraft(draft: ResumeDraft): ResumeDraft { return JSON.parse(JSON.stringify(draft)) as ResumeDraft }
function newId(prefix: string) { return `${prefix}-${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`}` }
function showError(error: unknown, fallback: string) { ElMessage.error(resumeWorkspaceMessage(error, fallback)) }
function templateName(id: string) { return templates.value.find(template => template.id === id)?.name || id }
function experienceTypeLabel(type: string) { return ({ PROJECT: '项目 / 课程实践', INTERNSHIP: '实习', CAMPUS: '校园活动', COMPETITION: '竞赛实践' } as Record<string, string>)[type] || '其他经历' }
function formatTime(value: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未保存' }
function historyReason(reason: string) { return ({ CREATED: '生成草稿', UPDATED: '保存编辑', SUGGESTION_APPLIED: '采纳建议', RESTORED: '恢复历史', DIAGNOSED: '生成诊断' } as Record<string, string>)[reason?.toUpperCase()] || '草稿修订' }
function humanIssue(value: string) {
  if (/font|10\s*pt/i.test(value)) return '正文字号不能小于 10pt，请精简内容或使用双页模板。'
  if (/page|overflow|exceed/i.test(value)) return '内容超过模板页数，请精简文字、隐藏条目，或切换双页模板。'
  if (/photo/i.test(value)) return '照片暂时无法排版，请重新上传 JPG / PNG 后重试。'
  return /[\u4e00-\u9fff]/.test(value) ? value : '请检查正文排版并调整后重试。'
}
function updateDraftRecord(updated: ResumeDraft) {
  drafts.value = [updated, ...drafts.value.filter(item => item.id !== updated.id)]
  localDrafts.value[updated.id] = cloneDraft(updated)
  savedFingerprints.value[updated.id] = resumeDraftFingerprint(updated)
  if (updated.resumeId && updated.confirmed) emit('confirmed', updated.resumeId)
}
async function load() {
  const request = ++workspaceRequest
  loading.value = true
  try {
    const [master, templateItems, draftItems, jobItems, resumeItems] = await Promise.all([
      getMasterResumeProfile(), listResumeTemplates(), listResumeDrafts(), listJobs(), listResumes()
    ])
    if (request !== workspaceRequest || disposed.value) return
    profile.value = master
    profileInput.value = copyResumeProfile(master.data)
    profileSourceResumeId.value = master.sourceResumeId
    profileConfirmed.value = false
    templates.value = templateItems
    jobs.value = jobItems.filter(job => !job.status || ['OPEN', 'ACTIVE', 'PUBLISHED'].includes(job.status))
    availableResumes.value = resumeItems
    drafts.value = draftItems
    for (const draft of draftItems) {
      const local = localDrafts.value[draft.id]
      if (!local || resumeDraftFingerprint(local) === savedFingerprints.value[draft.id]) localDrafts.value[draft.id] = cloneDraft(draft)
      savedFingerprints.value[draft.id] = resumeDraftFingerprint(draft)
    }
    if (!templates.value.some(item => item.id === generationTemplateId.value)) generationTemplateId.value = templateItems[0]?.id || ''
    if (!selectedDraftId.value) selectedDraftId.value = draftItems[0]?.id || ''
    if (selectedDraftId.value) step.value = 'resume'
    if (!importId.value) importId.value = props.resumeId || resumeItems[0]?.resumeId || ''
  } catch (error) { showError(error, '简历工作区加载失败，请稍后重试。') }
  finally { if (request === workspaceRequest) loading.value = false }
}
function addEducation() {
  profileInput.value.education.push({ id: newId('education'), school: '', major: '', degree: '', startDate: '', endDate: '', graduationDate: '', courses: [], notes: '', source: studentSource() })
}
function addSkill() { profileInput.value.skills.push({ id: newId('skill'), name: '', source: studentSource() }) }
function addExperience(type = 'PROJECT') {
  profileInput.value.experiences.push({ id: newId('experience'), type, title: '', organization: '', startDate: '', endDate: '', role: '', actions: '', methods: '', results: '', skills: [], links: [], source: studentSource(), confirmed: false })
}
function addCredential() { profileInput.value.credentials.push({ id: newId('credential'), title: '', date: '', description: '', source: studentSource() }) }
function markExperienceEdited(experience: ResumeWorkspaceExperience) { experience.confirmed = false; if (experience.source) experience.source.confirmed = false }
async function saveProfile(announce = true): Promise<boolean> {
  if (!profileConfirmed.value) { ElMessage.warning('请先核对资料，并勾选确认这些内容真实。'); return false }
  if (!profileInput.value.basics.name.trim()) { ElMessage.warning('请填写姓名。'); return false }
  if (profileInput.value.skills.some(item => !item.name.trim()) || profileInput.value.education.some(item => !item.school.trim()) || profileInput.value.experiences.some(item => !item.title.trim()) || profileInput.value.credentials.some(item => !item.title.trim())) {
    ElMessage.warning('请填写新增条目的名称，或删除不需要的空条目。'); return false
  }
  profileSaving.value = true
  try {
    const submittedInput = JSON.stringify(profileInput.value)
    const saved = await saveMasterResumeProfile({ expectedRevision: profile.value?.revision || 0, data: confirmedResumeProfile(profileInput.value), sourceResumeId: profileSourceResumeId.value, confirmed: true })
    profile.value = saved
    if (JSON.stringify(profileInput.value) === submittedInput) profileInput.value = copyResumeProfile(saved.data)
    else profileConfirmed.value = false
    profileConflict.value = undefined
    for (const draft of Object.values(localDrafts.value)) draft.sourceStale = draft.profileRevision !== saved.revision
    if (announce) ElMessage.success('资料已保存。')
    return true
  } catch (error) {
    if (isResumeRevisionConflict(error)) {
      try { profileConflict.value = await getMasterResumeProfile() } catch { /* Preserve input if refreshing the latest profile fails. */ }
    }
    showError(error, '主资料保存失败，你的输入已保留。')
    return false
  } finally { profileSaving.value = false }
}
function useServerProfile() {
  if (!profileConflict.value) return
  profileInputBackup.value = copyResumeProfile(profileInput.value)
  profile.value = profileConflict.value
  profileInput.value = copyResumeProfile(profileConflict.value.data)
  profileSourceResumeId.value = profileConflict.value.sourceResumeId
  profileConflict.value = undefined
  profileConfirmed.value = false
}
function keepLocalProfile() {
  if (!profileConflict.value) return
  profile.value = profileConflict.value
  profileConflict.value = undefined
  profileConfirmed.value = false
  ElMessage.info('本地输入已保留。重新核对并确认后，可保存为新的资料版本。')
}
async function importExisting() {
  if (!importId.value) { ElMessage.warning('请选择已有简历或上传文件。'); return }
  importing.value = true
  try { importCandidate.value = await importResumeProfile(importId.value); importConfirmed.value = false }
  catch (error) { showError(error, '简历导入失败，请重试。') }
  finally { importing.value = false }
}
async function uploadImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!/\.(docx?|pdf)$/i.test(file.name)) { ElMessage.warning('请选择文本型 PDF、DOC 或 DOCX。'); return }
  if (file.size > 20 * 1024 * 1024) { ElMessage.warning('简历文件不能超过 20 MB。'); return }
  importing.value = true
  try {
    const uploaded = await uploadResume(file)
    availableResumes.value = [uploaded, ...availableResumes.value.filter(item => item.resumeId !== uploaded.resumeId)]
    importId.value = uploaded.resumeId
    importCandidate.value = await importResumeProfile(uploaded.resumeId)
    importConfirmed.value = false
    ElMessage.success('上传原件已保留。请检查候选内容，再决定加入资料。')
  } catch (error) { showError(error, '上传或提取简历失败，请重试。') }
  finally { importing.value = false }
}
function acceptImport() {
  if (!importCandidate.value || !importConfirmed.value) return
  profileInput.value = mergeResumeCandidate(profileInput.value, importCandidate.value.data)
  profileSourceResumeId.value = importCandidate.value.resumeId
  importCandidate.value = undefined
  profileConfirmed.value = false
  ElMessage.success('候选内容已加入编辑区，仍需逐项核对并确认保存。')
}
async function uploadPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type) || !/\.(jpe?g|png)$/i.test(file.name)) { ElMessage.warning('照片仅支持 JPG、PNG。'); return }
  if (file.size > 2 * 1024 * 1024) { ElMessage.warning('照片不能超过 2 MB。'); return }
  photoUploading.value = true
  try {
    const uploaded = await uploadResumePhoto(file)
    profileInput.value.basics.photoObjectKey = uploaded.objectKey
    if (photoObjectUrl) URL.revokeObjectURL(photoObjectUrl)
    photoObjectUrl = URL.createObjectURL(file)
    photoPreviewUrl.value = photoObjectUrl
    profileConfirmed.value = false
    ElMessage.success('照片已上传。保存资料后，新草稿会使用这张照片。')
  } catch (error) { showError(error, '照片上传失败，请重试。') }
  finally { photoUploading.value = false }
}
function removePhoto() {
  profileInput.value.basics.photoObjectKey = undefined
  if (photoObjectUrl) URL.revokeObjectURL(photoObjectUrl)
  photoObjectUrl = ''
  photoPreviewUrl.value = ''
  profileConfirmed.value = false
}
async function generate() {
  if (!generationTemplateId.value) { ElMessage.warning('请选择模板。'); return }
  if (!profile.value?.revision || profileDirty.value) {
    if (!await saveProfile(false)) return
  }
  generating.value = true
  try {
    const draft = await createResumeDraft({ templateId: generationTemplateId.value, targetRole: targetRole.value, jobId: targetJobId.value || undefined, profileRevision: profile.value?.revision, resumeId: props.resumeId })
    updateDraftRecord(draft)
    selectedDraftId.value = draft.id
    await goToStep('resume')
    ElMessage.success('简历已生成，正在准备预览。')
    if (selectedDraftId.value === draft.id && exportDraftId.value !== draft.id) void autoPreview()
  } catch (error) { showError(error, '草稿生成失败，已保存资料仍然保留。') }
  finally { generating.value = false }
}
async function readHistory() {
  const id = selectedDraftId.value
  if (!id) return
  const request = ++historyRequest
  historyLoading.value = true
  try {
    const result = await listResumeDraftRevisions(id)
    if (request === historyRequest && selectedDraftId.value === id) revisions.value = [...result].sort((a, b) => b.revision - a.revision)
  } catch (error) { showError(error, '历史版本加载失败，请重试。') }
  finally { if (request === historyRequest) historyLoading.value = false }
}
async function saveDraft(announce = true): Promise<ResumeDraft | undefined> {
  const draft = selectedDraft.value
  if (!draft) return
  if (!draftDirty.value && draft.confirmed) return draft
  draftSaving.value = true
  try {
    const submittedFingerprint = resumeDraftFingerprint(draft)
    const updated = await updateResumeDraft(draft.id, { expectedRevision: draft.revision, templateId: draft.templateId, data: draft.data, confirm: true })
    const editsWhileSaving = resumeDraftFingerprint(localDrafts.value[draft.id]!) !== submittedFingerprint
      ? cloneDraft(localDrafts.value[draft.id]!) : undefined
    updateDraftRecord(updated)
    if (editsWhileSaving) {
      editsWhileSaving.revision = updated.revision
      editsWhileSaving.confirmed = false
      localDrafts.value[updated.id] = editsWhileSaving
    }
    draftConflict.value = undefined
    if (historyOpen.value) await readHistory()
    if (announce) ElMessage.success('当前草稿已确认保存，可用于诊断、匹配与导出。')
    return updated
  } catch (error) {
    if (isResumeRevisionConflict(error)) {
      try { draftConflict.value = await getResumeDraft(draft.id); conflictOpen.value = true } catch { /* Retain unsaved edits if refreshing fails. */ }
    }
    showError(error, '草稿保存失败，编辑内容已保留。')
    return undefined
  } finally { draftSaving.value = false }
}
function useServerDraft() {
  if (!draftConflict.value) return
  if (selectedDraft.value) draftInputBackups.value[selectedDraft.value.id] = cloneDraft(selectedDraft.value)
  updateDraftRecord(draftConflict.value)
  conflictOpen.value = false
  draftConflict.value = undefined
}
function keepLocalDraft() {
  if (!draftConflict.value || !selectedDraft.value) return
  selectedDraft.value.revision = draftConflict.value.revision
  savedFingerprints.value[selectedDraft.value.id] = resumeDraftFingerprint(draftConflict.value)
  drafts.value = drafts.value.map(item => item.id === draftConflict.value?.id ? draftConflict.value! : item)
  conflictOpen.value = false
  draftConflict.value = undefined
  ElMessage.info('本地编辑已保留。核对服务器内容后，再点击确认保存。')
}
function restoreLocalDraftInput() {
  const current = selectedDraft.value
  const backup = draftInputBackups.value[selectedDraftId.value]
  if (!current || !backup) return
  localDrafts.value[current.id] = { ...cloneDraft(current), templateId: backup.templateId, data: cloneDraft(backup).data, confirmed: false }
  delete draftInputBackups.value[current.id]
}
function restoreLocalProfileInput() {
  if (!profileInputBackup.value) return
  profileInput.value = copyResumeProfile(profileInputBackup.value)
  profileInputBackup.value = undefined
  profileConfirmed.value = false
}
async function diagnose() {
  const saved = await saveDraft(false)
  if (!saved) return
  diagnosing.value = true
  try {
    updateDraftRecord(await diagnoseResumeDraft(saved.id))
    diagnosisOpen.value = true
    expandedSuggestions.value = false
    await readHistory()
    ElMessage.success('诊断已完成，优先查看最值得修改的三项。')
  } catch (error) { showError(error, '诊断失败，已保存草稿仍然保留，可重试。') }
  finally { diagnosing.value = false }
}
async function applySuggestion(item: ResumeDraftSuggestion) {
  const draft = selectedDraft.value
  if (!draft || item.status === 'APPLIED') return
  if (!suggestionMatchesDraft(draft.data, item)) { ElMessage.warning('这条建议对应的原文已变化，请先重新诊断。'); return }
  const saved = await saveDraft(false)
  if (!saved) return
  draftSaving.value = true
  try {
    updateDraftRecord(await applyResumeSuggestion(saved.id, { expectedRevision: saved.revision, suggestionId: item.id }))
    await readHistory()
    ElMessage.success('建议已采纳，历史中保留了修改前的版本。')
  } catch (error) { showError(error, '建议采纳失败，请重新诊断后重试。') }
  finally { draftSaving.value = false }
}
async function restoreRevision(revision: number) {
  let draft = selectedDraft.value
  if (!draft) return
  if (draftDirty.value) {
    const saved = await saveDraft(false)
    if (!saved) return
    draft = saved
  }
  draftSaving.value = true
  try {
    updateDraftRecord(await restoreResumeDraft(draft.id, { expectedRevision: draft.revision, revision }))
    previewRevision.value = undefined
    await readHistory()
    ElMessage.success('历史内容已恢复为新版本，原有历史仍可查看。')
  } catch (error) { showError(error, '恢复失败，当前编辑内容已保留。') }
  finally { draftSaving.value = false }
}
async function undoLatest() {
  await readHistory()
  const draft = selectedDraft.value
  const previous = revisions.value.find(item => draft && item.revision < draft.revision)
  if (previous) await restoreRevision(previous.revision)
  else ElMessage.info('暂无更早的已保存版本。')
}
function resetExportUrls() {
  for (const url of Object.values(exportUrls.value)) if (url.startsWith('blob:')) URL.revokeObjectURL(url)
  exportUrls.value = { docx: '', pdf: '' }
}
async function setExportResult(status: ResumeExportStatus, request: number) {
  if (request !== exportRequest || disposed.value) return
  exportStatus.value = status
  if (status.docx?.url || status.pdf?.url) {
    const [docxFile, pdfFile] = await Promise.all([status.docx?.url ? getResumeExportFile(status.id, 'docx') : undefined, status.pdf?.url ? getResumeExportFile(status.id, 'pdf') : undefined])
    const docx = docxFile ? URL.createObjectURL(docxFile) : ''
    const pdf = pdfFile ? URL.createObjectURL(pdfFile) : ''
    if (request !== exportRequest || disposed.value) {
      if (docx.startsWith('blob:')) URL.revokeObjectURL(docx)
      if (pdf.startsWith('blob:')) URL.revokeObjectURL(pdf)
      return
    }
    resetExportUrls()
    exportUrls.value = { docx, pdf }
    previewError.value = ''
  }
}
async function pollExport(initial: ResumeExportStatus, request: number) {
  let status = initial
  await setExportResult(status, request)
  for (let attempt = 0; attempt < 90 && ['QUEUED', 'RUNNING'].includes(status.status); attempt++) {
    await new Promise(resolve => setTimeout(resolve, 1000))
    if (request !== exportRequest || disposed.value) return
    status = await getResumeExport(status.id)
    await setExportResult(status, request)
  }
  if (request !== exportRequest || disposed.value) return
  if (status.status === 'SUCCEEDED') previewError.value = ''
  else if (status.status === 'NEEDS_EDIT') ElMessage.warning('内容超出排版范围，请精简、隐藏内容或换双页模板。')
  else if (status.status === 'FAILED') ElMessage.error('文件转换失败，草稿已保留，可点击重试。')
  else { exportExpired.value = true; ElMessage.info('转换仍在进行，可以继续查询状态。') }
}
async function exportDraft() {
  if (!hasDraftContent.value || exportLoading.value) return
  const saved = await saveDraft(false)
  if (!saved) return
  await requestDraftExport(saved)
}
async function autoPreview() {
  const draft = selectedDraft.value
  if (!draft || draftDirty.value || !hasDraftContent.value || exportLoading.value) return
  if (!resumeDraftCanAutoPreview(draft)) return
  await requestDraftExport(draft)
}
async function requestDraftExport(saved: ResumeDraft) {
  if (exportLoading.value || saved.id !== selectedDraftId.value) return
  const request = ++exportRequest
  exportLoading.value = true
  exportExpired.value = false
  previewError.value = ''
  exportDraftId.value = saved.id
  resetExportUrls()
  try { await pollExport(await createResumeExport(saved.id, saved.revision), request) }
  catch (error) { exportExpired.value = true; previewError.value = resumeWorkspaceMessage(error, '预览文件暂时无法读取，请重试。'); showError(error, previewError.value) }
  finally { if (request === exportRequest) exportLoading.value = false }
}
async function refreshExport() {
  if (!exportStatus.value?.id) return
  const request = ++exportRequest
  exportLoading.value = true
  exportExpired.value = false
  try { await pollExport(await getResumeExport(exportStatus.value.id), request) }
  catch (error) { showError(error, '导出状态查询失败，请稍后重试。') }
  finally { if (request === exportRequest) exportLoading.value = false }
}
async function downloadExport(format: 'docx' | 'pdf') {
  const file = exportStatus.value?.[format]
  if (!file?.url) return
  try {
    const url = exportUrls.value[format]
    if (!url) return
    const link = document.createElement('a')
    link.href = url
    link.download = file.fileName || `简历.${format}`
    link.rel = 'noreferrer'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } catch (error) { showError(error, '下载地址获取失败，请重试。') }
}
async function loadLearningCandidates() {
  learningLoading.value = true
  try { learningCandidates.value = resumeLearningCandidates(await listLearningPlans()); learningOpen.value = true }
  catch (error) { showError(error, '学习成果暂时无法读取，请稍后重试。') }
  finally { learningLoading.value = false }
}
function addLearningCandidate(candidate: ResumeLearningCandidate) {
  if (profileInput.value.experiences.some(item => item.id === candidate.experience.id)) { ElMessage.info('这份成果已在资料中。'); return }
  profileInput.value.experiences.push(JSON.parse(JSON.stringify(candidate.experience)) as ResumeWorkspaceExperience)
  profileConfirmed.value = false
  ElMessage.success('成果已作为候选经历加入。请补充职责、方法和实际结果后确认。')
}
function factName(factId: string) {
  const source = selectedDraft.value?.profileSnapshot
  if (!source) return factId
  if (factId === 'basics') return '基本信息'
  return source.education.find(item => item.id === factId)?.school
    || source.experiences.find(item => item.id === factId)?.title
    || source.skills.find(item => item.id === factId)?.name
    || source.credentials.find(item => item.id === factId)?.title || '已确认资料项'
}
function returnToMasterProfile() {
  profileSection.value = 'experience'
  void goToStep('profile')
  ElMessage.info('请在主资料中补充真实职责、方法或结果，确认后重新生成岗位草稿。')
}
function updateBullets(entry: ResumeDraftEntry, value: string) { entry.bullets = editableResumeLines(value) }
watch(profileInput, () => { profileConfirmed.value = false }, { deep: true })
watch(selectedDraftId, async id => {
  exportRequest++
  exportLoading.value = false
  exportStatus.value = undefined
  exportDraftId.value = ''
  previewError.value = ''
  resetExportUrls()
  revisions.value = []
  previewRevision.value = undefined
  draftConflict.value = undefined
  expandedSuggestions.value = false
  diagnosisOpen.value = false
  if (historyOpen.value && id) await readHistory()
  await nextTick()
  if (id && id === selectedDraftId.value) void autoPreview()
})
watch(() => props.resumeId, value => { if (value) importId.value = value })
watch(() => props.targetRole, value => { if (!targetJobId.value && value?.trim()) customTargetRole.value = value.trim() })
onMounted(load)
onBeforeUnmount(() => {
  disposed.value = true
  exportRequest++
  workspaceRequest++
  resetExportUrls()
  if (photoObjectUrl) URL.revokeObjectURL(photoObjectUrl)
})
</script>
<template>
  <el-config-provider :locale="zhCn">
  <section ref="workspaceElement" class="panel resume-builder" data-testid="resume-workspace" v-loading="loading">
    <div class="section-heading">
<div>
<h2>{{ step === 'resume' ? '我的简历' : step === 'template' ? '选择岗位与模板' : '填写简历资料' }}</h2>
</div>
<el-button v-if="!profileDirty && !draftDirty" circle :loading="loading" title="刷新" aria-label="刷新工作区" @click="load"><RefreshCw :size="16" /></el-button>
</div>
    <nav class="workflow-steps" aria-label="简历制作步骤">
      <button type="button" data-testid="resume-step-profile" :class="{ active: step === 'profile' }" :aria-current="step === 'profile' ? 'step' : undefined" @click="goToStep('profile')"><span>1</span>填写资料</button>
      <ArrowRight :size="15" class="step-arrow" />
      <button type="button" data-testid="resume-step-template" :class="{ active: step === 'template' }" :aria-current="step === 'template' ? 'step' : undefined" @click="goToStep('template')"><span>2</span>岗位与模板</button>
      <ArrowRight :size="15" class="step-arrow" />
      <button type="button" data-testid="resume-step-result" :class="{ active: step === 'resume' }" :disabled="!drafts.length" :aria-current="step === 'resume' ? 'step' : undefined" @click="goToStep('resume')"><span>3</span>编辑与下载</button>
    </nav>
    <div class="builder-grid">
      <div v-show="step === 'profile'" class="builder-source" data-testid="resume-profile-form">
        <div class="subheading">
<span class="hint">{{ profileSummary }}</span>
<el-tag v-if="profileDirty" size="small" type="warning">尚未保存</el-tag>
</div>
        <nav class="profile-tabs" aria-label="资料分区">
          <button type="button" data-testid="resume-profile-basics" :class="{ active: profileSection === 'basics' }" @click="profileSection = 'basics'">基本信息与教育</button>
          <button type="button" data-testid="resume-profile-experience" :class="{ active: profileSection === 'experience' }" @click="profileSection = 'experience'">技能与经历</button>
          <button type="button" data-testid="resume-profile-optional" :class="{ active: profileSection === 'optional' }" @click="profileSection = 'optional'">其他资料</button>
        </nav>
        <div v-show="profileSection === 'basics'">
        <details class="import-panel">
<summary>导入已有简历（PDF / DOC / DOCX）</summary>
<p class="hint">请核对提取内容。扫描件请手动填写。</p>
<div class="builder-actions">
<el-select v-model="importId" placeholder="选择已上传简历" aria-label="选择已上传简历">
<el-option v-for="resume in availableResumes" :key="resume.resumeId" :label="resume.fileName || resume.resumeId" :value="resume.resumeId" />
</el-select>
<el-button :loading="importing" :disabled="!importId" @click="importExisting">提取候选资料</el-button>
<el-button data-testid="resume-import-upload" :loading="importing" @click="resumeFileInput?.click()">上传简历</el-button>
</div>
<input ref="resumeFileInput" data-testid="resume-import-file" class="file-input" type="file" accept=".pdf,.doc,.docx" aria-label="上传文本简历" @change="uploadImport" />
          <div v-if="importCandidate" class="import-review">
<h4>核对导入原文</h4>
<p class="hint">无法识别的内容不会自动补全。导入将补充空白基本字段和新的经历，不会替换你已填写的内容。</p>
<pre>{{ importCandidate.rawText || '未提取到文字，请检查是否为扫描件并手动填写。' }}</pre>
<p class="hint">候选：{{ importCandidate.data.education?.length || 0 }} 段教育、{{ importCandidate.data.experiences?.length || 0 }} 段经历、{{ importCandidate.data.skills?.length || 0 }} 项技能。请在下方编辑区继续核对。</p>
<el-checkbox v-model="importConfirmed" data-testid="resume-import-confirm">我已查看原文，同意把候选内容加入编辑区</el-checkbox>
<div class="builder-actions">
<el-button type="primary" data-testid="resume-import-accept" :disabled="!importConfirmed" @click="acceptImport">加入候选资料</el-button>
<el-button @click="importCandidate = undefined">取消导入</el-button>
</div>
</div>
        </details>
        <div class="builder-fields">
<label>
<span>姓名</span>
<el-input v-model="profileInput.basics.name" data-testid="resume-name" placeholder="你的真实姓名" />
</label>
<label>
<span>联系电话</span>
<el-input v-model="profileInput.basics.phone" data-testid="resume-phone" placeholder="可联系的电话号码" />
</label>
<label>
<span>邮箱</span>
<el-input v-model="profileInput.basics.email" data-testid="resume-email" type="email" placeholder="常用邮箱" />
</label>
<label>
<span>当前城市</span>
<el-input v-model="profileInput.basics.city" placeholder="例如：杭州" />
</label>
<label class="full-width">
<span>个人作品 / 主页链接</span>
<el-input v-model="profileInput.basics.portfolioUrl" placeholder="GitHub、作品集或个人主页（可选）" />
</label>
</div>
        </div>
        <details v-show="profileSection === 'optional'" class="optional-panel" open>
<summary>照片（可选）</summary>
        <div class="photo-panel">
<img v-if="photoPreviewUrl" :src="photoPreviewUrl" alt="你上传的简历照片" />
<div>
<span class="field-label">照片（可选，JPG / PNG，最多 2 MB）</span>
<div class="builder-actions">
<el-button size="small" :loading="photoUploading" @click="photoFileInput?.click()">{{ profileInput.basics.photoObjectKey ? '更换照片' : '上传照片' }}</el-button>
<el-button v-if="profileInput.basics.photoObjectKey" size="small" @click="removePhoto">移除照片</el-button>
<el-tag v-if="profileInput.basics.photoObjectKey" size="small" type="success">已选用个人照片</el-tag>
</div>
</div>
</div>
<input ref="photoFileInput" class="file-input" type="file" accept="image/jpeg,image/png" aria-label="上传个人照片" @change="uploadPhoto" />
        </details>
        <div v-show="profileSection === 'basics'">
        <div class="subheading">
<h4>教育经历</h4>
<el-button size="small" data-testid="resume-add-education" @click="addEducation"><Plus :size="14" />添加教育</el-button>
</div>
        <article v-for="(education, index) in profileInput.education" :key="education.id" class="profile-item">
<div class="item-toolbar">
<strong>教育 {{ index + 1 }}</strong>
<el-tag size="small" type="info">{{ resumeSourceLabel(education.source) }}</el-tag>
<div class="item-actions">
<el-button size="small" :disabled="index === 0" @click="moveResumeItem(profileInput.education, index, -1)">上移</el-button>
<el-button size="small" :disabled="index === profileInput.education.length - 1" @click="moveResumeItem(profileInput.education, index, 1)">下移</el-button>
<el-button size="small" type="danger" plain @click="profileInput.education.splice(index, 1)">删除</el-button>
</div>
</div>
          <div class="builder-fields">
<label>
<span>学校</span>
<el-input v-model="education.school" :data-testid="`resume-school-${index}`" />
</label>
<label>
<span>专业</span>
<el-input v-model="education.major" />
</label>
<label>
<span>学历</span>
<el-select v-model="education.degree" allow-create filterable placeholder="选择或输入学历">
<el-option v-for="degree in ['专科', '本科', '硕士', '博士']" :key="degree" :label="degree" :value="degree" />
</el-select>
</label>
<label>
<span>毕业日期</span>
<el-date-picker v-model="education.graduationDate" type="date" value-format="YYYY-MM-DD" placeholder="已知时填写" />
</label>
<label>
<span>入学年月</span>
<el-date-picker v-model="education.startDate" type="month" value-format="YYYY-MM" />
</label>
<label>
<span>结束年月</span>
<el-date-picker v-model="education.endDate" type="month" value-format="YYYY-MM" />
</label>
<label class="full-width">
<span>相关课程（逗号分隔）</span>
<el-input :model-value="education.courses.join('、')" @update:model-value="(value: string | number) => education.courses = editableResumeValues(String(value))" />
</label>
<label class="full-width">
<span>补充说明</span>
<el-input v-model="education.notes" type="textarea" :rows="2" placeholder="真实排名、奖项或其他信息；没有可留空" />
</label>
</div>
</article>
        <p v-if="!profileInput.education.length" class="empty-hint">添加学校、专业和毕业时间。未知日期可以留空。</p>
        <div class="section-next"><el-button data-testid="resume-next-experiences" @click="profileSection = 'experience'">填写技能与经历<ArrowRight :size="15" /></el-button></div>
        </div>
        <div v-show="profileSection === 'experience'">
        <div class="subheading">
<h4>技能声明</h4>
<el-button size="small" data-testid="resume-add-skill" @click="addSkill"><Plus :size="14" />添加技能</el-button>
</div>
<p class="hint">填写自己实际掌握的技能，项目证据会在匹配时单独核对。</p>
<div v-for="(skill, index) in profileInput.skills" :key="skill.id" class="skill-row">
<el-input v-model="skill.name" :data-testid="`resume-skill-${index}`" placeholder="例如：Java、Vue、活动策划" />
<el-tag size="small" type="info">{{ resumeSourceLabel(skill.source) }}</el-tag>
<el-button size="small" :disabled="index === 0" @click="moveResumeItem(profileInput.skills, index, -1)">上移</el-button>
<el-button size="small" @click="profileInput.skills.splice(index, 1)">删除</el-button>
</div>
        <div class="subheading">
<h4>项目、实习与校园经历</h4>
<el-button size="small" data-testid="resume-add-experience" @click="addExperience()"><Plus :size="14" />添加经历</el-button>
</div>
<el-button text size="small" :loading="learningLoading" @click="loadLearningCandidates">添加学习成果</el-button>
        <article v-for="(experience, index) in profileInput.experiences" :key="experience.id" class="profile-item" @input="markExperienceEdited(experience)">
<div class="item-toolbar">
<strong>经历 {{ index + 1 }}</strong>
<el-tag size="small" type="info">{{ resumeSourceLabel(experience.source) }}</el-tag>
<div class="item-actions">
<el-button size="small" :disabled="index === 0" @click="moveResumeItem(profileInput.experiences, index, -1)">上移</el-button>
<el-button size="small" :disabled="index === profileInput.experiences.length - 1" @click="moveResumeItem(profileInput.experiences, index, 1)">下移</el-button>
<el-button size="small" type="danger" plain @click="profileInput.experiences.splice(index, 1)">删除</el-button>
</div>
</div>
          <div class="builder-fields">
<label>
<span>经历类型</span>
<el-select v-model="experience.type">
<el-option v-for="type in ['PROJECT', 'INTERNSHIP', 'CAMPUS', 'COMPETITION', 'OTHER']" :key="type" :label="experienceTypeLabel(type)" :value="type" />
</el-select>
</label>
<label>
<span>项目 / 活动名称</span>
<el-input v-model="experience.title" :data-testid="`resume-experience-title-${index}`" />
</label>
<label>
<span>单位 / 组织（可选）</span>
<el-input v-model="experience.organization" />
</label>
<label>
<span>个人职责</span>
<el-input v-model="experience.role" placeholder="明确你负责的部分" />
</label>
<label>
<span>开始年月</span>
<el-date-picker v-model="experience.startDate" type="month" value-format="YYYY-MM" />
</label>
<label>
<span>结束年月</span>
<el-date-picker v-model="experience.endDate" type="month" value-format="YYYY-MM" />
</label>
<label class="full-width">
<span>你实际做了什么</span>
<el-input v-model="experience.actions" :data-testid="`resume-experience-actions-${index}`" type="textarea" :rows="3" placeholder="具体行动和个人贡献" />
</label>
<label class="full-width">
<span>采用的方法 / 技术</span>
<el-input v-model="experience.methods" type="textarea" :rows="2" placeholder="实际使用的方法或技术" />
</label>
<label class="full-width">
<span>结果与验证</span>
<el-input v-model="experience.results" type="textarea" :rows="2" placeholder="测试结果、交付成果或复盘；没有数据可写定性结果" />
</label>
<label class="full-width">
<span>材料涉及的技能（逗号分隔）</span>
<el-input :model-value="experience.skills.join('、')" @update:model-value="(value: string | number) => experience.skills = editableResumeValues(String(value))" />
</label>
<label class="full-width">
<span>作品 / 成果链接（每行一个）</span>
<el-input :model-value="experience.links.join('\n')" type="textarea" :rows="2" @update:model-value="(value: string | number) => experience.links = editableResumeLines(String(value))" />
</label>
</div>
<details v-if="experience.source?.quote || experience.source?.assessment" class="source-details">
<summary>查看来源原文与评价</summary>
<p>{{ experience.source?.quote }}</p>
<p class="hint">{{ experience.source?.assessment }}</p>
</details>
</article>
        <p v-if="!profileInput.experiences.length" class="empty-hint">没有实习也可添加课程项目、竞赛或社团活动。资料不足时会提出补充问题。</p>
        </div>
        <div v-show="profileSection === 'optional'">
        <details class="optional-panel" :open="profileInput.credentials.length > 0">
<summary>竞赛与证书</summary>
        <div class="subheading">
<h4>竞赛与证书</h4>
<el-button size="small" @click="addCredential">添加证书 / 奖项</el-button>
</div>
<article v-for="(credential, index) in profileInput.credentials" :key="credential.id" class="profile-item">
<div class="item-toolbar">
<strong>证书 / 奖项 {{ index + 1 }}</strong>
<el-tag size="small" type="info">{{ resumeSourceLabel(credential.source) }}</el-tag>
<el-button size="small" @click="profileInput.credentials.splice(index, 1)">删除</el-button>
</div>
<div class="builder-fields">
<label>
<span>名称</span>
<el-input v-model="credential.title" />
</label>
<label>
<span>获得日期</span>
<el-date-picker v-model="credential.date" type="date" value-format="YYYY-MM-DD" />
</label>
<label class="full-width">
<span>真实说明</span>
<el-input v-model="credential.description" type="textarea" :rows="2" />
</label>
</div>
</article>
        </details>
        <details class="optional-panel">
<summary>实习安排</summary>
        <h4>实习安排</h4>
<p class="hint">不知道的条件保持空白，岗位比较时会显示“信息不足”。</p>
<div class="builder-fields">
<label class="full-width">
<span>可实习城市（逗号分隔）</span>
<el-input :model-value="profileInput.availability.cities.join('、')" @update:model-value="(value: string | number) => profileInput.availability.cities = editableResumeValues(String(value))" />
</label>
<label>
<span>最早到岗日期</span>
<el-date-picker v-model="profileInput.availability.earliestStartDate" type="date" value-format="YYYY-MM-DD" />
</label>
<label>
<span>毕业日期</span>
<el-date-picker v-model="profileInput.availability.graduationDate" type="date" value-format="YYYY-MM-DD" />
</label>
<label>
<span>每周出勤天数</span>
<el-input-number v-model="profileInput.availability.daysPerWeek" :min="1" :max="7" :precision="0" controls-position="right" placeholder="未知" />
</label>
<label>
<span>连续实习月数</span>
<el-input-number v-model="profileInput.availability.continuousMonths" :min="1" :max="36" :precision="0" controls-position="right" placeholder="未知" />
</label>
</div>
        </details>
        </div>
        <el-alert v-if="profileConflict" type="warning" :closable="false" title="资料已在其他页面更新，本地输入已保留。">
<p>服务器版本 {{ profileConflict.revision }} · {{ formatTime(profileConflict.updatedAt) }}</p>
<details>
<summary>查看服务器资料</summary>
<pre>{{ JSON.stringify(profileConflict.data, null, 2) }}</pre>
</details>
<div class="builder-actions">
<el-button size="small" @click="useServerProfile">使用服务器资料</el-button>
<el-button size="small" @click="keepLocalProfile">保留本地输入并重新确认</el-button>
</div>
</el-alert>
        <div v-if="profileInputBackup" class="backup-notice">
<span>已保留切换前的本地输入。</span>
<el-button size="small" @click="restoreLocalProfileInput">恢复本地输入</el-button>
</div>
<div class="confirm-profile">
<el-checkbox v-if="profileDirty || !profile?.revision" v-model="profileConfirmed" data-testid="resume-profile-confirm">我确认填写的资料真实</el-checkbox>
<div class="builder-actions">
<el-button :loading="profileSaving" :disabled="!profileConfirmed || !profileDirty" data-testid="resume-save-profile" @click="saveProfile()"><Save :size="15" />保存资料</el-button>
<el-button type="success" :loading="profileSaving" :disabled="(profileDirty || !profile?.revision) && !profileConfirmed" data-testid="resume-profile-continue" @click="continueFromProfile">下一步：岗位与模板<ArrowRight :size="15" /></el-button>
</div>
</div>
      </div>
      <div v-show="step === 'template'" class="builder-generate" data-testid="resume-template-form">
<div class="profile-summary"><span>{{ profileSummary }}</span><el-button text @click="goToStep('profile')">修改资料</el-button></div>
<h3>目标岗位</h3>
<label class="field-label">实际岗位</label>
<el-select v-model="targetJobId" data-testid="resume-job-select" clearable filterable placeholder="选择实际岗位（可选）" aria-label="选择目标岗位">
<el-option v-for="job in jobs" :key="job.jobId" :label="`${job.title} · ${job.companyName} · ${job.city}`" :value="job.jobId" />
</el-select>
<div v-if="chosenJob" class="selected-job">
<strong>{{ chosenJob.title }}</strong>
<p>{{ chosenJob.companyName }} · {{ chosenJob.city }}</p>
<p class="hint">要求技能：{{ chosenJob.requiredSkills.join('、') || '岗位未列明' }}</p>
<details>
<summary>查看岗位原文</summary>
<p>{{ chosenJob.description }}</p>
</details>
</div>
<label v-else class="generic-role">
<span class="field-label">或填写求职方向</span>
<el-input v-model="customTargetRole" data-testid="resume-custom-role" placeholder="例如：Java 开发、前端开发、运营" />
</label>
<div class="subheading"><h3>简历模板</h3><span class="hint">{{ templates.length }} 套 · 可随时更换</span></div>
        <div class="template-list">
<button v-for="item in templates" :key="item.id" type="button" :data-testid="`resume-template-${item.id}`" :class="['template-card', { selected: generationTemplateId === item.id }]" :aria-pressed="generationTemplateId === item.id" @click="generationTemplateId = item.id">
<img v-if="item.previewUrl" :src="item.previewUrl" :alt="`${item.name}模板预览`" loading="lazy" />
<strong>{{ item.name }}</strong>
<small>{{ item.category }} · 最多 {{ item.maxPages }} 页</small>
</button>
</div>
<div class="stage-footer"><el-button @click="goToStep('profile')"><ArrowLeft :size="15" />返回资料</el-button><el-button type="success" data-testid="resume-generate" :loading="generating || profileSaving" :disabled="!generationTemplateId" @click="generate">生成我的简历<ArrowRight :size="15" /></el-button></div>
      </div>
    </div>
    <div v-show="step === 'resume'" data-testid="resume-result">
    <div v-if="drafts.length" class="draft-toolbar">
<label>
<span class="field-label">简历版本</span>
<el-select v-model="selectedDraftId" data-testid="resume-draft-select" aria-label="选择岗位草稿">
<el-option v-for="draft in drafts" :key="draft.id" :label="`${draft.targetRole || '通用岗位'} · ${templateName(draft.templateId)} · V${draft.revision}`" :value="draft.id" />
</el-select>
</label>
<el-tag v-if="draftDirty" type="warning">有未保存编辑</el-tag>
<el-button data-testid="resume-new-version" @click="goToStep('template')"><Plus :size="15" />新建岗位版本</el-button>
</div>
    <div v-if="selectedDraft" class="resume-layout">
    <aside class="resume-preview-pane" data-testid="resume-preview-pane">
      <div class="preview-heading"><h3>成品预览</h3><span class="hint">{{ templateName(selectedDraft.templateId) }}</span></div>
      <div class="preview-actions">
        <el-button type="success" data-testid="resume-update-preview" :loading="exportLoading" :disabled="draftSaving || diagnosing || !hasDraftContent" @click="exportDraft"><RefreshCw :size="15" />{{ draftDirty || selectedDraft.revision !== exportStatus?.draftRevision ? '保存并更新预览' : '刷新预览' }}</el-button>
        <el-button v-if="exportUrls.docx" data-testid="resume-download-word" :disabled="draftSaving || exportLoading || draftDirty || selectedDraft.revision !== exportStatus?.draftRevision" @click="downloadExport('docx')"><Download :size="15" />Word</el-button>
        <el-button v-if="exportUrls.pdf" data-testid="resume-download-pdf" :disabled="draftSaving || exportLoading || draftDirty || selectedDraft.revision !== exportStatus?.draftRevision" @click="downloadExport('pdf')"><Download :size="15" />PDF</el-button>
      </div>
      <p v-if="draftDirty || (exportStatus && selectedDraft.revision !== exportStatus.draftRevision)" class="preview-notice">有新的修改，请更新预览后下载。</p>
      <div v-if="exportStatus && exportDraftId === selectedDraftId" class="export-result">
        <div class="export-toolbar"><el-tag size="small" :type="exportStatus.status === 'SUCCEEDED' ? 'success' : exportStatus.status === 'FAILED' ? 'danger' : 'warning'">{{ resumeExportStatusLabel(exportStatus.status) }}</el-tag><span v-if="exportStatus.pageCount">{{ exportStatus.pageCount }} 页 · V{{ exportStatus.draftRevision }}</span></div>
        <el-alert v-if="exportStatus.status === 'NEEDS_EDIT'" type="warning" :closable="false" title="内容超出页数，请精简、隐藏条目或换双页模板。" />
        <p v-for="(issue, index) in exportStatus.layoutIssues" :key="index" class="layout-issue">{{ humanIssue(issue) }}</p>
        <p v-if="exportStatus.error" class="layout-issue">{{ /[\u4e00-\u9fff]/.test(exportStatus.error) ? exportStatus.error : '文件生成失败，请重试。' }}</p>
        <el-button v-if="exportExpired || ['QUEUED', 'RUNNING'].includes(exportStatus.status)" size="small" :loading="exportLoading" @click="refreshExport">继续查询</el-button>
      </div>
      <el-alert v-if="previewError" :title="previewError" type="error" :closable="false"><el-button size="small" :loading="exportLoading" @click="exportDraft">重新加载</el-button></el-alert>
      <ResumePdfPreview v-if="exportUrls.pdf" :src="exportUrls.pdf" :data-template-id="exportStatus?.templateId" :data-draft-revision="exportStatus?.draftRevision" @error="previewError = 'PDF 预览失败，请重新加载。'" />
      <div v-else class="draft-preview" :aria-busy="exportLoading">
        <p v-if="exportLoading" class="preview-progress"><RefreshCw :size="14" />正在生成 Word 和 PDF…</p>
        <article class="resume-paper" data-testid="resume-text-preview">
          <h2>{{ selectedDraft.profileSnapshot.basics.name }}</h2>
          <p class="paper-contact">{{ [selectedDraft.profileSnapshot.basics.phone, selectedDraft.profileSnapshot.basics.email, selectedDraft.profileSnapshot.basics.city].filter(Boolean).join(' · ') }}</p>
          <section v-for="block in selectedDraft.data.blocks.filter(item => item.visible && item.entries.some(entry => entry.visible))" :key="block.id" class="paper-section">
            <h3>{{ block.title }}</h3>
            <article v-for="entry in block.entries.filter(item => item.visible)" :key="entry.id">
              <div class="paper-entry-heading"><strong>{{ entry.title }}</strong><span>{{ entry.subtitle }}</span></div>
              <ul v-if="entry.bullets.some(line => line.trim())"><li v-for="(bullet, index) in entry.bullets.filter(line => line.trim())" :key="index">{{ bullet }}</li></ul>
              <p v-for="link in entry.links.filter(value => value.trim())" :key="link" class="paper-link">{{ link }}</p>
            </article>
          </section>
          <p v-if="!hasDraftContent" class="hint">请先补充资料，生成简历内容。</p>
        </article>
      </div>
    </aside>
    <div class="resume-edit-pane">
      <div class="editor-heading"><h3>编辑内容</h3><el-button data-testid="resume-save-draft" :loading="draftSaving" @click="saveDraft()"><Save :size="15" />保存并确认</el-button></div>
      <div class="editor-tools">
        <el-button data-testid="resume-diagnose" :loading="diagnosing" :disabled="draftSaving" @click="diagnose"><SearchCheck :size="15" />检查与优化</el-button>
        <el-tooltip content="历史版本" placement="top"><el-button circle data-testid="resume-history" :loading="historyLoading" aria-label="历史版本" @click="historyOpen = !historyOpen; historyOpen && readHistory()"><History :size="16" /></el-button></el-tooltip>
        <el-tooltip content="撤销最近保存" placement="top"><el-button circle data-testid="resume-undo" :disabled="draftSaving" aria-label="撤销最近保存" @click="undoLatest"><Undo2 :size="16" /></el-button></el-tooltip>
      </div>
    <div v-if="selectedDraft" class="draft-editor">
<div v-if="draftInputBackups[selectedDraftId]" class="backup-notice">
<span>已保留切换前的本地输入。</span>
<el-button size="small" @click="restoreLocalDraftInput">恢复本地输入</el-button>
</div>
      <el-alert v-if="selectedDraft.sourceStale" type="warning" title="资料已更新。如需采用新资料，请新建岗位版本。" :closable="false" />
      <div class="draft-context">
<label>
<span class="field-label">模板</span>
<el-select v-model="selectedDraft.templateId" data-testid="resume-draft-template" aria-label="切换当前草稿模板">
<el-option v-for="item in templates" :key="item.id" :label="`${item.name}（${item.maxPages} 页）`" :value="item.id" />
</el-select>
</label>
<details class="source-details"><summary>版本与来源</summary><p>{{ selectedDraft.targetRole || '通用岗位' }} · 资料 V{{ selectedDraft.profileRevision }} · 简历 V{{ selectedDraft.revision }}</p><p>{{ generatedSource }}</p></details>
</div>
      <details v-if="selectedDraft.data.warnings.length" class="source-details"><summary>资料提示（{{ selectedDraft.data.warnings.length }}）</summary><p v-for="(warning, index) in selectedDraft.data.warnings" :key="index">{{ /[\u4e00-\u9fff]/.test(warning) ? warning : '请核对事实来源，缺失内容可在资料中补充。' }}</p></details>
      <div v-if="historyOpen" class="history-panel" v-loading="historyLoading">
<div class="subheading">
<h3>已保存历史</h3>
<el-button size="small" @click="readHistory">刷新历史</el-button>
</div>
<p class="hint">恢复会创建新修订，所有历史仍保留。当前未保存输入可先确认保存。</p>
<div v-for="revision in revisions" :key="revision.revision" class="history-row">
<span>V{{ revision.revision }} · {{ templateName(revision.templateId) }} · {{ historyReason(revision.reason) }} · {{ formatTime(revision.createdAt) }}</span>
<div>
<el-button size="small" @click="previewRevision = revision">查看内容</el-button>
<el-button size="small" :disabled="revision.revision === selectedDraft.revision || draftSaving" @click="restoreRevision(revision.revision)">恢复此版本</el-button>
</div>
</div>
<p v-if="!revisions.length" class="empty-hint">暂无历史记录。</p>
<details v-if="previewRevision" open class="history-preview">
<summary>V{{ previewRevision.revision }} 内容快照</summary>
<article v-for="block in previewRevision.data.blocks" :key="block.id">
<strong>{{ block.title }}{{ block.visible ? '' : '（隐藏）' }}</strong>
<div v-for="entry in block.entries" :key="entry.id">
<p>{{ entry.title }} · {{ entry.subtitle }}{{ entry.visible ? '' : '（隐藏）' }}</p>
<ul>
<li v-for="(bullet, index) in entry.bullets" :key="index">{{ bullet }}</li>
</ul>
</div>
</article>
</details>
</div>
      <details v-for="(block, blockIndex) in selectedDraft.data.blocks" :key="block.id" :open="blockIndex === 0" :data-testid="`resume-edit-block-${blockIndex}`" :class="['draft-block', { hidden: !block.visible }]">
<summary>{{ block.title }}<span>{{ block.visible ? `${block.entries.filter(entry => entry.visible).length} 项` : '已隐藏' }}</span></summary>
<div class="draft-block-title">
<el-input v-model="block.title" aria-label="区块标题" />
<div class="item-actions">
<el-switch v-model="block.visible" active-text="显示" inactive-text="隐藏" />
<el-tooltip content="上移区块"><el-button size="small" circle aria-label="上移区块" :disabled="blockIndex === 0" @click="moveResumeItem(selectedDraft.data.blocks, blockIndex, -1)"><ChevronUp :size="14" /></el-button></el-tooltip>
<el-tooltip content="下移区块"><el-button size="small" circle aria-label="下移区块" :disabled="blockIndex === selectedDraft.data.blocks.length - 1" @click="moveResumeItem(selectedDraft.data.blocks, blockIndex, 1)"><ChevronDown :size="14" /></el-button></el-tooltip>
</div>
</div>
<article v-for="(entry, entryIndex) in block.entries" :key="entry.id" :class="['draft-entry', { hidden: !entry.visible }]">
<div class="item-toolbar">
<strong>内容 {{ entryIndex + 1 }}</strong>
<div class="item-actions">
<el-switch v-model="entry.visible" active-text="显示" inactive-text="隐藏" />
<el-tooltip content="上移内容"><el-button size="small" circle aria-label="上移内容" :disabled="entryIndex === 0" @click="moveResumeItem(block.entries, entryIndex, -1)"><ChevronUp :size="14" /></el-button></el-tooltip>
<el-tooltip content="下移内容"><el-button size="small" circle aria-label="下移内容" :disabled="entryIndex === block.entries.length - 1" @click="moveResumeItem(block.entries, entryIndex, 1)"><ChevronDown :size="14" /></el-button></el-tooltip>
</div>
</div>
<div class="builder-fields">
<label>
<span>标题</span>
<el-input v-model="entry.title" :data-testid="`resume-entry-title-${blockIndex}-${entryIndex}`" />
</label>
<label>
<span>副标题 / 时间</span>
<el-input v-model="entry.subtitle" />
</label>
<label class="full-width">
<span>正文（每行一项）</span>
<el-input :model-value="entry.bullets.join('\n')" :data-testid="`resume-entry-bullets-${blockIndex}-${entryIndex}`" type="textarea" :rows="Math.min(10, Math.max(2, entry.bullets.length + 1))" @update:model-value="(value: string | number) => updateBullets(entry, String(value))" />
</label>
<details class="full-width source-details">
<summary>链接与事实来源</summary>
<label>
<span>链接（每行一个）</span>
<el-input :model-value="entry.links.join('\n')" type="textarea" :rows="2" @update:model-value="(value: string | number) => entry.links = editableResumeLines(String(value))" />
</label>
<p class="fact-trace">来源：{{ entry.factIds?.map(factName).join('、') || '请核对并补充来源' }} · {{ entry.confirmed ? '已确认' : '请核对' }}</p>
</details>
</div>
</article>
</details>
      <p v-if="!selectedDraft.data.blocks.length" class="empty-hint">草稿还没有内容。请补充主资料后生成。</p>
<details v-if="selectedDraft.data.questions?.length" class="clarification-list">
<summary>待补充资料（{{ selectedDraft.data.questions.length }}）</summary>
<article v-for="(item, index) in selectedDraft.data.questions" :key="`${item.factId}-${index}`">
<strong>{{ factName(item.factId) }}</strong>
<p>{{ item.question }}</p>
<small>{{ item.reason }}</small>
</article>
<el-button size="small" @click="returnToMasterProfile">去补充资料</el-button>
</details>
      <details v-if="selectedDraft.data.suggestions?.length" :open="diagnosisOpen" class="suggestion-list" data-testid="resume-diagnosis-results" @toggle="diagnosisOpen = ($event.target as HTMLDetailsElement).open">
<summary>修改建议（{{ availableSuggestions.length }}）</summary>
<div class="subheading">
<h3>{{ expandedSuggestions ? '全部诊断建议' : '最值得修改的三项' }}</h3>
<el-button size="small" @click="expandedSuggestions = !expandedSuggestions">{{ expandedSuggestions ? '收起' : '查看全部建议' }}</el-button>
</div>
<article v-for="item in displaySuggestions" :key="item.id">
<p>
<b>原文：</b>{{ item.originalQuote }}</p>
<p>
<b>问题：</b>{{ item.problem }}</p>
<p>
<b>建议：</b>{{ item.suggestedText }}</p>
<p>
<b>依据：</b>{{ item.basis }}</p>
<small>对应资料：{{ item.factIds?.map(factName).join('、') || '原文引用' }}</small>
<div class="builder-actions">
<el-tag v-if="item.status === 'APPLIED'" type="success">已采纳，历史可撤销</el-tag>
<el-button v-else-if="suggestionNeedsProfile(item)" size="small" type="warning" plain @click="returnToMasterProfile">补充主资料</el-button>
<el-button v-else size="small" :data-testid="`resume-apply-suggestion-${item.id}`" :disabled="draftSaving || !suggestionMatchesDraft(selectedDraft.data, item)" @click="applySuggestion(item)">{{ suggestionMatchesDraft(selectedDraft.data, item) ? '采纳建议' : '原文已变化，请重新检查' }}</el-button>
</div>
</article>
</details>
    </div>
    </div>
    </div>
    <div v-else class="empty-hint">暂时没有简历，请先填写资料并生成。</div>
</div>
    <el-dialog v-model="conflictOpen" title="草稿版本已变化" width="min(800px, 95vw)">
<p>当前输入已保留。请比较服务器版本，再选择使用服务器内容，或保留当前输入并重新保存。</p>
<div v-if="draftConflict" class="conflict-content">
<p>服务器 V{{ draftConflict.revision }} · {{ formatTime(draftConflict.updatedAt) }}</p>
<article v-for="block in draftConflict.data.blocks" :key="block.id">
<strong>{{ block.title }}</strong>
<p v-for="entry in block.entries" :key="entry.id">{{ entry.title }} · {{ entry.bullets.join('；') }}</p>
</article>
</div>
<template #footer>
<el-button @click="conflictOpen = false">稍后处理，保留输入</el-button>
<el-button @click="useServerDraft">使用服务器内容</el-button>
<el-button type="primary" @click="keepLocalDraft">保留当前输入并重新确认</el-button>
</template>
</el-dialog>
    <el-dialog v-model="learningOpen" title="选择学习成果作为候选经历" width="min(760px, 95vw)">
<p class="hint">只展示已提交的文本或链接成果。选择后仍需补充与确认，不会因为完成任务自动增加技能。</p>
<article v-for="candidate in learningCandidates" :key="candidate.id" class="profile-item">
<strong>{{ candidate.title }}</strong>
<p>{{ candidate.description }}</p>
<p class="hint">{{ candidate.evaluated ? `评价：${candidate.evaluation}` : '尚无成果评价' }}</p>
<p v-if="candidate.links.length" class="hint">成果链接：{{ candidate.links.join('；') }}</p>
<el-button size="small" :disabled="profileInput.experiences.some(item => item.id === candidate.experience.id)" @click="addLearningCandidate(candidate)">{{ profileInput.experiences.some(item => item.id === candidate.experience.id) ? '已加入编辑区' : '选择加入候选经历' }}</el-button>
</article>
<p v-if="!learningCandidates.length" class="empty-hint">暂无已提交学习成果。可先在学习路径中提交文本说明或作品链接。</p>
</el-dialog>
  </section>
  </el-config-provider>
</template>
<style scoped>
.workflow-steps{display:flex;align-items:center;gap:12px;border-bottom:1px solid #e5ebe7;padding:6px 0 18px;margin-bottom:20px}
.workflow-steps button{display:flex;align-items:center;justify-content:center;gap:8px;padding:8px 12px;color:#68756e;border:0;background:transparent;font:inherit;font-size:13px;cursor:pointer;min-height:40px;border-radius:6px}
.workflow-steps button.active{background:#e9f3ec;color:#28664f;font-weight:600}
.workflow-steps button:disabled{cursor:default;opacity:.5}
.workflow-steps button span{display:grid;place-items:center;width:22px;height:22px;border-radius:50%;border:1px solid #cbd8cf;font-size:12px;flex:none}
.workflow-steps button.active span{background:#28664f;color:#fff;border-color:#28664f}
.step-arrow{color:#a4b2a9;flex:none}
.profile-tabs{display:flex;gap:20px;border-bottom:1px solid #e5ebe7;margin:4px 0 20px}
.profile-tabs button{padding:12px 0;font:inherit;font-size:13px;color:#68756e;border:0;border-bottom:2px solid transparent;background:transparent;cursor:pointer}
.profile-tabs button.active{color:#28664f;border-color:#28664f;font-weight:600}
.profile-summary,.stage-footer,.section-next,.editor-heading,.preview-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.profile-summary{padding:8px 0 16px;border-bottom:1px solid #e5ebe7;margin-bottom:20px;color:#58645e;font-size:13px}
.stage-footer{border-top:1px solid #e5ebe7;padding-top:18px;margin-top:20px}
.section-next{justify-content:flex-end;padding-top:16px}
.resume-layout{display:grid;grid-template-columns:minmax(0,1.05fr) minmax(360px,.95fr);gap:24px;align-items:start}
.resume-preview-pane{min-width:0;position:sticky;top:12px;overflow:hidden}
.resume-edit-pane{min-width:0;border-left:1px solid #e5ebe7;padding-left:22px}
.editor-heading h3,.preview-heading h3{font-size:16px;margin:0}
.editor-tools,.preview-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:12px 0}
.resume-builder :deep(.el-button){gap:5px}
.resume-builder :deep(.el-button+.el-button){margin-left:0}
.preview-notice{font-size:12px;color:#97602d;line-height:1.6;margin:8px 0}
.draft-preview{padding:16px;background:#eef1ef;min-height:540px;margin-top:14px}
.preview-progress{display:flex;align-items:center;gap:7px;font-size:12px;color:#58645e;margin:0 0 12px}
.resume-paper{padding:30px 26px;min-height:500px;background:#fff;color:#272e2a;box-shadow:0 2px 8px #17291d10;font-size:12px;line-height:1.6;overflow-wrap:anywhere}
.resume-paper h2{font-size:22px;margin:0 0 6px;color:#28664f}
.paper-contact{font-size:11px;margin:0;color:#58645e}
.paper-section{margin-top:20px}
.paper-section>h3{font-size:14px;color:#28664f;border-bottom:1px solid #28664f;padding-bottom:4px;margin:0 0 9px}
.paper-entry-heading{display:flex;align-items:baseline;justify-content:space-between;gap:10px;flex-wrap:wrap}
.paper-entry-heading span{font-size:11px;color:#58645e}
.paper-section ul{padding-left:18px;margin:6px 0 12px}
.paper-link{font-size:11px;margin:3px 0}
.optional-panel{border-top:1px solid #e5ebe7;margin-top:12px;padding:14px 0}
.optional-panel>summary,.clarification-list>summary,.suggestion-list>summary,.draft-block>summary{cursor:pointer;font-size:13px;font-weight:600;color:#45574d}
.draft-block>summary{display:flex;justify-content:space-between;gap:10px;align-items:center;min-height:24px}
.draft-block>summary:before{content:'+';font-weight:400;width:14px;color:#68756e}
.draft-block[open]>summary:before{content:'-'}
.draft-block>summary span{margin-left:auto;font-size:12px;color:#68756e;font-weight:400}
.draft-block-title{margin-top:14px}
.backup-notice{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-top:10px;padding:10px;background:#fff5df;border-radius:8px;font-size:12px}
.resume-builder{padding:20px}
.resume-builder h3{margin:0 0 12px}
.resume-builder h4{margin:18px 0 8px;color:#58645e}
.builder-grid{display:block}
.builder-source,.builder-generate{padding:0;border:0;background:transparent;min-width:0;max-width:900px;margin:0 auto}
.builder-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:10px}
.builder-fields label{display:grid;gap:5px;min-width:0}
.builder-fields label>span,.field-label{font-size:13px;color:#45574d;font-weight:600;display:block;margin-bottom:3px}
.builder-fields :deep(.el-input),.builder-fields :deep(.el-select),.builder-fields :deep(.el-input-number),.builder-fields :deep(.el-date-editor){width:100%;min-width:0}
.full-width{grid-column:1/-1}
.builder-actions,.draft-toolbar,.item-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.builder-actions{margin-top:10px}
.builder-actions :deep(.el-button)+:deep(.el-button),.item-actions :deep(.el-button)+:deep(.el-button){margin-left:0}
.subheading,.item-toolbar{display:flex;align-items:center;gap:8px;justify-content:space-between;flex-wrap:wrap}
.subheading h3,.subheading h4{margin:14px 0 9px}
.item-toolbar strong{font-size:13px}
.profile-item{padding:14px 0;margin-top:10px;border:0;border-bottom:1px solid #e3ebe5;background:transparent}
.profile-item p,.selected-job p{white-space:pre-wrap;overflow-wrap:anywhere}
.hint,.empty-hint,.fact-trace,.generic-role small,.draft-context small{font-size:12px;line-height:1.65;color:#68756e;margin:8px 0}
.empty-hint{padding:12px;background:#f3f7f4;border-radius:8px}
.file-input{display:none}
.import-panel{border:1px solid #dfe8e1;border-radius:8px;padding:10px;margin-bottom:12px;background:#fff}
.import-panel summary,.source-details summary,.history-preview summary{cursor:pointer;color:#28664f;font-size:13px}
.import-panel pre,.history-panel pre{white-space:pre-wrap;word-break:break-word;max-height:220px;overflow:auto;background:#f5f8f6;padding:10px;font-size:12px}
.import-review{padding-top:8px}
.photo-panel{display:flex;align-items:flex-start;gap:12px;margin-top:15px}
.photo-panel img{width:66px;height:84px;object-fit:cover;border:1px solid #dfe8e1;border-radius:4px}
.skill-row{display:flex;gap:6px;align-items:center;margin:8px 0;flex-wrap:wrap}
.skill-row :deep(.el-input){flex:1;min-width:130px}
.source-details{margin-top:10px;background:#f7faf8;padding:8px;border-radius:6px}
.source-details p{font-size:12px;white-space:pre-wrap;overflow-wrap:anywhere}
.confirm-profile{padding-top:16px;margin-top:14px;border-top:1px solid #e5ebe7}
.confirm-profile :deep(.el-checkbox){white-space:normal;height:auto;align-items:flex-start}
.confirm-profile :deep(.el-checkbox__label){white-space:normal;line-height:1.6}
.builder-generate>:deep(.el-select){width:100%}
.generic-role{display:grid;gap:4px;margin:12px 0}
.selected-job{background:#edf5ef;padding:12px;border-radius:8px;margin-top:12px;font-size:13px}
.selected-job details{font-size:12px;line-height:1.7}
.template-list{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:18px 0 14px}
.template-card{display:grid;gap:5px;text-align:left;padding:9px;border:1px solid #e2e9e4;border-radius:6px;background:#fff;cursor:pointer;min-width:0}
.template-card.selected{border-color:#28664f;box-shadow:0 0 0 2px #d7eee1}
.template-card:focus-visible{outline:2px solid #28664f;outline-offset:2px}
.template-card img{width:100%;height:150px;object-fit:contain;border-radius:4px;background:#f4f6f4}
.template-card strong{font-size:13px}
.template-card small{font-size:11px;line-height:1.5;color:#68756e}
.workflow-help{padding:8px 12px;margin-top:16px;background:#f1f6f2;border-radius:8px}
.workflow-help ol{font-size:12px;line-height:1.9;padding-left:19px;color:#58645e}
.draft-toolbar{padding:0 0 20px;gap:8px;justify-content:space-between}
.draft-toolbar>label{min-width:260px}
.draft-toolbar :deep(.el-button)+:deep(.el-button){margin-left:0}
.draft-editor{display:grid;gap:12px}
.draft-context{padding:12px 0;background:transparent;border-bottom:1px solid #e5ebe7}
.draft-context p{font-size:13px;color:#4b5e53;margin:0 0 8px}
.draft-context label{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.draft-context :deep(.el-select){width:220px;max-width:100%}
.draft-block{padding:14px 0;border:0;border-bottom:1px solid #e5ebe7;background:transparent;min-width:0}
.draft-block-title{display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap}
.draft-block-title>:deep(.el-input){flex:1;min-width:180px;max-width:300px}
.draft-entry{padding:13px 0;border-top:1px solid #eef2ef;margin-top:12px}
.draft-entry.hidden,.draft-block.hidden{background:#f5f7f5;opacity:.78}
.fact-trace{margin:8px 0 0}
.clarification-list,.suggestion-list,.history-panel{padding:14px 0;border-top:1px solid #e5ebe7;background:transparent}
.clarification-list article,.suggestion-list article{padding:10px;background:#fff;border:1px solid #e3ebe5;border-radius:8px;margin-top:8px}
.clarification-list p,.suggestion-list p{margin:4px 0;white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;line-height:1.75}
.clarification-list small,.suggestion-list small{font-size:12px;color:#68756e}
.history-row{display:flex;align-items:center;justify-content:space-between;gap:10px;font-size:12px;padding:10px 0;border-top:1px solid #e3ebe5;flex-wrap:wrap}
.history-preview{margin-top:10px;background:#fff;padding:10px;border-radius:6px;font-size:12px}
.history-preview article{padding:10px;border-bottom:1px solid #e3ebe5}
.export-result{margin:12px 0;padding:0;background:transparent}
.export-toolbar{display:flex;gap:8px;align-items:center;flex-wrap:wrap;font-size:13px}
.export-toolbar :deep(.el-button)+:deep(.el-button){margin-left:0}
.pdf-preview{width:100%;height:760px;border:1px solid #d9e5dc;border-radius:7px;margin-top:12px;background:#fff}
.layout-issue{font-size:13px;color:#97602d}
.conflict-content{max-height:380px;overflow:auto;background:#f5f8f6;padding:12px;border-radius:8px;font-size:13px}
.conflict-content article{padding:8px 0;border-top:1px solid #e3ebe5}
.conflict-content p{white-space:pre-wrap;overflow-wrap:anywhere}
@media(max-width:1100px){.resume-layout{grid-template-columns:minmax(0,1fr) minmax(330px,.95fr);gap:18px}
.resume-edit-pane{padding-left:16px}
.template-card img{height:120px}}
@media(max-width:860px){.resume-layout{display:flex;flex-direction:column}
.resume-preview-pane,.resume-edit-pane{width:100%;position:static}
.resume-edit-pane{padding-left:0;border-left:0;border-top:1px solid #e5ebe7;padding-top:20px}
.builder-fields{grid-template-columns:1fr}
.template-list{grid-template-columns:repeat(4,minmax(0,1fr))}
.item-actions{gap:5px}
.draft-toolbar>label{min-width:0;width:100%}
.draft-toolbar :deep(.el-select){width:100%}
.pdf-preview{height:580px}}
@media(max-width:560px){.resume-builder{padding:14px}
.workflow-steps{gap:2px;justify-content:space-between}
.workflow-steps button{padding:7px 4px;font-size:12px;gap:4px}
.workflow-steps button span{width:19px;height:19px;font-size:11px}
.step-arrow{width:12px}
.profile-tabs{gap:16px}
.profile-tabs button{font-size:12px}
.template-list{grid-template-columns:repeat(2,minmax(0,1fr))}
.builder-source,.builder-generate{padding:0}
.preview-actions{gap:6px}
.preview-actions :deep(.el-button){padding:8px 10px;font-size:12px}
.resume-paper{padding:24px 18px;font-size:11px}
.draft-preview{padding:10px;min-height:450px}
.template-card img{height:150px}}
</style>
