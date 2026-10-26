import { del, get, post, put } from './http'
import type { PageResult } from '../types/api'
import type {
  AssignRolesPayload,
  ChangeUserStatusPayload,
  CreateUserPayload,
  ResetPasswordPayload,
  UpdateUserPayload,
  UserListItem,
  UserProfile,
  UserQuery,
  UserRoles,
} from '../types/user'

export function fetchUsers(params: UserQuery): Promise<PageResult<UserListItem>> {
  return get<PageResult<UserListItem>>('/users', { params })
}

export function fetchUser(id: number): Promise<UserProfile> {
  return get<UserProfile>(`/users/${id}`)
}

export function createUser(payload: CreateUserPayload): Promise<void> {
  return post<void>('/users', payload)
}

export function updateUser(id: number, payload: UpdateUserPayload): Promise<void> {
  return put<void>(`/users/${id}`, payload)
}

export function deleteUser(id: number): Promise<void> {
  return del<void>(`/users/${id}`)
}

export function changeUserStatus(
  id: number,
  payload: ChangeUserStatusPayload,
): Promise<void> {
  return put<void>(`/users/${id}/status`, payload)
}

export function resetUserPassword(
  id: number,
  payload: ResetPasswordPayload,
): Promise<void> {
  return put<void>(`/users/${id}/password:reset`, payload)
}

export function fetchUserRoles(id: number): Promise<UserRoles> {
  return get<UserRoles>(`/users/${id}/roles`)
}

export function updateUserRoles(
  id: number,
  payload: AssignRolesPayload,
): Promise<void> {
  return put<void>(`/users/${id}/roles`, payload)
}
