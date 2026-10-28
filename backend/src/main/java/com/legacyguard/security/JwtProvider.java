package com.legacyguard.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验。
 *
 * <p>使用 HS256（对称签名）。密钥来自配置，<b>启动时校验强度</b>：
 * 不足 256 位直接拒绝启动，而不是降级运行 —— 弱密钥的 JWT 与明文密码等价。
 *
 * <p>载荷里<b>不放任何敏感信息</b>：不放邮箱、手机号、权限明细。
 * JWT 是客户端可读的（只是签名不可伪造，内容并不加密）。
 */
@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    /** HS256 要求的最小密钥位数 */
    private static final int MIN_KEY_BYTES = 32;

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USERNAME = "uname";

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(decodeSecret(properties.secret()));
        log.info("JWT 签发器初始化完成，令牌有效期 {}", properties.accessTokenTtl());
    }

    /**
     * 签发访问令牌。jti 自动生成随机 UUID，登出时用它写黑名单。
     */
    public String issue(Long userId, String username, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());

        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 校验并解析令牌。
     *
     * @throws JwtException 签名错误、格式错误、已过期、issuer 不匹配等
     */
    public AuthPrincipal parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            // subject 不是数字说明令牌不是本系统签发的，或已被篡改
            throw new JwtException("令牌 subject 非法");
        }

        return new AuthPrincipal(
                userId,
                claims.get(CLAIM_USERNAME, String.class),
                claims.get(CLAIM_ROLE, String.class),
                claims.getId()
        );
    }

    /**
     * 返回令牌的剩余有效期，用于写入黑名单时的 TTL。
     * 解析失败返回 {@link Duration#ZERO}。
     */
    public Duration remainingTtl(String token) {
        try {
            Date expiration = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();
            long millis = expiration.getTime() - System.currentTimeMillis();
            return millis > 0 ? Duration.ofMillis(millis) : Duration.ZERO;
        } catch (JwtException e) {
            return Duration.ZERO;
        }
    }

    /**
     * 解码密钥。约定配置为 Base64；若不是合法 Base64 则按 UTF-8 原文处理。
     * 无论哪种，解码后都必须 ≥ 32 字节。
     */
    private byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "未配置 JWT 密钥。请设置环境变量 LG_JWT_SECRET（Base64 编码的 32 字节随机值），"
                            + "生成方式：openssl rand -base64 32");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT 密钥强度不足：HS256 要求至少 " + MIN_KEY_BYTES + " 字节，当前 "
                            + bytes.length + " 字节。请用 openssl rand -base64 32 重新生成");
        }
        return bytes;
    }
}
