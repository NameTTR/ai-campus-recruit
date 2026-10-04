<script setup lang="ts">
import type { MatchDetails } from '../../api/client'
defineProps<{ details: MatchDetails }>()
const conditionLabels: Record<string, string> = { SATISFIED: '满足', NOT_SATISFIED: '不满足', UNKNOWN: '信息不足' }
</script>
<template><section class="match-evidence"><div class="metrics"><div><strong>{{ details.skillsCoverage }}%</strong><span>已声明技能覆盖率</span></div><div><strong>{{ details.evidenceCoverage }}%</strong><span>材料证据覆盖率</span></div></div><p>覆盖率用于核对岗位要求。材料尚未体现时，可以补充已有经历，或安排学习练习。</p><details v-for="item in details.requirements || []" :key="item.skill" class="requirement"><summary><strong>{{ item.skill }}</strong><el-tag :type="item.declared ? 'success' : 'info'">{{ item.declared ? '已声明' : '未声明' }}</el-tag><el-tag :type="item.supported ? 'success' : 'warning'">{{ item.supported ? '已有材料支撑' : '待补材料' }}</el-tag></summary><blockquote v-if="item.evidence?.quote">{{ item.evidence.quote }}</blockquote><p>{{ item.evidence?.explanation || '材料中尚未体现。' }}</p><p>{{ item.suggestion }}</p><small v-if="item.evidence">来源：{{ item.evidence.sourceReference }}</small></details><h4 v-if="details.conditions?.length">明确岗位条件</h4><div v-for="condition in details.conditions || []" :key="`${condition.type}-${condition.requirement}`" class="condition"><strong>{{ condition.requirement }}</strong><el-tag :type="condition.status === 'SATISFIED' ? 'success' : condition.status === 'NOT_SATISFIED' ? 'danger' : 'info'">{{ conditionLabels[condition.status] || condition.status }}</el-tag><p>{{ condition.explanation }}</p><small>{{ condition.observed }}</small></div><details class="snapshot"><summary>匹配依据与历史快照</summary><p>{{ details.metadata?.algorithmVersion }} · {{ details.metadata?.source }} · {{ details.metadata?.generatedAt }}</p><p>{{ details.jobSnapshot?.title }} · {{ details.jobSnapshot?.requiredSkills?.join('、') }}</p><p>{{ details.profileSnapshot?.education }}</p><p>{{ details.profileSnapshot?.skills?.join('、') }}</p><p v-for="project in details.profileSnapshot?.projects || []" :key="project">{{ project }}</p></details></section></template>
<style scoped>
.match-evidence{display:grid;gap:12px;margin-top:14px;font-size:14px;min-width:0}
.metrics{display:grid;grid-template-columns:1fr 1fr;padding:12px 0;border-block:1px solid var(--line,#dce3df)}
.metrics div{display:grid;gap:5px;padding:0 14px;min-width:0;border-right:1px solid var(--line,#dce3df)}
.metrics div:first-child{padding-left:0}.metrics div:last-child{border:0;padding-right:0}
.metrics strong{font:600 26px/1.2 "IBM Plex Mono",Consolas,monospace;color:var(--accent,#28664f)}
.metrics span{font-size:13px;color:var(--muted,#64716b);line-height:1.5}
.match-evidence p,.match-evidence small{color:var(--muted,#64716b);line-height:1.7}
.requirement,.condition,.snapshot{padding:12px 0;border:0;border-top:1px solid var(--line,#dce3df)}
.requirement summary{display:flex;gap:7px;align-items:center;cursor:pointer;flex-wrap:wrap;line-height:1.6}
.requirement summary strong{margin-right:auto}
blockquote{border-left:2px solid var(--accent,#28664f);margin:12px 0;padding-left:12px;white-space:pre-wrap}
.condition .el-tag{margin-left:10px}.snapshot summary{cursor:pointer;font-size:13px}
h4{margin:8px 0 0;font-size:14px}p{margin:5px 0;overflow-wrap:anywhere}
small{font-size:12px;overflow-wrap:anywhere}
@media(max-width:560px){.metrics strong{font-size:23px}.metrics div{padding-inline:10px}.metrics span{font-size:12px}.condition>.el-tag{margin:8px 0 0}}
</style>
