<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUpRight, Bot, BriefcaseBusiness, CalendarDays, ChevronRight, FileText, Library, LogOut, Menu, Route, Search, ShieldCheck, X } from 'lucide-vue-next'
import { clearAuthSession, getAuthSession } from './api/client'

const route = useRoute()
const router = useRouter()
const session = computed(() => { void route.fullPath; return getAuthSession() })
const userName = computed(() => session.value?.displayName || '')
const role = computed(() => session.value?.role || '')
const authed = computed(() => Boolean(session.value) && route.path !== '/login')
const section = computed(() => route.path.split('/')[1] || 'student')
const mobileNavOpen = ref(false)
const isMobile = ref(false)
const mobileNav = ref<HTMLElement>()
const pageLabel = ref<HTMLElement>()
const searchOpen = ref(false)
const searchQuery = ref('')
const searchInput = ref<HTMLInputElement>()
const searchDialog = ref<HTMLElement>()
let searchTrigger: HTMLElement | null = null
let navTrigger: HTMLElement | null = null
let mobileQuery: MediaQueryList | undefined
let bodyOverflowBeforeLock = ''
let bodyScrollLocked = false
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
function logout() { clearAuthSession(); router.push('/login') }
async function openSearch() {
  searchTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  searchOpen.value = true
  await nextTick()
  searchInput.value?.focus()
}
function closeSearch(restoreFocus = true) {
  searchOpen.value = false
  searchQuery.value = ''
  if (restoreFocus) nextTick(() => searchTrigger?.focus())
}
async function openMobileNav() {
  navTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  mobileNavOpen.value = true
  await nextTick()
  // The visibility transition must be painted before a hidden link can focus.
  await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))
  if (mobileNavOpen.value) mobileNav.value?.querySelector<HTMLElement>('.nav-link.active')?.focus()
}
function closeMobileNav(restoreFocus = true) {
  mobileNavOpen.value = false
  if (restoreFocus) nextTick(() => navTrigger?.focus())
}
function updateViewport() {
  isMobile.value = Boolean(mobileQuery?.matches)
  if (!isMobile.value) mobileNavOpen.value = false
}
function trapFocus(event: KeyboardEvent, container?: HTMLElement) {
  const controls = Array.from(container?.querySelectorAll<HTMLElement>('a[href], input, button:not([disabled])') || [])
    .filter(control => control.getClientRects().length > 0)
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (!first || !last) return
  if (event.shiftKey && (document.activeElement === first || !container?.contains(document.activeElement))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (document.activeElement === last || !container?.contains(document.activeElement))) {
    event.preventDefault()
    first.focus()
  }
}
function handleKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && authed.value) { event.preventDefault(); openSearch() }
  if (event.key === 'Escape') {
    if (searchOpen.value) closeSearch()
    else if (mobileNavOpen.value) closeMobileNav()
  }
  if (event.key === 'Tab') {
    if (searchOpen.value) trapFocus(event, searchDialog.value)
    else if (isMobile.value && mobileNavOpen.value) trapFocus(event, mobileNav.value)
  }
}
function visitSearchResult(path?: string) {
  if (!path) return
  closeSearch(false)
  closeMobileNav(false)
  router.push(path)
  nextTick(() => pageLabel.value?.focus())
}
watch(() => route.fullPath, () => {
  const overlayWasOpen = mobileNavOpen.value || searchOpen.value
  closeMobileNav(false)
  closeSearch(false)
  if (overlayWasOpen) nextTick(() => pageLabel.value?.focus())
})
watch([mobileNavOpen, searchOpen], ([navOpen, dialogOpen]) => {
  if (navOpen || dialogOpen) {
    if (!bodyScrollLocked) {
      bodyOverflowBeforeLock = document.body.style.overflow
      bodyScrollLocked = true
      document.body.style.overflow = 'hidden'
    }
  } else if (bodyScrollLocked) {
    document.body.style.overflow = bodyOverflowBeforeLock
    bodyScrollLocked = false
  }
})
onMounted(() => {
  mobileQuery = window.matchMedia('(max-width: 900px)')
  updateViewport()
  mobileQuery.addEventListener('change', updateViewport)
  window.addEventListener('keydown', handleKeydown)
})
onUnmounted(() => {
  mobileQuery?.removeEventListener('change', updateViewport)
  window.removeEventListener('keydown', handleKeydown)
  if (bodyScrollLocked) document.body.style.overflow = bodyOverflowBeforeLock
})
</script>

<template>
  <div class="app-shell" :class="{ 'is-authenticated': authed }" :data-section="section">
    <button v-if="authed && mobileNavOpen" class="nav-backdrop" aria-label="关闭导航" tabindex="-1" @click="closeMobileNav()" />
    <aside v-if="authed" id="workspace-navigation" ref="mobileNav" class="side-nav" :class="{ 'is-open': mobileNavOpen }" :inert="searchOpen || (isMobile && !mobileNavOpen)" :role="isMobile ? 'dialog' : undefined" :aria-modal="isMobile && mobileNavOpen ? true : undefined" aria-label="工作台导航">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true"><Route :size="21" :stroke-width="1.9" /></div>
        <div class="brand-copy"><strong>Campus Recruit</strong></div>
        <button class="icon-button mobile-close" title="关闭导航" aria-label="关闭导航" @click="closeMobileNav()"><X :size="18" /></button>
      </div>
      <div class="workspace-picker"><span class="workspace-avatar">{{ userInitial }}</span><div><strong>{{ roleLabel }}</strong><span>{{ userName }}</span></div><ShieldCheck :size="16" /></div>
      <nav aria-label="主要功能">
        <span class="nav-section-title">{{ navGroup.title }}</span>
        <RouterLink v-for="(item, index) in navItems" :key="item.path" :to="item.path" class="nav-link" :class="{ active: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined">
          <span class="nav-step" aria-hidden="true"><span class="nav-node" />{{ String(index + 1).padStart(2, '0') }}</span><component :is="item.icon" :size="17" :stroke-width="1.7" /><span class="nav-label">{{ item.label }}</span><ChevronRight v-if="route.path === item.path" class="nav-active-arrow" :size="14" aria-hidden="true" />
        </RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <button class="ghost-button logout-button" type="button" title="退出登录" @click="logout"><LogOut :size="17" /><span>退出登录</span></button>
      </div>
    </aside>
    <main class="main-view" :class="{ centered: !authed }" :inert="searchOpen || (isMobile && mobileNavOpen)">
      <header v-if="authed" class="workspace-topbar">
        <div class="topbar-location"><button class="icon-button mobile-menu" title="打开导航" aria-label="打开导航" aria-controls="workspace-navigation" :aria-expanded="mobileNavOpen" @click="openMobileNav"><Menu :size="20" /></button><span>{{ roleLabel }}</span><ChevronRight :size="13" /><strong ref="pageLabel" tabindex="-1">{{ currentPage }}</strong></div>
        <div class="topbar-tools"><span class="topbar-date"><CalendarDays :size="14" />{{ dateLabel }}</span><button class="icon-button global-search" title="搜索功能" aria-label="搜索功能" @click="openSearch"><Search :size="18" /></button><span class="user-avatar" :title="userName">{{ userInitial }}</span></div>
      </header>
      <div :class="authed ? 'workspace-content' : 'login-container'"><RouterView /></div>
    </main>
    <div v-if="searchOpen" class="search-overlay" @click.self="closeSearch()">
      <section ref="searchDialog" class="command-search" role="dialog" aria-modal="true" aria-label="搜索功能">
        <div class="command-search-input"><Search :size="20" /><input ref="searchInput" v-model="searchQuery" placeholder="搜索功能" aria-label="搜索功能名称" @keydown.enter="visitSearchResult(filteredNavItems[0]?.path)" /><button class="icon-button" title="关闭搜索" aria-label="关闭搜索" @click="closeSearch()"><X :size="18" /></button></div>
        <span class="command-label">快速前往</span>
        <button v-for="item in filteredNavItems" :key="item.path" class="command-result" @click="visitSearchResult(item.path)"><component :is="item.icon" :size="18" /><span>{{ item.label }}</span><ArrowUpRight :size="16" /></button>
        <p v-if="!filteredNavItems.length" class="command-empty">没有找到相关功能，请试试其他关键词。</p>
      </section>
    </div>
  </div>
</template>
