import { get, post, put } from './http'
import type {
  ChangePasswordPayload,
  LoginPayload,
  LoginResult,
  MeResult,
  RefreshResult,
} from '../types/auth'

export function login(payload: LoginPayload): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', payload, { skipAuthHandling: true })
}

export function getMe(): Promise<MeResult> {
  return get<MeResult>('/auth/me')
}

export function refresh(): Promise<RefreshResult> {
  return post<RefreshResult>('/auth/refresh')
}

export function logout(): Promise<void> {
  // 主动登出时 40101 视为“已登出”，不触发全局登录失效跳转。
  return post<void>('/auth/logout', undefined, { skipAuthHandling: true })
}

export function changePassword(payload: ChangePasswordPayload): Promise<void> {
  return put<void>('/auth/password', payload)
}
