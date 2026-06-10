<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import {
  AlertTriangle,
  BarChart3,
  BriefcaseBusiness,
  Building2,
  CheckCircle2,
  ClipboardCheck,
  Database,
  GraduationCap,
  HardDrive,
  LockKeyhole,
  RefreshCw,
  Rocket,
  Send,
  ServerCog,
  ShieldCheck,
  Timer
} from 'lucide-vue-next'
import {
  changeAccountPassword,
  createAccount,
  getDeploymentTopology,
  getDeploymentGuide,
  getDashboard,
  getCurrentPermissions,
  getSystemStatus,
  listAccounts,
  updateAccountStatus,
  type AccountStatus,
  type AccountSummary,
  type CurrentPermissions,
  type DashboardStats,
  type DeploymentGuide,
  type DeploymentTopology,
  type DeliveryStatus,
  type Role,
  type SystemServiceStatus,
  type SystemStatus
} from '../api/client'

const route = useRoute()
const stats = ref<DashboardStats>()
const systemStatus = ref<SystemStatus>()
const deploymentTopology = ref<DeploymentTopology>()
const deploymentGuide = ref<DeploymentGuide>()
const accounts = ref<AccountSummary[]>([])
const currentPermissions = ref<CurrentPermissions>()
const systemStatusLoading = ref(false)
const accountsLoading = ref(false)
const accountFilters = reactive({
  role: '',
  status: '',
  keyword: ''
})
const accountForm = reactive({
  username: 'student02',
  password: '123456',
  displayName: 'Student 02',
  role: 'STUDENT' as Role,
  status: 'ACTIVE' as AccountStatus
})
const passwordForm = reactive({
  accountId: '',
  newPassword: '123456'
})
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
const topologyNodes = computed(() => deploymentTopology.value?.nodes || [])
const topologyWarnings = computed(() => deploymentTopology.value?.warnings || [])
const deploymentSteps = computed(() => deploymentGuide.value?.steps || [])
const acceptanceChecks = computed(() => deploymentGuide.value?.acceptanceChecks || [])
const deploymentWarnings = computed(() => deploymentGuide.value?.warnings || [])
const permissionTags = computed(() => currentPermissions.value?.permissions || [])

onMounted(async () => {
  const [dashboard, status, topology, guide, accountList, permissions] = await Promise.all([
    getDashboard(),
    getSystemStatus(),
    getDeploymentTopology(),
    getDeploymentGuide(),
    listAccounts(),
    getCurrentPermissions()
  ])
  stats.value = dashboard
  systemStatus.value = status
  deploymentTopology.value = topology
  deploymentGuide.value = guide
  accounts.value = accountList
  currentPermissions.value = permissions
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
    const [status, topology] = await Promise.all([getSystemStatus(), getDeploymentTopology()])
    systemStatus.value = status
    deploymentTopology.value = topology
  } finally {
    systemStatusLoading.value = false
  }
}

async function refreshAccounts() {
  accountsLoading.value = true
  try {
    accounts.value = await listAccounts({
      role: accountFilters.role as Role || undefined,
      status: accountFilters.status as AccountStatus || undefined,
      keyword: accountFilters.keyword
    })
    currentPermissions.value = await getCurrentPermissions()
  } finally {
    accountsLoading.value = false
  }
}

async function submitAccount() {
  const account = await createAccount({
    username: accountForm.username,
    password: accountForm.password,
    displayName: accountForm.displayName,
    role: accountForm.role,
    status: accountForm.status
  })
  accounts.value = [account, ...accounts.value.filter((item) => item.accountId !== account.accountId)]
  passwordForm.accountId = account.accountId
  ElMessage.success('账号已创建')
}

async function setAccountStatus(account: AccountSummary, status: AccountStatus) {
  const updated = await updateAccountStatus(account.accountId, status)
  accounts.value = accounts.value.map((item) => item.accountId === updated.accountId ? updated : item)
  ElMessage.success('账号状态已更新')
}

async function resetAccountPassword() {
  if (!passwordForm.accountId.trim()) {
    ElMessage.warning('请选择账号')
    return
  }
  await changeAccountPassword({
    accountId: passwordForm.accountId.trim(),
    newPassword: passwordForm.newPassword
  })
  ElMessage.success('密码已重置')
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

function stepTagType(nodeId: string): 'success' | 'warning' | 'info' {
  if (nodeId === 'vm3') {
    return 'success'
  }
  if (['acceptance', 'all'].includes(nodeId)) {
    return 'warning'
  }
  return 'info'
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

    <div v-if="activeModule === 'accounts'" class="module-stack">
      <section class="panel module-panel">
        <h2 class="panel-title">
          当前权限
          <ShieldCheck :size="19" />
        </h2>
        <div class="permission-summary">
          <div>
            <strong>{{ currentPermissions?.userId || 'A001' }}</strong>
            <span>{{ currentPermissions?.role || 'ADMIN' }}</span>
          </div>
          <div class="permission-tags">
            <span v-for="permission in permissionTags" :key="permission" class="permission-chip">
              {{ permission }}
            </span>
          </div>
        </div>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          用户账号
          <span class="panel-title-actions">
            <el-button circle size="small" :loading="accountsLoading" @click="refreshAccounts">
              <RefreshCw :size="15" />
            </el-button>
            <ShieldCheck :size="19" />
          </span>
        </h2>
        <div class="account-toolbar">
          <el-select v-model="accountFilters.role" clearable placeholder="角色" style="width: 100%">
            <el-option label="STUDENT" value="STUDENT" />
            <el-option label="COMPANY" value="COMPANY" />
            <el-option label="ADMIN" value="ADMIN" />
          </el-select>
          <el-select v-model="accountFilters.status" clearable placeholder="状态" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DISABLED" value="DISABLED" />
            <el-option label="LOCKED" value="LOCKED" />
          </el-select>
          <el-input v-model="accountFilters.keyword" clearable placeholder="账号 / 姓名 / ID" />
          <el-button :loading="accountsLoading" @click="refreshAccounts">筛选</el-button>
        </div>

        <el-table v-loading="accountsLoading" class="account-table" :data="accounts" style="width: 100%">
          <el-table-column prop="accountId" label="ID" min-width="86" />
          <el-table-column prop="username" label="账号" min-width="120" />
          <el-table-column prop="displayName" label="名称" min-width="140" />
          <el-table-column label="角色" width="104">
            <template #default="{ row }">
              <span class="account-badge role">{{ row.role }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <span class="account-badge" :class="row.status.toLowerCase()">{{ row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column label="权限" min-width="260">
            <template #default="{ row }">
              <div class="permission-tags compact">
                <span v-for="permission in row.permissions" :key="permission" class="permission-chip compact">
                  {{ permission }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="260">
            <template #default="{ row }">
              <div class="account-actions">
                <el-button size="small" :type="row.status === 'ACTIVE' ? 'success' : 'default'" @click="setAccountStatus(row, 'ACTIVE')">启用</el-button>
                <el-button size="small" :type="row.status === 'DISABLED' ? 'warning' : 'default'" @click="setAccountStatus(row, 'DISABLED')">禁用</el-button>
                <el-button size="small" :type="row.status === 'LOCKED' ? 'danger' : 'default'" @click="setAccountStatus(row, 'LOCKED')">锁定</el-button>
                <el-button size="small" @click="passwordForm.accountId = row.accountId">重置密码</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <div class="account-grid">
        <section class="panel module-panel">
          <h2 class="panel-title">
            创建账号
            <ShieldCheck :size="19" />
          </h2>
          <el-form label-position="top">
            <div class="grid two">
              <el-form-item label="账号">
                <el-input v-model="accountForm.username" />
              </el-form-item>
              <el-form-item label="初始密码">
                <el-input v-model="accountForm.password" type="password" show-password />
              </el-form-item>
            </div>
            <el-form-item label="显示名称">
              <el-input v-model="accountForm.displayName" />
            </el-form-item>
            <div class="grid two">
              <el-form-item label="角色">
                <el-select v-model="accountForm.role">
                  <el-option label="STUDENT" value="STUDENT" />
                  <el-option label="COMPANY" value="COMPANY" />
                  <el-option label="ADMIN" value="ADMIN" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="accountForm.status">
                  <el-option label="ACTIVE" value="ACTIVE" />
                  <el-option label="DISABLED" value="DISABLED" />
                  <el-option label="LOCKED" value="LOCKED" />
                </el-select>
              </el-form-item>
            </div>
            <el-button type="primary" @click="submitAccount">创建</el-button>
          </el-form>
        </section>

        <section class="panel module-panel">
          <h2 class="panel-title">
            密码重置
            <LockKeyhole :size="19" />
          </h2>
          <el-form label-position="top">
            <el-form-item label="账号 ID">
              <el-input v-model="passwordForm.accountId" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-button type="primary" @click="resetAccountPassword">重置</el-button>
          </el-form>
        </section>
      </div>
    </div>

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
          部署拓扑
          <HardDrive :size="19" />
        </h2>
        <div class="topology-list">
          <article v-for="node in topologyNodes" :key="node.id" class="topology-node">
            <header class="topology-node-header">
              <div>
                <strong>{{ node.name }}</strong>
                <span>{{ node.host }}</span>
              </div>
              <el-tag type="info">{{ node.role }}</el-tag>
            </header>
            <div class="topology-services">
              <div v-for="service in node.services" :key="`${node.id}-${service.name}`" class="topology-service">
                <div class="topology-service-main">
                  <strong>{{ service.displayName }}</strong>
                  <span>{{ service.name }}</span>
                </div>
                <span class="topology-port">{{ service.port }}</span>
                <el-tag :type="systemTagType(service.status)">{{ service.status }}</el-tag>
                <span class="topology-health">{{ service.healthUrl }}</span>
                <span v-if="service.note" class="topology-note">{{ service.note }}</span>
              </div>
            </div>
          </article>
        </div>
        <div v-if="topologyWarnings.length" class="topology-warnings">
          <el-alert
            v-for="warning in topologyWarnings"
            :key="warning"
            :title="warning"
            type="info"
            :closable="false"
            show-icon
          />
        </div>
      </section>

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

    <div v-if="activeModule === 'deploy'" class="module-stack">
      <section class="panel module-panel deploy-hero">
        <div>
          <h2 class="panel-title">
            部署启动向导
            <Rocket :size="20" />
          </h2>
          <p>{{ deploymentGuide?.summary || '按三虚拟机顺序启动并验收校园招聘系统。' }}</p>
        </div>
        <el-tag type="info">{{ deploymentGuide?.environment || 'frontend-demo' }}</el-tag>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          启动顺序
          <ClipboardCheck :size="19" />
        </h2>
        <div class="deploy-steps">
          <article v-for="step in deploymentSteps" :key="step.order" class="deploy-step">
            <div class="deploy-step-index">{{ step.order }}</div>
            <div class="deploy-step-body">
              <header class="deploy-step-header">
                <div>
                  <strong>{{ step.title }}</strong>
                  <span>{{ step.nodeName }}</span>
                </div>
                <el-tag :type="stepTagType(step.nodeId)">{{ step.nodeId }}</el-tag>
              </header>
              <p>{{ step.purpose }}</p>

              <div class="deploy-command-list">
                <code v-for="command in step.commands" :key="command">{{ command }}</code>
              </div>

              <div class="deploy-check-grid">
                <div>
                  <span class="deploy-label">检查地址</span>
                  <span v-for="url in step.verifyUrls" :key="url" class="deploy-url">{{ url }}</span>
                </div>
                <div>
                  <span class="deploy-label">期望结果</span>
                  <strong>{{ step.expectedResult }}</strong>
                </div>
              </div>

              <div class="deploy-troubleshooting">
                <span class="deploy-label">排障提示</span>
                <span v-for="item in step.troubleshooting" :key="item">{{ item }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          验收命令
          <CheckCircle2 :size="19" />
        </h2>
        <div class="acceptance-list">
          <article v-for="check in acceptanceChecks" :key="check.name" class="acceptance-item">
            <div>
              <strong>{{ check.name }}</strong>
              <code>{{ check.command }}</code>
            </div>
            <span>{{ check.expectedResult }}</span>
          </article>
        </div>
      </section>

      <section class="panel module-panel">
        <h2 class="panel-title">
          向导提示
          <AlertTriangle :size="19" />
        </h2>
        <div v-if="deploymentWarnings.length" class="warning-list">
          <el-alert
            v-for="warning in deploymentWarnings"
            :key="warning"
            :title="warning"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
        <el-empty v-else description="暂无向导提示" />
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

.permission-summary {
  align-items: start;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(160px, 0.35fr) minmax(0, 1fr);
  min-width: 0;
}

.permission-summary > div:first-child {
  display: grid;
  gap: 4px;
}

.permission-summary span {
  color: #667085;
}

.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.permission-tags.compact {
  gap: 6px;
}

.permission-chip {
  border: 1px solid #b7d6ff;
  border-radius: 6px;
  background: #eef6ff;
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  max-width: 100%;
  padding: 3px 8px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.permission-chip.compact {
  min-height: 22px;
  padding: 2px 6px;
  font-size: 11px;
}

.account-toolbar {
  display: grid;
  gap: 10px;
  grid-template-columns: 150px 150px minmax(180px, 1fr) auto;
  margin-bottom: 14px;
  min-width: 0;
}

.account-toolbar :deep(.el-select),
.account-toolbar :deep(.el-input) {
  width: 100%;
}

.account-table {
  min-width: 0;
}

:deep(.account-table .cell) {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.account-badge {
  border: 1px solid #d0d5dd;
  border-radius: 5px;
  color: #344054;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 22px;
  padding: 2px 7px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.account-badge.role {
  background: #f8fafc;
}

.account-badge.active {
  border-color: #86efac;
  background: #f0fdf4;
  color: #15803d;
}

.account-badge.disabled {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.account-badge.locked {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.account-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.account-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.65fr);
}

.topology-list {
  display: grid;
  gap: 0;
}

.topology-node {
  border-bottom: 1px solid #e4e7ec;
  display: grid;
  gap: 14px;
  padding: 16px 0;
}

.topology-node:first-child {
  padding-top: 0;
}

.topology-node:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.topology-node-header {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-width: 0;
}

.topology-node-header div,
.topology-service-main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.topology-node-header span,
.topology-service-main span,
.topology-health,
.topology-note {
  color: #667085;
  font-size: 13px;
}

.topology-services {
  display: grid;
  gap: 10px;
}

.topology-service {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(138px, 1fr) 64px 112px minmax(180px, 1.4fr);
  min-width: 0;
}

.topology-port {
  color: #101828;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.topology-health,
.topology-note {
  min-width: 0;
  overflow-wrap: anywhere;
}

.topology-note {
  grid-column: 4;
}

.topology-warnings {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.deploy-hero {
  align-items: flex-start;
  display: flex;
  gap: 16px;
  justify-content: space-between;
}

.deploy-hero p {
  color: #475467;
  margin: 8px 0 0;
}

.deploy-steps {
  display: grid;
  gap: 18px;
}

.deploy-step {
  display: grid;
  gap: 14px;
  grid-template-columns: 36px minmax(0, 1fr);
}

.deploy-step-index {
  align-items: center;
  background: #0f766e;
  border-radius: 8px;
  color: #fff;
  display: inline-flex;
  font-weight: 800;
  height: 36px;
  justify-content: center;
  width: 36px;
}

.deploy-step-body {
  border-bottom: 1px solid #e4e7ec;
  display: grid;
  gap: 12px;
  min-width: 0;
  padding-bottom: 18px;
}

.deploy-step:last-child .deploy-step-body {
  border-bottom: 0;
  padding-bottom: 0;
}

.deploy-step-header {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-width: 0;
}

.deploy-step-header div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.deploy-step-header span,
.deploy-step-body p,
.deploy-label,
.deploy-troubleshooting span,
.acceptance-item span {
  color: #667085;
}

.deploy-step-body p {
  margin: 0;
}

.deploy-command-list,
.deploy-troubleshooting,
.acceptance-list {
  display: grid;
  gap: 10px;
}

.deploy-command-list code,
.acceptance-item code {
  background: #101828;
  border-radius: 6px;
  color: #f8fafc;
  display: block;
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
  padding: 10px 12px;
  white-space: normal;
}

.deploy-check-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
}

.deploy-check-grid > div {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.deploy-label {
  font-size: 13px;
  font-weight: 700;
}

.deploy-url {
  color: #344054;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.deploy-troubleshooting span:not(.deploy-label) {
  font-size: 13px;
}

.acceptance-item {
  align-items: start;
  border-bottom: 1px solid #e4e7ec;
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  padding-bottom: 14px;
}

.acceptance-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.acceptance-item > div {
  display: grid;
  gap: 8px;
  min-width: 0;
}

@media (max-width: 640px) {
  .panel-title-actions {
    align-items: flex-start;
    flex-direction: column-reverse;
  }

  .topology-node-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .permission-summary,
  .account-toolbar,
  .account-grid {
    grid-template-columns: 1fr;
  }

  .topology-service {
    align-items: start;
    gap: 8px;
    grid-template-columns: minmax(0, 1fr) 52px 96px;
  }

  .topology-health,
  .topology-note {
    grid-column: 1 / -1;
  }

  .deploy-hero,
  .deploy-step-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .deploy-check-grid,
  .acceptance-item {
    grid-template-columns: 1fr;
  }

  .deploy-step {
    grid-template-columns: 30px minmax(0, 1fr);
  }

  .deploy-step-index {
    border-radius: 7px;
    height: 30px;
    width: 30px;
  }
}
</style>
