import { createRouter, createWebHistory } from 'vue-router'
import { storage, tokenUtils } from '@/utils'

// 路由组件（懒加载）
const Login = () => import('@/views/Login.vue')
const Register = () => import('@/views/Register.vue')
const Dashboard = () => import('@/views/Dashboard.vue')

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: {
      title: '登录',
      requiresAuth: false
    }
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: {
      title: '注册',
      requiresAuth: false
    }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: Dashboard,
    meta: {
      title: '仪表盘',
      requiresAuth: true
    }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

// 路由守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - BrilliantTavern`
  }

  // 检查是否需要认证
  const requiresAuth = to.meta.requiresAuth
  const hasToken = tokenUtils.hasToken()

  if (requiresAuth && !hasToken) {
    // 需要认证但没有token，跳转到登录页
    next('/login')
  } else if (requiresAuth && hasToken) {
    // 需要认证且有token，先检查token是否过期
    if (tokenUtils.isTokenExpired()) {
      console.log('Token已过期，清除认证信息并跳转到登录页')
      tokenUtils.clearAuth()
      next('/login')
    } else {
      next()
    }
  } else if (!requiresAuth && hasToken && (to.path === '/login' || to.path === '/register')) {
    // 已有token访问登录/注册页，只有在token未过期时才跳转到仪表盘
    if (!tokenUtils.isTokenExpired()) {
      next('/dashboard')
    } else {
      // token已过期，清除并允许访问登录页
      tokenUtils.clearAuth()
      next()
    }
  } else {
    next()
  }
})

export default router
