# 04 API 清单

## 4.1 统一约定

### 响应结构

```json
{
  "code": 0,
  "message": "ok",
  "data": { },
  "requestId": "0c1b7f3e-8a2d-4f19-9c77-2e5f1a3b4d68"
}
```

`requestId` 由 `OncePerRequestFilter` 生成并写入 MDC，客户端回传时可直接用于日志检索。

### 业务码 ↔ HTTP 状态码（**不统一返回 200**）

| code | HTTP | 含义 | 典型场景 |
| --- | --- | --- | --- |
| `0` | 200 | 成功 | — |
| `40001` | 400 | 参数校验失败 | `@Valid` 不通过 |
| `40101` | 401 | 未登录 / token 失效 | 无 Authorization 头 |
| `40301` | 403 | 无权限 | 越权访问他人资产 |
| `40401` | 404 | 资源不存在 | 案件 ID 不存在 |
| `40901` | 409 | 状态冲突 | 乐观锁版本不匹配 |
| `40902` | 409 | 重复请求 | 幂等键冲突 |
| `42201` | 422 | 业务规则不满足 | 联系人非 ACTIVE 却提交申请 |
| `50001` | 500 | 系统异常 | 未捕获异常 |

**关键区分**：`42201` 与 `40301` 不同。前者是"你身份没问题但当前状态不允许"，后者是"你根本不该访问这个资源"。混用会让前端无法正确引导用户。

### 认证方式

JWT（HS256）+ Redis 黑名单：

- 登录签发 `accessToken`，含 `jti`、`sub`（用户 ID）、`role`、`exp`（30 分钟）
- 登出把 `jti` 写入 Redis，TTL = token 剩余有效期
- 每次请求校验签名 → 查黑名单 → **再从数据库加载用户状态**

> 为什么每次还要查库：JWT 是无状态的，用户被禁用后 token 仍然有效直到过期。所以 `status != 0` 一律拒绝。这不算性能问题 —— 用户表被 Redis 本地缓存（Caffeine），且这是认证不是业务授权。

**业务授权绝对不能靠 JWT 里的 role 判断**，role 只用于粗粒度拦截（`@PreAuthorize("hasRole('ADMIN')")`），资源级权限一律查库。

### 通用请求头

| Header | 必填 | 说明 |
| --- | --- | --- |
| `Authorization: Bearer <token>` | 除匿名接口外 | — |
| `X-Request-Id` | 否 | 客户端可传入以便串联链路 |
| `Idempotency-Key` | 部分接口 | 见 4.3 |

## 4.2 接口清单

### A. 认证与用户

| 接口 | 权限 | 关键校验 | 事务边界 | 幂等 |
| --- | --- | --- | --- | --- |
| `POST /api/auth/register` | 匿名 | 用户名格式 `^[a-zA-Z0-9_]{4,20}$`；密码 ≥ 8 位含字母+数字；用户名/邮箱唯一 | 插入 user + audit | 唯一索引兜底 |
| `POST /api/auth/login` | 匿名 | 账号存在、未锁定、密码匹配；失败计数 ≥ 5 锁定 15 分钟 | 无（Redis 计数 + login_record） | 否 |
| `POST /api/auth/logout` | 已登录 | — | 无 | 是（jti 重复加黑名单无害） |
| `GET /api/users/me` | 已登录 | — | 无 | 是（只读） |
| `PUT /api/users/me` | 已登录 | 只允许改 displayName / email / phone | 更新 + audit | 否 |

**登录失败处理的三层**：

1. Redis `login:fail:{username}` 计数，TTL 15 分钟，≥ 5 次后 `sys_user.status=1` + `locked_until`
2. 同一 IP 限流（`rate:login:{ip}`，10 次/分钟）
3. 无论账号是否存在都返回同一提示（防用户枚举），真实原因只写 `login_record`

### B. 数字资产

| 接口 | 权限 | 关键校验 | 事务边界 | 幂等 |
| --- | --- | --- | --- | --- |
| `POST /api/assets` | USER | 每用户上限 100 条；`content` ≤ 64KB；名称 ≤ 100 字 | asset + secret + audit + outbox **同事务** | 否（无业务键） |
| `GET /api/assets` | USER | — | 只读 | 是 |
| `GET /api/assets/{id}` | 见下 | 元数据读取 | 只读 | 是 |
| `GET /api/assets/{id}/content` | 见下 | **资源级权限守卫** | 只读 + 访问流水 | 是 |
| `PUT /api/assets/{id}` | 仅 owner | 乐观锁 version；内容变更需重新加密 | update + secret + audit | 否 |
| `DELETE /api/assets/{id}` | 仅 owner | **存在有效案件时禁止删除**（42201） | 软删 + audit | 是（重复删返回 200） |

#### 为什么把 `GET /{id}` 和 `GET /{id}/content` 拆开

需求原文只列了 `GET /api/assets/{id}`。但如果元数据和明文走同一个接口，会出现两个问题：

1. **列表与详情混用同一权限守卫，容易漏判**。资产列表页每个人都能看自己的，详情页却可能是联系人访问 —— 同一接口承担两种身份，Guard 逻辑会变复杂。
2. **响应体积失控**。联系人看到的应该只是被授权的那几项，不带其他元数据。

拆开后，权限判断集中在 `GET /{id}/content` 一处，而这个接口就是需求要求的"每次读取敏感内容都必须执行权限检查"的物理落点：

```
AssetAccessGuard.checkReadAccess(assetId, currentUser):
    asset = assetMapper.selectById(assetId)
    if asset == null || asset.deleted           -> 404
    if asset.ownerId == currentUser.id          -> ALLOW(OWNER_VIEW)
    else:
        case = caseMapper.findAuthorizedCase(assetId, currentUser.id)
        if case == null                          -> 403 + 写 DENIED 流水
        if case.status != 'AUTHORIZED'           -> 403
        if case.revokedAt != null                -> 403
        -> ALLOW(CASE_VIEW)
```

**注意这里没有任何缓存。** 每次访问都查库。这是原则 7 的直接体现 —— 撤销后必须立刻阻断。

### C. 可信联系人

| 接口 | 权限 | 关键校验 | 事务边界 | 幂等 |
| --- | --- | --- | --- | --- |
| `POST /api/contacts/invitations` | USER | 目标用户存在且未删除；不能邀请自己；无同 pair 有效关系 | insert + audit + outbox | 唯一索引 `uk_active_pair` |
| `POST /api/contacts/invitations/{id}/accept` | 被邀请人本人 | `status=PENDING`；乐观锁 | update + audit + outbox | 状态条件更新 |
| `POST /api/contacts/invitations/{id}/reject` | 被邀请人本人 | `status=PENDING` | update + audit + outbox | 同上 |
| `GET /api/contacts` | USER | 只返回与当前用户相关的关系 | 只读 | 是 |
| `DELETE /api/contacts/{id}` | 仅邀请方 owner | 该 id 是 owner 自己的关系 | update status=REVOKED + audit | 状态条件更新 |

**接受邀请的并发场景**：同一邀请被点两次"接受" → 第二次条件更新影响 0 行 → 返回 `40901`。不会产生两条 ACTIVE。

### D. 定期状态确认

| 接口 | 权限 | 关键校验 | 事务边界 | 幂等 |
| --- | --- | --- | --- | --- |
| `GET /api/heartbeat/status` | USER | 未配置时自动创建默认配置 | 只读（首次创建除外） | 是 |
| `PUT /api/heartbeat/config` | USER | `cycle_days` ∈ [7, 365]；`reminder_rounds` ∈ [1, 10] | update + audit | 否 |
| `POST /api/heartbeat/confirm` | USER | 请求者 = 配置所有者 | **重置周期 + record + 取消未生效案件 + audit + outbox** | 天然幂等（见下） |
| `GET /api/heartbeat/records` | USER | 分页 | 只读 | 是 |

#### confirm 接口的连锁反应（容易漏）

用户确认不只是改自己的心跳。如果此时存在**其他人为他发起的未生效案件**，必须一并取消 —— 否则"人回来了，授权流程还在跑"。

同一事务内：

```sql
-- 1. 重置心跳（带 version 条件）
UPDATE heartbeat_confirm_config
   SET cycle_no = cycle_no + 1, status = 'ACTIVE', reminder_count = 0,
       last_remind_at = NULL, last_confirm_at = NOW(),
       next_deadline_at = DATE_ADD(NOW(), INTERVAL cycle_days DAY),
       version = version + 1
 WHERE user_id = ? AND version = ?

-- 2. 取消所有未生效案件（DRAFT/PENDING_REVIEW/REVIEWING/WAITING_PERIOD/DISPUTED）
UPDATE authorization_case
   SET status = 'CANCELLED', closed_at = NOW(), version = version + 1
 WHERE owner_id = ? AND status IN (...)

-- 3. INSERT heartbeat_record
-- 4. INSERT audit_log
-- 5. 每个被取消的案件各 INSERT 一条 outbox_event
```

**幂等性来自 `cycle_no`**：同一秒内重复点击，第二次的 `version` 条件不满足，直接返回 409。

### E. 授权案件

| 接口 | 权限 | 关键校验 | 事务边界 | 幂等 |
| --- | --- | --- | --- | --- |
| `POST /api/authorizations` | 联系人（ACTIVE） | ①关系 ACTIVE ②owner 心跳 REVIEWING ③资产归属 ④资产未被有效案件占用（`FOR UPDATE`）⑤无同 pair 有效案件 | case + case_asset + audit + outbox | `uk_active_case` 唯一索引 |
| `GET /api/authorizations` | USER | 按角色过滤：owner 看别人对自己的申请，contact 看自己发的 | 只读 | 是 |
| `GET /api/authorizations/{id}` | 案件相关方 / ADMIN | 只有 owner、applicant 的 contact、管理员可读 | 只读 | 是 |
| `POST /api/authorizations/{id}/submit` | 申请联系人 | `status=DRAFT` | update + audit + outbox | 条件更新 |
| `POST /api/authorizations/{id}/cancel` | owner / 申请联系人 | `status IN (DRAFT, PENDING_REVIEW, REVIEWING, WAITING_PERIOD)` | update + audit + outbox | 条件更新 |
| `POST /api/authorizations/{id}/disputes` | owner / 该联系人 | `status=WAITING_PERIOD` | `UPDATE case → DISPUTED` + `INSERT dispute` + audit + outbox | 唯一约束 `(case_id, status='OPEN')` 由应用校验 |
| `POST /api/authorizations/{id}/revoke` | **仅 owner** | `status=AUTHORIZED` | update → REVOKED + audit + outbox | 条件更新 |

> 管理员**没有** revoke 接口。需求 2.3 明确"管理员不得利用后台权限绕过业务规则"。撤销已生效授权是所有者专属权利。

#### 创建案件的完整 Guard 链

```
1. trusted_contact.status == 'ACTIVE'                         否则 42201 CONTACT_NOT_ACTIVE
2. heartbeat_confirm_config.status == 'REVIEWING'             否则 42201 OWNER_NOT_REVIEWING
3. 每个 asset.owner_id == case.owner_id 且未删除               否则 40301 / 40401
4. SELECT ... FROM digital_asset WHERE id IN (...) FOR UPDATE
   查询这些资产是否已关联有效案件                               否则 42201 ASSET_ALREADY_CLAIMED
5. 不存在同 (owner, contact) 的未终结案件                      否则 40902 DUPLICATE_CASE
```

第 4 步的 `FOR UPDATE` 是**跨表条件唯一无法用索引表达**的补偿（见 03-数据库设计 3.3.3）。

### F. 管理端

| 接口 | 权限 | 关键校验 | 事务边界 | 幂等 |
| --- | --- | --- | --- | --- |
| `GET /api/admin/authorizations/pending` | ADMIN | 分页，按 `status, created_at` 索引 | 只读 | 是 |
| `POST /api/admin/authorizations/{id}/accept` | ADMIN | `status=PENDING_REVIEW` | update → REVIEWING + approval + audit | 条件更新 |
| `POST /api/admin/authorizations/{id}/approve` | ADMIN | `status=REVIEWING`；必填意见 | update → WAITING_PERIOD（含 `wait_period_end_at`）+ approval + audit + outbox | 条件更新 + version |
| `POST /api/admin/authorizations/{id}/reject` | ADMIN | `status IN (PENDING_REVIEW, REVIEWING)`；必填理由 | update → REJECTED + approval + audit + outbox | 同上 |
| `POST /api/admin/authorizations/{id}/resolve-dispute` | ADMIN | `status=DISPUTED`；存在 OPEN 异议；`result ∈ {UPHOLD, OVERTURN}` | 异议置 RESOLVED + case 状态 + approval + audit + outbox | 同上 |
| `GET /api/admin/heartbeat/verify-queue` | ADMIN | `status=VERIFYING` | 只读 | 是 |
| `POST /api/admin/heartbeat/{userId}/verify` | ADMIN | `status=VERIFYING` | update → REVIEWING + audit + outbox | 条件更新 + cycle_no |
| `GET /api/admin/notifications/failed` | ADMIN | `status IN (FAILED, DEAD)` | 只读 | 是 |
| `POST /api/admin/notifications/{id}/retry` | ADMIN | 重置 `retry_count=0, status=PENDING` | update + audit | 否 |

**管理员权限边界的两处体现**：

- 管理端所有返回 DTO **不包含**资产明文、`cipher_text`、`iv`、`auth_tag`。DTO 层面就不定义这些字段，而不是"查出来再删掉"。
- 管理员审批不校验也无法修改资产内容，只能决定"批不批"。

### G. 审计

| 接口 | 权限 | 关键校验 |
| --- | --- | --- |
| `GET /api/audit/logs` | USER / ADMIN | 普通用户**:强制** `actor_id = 当前用户`，管理员可按 object / action 过滤；分页上限 100 |

普通用户看自己的审计记录时，查询条件由服务端注入，**前端传的 `actorId` 参数一律忽略**。这是"不得信任客户端 userId"的又一个落点。

### H. 通知

| 接口 | 权限 | 关键校验 | 幂等 |
| --- | --- | --- | --- |
| `GET /api/notifications` | USER | `receiver_id = 当前用户`，分页 | 是 |
| `GET /api/notifications/{id}` | USER | `receiver_id = 当前用户` | 是 |
| `POST /api/notifications/{id}/read` | USER | 同上 | 是 |
| `POST /api/notifications/read-all` | USER | 同上 | 是 |

## 4.3 幂等键的使用

以下接口接受可选请求头 `Idempotency-Key`，服务端用 Redis SETNX 记录 5 分钟：

- `POST /api/assets`
- `POST /api/contacts/invitations`
- `POST /api/authorizations`

> 但**幂等的主要保障不是这个头**，而是数据库唯一约束。`Idempotency-Key` 只解决"前端因为网络超时重发"这一种情况，数据库约束才是最终防线。两者是补充关系。

## 4.4 错误响应示例

**越权访问资产（别人的资产、无授权案件）**

```http
HTTP/1.1 403 Forbidden
{"code":40301,"message":"无权访问该资产","data":null,"requestId":"..."}
```

**并发审批冲突**

```http
HTTP/1.1 409 Conflict
{"code":40901,"message":"案件状态已被其他操作改变，请刷新后重试","data":null,"requestId":"..."}
```

**业务规则不满足**

```http
HTTP/1.1 422 Unprocessable Entity
{"code":42201,"message":"该联系人尚未确认邀请，无法提交授权申请","data":null,"requestId":"..."}
```

**系统异常（不泄露堆栈）**

```http
HTTP/1.1 500 Internal Server Error
{"code":50001,"message":"系统繁忙，请稍后重试","data":null,"requestId":"0c1b7f3e-..."}
```

堆栈只写服务端日志，响应里只有 `requestId`。
