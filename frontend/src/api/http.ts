import axios, { type AxiosInstance, type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应结构 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string | null
}

/** 分页响应 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export const TOKEN_KEY = 'lg_token'
export const USER_KEY = 'lg_user'

/**
 * 全局 HTTP 客户端。
 *
 * 约定：
 * - 令牌放在 Authorization 头里，由请求拦截器统一注入
 * - 响应体的 code 才是业务结果，HTTP 状态码只用来区分粗粒度错误类别
 * - 401 统一跳登录；其余错误弹一次提示并把异常继续抛给调用方
 */
const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 避免同一时刻大量请求同时 401 时弹出多个提示 */
let redirecting = false

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    const body = error.response?.data
    const message = body?.message || error.message || '请求失败'
    const requestId = body?.requestId

    if (status === 401) {
      // 令牌失效或未登录：清掉本地状态并回到登录页
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      if (!redirecting && !location.hash.startsWith('#/login')) {
        redirecting = true
        ElMessage.warning('登录已失效，请重新登录')
        setTimeout(() => {
          location.hash = '#/login'
          redirecting = false
        }, 300)
      }
    } else if (status === 403) {
      ElMessage.error(message || '无权访问')
    } else if (status && status >= 500) {
      ElMessage.error(requestId ? `${message}（可凭 ${requestId} 排查）` : message)
    } else if (message) {
      ElMessage.error(message)
    }

    return Promise.reject(error)
  }
)

/** 取出成功响应的 data 字段 */
export async function request<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await promise
  return response.data.data
}

export default http
