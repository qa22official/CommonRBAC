import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
} from 'axios'
import { ApiError } from '@/types/api'
import { getToken, clearToken } from '@/utils/token'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截：附加 Bearer 令牌
service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一解包 { code, message, data, traceId }
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const body = response.data
    // 统一响应体判断
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      return Promise.reject(
        new ApiError(body.code as number, body.message || '请求失败', body.traceId),
      )
    }
    return body
  },
  (error) => {
    const status = error.response?.status as number | undefined
    const data = error.response?.data as
      | { code?: number; message?: string; traceId?: string }
      | undefined
    // 优先取响应体里的业务错误码；401 兜底视为令牌失效
    const code = data?.code ?? (status === 401 ? 40100 : status ?? -1)
    const message = data?.message ?? error.message ?? '网络异常，请稍后重试'
    const traceId = data?.traceId

    // 令牌无效 / 已被作废：清除本地令牌并回登录页
    if (code === 40100 || code === 40101) {
      clearToken()
      if (window.location.pathname !== '/login') {
        window.location.replace('/login')
      }
    }

    return Promise.reject(new ApiError(code, message, traceId))
  },
)

// 类型化请求助手：拦截器已把 data 解包，这里做类型收窄
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return service.get(url, config) as unknown as Promise<T>
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return service.post(url, data, config) as unknown as Promise<T>
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return service.put(url, data, config) as unknown as Promise<T>
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return service.delete(url, config) as unknown as Promise<T>
}

export default service
