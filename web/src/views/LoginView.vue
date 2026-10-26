<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)
const sessionExpired = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(() => {
  sessionExpired.value = route.query.reason === 'expired'
})

async function handleLogin(): Promise<void> {
  if (!formRef.value || loading.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    if (auth.mustChangePassword) {
      await router.replace('/change-password')
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target =
      redirect.startsWith('/') &&
      !redirect.startsWith('//') &&
      !redirect.startsWith('/login')
        ? redirect
        : '/dashboard'
    await router.replace(target)
    ElMessage.success('登录成功')
  } catch {
    // 全局拦截器已给出具体错误提示。
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-panel">
      <section class="login-intro">
        <div class="brand-mark brand-mark--large">R</div>
        <h1>RBAC 权限管理系统</h1>
        <p>统一身份、角色与权限管理平台</p>
        <ul class="login-features">
          <li>基于权限码的细粒度访问控制</li>
          <li>角色继承与权限树授权</li>
          <li>完整的变更与安全审计日志</li>
        </ul>
      </section>

      <el-card class="login-card" shadow="never">
        <template #header>
          <div class="login-card__header">
            <h2>账号登录</h2>
            <p>请使用管理员账号登录</p>
          </div>
        </template>

        <el-alert
          v-if="sessionExpired"
          class="login-alert"
          type="warning"
          :closable="false"
          show-icon
          title="登录状态已失效，请重新登录"
        />

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="form.username"
              :prefix-icon="User"
              placeholder="请输入账号"
              autocomplete="username"
              clearable
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              class="login-submit"
              :loading="loading"
              native-type="submit"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <p class="login-tip">登录后菜单与按钮将按账号权限自动展示。</p>
      </el-card>
    </div>
  </div>
</template>
