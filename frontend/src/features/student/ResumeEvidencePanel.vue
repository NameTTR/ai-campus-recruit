<script setup lang="ts">
import { computed } from 'vue'
import type { ResumeFinding, StructuredResumeDiagnosis } from '../../api/client'
import EvidenceContextHint from './EvidenceContextHint.vue'
const props = defineProps<{ diagnosis: StructuredResumeDiagnosis; compact?: boolean }>()
const allFindings = computed(() => props.diagnosis.findings || [])
const primaryFindings = computed(() => (props.diagnosis.topFindings?.length ? props.diagnosis.topFindings : allFindings.value).slice(0, 3))
const findingKey = (finding: ResumeFinding) => [finding.category, finding.issue, finding.factUnitId || '', finding.sourceReference].join(':')
const primaryKeys = computed(() => new Set(primaryFindings.value.map(findingKey)))
const additionalFindings = computed(() => allFindings.value.filter(finding => !primaryKeys.value.has(findingKey(finding))))
const categories: Record<string, string> = { COMPLETENESS: '资料完整性', SKILL_COVERAGE: '岗位技能', PROJECT_EVIDENCE: '项目成果证据', EXPRESSION: '表达问题', EVIDENCE: '材料依据' }
</script>
<template>
  <section class="structured-diagnosis">
    <p class="basis">{{ diagnosis.jobSnapshot ? `依据实际岗位：${diagnosis.jobSnapshot.title}` : '依据通用岗位要求；可选择实际岗位获得更具体的诊断。' }}</p>
    <EvidenceContextHint :value="diagnosis" />
    <div class="metrics"><div><strong>{{ diagnosis.completenessScore }}%</strong><span>资料完整性</span></div><div><strong>{{ diagnosis.skillCoverage }}%</strong><span>已声明技能覆盖</span></div><div><strong>{{ diagnosis.evidenceCoverage }}%</strong><span>材料证据覆盖</span></div></div>
    <p class="basis">技能声明、材料依据与练习表现分别记录。优先核对下方三项。</p>
    <details v-for="(finding, index) in primaryFindings" :key="index" class="finding" :open="!compact">
      <summary>{{ categories[finding.category] || finding.category }} · {{ finding.issue }}</summary>
      <dl><dt>原句</dt><dd>{{ finding.originalQuote || '现有材料中尚未体现，需补充。' }}</dd><dt>建议修改</dt><dd>{{ finding.suggestedRewrite }}</dd><dt>依据</dt><dd>{{ finding.basis }}<small>{{ finding.sourceReference }}</small><details v-if="finding.followUpQuestions?.length" class="follow-up"><summary>补充问题</summary><ul><li v-for="question in finding.followUpQuestions.slice(0, 3)" :key="question">{{ question }}</li></ul></details></dd></dl>
    </details>
    <details v-if="additionalFindings.length" class="all-findings"><summary>查看全部建议（还有 {{ additionalFindings.length }} 项）</summary><details v-for="(finding, index) in additionalFindings" :key="index" class="finding nested-finding"><summary>{{ categories[finding.category] || finding.category }} · {{ finding.issue }}</summary><dl><dt>原句</dt><dd>{{ finding.originalQuote || '现有材料中尚未体现，需补充。' }}</dd><dt>建议修改</dt><dd>{{ finding.suggestedRewrite }}</dd><dt>依据</dt><dd>{{ finding.basis }}<small>{{ finding.sourceReference }}</small><details v-if="finding.followUpQuestions?.length" class="follow-up"><summary>补充问题</summary><ul><li v-for="question in finding.followUpQuestions.slice(0, 3)" :key="question">{{ question }}</li></ul></details></dd></dl></details></details>
    <details class="snapshot"><summary>查看分析来源与事实依据</summary><p>规则版本 {{ diagnosis.metadata?.algorithmVersion || '历史版本' }} · 模型 {{ diagnosis.metadata?.model || '规则' }} · 来源 {{ diagnosis.metadata?.source || '未记录' }}</p><p>{{ diagnosis.metadata?.generatedAt }}</p><div v-for="(item, index) in diagnosis.skillEvidence || []" :key="index"><b>{{ item.skill }} · {{ item.supported ? '已有材料支撑' : '材料中尚未体现' }}</b><blockquote v-if="item.quote">{{ item.quote }}</blockquote><p>{{ item.explanation }} · {{ item.sourceReference }}</p></div><div v-if="diagnosis.factUnits?.length" class="fact-units"><h4>事实单元</h4><article v-for="fact in diagnosis.factUnits" :key="fact.id"><blockquote>{{ fact.originalQuote }}</blockquote><small>{{ fact.sourceReference }} · 版本 {{ fact.sourceVersion }}</small><p v-if="fact.personalAction">个人行动：{{ fact.personalAction }}</p><p v-if="fact.methodOrTechnology">方法或技术：{{ fact.methodOrTechnology }}</p><p v-if="fact.projectScope">项目范围：{{ fact.projectScope }}</p><p v-if="fact.validationProcess">验证过程：{{ fact.validationProcess }}</p><p v-if="fact.result">结果：{{ fact.result }}</p><p v-if="fact.dataMissing">结果数据待补充</p></article></div><p>{{ diagnosis.profileSnapshot?.education }}</p><p>{{ diagnosis.profileSnapshot?.skills?.join('、') }}</p><p v-for="(project, index) in diagnosis.profileSnapshot?.projects || []" :key="index">{{ project }}</p></details>
  </section>
</template>
<style scoped>
.structured-diagnosis{display:grid;gap:12px;margin:16px 0;font-size:13px}.basis{color:#66716c;margin:0;line-height:1.7}.metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.metrics div{display:grid;gap:6px;padding:13px;background:#f0f8f4;border-radius:10px}.metrics strong{font-size:23px;color:#28664f}.metrics span{color:#66716c;font-size:12px}.finding,.snapshot,.all-findings{padding:12px;border:1px solid #e5ebe7;border-radius:10px}.finding summary,.snapshot summary,.all-findings>summary{cursor:pointer;font-weight:600;line-height:1.6}.nested-finding{margin-top:10px}.follow-up{margin-top:8px}.follow-up ul{margin:6px 0;padding-left:20px}.fact-units article{border-top:1px solid #e5ebe7;padding:10px 0}.fact-units h4{margin:12px 0 4px}dl{display:grid;grid-template-columns:66px 1fr;gap:8px;line-height:1.8}dt{color:#66716c}dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}small{display:block;color:#66716c}blockquote{border-left:3px solid #8db7a2;padding-left:12px;margin:8px 0;white-space:pre-wrap}.snapshot p{overflow-wrap:anywhere;line-height:1.7}@media(max-width:560px){.metrics{grid-template-columns:1fr}dl{grid-template-columns:1fr}}
</style>
