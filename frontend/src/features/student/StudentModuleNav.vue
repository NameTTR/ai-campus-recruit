<script setup lang="ts">
import { computed, type Component } from 'vue'
import { RouterLink } from 'vue-router'
import { BookOpen, CalendarDays, FileText, History, Library, List, MessageCircle, Play, Search, UserRound } from 'lucide-vue-next'

type StudentModule = 'resume' | 'plan' | 'interview' | 'knowledge'
interface NavigationItem {
  key: string
  label: string
  icon: Component
  path: string
  pages: string[]
}

const props = defineProps<{
  module: StudentModule
  page: string
  context?: Record<string, string>
}>()

const navigation: Record<StudentModule, { label: string; items: NavigationItem[] }> = {
  resume: {
    label: '简历导航',
    items: [
      { key: 'editor', label: '我的简历', icon: FileText, path: '/student/resume', pages: ['editor', 'templates', 'edit', 'diagnosis', 'versions'] },
      { key: 'profile', label: '主资料', icon: UserRound, path: '/student/resume/profile', pages: ['profile'] },
      { key: 'history', label: '上传记录', icon: History, path: '/student/resume/history', pages: ['history', 'original', 'originalDiagnosis'] }
    ]
  },
  plan: {
    label: '学习路径导航',
    items: [
      { key: 'today', label: '今日', icon: CalendarDays, path: '/student/plan', pages: ['today'] },
      { key: 'tasks', label: '完整计划', icon: List, path: '/student/plan/tasks', pages: ['tasks', 'task'] },
      { key: 'review', label: '复盘调整', icon: BookOpen, path: '/student/plan/review', pages: ['review'] },
      { key: 'history', label: '历史', icon: History, path: '/student/plan/history', pages: ['history'] }
    ]
  },
  interview: {
    label: '模拟面试导航',
    items: [
      { key: 'start', label: '开始', icon: Play, path: '/student/interview', pages: ['start'] },
      { key: 'practice', label: '答题', icon: MessageCircle, path: '/student/interview/practice', pages: ['practice', 'report'] },
      { key: 'history', label: '历史', icon: History, path: '/student/interview/history', pages: ['history'] }
    ]
  },
  knowledge: {
    label: '知识库导航',
    items: [
      { key: 'search', label: '查知识', icon: Search, path: '/student/knowledge', pages: ['search', 'answer', 'topics', 'topic', 'reader', 'sources'] },
      { key: 'learning', label: '我的学习', icon: BookOpen, path: '/student/knowledge/learning', pages: ['learning', 'practice'] },
      { key: 'history', label: '查询历史', icon: History, path: '/student/knowledge/history', pages: ['history'] }
    ]
  }
}

const currentNavigation = computed(() => navigation[props.module])
</script>

<template>
  <nav class="student-module-nav" :aria-label="currentNavigation.label" :data-testid="`${module}-subnav`">
    <RouterLink
      v-for="item in currentNavigation.items"
      :key="item.key"
      :to="{ path: item.path, query: context }"
      :class="{ active: item.pages.includes(page) }"
      :aria-current="item.pages.includes(page) ? 'page' : undefined"
      :data-testid="`${module}-nav-${item.key}`"
    >
      <component :is="item.icon" :size="16" aria-hidden="true" />
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.student-module-nav {
  display: flex;
  min-width: 0;
  gap: 4px;
  border-bottom: 1px solid var(--line, #e8ebea);
}

.student-module-nav a {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 12px 16px;
  border-bottom: 2px solid transparent;
  color: var(--muted, #66716c);
  font-size: 13px;
  font-weight: 650;
  line-height: 20px;
  text-decoration: none;
  white-space: nowrap;
}

.student-module-nav a:hover {
  color: var(--accent, #28664f);
  background: #f4faf6;
}

.student-module-nav a.active {
  border-bottom-color: var(--accent, #28664f);
  color: var(--accent, #28664f);
}

.student-module-nav a:focus-visible {
  outline: 2px solid var(--accent, #28664f);
  outline-offset: -2px;
}

.student-module-nav svg {
  flex: none;
}

@media (max-width: 640px) {
  .student-module-nav {
    gap: 0;
  }

  .student-module-nav a {
    flex: 1 1 0;
    gap: 4px;
    padding: 10px 2px;
    font-size: 12px;
  }

  .student-module-nav svg {
    width: 14px;
    height: 14px;
  }
}
</style>
