package com.legacyguard.module.asset.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 资产密文，对应 {@code digital_asset_secret}。
 *
 * <p>与 {@link DigitalAsset} 是 1:1，主键即 {@code asset_id}。
 *
 * <p><b>本类刻意不提供 {@code toString}</b>：即使 Lombok 的 {@code @Getter/@Setter}
 * 不生成默认 toString，也要避免有人后来加上 {@code @Data} 或
 * {@code @ToString} 导致密文被打进日志。需要调试时请看 {@code assetId} 与 {@code plainSize}。
 */
@Getter
@Setter
@TableName("digital_asset_secret")
public class DigitalAssetSecret {

    /** 与 digital_asset 共享主键，非自增 */
    @TableId(type = IdType.INPUT)
    private Long assetId;

    /** 被 KEK 包裹的数据密钥，Base64(kekIv || 密文 || tag) */
    private String wrappedDek;

    /** 包裹 DEK 所用的主密钥版本 */
    private Integer kekVersion;

    /** 资产内容密文（Base64） */
    private String cipherText;

    /** GCM nonce（Base64） */
    private String iv;

    /** GCM 认证标签（Base64） */
    private String authTag;

    /** 明文 SHA-256，解密后比对 */
    private String contentHash;

    /** 明文字节数 */
    private Integer plainSize;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 只输出非敏感字段。任何日志、异常信息里出现本对象时，
     * 都不会带出密文与包裹密钥。
     */
    @Override
    public String toString() {
        return "DigitalAssetSecret[assetId=" + assetId
                + ", kekVersion=" + kekVersion
                + ", plainSize=" + plainSize
                + ", cipherText=<redacted>]";
    }
}
