package com.legacyguard.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 跨域配置。
 *
 * <p>只放行显式配置的前端源。开发环境是 Vite 的 5173，生产环境是同源反代（此时列表为空即可）。
 */
@ConfigurationProperties(prefix = "legacyguard.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        }
    }
}
