package com.legacyguard.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 主密钥加载测试。
 *
 * <p>重点验证"配置有问题就在启动时炸掉"——
 * 加密模块最怕的不是起不来，而是带着错误密钥跑起来，
 * 把用户数据加密成永远解不开的形态。
 */
class KeyProviderTest {

    private static String keyOfLength(int bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        return Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("未配置密钥时拒绝启动")
    void missingKeysRejected() {
        assertThatThrownBy(() -> new KeyProvider(new CryptoProperties("", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LG_KEK_KEYS");
    }

    @Test
    @DisplayName("密钥长度不是 32 字节时拒绝启动")
    void wrongKeyLengthRejected() {
        assertThatThrownBy(() -> new KeyProvider(new CryptoProperties("1=" + keyOfLength(16), 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    @DisplayName("格式错误时拒绝启动")
    void malformedEntryRejected() {
        assertThatThrownBy(() -> new KeyProvider(new CryptoProperties("noseparator", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("格式错误");
    }

    @Test
    @DisplayName("当前版本没有对应密钥时拒绝启动")
    void missingCurrentVersionRejected() {
        assertThatThrownBy(() -> new KeyProvider(new CryptoProperties("1=" + keyOfLength(32), 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有对应的主密钥");
    }

    @Test
    @DisplayName("支持多版本并存，用于密钥轮换")
    void supportsMultipleVersions() {
        String keys = "1=" + keyOfLength(32) + ",2=" + keyOfLength(32);
        KeyProvider provider = new KeyProvider(new CryptoProperties(keys, 2));

        assertThat(provider.currentVersion()).isEqualTo(2);
        assertThat(provider.currentKey()).isNotNull();
        // 旧版本密钥仍可取到，保证历史数据可解密
        assertThat(provider.keyOf(1)).isNotNull();
    }

    @Test
    @DisplayName("请求一个不存在的版本时给出明确错误，而不是返回 null")
    void unknownVersionThrows() {
        KeyProvider provider = new KeyProvider(new CryptoProperties("1=" + keyOfLength(32), 1));

        assertThatThrownBy(() -> provider.keyOf(9))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("版本 9");
    }
}
