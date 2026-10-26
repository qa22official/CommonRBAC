/**
 * 前端权限匹配。
 *
 * 规则（docs/04 §7.2）：
 * - `*` 命中一切；
 * - 普通权限码只做精确匹配；
 * - 只有持有通配码（以 `*` 结尾）时才做字符串前缀匹配，
 *   例如 `sys:user:*` 命中 `sys:user:delete`。
 *
 * 禁止用角色编码做权限判断。
 */
export function matchesPermission(
  grants: readonly string[],
  required: string | null | undefined,
): boolean {
  if (!required) {
    return true
  }
  if (grants.includes('*') || grants.includes(required)) {
    return true
  }
  for (const grant of grants) {
    if (grant === '*' || !grant.endsWith('*')) {
      continue
    }
    const prefix = grant.slice(0, -1)
    if (prefix.length > 0 && required.startsWith(prefix)) {
      return true
    }
  }
  return false
}

export function matchesAnyPermission(
  grants: readonly string[],
  requiredCodes: readonly string[],
): boolean {
  if (requiredCodes.length === 0) {
    return true
  }
  return requiredCodes.some((code) => matchesPermission(grants, code))
}
