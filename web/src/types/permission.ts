export type PermissionResourceType = 'MENU' | 'BUTTON' | 'API' | 'SYSTEM'

export interface PermissionTreeNode {
  id: number
  code: string
  name: string
  resourceType: PermissionResourceType
  parentId: number | null
  path: string | null
  httpMethod: string | null
  icon: string | null
  status: number
  sort: number
  children: PermissionTreeNode[]
}

export interface PermissionDetail {
  id: number
  code: string
  name: string
  resourceType: PermissionResourceType
  parentId: number | null
  path: string | null
  httpMethod: string | null
  icon: string | null
  status: number
  sort: number
  builtin: boolean
}

export interface CreatePermissionPayload {
  code: string
  name: string
  resourceType: PermissionResourceType
  parentId?: number | null
  path?: string | null
  httpMethod?: string | null
  icon?: string | null
  sort?: number
}

export interface UpdatePermissionPayload {
  name: string
  resourceType: PermissionResourceType
  parentId?: number | null
  path?: string | null
  httpMethod?: string | null
  icon?: string | null
  sort?: number
  status?: number
}
