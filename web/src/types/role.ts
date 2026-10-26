export interface RoleListItem {
  id: number
  code: string
  name: string
  description: string | null
  status: number
  builtin: boolean
  sort: number
  inheritRoleId: number | null
  inheritRoleName: string | null
}

export interface RoleDetail extends RoleListItem {
  permVersion: number
  userCount: number
  permissionCount: number
}

export interface RoleTreeNode extends RoleListItem {
  children: RoleTreeNode[]
}

export interface RoleQuery {
  page: number
  size: number
  keyword?: string
  status?: number
}

export interface CreateRolePayload {
  code: string
  name: string
  description?: string | null
  sort?: number
  inheritRoleId?: number | null
}

export interface UpdateRolePayload {
  name: string
  description?: string | null
  sort?: number
  inheritRoleId?: number | null
}

export interface RolePermissions {
  permissionCodes: string[]
  version: number
}

export interface AssignPermissionsPayload {
  permissionCodes: string[]
  expectedVersion: number
}

export interface ChangeRoleStatusPayload {
  status: number
}
