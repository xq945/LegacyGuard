package com.legacyguard.common.crypto;

/**
 * 一次加密的完整产物，字段与 {@code digital_asset_secret} 表一一对应。
 *
 * @param cipherText  密文（Base64）
 * @param iv          GCM 随机 12 字节 nonce（Base64）
 * @param authTag     GCM 认证标签（Base64）
 * @param wrappedDek  被 KEK 包裹的数据密钥，格式 {@code Base64(kekIv || 密文 || tag)}
 * @param kekVersion  包裹 DEK 所用的主密钥版本
 * @param contentHash 明文 SHA-256（十六进制），用于解密后完整性校验
 * @param plainSize   明文字节数
 */
public record EncryptedPayload(
        String cipherText,
        String iv,
        String authTag,
        String wrappedDek,
        int kekVersion,
        String contentHash,
        int plainSize
) {

    /**
     * 防止密文意外进入日志。
     *
     * <p>{@code record} 默认的 {@code toString} 会打印每一个字段 ——
     * 一旦有人不小心 {@code log.info("{}", payload)}，密文与包裹密钥就会落到日志里。
     * 密文虽然不可直接读，但它是解密的全部必要材料之一，不该出现在日志系统中。
     */
    @Override
    public String toString() {
        return "EncryptedPayload[kekVersion=" + kekVersion
                + ", plainSize=" + plainSize
                + ", cipherText=<redacted>]";
    }
}
