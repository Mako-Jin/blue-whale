package com.bluewhale.security.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TokenBlacklistKeys} 单元测试：登出黑名单键与 refresh 轮换两类新键的格式约定（AUTH-39 / A1）。
 *
 * @author BlueWhale
 */
class TokenBlacklistKeysTest {

    /** 登出黑名单键前缀（既有约定，不得漂移） */
    private static final String BLACKLIST_PREFIX = "auth:token:blacklist:";

    /** 测试用 jti */
    private static final String JTI = "9f1f0b3e-8b9a-4c2d-9f6e-1a2b3c4d5e6f";

    /** 测试用 familyId */
    private static final String FAMILY_ID = "5a4b3c2d-1e0f-4a9b-8c7d-6e5f4a3b2c1d";

    /** SHA-256 十六进制摘要模式 */
    private static final String SHA256_HEX_PATTERN = "[0-9a-f]{64}";

    @Test
    @DisplayName("blacklistKey：前缀固定且后缀为 SHA-256 摘要，同 token 稳定、不同 token 相异")
    void blacklistKeyShouldBeStableAndDistinct() {
        String key = TokenBlacklistKeys.blacklistKey("token-a");

        assertTrue(key.startsWith(BLACKLIST_PREFIX), "登出黑名单键前缀不得漂移");
        assertTrue(key.substring(BLACKLIST_PREFIX.length()).matches(SHA256_HEX_PATTERN),
                "后缀应为 64 位十六进制摘要（不落 token 明文）");
        assertEquals(key, TokenBlacklistKeys.blacklistKey("token-a"));
        assertNotEquals(key, TokenBlacklistKeys.blacklistKey("token-b"));
    }

    @Test
    @DisplayName("refreshUsedKey：键为 whale:auth:refresh:used:{jti}，与登出黑名单前缀互不干扰")
    void refreshUsedKeyShouldFollowConvention() {
        String key = TokenBlacklistKeys.refreshUsedKey(JTI);

        assertEquals(TokenBlacklistKeys.REFRESH_USED_PREFIX + JTI, key);
        assertEquals("whale:auth:refresh:used:" + JTI, key);
        assertTrue(key.startsWith("whale:auth:refresh:used:"), "须遵守 whale:<域>:<用途>:<标识> 约定");
    }

    @Test
    @DisplayName("refreshFamilyKey：键为 whale:auth:refresh:family:{familyId}")
    void refreshFamilyKeyShouldFollowConvention() {
        String key = TokenBlacklistKeys.refreshFamilyKey(FAMILY_ID);

        assertEquals(TokenBlacklistKeys.REFRESH_FAMILY_PREFIX + FAMILY_ID, key);
        assertEquals("whale:auth:refresh:family:" + FAMILY_ID, key);
        assertNotEquals(TokenBlacklistKeys.refreshUsedKey(FAMILY_ID), key,
                "已消费键与 family 吊销键必须落在不同前缀空间");
    }
}
