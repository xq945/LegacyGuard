package com.legacyguard.security;

import com.legacyguard.module.user.domain.SysUser;
import com.legacyguard.module.user.domain.UserStatus;
import com.legacyguard.module.user.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

/**
 * 判断用户当前是否仍可用。
 *
 * <p>JWT 是无状态的，签名有效只能证明"令牌是真的"，不能证明"这个人现在还能用"。
 * 用户被禁用、锁定或注销之后，旧令牌必须立即失效 —— 因此每个请求都要问一次这里。
 *
 * <p>这是<b>认证</b>层面的检查，不是业务授权：它回答"你是不是还能访问系统"，
 * 不回答"你能不能看这份资产"。后者由各模块的访问守卫查数据库判定。
 */
@Component
public class UserAuthStateResolverImpl implements UserAuthStateResolver {

    private final SysUserMapper userMapper;

    public UserAuthStateResolverImpl(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean isActive(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.isDeleted()) {
            return false;
        }
        // 锁定状态同样拒绝：锁定中的账号即使持有旧令牌也不应继续操作
        return UserStatus.isUsable(user.getStatus());
    }
}
