<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Building2, GraduationCap, LogOut, ShieldCheck } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

const userName = computed(() => localStorage.getItem('displayName') || '')
const role = computed(() => localStorage.getItem('role') || '')
const authed = computed(() => Boolean(localStorage.getItem('token')) && route.path !== '/login')

const navItems = [
  { path: '/student', label: '学生端', icon: GraduationCap },
  { path: '/company', label: '企业端', icon: Building2 },
  { path: '/admin', label: '学校端', icon: ShieldCheck }
]

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

