package com.legacyguard.module.asset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 资产模块参数。
 *
 * @param maxPerUser      单个用户可持有的资产上限
 * @param maxContentBytes 单条资产内容的字节上限（UTF-8）
 */
@ConfigurationProperties(prefix = "legacyguard.asset")
public record AssetProperties(int maxPerUser, int maxContentBytes) {

    public AssetProperties {
        if (maxPerUser <= 0) {
            maxPerUser = 100;
        }
        if (maxContentBytes <= 0) {
            maxContentBytes = 64 * 1024;
        }
    }
}
