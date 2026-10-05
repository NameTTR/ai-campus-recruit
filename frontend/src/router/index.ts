import { createRouter, createWebHistory } from 'vue-router'
import { getAuthSession, type Role } from '../api/client'

const LoginView = () => import('../views/LoginView.vue')
const StudentView = () => import('../views/StudentView.vue')
const CompanyView = () => import('../views/CompanyView.vue')
const AdminView = () => import('../views/AdminView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginView },
    { path: '/student', redirect: '/student/resume' },
    { path: '/student/history', redirect: { path: '/student/interview', query: { tab: 'history' } } },
    { path: '/student/:module(jobs)/compare', component: StudentView, meta: { jobsPage: 'compare' } },
    { path: '/student/:module(jobs)/history', component: StudentView, meta: { jobsPage: 'history' } },
    { path: '/student/:module(jobs)/:jobId/match', component: StudentView, meta: { jobsPage: 'match' } },
    { path: '/student/:module(jobs)/:jobId', component: StudentView, meta: { jobsPage: 'detail' } },
    { path: '/student/:module(jobs)', component: StudentView, meta: { jobsPage: 'list' } },
    { path: '/student/:module(resume|plan|interview|knowledge)', component: StudentView },
    { path: '/student/:pathMatch(.*)*', redirect: '/student/resume' },
    { path: '/company', redirect: '/company/jobs' },
    { path: '/company/:module(publish|jobs)', component: CompanyView },
    { path: '/company/:pathMatch(.*)*', redirect: '/company/jobs' },
    { path: '/admin', redirect: { path: '/admin/ai', query: { tab: 'documents' } } },
    { path: '/admin/:module(accounts|ai)', component: AdminView },
    { path: '/admin/:pathMatch(.*)*', redirect: { path: '/admin/ai', query: { tab: 'documents' } } },
    { path: '/:pathMatch(.*)*', redirect: '/login' }
  ],
  scrollBehavior(to, from, savedPosition) {
    if (to.meta.jobsPage || from.meta.jobsPage) return savedPosition || { top: 0 }
  }
})

router.beforeEach((to) => {
  const session = getAuthSession()
  if (to.path !== '/login' && !session) {
    return '/login'
  }
  const role = session?.role
  if (to.path.startsWith('/student') && role !== 'STUDENT') {
    return roleHome(role)
  }
  if (to.path.startsWith('/company') && role !== 'COMPANY') {
    return roleHome(role)
  }
  if (to.path.startsWith('/admin') && role !== 'ADMIN') {
    return roleHome(role)
  }
  return true
})

function roleHome(role: Role | undefined) {
  if (role === 'COMPANY') {
    return '/company/jobs'
  }
  if (role === 'ADMIN') {
    return { path: '/admin/ai', query: { tab: 'documents' } }
  }
  return '/student/resume'
}

export default router
