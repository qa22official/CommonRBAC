import { defineStore } from 'pinia'
import { fetchPermissionTree } from '@/api/permission'
import type { PermissionNode } from '@/types/permission'
import { useAuthStore } from './auth'

export interface MenuItem {
  code: string
  title: string
  path: string
  icon?: string
  children: MenuItem[]
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    menuItems: [] as MenuItem[],
    loaded: false,
  }),
  actions: {
    /** 拉取权限树并按用户权限过滤出菜单树 */
    async loadMenus(): Promise<MenuItem[]> {
      const auth = useAuthStore()
      const tree = await fetchPermissionTree()

      const build = (nodes: PermissionNode[]): MenuItem[] => {
        const items: MenuItem[] = []
        for (const node of nodes) {
          if (node.resourceType !== 'MENU') continue
          if (!auth.hasPermission(node.code)) continue
          items.push({
            code: node.code,
            title: node.name,
            path: node.path || '',
            icon: node.icon,
            children: build(node.children || []),
          })
        }
        return items
      }

      this.menuItems = build(tree)
      this.loaded = true
      return this.menuItems
    },

    reset() {
      this.menuItems = []
      this.loaded = false
    },
  },
})
