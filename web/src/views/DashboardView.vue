<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { Avatar, Document, Key, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

interface QuickLink {
  title: string
  description: string
  path: string
  permission?: string
  icon: Component
}

const auth = useAuthStore()
const router = useRouter()

const quickLinks: QuickLink[] = [
  {
    title: '用户管理',
    description: '维护账号、状态与角色分配',
    path: '/system/users',
    permission: 'sys:user',
    icon: User,
  },
  {
    title: '角色管理',
    description: '维护角色、继承关系与权限授权',
    path: '/system/roles',
    permission: 'sys:role',
    icon: Avatar,
  },
  {
    title: '权限管理',
    description: '维护菜单、按钮与 API 权限点',
    path: '/system/permissions',
    permission: 'sys:perm',
    icon: Key,
  },
  {
    title: '审计日志',
    description: '查询变更与安全事件',
    path: '/system/audit-logs',
    permission: 'sys:audit',
    icon: Document,
  },
]

const visibleLinks = computed(() =>
  quickLinks.filter((link) => !link.permission || auth.hasPermission(link.permission)),
)
const isWildcard = computed(() => auth.permissions.includes('*'))
const permissionPreview = computed(() => auth.permissions.slice(0, 24))
const roleNames = computed(() => auth.roles.map((role) => role.name).join('、') || '未分配角色')
const userEmail = computed(() => auth.user?.email || '-')

function openLink(path: string): void {
  void router.push(path)
}
</script>

<template>
  <div class="page-stack">
    <section class="page-heading">
      <div>
        <h1>仪表盘</h1>
        <p>欢迎回来，{{ auth.displayName }}。以下信息来自当前登录会话。</p>
      </div>
    </section>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-card__header">
              <span>账号信息</span>
              <el-tag v-if="isWildcard" type="danger" effect="plain" size="small">
                超级管理员
              </el-tag>
            </div>
          </template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="账号">
              {{ auth.user?.username ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="昵称">
              {{ auth.user?.nickname || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userEmail }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ roleNames }}</el-descriptions-item>
            <el-descriptions-item label="权限数量">
              {{ auth.permissions.length }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-card__header">
              <span>权限概览</span>
              <el-tag size="small" type="info" effect="plain">/api/auth/me</el-tag>
            </div>
          </template>
          <div class="permission-summary">
            <div class="permission-summary__number">{{ auth.permissions.length }}</div>
            <div class="permission-summary__label">已授予权限码</div>
          </div>
          <div class="permission-tags">
            <el-tag
              v-for="code in permissionPreview"
              :key="code"
              size="small"
              type="info"
              effect="plain"
              class="permission-tag"
            >
              {{ code }}
            </el-tag>
            <span v-if="auth.permissions.length === 0" class="text-muted">
              当前账号尚未授予任何权限码
            </span>
            <span
              v-else-if="auth.permissions.length > permissionPreview.length"
              class="text-muted"
            >
              等 {{ auth.permissions.length }} 项
            </span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-card__header">
          <span>快捷入口</span>
        </div>
      </template>
      <div v-if="visibleLinks.length > 0" class="quick-links">
        <button
          v-for="link in visibleLinks"
          :key="link.path"
          type="button"
          class="quick-link"
          @click="openLink(link.path)"
        >
          <el-icon class="quick-link__icon"><component :is="link.icon" /></el-icon>
          <span class="quick-link__title">{{ link.title }}</span>
          <span class="quick-link__desc">{{ link.description }}</span>
        </button>
      </div>
      <el-empty v-else description="当前账号没有可访问的管理菜单" :image-size="72" />
    </el-card>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="前端权限只决定菜单、路由与按钮的展示；真正的安全边界由后端 API 鉴权保证。"
    />
  </div>
</template>
