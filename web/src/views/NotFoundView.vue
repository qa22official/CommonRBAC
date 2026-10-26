<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

function goHome(): void {
  void router.replace(auth.isAuthenticated ? '/dashboard' : '/login')
}
</script>

<template>
  <div class="centered-page">
    <el-result icon="warning" title="404" sub-title="页面不存在，或当前账号没有访问该页面的权限">
      <template #extra>
        <el-button type="primary" @click="goHome">
          {{ auth.isAuthenticated ? '返回仪表盘' : '前往登录' }}
        </el-button>
      </template>
    </el-result>
  </div>
</template>
