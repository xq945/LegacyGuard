package com.legacyguard.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析客户端 IP。
 *
 * <p><b>这是一处真实的安全边界。</b>{@code X-Forwarded-For} 由客户端发送，
 * 可以被任意伪造 —— 如果无条件信任它，攻击者每次请求换一个头值，
 * 就能让基于 IP 的限流彻底失效。
 *
 * <p>因此只有显式开启 {@code legacyguard.security.trust-forwarded-headers}
 * 时才读取该头。开启的前提是：请求<b>必经</b>一个会覆盖而非追加该头的反向代理，
 * 否则后端拿到的仍是攻击者写进去的值。
 *
 * <p>关闭时退化为 {@code remoteAddr}，这是最保守的选择：
 * 代价是在 NAT 后面多个用户共享一个出口 IP，会互相消耗限流额度。
 *
 * <p>无论哪种模式，本值都只用于<b>记录与限流</b>，绝不用于授权判断 ——
 * 账号维度的失败锁定不依赖 IP，是绕不过去的那道防线。
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedHeaders;

    public ClientIpResolver(WebSecurityProperties properties) {
        this.trustForwardedHeaders = properties.trustForwardedHeaders();
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                // 格式为 "客户端IP, 代理1, 代理2"，最左边是原始客户端
                String first = forwarded.split(",")[0].trim();
                if (StringUtils.hasText(first)) {
                    return truncate(first);
                }
            }
        }
        return truncate(request.getRemoteAddr());
    }

    private static String truncate(String ip) {
        if (ip == null) {
            return null;
        }
        // 列定义为 VARCHAR(45)，IPv6 最长 45 字符
        return ip.length() <= 45 ? ip : ip.substring(0, 45);
    }
}
