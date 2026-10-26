<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { changePassword } from '../api/auth'
import { resetBusinessRoutes } from '../router/dynamic'
import { PASSWORD_RULE_TEXT, evaluatePassword } from '../utils/password'
import PasswordStrength from '../components/PasswordStrength.vue'

const auth = useAuthStore()
const router = useRouter()

const formRef = ref<FormInstance>()
const saving = ref(false)
const loggingOut = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function validateNewPassword(
  _rule: unknown,
  value: unknown,
  callback: (error?: string | Error) => void,
): void {
  const password = typeof value === 'string' ? value : ''
  if (!password) {
    callback(new Error('请输入新密码'))
    return
  }
  if (!evaluatePassword(password).valid) {
    callback(new Error(PASSWORD_RULE_TEXT))
    return
  }
  callback()
}

function validateConfirmPassword(
  _rule: unknown,
  value: unknown,
  callback: (error?: string | Error) => void,
): void {
  const confirm = typeof value === 'string' ? value : ''
  if (!confirm) {
    callback(new Error('请再次输入新密码'))
    return
  }
  if (confirm !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ validator: validateNewPassword, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }],
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value || saving.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    auth.clearAuth()
    resetBusinessRoutes()
    ElMessage.success('密码修改成功，请重新登录')
    await router.replace('/login')
  } catch {
    // 全局拦截器已给出具体错误提示。
  } finally {
    saving.value = false
  }
}

async function handleLogout(): Promise<void> {
  loggingOut.value = true
  try {
    await auth.logout()
    resetBusinessRoutes()
    await router.replace('/login')
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="centered-page">
    <el-card class="password-card" shadow="never">
      <template #header>
        <div class="password-card__header">
          <h2>{{ auth.mustChangePassword ? '首次登录，请修改密码' : '修改密码' }}</h2>
          <p>
            当前账号：
            <strong>{{ auth.displayName }}</strong>
          </p>
        </div>
      </template>

      <el-alert
        v-if="auth.mustChangePassword"
        class="password-alert"
        type="warning"
        :closable="false"
        show-icon
        title="为保障账号安全，首次登录必须修改初始密码后才能进入管理后台。"
      />
      <el-alert
        v-else
        class="password-alert"
        type="info"
        :closable="false"
        show-icon
        title="修改密码后当前登录状态会失效，需要重新登录。"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入原密码"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="至少 8 位，包含大小写字母与数字"
          />
          <PasswordStrength :password="form.newPassword" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="请再次输入新密码"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <div class="password-actions">
          <el-button type="primary" :loading="saving" native-type="submit">
            确认修改
          </el-button>
          <el-button v-if="!auth.mustChangePassword" @click="router.push('/dashboard')">
            返回首页
          </el-button>
          <el-button text type="danger" :loading="loggingOut" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
