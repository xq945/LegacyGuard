package com.legacyguard.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 主密钥（KEK）管理。
 *
 * <p><b>KEK 的定位</b>：它不直接加密资产内容，而是用来"包裹"每份资产各自的数据密钥（DEK）。
 * 这样主密钥泄露不会直接暴露密文（还得再解一层），轮换主密钥时也只需重新包裹
 * 32 字节的 DEK，而不是把所有资产重新加密一遍。
 *
 * <p><b>存放要求</b>：
 * <ul>
 *   <li>只从环境变量注入，不写进代码、配置文件、镜像层</li>
 *   <li>丢失即意味着所有资产永久无法解密 —— 这是刻意设计，不提供任何后门恢复</li>
 * </ul>
 *
 * <p><b>启动即校验</b>：密钥缺失或长度不足，应用直接启动失败。
 * 让它在启动时炸掉，好过运行到第一次加密时才暴露问题。
 */
@Component
public class KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(KeyProvider.class);

    /** AES-256 要求 32 字节 */
    private static final int KEY_BYTES = 32;

    private final Map<Integer, SecretKey> keys;
    private final int currentVersion;

    public KeyProvider(CryptoProperties properties) {
        this.keys = parse(properties.keys());
        this.currentVersion = properties.currentVersion();

        if (keys.isEmpty()) {
            throw new IllegalStateException(
                    "未配置资产加密主密钥。请设置环境变量 LG_KEK_KEYS，格式 \"1=<Base64 32字节>\"，"
                            + "生成方式：openssl rand -base64 32");
        }
        if (!keys.containsKey(currentVersion)) {
            throw new IllegalStateException(
                    "当前加密版本 " + currentVersion + " 没有对应的主密钥，已配置的版本: " + keys.keySet());
        }
        log.info("资产加密密钥已加载，可用版本 {}，当前版本 {}", keys.keySet(), currentVersion);
    }

    /** 取用于<b>加密</b>的主密钥（始终是当前版本）。 */
    public SecretKey currentKey() {
        return keys.get(currentVersion);
    }

    public int currentVersion() {
        return currentVersion;
    }

    /**
     * 取用于<b>解密</b>的主密钥。
     *
     * @throws IllegalStateException 该版本密钥已不在配置中（轮换时误删了旧密钥）
     */
    public SecretKey keyOf(int version) {
        SecretKey key = keys.get(version);
        if (key == null) {
            throw new IllegalStateException(
                    "缺少版本 " + version + " 的主密钥，无法解密历史数据。请恢复该版本的 LG_KEK_KEYS 配置");
        }
        return key;
    }

    /**
     * 解析 {@code "1=base64,2=base64"}。
     *
     * <p>单个密钥的格式错误直接抛异常终止启动 —— 不能"跳过坏的那个继续跑"，
     * 否则会在某个用户的数据解密失败时才发现配置有问题。
     */
    private Map<Integer, SecretKey> parse(String raw) {
        Map<Integer, SecretKey> result = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }

        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new IllegalStateException("主密钥配置格式错误，应为 \"版本=Base64密钥\"，实际: " + trimmed);
            }

            int version;
            try {
                version = Integer.parseInt(trimmed.substring(0, eq).trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("主密钥版本号不是数字: " + trimmed.substring(0, eq));
            }

            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(trimmed.substring(eq + 1).trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("主密钥不是合法的 Base64 编码（版本 " + version + "）");
            }

            if (bytes.length != KEY_BYTES) {
                throw new IllegalStateException(
                        "主密钥长度必须是 " + KEY_BYTES + " 字节（AES-256），版本 " + version
                                + " 实际为 " + bytes.length + " 字节");
            }

            result.put(version, new SecretKeySpec(bytes, "AES"));
        }
        return result;
    }
}
