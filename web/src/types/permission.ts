export type ResourceType = 'MENU' | 'BUTTON' | 'API' | 'SYSTEM'

/** 权限树节点 */
export interface PermissionNode {
  id: number
  code: string
  name: string
  resourceType: ResourceType
  parentId: number
  path?: string
  httpMethod?: string
  icon?: string
  status: number
  builtin: boolean
  sort: number
  children: PermissionNode[]
  createdAt: string
  updatedAt: string
}

/** 新增 / 编辑权限表单 */
export interface PermissionForm {
  code: string
  name: string
  resourceType: ResourceType
  parentId: number
  path?: string
  httpMethod?: string
  icon?: string
  sort: number
  status: number
}
