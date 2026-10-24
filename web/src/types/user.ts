/** 用户列表 / 详情记录 */
export interface UserRecord {
  id: number
  username: string
  nickname: string
  email?: string
  phone?: string
  status: number
  remark?: string
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
}

/** 新增 / 编辑用户表单 */
export interface UserForm {
  username: string
  nickname: string
  email?: string
  phone?: string
  status: number
  remark?: string
}

/** 查询用户已分配角色 */
export interface UserRolesResult {
  roleIds: number[]
  version: number
}

/** 覆盖式设置用户角色 */
export interface AssignRolesRequest {
  roleIds: number[]
  expectedVersion: number
}
