export interface UserListItem {
  id: number
  username: string
  nickname: string | null
  email: string | null
  phone: string | null
  status: number
  createdAt: string
}

export interface UserProfile {
  id: number
  username: string
  nickname: string | null
  email: string | null
  phone: string | null
  status: number
  remark: string | null
  createdAt: string
  roles: Array<{
    id: number
    code: string
    name: string
  }>
}

export interface UserQuery {
  page: number
  size: number
  keyword?: string
  status?: number
}

export interface CreateUserPayload {
  username: string
  nickname: string
  email?: string | null
  phone?: string | null
  status: number
  remark?: string | null
  initialPassword: string
}

export interface UpdateUserPayload {
  nickname: string
  email?: string | null
  phone?: string | null
  remark?: string | null
}

export interface UserRoles {
  roleIds: number[]
  version: number
}

export interface AssignRolesPayload {
  roleIds: number[]
  expectedVersion: number
}

export interface ChangeUserStatusPayload {
  status: number
}

export interface ResetPasswordPayload {
  newPassword: string
}
