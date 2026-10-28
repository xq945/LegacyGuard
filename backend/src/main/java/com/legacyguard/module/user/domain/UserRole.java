package com.legacyguard.module.user.domain;

/**
 * 角色。对应 {@code sys_user.role}。
 *
 * <p>本系统只有两类角色，且<b>角色只用于粗粒度拦截</b>。
 * "这个人能不能看这份资产"永远由数据决定，不由角色决定 ——
 * 管理员角色并不带来读取用户资产明文的能力。
 */
public final class UserRole {

    /** 普通用户：既是资产所有者，也可能是他人的可信联系人 */
    public static final String USER = "USER";

    /** 审核管理员：可审批案件、处理异议，但不能读取资产内容 */
    public static final String ADMIN = "ADMIN";

    private UserRole() {
    }
}
