package com.legacyguard.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加密配置。
 *
 * @param keys           主密钥集合，格式 {@code "1=<Base64>,2=<Base64>"}。
 *                       版本号用于密钥轮换：新数据用 {@code currentVersion} 加密，
 *                       旧数据按各自记录的 {@code kek_version} 解密。
 * @param currentVersion 当前用于加密的主密钥版本
 */
@ConfigurationProperties(prefix = "legacyguard.crypto")
public record CryptoProperties(String keys, int currentVersion) {

    public CryptoProperties {
        if (currentVersion <= 0) {
            currentVersion = 1;
        }
    }
}
