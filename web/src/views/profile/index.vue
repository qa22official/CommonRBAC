<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { changePassword } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/types/api'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

// 与后端 40010 约定一致：至少 8 位，含大小写字母与数字
const PWD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: PWD_PATTERN,
      message: '密码需至少 8 位，且包含大小写字母与数字',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    // 改密后 token_version 自增，当前令牌已作废，回登录页
    auth.reset()
    router.push('/login')
  } catch (e) {
    if (e instanceof ApiError) {
      ElMessage.error(e.message)
    } else {
      ElMessage.error('修改失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <el-card class="profile-card">
      <template #header><span>个人中心</span></template>
      <el-descriptions :column="2" border class="user-desc">
        <el-descriptions-item label="账号">
          {{ auth.user?.username }}
        </el-descriptions-item>
        <el-descriptions-item label="昵称">
          {{ auth.user?.nickname }}
        </el-descriptions-item>
        <el-descriptions-item label="邮箱">
          {{ auth.user?.email || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">修改密码</el-divider>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="pwd-form"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSubmit">确认修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.profile-card {
  max-width: 720px;
}

.pwd-form {
  max-width: 440px;
}

.user-desc {
  margin-bottom: 8px;
}
</style>
