/**
 * 统一响应体，与 docs/04-接口设计.md §1.2 保持一致。
 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string | null
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface PageQuery {
  page: number
  size: number
}
