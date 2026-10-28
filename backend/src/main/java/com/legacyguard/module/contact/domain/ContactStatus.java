package com.legacyguard.module.contact.domain;

/**
 * 可信联系人关系状态。
 *
 * <pre>
 *   PENDING ──接受──> ACTIVE ──撤销──> REVOKED
 *      │
 *      ├──拒绝──> REJECTED
 *      └──撤回──> REVOKED
 * </pre>
 *
 * <p><b>只有 ACTIVE 状态的联系人才能发起授权申请。</b>
 * 这个判断在 Service 层和数据库唯一约束两处都有体现 ——
 * 前者给出友好提示，后者防止并发情况下的漏网。
 */
public final class ContactStatus {

    /** 邀请已发出，等待对方回应 */
    public static final String PENDING = "PENDING";

    /** 关系生效，可以发起授权申请 */
    public static final String ACTIVE = "ACTIVE";

    /** 对方拒绝 */
    public static final String REJECTED = "REJECTED";

    /** 邀请被撤回，或已生效关系被所有者撤销 */
    public static final String REVOKED = "REVOKED";

    private ContactStatus() {
    }

    /** 是否为"占用唯一性"的状态 —— 同一对用户只能有一条这样的记录 */
    public static boolean isActivePair(String status) {
        return PENDING.equals(status) || ACTIVE.equals(status);
    }
}
