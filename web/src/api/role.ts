import { get, post, put, del } from './request'
import type { PageResult } from '@/types/api'
import type {
  RoleRecord,
  RoleForm,
  RoleOption,
  RolePermissionsResult,
  AssignPermissionsRequest,
} from '@/types/role'

export interface RoleQuery {
  page: number
  size: number
  keyword?: string
  status?: number
}

/** GET /api/roles */
export function fetchRoles(params: RoleQuery): Promise<PageResult<RoleRecord>> {
  return get<PageResult<RoleRecord>>('/roles', { params })
}

/** GET /api/roles/options */
export function fetchRoleOptions(): Promise<RoleOption[]> {
  return get<RoleOption[]>('/roles/options')
}

/** GET /api/roles/{id} */
export function fetchRole(id: number): Promise<RoleRecord> {
  return get<RoleRecord>(`/roles/${id}`)
}

/** POST /api/roles */
export function createRole(data: RoleForm): Promise<RoleRecord> {
  return post<RoleRecord>('/roles', data)
}

/** PUT /api/roles/{id} */
export function updateRole(id: number, data: RoleForm): Promise<RoleRecord> {
  return put<RoleRecord>(`/roles/${id}`, data)
}

/** DELETE /api/roles/{id} */
export function deleteRole(id: number): Promise<null> {
  return del<null>(`/roles/${id}`)
}

/** PUT /api/roles/{id}/status */
export function toggleRoleStatus(id: number, status: number): Promise<null> {
  return put<null>(`/roles/${id}/status`, { status })
}

/** GET /api/roles/{id}/permissions */
export function fetchRolePermissions(id: number): Promise<RolePermissionsResult> {
  return get<RolePermissionsResult>(`/roles/${id}/permissions`)
}

/** PUT /api/roles/{id}/permissions */
export function assignRolePermissions(
  id: number,
  data: AssignPermissionsRequest,
): Promise<null> {
  return put<null>(`/roles/${id}/permissions`, data)
}
