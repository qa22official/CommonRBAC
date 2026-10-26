<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { fetchAuditLogs } from '../../api/audit'
import type { AuditCategory, AuditLogItem, AuditLogQuery } from '../../types/audit'
import {
  AUDIT_CATEGORY_OPTIONS,
  AUDIT_RESULT_OPTIONS,
  auditCategoryTagType,
  auditCategoryText,
} from '../../utils/constants'
import {
  formatDateTime,
  formatJson,
  formatOperator,
  formatTarget,
  trimToNull,
} from '../../utils/format'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const canRead = computed(() => auth.hasPermission('sys:audit:read'))

function defaultStartTime(): Date {
  return new Date(Date.now() - 24 * 60 * 60 * 1000)
}

const startTime = ref<Date | null>(defaultStartTime())
const endTime = ref<Date | null>(new Date())
const category = ref<AuditCategory | undefined>(undefined)
const operatorIdText = ref('')
const action = ref('')
const targetType = ref('')
const targetIdText = ref('')
const result = ref<number | undefined>(undefined)
const size = ref(20)

const records = ref<AuditLogItem[]>([])
const lastId = ref<number | null>(null)
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const loadedOnce = ref(false)

function parseOptionalId(text: string): { value: number | undefined; valid: boolean } {
  const trimmed = text.trim()
  if (!trimmed) {
    return { value: undefined, valid: true }
  }
  const value = Number(trimmed)
  if (!Number.isInteger(value) || value <= 0) {
    return { value: undefined, valid: false }
  }
  return { value, valid: true }
}

function buildQuery(nextLastId?: number): AuditLogQuery | null {
  if (!startTime.value || !endTime.value) {
    ElMessage.warning('开始时间与结束时间为必填项')
    return null
  }
  if (startTime.value.getTime() > endTime.value.getTime()) {
    ElMessage.warning('开始时间不能晚于结束时间')
    return null
  }

  const operator = parseOptionalId(operatorIdText.value)
  if (!operator.valid) {
    ElMessage.warning('操作人 ID 必须为正整数')
    return null
  }
  const target = parseOptionalId(targetIdText.value)
  if (!target.valid) {
    ElMessage.warning('目标 ID 必须为正整数')
    return null
  }

  const query: AuditLogQuery = {
    startTime: startTime.value.toISOString(),
    endTime: endTime.value.toISOString(),
    size: size.value,
  }
  if (category.value) {
    query.auditCategory = category.value
  }
  if (operator.value !== undefined) {
    query.operatorId = operator.value
  }
  const actionText = trimToNull(action.value)
  if (actionText) {
    query.action = actionText
  }
  const targetTypeText = trimToNull(targetType.value)
  if (targetTypeText) {
    query.targetType = targetTypeText
  }
  if (target.value !== undefined) {
    query.targetId = target.value
  }
  if (typeof result.value === 'number') {
    query.result = result.value
  }
  if (nextLastId !== undefined) {
    query.lastId = nextLastId
  }
  return query
}

async function handleSearch(): Promise<void> {
  if (!canRead.value) {
    ElMessage.warning('当前账号没有审计日志查询权限')
    return
  }
  const query = buildQuery()
  if (!query) {
    return
  }
  loading.value = true
  try {
    const page = await fetchAuditLogs(query)
    records.value = page.records
    lastId.value = page.nextLastId ?? null
    hasMore.value = page.nextLastId != null
    loadedOnce.value = true
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    loading.value = false
  }
}

async function handleLoadMore(): Promise<void> {
  if (loadingMore.value || lastId.value === null) {
    return
  }
  const query = buildQuery(lastId.value)
  if (!query) {
    return
  }
  loadingMore.value = true
  try {
    const page = await fetchAuditLogs(query)
    records.value = [...records.value, ...page.records]
    lastId.value = page.nextLastId ?? null
    hasMore.value = page.nextLastId != null
  } catch {
    // 全局拦截器已提示错误。
  } finally {
    loadingMore.value = false
  }
}

function handleReset(): void {
  startTime.value = defaultStartTime()
  endTime.value = new Date()
  category.value = undefined
  operatorIdText.value = ''
  action.value = ''
  targetType.value = ''
  targetIdText.value = ''
  result.value = undefined
  size.value = 20
  void handleSearch()
}

function handleSizeChange(): void {
  if (loadedOnce.value) {
    void handleSearch()
  }
}

onMounted(() => {
  if (canRead.value) {
    void handleSearch()
  }
})
</script>

<template>
  <div class="page-stack">
    <section class="page-heading">
      <div>
        <h1>审计日志</h1>
        <p>按时间范围、事件类别、操作人与目标对象查询变更与安全事件。</p>
      </div>
    </section>

    <el-alert
      v-if="!canRead"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号缺少审计日志查询权限（sys:audit:read），无法加载数据。"
    />

    <el-card shadow="never" class="filter-card">
      <el-form label-position="top" class="audit-filter">
        <div class="audit-filter__row">
          <el-form-item label="开始时间" required>
            <el-date-picker
              v-model="startTime"
              type="datetime"
              placeholder="开始时间"
              format="YYYY-MM-DD HH:mm:ss"
              style="width: 210px"
            />
          </el-form-item>
          <el-form-item label="结束时间" required>
            <el-date-picker
              v-model="endTime"
              type="datetime"
              placeholder="结束时间"
              format="YYYY-MM-DD HH:mm:ss"
              style="width: 210px"
            />
          </el-form-item>
          <el-form-item label="类别">
            <el-select
              v-model="category"
              placeholder="全部"
              clearable
              style="width: 180px"
            >
              <el-option
                v-for="option in AUDIT_CATEGORY_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="操作人 ID">
            <el-input
              v-model="operatorIdText"
              placeholder="可选，正整数"
              clearable
              style="width: 150px"
            />
          </el-form-item>
        </div>
        <div class="audit-filter__row">
          <el-form-item label="操作类型">
            <el-input
              v-model="action"
              placeholder="可选，如 LOGIN"
              clearable
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="目标类型">
            <el-input
              v-model="targetType"
              placeholder="可选，如 USER"
              clearable
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="目标 ID">
            <el-input
              v-model="targetIdText"
              placeholder="可选，正整数"
              clearable
              style="width: 150px"
            />
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="result" placeholder="全部" clearable style="width: 120px">
              <el-option
                v-for="option in AUDIT_RESULT_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="每页条数">
            <el-select v-model="size" style="width: 110px" @change="handleSizeChange">
              <el-option :value="20" label="20 条" />
              <el-option :value="50" label="50 条" />
              <el-option :value="100" label="100 条" />
              <el-option :value="200" label="200 条" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作">
            <div class="filter-bar__actions">
              <el-button
                v-perm="'sys:audit:read'"
                type="primary"
                :icon="Search"
                :loading="loading"
                @click="handleSearch"
              >
                查询
              </el-button>
              <el-button :icon="Refresh" @click="handleReset">重置</el-button>
            </div>
          </el-form-item>
        </div>
      </el-form>
      <div class="form-tip">
        时间范围对应事件的 event_time；查询使用游标分页（lastId + size），不使用 OFFSET。
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="records" row-key="id" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="audit-detail">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="日志 ID">{{ row.id }}</el-descriptions-item>
                <el-descriptions-item label="事务 ID">
                  {{ row.transactionId || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="User-Agent" :span="2">
                  {{ row.userAgent || '-' }}
                </el-descriptions-item>
                <el-descriptions-item v-if="row.failReason" label="失败原因" :span="2">
                  {{ row.failReason }}
                </el-descriptions-item>
                <el-descriptions-item label="落库时间" :span="2">
                  {{ formatDateTime(row.createdAt) }}
                </el-descriptions-item>
              </el-descriptions>
              <div class="json-panels">
                <div class="json-panel">
                  <h4>变更前（detailBefore）</h4>
                  <pre class="json-block">{{ formatJson(row.detailBefore) }}</pre>
                </div>
                <div class="json-panel">
                  <h4>变更后（detailAfter）</h4>
                  <pre class="json-block">{{ formatJson(row.detailAfter) }}</pre>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="事件时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.eventTime) }}</template>
        </el-table-column>
        <el-table-column label="类别" width="90">
          <template #default="{ row }">
            <el-tag :type="auditCategoryTagType(row.category)" size="small">
              {{ auditCategoryText(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作人" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatOperator(row.operatorId, row.operatorName) }}
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作" min-width="140" show-overflow-tooltip />
        <el-table-column label="目标对象" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTarget(row.targetType, row.targetId) }}
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tooltip
              :content="row.success ? '执行成功' : row.failReason || '执行失败'"
              placement="top"
            >
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="来源 IP" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.clientIp || '-' }}</template>
        </el-table-column>
        <el-table-column label="traceId" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.traceId || '-' }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            :description="loadedOnce ? '当前条件下暂无审计日志' : '请设置时间范围后查询审计日志'"
            :image-size="72"
          />
        </template>
      </el-table>

      <div class="load-more">
        <el-button
          v-if="hasMore"
          v-perm="'sys:audit:read'"
          :loading="loadingMore"
          @click="handleLoadMore"
        >
          加载更多
        </el-button>
        <span v-else-if="loadedOnce && records.length > 0" class="text-muted">
          已加载全部数据
        </span>
        <span v-if="loadedOnce" class="text-muted">当前已加载 {{ records.length }} 条</span>
      </div>
    </el-card>
  </div>
</template>
