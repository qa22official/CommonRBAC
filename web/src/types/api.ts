/** 统一响应体：{ code, message, data, traceId } */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
}

/** 浅翻页分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 浅翻页查询参数 */
export interface PageQuery {
  page: number
  size: number
}

/** 业务错误（由 axios 拦截器统一抛出） */
export class ApiError extends Error {
  code: number
  traceId?: string

  constructor(code: number, message: string, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
  }
}
