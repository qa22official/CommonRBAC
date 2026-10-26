import type { Directive } from 'vue'
import { useAuthStore } from '../stores/auth'

type PermissionValue = string | string[] | null | undefined

function isAllowed(value: PermissionValue): boolean {
  if (value === null || value === undefined) {
    return true
  }
  const codes = Array.isArray(value) ? value : [value]
  if (codes.length === 0) {
    return true
  }
  return useAuthStore().hasAnyPermission(codes)
}

function applyPermission(el: HTMLElement, value: PermissionValue): void {
  if (isAllowed(value)) {
    return
  }
  // 无权限直接移除 DOM，而不是 display:none。
  el.parentNode?.removeChild(el)
}

/**
 * v-perm="'sys:user:create'" 或 v-perm="['sys:user:create', 'sys:user:update']"
 * 数组语义为“任一权限满足”；通配码规则与 auth store 一致。
 */
export const permissionDirective: Directive<HTMLElement, PermissionValue> = {
  mounted(el, binding) {
    applyPermission(el, binding.value)
  },
  updated(el, binding) {
    if (binding.value !== binding.oldValue) {
      applyPermission(el, binding.value)
    }
  },
}
