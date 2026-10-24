<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { fetchAuditLogs } from '@/api/audit'
import type { AuditLogRecord, AuditQuery } from '@/types/audit'
import { ApiError } from '@/types/api'
import { formatDateTime, toJson } from '@/utils/format'

const loading = ref(false)
const list = ref<AuditLogRecord[]>([])
const size = ref(20)

const filters = reactive<Omit<AuditQuery, 'lastId' | 'size'>>({
  startTime: '',
  endTime: '',
  auditCategory: undefined,
  operatorId: undefined,
  action: '',
  targetType: '',
  targetId: undefined,
  result: undefined,
})

const dateRange = ref<[Date, Date] | null>(null)

// 游标分页状态
const lastId = ref<number | undefined>(undefined)
const nextLastId = ref<number | null>(null)
const cursorStack = ref<number[]>([])

const canPrev = () => cursorStack.value.length > 0
const canNext = () => nextLastId.value != null

async function fetchPage() {
  loading.value = true
  try {
    const params: AuditQuery = {
      startTime: filters.startTime,
      endTime: filters.endTime,
      auditCategory: filters.auditCategory,
      operatorId: filters.operatorId,
      action: filters.action || undefined,
      targetType: filters.targetType || undefined,
      targetId: filters.targetId,
      result: filters.result,
      lastId: lastId.value,
      size: size.value,
    }
    const data = await fetchAuditLogs(params)
    list.value = data.records
    nextLastId.value = data.nextLastId ?? null
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) {
    ElMessage.warning('请选择时间范围')
    return
  }
  filters.startTime = dateRange.value[0].toISOString()
  filters.endTime = dateRange.value[1].toISOString()
  cursorStack.value = []
  lastId.value = undefined
  fetchPage()
}

function handleReset() {
  filters.auditCategory = undefined
  filters.operatorId = undefined
  filters.action = ''
  filters.targetType = ''
  filters.targetId = undefined
  filters.result = undefined
  dateRange.value = null
  cursorStack.value = []
  lastId.value = undefined
  list.value = []
  nextLastId.value = null
}

function handleNext() {
  if (nextLastId.value == null) return
  if (lastId.value != null) cursorStack.value.push(lastId.value)
  lastId.value = nextLastId.value
  fetchPage()
}

function handlePrev() {
  lastId.value = cursorStack.value.pop()
  fetchPage()
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detail = ref<AuditLogRecord | null>(null)

function openDetail(row: AuditLogRecord) {
  detail.value = row
  detailVisible.value = true
}

function categoryTag(cat: string): 'warning' | 'danger' {
  return cat === 'SECURITY' ? 'danger' : 'warning'
}

onMounted(() => {
  // 首次进入不自动查询（时间范围必填），仅提示
})
</script>

<template>
  <div>
    <el-card>
      <el-form inline @submit.prevent>
        <el-form-item label="时间范围" required>
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="filters.auditCategory" placeholder="全部" clearable style="width: 130px">
            <el-option label="CHANGE" value="CHANGE" />
            <el-option label="SECURITY" value="SECURITY" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人ID">
          <el-input-number v-model="filters.operatorId" :min="1" :controls="false" placeholder="选填" style="width: 130px" />
        </el-form-item>
        <el-form-item label="动作">
          <el-input v-model="filters.action" placeholder="如 USER_CREATE" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="filters.result" placeholder="全部" clearable style="width: 110px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="审计日志按时间范围必填查询，采用游标翻页（上一页 / 下一页）。"
        class="tip"
      />

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.eventTime) }}</template>
        </el-table-column>
        <el-table-column label="类别" width="100">
          <template #default="{ row }">
            <el-tag :type="categoryTag(row.auditCategory)" size="small">{{ row.auditCategory }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" min-width="110" />
        <el-table-column prop="action" label="动作" min-width="150" />
        <el-table-column label="目标" min-width="140">
          <template #default="{ row }">
            {{ row.targetType ? `${row.targetType}#${row.targetId ?? '-'}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.result === 1 ? 'success' : 'danger'" size="small">
              {{ row.result === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" label="来源 IP" width="140" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-button :disabled="!canPrev()" @click="handlePrev">上一页</el-button>
        <el-button type="primary" :disabled="!canNext()" @click="handleNext">下一页</el-button>
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="审计日志详情" width="640px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="类别">{{ detail.auditCategory }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ detail.action }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          {{ detail.result === 1 ? '成功' : '失败' }}
        </el-descriptions-item>
        <el-descriptions-item label="来源 IP">{{ detail.clientIp }}</el-descriptions-item>
        <el-descriptions-item label="事务ID">{{ detail.transactionId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="TraceID">{{ detail.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="事件时间" :span="2">
          {{ formatDateTime(detail.eventTime) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.failReason" label="失败原因" :span="2">
          {{ detail.failReason }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">变更前快照</el-divider>
      <pre class="json-box">{{ toJson(detail?.detailBefore) }}</pre>
      <el-divider content-position="left">变更后快照</el-divider>
      <pre class="json-box">{{ toJson(detail?.detailAfter) }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
.tip {
  margin-bottom: 12px;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 8px;
}

.json-box {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  margin: 0;
}
</style>
