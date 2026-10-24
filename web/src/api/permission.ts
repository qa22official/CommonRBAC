import { get, post, put, del } from './request'
import type { PermissionNode, PermissionForm } from '@/types/permission'

/** GET /api/permissions/tree */
export function fetchPermissionTree(): Promise<PermissionNode[]> {
  return get<PermissionNode[]>('/permissions/tree')
}

/** GET /api/permissions/{id} */
export function fetchPermission(id: number): Promise<PermissionNode> {
  return get<PermissionNode>(`/permissions/${id}`)
}

/** POST /api/permissions */
export function createPermission(data: PermissionForm): Promise<PermissionNode> {
  return post<PermissionNode>('/permissions', data)
}

/** PUT /api/permissions/{id} */
export function updatePermission(id: number, data: PermissionForm): Promise<PermissionNode> {
  return put<PermissionNode>(`/permissions/${id}`, data)
}

/** DELETE /api/permissions/{id} */
export function deletePermission(id: number): Promise<null> {
  return del<null>(`/permissions/${id}`)
}
