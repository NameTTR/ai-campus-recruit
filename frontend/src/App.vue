<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BarChart3,
  Bot,
  BriefcaseBusiness,
  ClipboardList,
  Clock3,
  FileText,
  GraduationCap,
  LogOut,
  Plus,
  Send
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

const userName = computed(() => localStorage.getItem('displayName') || '')
const role = computed(() => localStorage.getItem('role') || '')
const authed = computed(() => Boolean(localStorage.getItem('token')) && route.path !== '/login')
const section = computed(() => route.path.split('/')[1] || 'student')

const navGroups = {
  student: {
    title: '学生端模块',
    items: [
      { path: '/student/resume', label: '简历诊断', icon: FileText },
      { path: '/student/jobs', label: '岗位匹配', icon: BriefcaseBusiness },
      { path: '/student/interview', label: 'AI 模拟面试', icon: Bot },
      { path: '/student/history', label: '面试记录', icon: Clock3 },
      { path: '/student/deliveries', label: '投递记录', icon: Send }
    ]
  },
  company: {
    title: '企业端模块',
    items: [
      { path: '/company/publish', label: '发布岗位', icon: Plus },
      { path: '/company/jobs', label: '岗位管理', icon: BriefcaseBusiness },
      { path: '/company/deliveries', label: '投递审核', icon: ClipboardList },
      { path: '/company/screening', label: 'AI 筛选历史', icon: Bot }
    ]
  },
  admin: {
    title: '学校端模块',
    items: [
      { path: '/admin/overview', label: '数据概览', icon: GraduationCap },
      { path: '/admin/status', label: '投递状态', icon: BarChart3 },
      { path: '/admin/guidance', label: '就业指导', icon: ClipboardList }
    ]
  }
} as const

const navGroup = computed(() => navGroups[section.value as keyof typeof navGroups] || navGroups.student)
const navItems = computed(() => navGroup.value.items)

function logout() {
  localStorage.clear()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside v-if="authed" class="side-nav">
      <div class="brand">
        <div class="brand-mark">AI</div>
        <div>
          <strong>Campus Recruit</strong>
          <span>{{ userName }} · {{ role }}</span>
        </div>
      </div>

      <nav>
        <span class="nav-section-title">{{ navGroup.title }}</span>
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ active: route.path === item.path }"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <button class="ghost-button" type="button" @click="logout">
        <LogOut :size="18" />
        <span>退出</span>
      </button>
    </aside>

    <main class="main-view" :class="{ centered: !authed }">
      <RouterView />
    </main>
  </div>
</template>
