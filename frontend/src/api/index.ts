import http, { request, type PageResult } from './http'
import type {
  ApplicableAssets,
  Asset,
  AssetContent,
  AuditLog,
  CaseDetail,
  CaseSummary,
  ContactRelation,
  HeartbeatRecord,
  HeartbeatStatus,
  JobExecution,
  LoginResponse,
  NotificationItem,
  OutboxEvent,
  UserProfile
} from './types'

// ==================== 认证 ====================

export const authApi = {
  register(body: { username: string; password: string; displayName: string; email?: string }) {
    return request<UserProfile>(http.post('/auth/register', body))
  },

  login(body: { username: string; password: string }) {
    return request<LoginResponse>(http.post('/auth/login', body))
  },

  logout() {
    return request<void>(http.post('/auth/logout'))
  },

  me() {
    return request<UserProfile>(http.get('/users/me'))
  },

  updateProfile(body: { displayName?: string; email?: string; phone?: string }) {
    return request<UserProfile>(http.put('/users/me', body))
  }
}

// ==================== 资产 ====================

export const assetApi = {
  list(page = 1, size = 20) {
    return request<PageResult<Asset>>(http.get('/assets', { params: { page, size } }))
  },

  create(body: {
    name: string
    assetType: string
    description?: string
    content: string
    fileName?: string
    fileSize?: number
    mimeType?: string
  }) {
    return request<Asset>(http.post('/assets', body))
  },

  detail(id: number) {
    return request<Asset>(http.get(`/assets/${id}`))
  },

  /** 读取明文：系统里唯一会返回解密内容的接口 */
  content(id: number) {
    return request<AssetContent>(http.get(`/assets/${id}/content`))
  },

  update(id: number, body: Record<string, unknown>) {
    return request<Asset>(http.put(`/assets/${id}`, body))
  },

  remove(id: number) {
    return request<void>(http.delete(`/assets/${id}`))
  }
}

// ==================== 联系人 ====================

export const contactApi = {
  list() {
    return request<ContactRelation[]>(http.get('/contacts'))
  },

  invite(body: { username: string; note?: string }) {
    return request<ContactRelation>(http.post('/contacts/invitations', body))
  },

  accept(id: number) {
    return request<void>(http.post(`/contacts/invitations/${id}/accept`))
  },

  reject(id: number) {
    return request<void>(http.post(`/contacts/invitations/${id}/reject`))
  },

  revoke(id: number) {
    return request<void>(http.delete(`/contacts/${id}`))
  }
}

// ==================== 状态确认 ====================

export const heartbeatApi = {
  status() {
    return request<HeartbeatStatus>(http.get('/heartbeat/status'))
  },

  updateConfig(body: { cycleDays: number; reminderRounds: number; reminderIntervalHours: number }) {
    return request<HeartbeatStatus>(http.put('/heartbeat/config', body))
  },

  confirm() {
    return request<HeartbeatStatus>(http.post('/heartbeat/confirm'))
  },

  records(page = 1, size = 20) {
    return request<PageResult<HeartbeatRecord>>(http.get('/heartbeat/records', { params: { page, size } }))
  }
}

// ==================== 授权案件 ====================

export const caseApi = {
  /** 申请前查询：这条关系能否申请、该所有者有哪些资产可选 */
  applicableAssets(contactId: number) {
    return request<ApplicableAssets>(
      http.get('/authorizations/applicable-assets', { params: { contactId } })
    )
  },

  create(body: { contactId: number; assetIds: number[]; reason?: string }) {
    return request<CaseDetail>(http.post('/authorizations', body))
  },

  list(page = 1, size = 20) {
    return request<PageResult<CaseSummary>>(http.get('/authorizations', { params: { page, size } }))
  },

  detail(id: number) {
    return request<CaseDetail>(http.get(`/authorizations/${id}`))
  },

  submit(id: number) {
    return request<void>(http.post(`/authorizations/${id}/submit`))
  },

  cancel(id: number, reason?: string) {
    return request<void>(http.post(`/authorizations/${id}/cancel`, { reason }))
  },

  dispute(id: number, reason: string) {
    return request<void>(http.post(`/authorizations/${id}/disputes`, { reason }))
  },

  revoke(id: number) {
    return request<void>(http.post(`/authorizations/${id}/revoke`))
  }
}

// ==================== 通知 ====================

export const notificationApi = {
  list(page = 1, size = 20, unreadOnly = false) {
    return request<PageResult<NotificationItem>>(
      http.get('/notifications', { params: { page, size, unreadOnly } })
    )
  },

  unreadCount() {
    return request<{ count: number }>(http.get('/notifications/unread-count'))
  },

  markRead(id: number) {
    return request<void>(http.post(`/notifications/${id}/read`))
  },

  markAllRead() {
    return request<{ updated: number }>(http.post('/notifications/read-all'))
  }
}

// ==================== 审计 ====================

export const auditApi = {
  query(params: {
    page?: number
    size?: number
    action?: string
    objectType?: string
    objectId?: number
  }) {
    return request<PageResult<AuditLog>>(http.get('/audit/logs', { params }))
  }
}

// ==================== 管理端 ====================

export const adminApi = {
  pendingCases(page = 1, size = 20) {
    return request<PageResult<CaseSummary>>(
      http.get('/admin/authorizations/pending', { params: { page, size } })
    )
  },

  caseDetail(id: number) {
    return request<CaseDetail>(http.get(`/admin/authorizations/${id}`))
  },

  acceptCase(id: number) {
    return request<void>(http.post(`/admin/authorizations/${id}/accept`))
  },

  approveCase(id: number, comment: string) {
    return request<void>(http.post(`/admin/authorizations/${id}/approve`, { comment }))
  },

  rejectCase(id: number, comment: string) {
    return request<void>(http.post(`/admin/authorizations/${id}/reject`, { comment }))
  },

  resolveDispute(id: number, result: 'UPHELD' | 'OVERTURNED', comment: string) {
    return request<void>(
      http.post(`/admin/authorizations/${id}/resolve-dispute`, { result, comment })
    )
  },

  scanExpired() {
    return request<{ authorized: number }>(http.post('/admin/authorizations/scan-expired'))
  },

  verifyQueue(page = 1, size = 20) {
    return request<HeartbeatStatus[]>(
      http.get('/admin/heartbeat/verify-queue', { params: { page, size } })
    )
  },

  verifyHeartbeat(userId: number) {
    return request<void>(http.post(`/admin/heartbeat/${userId}/verify`))
  },

  jobExecutions(page = 1, size = 20, jobName?: string) {
    return request<PageResult<JobExecution>>(
      http.get('/admin/jobs/executions', { params: { page, size, jobName } })
    )
  },

  outboxSummary() {
    return request<{ pending: number; failed: number }>(http.get('/admin/jobs/outbox/summary'))
  },

  failedEvents(page = 1, size = 20) {
    return request<PageResult<OutboxEvent>>(
      http.get('/admin/jobs/outbox/failed', { params: { page, size } })
    )
  },

  retryEvent(id: number) {
    return request<void>(http.post(`/admin/jobs/outbox/${id}/retry`))
  },

  publishNow() {
    return request<{ published: number }>(http.post('/admin/jobs/outbox/publish-now'))
  }
}
