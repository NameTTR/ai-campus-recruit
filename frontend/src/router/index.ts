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
  return true
})

export default router
