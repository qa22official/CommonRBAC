export type AuditCategory = 'CHANGE' | 'SECURITY'

export interface AuditLogItem {
  id: number
  category: AuditCategory
  operatorId: number | null
  operatorName: string | null
  action: string
  targetType: string | null
  targetId: number | null
  detailBefore: string | null
  detailAfter: string | null
  success: boolean
  failReason: string | null
  clientIp: string | null
  userAgent: string | null
  traceId: string | null
  transactionId: string | null
  eventTime: string
  createdAt: string
}

export interface AuditLogPage {
  records: AuditLogItem[]
  nextLastId: number | null
}

export interface AuditLogQuery {
  startTime: string
  endTime: string
  auditCategory?: AuditCategory
  operatorId?: number
  action?: string
  targetType?: string
  targetId?: number
  result?: number
  lastId?: number
  size: number
}
