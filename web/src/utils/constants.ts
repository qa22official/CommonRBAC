import type { AuditCategory } from '../types/audit'
import type { PermissionResourceType } from '../types/permission'

export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export interface SelectOption<T> {
  label: string
  value: T
}

export const STATUS_ENABLED = 1
export const STATUS_DISABLED = 0
export const STATUS_DELETED = 2

export const USER_STATUS_OPTIONS: Array<SelectOption<number>> = [
  { label: '启用', value: STATUS_ENABLED },
  { label: '停用', value: STATUS_DISABLED },
]

export const ROLE_STATUS_OPTIONS: Array<SelectOption<number>> = [
  { label: '启用', value: STATUS_ENABLED },
  { label: '停用', value: STATUS_DISABLED },
]

export function statusText(status: number): string {
  if (status === STATUS_ENABLED) {
    return '启用'
  }
  if (status === STATUS_DISABLED) {
    return '停用'
  }
  if (status === STATUS_DELETED) {
    return '已删除'
  }
  return `未知（${status}）`
}

export function statusTagType(status: number): TagType {
  if (status === STATUS_ENABLED) {
    return 'success'
  }
  if (status === STATUS_DISABLED) {
    return 'info'
  }
  return 'danger'
}

export const RESOURCE_TYPE_OPTIONS: Array<SelectOption<PermissionResourceType>> = [
  { label: '菜单', value: 'MENU' },
  { label: '按钮', value: 'BUTTON' },
  { label: '接口', value: 'API' },
  { label: '系统', value: 'SYSTEM' },
]

export function resourceTypeText(type: PermissionResourceType | string): string {
  switch (type) {
    case 'MENU':
      return '菜单'
    case 'BUTTON':
      return '按钮'
    case 'API':
      return '接口'
    case 'SYSTEM':
      return '系统'
    default:
      return type
  }
}

export function resourceTypeTagType(type: PermissionResourceType | string): TagType {
  switch (type) {
    case 'MENU':
      return 'primary'
    case 'BUTTON':
      return 'success'
    case 'API':
      return 'warning'
    case 'SYSTEM':
      return 'danger'
    default:
      return 'info'
  }
}

export const HTTP_METHOD_OPTIONS: Array<SelectOption<string>> = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
  { label: 'PATCH', value: 'PATCH' },
]

export const AUDIT_CATEGORY_OPTIONS: Array<SelectOption<AuditCategory>> = [
  { label: '变更事件（CHANGE）', value: 'CHANGE' },
  { label: '安全事件（SECURITY）', value: 'SECURITY' },
]

export const AUDIT_RESULT_OPTIONS: Array<SelectOption<number>> = [
  { label: '成功', value: 1 },
  { label: '失败', value: 0 },
]

export function auditCategoryText(category: AuditCategory): string {
  return category === 'CHANGE' ? '变更' : '安全'
}

export function auditCategoryTagType(category: AuditCategory): TagType {
  return category === 'CHANGE' ? 'primary' : 'warning'
}
