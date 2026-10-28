package com.legacyguard.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置。
 *
 * @param secret         HMAC 密钥，<b>Base64 编码</b>，解码后必须 ≥ 32 字节（HS256 要求 256 位）
 * @param accessTokenTtl 访问令牌有效期。刻意不设 refresh token —— v1 用短有效期即可，
 *                       少一条续期链路就少一处可被利用的入口
 * @param issuer         签发者标识，写入 iss 声明
 */
@ConfigurationProperties(prefix = "legacyguard.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, String issuer) {

    public JwtProperties {
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            accessTokenTtl = Duration.ofMinutes(30);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "legacyguard";
        }
    }
}
