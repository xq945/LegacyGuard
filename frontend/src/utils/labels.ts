import type { CaseState, ContactStatus, HeartbeatState } from '@/api/types'

/** Element Plus 标签可接受的颜色类型 */
export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/**
 * 状态 → 中文文案与配色。
 *
 * 映射表本身是内部实现，对外暴露的是 `caseLabel(status)` 这类函数 ——
 * 因为接口返回的状态在 TypeScript 看来只是 string（表格插槽里更是 any），
 * 直接用 `映射表[row.status]` 会触发索引类型错误；封装成函数后既能兜底未知值，
 * 也保留了调用处的类型安全。
 */

const CASE_LABEL: Record<CaseState, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '待审核',
  REVIEWING: '审核中',
  WAITING_PERIOD: '等待期',
  AUTHORIZED: '已授权',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  DISPUTED: '异议中',
  REVOKED: '已撤销'
}

const CASE_TYPE: Record<CaseState, TagType> = {
  DRAFT: 'info',
  PENDING_REVIEW: 'warning',
  REVIEWING: 'warning',
  WAITING_PERIOD: 'warning',
  AUTHORIZED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'info',
  DISPUTED: 'danger',
  REVOKED: 'info'
}

const HEARTBEAT_LABEL_MAP: Record<HeartbeatState, string> = {
  ACTIVE: '正常',
  REMINDING: '提醒中',
  VERIFYING: '待核验',
  REVIEWING: '失联已核验',
  SUSPENDED: '已冻结'
}

const HEARTBEAT_TYPE_MAP: Record<HeartbeatState, TagType> = {
  ACTIVE: 'success',
  REMINDING: 'warning',
  VERIFYING: 'danger',
  REVIEWING: 'danger',
  SUSPENDED: 'info'
}

const CONTACT_LABEL_MAP: Record<ContactStatus, string> = {
  PENDING: '待确认',
  ACTIVE: '已生效',
  REJECTED: '已拒绝',
  REVOKED: '已撤销'
}

const CONTACT_TYPE_MAP: Record<ContactStatus, TagType> = {
  PENDING: 'warning',
  ACTIVE: 'success',
  REJECTED: 'info',
  REVOKED: 'info'
}

export function caseLabel(status: string): string {
  return (CASE_LABEL as Record<string, string>)[status] ?? status
}

export function caseType(status: string): TagType {
  return (CASE_TYPE as Record<string, TagType>)[status] ?? 'info'
}

export function heartbeatLabel(status: string): string {
  return (HEARTBEAT_LABEL_MAP as Record<string, string>)[status] ?? status
}

export function heartbeatType(status: string): TagType {
  return (HEARTBEAT_TYPE_MAP as Record<string, TagType>)[status] ?? 'info'
}

export function contactLabel(status: string): string {
  return (CONTACT_LABEL_MAP as Record<string, string>)[status] ?? status
}

export function contactType(status: string): TagType {
  return (CONTACT_TYPE_MAP as Record<string, TagType>)[status] ?? 'info'
}

export const ASSET_TYPE_LABEL: Record<string, string> = {
  DOCUMENT: '个人文档',
  IMAGE: '图片说明',
  NOTE: '重要资料说明',
  ACCOUNT_RECOVERY: '账号恢复说明',
  OTHER: '其他'
}

export const ASSET_TYPE_OPTIONS = Object.entries(ASSET_TYPE_LABEL).map(([value, label]) => ({
  value,
  label
}))

/** 审批动作 → 中文 */
export const APPROVAL_ACTION_LABEL: Record<string, string> = {
  ACCEPT: '受理',
  APPROVE: '批准',
  REJECT: '驳回',
  RESOLVE_DISPUTE: '处理异议'
}

/** 审计动作 → 中文 */
export const AUDIT_ACTION_LABEL: Record<string, string> = {
  REGISTER: '注册',
  LOGIN_SUCCESS: '登录成功',
  LOGIN_FAILURE: '登录失败',
  LOGOUT: '登出',
  PROFILE_UPDATE: '修改资料',
  ASSET_CREATE: '创建资产',
  ASSET_VIEW: '查看资产',
  ASSET_UPDATE: '修改资产',
  ASSET_DELETE: '删除资产',
  CONTACT_INVITE: '邀请联系人',
  CONTACT_ACCEPT: '接受邀请',
  CONTACT_REJECT: '拒绝邀请',
  CONTACT_REVOKE: '撤销联系人',
  HEARTBEAT_CONFIRM: '状态确认',
  HEARTBEAT_CONFIG_UPDATE: '修改确认配置',
  HEARTBEAT_VERIFY: '核验失联',
  CASE_CREATE: '创建案件',
  CASE_SUBMIT: '提交案件',
  CASE_CANCEL: '取消案件',
  CASE_DISPUTE: '提出异议',
  CASE_REVOKE: '撤销授权',
  CASE_ACCEPT: '受理案件',
  CASE_APPROVE: '批准案件',
  CASE_REJECT: '驳回案件',
  CASE_RESOLVE_DISPUTE: '处理异议',
  CASE_AUTHORIZE: '授权生效'
}

/** 格式化时间：去掉秒与 T */
export function fmtTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}
