import { get, post, put, del } from './request'
import type { PageResult } from '@/types/api'
import type {
  UserRecord,
  UserForm,
  UserRolesResult,
  AssignRolesRequest,
} from '@/types/user'

export interface UserQuery {
  page: number
  size: number
  keyword?: string
  status?: number
}

/** GET /api/users */
export function fetchUsers(params: UserQuery): Promise<PageResult<UserRecord>> {
  return get<PageResult<UserRecord>>('/users', { params })
}

/** GET /api/users/{id} */
export function fetchUser(id: number): Promise<UserRecord> {
  return get<UserRecord>(`/users/${id}`)
}

/** POST /api/users */
export function createUser(data: UserForm): Promise<UserRecord> {
  return post<UserRecord>('/users', data)
}

/** PUT /api/users/{id} */
export function updateUser(id: number, data: UserForm): Promise<UserRecord> {
  return put<UserRecord>(`/users/${id}`, data)
}

/** DELETE /api/users/{id} */
export function deleteUser(id: number): Promise<null> {
  return del<null>(`/users/${id}`)
}

/** PUT /api/users/{id}/status */
export function toggleUserStatus(id: number, status: number): Promise<null> {
  return put<null>(`/users/${id}/status`, { status })
}

/** PUT /api/users/{id}/password:reset */
export function resetUserPassword(id: number, password: string): Promise<null> {
  return put<null>(`/users/${id}/password:reset`, { password })
}

/** GET /api/users/{id}/roles */
export function fetchUserRoles(id: number): Promise<UserRolesResult> {
  return get<UserRolesResult>(`/users/${id}/roles`)
}

/** PUT /api/users/{id}/roles */
export function assignUserRoles(id: number, data: AssignRolesRequest): Promise<null> {
  return put<null>(`/users/${id}/roles`, data)
}
