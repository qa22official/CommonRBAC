export const PASSWORD_RULE_TEXT = '密码需至少 8 位，且包含大写字母、小写字母与数字'

export interface PasswordStrength {
  lengthOk: boolean
  lowerOk: boolean
  upperOk: boolean
  digitOk: boolean
  valid: boolean
  percent: number
  label: string
}

export function evaluatePassword(value: string): PasswordStrength {
  const lengthOk = value.length >= 8
  const lowerOk = /[a-z]/.test(value)
  const upperOk = /[A-Z]/.test(value)
  const digitOk = /\d/.test(value)
  const passed = [lengthOk, lowerOk, upperOk, digitOk].filter(Boolean).length
  const valid = lengthOk && lowerOk && upperOk && digitOk

  let label = '未输入'
  if (value.length > 0) {
    if (passed <= 2) {
      label = '弱'
    } else if (passed === 3) {
      label = '中'
    } else {
      label = '强'
    }
  }

  return {
    lengthOk,
    lowerOk,
    upperOk,
    digitOk,
    valid,
    percent: passed * 25,
    label,
  }
}
