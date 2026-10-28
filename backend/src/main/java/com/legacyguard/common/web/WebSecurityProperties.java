package com.legacyguard.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web 层安全相关配置。
 *
 * @param trustForwardedHeaders 是否信任 {@code X-Forwarded-For} 头
 */
@ConfigurationProperties(prefix = "legacyguard.security")
public record WebSecurityProperties(boolean trustForwardedHeaders) {
}
