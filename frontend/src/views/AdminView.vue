<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { BarChart3, BriefcaseBusiness, Building2, GraduationCap, Send } from 'lucide-vue-next'
import { getDashboard, type DashboardStats } from '../api/client'

const stats = ref<DashboardStats>()

onMounted(async () => {
  stats.value = await getDashboard()
})
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1 class="page-title">学校就业看板</h1>
        <p class="page-subtitle">就业办 · 2026 届校园招聘</p>
      </div>
    </header>

    <div class="grid three">
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
        <BarChart3 :size="22" />
        <span>平均匹配度</span>
        <strong>{{ stats?.averageMatchScore }}</strong>
      </div>
    </div>

    <section class="panel">
      <h2 class="panel-title">就业指导关注点</h2>
      <el-timeline>
        <el-timeline-item timestamp="简历质量">项目经历需要补充量化指标和部署信息</el-timeline-item>
        <el-timeline-item timestamp="岗位需求">Java 后端、测试开发、数据分析岗位热度较高</el-timeline-item>
        <el-timeline-item timestamp="辅导安排">建议组织 Spring Cloud、Redis、MySQL 面试专题</el-timeline-item>
      </el-timeline>
    </section>
  </section>
</template>

