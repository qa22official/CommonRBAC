export type AuditCategory = 'CHANGE' | 'SECURITY'

/** 审计日志记录 */
export interface AuditLogRecord {
  id: number
  auditCategory: AuditCategory
  operatorId?: number
  operatorName: string
  action: string
  targetType?: string
  targetId?: number
  detailBefore?: unknown
  detailAfter?: unknown
  result: number
  failReason?: string
  transactionId?: string
  clientIp: string
  userAgent?: string
  traceId?: string
  eventTime: string
  createdAt: string
}

/** 审计日志游标分页查询参数 */
export interface AuditQuery {
  startTime: string
  endTime: string
  auditCategory?: AuditCategory
  operatorId?: number
  action?: string
  targetType?: string
  targetId?: number
  result?: number
  lastId?: number
  size?: number
}

/** 审计日志游标分页结果 */
export interface AuditPageResult {
  records: AuditLogRecord[]
  nextLastId?: number | null
}
