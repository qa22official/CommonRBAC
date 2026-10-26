import { del, get, post, put } from './http'
import type { PageResult } from '../types/api'
import type { RoleSummary } from '../types/auth'
import type {
  AssignPermissionsPayload,
  ChangeRoleStatusPayload,
  CreateRolePayload,
  RoleDetail,
  RoleListItem,
  RolePermissions,
  RoleQuery,
  RoleTreeNode,
  UpdateRolePayload,
} from '../types/role'

export function fetchRoles(params: RoleQuery): Promise<PageResult<RoleListItem>> {
  return get<PageResult<RoleListItem>>('/roles', { params })
}

export function fetchRoleOptions(): Promise<RoleSummary[]> {
  return get<RoleSummary[]>('/roles/options')
}

export function fetchRoleTree(): Promise<RoleTreeNode[]> {
  return get<RoleTreeNode[]>('/roles/tree')
}

export function fetchRole(id: number): Promise<RoleDetail> {
  return get<RoleDetail>(`/roles/${id}`)
}

export function createRole(payload: CreateRolePayload): Promise<void> {
  return post<void>('/roles', payload)
}

export function updateRole(id: number, payload: UpdateRolePayload): Promise<void> {
  return put<void>(`/roles/${id}`, payload)
}

export function deleteRole(id: number): Promise<void> {
  return del<void>(`/roles/${id}`)
}

export function changeRoleStatus(
  id: number,
  payload: ChangeRoleStatusPayload,
): Promise<void> {
  return put<void>(`/roles/${id}/status`, payload)
}

export function fetchRolePermissions(id: number): Promise<RolePermissions> {
  return get<RolePermissions>(`/roles/${id}/permissions`)
}

export function updateRolePermissions(
  id: number,
  payload: AssignPermissionsPayload,
): Promise<void> {
  return put<void>(`/roles/${id}/permissions`, payload)
}
