<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<template>
  <div>
    <el-card>
      <template #header>
        <span>欢迎使用 RBAC 权限系统</span>
      </template>
      <p>
        你好，{{ auth.user?.nickname || auth.user?.username }}。当前角色：
        <el-tag v-for="r in auth.roles" :key="r.id" class="role-tag" size="small">
          {{ r.name }}
        </el-tag>
      </p>
      <p class="tip">请通过左侧菜单进入各管理模块。</p>
    </el-card>
  </div>
</template>

<style scoped>
.role-tag {
  margin-left: 4px;
}

.tip {
  color: #909399;
}
</style>
