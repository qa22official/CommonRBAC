import axios, {
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import { notifySessionExpired, readStoredToken } from '../utils/auth-session'
import { startRateLimitCountdown } from '../stores/rate-limit'

interface RawEnvelope {
  code: number
  message?: string
  data?: unknown
  traceId?: string | null
}

interface ApiErrorOptions {
  code: number
  httpStatus: number | null
  traceId: string | null
  retryAfterSeconds: number | null
}

export class ApiError extends Error {
  readonly code: number

  readonly httpStatus: number | null

  readonly traceId: string | null

  readonly retryAfterSeconds: number | null

  constructor(message: string, options: ApiErrorOptions) {
    super(message)
    this.name = 'ApiError'
    this.code = options.code
    this.httpStatus = options.httpStatus
    this.traceId = options.traceId
    this.retryAfterSeconds = options.retryAfterSeconds
  }
}

const LOGIN_ERROR_CODES = new Set([40110, 40111, 40112])

function isApiEnvelope(value: unknown): value is RawEnvelope {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as { code?: unknown }
  return typeof candidate.code === 'number'
}

function readHeader(headers: unknown, name: string): string | null {
  if (typeof headers !== 'object' || headers === null) {
    return null
  }
  const value = (headers as Record<string, unknown>)[name]
  if (typeof value === 'string') {
    return value
  }
  if (Array.isArray(value)) {
    return value.map((item) => String(item)).join(',')
  }
  if (typeof value === 'number') {
    return String(value)
  }
  return null
}

function parseRetryAfter(headers: unknown): number | null {
  const raw = readHeader(headers, 'retry-after')
  if (!raw) {
    return null
  }
  const seconds = Number.parseInt(raw, 10)
  if (!Number.isNaN(seconds)) {
    return Math.max(1, seconds)
  }
  const date = new Date(raw)
  if (!Number.isNaN(date.getTime())) {
    return Math.max(1, Math.ceil((date.getTime() - Date.now()) / 1000))
  }
  return null
}

function statusToCode(status: number | null): number {
  if (status === null) {
    return -1
  }
  if (status === 400) {
    return 40000
  }
  if (status === 401) {
    return 40100
  }
  if (status === 403) {
    return 40300
  }
  if (status === 404) {
    return 40400
  }
  if (status === 409) {
    return 40900
  }
  if (status === 429) {
    return 42900
  }
  if (status === 503) {
    return 50300
  }
  if (status >= 500) {
    return 50000
  }
  return -1
}

function defaultMessage(code: number, status: number | null): string {
  if (code === 40000) {
    return '参数校验失败'
  }
  if (code === 40100 || code === 40101) {
    return '登录状态已失效'
  }
  if (code === 40300) {
    return '无操作权限'
  }
  if (code === 40400) {
    return '资源不存在'
  }
  if (code === 40900) {
    return '数据冲突，请刷新后重试'
  }
  if (code === 40950) {
    return '数据已被他人修改'
  }
  if (code === 42900) {
    return '操作过于频繁，请稍后重试'
  }
  if (code === 50000) {
    return '系统繁忙'
  }
  if (code === 50300) {
    return '系统暂时不可用，请稍后重试'
  }
  if (status !== null) {
    return `请求失败（HTTP ${status}）`
  }
  return '网络连接失败，请检查网络或后端服务是否已启动'
}

function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }
  if (axios.isAxiosError(error)) {
    const response = error.response
    const status = response?.status ?? null
    const body: unknown = response?.data
    const envelope = isApiEnvelope(body) ? body : null
    const code = envelope?.code ?? statusToCode(status)
    const message = envelope?.message?.trim() || defaultMessage(code, status)
    const bodyTrace = envelope?.traceId ?? null
    const headerTrace = readHeader(response?.headers, 'x-trace-id')
    return new ApiError(message, {
      code,
      httpStatus: status,
      traceId: bodyTrace ?? headerTrace,
      retryAfterSeconds: parseRetryAfter(response?.headers),
    })
  }
  if (error instanceof Error) {
    return new ApiError(error.message, {
      code: -1,
      httpStatus: null,
      traceId: null,
      retryAfterSeconds: null,
    })
  }
  return new ApiError('未知错误', {
    code: -1,
    httpStatus: null,
    traceId: null,
    retryAfterSeconds: null,
  })
}

function handleApiError(error: ApiError, config?: InternalAxiosRequestConfig): void {
  const isAuthFailure =
    error.code === 40100 ||
    error.code === 40101 ||
    (error.httpStatus === 401 && !LOGIN_ERROR_CODES.has(error.code))

  if (isAuthFailure) {
    if (config?.skipAuthHandling === true) {
      return
    }
    notifySessionExpired()
    ElMessage.error('登录状态已失效')
    return
  }

  if (error.code === 40300) {
    ElMessage.error('无操作权限')
    return
  }

  if (error.code >= 40300 && error.code < 40400) {
    ElMessage.error(error.message)
    return
  }

  if (error.code === 40950) {
    ElMessage.error('数据已被他人修改，请刷新后重试')
    return
  }

  if (error.code >= 40900 && error.code < 41000) {
    ElMessage.error(error.message)
    return
  }

  if (error.code === 42900) {
    const seconds = error.retryAfterSeconds ?? 60
    startRateLimitCountdown(seconds)
    ElMessage.warning(`操作过于频繁，请 ${seconds} 秒后重试`)
    return
  }

  if (error.code === 50300) {
    ElMessage.error('系统暂时不可用，请稍后重试')
    return
  }

  if (error.code === 50000 || (error.httpStatus !== null && error.httpStatus >= 500)) {
    const suffix = error.traceId ? `（traceId：${error.traceId}）` : ''
    ElMessage.error(`系统繁忙${suffix}`)
    return
  }

  if (error.code === 40400) {
    ElMessage.error(error.message)
    return
  }

  if (error.code === 40110 || error.code === 40111 || error.code === 40112) {
    ElMessage.error(error.message)
    return
  }

  if (error.code >= 40000 && error.code < 40500) {
    ElMessage.error(error.message)
    return
  }

  ElMessage.error(error.message)
}

export function createTraceId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`
}

export function isVersionConflict(error: unknown): boolean {
  return error instanceof ApiError && error.code === 40950
}

const http = axios.create({
  baseURL: '/api',
  timeout: 20000,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = readStoredToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  config.headers.set('X-Trace-Id', createTraceId())
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload: unknown = response.data
    if (isApiEnvelope(payload) && payload.code !== 0) {
      const apiError = new ApiError(payload.message?.trim() || defaultMessage(payload.code, null), {
        code: payload.code,
        httpStatus: response.status,
        traceId: payload.traceId ?? readHeader(response.headers, 'x-trace-id'),
        retryAfterSeconds: parseRetryAfter(response.headers),
      })
      handleApiError(apiError, response.config)
      return Promise.reject(apiError)
    }
    return response
  },
  (error: unknown) => {
    const apiError = toApiError(error)
    const config = axios.isAxiosError(error) ? error.config : undefined
    handleApiError(apiError, config)
    return Promise.reject(apiError)
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<unknown>(config)
  const payload: unknown = response.data
  if (isApiEnvelope(payload)) {
    return payload.data as T
  }
  return payload as T
}

export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ ...config, method: 'GET', url })
}

export function post<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ ...config, method: 'POST', url, data })
}

export function put<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ ...config, method: 'PUT', url, data })
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ ...config, method: 'DELETE', url })
}
