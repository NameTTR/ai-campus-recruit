<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bot,
  BriefcaseBusiness,
  FileText,
  Library,
  LogOut,
  Route,
  ShieldCheck
} from 'lucide-vue-next'
import { clearAuthSession, getAuthSession } from './api/client'

const route = useRoute()
const router = useRouter()

const session = computed(() => {
  void route.fullPath
  return getAuthSession()
})
const userName = computed(() => session.value?.displayName || '')
const role = computed(() => session.value?.role || '')
const userId = computed(() => session.value?.userId || '')
const authed = computed(() => Boolean(session.value) && route.path !== '/login')
const section = computed(() => route.path.split('/')[1] || 'student')

const navGroups = {
  student: {
    title: '学生工作台',
    items: [
      { path: '/student/resume', label: '简历', icon: FileText },
      { path: '/student/jobs', label: '岗位匹配', icon: BriefcaseBusiness },
      { path: '/student/plan', label: '学习路径', icon: Route },
      { path: '/student/interview', label: '模拟面试', icon: Bot },
      { path: '/student/knowledge', label: '知识库', icon: Library }
    ]
  },
  company: {
    title: '企业工作台',
    items: [
      { path: '/company/jobs', label: '岗位管理', icon: BriefcaseBusiness },
      { path: '/company/publish', label: '发布岗位', icon: FileText }
    ]
  },
  admin: {
    title: '就业管理',
    items: [
      { path: '/admin/accounts', label: '账号管理', icon: ShieldCheck },
      { path: '/admin/ai', label: '知识库管理', icon: Library }
    ]
  }
} as const

const navGroup = computed(() => navGroups[section.value as keyof typeof navGroups] || navGroups.student)
const navItems = computed(() => navGroup.value.items)

function logout() {
  clearAuthSession()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside v-if="authed" class="side-nav">
      <div class="brand">
        <div class="brand-mark">AI</div>
        <div class="brand-copy">
          <strong>Campus Recruit</strong>
          <span>{{ userName }} · {{ userId }} · {{ role }}</span>
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

<style scoped>
.brand-copy{min-width:0}.brand-copy span{overflow-wrap:anywhere;word-break:break-word}
</style>
