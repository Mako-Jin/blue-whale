package com.bluewhale.sts.auth.common.codec;

import com.bluewhale.sts.auth.common.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 编解码器测试。
 *
 * <p>重点不在"正常签发能验过"（那是最容易的部分），而在**三类攻击必须被挡住**：
 * 篡改载荷、算法降级（alg 混淆）、过期令牌。
 * 这三条任何一条失守，整套认证就等于没有。
 */
class JwtCodecTest {

    private static final String SECRET =
            "unit-test-secret-0123456789-0123456789-0123456789-0123456789-0123456789";

    private JwtCodec codec;

    @BeforeEach
    void setUp() {
        codec = new JwtCodec(propertiesWithSecret(SECRET), JsonMapper.builder().build());
    }

    @Test
    @DisplayName("签发 → 校验：载荷原样还原")
    void shouldRoundTrip() {
        long now = System.currentTimeMillis();
        String token = codec.issue(42L, 7L, "alice", "access", "jti-1", now, Duration.ofMinutes(30));

        JwtPayload payload = codec.verify(token, now + 1000);

        assertEquals(42L, payload.userId());
        assertEquals(7L, payload.tenantId());
        assertEquals("alice", payload.username());
        assertEquals("jti-1", payload.jti());
        assertTrue(payload.isAccessToken());
        assertFalse(payload.isRefreshToken());
    }

    @Test
    @DisplayName("篡改载荷后签名不再匹配")
    void shouldRejectTamperedPayload() {
        long now = System.currentTimeMillis();
        String token = codec.issue(1L, 1L, "bob", "access", "jti-2", now, Duration.ofMinutes(30));

        // 把载荷改成"我是 999 号用户"，签名部分保持原样
        String[] parts = token.split("\\.");
        String forgedPayload = base64Url("""
                {"sub":"999","uid":999,"tid":1,"uname":"bob","typ":"access","jti":"jti-2",
                 "iss":"bluewhale","aud":"bluewhale-api","iat":%d,"exp":%d}
                """.formatted(now / 1000, now / 1000 + 1800));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThrows(JwtVerificationException.class, () -> codec.verify(forged, now));
    }

    @Test
    @DisplayName("算法降级（把 HS512 改成 HS256）被拒绝")
    void shouldRejectAlgorithmDowngrade() throws Exception {
        long now = System.currentTimeMillis();
        // 用**同一个密钥**手工签一个 alg=HS256 的令牌：
        // 签名本身是合法的，唯一的问题就是算法不对 —— 这正是 alg 混淆攻击的形态
        String header = """
                {"alg":"HS256","typ":"JWT"}""";
        String payload = """
                {"sub":"1","uid":1,"tid":1,"uname":"bob","typ":"access","jti":"jti-3",
                 "iss":"bluewhale","aud":"bluewhale-api","iat":%d,"exp":%d}
                """.formatted(now / 1000, now / 1000 + 1800);
        String token = signRaw(header, payload);

        assertThrows(JwtVerificationException.class, () -> codec.verify(token, now));
    }

    @Test
    @DisplayName("alg=none 的令牌被拒绝")
    void shouldRejectAlgNone() {
        long now = System.currentTimeMillis();
        String header = """
                {"alg":"none","typ":"JWT"}""";
        String payload = """
                {"sub":"1","uid":1,"tid":1,"uname":"bob","typ":"access","jti":"jti-4",
                 "iss":"bluewhale","aud":"bluewhale-api","iat":%d,"exp":%d}
                """.formatted(now / 1000, now / 1000 + 1800);
        String token = base64Url(header) + "." + base64Url(payload) + ".";

        assertThrows(JwtVerificationException.class, () -> codec.verify(token, now));
    }

    @Test
    @DisplayName("过期令牌抛过期异常，可与普通校验失败区分")
    void shouldRejectExpired() {
        long now = System.currentTimeMillis();
        String token = codec.issue(1L, 1L, "bob", "access", "jti-5", now - 7200_000L, Duration.ofMinutes(30));

        assertThrows(JwtExpiredException.class, () -> codec.verify(token, now));
    }

    @Test
    @DisplayName("用另一个密钥签发的令牌被拒绝")
    void shouldRejectForeignSignature() {
        JwtCodec other = new JwtCodec(propertiesWithSecret("another-secret-completely-different-000000000000000000000000000000000000000000"),
                JsonMapper.builder().build());
        long now = System.currentTimeMillis();
        String token = other.issue(1L, 1L, "bob", "access", "jti-6", now, Duration.ofMinutes(30));

        assertThrows(JwtVerificationException.class, () -> codec.verify(token, now));
    }

    @Test
    @DisplayName("密钥为空时构造失败，把配置缺失暴露在启动阶段")
    void shouldFailFastOnBlankSecret() {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setSecret("  ");
        assertThrows(IllegalStateException.class,
                () -> new JwtCodec(properties, JsonMapper.builder().build()));
    }

    private static AuthProperties propertiesWithSecret(String secret) {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setSecret(secret);
        return properties;
    }

    private String signRaw(String headerJson, String payloadJson) throws Exception {
        String input = base64Url(headerJson) + "." + base64Url(payloadJson);
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return input + "." + base64Url(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

}
