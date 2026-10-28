package com.legacyguard.module.user.domain;

/**
 * 账号状态。对应 {@code sys_user.status}。
 *
 * <p>用常量而非枚举：该字段只用于判断，不参与状态机迁移，
 * 用 int 省去 MyBatis-Plus 的类型处理器配置。
 */
public final class UserStatus {

    /** 正常 */
    public static final int ACTIVE = 0;

    /** 锁定（登录失败次数超限，到期自动解除） */
    public static final int LOCKED = 1;

    /** 禁用（管理员或风控操作，需人工解除） */
    public static final int DISABLED = 2;

    private UserStatus() {
    }

    public static boolean isUsable(int status) {
        return status == ACTIVE;
    }
}
