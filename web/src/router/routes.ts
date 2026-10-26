import type { RouteRecordRaw } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import LoginView from '../views/LoginView.vue'
import ChangePasswordView from '../views/ChangePasswordView.vue'
import NotFoundView from '../views/NotFoundView.vue'

/**
 * 静态路由：登录、强制改密、主布局与 404。
 * 业务路由由 router/dynamic.ts 按权限动态注册到 root 下。
 */
export const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { title: '登录', public: true },
  },
  {
    path: '/change-password',
    name: 'change-password',
    component: ChangePasswordView,
    meta: { title: '修改密码' },
  },
  {
    path: '/',
    name: 'root',
    component: MainLayout,
    meta: { title: '首页' },
    children: [
      {
        path: '',
        redirect: '/dashboard',
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: { title: '页面不存在' },
  },
]

/**
 * 业务路由及其菜单权限码。
 * dashboard 不依赖 MENU 权限码，登录后始终可用。
 */
export const businessRoutes: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: '仪表盘' },
  },
  {
    path: 'system/users',
    name: 'system-users',
    component: () => import('../views/system/UserListView.vue'),
    meta: { title: '用户管理', menuCode: 'sys:user' },
  },
  {
    path: 'system/roles',
    name: 'system-roles',
    component: () => import('../views/system/RoleListView.vue'),
    meta: { title: '角色管理', menuCode: 'sys:role' },
  },
  {
    path: 'system/permissions',
    name: 'system-permissions',
    component: () => import('../views/system/PermissionListView.vue'),
    meta: { title: '权限管理', menuCode: 'sys:perm' },
  },
  {
    path: 'system/audit-logs',
    name: 'system-audit-logs',
    component: () => import('../views/system/AuditLogView.vue'),
    meta: { title: '审计日志', menuCode: 'sys:audit' },
  },
]
