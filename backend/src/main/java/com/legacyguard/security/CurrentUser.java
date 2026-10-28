package com.legacyguard.security;

import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户的唯一获取入口。
 *
 * <p><b>这是本项目最重要的一条安全约定</b>：任何涉及"这是谁的资源"的判断，
 * 身份必须来自这里（服务端认证上下文），<b>绝不能</b>接受前端提交的 userId。
 *
 * <p>反例（禁止）：
 * <pre>{@code
 * @GetMapping("/api/assets")
 * public List<Asset> list(@RequestParam Long userId) { ... }   // 换个 userId 就能看别人的
 * }</pre>
 *
 * <p>正例：
 * <pre>{@code
 * @GetMapping("/api/assets")
 * public List<Asset> list() {
 *     Long ownerId = CurrentUser.id();                          // 身份来自令牌
 *     return assetService.listByOwner(ownerId);
 * }
 * }</pre>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * @throws BizException 未认证时抛出（响应 401）
     */
    public static AuthPrincipal get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }

    /** 当前用户 ID。 */
    public static Long id() {
        return get().userId();
    }

    /** 当前用户是否为管理员（粗粒度判断，资源级权限仍须查库）。 */
    public static boolean isAdmin() {
        return get().isAdmin();
    }
}
