<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'
import {
  changeUserStatus,
  createUser,
  deleteUser,
  fetchUser,
  fetchUserRoles,
  fetchUsers,
  resetUserPassword,
  updateUser,
  updateUserRoles,
} from '../../api/users'
import { fetchRoleOptions } from '../../api/roles'
import { isVersionConflict } from '../../api/http'
import type { RoleSummary } from '../../types/auth'
import type { UserListItem } from '../../types/user'
import {
  STATUS_DISABLED,
  STATUS_ENABLED,
  USER_STATUS_OPTIONS,
  statusTagType,
  statusText,
} from '../../utils/constants'
import { formatDateTime, trimToNull } from '../../utils/format'
import {
  PASSWORD_RULE_TEXT,
  evaluatePassword,
} from '../../utils/password'
import PasswordStrength from '../../components/PasswordStrength.vue'

const auth = useAuthStore()

const loading = ref(false)
const rows = ref<UserListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchUsers({
      page: page.value,
      size: size.value,
      keyword: trimToNull(keyword.value) ?? undefined,
      status: typeof statusFilter.value === 'number' ? statusFilter.value : undefined,
    })
    rows.value = result.records
    total.value = result.total
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  page.value = 1
  void loadUsers()
}

function handleReset(): void {
  keyword.value = ''
  statusFilter.value = undefined
  page.value = 1
  void loadUsers()
}

function handleCurrentChange(nextPage: number): void {
  page.value = nextPage
  void loadUsers()
}

function handleSizeChange(nextSize: number): void {
  size.value = nextSize
  page.value = 1
  void loadUsers()
}

function isSelf(rowData: unknown): boolean {
  const row = rowData as UserListItem
  return auth.user?.id === row.id
}

async function reloadAfterMutation(): Promise<void> {
  await loadUsers()
  if (rows.value.length === 0 && total.value > 0 && page.value > 1) {
    page.value -= 1
    await loadUsers()
  }
}

/* ---------------- 新增 / 编辑 ---------------- */

type DialogMode = 'create' | 'edit'

interface UserFormState {
  username: string
  nickname: string
  initialPassword: string
  email: string
  phone: string
  status: number
  remark: string
}

const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<UserFormState>({
  username: '',
  nickname: '',
  initialPassword: '',
  email: '',
  phone: '',
  status: STATUS_ENABLED,
  remark: '',
})

function validateInitialPassword(
  _rule: unknown,
  value: unknown,
  callback: (error?: string | Error) => void,
): void {
  if (dialogMode.value !== 'create') {
    callback()
    return
  }
  const password = typeof value === 'string' ? value : ''
  if (!password) {
    callback(new Error('请输入初始密码'))
    return
  }
  if (!evaluatePassword(password).valid) {
    callback(new Error(PASSWORD_RULE_TEXT))
    return
  }
  callback()
}

function validateEmail(
  _rule: unknown,
  value: unknown,
  callback: (error?: string | Error) => void,
): void {
  const text = typeof value === 'string' ? value.trim() : ''
  if (text && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text)) {
    callback(new Error('邮箱格式不正确'))
    return
  }
  callback()
}

function validatePhone(
  _rule: unknown,
  value: unknown,
  callback: (error?: string | Error) => void,
): void {
  const text = typeof value === 'string' ? value.trim() : ''
  if (text && !/^1[3-9]\d{9}$/.test(text)) {
    callback(new Error('手机号格式不正确'))
    return
  }
  callback()
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 64, message: '账号长度需为 3~64 个字符', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_.-]+$/,
      message: '账号只能包含字母、数字、下划线、点与连字符',
      trigger: 'blur',
    },
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 64, message: '昵称不能超过 64 个字符', trigger: 'blur' },
  ],
  initialPassword: [{ validator: validateInitialPassword, trigger: 'blur' }],
  email: [{ validator: validateEmail, trigger: 'blur' }],
  phone: [{ validator: validatePhone, trigger: 'blur' }],
  remark: [{ max: 255, message: '备注不能超过 255 个字符', trigger: 'blur' }],
}

function resetForm(): void {
  form.username = ''
  form.nickname = ''
  form.initialPassword = ''
  form.email = ''
  form.phone = ''
  form.status = STATUS_ENABLED
  form.remark = ''
}

function openCreate(): void {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function openEdit(rowData: unknown): Promise<void> {
  const row = rowData as UserListItem
  try {
    const detail = await fetchUser(row.id)
    dialogMode.value = 'edit'
    editingId.value = detail.id
    form.username = detail.username
    form.nickname = detail.nickname ?? ''
    form.initialPassword = ''
    form.email = detail.email ?? ''
    form.phone = detail.phone ?? ''
    form.status = detail.status
    form.remark = detail.remark ?? ''
    dialogVisible.value = true
    void nextTick(() => formRef.value?.clearValidate())
  } catch {
    // 全局拦截器已提示错误。
  }
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value || submitting.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createUser({
        username: form.username.trim(),
        nickname: form.nickname.trim(),
        email: trimToNull(form.email),
        phone: trimToNull(form.phone),
        status: form.status,
        remark: trimToNull(form.remark),
        initialPassword: form.initialPassword,
      })
      ElMessage.success('用户创建成功，首次登录需修改初始密码')
    } else if (editingId.value !== null) {
      await updateUser(editingId.value, {
        nickname: form.nickname.trim(),
        email: trimToNull(form.email),
        phone: trimToNull(form.phone),
        remark: trimToNull(form.remark),
      })
      ElMessage.success('用户信息已更新')
    }
    dialogVisible.value = false
    await reloadAfterMutation()
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    submitting.value = false
  }
}

/* ---------------- 删除 / 启停 ---------------- */

async function handleDelete(rowData: unknown): Promise<void> {
  const row = rowData as UserListItem
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」吗？删除后该账号将无法登录。`,
      '删除用户',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  try {
    await deleteUser(row.id)
    ElMessage.success('用户已删除')
    await reloadAfterMutation()
  } catch {
    // 全局拦截器已提示错误。
  }
}

async function handleToggleStatus(rowData: unknown): Promise<void> {
  const row = rowData as UserListItem
  const target = row.status === STATUS_ENABLED ? STATUS_DISABLED : STATUS_ENABLED
  const actionText = target === STATUS_ENABLED ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `确定${actionText}用户「${row.username}」吗？${
        target === STATUS_DISABLED ? '停用后该账号的全部登录会话将失效。' : ''
      }`,
      `${actionText}用户`,
      {
        type: 'warning',
        confirmButtonText: actionText,
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  try {
    await changeUserStatus(row.id, { status: target })
    ElMessage.success(`用户已${actionText}`)
    await loadUsers()
  } catch {
    // 全局拦截器已提示错误。
  }
}

/* ---------------- 重置密码 ---------------- */

interface ResetPasswordFormState {
  newPassword: string
  confirmPassword: string
}

const resetDialogVisible = ref(false)
const resetSaving = ref(false)
const resetTarget = ref<UserListItem | null>(null)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive<ResetPasswordFormState>({
  newPassword: '',
  confirmPassword: '',
})

function validateResetPassword(
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

function validateResetConfirm(
  _rule: unknown,
  value: unknown,
  callback: (error?: string | Error) => void,
): void {
  const confirm = typeof value === 'string' ? value : ''
  if (confirm !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const resetRules: FormRules = {
  newPassword: [{ validator: validateResetPassword, trigger: 'blur' }],
  confirmPassword: [{ validator: validateResetConfirm, trigger: 'blur' }],
}

function openResetPassword(rowData: unknown): void {
  resetTarget.value = rowData as UserListItem
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  resetDialogVisible.value = true
  void nextTick(() => passwordFormRef.value?.clearValidate())
}

async function handleResetPassword(): Promise<void> {
  if (!passwordFormRef.value || !resetTarget.value || resetSaving.value) {
    return
  }
  try {
    await passwordFormRef.value.validate()
  } catch {
    return
  }

  resetSaving.value = true
  try {
    await resetUserPassword(resetTarget.value.id, {
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码已重置，该用户下次登录需修改密码')
    resetDialogVisible.value = false
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    resetSaving.value = false
  }
}

/* ---------------- 分配角色 ---------------- */

const roleDialogVisible = ref(false)
const roleDialogLoading = ref(false)
const roleSaving = ref(false)
const roleAssignmentLoaded = ref(false)
const roleTarget = ref<UserListItem | null>(null)
const roleOptions = ref<RoleSummary[]>([])
const selectedRoleIds = ref<number[]>([])
const roleVersion = ref(0)

async function loadRoleAssignment(userId: number): Promise<void> {
  const [assignment, options] = await Promise.all([
    fetchUserRoles(userId),
    fetchRoleOptions(),
  ])
  selectedRoleIds.value = [...assignment.roleIds]
  roleVersion.value = assignment.version
  roleOptions.value = options
}

async function openRoleDialog(rowData: unknown): Promise<void> {
  const row = rowData as UserListItem
  roleTarget.value = row
  roleDialogVisible.value = true
  roleDialogLoading.value = true
  roleAssignmentLoaded.value = false
  roleOptions.value = []
  selectedRoleIds.value = []
  roleVersion.value = 0
  try {
    await loadRoleAssignment(row.id)
    roleAssignmentLoaded.value = true
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    roleDialogLoading.value = false
  }
}

async function handleAssignRoles(): Promise<void> {
  if (!roleTarget.value || !roleAssignmentLoaded.value || roleSaving.value) {
    return
  }
  roleSaving.value = true
  try {
    await updateUserRoles(roleTarget.value.id, {
      roleIds: [...selectedRoleIds.value],
      expectedVersion: roleVersion.value,
    })
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    await loadUsers()
  } catch (error) {
    if (isVersionConflict(error) && roleTarget.value) {
      roleDialogLoading.value = true
      try {
        await loadRoleAssignment(roleTarget.value.id)
        roleAssignmentLoaded.value = true
        ElMessage.info('已重新拉取最新角色，请确认后再次保存')
      } catch {
        // 全局拦截器已提示错误。
      } finally {
        roleDialogLoading.value = false
      }
    }
  } finally {
    roleSaving.value = false
  }
}

onMounted(() => {
  void loadUsers()
})
</script>

<template>
  <div class="page-stack">
    <section class="page-heading">
      <div>
        <h1>用户管理</h1>
        <p>维护账号信息、启用状态，以及用户与角色的绑定关系。</p>
      </div>
      <div class="page-heading__actions">
        <el-button
          v-perm="'sys:user:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          新增用户
        </el-button>
      </div>
    </section>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" class="filter-form" @submit.prevent="handleSearch">
        <el-form-item label="账号">
          <el-input
            v-model="keyword"
            placeholder="账号前缀，如 zhangsan"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="statusFilter"
            placeholder="全部状态"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="option in USER_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="账号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-perm="'sys:user:update'"
                link
                type="primary"
                size="small"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-perm="'sys:user:assign-role'"
                link
                type="primary"
                size="small"
                @click="openRoleDialog(row)"
              >
                分配角色
              </el-button>
              <el-button
                v-perm="'sys:user:reset-password'"
                link
                type="warning"
                size="small"
                @click="openResetPassword(row)"
              >
                重置密码
              </el-button>
              <el-button
                v-perm="'sys:user:toggle'"
                link
                type="warning"
                size="small"
                :disabled="isSelf(row)"
                :title="isSelf(row) ? '不能停用当前登录账号' : ''"
                @click="handleToggleStatus(row)"
              >
                {{ row.status === STATUS_ENABLED ? '停用' : '启用' }}
              </el-button>
              <el-button
                v-perm="'sys:user:delete'"
                link
                type="danger"
                size="small"
                :disabled="isSelf(row)"
                :title="isSelf(row) ? '不能删除当前登录账号' : ''"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户数据" :image-size="72" />
        </template>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增用户' : '编辑用户'"
      width="580px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="账号" prop="username">
          <el-input
            v-model="form.username"
            :disabled="dialogMode === 'edit'"
            placeholder="登录账号，创建后不可修改"
            maxlength="32"
            show-word-limit
          />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="初始密码" prop="initialPassword">
          <el-input
            v-model="form.initialPassword"
            type="password"
            show-password
            placeholder="至少 8 位，包含大小写字母与数字"
          />
          <PasswordStrength :password="form.initialPassword" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" maxlength="64" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" maxlength="128" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" maxlength="20" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="option in USER_STATUS_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-else label="状态">
          <el-tag :type="statusTagType(form.status)" size="small">
            {{ statusText(form.status) }}
          </el-tag>
          <span class="form-tip">启用/停用请使用列表中的状态操作。</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="resetDialogVisible"
      title="重置密码"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="`将为用户「${resetTarget?.username ?? ''}」重置密码，重置后该用户所有令牌失效。`"
      />
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="resetRules" label-width="96px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            placeholder="至少 8 位，包含大小写字母与数字"
          />
          <PasswordStrength :password="passwordForm.newPassword" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSaving" @click="handleResetPassword">
          确认重置
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="roleDialogVisible"
      title="分配角色"
      width="560px"
      :close-on-click-modal="false"
    >
      <div v-loading="roleDialogLoading">
        <el-alert
          class="dialog-alert"
          type="info"
          :closable="false"
          show-icon
          :title="`用户：${roleTarget?.username ?? ''}；当前角色版本：${roleVersion}`"
        />
        <el-form label-width="96px">
          <el-form-item label="角色">
            <el-select
              v-model="selectedRoleIds"
              multiple
              filterable
              clearable
              placeholder="请选择角色"
              style="width: 100%"
            >
              <el-option
                v-for="role in roleOptions"
                :key="role.id"
                :label="`${role.name}（${role.code}）`"
                :value="role.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <p class="dialog-tip">
          提交时会携带当前版本号，若数据已被他人修改将自动重新拉取，不会静默覆盖。
        </p>
      </div>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="roleSaving"
          :disabled="roleDialogLoading || !roleAssignmentLoaded"
          @click="handleAssignRoles"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
