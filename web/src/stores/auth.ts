import { defineStore } from 'pinia'
import {
  login as apiLogin,
  fetchMe as apiFetchMe,
  logout as apiLogout,
} from '@/api/auth'
import type { LoginRequest, UserBrief, RoleBrief } from '@/types/auth'
import { getToken, setToken, clearToken } from '@/utils/token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken() || '',
    user: null as UserBrief | null,
    permissions: [] as string[],
    roles: [] as RoleBrief[],
    loaded: false,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
  },
  actions: {
    async login(data: LoginRequest) {
      const result = await apiLogin(data)
      setToken(result.accessToken)
      this.token = result.accessToken
      this.user = result.user
      await this.fetchMe()
      return result
    },

    async fetchMe() {
      const me = await apiFetchMe()
      this.user = me.user
      this.permissions = me.permissions || []
      this.roles = me.roles || []
      this.loaded = true
      return me
    },

    async logout() {
      try {
        await apiLogout()
      } catch {
        // 登出接口失败也继续本地清理
      }
      this.reset()
    },

    reset() {
      clearToken()
      this.token = ''
      this.user = null
      this.permissions = []
      this.roles = []
      this.loaded = false
    },

    /**
     * 权限判定：`*` 命中一切；`sys:user:*` 前缀通配；
     * 普通权限码走精确匹配。仅依赖 /auth/me 返回的 permissions。
     */
    hasPermission(code: string): boolean {
      if (!code) return false
      const perms = this.permissions
      if (perms.includes('*')) return true
      if (perms.includes(code)) return true
      return perms.some((p) => p.endsWith('*') && code.startsWith(p.slice(0, -1)))
    },
  },
})
