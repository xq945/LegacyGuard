package com.legacyguard.security;

/**
 * 判定用户当前是否仍然允许访问系统。
 *
 * <p>为什么需要它：JWT 是<b>无状态</b>的 —— 令牌一旦签发，用户被禁用、密码被重置、
 * 或者账号被锁定，令牌仍然能通过签名校验直到过期。所以每次请求都必须再问一次
 * "这个人现在还能用吗"。
 *
 * <p>这是<b>认证</b>层面的检查，不是业务授权，因此允许查库（可配合短 TTL 缓存）。
 * 业务授权（谁能看哪个资产）不在此处判断，见 {@code AssetAccessGuard}。
 *
 * <p>实现说明：第三阶段随用户模块接入 {@code sys_user} 查询，
 * 届时本接口的实现会改为读取用户状态；调用方无需改动。
 */
public interface UserAuthStateResolver {

    /**
     * @return true 表示账号状态正常（未禁用、未锁定、未注销）
     */
    boolean isActive(Long userId);
}
