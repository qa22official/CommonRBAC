export const TOKEN_STORAGE_KEY = 'rbac.accessToken'

let sessionExpiredHandler: (() => void) | null = null
let expiredNotified = false

export function readStoredToken(): string {
  try {
    return window.localStorage.getItem(TOKEN_STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

export function writeStoredToken(token: string): void {
  try {
    if (token) {
      window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
      expiredNotified = false
    } else {
      window.localStorage.removeItem(TOKEN_STORAGE_KEY)
    }
  } catch {
    // localStorage 不可用时只保留内存态，不阻断流程。
  }
}

export function setSessionExpiredHandler(handler: () => void): void {
  sessionExpiredHandler = handler
}

/**
 * 令牌失效（40100/40101）统一入口：清除本地令牌，并通知应用跳转登录页。
 * 并发请求同时失败时只触发一次提示。
 */
export function notifySessionExpired(): void {
  writeStoredToken('')
  if (expiredNotified) {
    return
  }
  expiredNotified = true
  if (sessionExpiredHandler) {
    sessionExpiredHandler()
    return
  }
  window.location.assign('/login?reason=expired')
}
