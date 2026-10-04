<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUpRight, Bot, BriefcaseBusiness, CalendarDays, ChevronRight, FileText, Library, LogOut, Menu, Route, Search, ShieldCheck, Sparkles, X } from 'lucide-vue-next'
import { clearAuthSession, getAuthSession } from './api/client'

const route = useRoute()
const router = useRouter()
const session = computed(() => { void route.fullPath; return getAuthSession() })
const userName = computed(() => session.value?.displayName || '')
const role = computed(() => session.value?.role || '')
const authed = computed(() => Boolean(session.value) && route.path !== '/login')
const section = computed(() => route.path.split('/')[1] || 'student')
const mobileNavOpen = ref(false)
const searchOpen = ref(false)
const searchQuery = ref('')
const searchInput = ref<HTMLInputElement>()
const searchDialog = ref<HTMLElement>()
let searchTrigger: HTMLElement | null = null
const dateLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date())
const roleLabel = computed(() => role.value === 'COMPANY' ? '企业空间' : role.value === 'ADMIN' ? '管理空间' : '学生空间')
const userInitial = computed(() => Array.from(userName.value || 'C')[0])
const navGroups = {
  student: { title: '求职工作台', items: [
    { path: '/student/resume', label: '简历', icon: FileText },
    { path: '/student/jobs', label: '岗位匹配', icon: BriefcaseBusiness },
    { path: '/student/plan', label: '学习路径', icon: Route },
    { path: '/student/interview', label: '模拟面试', icon: Bot },
    { path: '/student/knowledge', label: '知识库', icon: Library }
  ] },
  company: { title: '招聘工作台', items: [
    { path: '/company/jobs', label: '岗位管理', icon: BriefcaseBusiness },
    { path: '/company/publish', label: '发布岗位', icon: FileText }
  ] },
  admin: { title: '管理工作台', items: [
    { path: '/admin/accounts', label: '账号管理', icon: ShieldCheck },
    { path: '/admin/ai', label: '知识库管理', icon: Library }
  ] }
} as const
const navGroup = computed(() => navGroups[section.value as keyof typeof navGroups] || navGroups.student)
const navItems = computed(() => navGroup.value.items)
const currentPage = computed(() => navItems.value.find(item => item.path === route.path)?.label || navGroup.value.title)
const filteredNavItems = computed(() => navItems.value.filter(item => item.label.includes(searchQuery.value.trim())))
const workspaceTip = computed(() => section.value === 'company'
  ? { label: '让机会遇见合适的人', description: '清晰的岗位要求，是理想匹配的开始。', action: '发布一个岗位', path: '/company/publish' }
  : section.value === 'admin'
    ? { label: '让知识成为助力', description: '管理可信资料，为每一次提问提供依据。', action: '管理知识库', path: '/admin/ai' }
    : { label: '下一步，更有方向', description: '把能力差距变成一项项可完成的学习任务。', action: '规划学习路径', path: '/student/plan' })

function logout() { clearAuthSession(); router.push('/login') }
async function openSearch() {
  searchTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  searchOpen.value = true
  await nextTick()
  searchInput.value?.focus()
}
function closeSearch() {
  searchOpen.value = false
  searchQuery.value = ''
  nextTick(() => searchTrigger?.focus())
}
function handleKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && authed.value) { event.preventDefault(); openSearch() }
  if (event.key === 'Escape') { if (searchOpen.value) closeSearch(); mobileNavOpen.value = false }
  if (event.key === 'Tab' && searchOpen.value) {
    const controls = searchDialog.value?.querySelectorAll<HTMLElement>('input, button')
    if (!controls?.length) return
    const first = controls[0]
    const last = controls[controls.length - 1]
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
  }
}
function visitSearchResult(path?: string) { if (path) { router.push(path); closeSearch() } }
watch(() => route.fullPath, () => { mobileNavOpen.value = false; searchOpen.value = false })
onMounted(() => window.addEventListener('keydown', handleKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <div class="app-shell" :class="{ 'is-authenticated': authed }">
    <button v-if="authed && mobileNavOpen" class="nav-backdrop" aria-label="关闭导航" @click="mobileNavOpen = false" />
    <aside v-if="authed" class="side-nav" :class="{ 'is-open': mobileNavOpen }" :inert="searchOpen">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true"><Sparkles :size="23" :stroke-width="1.8" /></div>
        <div class="brand-copy"><strong>Campus Recruit</strong><span>每一步，向理想靠近</span></div>
        <button class="icon-button mobile-close" aria-label="关闭导航" @click="mobileNavOpen = false"><X :size="18" /></button>
      </div>
      <div class="workspace-picker"><span class="workspace-avatar">{{ userInitial }}</span><div><strong>{{ roleLabel }}</strong><span>{{ userName }}</span></div><ShieldCheck :size="16" /></div>
      <nav aria-label="主要功能">
        <span class="nav-section-title">{{ navGroup.title }}</span>
        <RouterLink v-for="item in navItems" :key="item.path" :to="item.path" class="nav-link" :class="{ active: route.path === item.path }">
          <component :is="item.icon" :size="18" :stroke-width="1.7" /><span>{{ item.label }}</span><span v-if="route.path === item.path" class="nav-active-dot" />
        </RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <div class="workspace-tip"><div class="tip-icon"><Sparkles :size="20" /></div><strong>{{ workspaceTip.label }}</strong><p>{{ workspaceTip.description }}</p><RouterLink :to="workspaceTip.path">{{ workspaceTip.action }}<ArrowUpRight :size="16" /></RouterLink></div>
        <button class="ghost-button logout-button" type="button" @click="logout"><LogOut :size="17" /><span>退出登录</span></button>
        <div class="sidebar-footnote">AI Campus Recruitment <span>✦</span></div>
      </div>
    </aside>
    <main class="main-view" :class="{ centered: !authed }" :inert="searchOpen">
      <header v-if="authed" class="workspace-topbar">
        <div class="topbar-location"><button class="icon-button mobile-menu" aria-label="打开导航" :aria-expanded="mobileNavOpen" @click="mobileNavOpen = !mobileNavOpen"><Menu :size="20" /></button><span>{{ roleLabel }}</span><ChevronRight :size="14" /><strong>{{ currentPage }}</strong></div>
        <div class="topbar-tools"><button class="global-search" aria-label="搜索功能" @click="openSearch"><Search :size="16" /><span>搜索功能</span><kbd>Ctrl K</kbd></button><span class="topbar-date"><CalendarDays :size="15" />{{ dateLabel }}</span><span class="user-avatar" :title="userName">{{ userInitial }}</span></div>
      </header>
      <div :class="authed ? 'workspace-content' : 'login-container'"><RouterView /></div>
    </main>
    <div v-if="searchOpen" class="search-overlay" @click.self="closeSearch">
      <section ref="searchDialog" class="command-search" role="dialog" aria-modal="true" aria-label="搜索功能">
        <div class="command-search-input"><Search :size="20" /><input ref="searchInput" v-model="searchQuery" placeholder="输入功能名称，例如学习路径" aria-label="搜索功能名称" @keydown.enter="visitSearchResult(filteredNavItems[0]?.path)" /><button class="icon-button" aria-label="关闭搜索" @click="closeSearch"><X :size="18" /></button></div>
        <span class="command-label">快速前往</span>
        <button v-for="item in filteredNavItems" :key="item.path" class="command-result" @click="visitSearchResult(item.path)"><component :is="item.icon" :size="18" /><span>{{ item.label }}</span><ArrowUpRight :size="16" /></button>
        <p v-if="!filteredNavItems.length" class="command-empty">没有找到相关功能，请试试其他关键词。</p>
      </section>
    </div>
  </div>
</template>
