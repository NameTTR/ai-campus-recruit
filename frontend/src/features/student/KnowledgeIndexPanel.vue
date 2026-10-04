<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { getKnowledgeIndexStatus, getKnowledgeIndexRebuild, rebuildKnowledgeIndex, type KnowledgeIndexRebuildStatus } from '../../api/client'
const status = ref<KnowledgeIndexRebuildStatus>()
const busy = ref(false)
const error = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
let disposed = false
const inProgress = () => ['PENDING', 'RUNNING', 'INDEXING'].includes(status.value?.status || '')
async function load() {
  error.value = ''
  try {
    status.value = status.value?.jobId && inProgress() ? await getKnowledgeIndexRebuild(status.value.jobId) : await getKnowledgeIndexStatus()
    if (!disposed && inProgress()) timer = setTimeout(load, 5000)
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '索引状态读取失败' }
}
async function rebuild() {
  if (busy.value || inProgress()) return
  busy.value = true
  error.value = ''
  try { status.value = await rebuildKnowledgeIndex(); if (!disposed && inProgress()) timer = setTimeout(load, 5000) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '索引重建失败' }
  finally { busy.value = false }
}
onMounted(load)
onUnmounted(() => { disposed = true; if (timer) clearTimeout(timer) })
</script>
<template><section class="index-panel"><header><div><h3>知识语义索引</h3><p>使用当前资料与权限重新生成语义索引，重建期间继续提供关键词检索。</p></div><el-button type="primary" :loading="busy || inProgress()" @click="rebuild">重建知识索引</el-button><el-button :disabled="busy" @click="load">刷新状态</el-button></header><div v-if="status" class="index-status"><el-tag :type="status.status === 'COMPLETED' ? 'success' : status.status === 'FAILED' ? 'danger' : 'info'">{{ status.status }}</el-tag><span>{{ status.model }} · {{ status.dimension }} 维</span><span>{{ status.completedDocuments }}/{{ status.totalDocuments }} 文档 · {{ status.indexedChunks }} 个片段</span><p>{{ status.message }}</p><small>索引版本 {{ status.indexVersion }} · {{ status.updatedAt }}</small></div><el-alert v-if="error" :title="error" type="warning" :closable="false" /></section></template>
<style scoped>
.index-panel{border:0;border-top:1px solid var(--line,#dce3df);border-radius:0;background:transparent;padding:18px 0;margin-bottom:18px;min-width:0}
.index-panel header{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.index-panel header>div{flex:1;min-width:min(220px,100%)}
.index-panel h3{margin:0;font-size:17px}
.index-panel p{font-size:14px;color:var(--muted,#64716b);line-height:1.7;overflow-wrap:anywhere}
.index-status{display:flex;align-items:center;gap:12px;flex-wrap:wrap;font-size:13px;padding-top:12px}
.index-status p,.index-status small{flex-basis:100%;margin:0;overflow-wrap:anywhere}
.index-status small{color:var(--muted,#64716b)}
</style>
