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
    { path: '/student/:module', component: StudentView },
    { path: '/company', redirect: '/company/publish' },
    { path: '/company/:module', component: CompanyView },
    { path: '/admin', redirect: '/admin/overview' },
    { path: '/admin/:module', component: AdminView }
  ]
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
    return '/company/publish'
  }
  if (role === 'ADMIN') {
    return '/admin/overview'
  }
  return '/student/resume'
}

export default router
