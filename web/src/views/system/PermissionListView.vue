<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createPermission,
  deletePermission,
  fetchPermissionTree,
  updatePermission,
} from '../../api/permissions'
import type { PermissionResourceType, PermissionTreeNode } from '../../types/permission'
import {
  HTTP_METHOD_OPTIONS,
  RESOURCE_TYPE_OPTIONS,
  statusTagType,
  statusText,
  resourceTypeTagType,
  resourceTypeText,
} from '../../utils/constants'
import { trimToNull } from '../../utils/format'

const loading = ref(false)
const treeRows = ref<PermissionTreeNode[]>([])
const keyword = ref('')
const expandAll = ref(true)
const tableKey = ref(0)

async function loadTree(): Promise<void> {
  loading.value = true
  try {
    treeRows.value = await fetchPermissionTree()
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    loading.value = false
  }
}

function filterTree(nodes: PermissionTreeNode[], kw: string): PermissionTreeNode[] {
  if (!kw) {
    return nodes
  }
  const result: PermissionTreeNode[] = []
  for (const node of nodes) {
    const children = filterTree(node.children, kw)
    if (
      node.name.toLowerCase().includes(kw) ||
      node.code.toLowerCase().includes(kw) ||
      children.length > 0
    ) {
      result.push({ ...node, children })
    }
  }
  return result
}

const filteredRows = computed(() =>
  filterTree(treeRows.value, keyword.value.trim().toLowerCase()),
)

watch(keyword, () => {
  tableKey.value += 1
})

function toggleExpand(): void {
  expandAll.value = !expandAll.value
  tableKey.value += 1
}

function handleRefresh(): void {
  void loadTree()
}

/* ---------------- 表单 ---------------- */

type DialogMode = 'create' | 'edit'

interface PermissionFormState {
  code: string
  name: string
  resourceType: PermissionResourceType
  parentId: number | null
  path: string
  httpMethod: string
  icon: string
  sort: number
  status: number
}

const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<PermissionFormState>({
  code: '',
  name: '',
  resourceType: 'API',
  parentId: null,
  path: '',
  httpMethod: '',
  icon: '',
  sort: 0,
  status: 1,
})

const rules: FormRules = {
  code: [
    { required: true, message: '请输入权限码', trigger: 'blur' },
    {
      pattern:
        /^(\*|[a-z][a-z0-9-]*(?::(?:\*|[a-z][a-z0-9-]*)(?:\.[a-z][a-z0-9-]*)?){0,2})$/,
      message: '权限码需全小写，格式为「模块:资源:操作」，或通配码「*」',
      trigger: 'blur',
    },
    { max: 128, message: '权限码不能超过 128 个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入权限名称', trigger: 'blur' },
    { max: 64, message: '权限名称不能超过 64 个字符', trigger: 'blur' },
  ],
  resourceType: [{ required: true, message: '请选择权限类型', trigger: 'change' }],
}

function findNode(
  nodes: PermissionTreeNode[],
  id: number,
): PermissionTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) {
      return node
    }
    const found = findNode(node.children, id)
    if (found) {
      return found
    }
  }
  return null
}

function collectIds(node: PermissionTreeNode, target: Set<number>): void {
  target.add(node.id)
  for (const child of node.children) {
    collectIds(child, target)
  }
}

function pruneTree(
  nodes: PermissionTreeNode[],
  excluded: Set<number>,
): PermissionTreeNode[] {
  const result: PermissionTreeNode[] = []
  for (const node of nodes) {
    if (excluded.has(node.id)) {
      continue
    }
    result.push({ ...node, children: pruneTree(node.children, excluded) })
  }
  return result
}

const parentTree = computed(() => {
  if (dialogMode.value !== 'edit' || editingId.value === null) {
    return treeRows.value
  }
  const current = findNode(treeRows.value, editingId.value)
  if (!current) {
    return treeRows.value
  }
  const excluded = new Set<number>()
  collectIds(current, excluded)
  return pruneTree(treeRows.value, excluded)
})

function resetForm(parentId: number | null): void {
  form.code = ''
  form.name = ''
  form.resourceType = 'API'
  form.parentId = parentId
  form.path = ''
  form.httpMethod = ''
  form.icon = ''
  form.sort = 0
  form.status = 1
}

function openCreate(parentNode?: unknown): void {
  const parent = parentNode as PermissionTreeNode | null | undefined
  dialogMode.value = 'create'
  editingId.value = null
  resetForm(parent?.id ?? null)
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openEdit(rowData: unknown): void {
  const row = rowData as PermissionTreeNode
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.resourceType = row.resourceType
  form.parentId = row.parentId
  form.path = row.path ?? ''
  form.httpMethod = row.httpMethod ?? ''
  form.icon = row.icon ?? ''
  form.sort = row.sort
  form.status = row.status
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
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
      await createPermission({
        code: form.code.trim(),
        name: form.name.trim(),
        resourceType: form.resourceType,
        parentId: form.parentId ?? null,
        path: trimToNull(form.path),
        httpMethod: trimToNull(form.httpMethod),
        icon: trimToNull(form.icon),
        sort: Number.isFinite(form.sort) ? form.sort : 0,
      })
      ElMessage.success('权限创建成功')
    } else if (editingId.value !== null) {
      await updatePermission(editingId.value, {
        name: form.name.trim(),
        resourceType: form.resourceType,
        parentId: form.parentId ?? null,
        path: trimToNull(form.path),
        httpMethod: trimToNull(form.httpMethod),
        icon: trimToNull(form.icon),
        sort: Number.isFinite(form.sort) ? form.sort : 0,
        status: form.status,
      })
      ElMessage.success('权限已更新')
    }
    dialogVisible.value = false
    await loadTree()
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    submitting.value = false
  }
}

async function handleDelete(rowData: unknown): Promise<void> {
  const row = rowData as PermissionTreeNode
  try {
    await ElMessageBox.confirm(
      `确定删除权限「${row.name}」（${row.code}）吗？被角色引用的权限无法删除。`,
      '删除权限',
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
    await deletePermission(row.id)
    ElMessage.success('权限已删除')
    await loadTree()
  } catch {
    // 全局拦截器已提示错误。
  }
}

onMounted(() => {
  void loadTree()
})
</script>

<template>
  <div class="page-stack">
    <section class="page-heading">
      <div>
        <h1>权限管理</h1>
        <p>维护菜单、按钮、API 与系统权限点。权限码创建后不可修改。</p>
      </div>
      <div class="page-heading__actions">
        <el-button
          v-perm="'sys:perm:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate()"
        >
          新增权限
        </el-button>
      </div>
    </section>

    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="按名称或权限码过滤"
          clearable
          style="width: 280px"
        />
        <div class="filter-bar__actions">
          <el-button :icon="Refresh" @click="handleRefresh">刷新</el-button>
          <el-button @click="toggleExpand">
            {{ expandAll ? '全部折叠' : '全部展开' }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table
        :key="tableKey"
        v-loading="loading"
        :data="filteredRows"
        row-key="id"
        border
        :tree-props="{ children: 'children' }"
        :default-expand-all="expandAll || keyword.trim().length > 0"
      >
        <el-table-column prop="name" label="权限名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="code" label="权限码" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="resourceTypeTagType(row.resourceType)" size="small">
              {{ resourceTypeText(row.resourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.path || '-' }}</template>
        </el-table-column>
        <el-table-column label="方法" width="90">
          <template #default="{ row }">
            <span class="mono">{{ row.httpMethod || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="110">
          <template #default="{ row }">{{ row.icon || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-perm="'sys:perm:create'"
                link
                type="primary"
                size="small"
                @click="openCreate(row)"
              >
                新增子项
              </el-button>
              <el-button
                v-perm="'sys:perm:update'"
                link
                type="primary"
                size="small"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-perm="'sys:perm:delete'"
                link
                type="danger"
                size="small"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无权限数据" :image-size="72" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增权限' : '编辑权限'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="上级节点">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            filterable
            placeholder="不选择则为根节点"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="权限码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="dialogMode === 'edit'"
            placeholder="例如 sys:user:read"
            maxlength="128"
          />
          <div class="form-tip">
            {{
              dialogMode === 'edit'
                ? '权限码创建后不可修改。'
                : '格式：模块:资源:操作；通配码使用 * 结尾，例如 sys:user:*。'
            }}
          </div>
        </el-form-item>
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入权限名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型" prop="resourceType">
          <el-radio-group v-model="form.resourceType">
            <el-radio
              v-for="option in RESOURCE_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="form.path" placeholder="菜单路径，例如 /system/users" maxlength="255" />
        </el-form-item>
        <el-form-item label="HTTP 方法">
          <el-select
            v-model="form.httpMethod"
            clearable
            placeholder="API 权限可指定方法"
            style="width: 100%"
          >
            <el-option
              v-for="option in HTTP_METHOD_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="选填，图标名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'edit'" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-else label="状态">
          <el-tag type="success" size="small">启用</el-tag>
          <span class="form-tip">新建权限默认启用，创建后可在编辑中停用。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
