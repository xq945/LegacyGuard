package com.legacyguard.module.audit.domain;

/**
 * 审计动作常量。
 *
 * <p>统一常量而不是各处写字符串：审计日志要能按动作聚合查询，
 * 一旦拼写不一致（{@code LOGIN_FAIL} vs {@code LOGIN_FAILED}），
 * 统计结果就是错的，而且没人会发现。
 */
public final class AuditAction {

    // ===== 认证 =====
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGOUT = "LOGOUT";
    public static final String PROFILE_UPDATE = "PROFILE_UPDATE";

    // ===== 资产 =====
    public static final String ASSET_CREATE = "ASSET_CREATE";
    public static final String ASSET_VIEW = "ASSET_VIEW";
    public static final String ASSET_UPDATE = "ASSET_UPDATE";
    public static final String ASSET_DELETE = "ASSET_DELETE";

    // ===== 联系人 =====
    public static final String CONTACT_INVITE = "CONTACT_INVITE";
    public static final String CONTACT_ACCEPT = "CONTACT_ACCEPT";
    public static final String CONTACT_REJECT = "CONTACT_REJECT";
    public static final String CONTACT_REVOKE = "CONTACT_REVOKE";

    // ===== 心跳 =====
    public static final String HEARTBEAT_CONFIRM = "HEARTBEAT_CONFIRM";
    public static final String HEARTBEAT_CONFIG_UPDATE = "HEARTBEAT_CONFIG_UPDATE";
    public static final String HEARTBEAT_VERIFY = "HEARTBEAT_VERIFY";

    // ===== 授权案件 =====
    public static final String CASE_CREATE = "CASE_CREATE";
    public static final String CASE_SUBMIT = "CASE_SUBMIT";
    public static final String CASE_CANCEL = "CASE_CANCEL";
    public static final String CASE_DISPUTE = "CASE_DISPUTE";
    public static final String CASE_REVOKE = "CASE_REVOKE";
    public static final String CASE_ACCEPT = "CASE_ACCEPT";
    public static final String CASE_APPROVE = "CASE_APPROVE";
    public static final String CASE_REJECT = "CASE_REJECT";
    public static final String CASE_RESOLVE_DISPUTE = "CASE_RESOLVE_DISPUTE";
    public static final String CASE_AUTHORIZE = "CASE_AUTHORIZE";

    private AuditAction() {
    }
}
