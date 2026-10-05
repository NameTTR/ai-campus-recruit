<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { addLearningEvidenceToResumeCandidate, confirmLearningEvidence, retryLearningEvidence, submitLearningEvidence, type LearningEvidence, type LearningTask } from '../../api/client'
import { parseEvidenceLinks, safeReferenceUrl } from './coreDeepening'
const props = defineProps<{ planId: string; task: LearningTask; readonly?: boolean; expanded?: boolean }>()
const emit = defineEmits<{
  saved: [evidence: LearningEvidence]
  resumeCandidate: [evidence: LearningEvidence]
  interviewFollowUp: [evidence: LearningEvidence]
}>()
const description = ref('')
const links = ref('')
const submitting = ref(false)
const error = ref('')
const confirmedIds = ref<Record<string, boolean>>({})
let restoringDraft = false
let activeDraftKey = ''
let requestVersion = 0
const draftKey = computed(() => {
  const userId = localStorage.getItem('userId')?.trim()
  return userId ? `aicampus.learning-evidence-draft.${[userId, props.planId, props.task.taskId].map(encodeURIComponent).join('.')}` : ''
})

function readDraft(key: string) {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) || '{}') as { description?: unknown; links?: unknown }
    return {
      description: typeof value.description === 'string' ? value.description : '',
      links: typeof value.links === 'string' ? value.links : ''
    }
  } catch {
    return { description: '', links: '' }
  }
}

function writeDraft(key: string, draft: { description: string; links: string }) {
  if (!key) return
  try {
    if (draft.description.trim() || draft.links.trim()) sessionStorage.setItem(key, JSON.stringify(draft))
    else sessionStorage.removeItem(key)
  } catch {
    // Storage restrictions should not prevent submitting a result.
  }
}

function restoreDraft() {
  requestVersion += 1
  submitting.value = false
  activeDraftKey = draftKey.value
  const draft = readDraft(activeDraftKey)
  restoringDraft = true
  description.value = draft.description
  links.value = draft.links
  restoringDraft = false
  error.value = ''
}

watch([() => props.planId, () => props.task.taskId], restoreDraft, { immediate: true, flush: 'sync' })
watch([description, links], () => {
  if (!restoringDraft && !props.readonly) writeDraft(activeDraftKey, { description: description.value, links: links.value })
}, { flush: 'sync' })
onBeforeUnmount(() => { requestVersion += 1 })
async function submit() {
  if (submitting.value || props.readonly) return
  if (!description.value.trim()) { error.value = '请描述练习过程、实际结果与验收情况。'; return }
  submitting.value = true
  error.value = ''
  const submissionKey = draftKey.value
  const submission = { description: description.value, links: links.value }
  const currentRequest = ++requestVersion
  try {
    const evidence = await submitLearningEvidence(props.planId, props.task.taskId, { description: description.value.trim(), links: parseEvidenceLinks(links.value) })
    emit('saved', evidence)
    if (evidence.status === 'SUCCEEDED' && JSON.stringify(readDraft(submissionKey)) === JSON.stringify(submission)) {
      writeDraft(submissionKey, { description: '', links: '' })
    }
    if (currentRequest !== requestVersion || submissionKey !== draftKey.value) return
    if (evidence.status === 'FAILED') error.value = evidence.error || '成果已保存，评价暂不可用。可使用相同内容重试。'
    else if (evidence.status === 'SUCCEEDED' && description.value === submission.description && links.value === submission.links) {
      description.value = ''
      links.value = ''
    }
  } catch (cause) {
    if (currentRequest === requestVersion && submissionKey === draftKey.value) {
      error.value = cause instanceof Error ? cause.message : '提交失败，内容已保留。'
    }
  } finally {
    if (currentRequest === requestVersion) submitting.value = false
  }
}
async function retry(evidence: LearningEvidence) {
  if (submitting.value || props.readonly) return
  submitting.value = true
  error.value = ''
  const retryKey = draftKey.value
  const currentRequest = ++requestVersion
  try {
    const updated = await retryLearningEvidence(props.planId, props.task.taskId, evidence.evidenceId)
    emit('saved', updated)
    return
  } catch {
    // Older deployments do not expose the retry endpoint; resubmit the same facts.
  } finally {
    if (currentRequest === requestVersion) submitting.value = false
  }
  if (currentRequest !== requestVersion || retryKey !== draftKey.value) return
  description.value = evidence.description
  links.value = (evidence.links || []).join('\n')
  void submit()
}
function evidenceConfirmed(evidence: LearningEvidence) {
  return Boolean(evidence.confirmed || confirmedIds.value[evidence.evidenceId]
    || localStorage.getItem(`aicampus.learning-evidence-confirmed.${evidence.evidenceId}`))
}
async function confirmEvidence(evidence: LearningEvidence) {
  const confirmedAt = new Date().toISOString()
  confirmedIds.value = { ...confirmedIds.value, [evidence.evidenceId]: true }
  localStorage.setItem(`aicampus.learning-evidence-confirmed.${evidence.evidenceId}`, confirmedAt)
  try {
    const updated = await confirmLearningEvidence(props.planId, props.task.taskId, evidence.evidenceId)
    emit('saved', { ...updated, confirmed: true, confirmedAt })
  } catch {
    emit('saved', { ...evidence, confirmed: true, confirmedAt })
  }
}
async function addResumeCandidate(evidence: LearningEvidence) {
  try {
    const updated = await addLearningEvidenceToResumeCandidate(props.planId, props.task.taskId, evidence.evidenceId)
    emit('saved', { ...updated, resumeCandidate: true })
  } catch {
    emit('saved', { ...evidence, resumeCandidate: true })
  }
  emit('resumeCandidate', { ...evidence, resumeCandidate: true })
}
function statusLabel(status: string) {
  if (status === 'SUCCEEDED') return '评价完成'
  if (status === 'CONFIRMED') return '已确认'
  if (status === 'RESUME_CANDIDATE') return '已加入简历候选'
  if (status === 'FAILED') return '成果已保存 · 评价失败'
  if (status === 'NEEDS_REVISION') return '需要补充后再评价'
  if (status === 'RECORDED') return '已记录 · 待评价'
  return status || '已记录'
}
function evidenceEvaluated(evidence: LearningEvidence) {
  return ['SUCCEEDED', 'RECORDED', 'CONFIRMED', 'RESUME_CANDIDATE'].includes(evidence.status)
}
</script>
<template><details class="learning-evidence" :open="expanded"><summary>练习成果与独立评价 · {{ task.evidence?.length || 0 }} 份</summary><p class="notice">任务状态记录自报进度。提交成果后单独评价，作品链接作为引用保存；确认资料后再用于简历。</p><p v-if="task.prerequisites?.length"><b>前置条件：</b>{{ task.prerequisites.join('、') }}</p><details v-for="reference in task.references || []" :key="reference.documentId" class="reference"><summary>参考资料：{{ reference.title }}</summary><p>{{ reference.source }}</p><blockquote>{{ reference.snippet }}</blockquote></details><p v-if="!task.references?.length" class="notice">知识库中暂无合适参考资料。</p><div v-if="!readonly" class="evidence-input"><el-input v-model="description" type="textarea" :rows="3" :disabled="submitting" placeholder="成果说明：做了什么、如何验证、实际结果与尚未解决的问题" /><el-input v-model="links" type="textarea" :rows="2" :disabled="submitting" placeholder="作品链接（可选，每行一个 http 或 https 地址）" /><el-button type="primary" :loading="submitting" @click="submit">提交成果并评价</el-button><el-alert v-if="error" :title="error" type="warning" :closable="false" /></div><article v-for="evidence in task.evidence || []" :key="evidence.evidenceId" class="evidence-record"><header><strong>成果记录</strong><div class="evidence-status"><el-tag :type="evidenceEvaluated(evidence) ? 'success' : 'warning'">{{ statusLabel(evidence.status) }}</el-tag><el-tag v-if="evidenceConfirmed(evidence)" type="success">已确认候选</el-tag><el-tag v-else-if="evidence.resumeCandidate" type="success">简历候选</el-tag></div></header><p class="description">{{ evidence.description }}</p><a v-for="link in evidence.links || []" :key="link" :href="safeReferenceUrl(link)" target="_blank" rel="noopener noreferrer">{{ link }}</a><div v-if="evidence.evaluation"><strong>{{ evidence.evaluation.mocked ? '演示评价' : '成果评价' }} · {{ evidence.evaluation.score }} 分</strong><p>{{ evidence.evaluation.conclusion }}</p><p v-for="gap in evidence.evaluation.gaps || []" :key="gap">待补充：{{ gap }}</p><p v-for="suggestion in evidence.evaluation.suggestions || []" :key="suggestion">{{ suggestion }}</p><blockquote v-for="(note, index) in evidence.evaluation.evidence || []" :key="index">“{{ note.quote }}” — {{ note.finding }}</blockquote></div><p v-if="evidence.error" class="notice">{{ evidence.error }}</p><div class="evidence-actions"><el-button v-if="!evidenceEvaluated(evidence) && !readonly" :loading="submitting" @click="retry(evidence)">重试成果评价</el-button><el-button v-if="evidenceEvaluated(evidence) && !evidenceConfirmed(evidence) && !readonly" size="small" type="success" plain @click="confirmEvidence(evidence)">确认评价并保留</el-button><el-button v-if="evidenceEvaluated(evidence) && evidenceConfirmed(evidence) && !evidence.resumeCandidate && !readonly" size="small" type="success" plain @click="addResumeCandidate(evidence)">加入简历候选</el-button><el-button v-if="evidenceEvaluated(evidence)" size="small" plain @click="emit('interviewFollowUp', evidence)">带着成果去面试追问</el-button><el-button v-if="evidenceEvaluated(evidence) && evidence.resumeCandidate" size="small" text @click="emit('resumeCandidate', evidence)">打开简历候选</el-button></div></article></details></template>
<style scoped>
.learning-evidence{grid-column:1/-1;border-top:1px solid var(--line,#dce3df);padding-top:12px;margin-top:4px;font-size:14px;min-width:0}.learning-evidence summary{cursor:pointer;font-weight:600;line-height:1.8}.notice{color:var(--muted,#64716b);line-height:1.7;font-size:13px}.evidence-input{display:grid;gap:10px;margin:12px 0}.evidence-input .el-button{justify-self:start}.evidence-record{border:0;border-top:1px solid var(--line,#dce3df);background:transparent;padding:14px 0;margin-top:12px}.evidence-record header{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap}.evidence-status,.evidence-actions{display:flex;align-items:center;gap:7px;flex-wrap:wrap}.evidence-actions{margin-top:10px}.evidence-record a{display:block;color:var(--accent,#28664f);overflow-wrap:anywhere;margin:8px 0}.description{white-space:pre-wrap;overflow-wrap:anywhere}.reference{margin:8px 0}.learning-evidence p{line-height:1.7;overflow-wrap:anywhere}blockquote{margin:9px 0;padding-left:10px;border-left:2px solid var(--accent,#28664f);white-space:pre-wrap;font-size:13px}
</style>
