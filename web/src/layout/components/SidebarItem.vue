<script setup lang="ts">
import * as Icons from '@element-plus/icons-vue'
import type { MenuItem } from '@/stores/permission'

defineProps<{ item: MenuItem }>()

/** 后端 icon 字段 -> Element Plus 图标名，非法时回退 Menu */
function resolveIcon(icon?: string): string {
  if (!icon) return 'Menu'
  if (icon in Icons) return icon
  const pascal = icon.charAt(0).toUpperCase() + icon.slice(1)
  return pascal in Icons ? pascal : 'Menu'
}
</script>

<template>
  <el-sub-menu v-if="item.children && item.children.length > 0" :index="item.code">
    <template #title>
      <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
      <span>{{ item.title }}</span>
    </template>
    <sidebar-item
      v-for="child in item.children"
      :key="child.code"
      :item="child"
    />
  </el-sub-menu>

  <el-menu-item v-else-if="item.path" :index="item.path">
    <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
    <template #title>{{ item.title }}</template>
  </el-menu-item>
</template>
