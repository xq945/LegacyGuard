package com.legacyguard.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 信封加密测试。
 *
 * <p>这些用例比业务测试更重要 —— 加密写错的后果是静默的：
 * 系统照常运行，但资产要么不再安全，要么再也解不开。
 */
class AssetCipherTest {

    private static final String KEK_V1 = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private AssetCipher cipher;

    @BeforeEach
    void setUp() {
        KeyProvider keyProvider = new KeyProvider(new CryptoProperties("1=" + KEK_V1, 1));
        cipher = new AssetCipher(keyProvider);
    }

    @Test
    @DisplayName("加密后能原样解回")
    void roundTrip() {
        EncryptedPayload payload = cipher.encrypt(1L, "书房第二个抽屉里有块硬盘");

        assertThat(cipher.decrypt(1L, payload)).isEqualTo("书房第二个抽屉里有块硬盘");
    }

    @Test
    @DisplayName("密文里不含明文")
    void cipherTextDoesNotLeakPlaintext() {
        String secret = "SuperSecret-12345";
        EncryptedPayload payload = cipher.encrypt(1L, secret);

        String rawCipher = new String(Base64.getDecoder().decode(payload.cipherText()),
                StandardCharsets.UTF_8);
        assertThat(rawCipher).doesNotContain(secret);
        assertThat(payload.cipherText()).doesNotContain(secret);
    }

    @Test
    @DisplayName("同一明文两次加密产生不同密文（随机 IV/DEK）")
    void samePlaintextProducesDifferentCipherText() {
        EncryptedPayload first = cipher.encrypt(1L, "same content");
        EncryptedPayload second = cipher.encrypt(1L, "same content");

        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.cipherText()).isNotEqualTo(second.cipherText());
        assertThat(first.wrappedDek()).isNotEqualTo(second.wrappedDek());
    }

    @Test
    @DisplayName("assetId 不同则解密失败 —— AAD 把密文绑死在具体记录上")
    void wrongAssetIdFails() {
        EncryptedPayload payload = cipher.encrypt(1L, "只属于 1 号资产的内容");

        // 如果有人把这条密文行挪到 2 号资产上，解密会立刻失败
        assertThatThrownBy(() -> cipher.decrypt(2L, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    @DisplayName("密文被篡改时解密失败")
    void tamperedCipherTextFails() {
        EncryptedPayload payload = cipher.encrypt(1L, "内容");

        byte[] raw = Base64.getDecoder().decode(payload.cipherText());
        raw[0] ^= 0x01;   // 翻转一个比特
        EncryptedPayload tampered = new EncryptedPayload(
                Base64.getEncoder().encodeToString(raw),
                payload.iv(), payload.authTag(), payload.wrappedDek(),
                payload.kekVersion(), payload.contentHash(), payload.plainSize());

        assertThatThrownBy(() -> cipher.decrypt(1L, tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("认证标签被篡改时解密失败")
    void tamperedAuthTagFails() {
        EncryptedPayload payload = cipher.encrypt(1L, "内容");

        byte[] tag = Base64.getDecoder().decode(payload.authTag());
        tag[0] ^= 0x01;
        EncryptedPayload tampered = new EncryptedPayload(
                payload.cipherText(), payload.iv(),
                Base64.getEncoder().encodeToString(tag),
                payload.wrappedDek(), payload.kekVersion(),
                payload.contentHash(), payload.plainSize());

        assertThatThrownBy(() -> cipher.decrypt(1L, tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("包裹的数据密钥被篡改时解密失败")
    void tamperedWrappedDekFails() {
        EncryptedPayload payload = cipher.encrypt(1L, "内容");

        byte[] wrapped = Base64.getDecoder().decode(payload.wrappedDek());
        wrapped[wrapped.length - 1] ^= 0x01;
        EncryptedPayload tampered = new EncryptedPayload(
                payload.cipherText(), payload.iv(), payload.authTag(),
                Base64.getEncoder().encodeToString(wrapped),
                payload.kekVersion(), payload.contentHash(), payload.plainSize());

        assertThatThrownBy(() -> cipher.decrypt(1L, tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("摘要与明文一致，且记录的是 SHA-256")
    void contentHashIsSha256OfPlaintext() throws Exception {
        String plaintext = "摘要校验";
        EncryptedPayload payload = cipher.encrypt(1L, plaintext);

        byte[] digest = MessageDigest.getInstance("SHA-256").digest(plaintext.getBytes(StandardCharsets.UTF_8));
        StringBuilder expected = new StringBuilder();
        for (byte b : digest) {
            expected.append(String.format("%02x", b));
        }

        assertThat(payload.contentHash()).isEqualTo(expected.toString());
        assertThat(payload.plainSize()).isEqualTo(plaintext.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("记录当前主密钥版本，便于日后轮换")
    void recordsKekVersion() {
        EncryptedPayload payload = cipher.encrypt(1L, "x");
        assertThat(payload.kekVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("payload 的 toString 不泄露密文")
    void toStringRedactsSecrets() {
        EncryptedPayload payload = cipher.encrypt(1L, "x");
        assertThat(payload.toString())
                .doesNotContain(payload.cipherText())
                .doesNotContain(payload.wrappedDek())
                .contains("redacted");
    }
}
