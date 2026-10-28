package com.legacyguard.security;

import com.legacyguard.common.web.RequestContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>职责：从 {@code Authorization: Bearer} 头取出令牌，校验签名，
 * 检查黑名单与账号状态，通过后把身份放入 {@code SecurityContext}。
 *
 * <p><b>校验失败时不抛异常</b>，只是不设置认证信息。原因：
 * 有些接口（注册、登录、健康检查）是匿名的，如果这里抛异常，
 * 匿名请求也会被拦下。真正的拦截由 Spring Security 的授权规则 +
 * {@code AuthenticationEntryPoint} 完成。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BLACKLIST_PREFIX = "lg:jwt:blacklist:";

    private final JwtProvider jwtProvider;
    private final UserAuthStateResolver authStateResolver;
    private final StringRedisTemplate redis;

    public JwtAuthenticationFilter(JwtProvider jwtProvider,
                                   UserAuthStateResolver authStateResolver,
                                   StringRedisTemplate redis) {
        this.jwtProvider = jwtProvider;
        this.authStateResolver = authStateResolver;
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null) {
            try {
                AuthPrincipal principal = jwtProvider.parse(token);
                authenticateIfStillValid(principal, request);
            } catch (JwtException | IllegalArgumentException e) {
                // 令牌无效就当作匿名请求，交给后续的授权规则决定该不该拒绝
                log.debug("JWT 校验未通过: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateIfStillValid(AuthPrincipal principal, HttpServletRequest request) {
        if (isBlacklisted(principal.tokenId())) {
            log.debug("令牌已被登出作废 jti={}", principal.tokenId());
            return;
        }

        // JWT 无状态，签名有效不代表账号现在仍可用 —— 禁用/锁定必须即时生效
        if (!authStateResolver.isActive(principal.userId())) {
            log.info("账号不可用，拒绝令牌 userId={}", principal.userId());
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 查询登出黑名单。
     *
     * <p><b>Redis 故障时的取舍</b>：放行并记 WARN。理由是黑名单只是"提前失效"机制，
     * 令牌本身仍在 30 分钟有效期内且签名已通过校验，故障窗口内的影响可控；
     * 若改为拒绝，Redis 一挂整个系统就不可用，代价更大。这是一次有意的权衡，不是遗漏。
     */
    private boolean isBlacklisted(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + tokenId));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，跳过登录黑名单校验 requestId={}", RequestContext.currentRequestId());
            return false;
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
