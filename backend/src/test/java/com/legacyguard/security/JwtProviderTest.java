package com.legacyguard.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发器单元测试。
 *
 * <p>不启动 Spring 容器，纯对象测试 —— 这类测试应当在毫秒级完成。
 */
class JwtProviderTest {

    /** 32 字节随机值的 Base64，满足 HS256 强度要求 */
    private static final String VALID_SECRET = generateSecret(32);

    private static JwtProvider newProvider(Duration ttl) {
        return new JwtProvider(new JwtProperties(VALID_SECRET, ttl, "legacyguard-test"));
    }

    @Test
    @DisplayName("签发的令牌可以被解析回原始身份")
    void issueAndParse() {
        JwtProvider provider = newProvider(Duration.ofMinutes(30));

        String token = provider.issue(42L, "zhangsan", "USER");
        AuthPrincipal principal = provider.parse(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("zhangsan");
        assertThat(principal.role()).isEqualTo("USER");
        assertThat(principal.tokenId()).isNotBlank();
        assertThat(principal.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("管理员令牌的 isAdmin 为 true")
    void adminFlag() {
        JwtProvider provider = newProvider(Duration.ofMinutes(30));
        AuthPrincipal principal = provider.parse(provider.issue(1L, "root", "ADMIN"));
        assertThat(principal.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("篡改令牌内容后签名校验失败")
    void tamperedTokenRejected() {
        JwtProvider provider = newProvider(Duration.ofMinutes(30));
        String token = provider.issue(42L, "zhangsan", "USER");

        // 改动 payload 段中间的一个字符，签名必然不匹配
        String[] parts = token.split("\\.");
        char[] payload = parts[1].toCharArray();
        int mid = payload.length / 2;
        payload[mid] = payload[mid] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

        assertThatThrownBy(() -> provider.parse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("过期令牌被拒绝")
    void expiredTokenRejected() throws InterruptedException {
        JwtProvider provider = newProvider(Duration.ofMillis(50));
        String token = provider.issue(42L, "zhangsan", "USER");

        Thread.sleep(150);

        assertThatThrownBy(() -> provider.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("其它 issuer 签发的令牌不被接受")
    void foreignIssuerRejected() {
        JwtProvider foreign = new JwtProvider(
                new JwtProperties(VALID_SECRET, Duration.ofMinutes(30), "someone-else"));
        String token = foreign.issue(42L, "zhangsan", "USER");

        JwtProvider ours = newProvider(Duration.ofMinutes(30));

        assertThatThrownBy(() -> ours.parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("乱码令牌不会导致未捕获异常")
    void garbageTokenRejected() {
        JwtProvider provider = newProvider(Duration.ofMinutes(30));
        assertThatThrownBy(() -> provider.parse("not-a-jwt-at-all"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("密钥强度不足时拒绝启动，而不是降级运行")
    void weakSecretRejectedAtStartup() {
        String weak = Base64.getEncoder().encodeToString(generateSecret(16).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JwtProvider(
                new JwtProperties(weak, Duration.ofMinutes(30), "legacyguard-test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("强度不足");
    }

    @Test
    @DisplayName("未配置密钥时拒绝启动，并提示生成方式")
    void missingSecretRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtProvider(
                new JwtProperties("", Duration.ofMinutes(30), "legacyguard-test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LG_JWT_SECRET");
    }

    @Test
    @DisplayName("剩余有效期随令牌签发而减少")
    void remainingTtlDecreases() {
        JwtProvider provider = newProvider(Duration.ofMinutes(30));
        String token = provider.issue(42L, "zhangsan", "USER");

        Duration ttl = provider.remainingTtl(token);

        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofMinutes(30));
    }

    private static String generateSecret(int bytes) {
        String raw = "0123456789abcdef".repeat(bytes / 16 + 1).substring(0, bytes);
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
