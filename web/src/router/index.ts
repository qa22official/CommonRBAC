import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import {
  installBusinessRoutes,
  areBusinessRoutesInstalled,
  resetBusinessRoutes,
  setRouter,
} from './dynamic'
import { staticRoutes } from './routes'

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
  scrollBehavior: () => ({ top: 0 }),
})

setRouter(router)

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    if (to.meta.public === true) {
      return true
    }
    return {
      path: '/login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }

  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      // 401 已由拦截器统一清理并跳转。网络/5xx 时令牌仍在，若当前目标就是
      // 公开登录页必须放行，否则会再次进入守卫、再次请求 /me，并以不断增长的
      // redirect 参数自我重定向。
      if (to.meta.public === true) {
        return true
      }
      return {
        path: '/login',
        query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
      }
    }
  }

  if (auth.mustChangePassword) {
    // 强制改密期间不注册任何业务路由。
    resetBusinessRoutes()
    if (to.name === 'change-password') {
      return true
    }
    return { path: '/change-password' }
  }

  if (to.name === 'change-password') {
    return true
  }

  if (to.name === 'login') {
    return { path: '/dashboard' }
  }

  if (!areBusinessRoutesInstalled()) {
    installBusinessRoutes(auth.permissions)
    return { path: to.fullPath, replace: true }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} · RBAC 管理系统` : 'RBAC 管理系统'
})

export default router
