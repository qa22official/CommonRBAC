import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import type { Component } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore, type MenuItem } from '@/stores/permission'
import Layout from '@/layout/index.vue'
import LoginView from '@/views/login/index.vue'
import DashboardView from '@/views/dashboard/index.vue'
import ProfileView from '@/views/profile/index.vue'
import UserView from '@/views/system/user/index.vue'
import RoleView from '@/views/system/role/index.vue'
import PermissionView from '@/views/system/permission/index.vue'
import AuditView from '@/views/audit/index.vue'

/**
 * 路径 -> 页面组件注册表。
 * 与后端 sys_permission 表里 MENU 节点的 path 保持一致，
 * 后端菜单 path 必须落在下面这个映射中。
 */
const componentMap: Record<string, Component> = {
  '/dashboard': DashboardView,
  '/system/users': UserView,
  '/system/roles': RoleView,
  '/system/permissions': PermissionView,
  '/system/audit': AuditView,
  '/profile': ProfileView,
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { public: true },
  },
  {
    path: '/',
    name: 'Layout',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: DashboardView,
        meta: { title: '首页' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: ProfileView,
        meta: { title: '个人中心' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/** 根据菜单树递归注册动态路由（幂等） */
function registerRoutes(menuItems: MenuItem[]) {
  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      if (item.path && componentMap[item.path]) {
        const name =
          item.path.replace(/^\//, '').replace(/\//g, '-') || 'dynamic-page'
        if (!router.hasRoute(name)) {
          router.addRoute('Layout', {
            path: item.path,
            name,
            component: componentMap[item.path],
            meta: { title: item.title, perm: item.code },
          })
        }
      }
      walk(item.children || [])
    }
  }
  walk(menuItems)
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const isPublic = to.meta.public === true

  if (isPublic) {
    if (auth.isLoggedIn && to.path === '/login') {
      return { path: '/' }
    }
    return true
  }

  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录但用户信息 / 动态路由尚未加载：先加载再重试导航
  if (!auth.loaded) {
    try {
      await auth.fetchMe()
      const perm = usePermissionStore()
      await perm.loadMenus()
      registerRoutes(perm.menuItems)
      return { ...to, replace: true }
    } catch {
      auth.reset()
      return { path: '/login' }
    }
  }

  return true
})

export default router
