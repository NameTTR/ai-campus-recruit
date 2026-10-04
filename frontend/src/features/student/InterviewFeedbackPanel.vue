<script setup lang="ts">
import type { InterviewQuestionFeedback } from '../../api/client'
defineProps<{ feedback: InterviewQuestionFeedback }>()
const noteLabels: Record<string, string> = { ERROR: '回答错误', INCORRECT: '回答错误', INSUFFICIENT_EVIDENCE: '依据不足', MISSING_EVIDENCE: '依据不足', MISSING: '尚未提供', SUPPORTED: '已有依据', STRENGTH: '已有依据' }
</script>
<template><section class="feedback-panel"><header><strong>本题评价 · {{ feedback.score ?? '—' }} 分</strong><el-tag :type="feedback.mocked ? 'warning' : 'success'">{{ feedback.mocked ? '演示评价' : '逐题评价' }}</el-tag></header><p>{{ feedback.summary }}</p><div v-if="feedback.dimensions?.length" class="dimensions"><article v-for="dimension in feedback.dimensions" :key="dimension.dimension"><strong>{{ dimension.label }} · {{ dimension.score }} 分</strong><p>{{ dimension.explanation }}</p></article></div><blockquote v-for="(note, index) in feedback.evidence || []" :key="index"><b>{{ noteLabels[note.type] || note.type }}</b><p v-if="note.quote">“{{ note.quote }}”</p><p>{{ note.finding }}</p></blockquote><ul v-if="feedback.suggestions?.length"><li v-for="item in feedback.suggestions" :key="item">{{ item }}</li></ul><small v-if="feedback.rubricVersion">评价标准 {{ feedback.rubricVersion }} · {{ feedback.analysisMetadata?.model || '未记录模型' }}</small></section></template>
<style scoped>
.feedback-panel{display:grid;gap:12px;margin:12px 0;padding:14px 0;border-top:2px solid var(--accent,#28664f);background:transparent;font-size:14px;min-width:0}
.feedback-panel header{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}
.dimensions{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.dimensions article{padding:10px 0;border:0;border-top:1px solid var(--line,#dce3df);min-width:0}
.dimensions article strong{font-size:13px}
.feedback-panel p{margin:4px 0;color:var(--muted,#64716b);line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}
.feedback-panel blockquote{border-left:2px solid var(--accent,#28664f);margin:0;padding-left:12px;font-size:13px}
.feedback-panel ul{padding-left:20px;margin:0;line-height:1.9}
.feedback-panel small{color:var(--muted,#64716b);font-size:12px;overflow-wrap:anywhere}
@media(max-width:560px){.dimensions{grid-template-columns:1fr;gap:10px}}
</style>
