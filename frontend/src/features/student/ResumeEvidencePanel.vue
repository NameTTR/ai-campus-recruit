<script setup lang="ts">
import type { StructuredResumeDiagnosis } from '../../api/client'
defineProps<{ diagnosis: StructuredResumeDiagnosis; compact?: boolean }>()
const categories: Record<string, string> = { COMPLETENESS: '资料完整性', SKILL_COVERAGE: '岗位技能', PROJECT_EVIDENCE: '项目成果证据', EXPRESSION: '表达问题', EVIDENCE: '材料依据' }
</script>
<template>
  <section class="structured-diagnosis">
    <el-alert v-if="diagnosis.stale" title="资料或岗位要求已变化；这是当时的诊断，请重新生成。" type="warning" :closable="false" show-icon />
    <p class="basis">{{ diagnosis.jobSnapshot ? `依据实际岗位：${diagnosis.jobSnapshot.title}` : '依据通用岗位要求；可选择实际岗位获得更具体的诊断。' }}</p>
    <div class="metrics"><div><strong>{{ diagnosis.completenessScore }}%</strong><span>资料完整性</span></div><div><strong>{{ diagnosis.skillCoverage }}%</strong><span>已声明技能覆盖</span></div><div><strong>{{ diagnosis.evidenceCoverage }}%</strong><span>材料证据覆盖</span></div></div>
    <p class="basis">技能声明、材料依据与练习表现分别记录。下方建议需核对后编辑保存。</p>
    <details v-for="(finding, index) in diagnosis.findings || []" :key="index" class="finding" :open="!compact && index < 2">
      <summary>{{ categories[finding.category] || finding.category }} · {{ finding.issue }}</summary>
      <dl><dt>原句</dt><dd>{{ finding.originalQuote || '现有材料中尚未体现，需补充。' }}</dd><dt>建议修改</dt><dd>{{ finding.suggestedRewrite }}</dd><dt>依据</dt><dd>{{ finding.basis }}<small>{{ finding.sourceReference }}</small></dd></dl>
    </details>
    <details class="snapshot"><summary>查看分析来源与当时资料</summary><p>规则版本 {{ diagnosis.metadata?.algorithmVersion || '历史版本' }} · 模型 {{ diagnosis.metadata?.model || '规则' }} · 来源 {{ diagnosis.metadata?.source || '未记录' }}</p><p>{{ diagnosis.metadata?.generatedAt }}</p><div v-for="(item, index) in diagnosis.skillEvidence || []" :key="index"><b>{{ item.skill }} · {{ item.supported ? '已有材料支撑' : '材料中尚未体现' }}</b><blockquote v-if="item.quote">{{ item.quote }}</blockquote><p>{{ item.explanation }} · {{ item.sourceReference }}</p></div><p>{{ diagnosis.profileSnapshot?.education }}</p><p>{{ diagnosis.profileSnapshot?.skills?.join('、') }}</p><p v-for="(project, index) in diagnosis.profileSnapshot?.projects || []" :key="index">{{ project }}</p></details>
  </section>
</template>
<style scoped>
.structured-diagnosis{display:grid;gap:12px;margin:16px 0;font-size:14px;min-width:0}
.basis{color:var(--muted,#64716b);margin:0;line-height:1.7;font-size:13px}
.metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));border-block:1px solid var(--line,#dce3df);padding:12px 0}
.metrics div{display:grid;gap:5px;padding:0 12px;border-right:1px solid var(--line,#dce3df);min-width:0}
.metrics div:first-child{padding-left:0}.metrics div:last-child{padding-right:0;border-right:0}
.metrics strong{font:600 22px/1.2 "IBM Plex Mono",Consolas,monospace;color:var(--accent,#28664f)}
.metrics span{color:var(--muted,#64716b);font-size:12px;line-height:1.5}
.finding,.snapshot{padding:12px 0;border:0;border-top:1px solid var(--line,#dce3df);min-width:0}
.finding summary,.snapshot summary{cursor:pointer;font-weight:600;line-height:1.7;overflow-wrap:anywhere}
dl{display:grid;grid-template-columns:66px minmax(0,1fr);gap:8px;line-height:1.8;font-size:13px}
dt{color:var(--muted,#64716b)}dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}
small{display:block;color:var(--muted,#64716b);font-size:12px;line-height:1.6}
blockquote{border-left:2px solid var(--accent,#28664f);padding-left:12px;margin:8px 0;white-space:pre-wrap}
.snapshot p{overflow-wrap:anywhere;line-height:1.7;font-size:13px;color:var(--muted,#64716b)}
@media(max-width:560px){.metrics div{padding:0 7px}.metrics strong{font-size:20px}dl{grid-template-columns:1fr;gap:5px}}
</style>
