<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  fetchUsers,
  createUser,
  updateUser,
  deleteUser,
  toggleUserStatus,
  resetUserPassword,
  fetchUserRoles,
  assignUserRoles,
  type UserQuery,
} from '@/api/user'
import { fetchRoleOptions } from '@/api/role'
import type { UserRecord, UserForm } from '@/types/user'
import type { RoleOption } from '@/types/role'
import { ApiError } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const PWD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/

// ---------- 列表 ----------
const loading = ref(false)
const list = ref<UserRecord[]>([])
const total = ref(0)
const query = reactive<UserQuery>({ page: 1, size: 20, keyword: '', status: undefined })

async function fetchList() {
  loading.value = true
  try {
    const data = await fetchUsers({ ...query })
    list.value = data.records
    total.value = data.total
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function handleReset() {
  query.keyword = ''
  query.status = undefined
  query.page = 1
  fetchList()
}

function statusText(status: number): string {
  if (status === 1) return '启用'
  if (status === 0) return '停用'
  return '已删除'
}

function statusTag(status: number): 'success' | 'info' | 'danger' {
  if (status === 1) return 'success'
  if (status === 0) return 'info'
  return 'danger'
}

// ---------- 新增 / 编辑 ----------
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive<UserForm>({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  status: 1,
  remark: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  Object.assign(form, {
    username: '',
    nickname: '',
    email: '',
    phone: '',
    status: 1,
    remark: '',
  })
  dialogVisible.value = true
}

function openEdit(row: UserRecord) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    nickname: row.nickname,
    email: row.email ?? '',
    phone: row.phone ?? '',
    status: row.status,
    remark: row.remark ?? '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: UserForm = {
      username: form.username,
      nickname: form.nickname,
      email: form.email || undefined,
      phone: form.phone || undefined,
      status: form.status,
      remark: form.remark || undefined,
    }
    if (dialogMode.value === 'create') {
      await createUser(payload)
      ElMessage.success('新增成功')
    } else if (editingId.value !== null) {
      await updateUser(editingId.value, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

// ---------- 启用 / 停用 ----------
async function handleToggle(row: UserRecord) {
  const target = row.status === 1 ? 0 : 1
  const actionText = target === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${actionText}账号「${row.username}」吗？`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await toggleUserStatus(row.id, target)
    ElMessage.success(`${actionText}成功`)
    fetchList()
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  }
}

// ---------- 删除 ----------
async function handleDelete(row: UserRecord) {
  try {
    await ElMessageBox.confirm(`确定删除账号「${row.username}」吗？`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  }
}

// ---------- 重置密码 ----------
const pwdDialogVisible = ref(false)
const pwdTarget = ref<UserRecord | null>(null)
const newPassword = ref('')

function openResetPassword(row: UserRecord) {
  pwdTarget.value = row
  newPassword.value = ''
  pwdDialogVisible.value = true
}

async function submitResetPassword() {
  if (!pwdTarget.value) return
  if (!PWD_PATTERN.test(newPassword.value)) {
    ElMessage.warning('密码需至少 8 位，且包含大小写字母与数字')
    return
  }
  try {
    await resetUserPassword(pwdTarget.value.id, newPassword.value)
    ElMessage.success('密码已重置')
    pwdDialogVisible.value = false
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  }
}

// ---------- 分配角色 ----------
const roleDialogVisible = ref(false)
const roleTarget = ref<UserRecord | null>(null)
const roleOptions = ref<RoleOption[]>([])
const selectedRoleIds = ref<number[]>([])
const roleVersion = ref(0)
const roleLoading = ref(false)
const roleSubmitting = ref(false)

async function openAssignRole(row: UserRecord) {
  roleTarget.value = row
  selectedRoleIds.value = []
  roleVersion.value = 0
  roleDialogVisible.value = true
  roleLoading.value = true
  try {
    const [options, current] = await Promise.all([
      fetchRoleOptions(),
      fetchUserRoles(row.id),
    ])
    roleOptions.value = options
    selectedRoleIds.value = current.roleIds
    roleVersion.value = current.version
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
    roleDialogVisible.value = false
  } finally {
    roleLoading.value = false
  }
}

async function submitAssignRole() {
  if (!roleTarget.value) return
  roleSubmitting.value = true
  try {
    await assignUserRoles(roleTarget.value.id, {
      roleIds: selectedRoleIds.value,
      expectedVersion: roleVersion.value,
    })
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
  } catch (e) {
    if (e instanceof ApiError && e.code === 40950) {
      ElMessage.warning('数据已被他人修改，已重新加载，请再次确认提交')
      try {
        const current = await fetchUserRoles(roleTarget.value.id)
        selectedRoleIds.value = current.roleIds
        roleVersion.value = current.version
      } catch {
        /* ignore */
      }
    } else if (e instanceof ApiError) {
      ElMessage.error(e.message)
    }
  } finally {
    roleSubmitting.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div>
    <el-card>
      <!-- 搜索栏 -->
      <el-form inline @submit.prevent>
        <el-form-item label="账号">
          <el-input
            v-model="query.keyword"
            placeholder="账号前缀匹配"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-button v-perm="'sys:user:create'" type="primary" :icon="Plus" @click="openCreate">
          新增用户
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'sys:user:update'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-perm="'sys:user:assign-role'" link type="primary" @click="openAssignRole(row)">
              分配角色
            </el-button>
            <el-button v-perm="'sys:user:reset-password'" link type="primary" @click="openResetPassword(row)">
              重置密码
            </el-button>
            <el-button
              v-perm="'sys:user:toggle'"
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggle(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-perm="'sys:user:delete'" link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="fetchList"
        @size-change="handleSearch"
      />
    </el-card>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增用户' : '编辑用户'"
      width="520px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" :title="`分配角色 - ${roleTarget?.username ?? ''}`" width="480px">
      <div v-loading="roleLoading">
        <el-select
          v-model="selectedRoleIds"
          multiple
          filterable
          placeholder="请选择角色"
          style="width: 100%"
        >
          <el-option
            v-for="opt in roleOptions"
            :key="opt.id"
            :label="`${opt.name}（${opt.code}）`"
            :value="opt.id"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="submitAssignRole">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="pwdDialogVisible" :title="`重置密码 - ${pwdTarget?.username ?? ''}`" width="420px">
      <el-form label-width="80px">
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password placeholder="至少 8 位，含大小写字母与数字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
