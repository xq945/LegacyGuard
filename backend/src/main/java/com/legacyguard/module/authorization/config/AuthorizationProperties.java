package com.legacyguard.module.authorization.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 授权案件参数。
 *
 * @param waitPeriodDays    审核通过后的等待期（天）。等待期内可提异议，到期才生效
 * @param maxAssetsPerCase  单个案件最多覆盖的资产数
 */
@ConfigurationProperties(prefix = "legacyguard.authorization")
public record AuthorizationProperties(int waitPeriodDays, int maxAssetsPerCase) {

    public AuthorizationProperties {
        if (waitPeriodDays <= 0) {
            waitPeriodDays = 7;
        }
        if (maxAssetsPerCase <= 0) {
            maxAssetsPerCase = 100;
        }
    }
}
