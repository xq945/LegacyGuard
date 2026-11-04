/** 与后端 DTO 对应的类型定义 */

export interface UserProfile {
  id: number
  username: string
  displayName: string
  email: string | null
  phone: string | null
  role: 'USER' | 'ADMIN'
  lastLoginAt: string | null
  createdAt: string | null
}

export interface LoginResponse {
  accessToken: string
  expiresInSeconds: number
  user: UserProfile
}

export interface Asset {
  id: number
  name: string
  assetType: string
  description: string | null
  fileName: string | null
  fileSize: number | null
  mimeType: string | null
  status: number
  createdAt: string
  updatedAt: string
}

export interface AssetContent {
  assetId: number
  name: string
  assetType: string
  content: string
  accessType: 'OWNER_VIEW' | 'CASE_VIEW'
  updatedAt: string
}

export type ContactStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'REVOKED'

export interface ContactRelation {
  id: number
  ownerId: number
  ownerDisplayName: string
  contactUserId: number
  contactDisplayName: string
  status: ContactStatus
  note: string | null
  invitedAt: string
  respondedAt: string | null
  iAmOwner: boolean
  canRespond: boolean
}

export type HeartbeatState = 'ACTIVE' | 'REMINDING' | 'VERIFYING' | 'REVIEWING' | 'SUSPENDED'

export interface HeartbeatStatus {
  userId: number
  status: HeartbeatState
  cycleNo: number
  cycleDays: number
  lastConfirmAt: string
  nextDeadlineAt: string
  reminderCount: number
  reminderRounds: number
  daysRemaining: number
  overdue: boolean
}

export interface HeartbeatRecord {
  id: number
  cycleNo: number
  confirmedAt: string
  prevDeadlineAt: string
  nextDeadlineAt: string
}

export type CaseState =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'REVIEWING'
  | 'WAITING_PERIOD'
  | 'AUTHORIZED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'DISPUTED'
  | 'REVOKED'

export interface CaseSummary {
  id: number
  caseNo: string
  ownerId: number
  contactId: number
  status: CaseState
  reason: string | null
  reviewComment: string | null
  waitPeriodEndAt: string | null
  authorizedAt: string | null
  revokedAt: string | null
  createdAt: string
  iAmOwner: boolean
  iAmApplicant: boolean
}

export interface ApprovalItem {
  approverId: number
  action: string
  fromStatus: string
  toStatus: string
  comment: string | null
  createdAt: string
}

export interface CaseAssetItem {
  id: number
  name: string
  assetType: string
  deleted: boolean
}

export interface CaseDetail extends CaseSummary {
  assetIds: number[]
  /** 案件覆盖的资产元数据；申请人与所有者都可见 */
  assets: CaseAssetItem[]
  closedAt: string | null
  updatedAt: string
  approvals: ApprovalItem[]
}

/** 发起申请前查询到的可选资产元数据 */
export interface ApplicableAssetItem {
  id: number
  name: string
  assetType: string
  description: string | null
  fileName: string | null
  fileSize: number | null
  updatedAt: string
  occupied: boolean
}

export interface ApplicableAssets {
  contactId: number
  ownerId: number
  canApply: boolean
  blockReason: string | null
  assets: ApplicableAssetItem[]
}

export interface NotificationItem {
  id: number
  bizType: string
  bizId: number | null
  title: string
  content: string
  read: boolean
  readAt: string | null
  createdAt: string
}

export interface AuditLog {
  id: number
  actorId: number | null
  actorRole: string | null
  action: string
  objectType: string
  objectId: number | null
  result: string
  requestId: string | null
  detail: string | null
  clientIp: string | null
  createdAt: string
}

export interface JobExecution {
  id: number
  jobName: string
  instanceId: string
  status: string
  scannedCount: number
  processedCount: number
  failedCount: number
  errorMsg: string | null
  startedAt: string
  finishedAt: string | null
  durationMs: number | null
}

export interface OutboxEvent {
  id: number
  eventId: string
  eventType: string
  aggregateType: string
  aggregateId: number
  status: string
  retryCount: number
  lastError: string | null
  createdAt: string
}
