<script setup lang="ts">
import { ref, watch } from 'vue'
import { submitLearningEvidence, type LearningEvidence, type LearningTask } from '../../api/client'
import { parseEvidenceLinks, safeReferenceUrl } from './coreDeepening'
const props = defineProps<{ planId: string; task: LearningTask; readonly?: boolean }>()
const emit = defineEmits<{ saved: [evidence: LearningEvidence] }>()
const description = ref('')
const links = ref('')
const submitting = ref(false)
const error = ref('')
watch(() => `${props.planId}:${props.task.taskId}`, () => { description.value = ''; links.value = ''; error.value = '' })
async function submit() {
  if (submitting.value || props.readonly) return
  if (!description.value.trim()) { error.value = '请描述练习过程、实际结果与验收情况。'; return }
  submitting.value = true
  error.value = ''
  try {
    const evidence = await submitLearningEvidence(props.planId, props.task.taskId, { description: description.value.trim(), links: parseEvidenceLinks(links.value) })
    emit('saved', evidence)
    if (evidence.status === 'FAILED') error.value = evidence.error || '成果已保存，评价暂不可用。可使用相同内容重试。'
    else if (evidence.status === 'SUCCEEDED') { description.value = ''; links.value = '' }
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '提交失败，内容已保留。' }
  finally { submitting.value = false }
}
function retry(evidence: LearningEvidence) {
  description.value = evidence.description
  links.value = (evidence.links || []).join('\n')
  void submit()
}
</script>
<template><details class="learning-evidence"><summary>练习成果与独立评价 · {{ task.evidence?.length || 0 }} 份</summary><p class="notice">任务状态记录自报进度。提交成果后单独评价，作品链接作为引用保存；确认资料后再用于简历。</p><p v-if="task.prerequisites?.length"><b>前置条件：</b>{{ task.prerequisites.join('、') }}</p><details v-for="reference in task.references || []" :key="reference.documentId" class="reference"><summary>参考资料：{{ reference.title }}</summary><p>{{ reference.source }}</p><blockquote>{{ reference.snippet }}</blockquote></details><p v-if="!task.references?.length" class="notice">知识库中暂无合适参考资料。</p><div v-if="!readonly" class="evidence-input"><el-input v-model="description" type="textarea" :rows="3" :disabled="submitting" placeholder="成果说明：做了什么、如何验证、实际结果与尚未解决的问题" /><el-input v-model="links" type="textarea" :rows="2" :disabled="submitting" placeholder="作品链接（可选，每行一个 http 或 https 地址）" /><el-button type="primary" :loading="submitting" @click="submit">提交成果并评价</el-button><el-alert v-if="error" :title="error" type="warning" :closable="false" /></div><article v-for="evidence in task.evidence || []" :key="evidence.evidenceId" class="evidence-record"><header><strong>成果记录</strong><el-tag :type="evidence.status === 'SUCCEEDED' ? 'success' : 'warning'">{{ evidence.status === 'SUCCEEDED' ? '评价完成' : evidence.status === 'FAILED' ? '成果已保存 · 评价失败' : '已记录 · 待评价' }}</el-tag></header><p class="description">{{ evidence.description }}</p><a v-for="link in evidence.links || []" :key="link" :href="safeReferenceUrl(link)" target="_blank" rel="noopener noreferrer">{{ link }}</a><div v-if="evidence.evaluation"><strong>{{ evidence.evaluation.mocked ? '演示评价' : '成果评价' }} · {{ evidence.evaluation.score }} 分</strong><p>{{ evidence.evaluation.conclusion }}</p><p v-for="gap in evidence.evaluation.gaps || []" :key="gap">待补充：{{ gap }}</p><p v-for="suggestion in evidence.evaluation.suggestions || []" :key="suggestion">{{ suggestion }}</p><blockquote v-for="(note, index) in evidence.evaluation.evidence || []" :key="index">“{{ note.quote }}” — {{ note.finding }}</blockquote></div><p v-if="evidence.error" class="notice">{{ evidence.error }}</p><el-button v-if="evidence.status !== 'SUCCEEDED' && !readonly" :loading="submitting" @click="retry(evidence)">重试成果评价</el-button></article></details></template>
<style scoped>
.learning-evidence{grid-column:1/-1;border-top:1px solid var(--line,#dce3df);padding-top:12px;margin-top:4px;font-size:14px;min-width:0}
.learning-evidence summary{cursor:pointer;font-weight:600;line-height:1.8}
.notice{color:var(--muted,#64716b);line-height:1.7;font-size:13px}
.evidence-input{display:grid;gap:10px;margin:12px 0}.evidence-input .el-button{justify-self:start}
.evidence-record{border:0;border-top:1px solid var(--line,#dce3df);background:transparent;padding:14px 0;margin-top:12px}
.evidence-record header{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap}
.evidence-record a{display:block;color:var(--accent,#28664f);overflow-wrap:anywhere;margin:8px 0}
.description{white-space:pre-wrap;overflow-wrap:anywhere}.reference{margin:8px 0}
.learning-evidence p{line-height:1.7;overflow-wrap:anywhere}
blockquote{margin:9px 0;padding-left:10px;border-left:2px solid var(--accent,#28664f);white-space:pre-wrap;font-size:13px}
</style>
