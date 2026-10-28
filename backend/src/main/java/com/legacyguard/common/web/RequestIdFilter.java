package com.legacyguard.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求关联 ID 过滤器。
 *
 * <p>每个请求分配一个 ID，写入两处：
 * <ul>
 *   <li>MDC —— 让这一请求产生的所有日志自动带上它</li>
 *   <li>响应头 —— 让客户端能拿到，报障时提供</li>
 * </ul>
 *
 * <p>ID 同时用于错误响应体（见 {@link com.legacyguard.common.api.ApiResponse}）。
 *
 * <p><b>为什么校验客户端传入的 ID</b>：如果不校验，攻击者可以传入带换行的字符串，
 * 伪造日志行（日志注入）。因此只接受白名单字符与长度范围内的值，否则一律重新生成。
 *
 * <p>顺序设为最高优先级，确保它先于 Spring Security 的过滤器链执行 ——
 * 否则认证失败的日志里就没有 requestId。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    /** 合法的客户端 ID：8~64 位字母、数字、下划线、连字符 */
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestId = resolveRequestId(request);
        MDC.put(RequestContext.MDC_KEY, requestId);
        response.setHeader(RequestContext.HEADER_NAME, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须清理：Tomcat 复用线程，残留的 MDC 会污染后续请求的日志
            MDC.remove(RequestContext.MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String provided = request.getHeader(RequestContext.HEADER_NAME);
        if (provided != null && VALID_ID.matcher(provided).matches()) {
            return provided;
        }
        return UUID.randomUUID().toString();
    }
}
