<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  AlertTriangle,
  BarChart3,
  BriefcaseBusiness,
  Building2,
  Database,
  GraduationCap,
  HardDrive,
  RefreshCw,
  Send,
  ServerCog,
  Timer
} from 'lucide-vue-next'
import {
  getDashboard,
  getSystemStatus,
  type DashboardStats,
  type DeliveryStatus,
  type SystemServiceStatus,
  type SystemStatus
} from '../api/client'

const route = useRoute()
const stats = ref<DashboardStats>()
const systemStatus = ref<SystemStatus>()
const systemStatusLoading = ref(false)
const statusLabels: Record<DeliveryStatus, string> = {
  SUBMITTED: '已投递',
  VIEWED: '已查看',
  INTERVIEW: '面试中',
  OFFER: '已录用',
  REJECTED: '未通过'
}
const statusTypes: Record<DeliveryStatus, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  SUBMITTED: 'info',
  VIEWED: 'primary',
  INTERVIEW: 'warning',
  OFFER: 'success',
  REJECTED: 'danger'
}
const statusRows = computed(() => {
  const counts = stats.value?.deliveryStatusCounts
  if (!counts) {
    return []
  }
  return (Object.keys(statusLabels) as DeliveryStatus[]).map((status) => ({
    status,
    label: statusLabels[status],
    count: counts[status] || 0,
    type: statusTypes[status]
  }))
})
const activeModule = computed(() => typeof route.params.module === 'string' ? route.params.module : 'overview')
const serviceRows = computed(() => systemStatus.value?.services || [])
const persistenceRows = computed(() => systemStatus.value?.persistence || [])
const infrastructureRows = computed(() => systemStatus.value?.infrastructure || [])
const warningRows = computed(() => systemStatus.value?.warnings || [])

onMounted(async () => {
  const [dashboard, status] = await Promise.all([getDashboard(), getSystemStatus()])
  stats.value = dashboard
  systemStatus.value = status
})

function statusPercent(count: number) {
  if (!stats.value?.deliveryCount) {
    return 0
  }
  return Math.round((count / stats.value.deliveryCount) * 100)
}

async function refreshSystemStatus() {
  systemStatusLoading.value = true
  try {
    systemStatus.value = await getSystemStatus()
  } finally {
    systemStatusLoading.value = false
  }
}

function systemTagType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  const normalized = status.toUpperCase()
  if (['UP', 'ONLINE', 'CONFIGURED', 'ENABLED'].includes(normalized)) {
    return 'success'
  }
  if (['DOWN', 'FAILED', 'ERROR'].includes(normalized)) {
    return 'danger'
  }
  if (['DISABLED', 'OPTIONAL', 'UNKNOWN'].includes(normalized)) {
    return 'warning'
  }
  return 'info'
}

function enabledTagType(enabled: boolean): 'success' | 'warning' {
  return enabled ? 'success' : 'warning'
}

function formatDateTime(value?: string) {
  if (!value) {
    return '待生成'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function servicePort(row: SystemServiceStatus) {
  if (row.defaultPort && row.defaultPort !== row.port) {
    return `${row.port} / ${row.defaultPort}`
  }
  return String(row.port)
}
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1 class="page-title">学校就业看板</h1>
        <p class="page-subtitle">就业办 · 2026 届校园招聘</p>
      </div>
    </header>

    <div v-if="activeModule === 'overview'" class="grid three">
      <div class="metric">
        <GraduationCap :size="22" />
        <span>学生数</span>
        <strong>{{ stats?.studentCount }}</strong>
      </div>
      <div class="metric">
        <Building2 :size="22" />
        <span>企业数</span>
        <strong>{{ stats?.companyCount }}</strong>
      </div>
      <div class="metric">
        <BriefcaseBusiness :size="22" />
        <span>岗位数</span>
        <strong>{{ stats?.jobCount }}</strong>
      </div>
      <div class="metric">
        <Send :size="22" />
        <span>投递数</span>
        <strong>{{ stats?.deliveryCount }}</strong>
      </div>
      <div class="metric">
        <Timer :size="22" />
        <span>待处理投递</span>
        <strong>{{ stats?.pendingDeliveryCount }}</strong>
      </div>
      <div class="metric">
        <BarChart3 :size="22" />
        <span>平均匹配度</span>
        <strong>{{ stats?.averageMatchScore }}</strong>
      </div>
    </div>

    <section v-if="activeModule === 'status'" class="panel module-panel">
      <h2 class="panel-title">投递状态分布</h2>
      <el-table :data="statusRows" style="width: 100%">
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="row.type">{{ row.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="数量" width="120" />
        <el-table-column label="占比">
          <template #default="{ row }">
            <el-progress :percentage="statusPercent(row.count)" :stroke-width="10" />
          </template>
        </el-table-column>
      </el-table>
    </section>

    <div v-if="activeModule === 'system'" class="module-stack">
      <div class="grid three">
        <div class="metric system-metric">
          <ServerCog :size="22" />
          <span>状态来源</span>
          <strong>{{ systemStatus?.applicationName || 'user-service' }}</strong>
        </div>
        <div class="metric system-metric">
          <HardDrive :size="22" />
          <span>运行环境</span>
          <strong>{{ systemStatus?.environment || 'unknown' }}</strong>
        </div>
        <div class="metric system-metric">
          <AlertTriangle :size="22" />
          <span>告警数</span>
          <strong>{{ warningRows.length }}</strong>
        </div>
      </div>

      <section class="panel module-panel">
        <h2 class="panel-title">
          服务运行状态
          <span class="panel-title-actions">
            <span class="generated-at">{{ formatDateTime(systemStatus?.generatedAt) }}</span>
            <el-button circle size="small" :loading="systemStatusLoading" @click="refreshSystemStatus">
              <RefreshCw :size="15" />
            </el-button>
            <ServerCog :size="19" />
          </span>
        </h2>
        <el-table class="system-table" :data="serviceRows" style="width: 100%">
          <el-table-column label="服务" min-width="168">
            <template #default="{ row }">
              <div class="system-name">
                <strong>{{ row.displayName }}</strong>
                <span>{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="端口" width="104">
            <template #default="{ row }">{{ servicePort(row) }}</template>
          </el-table-column>
          <el-table-column prop="healthPath" label="健康检查" min-width="150" />
          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <el-tag :type="systemTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="说明" min-width="180" />
        </el-table>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          持久化与缓存
          <Database :size="19" />
        </h2>
        <el-table class="system-table" :data="persistenceRows" style="width: 100%">
          <el-table-column prop="module" label="模块" min-width="120" />
          <el-table-column label="启用" width="92">
            <template #default="{ row }">
              <el-tag :type="enabledTagType(row.enabled)">{{ row.enabled ? '已启用' : '未启用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="database" label="数据库" min-width="160" />
          <el-table-column prop="cacheKeyPrefix" label="缓存前缀" min-width="180" />
          <el-table-column prop="note" label="说明" min-width="180" />
        </el-table>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          基础设施
          <HardDrive :size="19" />
        </h2>
        <el-table class="system-table" :data="infrastructureRows" style="width: 100%">
          <el-table-column prop="name" label="组件" min-width="120" />
          <el-table-column label="地址" min-width="160">
            <template #default="{ row }">{{ row.host }}:{{ row.port }}</template>
          </el-table-column>
          <el-table-column label="配置" width="92">
            <template #default="{ row }">
              <el-tag :type="enabledTagType(row.configured)">{{ row.configured ? '已配置' : '未配置' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <el-tag :type="systemTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="说明" min-width="180" />
        </el-table>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          运维提示
          <AlertTriangle :size="19" />
        </h2>
        <div v-if="warningRows.length" class="warning-list">
          <el-alert
            v-for="warning in warningRows"
            :key="warning"
            :title="warning"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
        <el-empty v-else description="暂无运维提示" />
      </section>
    </div>

    <section v-if="activeModule === 'guidance'" class="panel module-panel">
      <h2 class="panel-title">就业指导关注点</h2>
      <el-timeline>
        <el-timeline-item timestamp="简历质量">项目经历需要补充量化指标和部署信息</el-timeline-item>
        <el-timeline-item timestamp="岗位需求">Java 后端、测试开发、数据分析岗位热度较高</el-timeline-item>
        <el-timeline-item timestamp="辅导安排">建议组织 Spring Cloud、Redis、MySQL 面试专题</el-timeline-item>
      </el-timeline>
    </section>
  </section>
</template>

<style scoped>
.panel-title-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.generated-at {
  color: #667085;
  font-size: 13px;
  font-weight: 500;
}

.system-metric strong {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 22px;
  line-height: 1.2;
}

.system-table {
  min-width: 0;
}

:deep(.system-table .cell) {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.system-name {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.system-name span {
  color: #667085;
  font-size: 13px;
}

.warning-list {
  display: grid;
  gap: 10px;
}

@media (max-width: 640px) {
  .panel-title-actions {
    align-items: flex-start;
    flex-direction: column-reverse;
  }
}
</style>
