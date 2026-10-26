import { defineStore } from 'pinia'
import {
  getMe as getMeApi,
  login as loginApi,
  logout as logoutApi,
  refresh as refreshApi,
} from '../api/auth'
import type { AuthUser, LoginResult, MeResult, RoleSummary } from '../types/auth'
import {
  readStoredToken,
  writeStoredToken,
} from '../utils/auth-session'
import { matchesAnyPermission, matchesPermission } from '../utils/permission'

interface AuthState {
  token: string
  user: AuthUser | null
  permissions: string[]
  roles: RoleSummary[]
  mustChangePassword: boolean
  expiresAt: number | null
}

let refreshTimer: ReturnType<typeof setTimeout> | null = null

function decodeTokenExpiry(token: string): number | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) {
      return null
    }
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const claims = JSON.parse(atob(padded)) as { exp?: unknown }
    return typeof claims.exp === 'number' ? claims.exp * 1000 : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => {
    const token = readStoredToken()
    return {
      token,
      user: null,
      permissions: [],
      roles: [],
      mustChangePassword: false,
      expiresAt: token ? decodeTokenExpiry(token) : null,
    }
  },

  getters: {
    isAuthenticated: (state): boolean => state.token.length > 0,
    displayName: (state): string =>
      state.user?.nickname || state.user?.username || '未登录',
  },

  actions: {
    async login(username: string, password: string): Promise<LoginResult> {
      const result = await loginApi({ username, password })
      this.token = result.accessToken
      writeStoredToken(result.accessToken)
      this.expiresAt = Date.now() + result.expiresIn * 1000
      this.user = result.user
      this.mustChangePassword = result.mustChangePassword
      try {
        await this.fetchMe()
      } catch (error) {
        this.clearAuth()
        throw error
      }
      this.scheduleRefresh()
      return result
    },

    async fetchMe(): Promise<MeResult> {
      const me = await getMeApi()
      this.user = me.user
      this.permissions = me.permissions ?? []
      this.roles = me.roles ?? []
      this.mustChangePassword = me.mustChangePassword
      if (this.expiresAt === null) {
        this.expiresAt = decodeTokenExpiry(this.token)
      }
      this.scheduleRefresh()
      return me
    },

    /**
     * 访问令牌有效期 2 小时（ADR/Q-3）。在到期前 60 秒续期，避免用户因
     * 长时间停留页面而在下一次操作时直接跳登录页；续期失败交给 401 拦截器。
     */
    async refreshAccessToken(): Promise<void> {
      if (!this.token) {
        return
      }
      try {
        const result = await refreshApi()
        this.token = result.accessToken
        writeStoredToken(result.accessToken)
        this.expiresAt = Date.now() + result.expiresIn * 1000
        this.scheduleRefresh()
      } catch {
        // 40100/40101 已由拦截器清理令牌；网络异常时稍后重试，避免静默过期。
        if (this.token) {
          refreshTimer = setTimeout(() => {
            void this.refreshAccessToken()
          }, 30_000)
        }
      }
    },

    scheduleRefresh(): void {
      if (refreshTimer) {
        clearTimeout(refreshTimer)
        refreshTimer = null
      }
      if (!this.token || this.expiresAt === null) {
        return
      }
      const remaining = this.expiresAt - Date.now()
      if (remaining <= 0) {
        return
      }
      const delay = Math.max(1_000, remaining - 60_000)
      refreshTimer = setTimeout(() => {
        void this.refreshAccessToken()
      }, Math.min(delay, 2_147_000_000))
    },

    async logout(): Promise<void> {
      if (this.token.length > 0) {
        try {
          await logoutApi()
        } catch {
          // 40101 表示令牌已作废，按已登出处理；网络异常同样不应阻止本地清理。
        }
      }
      this.clearAuth()
    },

    clearAuth(): void {
      if (refreshTimer) {
        clearTimeout(refreshTimer)
        refreshTimer = null
      }
      this.token = ''
      this.user = null
      this.permissions = []
      this.roles = []
      this.mustChangePassword = false
      this.expiresAt = null
      writeStoredToken('')
    },

    /**
     * 权限判断唯一入口。只读取 /api/auth/me 返回的 permissions，
     * 禁止使用角色编码。
     */
    hasPermission(code: string | null | undefined): boolean {
      return matchesPermission(this.permissions, code)
    },

    hasAnyPermission(codes: readonly string[]): boolean {
      return matchesAnyPermission(this.permissions, codes)
    },
  },
})
