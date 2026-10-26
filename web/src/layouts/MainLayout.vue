<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Avatar,
  Document,
  Expand,
  Fold,
  Key,
  Odometer,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { resetBusinessRoutes } from '../router/dynamic'

interface MenuItem {
  path: string
  title: string
  icon: Component
  code?: string
}

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const menus: MenuItem[] = [
  { path: '/dashboard', title: '仪表盘', icon: Odometer },
  { path: '/system/users', title: '用户管理', icon: User, code: 'sys:user' },
  { path: '/system/roles', title: '角色管理', icon: Avatar, code: 'sys:role' },
  { path: '/system/permissions', title: '权限管理', icon: Key, code: 'sys:perm' },
  { path: '/system/audit-logs', title: '审计日志', icon: Document, code: 'sys:audit' },
]

const collapsed = ref(window.innerWidth < 992)
const visibleMenus = computed(() =>
  menus.filter((menu) => !menu.code || auth.hasPermission(menu.code)),
)
const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title ?? '')
const avatarText = computed(() => auth.displayName.slice(0, 1).toUpperCase())
const roleTags = computed(() => auth.roles.slice(0, 3))

function toggleCollapse(): void {
  collapsed.value = !collapsed.value
}

function handleResize(): void {
  if (window.innerWidth < 768) {
    collapsed.value = true
  }
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '退出后当前账号的全部登录会话将失效，确定退出吗？',
      '退出登录',
      {
        type: 'warning',
        confirmButtonText: '退出',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  await auth.logout()
  resetBusinessRoutes()
  await router.replace('/login')
  ElMessage.success('已退出登录')
}

function handleCommand(command: string | number | object): void {
  if (command === 'password') {
    void router.push('/change-password')
    return
  }
  if (command === 'logout') {
    void handleLogout()
  }
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside :width="collapsed ? '64px' : '220px'" class="app-aside">
      <div class="app-brand" :class="{ 'is-collapsed': collapsed }">
        <div class="brand-mark">R</div>
        <div v-if="!collapsed" class="app-brand__text">
          <strong>RBAC 管理台</strong>
          <span>权限管理系统</span>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        class="app-menu"
        router
      >
        <el-menu-item v-for="menu in visibleMenus" :key="menu.path" :index="menu.path">
          <el-icon><component :is="menu.icon" /></el-icon>
          <template #title>{{ menu.title }}</template>
        </el-menu-item>
      </el-menu>

      <div v-if="!collapsed" class="app-aside__footer">RBAC Admin v0.1</div>
    </el-aside>

    <el-container class="app-body">
      <el-header class="app-header">
        <div class="app-header__left">
          <el-button
            text
            :icon="collapsed ? Expand : Fold"
            :title="collapsed ? '展开菜单' : '收起菜单'"
            @click="toggleCollapse"
          />
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="pageTitle && pageTitle !== '仪表盘'">
              {{ pageTitle }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="app-header__right">
          <div class="header-roles">
            <el-tag
              v-for="role in roleTags"
              :key="role.id"
              size="small"
              type="info"
              effect="plain"
            >
              {{ role.name }}
            </el-tag>
          </div>

          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-trigger">
              <el-avatar :size="30" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ auth.displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>
