<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import SidebarItem from './components/SidebarItem.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const perm = usePermissionStore()

const activeMenu = computed(() => route.path)
const breadcrumbs = computed(() =>
  route.matched.filter((m) => m.meta && typeof m.meta.title === 'string'),
)

async function onCommand(cmd: string) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    } catch {
      return
    }
    await auth.logout()
    perm.reset()
    router.push('/login')
  }
}
</script>

<template>
  <el-container class="app-wrapper">
    <el-aside width="220px" class="sidebar">
      <div class="logo">RBAC 权限系统</div>
      <el-scrollbar class="sidebar-scroll">
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#001529"
          text-color="#a6adb4"
          active-text-color="#ffffff"
        >
          <sidebar-item
            v-for="item in perm.menuItems"
            :key="item.code"
            :item="item"
          />
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="navbar">
        <div class="breadcrumb">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="m in breadcrumbs"
              :key="m.path"
            >
              {{ m.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="navbar-right">
          <el-dropdown @command="onCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              <span class="username">{{ auth.user?.nickname || auth.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-wrapper {
  height: 100%;
}

.sidebar {
  background-color: #001529;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 1px;
  background-color: #002140;
}

.sidebar-scroll {
  flex: 1;
}

.sidebar :deep(.el-menu) {
  border-right: none;
}

.navbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
}

.username {
  font-size: 14px;
}

.main {
  background-color: #f0f2f5;
  padding: 16px;
}
</style>
