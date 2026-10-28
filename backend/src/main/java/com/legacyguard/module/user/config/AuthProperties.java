package com.legacyguard.module.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 认证相关策略参数。
 *
 * @param maxLoginFailures 连续失败多少次后锁定账号
 * @param lockDuration     锁定时长
 * @param loginRateLimit   同一来源在窗口内允许的登录尝试次数（防爆破）
 * @param loginRateWindow  限流窗口长度
 */
@ConfigurationProperties(prefix = "legacyguard.auth")
public record AuthProperties(
        int maxLoginFailures,
        Duration lockDuration,
        int loginRateLimit,
        Duration loginRateWindow
) {

    public AuthProperties {
        if (maxLoginFailures <= 0) {
            maxLoginFailures = 5;
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            lockDuration = Duration.ofMinutes(15);
        }
        if (loginRateLimit <= 0) {
            loginRateLimit = 10;
        }
        if (loginRateWindow == null || loginRateWindow.isNegative() || loginRateWindow.isZero()) {
            loginRateWindow = Duration.ofMinutes(1);
        }
    }
}
