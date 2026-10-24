import { get, post, put } from './request'
import type {
  LoginRequest,
  LoginResult,
  MeResult,
  PasswordChangeRequest,
} from '@/types/auth'

/** POST /api/auth/login */
export function login(data: LoginRequest): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', data)
}

/** GET /api/auth/me */
export function fetchMe(): Promise<MeResult> {
  return get<MeResult>('/auth/me')
}

/** POST /api/auth/logout */
export function logout(): Promise<null> {
  return post<null>('/auth/logout')
}

/** POST /api/auth/refresh */
export function refresh(): Promise<LoginResult> {
  return post<LoginResult>('/auth/refresh')
}

/** PUT /api/auth/password */
export function changePassword(data: PasswordChangeRequest): Promise<null> {
  return put<null>('/auth/password', data)
}
