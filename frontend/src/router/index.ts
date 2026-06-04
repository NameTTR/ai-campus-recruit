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
    { path: '/student', component: StudentView },
    { path: '/company', component: CompanyView },
    { path: '/admin', component: AdminView }
  ]
})

router.beforeEach((to) => {
  if (to.path !== '/login' && !localStorage.getItem('token')) {
    return '/login'
  }
  return true
})

export default router
