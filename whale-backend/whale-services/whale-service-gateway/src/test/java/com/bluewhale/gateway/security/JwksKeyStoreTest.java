package com.bluewhale.gateway.security;

import com.bluewhale.gateway.config.WhaleGatewayProperties;
import io.jsonwebtoken.security.Jwks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link JwksKeyStore} 单元测试：kid 选择、缓存、未知 kid 强制刷新、对称密钥拒收、HS256 回落。
 *
 * @author BlueWhale
 */
class JwksKeyStoreTest {

    private static final String SECRET = "unit-test-secret-key-with-enough-length-0123456789";
    private static final String JWKS_URI = "http://auth/oauth2/jwks";

    @Test
    @DisplayName("resolve：按 kid 选择公钥，重复解析命中本地缓存（只拉取一次）")
    void shouldSelectKeyByKidAndCache() throws Exception {
        KeyPair first = generateRsaKeyPair();
        KeyPair second = generateRsaKeyPair();
        AtomicInteger calls = new AtomicInteger();
        JwksKeyStore store = newStore(jwks(first, "k1", second, "k2"), calls);

        Key resolved = store.resolve(new TokenHeader("k2", "RS256")).block();

        assertEquals(second.getPublic(), resolved);
        assertEquals(1, calls.get());
        assertEquals(second.getPublic(), store.resolve(new TokenHeader("k2", "RS256")).block());
        assertEquals(1, calls.get(), "缓存命中不应再次拉取 JWKS");
    }

    @Test
    @DisplayName("resolve：未知 kid 强制刷新一次，轮换后的新公钥立即可用")
    void shouldRefreshOnceForUnknownKid() throws Exception {
        KeyPair first = generateRsaKeyPair();
        KeyPair second = generateRsaKeyPair();
        AtomicInteger calls = new AtomicInteger();
        JwksKeyStore store = newStore(null, calls, jwks(first, "k1"), jwks(second, "k2"));

        Key resolved = store.resolve(new TokenHeader("k2", "RS256")).block();

        assertEquals(second.getPublic(), resolved);
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("resolve：并发未知 kid 只触发一次强制刷新（避免惊群）")
    void shouldCoalesceConcurrentForcedRefresh() throws Exception {
        KeyPair first = generateRsaKeyPair();
        KeyPair second = generateRsaKeyPair();
        AtomicInteger calls = new AtomicInteger();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            int index = calls.getAndIncrement();
            String body = index == 0 ? jwks(first, "k1") : jwks(second, "k2");
            Mono<ClientResponse> response = Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
            // 第二个响应延迟返回，确保并发请求落在同一次刷新窗口内
            return index == 0 ? response : response.delayElement(java.time.Duration.ofMillis(80));
        });
        JwksKeyStore store = newStore(builder);

        store.resolve(new TokenHeader("k1", "RS256")).block();
        java.util.List<Key> keys = reactor.core.publisher.Flux.range(0, 8)
                .flatMap(i -> store.resolve(new TokenHeader("k2", "RS256"))
                        .subscribeOn(reactor.core.scheduler.Schedulers.parallel()))
                .collectList()
                .block();

        assertNotNull(keys);
        assertEquals(8, keys.size());
        assertEquals(2, calls.get(), "8 个并发未知 kid 只应触发一次强制刷新");
    }

    @Test
    @DisplayName("resolve：JWKS 只含对称密钥时拒绝（公开 JWKS 不得发布签名密钥）")
    void shouldRejectSymmetricJwks() throws Exception {
        String json = "{\"keys\":[{\"kty\":\"oct\",\"kid\":\"oct-1\",\"k\":\""
                + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes())
                + "\"}]}";
        AtomicInteger calls = new AtomicInteger();
        JwksKeyStore store = newStore(json, calls);

        assertThrows(IllegalStateException.class,
                () -> store.resolve(new TokenHeader("oct-1", "RS256")).block());
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("resolve：HS256 令牌直接使用共享密钥，不访问 JWKS")
    void shouldUseSharedKeyForHs256WithoutHttp() {
        AtomicInteger calls = new AtomicInteger();
        JwksKeyStore store = newStore(null, calls);

        Key resolved = store.resolve(new TokenHeader("", "HS256")).block();

        assertNotNull(resolved);
        assertEquals(0, calls.get());
    }

    private static JwksKeyStore newStore(WebClient.Builder builder) {
        WhaleGatewayProperties properties = new WhaleGatewayProperties();
        properties.getJwt().setSecret(SECRET);
        properties.getJwt().setJwksEnabled(true);
        properties.getJwt().setJwksUri(JWKS_URI);
        return new JwksKeyStore(properties, builder);
    }

    /**
     * 构造 JWKS 仓库
     *
     * @param json  固定响应体（{@code null} 时使用 {@code responses} 依次返回）
     * @param calls 调用计数
     * @param responses 依次响应体
     * @return JWKS 仓库
     */
    private static JwksKeyStore newStore(String json, AtomicInteger calls, String... responses) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            int index = calls.getAndIncrement();
            String body = json != null ? json
                    : responses[Math.min(index, responses.length - 1)];
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        });
        WhaleGatewayProperties properties = new WhaleGatewayProperties();
        properties.getJwt().setSecret(SECRET);
        properties.getJwt().setJwksEnabled(true);
        properties.getJwt().setJwksUri(JWKS_URI);
        return new JwksKeyStore(properties, builder);
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
     * 构造两把公钥的 JWKS JSON
     *
     * @param first  第一把密钥对
     * @param firstKid 第一把 kid
     * @param second 第二把密钥对
     * @param secondKid 第二把 kid
     * @return JWKS JSON
     */
    private static String jwks(KeyPair first, String firstKid, KeyPair second, String secondKid) {
        return "{\"keys\":[" + jwkJson(first, firstKid) + "," + jwkJson(second, secondKid) + "]}";
    }

    /**
     * 构造单把公钥的 JWKS JSON
     *
     * @param keyPair 密钥对
     * @param kid     kid
     * @return JWKS JSON
     */
    private static String jwks(KeyPair keyPair, String kid) {
        return "{\"keys\":[" + jwkJson(keyPair, kid) + "]}";
    }

    /**
     * 构造单个公钥 JWK JSON
     *
     * @param keyPair 密钥对
     * @param kid     kid
     * @return JWK JSON
     */
    private static String jwkJson(KeyPair keyPair, String kid) {
        return Jwks.json(Jwks.builder().key(keyPair.getPublic()).id(kid).build());
    }
}
