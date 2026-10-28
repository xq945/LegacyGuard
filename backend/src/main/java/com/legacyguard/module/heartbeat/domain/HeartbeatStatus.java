package com.legacyguard.module.heartbeat.domain;

/**
 * 状态确认配置的状态。
 *
 * <pre>
 *   ACTIVE ──到期──> REMINDING ──提醒耗尽──> VERIFYING ──人工核验──> REVIEWING
 *     │                 │                      │                        │
 *     └──────用户确认────┴──────────────────────┴────────────────────────┘
 *                          （任意状态确认都回到 ACTIVE，周期号 +1）
 * </pre>
 *
 * <p><b>这条链的终点是 REVIEWING，不是"授权"。</b>
 * 代码里不存在任何从 ACTIVE 直达"可授权"的路径 ——
 * 用户没响应最多只能证明"系统联系不上他"，不能证明"他同意交出去"。
 * 这是需求第一条业务原则的物理落点。
 */
public final class HeartbeatStatus {

    /** 正常计时中 */
    public static final String ACTIVE = "ACTIVE";

    /** 已逾期，正在多轮提醒 */
    public static final String REMINDING = "REMINDING";

    /** 提醒轮次耗尽，等待人工核验失联事实 */
    public static final String VERIFYING = "VERIFYING";

    /** 失联已核验成立，允许可信联系人发起授权申请 */
    public static final String REVIEWING = "REVIEWING";

    /** 管理员冻结（争议、误判、临时处置） */
    public static final String SUSPENDED = "SUSPENDED";

    private HeartbeatStatus() {
    }

    /** 该状态下是否允许发起授权申请 */
    public static boolean allowsAuthorizationApplication(String status) {
        return REVIEWING.equals(status);
    }
}
