import { get } from './request'
import type { AuditQuery, AuditPageResult } from '@/types/audit'

/** GET /api/audit-logs（游标分页） */
export function fetchAuditLogs(params: AuditQuery): Promise<AuditPageResult> {
  return get<AuditPageResult>('/audit-logs', { params })
}
