<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { ElTree } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  fetchRoles,
  createRole,
  updateRole,
  deleteRole,
  toggleRoleStatus,
  fetchRolePermissions,
  assignRolePermissions,
  type RoleQuery,
} from '@/api/role'
import { fetchPermissionTree } from '@/api/permission'
import type { RoleRecord, RoleForm } from '@/types/role'
import type { PermissionNode } from '@/types/permission'
import { ApiError } from '@/types/api'
import { formatDateTime } from '@/utils/format'

// ---------- 列表 ----------
const loading = ref(false)
const list = ref<RoleRecord[]>([])
const total = ref(0)
const query = reactive<RoleQuery>({ page: 1, size: 20, keyword: '', status: undefined })

async function fetchList() {
  loading.value = true
  try {
    const data = await fetchRoles({ ...query })
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
const form = reactive<RoleForm>({
  code: '',
  name: '',
  description: '',
  inheritRoleId: 0,
  status: 1,
  sort: 0,
})

const rules: FormRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  Object.assign(form, {
    code: '',
    name: '',
    description: '',
    inheritRoleId: 0,
    status: 1,
    sort: 0,
  })
  dialogVisible.value = true
}

function openEdit(row: RoleRecord) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    code: row.code,
    name: row.name,
    description: row.description ?? '',
    inheritRoleId: row.inheritRoleId,
    status: row.status,
    sort: row.sort,
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: RoleForm = {
      code: form.code,
      name: form.name,
      description: form.description || undefined,
      inheritRoleId: form.inheritRoleId,
      status: form.status,
      sort: form.sort,
    }
    if (dialogMode.value === 'create') {
      await createRole(payload)
      ElMessage.success('新增成功')
    } else if (editingId.value !== null) {
      await updateRole(editingId.value, payload)
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
async function handleToggle(row: RoleRecord) {
  const target = row.status === 1 ? 0 : 1
  const actionText = target === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${actionText}角色「${row.name}」吗？`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await toggleRoleStatus(row.id, target)
    ElMessage.success(`${actionText}成功`)
    fetchList()
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  }
}

// ---------- 删除 ----------
async function handleDelete(row: RoleRecord) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  }
}

// ---------- 分配权限 ----------
const permDialogVisible = ref(false)
const permTarget = ref<RoleRecord | null>(null)
const permTree = ref<PermissionNode[]>([])
const permVersion = ref(0)
const permLoading = ref(false)
const permSubmitting = ref(false)
const treeRef = ref<InstanceType<typeof ElTree>>()
const idToCode = new Map<number, string>()

function flatten(nodes: PermissionNode[]) {
  for (const n of nodes) {
    idToCode.set(n.id, n.code)
    if (n.children && n.children.length) flatten(n.children)
  }
}

async function openAssignPerm(row: RoleRecord) {
  permTarget.value = row
  permTree.value = []
  permVersion.value = 0
  permDialogVisible.value = true
  permLoading.value = true
  idToCode.clear()
  try {
    const [tree, current] = await Promise.all([
      fetchPermissionTree(),
      fetchRolePermissions(row.id),
    ])
    permTree.value = tree
    flatten(tree)
    permVersion.value = current.version
    const checkedIds: number[] = []
    for (const [id, code] of idToCode) {
      if (current.permissionCodes.includes(code)) checkedIds.push(id)
    }
    // 等 DOM 渲染后再设置勾选
    await nextTick()
    treeRef.value?.setCheckedKeys(checkedIds)
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
    permDialogVisible.value = false
  } finally {
    permLoading.value = false
  }
}

async function submitAssignPerm() {
  if (!permTarget.value) return
  const checkedIds = treeRef.value?.getCheckedKeys() as number[]
  const permissionCodes = checkedIds.map((id) => idToCode.get(id)).filter((c): c is string => !!c)
  permSubmitting.value = true
  try {
    await assignRolePermissions(permTarget.value.id, {
      permissionCodes,
      expectedVersion: permVersion.value,
    })
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
  } catch (e) {
    if (e instanceof ApiError && e.code === 40950) {
      ElMessage.warning('数据已被他人修改，已重新加载，请再次确认提交')
      try {
        const current = await fetchRolePermissions(permTarget.value.id)
        permVersion.value = current.version
        const checkedIds: number[] = []
        for (const [id, code] of idToCode) {
          if (current.permissionCodes.includes(code)) checkedIds.push(id)
        }
        treeRef.value?.setCheckedKeys(checkedIds)
      } catch {
        /* ignore */
      }
    } else if (e instanceof ApiError) {
      ElMessage.error(e.message)
    }
  } finally {
    permSubmitting.value = false
  }
}

function typeTag(type: string): 'primary' | 'success' | 'warning' | 'info' {
  if (type === 'MENU') return 'primary'
  if (type === 'BUTTON') return 'success'
  if (type === 'API') return 'warning'
  return 'info'
}

onMounted(fetchList)
</script>

<template>
  <div>
    <el-card>
      <el-form inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="角色编码 / 名称"
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

      <div class="toolbar">
        <el-button v-perm="'sys:role:create'" type="primary" :icon="Plus" @click="openCreate">
          新增角色
        </el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="code" label="角色编码" min-width="140" />
        <el-table-column prop="name" label="角色名称" min-width="120" />
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="内置" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.builtin" type="danger" size="small">内置</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'sys:role:update'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-perm="'sys:role:assign-perm'" link type="primary" @click="openAssignPerm(row)">
              分配权限
            </el-button>
            <el-button
              v-perm="'sys:role:toggle'"
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              :disabled="row.builtin"
              @click="handleToggle(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button
              v-perm="'sys:role:delete'"
              link
              type="danger"
              :disabled="row.builtin"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

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
      :title="dialogMode === 'create' ? '新增角色' : '编辑角色'"
      width="520px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="dialogMode === 'edit'" placeholder="如 OPERATOR" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="继承角色ID" prop="inheritRoleId">
          <el-input-number v-model="form.inheritRoleId" :min="0" />
          <span class="form-tip">RBAC1 预留，RBAC0 阶段恒为 0</span>
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限弹窗 -->
    <el-dialog v-model="permDialogVisible" :title="`分配权限 - ${permTarget?.name ?? ''}`" width="560px">
      <div v-loading="permLoading" class="perm-tree-wrap">
        <el-tree
          ref="treeRef"
          :data="permTree"
          node-key="id"
          show-checkbox
          check-strictly
          default-expand-all
          :props="{ label: 'name', children: 'children' }"
        >
          <template #default="{ data }">
            <span class="tree-node">
              <span>{{ data.name }}</span>
              <el-tag :type="typeTag(data.resourceType)" size="small" class="tree-tag">
                {{ data.resourceType }}
              </el-tag>
              <span class="tree-code">{{ data.code }}</span>
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSubmitting" @click="submitAssignPerm">确定</el-button>
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

.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.perm-tree-wrap {
  max-height: 420px;
  overflow: auto;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tree-tag {
  flex-shrink: 0;
}

.tree-code {
  color: #909399;
  font-size: 12px;
}
</style>
