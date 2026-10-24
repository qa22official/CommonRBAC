/** 角色记录 */
export interface RoleRecord {
  id: number
  code: string
  name: string
  description?: string
  inheritRoleId: number
  status: number
  builtin: boolean
  permVersion: number
  sort: number
  userCount?: number
  permissionCount?: number
  createdAt: string
  updatedAt: string
}

/** 新增 / 编辑角色表单 */
export interface RoleForm {
  code: string
  name: string
  description?: string
  inheritRoleId: number
  status: number
  sort: number
}

/** 下拉选项 */
export interface RoleOption {
  id: number
  code: string
  name: string
}

/** 查询角色已授权限 */
export interface RolePermissionsResult {
  permissionCodes: string[]
  version: number
}

/** 覆盖式设置角色权限 */
export interface AssignPermissionsRequest {
  permissionCodes: string[]
  expectedVersion: number
}
