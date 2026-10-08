<script setup lang="ts">
import { computed } from 'vue'
import { evidenceContextStatus, evidenceContextStatusHint, evidenceContextStatusLabel, evidenceContextStatusType } from './coreDeepening'

const props = defineProps<{ value?: unknown; compact?: boolean }>()
const status = computed(() => evidenceContextStatus(props.value))
const label = computed(() => evidenceContextStatusLabel(status.value))
const hint = computed(() => evidenceContextStatusHint(status.value))
</script>

<template>
  <span v-if="status" class="context-hint">
    <el-tag size="small" :type="evidenceContextStatusType(status)">{{ label }}</el-tag>
    <small v-if="!compact">{{ hint }}</small>
  </span>
</template>

<style scoped>
.context-hint{display:inline-flex;align-items:center;gap:8px;flex-wrap:wrap}.context-hint small{color:#66716c;line-height:1.6}
</style>
