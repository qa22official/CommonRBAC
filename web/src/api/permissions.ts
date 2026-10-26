import { del, get, post, put } from './http'
import type {
  CreatePermissionPayload,
  PermissionDetail,
  PermissionTreeNode,
  UpdatePermissionPayload,
} from '../types/permission'

export function fetchPermissionTree(): Promise<PermissionTreeNode[]> {
  return get<PermissionTreeNode[]>('/permissions/tree')
}

export function fetchPermission(id: number): Promise<PermissionDetail> {
  return get<PermissionDetail>(`/permissions/${id}`)
}

export function createPermission(payload: CreatePermissionPayload): Promise<void> {
  return post<void>('/permissions', payload)
}

export function updatePermission(
  id: number,
  payload: UpdatePermissionPayload,
): Promise<void> {
  return put<void>(`/permissions/${id}`, payload)
}

export function deletePermission(id: number): Promise<void> {
  return del<void>(`/permissions/${id}`)
}
