package com.legacyguard.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证访问受保护资源时的响应（401）。
 *
 * <p>Spring Security 默认会重定向到登录页或返回空 body，这对前后端分离的 API 不合适。
 * 这里改为返回与业务接口一致的 JSON 结构，前端拦截到 401 统一跳登录。
 *
 * <p>这里处理的是<b>过滤器链</b>中的认证失败；Controller 层抛出的
 * {@code AuthenticationException} 由 {@code GlobalExceptionHandler} 处理。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.debug("未认证访问已拦截 path={}", request.getRequestURI());

        response.setStatus(ErrorCode.UNAUTHENTICATED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ErrorCode.UNAUTHENTICATED));
    }
}
