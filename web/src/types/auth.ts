export interface LoginRequest {
  username: string
  password: string
}

export interface UserBrief {
  id: number
  username: string
  nickname: string
  email?: string
}

export interface LoginResult {
  accessToken: string
  expiresIn: number
  user: UserBrief
  mustChangePassword: boolean
}

export interface RoleBrief {
  id: number
  code: string
  name: string
}

export interface MeResult {
  user: UserBrief
  permissions: string[]
  roles: RoleBrief[]
}

export interface PasswordChangeRequest {
  oldPassword: string
  newPassword: string
}
