<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  fetchPermissionTree,
  createPermission,
  updatePermission,
  deletePermission,
} from '@/api/permission'
import type { PermissionNode, PermissionForm, ResourceType } from '@/types/permission'
import { ApiError } from '@/types/api'

const loading = ref(false)
const tree = ref<PermissionNode[]>([])

async function fetchTree() {
  loading.value = true
  try {
    tree.value = await fetchPermissionTree()
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function typeTag(type: ResourceType): 'primary' | 'success' | 'warning' | 'info' {
  if (type === 'MENU') return 'primary'
  if (type === 'BUTTON') return 'success'
  if (type === 'API') return 'warning'
  return 'info'
}

// ---------- 新增 / 编辑 ----------
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<PermissionForm>({
  code: '',
  name: '',
  resourceType: 'MENU',
  parentId: 0,
  path: '',
  httpMethod: '',
  icon: '',
  sort: 0,
  status: 1,
})

const rules: FormRules = {
  code: [{ required: true, message: '请输入权限码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  resourceType: [{ required: true, message: '请选择类型', trigger: 'change' }],
}

/** 父节点选择树：加一个虚拟根节点表示“顶级” */
const parentTree = computed<PermissionNode[]>(() => [
  {
    id: 0,
    code: '',
    name: '顶级（无父节点）',
    resourceType: 'MENU',
    parentId: 0,
    sort: 0,
    status: 1,
    builtin: false,
    children: tree.value,
    createdAt: '',
    updatedAt: '',
  },
])

function openCreate(parentId = 0) {
  dialogMode.value = 'create'
  editingId.value = null
  Object.assign(form, {
    code: '',
    name: '',
    resourceType: 'MENU',
    parentId,
    path: '',
    httpMethod: '',
    icon: '',
    sort: 0,
    status: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: PermissionNode) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    code: row.code,
    name: row.name,
    resourceType: row.resourceType,
    parentId: row.parentId,
    path: row.path ?? '',
    httpMethod: row.httpMethod ?? '',
    icon: row.icon ?? '',
    sort: row.sort,
    status: row.status,
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: PermissionForm = {
      code: form.code,
      name: form.name,
      resourceType: form.resourceType,
      parentId: form.parentId,
      path: form.path || undefined,
      httpMethod: form.httpMethod || undefined,
      icon: form.icon || undefined,
      sort: form.sort,
      status: form.status,
    }
    if (dialogMode.value === 'create') {
      await createPermission(payload)
      ElMessage.success('新增成功')
    } else if (editingId.value !== null) {
      await updatePermission(editingId.value, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    fetchTree()
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: PermissionNode) {
  try {
    await ElMessageBox.confirm(`确定删除权限「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deletePermission(row.id)
    ElMessage.success('删除成功')
    fetchTree()
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  }
}

onMounted(fetchTree)
</script>

<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-button v-perm="'sys:perm:create'" type="primary" :icon="Plus" @click="openCreate(0)">
          新增顶级权限
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        border
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="code" label="权限码" min-width="180" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.resourceType)" size="small">{{ row.resourceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="140" show-overflow-tooltip />
        <el-table-column prop="httpMethod" label="HTTP 方法" width="110">
          <template #default="{ row }">{{ row.httpMethod || '-' }}</template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="90">
          <template #default="{ row }">{{ row.icon || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-perm="'sys:perm:create'"
              link
              type="primary"
              @click="openCreate(row.id)"
            >
              新增子节点
            </el-button>
            <el-button v-perm="'sys:perm:update'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button
              v-perm="'sys:perm:delete'"
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
    </el-card>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增权限' : '编辑权限'"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="类型" prop="resourceType">
          <el-radio-group v-model="form.resourceType">
            <el-radio-button value="MENU">MENU</el-radio-button>
            <el-radio-button value="BUTTON">BUTTON</el-radio-button>
            <el-radio-button value="API">API</el-radio-button>
            <el-radio-button value="SYSTEM">SYSTEM</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="权限码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="dialogMode === 'edit'"
            placeholder="如 sys:user:create"
          />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="父节点" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="form.resourceType === 'MENU' || form.resourceType === 'API'" label="路径" prop="path">
          <el-input v-model="form.path" placeholder="MENU 填前端路由，API 填接口路径" />
        </el-form-item>
        <el-form-item v-if="form.resourceType === 'API'" label="HTTP 方法" prop="httpMethod">
          <el-select v-model="form.httpMethod" placeholder="不限" clearable style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
            <el-option label="PATCH" value="PATCH" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.resourceType === 'MENU'" label="图标" prop="icon">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 User" />
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
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
</style>
