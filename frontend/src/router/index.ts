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
    { path: '/student/history', redirect: to => ({ path: '/student/interview/history', query: to.query }) },
    { path: '/student/:module(jobs)/compare', component: StudentView, meta: { jobsPage: 'compare' } },
    { path: '/student/:module(jobs)/history', component: StudentView, meta: { jobsPage: 'history' } },
    { path: '/student/:module(jobs)/:jobId/match', component: StudentView, meta: { jobsPage: 'match' } },
    { path: '/student/:module(jobs)/:jobId', component: StudentView, meta: { jobsPage: 'detail' } },
    { path: '/student/:module(jobs)', component: StudentView, meta: { jobsPage: 'list' } },
    { path: '/student/:module(resume)/profile', component: StudentView, meta: { resumePage: 'profile' } },
    { path: '/student/:module(resume)/templates', component: StudentView, meta: { resumePage: 'templates' } },
    { path: '/student/:module(resume)/edit', component: StudentView, meta: { resumePage: 'edit' } },
    { path: '/student/:module(resume)/diagnosis', component: StudentView, meta: { resumePage: 'diagnosis' } },
    { path: '/student/:module(resume)/versions', component: StudentView, meta: { resumePage: 'versions' } },
    { path: '/student/:module(resume)/history', component: StudentView, meta: { resumePage: 'history' } },
    { path: '/student/:module(resume)/original/:resumeId/diagnosis', component: StudentView, meta: { resumePage: 'originalDiagnosis' } },
    { path: '/student/:module(resume)/original/:resumeId', component: StudentView, meta: { resumePage: 'original' } },
    { path: '/student/:module(resume)', component: StudentView, meta: { resumePage: 'editor' } },
    { path: '/student/:module(plan)/tasks/:taskId', component: StudentView, meta: { planPage: 'task' } },
    { path: '/student/:module(plan)/tasks', component: StudentView, meta: { planPage: 'tasks' } },
    { path: '/student/:module(plan)/create', component: StudentView, meta: { planPage: 'create' } },
    { path: '/student/:module(plan)/review', component: StudentView, meta: { planPage: 'review' } },
    { path: '/student/:module(plan)/history', component: StudentView, meta: { planPage: 'history' } },
    { path: '/student/:module(plan)', component: StudentView, meta: { planPage: 'today' } },
    { path: '/student/:module(interview)/practice', component: StudentView, meta: { interviewPage: 'practice' } },
    { path: '/student/:module(interview)/report', component: StudentView, meta: { interviewPage: 'report' } },
    { path: '/student/:module(interview)/history', component: StudentView, meta: { interviewPage: 'history' } },
    { path: '/student/:module(interview)', component: StudentView, meta: { interviewPage: 'start' } },
    { path: '/student/:module(knowledge)/answer', component: StudentView, meta: { knowledgePage: 'answer' } },
    { path: '/student/:module(knowledge)/sources', component: StudentView, meta: { knowledgePage: 'sources' } },
    { path: '/student/:module(knowledge)/history', component: StudentView, meta: { knowledgePage: 'history' } },
    { path: '/student/:module(knowledge)', component: StudentView, meta: { knowledgePage: 'search' } },
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
    if (to.path.startsWith('/student/') || from.path.startsWith('/student/')) return savedPosition || { top: 0 }
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
  if (to.path === '/student/interview' && to.query.tab === 'history') {
    const { tab: _tab, ...query } = to.query
    return { path: '/student/interview/history', query, replace: true }
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
