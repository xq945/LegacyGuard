import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api'
import { TOKEN_KEY, USER_KEY } from '@/api/http'
import type { UserProfile } from '@/api/types'

/**
 * 登录态。
 *
 * 只保存令牌与用户资料用于界面展示；**任何权限判断的真实依据都在后端**。
 * 这里的 isAdmin 只决定"要不要显示管理入口"，不决定"能不能调用管理接口" ——
 * 后者由服务端的角色校验兜底。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<UserProfile | null>(readStoredUser())

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const displayName = computed(() => user.value?.displayName || user.value?.username || '')

  function readStoredUser(): UserProfile | null {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as UserProfile
    } catch {
      return null
    }
  }

  function persist(nextToken: string, profile: UserProfile) {
    token.value = nextToken
    user.value = profile
    localStorage.setItem(TOKEN_KEY, nextToken)
    localStorage.setItem(USER_KEY, JSON.stringify(profile))
  }

  async function login(username: string, password: string) {
    const result = await authApi.login({ username, password })
    persist(result.accessToken, result.user)
    return result.user
  }

  async function register(body: {
    username: string
    password: string
    displayName: string
    email?: string
  }) {
    return authApi.register(body)
  }

  async function refresh() {
    const profile = await authApi.me()
    user.value = profile
    localStorage.setItem(USER_KEY, JSON.stringify(profile))
    return profile
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 登出接口失败（例如网络问题）也要清掉本地状态，
      // 否则用户会卡在"看起来还登录着但什么都做不了"的状态
    } finally {
      token.value = ''
      user.value = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }

  return { token, user, isLoggedIn, isAdmin, displayName, login, register, refresh, logout }
})
