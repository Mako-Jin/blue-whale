package com.bluewhale.sts.auth.common.codec;

import com.bluewhale.sts.auth.common.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 口令哈希器测试 */
class PasswordHasherTest {

    private static PasswordHasher hasher(int iterations) {
        AuthProperties properties = new AuthProperties();
        properties.getPassword().setIterations(iterations);
        return new PasswordHasher(properties);
    }

    @Test
    @DisplayName("哈希后能校验通过，错误口令不通过")
    void shouldHashAndMatch() {
        PasswordHasher hasher = hasher(1000);
        String encoded = hasher.hash("S3cret!pass");

        assertTrue(hasher.matches("S3cret!pass", encoded));
        assertFalse(hasher.matches("s3cret!pass", encoded));
        assertFalse(hasher.matches("", encoded));
        assertFalse(hasher.matches(null, encoded));
    }

    @Test
    @DisplayName("同一口令两次哈希结果不同（盐随机）")
    void shouldUseRandomSalt() {
        PasswordHasher hasher = hasher(1000);
        assertNotEquals(hasher.hash("same-password"), hasher.hash("same-password"));
    }

    @Test
    @DisplayName("格式非法的存储值判失败而不是抛异常")
    void shouldTolerateMalformedStoredValue() {
        PasswordHasher hasher = hasher(1000);
        // 脏数据不该把一次登录变成 500：判失败即可
        assertFalse(hasher.matches("whatever", "not-a-valid-hash"));
        assertFalse(hasher.matches("whatever", "PBKDF2-SHA256$abc$def$ghi"));
        assertFalse(hasher.matches("whatever", "MD5$1$a$b"));
        assertFalse(hasher.matches("whatever", null));
    }

    @Test
    @DisplayName("存储参数低于当前配置时提示需要重算")
    void shouldDetectStaleParameters() {
        String oldEncoded = hasher(1000).hash("password-1");

        assertTrue(hasher(120_000).needsRehash(oldEncoded));
        assertFalse(hasher(1000).needsRehash(oldEncoded));
        // 已经用更高参数算过的哈希不需要再重算
        assertFalse(hasher(500).needsRehash(oldEncoded));
    }

    @Test
    @DisplayName("换了迭代次数后，旧哈希仍能被校验（参数随哈希存储）")
    void shouldStillVerifyAfterParameterChange() {
        String oldEncoded = hasher(1000).hash("password-2");

        // 这正是"参数随哈希存储"的价值：调大迭代次数不会让存量口令全部失效
        assertTrue(hasher(120_000).matches("password-2", oldEncoded));
    }

}
