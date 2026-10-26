export interface AuthUser {
  id: number
  username: string
  nickname: string | null
  email: string | null
}

export interface RoleSummary {
  id: number
  code: string
  name: string
}

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  expiresIn: number
  user: AuthUser
  mustChangePassword: boolean
}

export interface RefreshResult {
  accessToken: string
  expiresIn: number
}

export interface MeResult {
  user: AuthUser
  permissions: string[]
  roles: RoleSummary[]
  mustChangePassword: boolean
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}
