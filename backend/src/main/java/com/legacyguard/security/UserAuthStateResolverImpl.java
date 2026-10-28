package com.legacyguard.security;

import org.springframework.stereotype.Component;

/**
 * {@link UserAuthStateResolver} 的当前实现。
 *
 * <p><b>第二阶段的状态</b>：用户表尚未接入，此处一律放行。
 * 这在本阶段是安全的 —— 还没有登录接口，不存在能被合法签发的令牌。
 *
 * <p><b>第三阶段要做的改动</b>：改为查询 {@code sys_user}，判断
 * {@code status = 0}（正常）且 {@code deleted_at IS NULL}。
 * 改动的范围仅限本类，调用方（{@link JwtAuthenticationFilter}）不需要变化 ——
 * 这正是把它抽成接口的原因。
 */
@Component
public class UserAuthStateResolverImpl implements UserAuthStateResolver {

    @Override
    public boolean isActive(Long userId) {
        // 第三阶段：return userMapper.selectById(userId) 状态为正常;
        return true;
    }
}
