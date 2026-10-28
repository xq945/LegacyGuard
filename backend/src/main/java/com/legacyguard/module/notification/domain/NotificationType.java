package com.legacyguard.module.notification.domain;

/**
 * 站内信类型。
 *
 * <p>统一常量而不是散落的字符串：通知会按类型做筛选与统计，
 * 拼写不一致会导致统计结果静默出错。
 */
public final class NotificationType {

    /** 状态确认已逾期，开始提醒 */
    public static final String HEARTBEAT_REMIND = "HEARTBEAT_REMIND";

    /** 提醒轮次耗尽，进入人工核验 */
    public static final String HEARTBEAT_VERIFYING = "HEARTBEAT_VERIFYING";

    /** 收到可信联系人邀请 */
    public static final String CONTACT_INVITED = "CONTACT_INVITED";

    /** 联系人关系已生效 */
    public static final String CONTACT_ACTIVATED = "CONTACT_ACTIVATED";

    /** 有人提交了授权申请 */
    public static final String CASE_SUBMITTED = "CASE_SUBMITTED";

    /** 授权申请已批准，进入等待期 */
    public static final String CASE_WAITING_PERIOD = "CASE_WAITING_PERIOD";

    /** 授权申请被驳回 */
    public static final String CASE_REJECTED = "CASE_REJECTED";

    /** 授权已生效 */
    public static final String CASE_AUTHORIZED = "CASE_AUTHORIZED";

    /** 授权被撤销 */
    public static final String CASE_REVOKED = "CASE_REVOKED";

    /** 案件被提出异议 */
    public static final String CASE_DISPUTED = "CASE_DISPUTED";

    /** 案件被取消 */
    public static final String CASE_CANCELLED = "CASE_CANCELLED";

    private NotificationType() {
    }
}
