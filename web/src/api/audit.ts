import { get } from './http'
import type { AuditLogPage, AuditLogQuery } from '../types/audit'

export function fetchAuditLogs(params: AuditLogQuery): Promise<AuditLogPage> {
  return get<AuditLogPage>('/audit-logs', { params })
}
