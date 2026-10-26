import 'axios'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /**
     * 为 true 时，40100 / 40101 不触发全局“登录状态已失效”处理，
     * 用于登录、主动登出等调用方自行处理错误的场景。
     */
    skipAuthHandling?: boolean
  }

  export interface InternalAxiosRequestConfig {
    skipAuthHandling?: boolean
  }
}
