import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 按钮级权限指令：v-perm="'sys:user:create'"
 * 无权限时移除元素。
 */
export const permissionDirective: Directive<HTMLElement, string> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string>) {
    const auth = useAuthStore()
    const code = binding.value
    if (code && !auth.hasPermission(code)) {
      el.parentNode?.removeChild(el)
    }
  },
}
