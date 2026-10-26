function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return [
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`,
  ].join(' ')
}

export function formatDateTimeWithOffset(value: Date | null): string | null {
  if (!value || Number.isNaN(value.getTime())) {
    return null
  }
  return value.toISOString()
}

export function formatJson(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  try {
    const parsed: unknown = JSON.parse(value)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return value
  }
}

export function formatOperator(
  id: number | null,
  name: string | null,
): string {
  if (name && id !== null) {
    return `${name}（#${id}）`
  }
  if (name) {
    return name
  }
  if (id !== null) {
    return `#${id}`
  }
  return '-'
}

export function formatTarget(
  type: string | null,
  id: number | null,
): string {
  if (type && id !== null) {
    return `${type} #${id}`
  }
  if (type) {
    return type
  }
  if (id !== null) {
    return `#${id}`
  }
  return '-'
}

export function trimToNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed.length === 0 ? null : trimmed
}
