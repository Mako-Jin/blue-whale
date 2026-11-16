package com.bluewhale.auth.infrastructure.jwt;

import com.bluewhale.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtKeyMaterial} 单元测试：RS256 签发/验签、kid 写入、HS256 过渡期兼容。
 *
 * @author BlueWhale
 */
class JwtKeyMaterialTest {

    private static final String SECRET = "unit-test-secret-key-with-enough-length-0123456789";
    private static final String KEY_ID = "whale-2026-11";
    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 100L;
    private static final String USERNAME = "alice";
    private static final long TTL_MILLIS = 60_000L;

    @Test
    @DisplayName("配置 RSA 私钥：签发 RS256 且 JWT 头携带 kid，可被自身公钥验签")
    void shouldSignRs256WithKeyId() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        JwtProperties properties = propertiesWithPrivateKey(keyPair);

        JwtKeyMaterial material = new JwtKeyMaterial(properties);
        JwtTokenProvider provider = new JwtTokenProvider(properties, material);
        String token = provider.generateAccessToken(TENANT_ID, USER_ID, USERNAME,
                java.util.List.of("admin"), false);

        assertTrue(material.isAsymmetric());
        assertEquals(KEY_ID, material.getKeyId());
        assertEquals(1, material.getVerificationKeys().size());

        var parsed = Jwts.parser()
                .keyLocator(material::verificationKey)
                .build()
                .parseSignedClaims(token);
        JwsHeader header = parsed.getHeader();
        assertEquals("RS256", header.getAlgorithm());
        assertEquals(KEY_ID, header.getKeyId());
        assertEquals(USER_ID, ((Number) parsed.getPayload().get(JwtTokenProvider.CLAIM_USER_ID)).longValue());
    }

    @Test
    @DisplayName("RS256 模式下仍可验签过渡期 HS256 旧令牌")
    void shouldStillVerifyLegacyHs256Token() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        JwtProperties properties = propertiesWithPrivateKey(keyPair);
        JwtKeyMaterial material = new JwtKeyMaterial(properties);
        JwtTokenProvider provider = new JwtTokenProvider(properties, material);

        SecretKey legacyKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String legacyToken = Jwts.builder()
                .subject(USERNAME)
                .claim(JwtTokenProvider.CLAIM_USER_ID, USER_ID)
                .claim(JwtTokenProvider.CLAIM_TENANT_ID, TENANT_ID)
                .claim(JwtTokenProvider.CLAIM_TOKEN_TYPE, JwtTokenProvider.TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TTL_MILLIS))
                .signWith(legacyKey, Jwts.SIG.HS256)
                .compact();

        Claims claims = provider.parse(legacyToken);

        assertNotNull(claims);
        assertEquals(USERNAME, claims.getSubject());
    }

    @Test
    @DisplayName("未配置 RSA 私钥：保持 HS256 且 JWKS 模式标记为非对称关闭")
    void shouldStayHs256WithoutPrivateKey() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);

        JwtKeyMaterial material = new JwtKeyMaterial(properties);
        JwtTokenProvider provider = new JwtTokenProvider(properties, material);

        assertFalse(material.isAsymmetric());
        assertTrue(material.getVerificationKeys().isEmpty());
        assertNotNull(provider.parse(provider.generateAccessToken(TENANT_ID, USER_ID, USERNAME,
                java.util.List.of("admin"), false)));
    }

    @Test
    @DisplayName("轮换：旧 kid 公钥仍在 JWKS 中，存量令牌可继续验签")
    void shouldVerifyTokenSignedByPreviousKey() throws Exception {
        KeyPair previous = generateRsaKeyPair();
        KeyPair current = generateRsaKeyPair();
        JwtProperties properties = propertiesWithPrivateKey(current);
        properties.getPreviousPublicKeys().put("whale-old",
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getMimeEncoder().encodeToString(previous.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----");
        JwtKeyMaterial material = new JwtKeyMaterial(properties);
        JwtTokenProvider provider = new JwtTokenProvider(properties, material);

        Date now = new Date();
        String oldToken = Jwts.builder()
                .subject(USERNAME)
                .claim(JwtTokenProvider.CLAIM_USER_ID, USER_ID)
                .claim(JwtTokenProvider.CLAIM_TENANT_ID, TENANT_ID)
                .claim(JwtTokenProvider.CLAIM_TOKEN_TYPE, JwtTokenProvider.TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TTL_MILLIS))
                .header().keyId("whale-old").and()
                .signWith(previous.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertEquals(2, material.getVerificationKeys().size());
        assertEquals(USERNAME, provider.parse(oldToken).getSubject());
    }

    /**
     * 生成 2048 位 RSA 密钥对
     *
     * @return 密钥对
     * @throws Exception 生成失败
     */
    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /**
     * 构造 RS256 配置（PKCS#8 PEM 私钥）
     *
     * @param keyPair RSA 密钥对
     * @return JWT 配置
     */
    private static JwtProperties propertiesWithPrivateKey(KeyPair keyPair) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setKeyId(KEY_ID);
        properties.setPrivateKey("-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        return properties;
    }
}
