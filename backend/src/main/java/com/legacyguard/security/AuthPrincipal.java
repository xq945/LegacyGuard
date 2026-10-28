package com.legacyguard.security;

import java.io.Serializable;

/**
 * 已认证用户身份，作为 Spring Security 的 principal 存入 SecurityContext。
 *
 * <p><b>业务代码获取当前用户，只能从这里拿</b>，绝不接受前端传入的 userId 参数：
 * <pre>{@code
 * AuthPrincipal me = CurrentUser.get();   // 内部读 SecurityContextHolder
 * // 而不是 @RequestParam Long userId
 * }</pre>
 *
 * @param userId  用户 ID
 * @param username 登录名
 * @param role    角色（USER / ADMIN），仅用于粗粒度拦截
 * @param tokenId JWT 的 jti，登出时写入黑名单
 */
public record AuthPrincipal(Long userId, String username, String role, String tokenId) implements Serializable {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
