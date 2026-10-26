<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  changeRoleStatus,
  createRole,
  deleteRole,
  fetchRole,
  fetchRoleOptions,
  fetchRolePermissions,
  fetchRoles,
  fetchRoleTree,
  updateRole,
  updateRolePermissions,
} from '../../api/roles'
import { fetchPermissionTree } from '../../api/permissions'
import { isVersionConflict } from '../../api/http'
import type { RoleSummary } from '../../types/auth'
import type { RoleDetail, RoleListItem, RoleTreeNode } from '../../types/role'
import type { PermissionTreeNode } from '../../types/permission'
import {
  ROLE_STATUS_OPTIONS,
  STATUS_DISABLED,
  STATUS_ENABLED,
  resourceTypeText,
  statusTagType,
  statusText,
} from '../../utils/constants'
import { trimToNull } from '../../utils/format'

interface PermissionTreeInstance {
  setCheckedKeys: (keys: string[]) => void
  getCheckedKeys: (leafOnly?: boolean) => Array<string | number>
  filter: (value: string) => void
}

const loading = ref(false)
const rows = ref<RoleListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)
const roleOptions = ref<RoleSummary[]>([])
const treeMode = ref(false)
const treeLoading = ref(false)
const treeRows = ref<RoleTreeNode[]>([])
const displayRows = computed<RoleListItem[]>(() =>
  treeMode.value ? treeRows.value : rows.value,
)
const displayLoading = computed(() => (treeMode.value ? treeLoading.value : loading.value))

async function loadRoles(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchRoles({
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

async function loadRoleOptions(): Promise<void> {
  try {
    roleOptions.value = await fetchRoleOptions()
  } catch {
    // 全局拦截器已提示错误。
  }
}

async function loadRoleTree(): Promise<void> {
  treeLoading.value = true
  try {
    treeRows.value = await fetchRoleTree()
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    treeLoading.value = false
  }
}

async function toggleTreeMode(): Promise<void> {
  treeMode.value = !treeMode.value
  if (treeMode.value) {
    await loadRoleTree()
  }
}

function handleSearch(): void {
  page.value = 1
  void loadRoles()
}

function handleReset(): void {
  keyword.value = ''
  statusFilter.value = undefined
  page.value = 1
  void loadRoles()
}

function handleCurrentChange(nextPage: number): void {
  page.value = nextPage
  void loadRoles()
}

function handleSizeChange(nextSize: number): void {
  size.value = nextSize
  page.value = 1
  void loadRoles()
}

function inheritText(rowData: unknown): string {
  const row = rowData as RoleListItem
  if (row.inheritRoleName) {
    return row.inheritRoleName
  }
  if (row.inheritRoleId != null && row.inheritRoleId > 0) {
    return `#${row.inheritRoleId}`
  }
  return '无'
}

async function reloadAfterMutation(): Promise<void> {
  if (treeMode.value) {
    await loadRoleTree()
  } else {
    await loadRoles()
    if (rows.value.length === 0 && total.value > 0 && page.value > 1) {
      page.value -= 1
      await loadRoles()
    }
  }
  await loadRoleOptions()
}

/* ---------------- 新增 / 编辑 ---------------- */

type DialogMode = 'create' | 'edit'

interface RoleFormState {
  code: string
  name: string
  description: string
  sort: number
  inheritRoleId: number | null
}

const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<RoleFormState>({
  code: '',
  name: '',
  description: '',
  sort: 0,
  inheritRoleId: null,
})

function findRoleNode(nodes: RoleTreeNode[], id: number): RoleTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) {
      return node
    }
    const found = findRoleNode(node.children, id)
    if (found) {
      return found
    }
  }
  return null
}

function collectRoleIds(node: RoleTreeNode, target: Set<number>): void {
  target.add(node.id)
  node.children.forEach((child) => collectRoleIds(child, target))
}

const excludedInheritIds = computed(() => {
  const excluded = new Set<number>()
  if (editingId.value === null) {
    return excluded
  }
  const node = findRoleNode(treeRows.value, editingId.value)
  if (node) {
    collectRoleIds(node, excluded)
  }
  return excluded
})

const inheritOptions = computed(() =>
  roleOptions.value.filter(
    (role) => role.id !== editingId.value && !excludedInheritIds.value.has(role.id),
  ),
)

const rules: FormRules = {
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    {
      pattern: /^[A-Z][A-Z0-9_]+$/,
      message: '角色编码需以大写字母开头，只能包含大写字母、数字与下划线',
      trigger: 'blur',
    },
    { max: 64, message: '角色编码不能超过 64 个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 64, message: '角色名称不能超过 64 个字符', trigger: 'blur' },
  ],
  description: [{ max: 255, message: '描述不能超过 255 个字符', trigger: 'blur' }],
}

function resetForm(): void {
  form.code = ''
  form.name = ''
  form.description = ''
  form.sort = 0
  form.inheritRoleId = null
}

function openCreate(): void {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
  void loadRoleOptions()
  void loadRoleTree()
  void nextTick(() => formRef.value?.clearValidate())
}

function openEdit(rowData: unknown): void {
  const row = rowData as RoleListItem
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.description = row.description ?? ''
  form.sort = row.sort
  form.inheritRoleId = row.inheritRoleId != null && row.inheritRoleId > 0
    ? row.inheritRoleId
    : null
  dialogVisible.value = true
  void loadRoleTree()
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
      await createRole({
        code: form.code.trim(),
        name: form.name.trim(),
        description: trimToNull(form.description),
        sort: Number.isFinite(form.sort) ? form.sort : 0,
        inheritRoleId: form.inheritRoleId ?? null,
      })
      ElMessage.success('角色创建成功')
    } else if (editingId.value !== null) {
      await updateRole(editingId.value, {
        name: form.name.trim(),
        description: trimToNull(form.description),
        sort: Number.isFinite(form.sort) ? form.sort : 0,
        inheritRoleId: form.inheritRoleId ?? null,
      })
      ElMessage.success('角色信息已更新')
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
  const row = rowData as RoleListItem
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${row.name}」吗？仍被用户绑定的角色无法删除。`,
      '删除角色',
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
    await deleteRole(row.id)
    ElMessage.success('角色已删除')
    await reloadAfterMutation()
  } catch {
    // 全局拦截器已提示错误。
  }
}

async function handleToggleStatus(rowData: unknown): Promise<void> {
  const row = rowData as RoleListItem
  const target = row.status === STATUS_ENABLED ? STATUS_DISABLED : STATUS_ENABLED
  const actionText = target === STATUS_ENABLED ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `确定${actionText}角色「${row.name}」吗？${
        target === STATUS_DISABLED ? '停用后持有该角色的用户将立即失去对应权限。' : ''
      }`,
      `${actionText}角色`,
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
    await changeRoleStatus(row.id, { status: target })
    ElMessage.success(`角色已${actionText}`)
    await reloadAfterMutation()
  } catch {
    // 全局拦截器已提示错误。
  }
}

/* ---------------- 详情 ---------------- */

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<RoleDetail | null>(null)

async function openDetail(rowData: unknown): Promise<void> {
  const row = rowData as RoleListItem
  detail.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await fetchRole(row.id)
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    detailLoading.value = false
  }
}

/* ---------------- 权限授权 ---------------- */

const permissionDialogVisible = ref(false)
const permissionDialogLoading = ref(false)
const permissionSaving = ref(false)
const permissionsLoaded = ref(false)
const permissionTarget = ref<RoleListItem | null>(null)
const permissionTree = ref<PermissionTreeNode[]>([])
const permissionCodes = ref<string[]>([])
const permissionVersion = ref(0)
const permissionFilter = ref('')
const permissionTreeRef = ref<PermissionTreeInstance>()

async function loadPermissionAssignment(roleId: number): Promise<void> {
  const [assignment, tree] = await Promise.all([
    fetchRolePermissions(roleId),
    fetchPermissionTree(),
  ])
  permissionTree.value = enabledPermissionNodes(tree)
  const enabledCodes = new Set<string>()
  collectPermissionCodes(permissionTree.value, enabledCodes)
  // 停用权限不允许再被分配；后端只接受 status=1 的码，若不在这里剔除，
  // 编辑一个历史上引用过停用权限的角色会一直得到 40020。
  permissionCodes.value = assignment.permissionCodes.filter((code) => enabledCodes.has(code))
  permissionVersion.value = assignment.version
}

function enabledPermissionNodes(nodes: PermissionTreeNode[]): PermissionTreeNode[] {
  return nodes
    .filter((node) => node.status === 1)
    .map((node) => ({ ...node, children: enabledPermissionNodes(node.children) }))
}

function collectPermissionCodes(nodes: PermissionTreeNode[], target: Set<string>): void {
  for (const node of nodes) {
    target.add(node.code)
    collectPermissionCodes(node.children, target)
  }
}

async function openPermissionDialog(rowData: unknown): Promise<void> {
  const row = rowData as RoleListItem
  permissionTarget.value = row
  permissionFilter.value = ''
  permissionDialogVisible.value = true
  permissionDialogLoading.value = true
  permissionsLoaded.value = false
  permissionTree.value = []
  permissionCodes.value = []
  permissionVersion.value = 0
  try {
    await loadPermissionAssignment(row.id)
    permissionsLoaded.value = true
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    permissionDialogLoading.value = false
  }
  await nextTick()
  permissionTreeRef.value?.setCheckedKeys(permissionCodes.value)
}

function filterPermissionNode(
  value: string | number,
  data: Record<string, unknown>,
): boolean {
  const keywordText = String(value).trim().toLowerCase()
  if (!keywordText) {
    return true
  }
  const name = typeof data.name === 'string' ? data.name.toLowerCase() : ''
  const code = typeof data.code === 'string' ? data.code.toLowerCase() : ''
  return name.includes(keywordText) || code.includes(keywordText)
}

function handlePermissionFilter(value: string): void {
  permissionTreeRef.value?.filter(value)
}

async function handleAssignPermissions(): Promise<void> {
  if (
    !permissionTarget.value ||
    !permissionTreeRef.value ||
    !permissionsLoaded.value ||
    permissionSaving.value
  ) {
    return
  }
  permissionSaving.value = true
  try {
    const codes = permissionTreeRef.value.getCheckedKeys(false).map((key) => String(key))
    await updateRolePermissions(permissionTarget.value.id, {
      // 提交完整显式列表；父子节点独立勾选，不做隐式放大。
      permissionCodes: codes,
      expectedVersion: permissionVersion.value,
    })
    ElMessage.success('权限授权已保存')
    permissionDialogVisible.value = false
  } catch (error) {
    if (isVersionConflict(error) && permissionTarget.value) {
      permissionDialogLoading.value = true
      try {
        await loadPermissionAssignment(permissionTarget.value.id)
        permissionsLoaded.value = true
        permissionFilter.value = ''
        await nextTick()
        permissionTreeRef.value?.filter('')
        permissionTreeRef.value?.setCheckedKeys(permissionCodes.value)
        ElMessage.info('已重新拉取最新授权，请确认后再次保存')
      } catch {
        // 全局拦截器已提示错误。
      } finally {
        permissionDialogLoading.value = false
      }
    }
  } finally {
    permissionSaving.value = false
  }
}

onMounted(() => {
  void loadRoles()
  void loadRoleOptions()
})
</script>

<template>
  <div class="page-stack">
    <section class="page-heading">
      <div>
        <h1>角色管理</h1>
        <p>维护角色、继承关系与权限集合。角色编码创建后不可修改。</p>
      </div>
      <div class="page-heading__actions">
        <el-button :icon="Refresh" @click="toggleTreeMode">
          {{ treeMode ? '列表视图' : '树形视图' }}
        </el-button>
        <el-button
          v-perm="'sys:role:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          新增角色
        </el-button>
      </div>
    </section>

    <el-card v-if="!treeMode" shadow="never" class="filter-card">
      <el-form :inline="true" class="filter-form" @submit.prevent="handleSearch">
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            placeholder="角色编码或名称"
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
              v-for="option in ROLE_STATUS_OPTIONS"
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
      <el-table
        v-loading="displayLoading"
        :data="displayRows"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        border
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="角色编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="角色名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="继承来源" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'text-muted': row.inheritRoleId == null || row.inheritRoleId === 0 }">
              {{ inheritText(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="内置" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.builtin" size="small" type="warning" effect="plain">
              内置
            </el-tag>
            <span v-else class="text-muted">否</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-perm="'sys:role:read'"
                link
                type="primary"
                size="small"
                @click="openDetail(row)"
              >
                详情
              </el-button>
              <el-button
                v-perm="'sys:role:update'"
                link
                type="primary"
                size="small"
                :disabled="row.builtin"
                :title="row.builtin ? '内置角色不允许修改' : ''"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-perm="'sys:role:assign-perm'"
                link
                type="primary"
                size="small"
                @click="openPermissionDialog(row)"
              >
                授权限
              </el-button>
              <el-button
                v-perm="'sys:role:toggle'"
                link
                type="warning"
                size="small"
                :disabled="row.builtin"
                :title="row.builtin ? '内置角色不允许停用' : ''"
                @click="handleToggleStatus(row)"
              >
                {{ row.status === STATUS_ENABLED ? '停用' : '启用' }}
              </el-button>
              <el-button
                v-perm="'sys:role:delete'"
                link
                type="danger"
                size="small"
                :disabled="row.builtin"
                :title="row.builtin ? '内置角色不允许删除' : ''"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无角色数据" :image-size="72" />
        </template>
      </el-table>

      <div v-if="!treeMode" class="table-pagination">
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
      :title="dialogMode === 'create' ? '新增角色' : '编辑角色'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="角色编码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="dialogMode === 'edit'"
            placeholder="例如 OPERATOR，创建后不可修改"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
        <el-form-item label="继承来源">
          <el-select
            v-model="form.inheritRoleId"
            clearable
            filterable
            placeholder="不继承（无）"
            style="width: 100%"
          >
            <el-option
              v-for="role in inheritOptions"
              :key="role.id"
              :label="`${role.name}（${role.code}）`"
              :value="role.id"
            />
          </el-select>
          <div class="form-tip">
            继承来源的权限会在权限计算时合并；仅可选择启用角色，循环与继承深度由后端校验。
          </div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="角色详情" width="620px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border size="small">
          <el-descriptions-item label="角色编码">{{ detail.code }}</el-descriptions-item>
          <el-descriptions-item label="角色名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.status)" size="small">
              {{ statusText(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="内置角色">
            {{ detail.builtin ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="继承来源">
            {{ inheritText(detail) }}
          </el-descriptions-item>
          <el-descriptions-item label="排序">{{ detail.sort }}</el-descriptions-item>
          <el-descriptions-item label="权限数量">
            {{ detail.permissionCount }}
          </el-descriptions-item>
          <el-descriptions-item label="用户数量">
            {{ detail.userCount }}
          </el-descriptions-item>
          <el-descriptions-item label="权限版本">
            {{ detail.permVersion }}
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">
            {{ detail.description || '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-empty v-else-if="!detailLoading" description="暂无详情" :image-size="64" />
      </div>
    </el-dialog>

    <el-dialog
      v-model="permissionDialogVisible"
      :title="`分配权限 · ${permissionTarget?.name ?? ''}`"
      width="720px"
      :close-on-click-modal="false"
    >
      <div class="permission-dialog">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          :title="`当前权限版本：${permissionVersion}。父子节点独立勾选，提交时会发送所选的全部权限码，不做父子联动放大。`"
        />
        <el-alert
          v-if="permissionTarget?.builtin"
          type="warning"
          :closable="false"
          show-icon
          title="内置角色的权限受保护，提交可能被后端拒绝（40321）。"
        />
        <div class="permission-tree__toolbar">
          <el-input
            v-model="permissionFilter"
            placeholder="搜索权限名称或权限码"
            clearable
            :prefix-icon="Search"
            style="width: 320px"
            @input="handlePermissionFilter"
          />
        </div>
        <div v-loading="permissionDialogLoading" class="permission-tree">
          <el-tree
            ref="permissionTreeRef"
            :data="permissionTree"
            node-key="code"
            show-checkbox
            check-strictly
            default-expand-all
            :props="{ label: 'name', children: 'children' }"
            :filter-node-method="filterPermissionNode"
          >
            <template #default="{ data }">
              <span class="perm-node">
                <span class="perm-node__name">{{ data.name }}</span>
                <el-tag size="small" type="info" effect="plain">
                  {{ resourceTypeText(data.resourceType) }}
                </el-tag>
                <span class="perm-node__code">{{ data.code }}</span>
              </span>
            </template>
          </el-tree>
          <el-empty
            v-if="!permissionDialogLoading && permissionTree.length === 0"
            description="暂无权限数据"
            :image-size="64"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="permissionSaving"
          :disabled="permissionDialogLoading || !permissionsLoaded"
          @click="handleAssignPermissions"
        >
          保存授权
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
