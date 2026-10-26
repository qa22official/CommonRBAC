import type { Router } from 'vue-router'
import { businessRoutes } from './routes'
import { matchesPermission } from '../utils/permission'

let routerRef: Router | null = null
let installed = false
const installedNames: string[] = []

export function setRouter(instance: Router): void {
  routerRef = instance
}

export function areBusinessRoutesInstalled(): boolean {
  return installed
}

/**
 * 按 /api/auth/me 返回的 permissions 注册业务路由。
 * 未授权路由不注册，访问时由 catch-all 路由呈现 404。
 */
export function installBusinessRoutes(permissions: readonly string[]): void {
  const router = routerRef
  if (!router) {
    return
  }
  resetBusinessRoutes()
  for (const route of businessRoutes) {
    const menuCode = route.meta?.menuCode
    if (menuCode && !matchesPermission(permissions, menuCode)) {
      continue
    }
    router.addRoute('root', route)
    if (typeof route.name === 'string') {
      installedNames.push(route.name)
    }
  }
  installed = true
}

export function resetBusinessRoutes(): void {
  const router = routerRef
  if (router) {
    for (const name of installedNames) {
      if (router.hasRoute(name)) {
        router.removeRoute(name)
      }
    }
  }
  installedNames.length = 0
  installed = false
}
