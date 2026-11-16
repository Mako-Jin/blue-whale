package com.bluewhale.auth.infrastructure.config;

import com.bluewhale.auth.infrastructure.jwt.JwtKeyMaterial;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link JwksExposureGuardFilter} 单元测试：HS256 模式关闭 JWKS，RS256 模式放行。
 *
 * @author BlueWhale
 */
class JwksExposureGuardFilterTest {

    private static final String SECRET = "unit-test-secret-key-with-enough-length-0123456789";

    @Test
    @DisplayName("HS256 模式：/oauth2/jwks 返回 404，且不再进入后续过滤器")
    void shouldBlockJwksInHs256Mode() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        JwksExposureGuardFilter filter = new JwksExposureGuardFilter(new JwtKeyMaterial(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/jwks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(404, response.getStatus());
        assertNull(chain.getRequest(), "HS256 模式下不得继续执行授权服务器端点");
    }

    @Test
    @DisplayName("HS256 模式：尾斜杠写法同样被拦截，不能绕过守卫")
    void shouldBlockJwksWithTrailingSlash() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        JwksExposureGuardFilter filter = new JwksExposureGuardFilter(new JwtKeyMaterial(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/jwks/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(404, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("RS256 模式：/oauth2/jwks 放行（只发布公钥）")
    void shouldAllowJwksInRs256Mode() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setKeyId("whale-test");
        properties.setPrivateKey(pemPrivateKey());
        JwksExposureGuardFilter filter = new JwksExposureGuardFilter(new JwtKeyMaterial(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/jwks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "RS256 模式下 JWKS 应放行");
        assertEquals(200, response.getStatus());
    }

    /**
     * 生成测试用 RSA 私钥 PEM
     *
     * @return PKCS#8 PEM 文本
     * @throws Exception 生成失败
     */
    private static String pemPrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }
}
