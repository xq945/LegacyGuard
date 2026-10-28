package com.legacyguard.common.crypto;

import com.legacyguard.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 资产内容的信封加密。
 *
 * <pre>
 *   KEK（主密钥，环境变量） ──包裹──> DEK（每份资产一个，随机 32 字节）
 *                                        └──AES-256-GCM──> 资产明文
 *
 *   入库：wrapped_dek, cipher_text, iv, auth_tag, content_hash, kek_version
 * </pre>
 *
 * <p><b>为什么用信封加密而不是主密钥直接加密内容</b>：
 * <ol>
 *   <li>主密钥泄露不等于全体数据泄露，攻击者还要再解一层</li>
 *   <li>轮换主密钥只需重新包裹 32 字节的 DEK，不必重新加密所有资产</li>
 *   <li>每份资产独立密钥，单条记录的密钥暴露不影响其他记录</li>
 * </ol>
 *
 * <p><b>为什么把 assetId 作为 AAD</b>：附加认证数据参与 GCM 的完整性计算，
 * 但不加密。如果有人把 A 资产的密文行挪到 B 资产的记录上，
 * 解密时 AAD 不匹配会直接失败 —— 而不是悄悄解出别人的内容。
 */
@Component
public class AssetCipher {

    private static final Logger log = LoggerFactory.getLogger(AssetCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";

    /** GCM 认证标签长度（位） */
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;

    /** GCM 推荐 96 位 nonce */
    private static final int IV_BYTES = 12;

    /** AES-256 数据密钥长度 */
    private static final int DEK_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder B64_DECODER = Base64.getDecoder();

    private final KeyProvider keyProvider;

    public AssetCipher(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /**
     * 加密。
     *
     * @param assetId 资产 ID，作为 AAD 绑定密文与记录。**必须先有 ID 才能加密**，
     *                因此调用方需要先插入元数据行拿到自增主键，再加密写密文表 ——
     *                两步在同一事务内完成
     */
    public EncryptedPayload encrypt(Long assetId, String plaintext) {
        byte[] plain = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] aad = aadOf(assetId);

        try {
            // 1. 生成该资产专属的数据密钥
            byte[] dekBytes = new byte[DEK_BYTES];
            RANDOM.nextBytes(dekBytes);
            SecretKey dek = new SecretKeySpec(dekBytes, ALGORITHM);

            // 2. 用 DEK 加密内容
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);

            Cipher contentCipher = Cipher.getInstance(TRANSFORMATION);
            contentCipher.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(TAG_BITS, iv));
            contentCipher.updateAAD(aad);

            // Java 的 GCM 输出是 "密文 || tag"，这里拆开分别存储
            byte[] cipherWithTag = contentCipher.doFinal(plain);
            byte[] cipherText = Arrays.copyOfRange(cipherWithTag, 0, cipherWithTag.length - TAG_BYTES);
            byte[] tag = Arrays.copyOfRange(cipherWithTag, cipherWithTag.length - TAG_BYTES, cipherWithTag.length);

            // 3. 用 KEK 包裹 DEK
            byte[] kekIv = new byte[IV_BYTES];
            RANDOM.nextBytes(kekIv);

            Cipher keyCipher = Cipher.getInstance(TRANSFORMATION);
            keyCipher.init(Cipher.ENCRYPT_MODE, keyProvider.currentKey(), new GCMParameterSpec(TAG_BITS, kekIv));
            byte[] wrappedWithTag = keyCipher.doFinal(dekBytes);

            byte[] wrappedBlob = new byte[kekIv.length + wrappedWithTag.length];
            System.arraycopy(kekIv, 0, wrappedBlob, 0, kekIv.length);
            System.arraycopy(wrappedWithTag, 0, wrappedBlob, kekIv.length, wrappedWithTag.length);

            return new EncryptedPayload(
                    B64_ENCODER.encodeToString(cipherText),
                    B64_ENCODER.encodeToString(iv),
                    B64_ENCODER.encodeToString(tag),
                    B64_ENCODER.encodeToString(wrappedBlob),
                    keyProvider.currentVersion(),
                    sha256Hex(plain),
                    plain.length);

        } catch (GeneralSecurityException e) {
            // 加密失败属于系统级故障（JDK 配置、密钥长度限制等），
            // 不是用户的输入问题，必须暴露给运维而不是包装成业务异常
            log.error("资产加密失败 assetId={}", assetId, e);
            throw new IllegalStateException("资产加密失败", e);
        }
    }

    /**
     * 解密并校验完整性。
     *
     * @throws IllegalStateException 密钥版本缺失、密文被篡改、AAD 不匹配或摘要不符
     */
    public String decrypt(Long assetId, EncryptedPayload payload) {
        byte[] aad = aadOf(assetId);

        try {
            // 1. 解包 DEK
            byte[] wrappedBlob = B64_DECODER.decode(payload.wrappedDek());
            if (wrappedBlob.length <= IV_BYTES) {
                throw new IllegalStateException("包裹密钥格式非法");
            }
            byte[] kekIv = Arrays.copyOfRange(wrappedBlob, 0, IV_BYTES);
            byte[] wrappedWithTag = Arrays.copyOfRange(wrappedBlob, IV_BYTES, wrappedBlob.length);

            Cipher keyCipher = Cipher.getInstance(TRANSFORMATION);
            keyCipher.init(Cipher.DECRYPT_MODE, keyProvider.keyOf(payload.kekVersion()),
                    new GCMParameterSpec(TAG_BITS, kekIv));
            byte[] dekBytes = keyCipher.doFinal(wrappedWithTag);

            // 2. 用 DEK 解密内容
            byte[] iv = B64_DECODER.decode(payload.iv());
            byte[] cipherText = B64_DECODER.decode(payload.cipherText());
            byte[] tag = B64_DECODER.decode(payload.authTag());

            byte[] cipherWithTag = new byte[cipherText.length + tag.length];
            System.arraycopy(cipherText, 0, cipherWithTag, 0, cipherText.length);
            System.arraycopy(tag, 0, cipherWithTag, cipherText.length, tag.length);

            Cipher contentCipher = Cipher.getInstance(TRANSFORMATION);
            contentCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dekBytes, ALGORITHM),
                    new GCMParameterSpec(TAG_BITS, iv));
            contentCipher.updateAAD(aad);
            byte[] plain = contentCipher.doFinal(cipherWithTag);

            // 3. 摘要比对。GCM 的 tag 已经保证了完整性，这层是纵深防御：
            //    它能发现"tag 校验通过但内容与记录时不一致"这类实现层面的错误
            String actual = sha256Hex(plain);
            if (!MessageDigest.isEqual(
                    actual.getBytes(StandardCharsets.UTF_8),
                    payload.contentHash().getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalStateException("资产内容摘要校验失败，数据可能已损坏 assetId=" + assetId);
            }

            return new String(plain, StandardCharsets.UTF_8);

        } catch (GeneralSecurityException e) {
            // AEADBadTagException 落在这里：密文被改动，或 AAD（assetId）与加密时不符
            log.error("资产解密失败，密文可能被篡改或密钥不匹配 assetId={}", assetId, e);
            throw new IllegalStateException("资产解密失败", e);
        }
    }

    /**
     * AAD 把密文与具体资产绑定。
     */
    private byte[] aadOf(Long assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("加密/解密时必须提供 assetId 作为 AAD");
        }
        return ("asset:" + assetId).getBytes(StandardCharsets.UTF_8);
    }

    private String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
