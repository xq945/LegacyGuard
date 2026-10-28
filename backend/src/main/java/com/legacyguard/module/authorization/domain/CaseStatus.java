package com.legacyguard.module.authorization.domain;

import java.util.Set;

/**
 * 授权案件状态。
 *
 * <pre>
 *   DRAFT ──submit──> PENDING_REVIEW ──受理──> REVIEWING ──批准──> WAITING_PERIOD ──到期复核──> AUTHORIZED
 *     │                     │                    │                     │                      │
 *     └──cancel──> CANCELLED└──reject──> REJECTED└──reject──> REJECTED  ├──异议──> DISPUTED   └──revoke──> REVOKED
 *                                                                      └──cancel──> CANCELLED
 * </pre>
 *
 * <p>三个终态：{@link #REJECTED}、{@link #CANCELLED}、{@link #REVOKED}。
 * 进入终态后不可再迁移，所有操作都返回冲突。
 */
public final class CaseStatus {

    /** 草稿：联系人已选好资产，尚未提交审核 */
    public static final String DRAFT = "DRAFT";

    /** 已提交，等待管理员受理 */
    public static final String PENDING_REVIEW = "PENDING_REVIEW";

    /** 管理员审核中 */
    public static final String REVIEWING = "REVIEWING";

    /** 审核通过，等待期内任何人可提异议 */
    public static final String WAITING_PERIOD = "WAITING_PERIOD";

    /** 授权生效，联系人可访问被授权的资产 */
    public static final String AUTHORIZED = "AUTHORIZED";

    /** 驳回（终态） */
    public static final String REJECTED = "REJECTED";

    /** 取消（终态） */
    public static final String CANCELLED = "CANCELLED";

    /** 异议中，流程暂停 */
    public static final String DISPUTED = "DISPUTED";

    /** 已撤销（终态） */
    public static final String REVOKED = "REVOKED";

    /** 终态集合 */
    private static final Set<String> TERMINAL = Set.of(REJECTED, CANCELLED, REVOKED);

    /**
     * 未终结的状态集合。
     *
     * <p>与数据库上的 {@code uk_active_case} 生成列表达式保持一致 ——
     * 两者都表示"这个案件还占着资产"。改一处必须改另一处。
     */
    private static final Set<String> ACTIVE = Set.of(
            DRAFT, PENDING_REVIEW, REVIEWING, WAITING_PERIOD, DISPUTED, AUTHORIZED);

    private CaseStatus() {
    }

    public static boolean isTerminal(String status) {
        return TERMINAL.contains(status);
    }

    public static boolean isActive(String status) {
        return ACTIVE.contains(status);
    }

    /** 是否处于"用户还可以主动取消"的阶段 */
    public static boolean isCancellable(String status) {
        return DRAFT.equals(status) || PENDING_REVIEW.equals(status)
                || REVIEWING.equals(status) || WAITING_PERIOD.equals(status);
    }

    public static Set<String> terminalStates() {
        return TERMINAL;
    }

    public static Set<String> cancellableStates() {
        return Set.of(DRAFT, PENDING_REVIEW, REVIEWING, WAITING_PERIOD);
    }
}
