import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'home', component: () => import('@/views/user/HomeView.vue') },
      { path: 'assets', name: 'assets', component: () => import('@/views/user/AssetView.vue') },
      { path: 'contacts', name: 'contacts', component: () => import('@/views/user/ContactView.vue') },
      { path: 'heartbeat', name: 'heartbeat', component: () => import('@/views/user/HeartbeatView.vue') },
      { path: 'cases', name: 'cases', component: () => import('@/views/user/CaseListView.vue') },
      {
        path: 'cases/:id',
        name: 'case-detail',
        component: () => import('@/views/user/CaseDetailView.vue')
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/user/NotificationView.vue')
      },
      { path: 'audit', name: 'audit', component: () => import('@/views/user/AuditView.vue') }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/cases',
    meta: { admin: true },
    children: [
      {
        path: 'cases',
        name: 'admin-cases',
        component: () => import('@/views/admin/AdminCaseView.vue')
      },
      {
        path: 'cases/:id',
        name: 'admin-case-detail',
        component: () => import('@/views/admin/AdminCaseDetailView.vue')
      },
      {
        path: 'heartbeat',
        name: 'admin-heartbeat',
        component: () => import('@/views/admin/AdminHeartbeatView.vue')
      },
      { path: 'jobs', name: 'admin-jobs', component: () => import('@/views/admin/AdminJobView.vue') },
      { path: 'audit', name: 'admin-audit', component: () => import('@/views/admin/AdminAuditView.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 路由守卫。
 *
 * 这里只做"体验层"的拦截：没登录就去登录页，非管理员不显示管理端。
 * 真正的权限校验在后端 —— 即使有人手动改 URL 进来，接口也会返回 403。
 */
router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    return true
  }

  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.admin && !auth.isAdmin) {
    return { name: 'home' }
  }

  return true
})

export default router
