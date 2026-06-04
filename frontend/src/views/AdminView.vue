<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { BarChart3, BriefcaseBusiness, Building2, GraduationCap, Send, Timer } from 'lucide-vue-next'
import { getDashboard, type DashboardStats, type DeliveryStatus } from '../api/client'

const route = useRoute()
const stats = ref<DashboardStats>()
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

onMounted(async () => {
  stats.value = await getDashboard()
})

function statusPercent(count: number) {
  if (!stats.value?.deliveryCount) {
    return 0
  }
  return Math.round((count / stats.value.deliveryCount) * 100)
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
