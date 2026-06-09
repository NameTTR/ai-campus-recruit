import { createRouter, createWebHistory } from 'vue-router'

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
  if (to.path !== '/login' && !localStorage.getItem('token')) {
    return '/login'
  }
  const role = localStorage.getItem('role')
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

function roleHome(role: string | null) {
  if (role === 'COMPANY') {
    return '/company/publish'
  }
  if (role === 'ADMIN') {
    return '/admin/overview'
  }
  return '/student/resume'
}

export default router
