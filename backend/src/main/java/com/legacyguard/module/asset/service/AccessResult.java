package com.legacyguard.module.asset.service;

/**
 * 一次访问判定的结果。
 *
 * @param allowed     是否放行
 * @param accessType  {@code OWNER_VIEW} 或 {@code CASE_VIEW}
 * @param caseId      通过授权案件访问时的案件 ID，所有者直接访问时为 null
 * @param denyReason  拒绝原因，写入访问流水，不放行时非空
 */
public record AccessResult(boolean allowed, String accessType, Long caseId, String denyReason) {

    public static final String OWNER_VIEW = "OWNER_VIEW";
    public static final String CASE_VIEW = "CASE_VIEW";

    /** 被拒绝时写入访问流水的占位值（{@code access_type} 列非空） */
    public static final String DENIED_TYPE = "DENIED";

    public static AccessResult owner() {
        return new AccessResult(true, OWNER_VIEW, null, null);
    }

    public static AccessResult viaCase(Long caseId) {
        return new AccessResult(true, CASE_VIEW, caseId, null);
    }

    public static AccessResult denied(String reason) {
        return new AccessResult(false, null, null, reason);
    }
}
