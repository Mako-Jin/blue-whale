package com.bluewhale.gateway.security;

import com.bluewhale.gateway.config.WhaleGatewayProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JWKS 公钥仓库：按 {@code kid} 为 RS256 令牌选择验签公钥。
 *
 * <p><b>为什么不是「每请求拉 JWKS」</b>：JWKS 是低频变化、可缓存的公钥集合。本地缓存 TTL 默认 10 分钟，
 * 未知 {@code kid} 时强制刷新一次（密钥轮换后新令牌立即可验，无需等待 TTL）；并发刷新通过
 * {@link AtomicReference} 合并为一次请求。拉取失败保留上次快照，并做 30 秒负缓存，避免 auth 抖动
 * 时每个请求都打一次 JWKS。</p>
 *
 * <p><b>只接受公钥</b>：JWKS 中若出现 {@code kty=oct}（对称密钥），一律忽略——把对称签名密钥
 * 发布到可匿名访问的 JWKS 等于公开令牌伪造能力。HS256 令牌不查 JWKS，直接使用本地共享密钥。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class JwksKeyStore {

    /** 缓存键（单快照；JWKS 是整体替换而非按 kid 增量） */
    private static final String CACHE_KEY = "jwks";

    /** 无 kid 且只有一把公钥时使用的占位标识 */
    private static final String SINGLE_KEY_ID = "default";

    /** JWKS 拉取失败后的负缓存时长：期间直接失败，避免打爆 auth */
    private static final Duration FAILURE_BACKOFF = Duration.ofSeconds(30);

    /** HS256 共享密钥最小字节数（256 bit） */
    private static final int MIN_HMAC_SECRET_BYTES = 32;

    /** 对称签名算法前缀 */
    private static final String ALGORITHM_PREFIX_HMAC = "HS";

    private final WhaleGatewayProperties.Jwt properties;

    private final WebClient.Builder webClientBuilder;

    /** kid → 公钥快照；TTL 由配置控制 */
    private final Cache<String, Map<String, Key>> cache;

    /** 进行中的拉取，合并并发刷新 */
    private final AtomicReference<Mono<Map<String, Key>>> inFlight = new AtomicReference<>();

    /** 拉取失败后的重试时间点（毫秒时间戳） */
    private volatile long retryAfterMillis;

    /** HS256 共享密钥；密钥为空或过短时为 {@code null}（JWKS-only 部署） */
    private final SecretKey fallbackKey;

    /**
     * 构造 JWKS 公钥仓库
     *
     * @param gatewayProperties 网关配置
     * @param webClientBuilder  负载均衡 WebClient 构造器（支持 {@code lb://}）
     */
    public JwksKeyStore(WhaleGatewayProperties gatewayProperties,
                        @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder webClientBuilder) {
        this.properties = gatewayProperties.getJwt();
        this.webClientBuilder = webClientBuilder;
        this.cache = Caffeine.newBuilder()
                .maximumSize(4)
                .expireAfterWrite(Duration.ofMinutes(Math.max(1L, properties.getJwksCacheMinutes())))
                .build();
        this.fallbackKey = buildFallbackKey(properties.getSecret());
    }

    /**
     * 解析验签密钥：HS* 走共享密钥，RS* 走 JWKS（未知 kid 强制刷新一次）
     *
     * @param header 令牌头（alg/kid）
     * @return 验签密钥；无法解析时发出错误信号
     */
    public Mono<Key> resolve(TokenHeader header) {
        String algorithm = header.algorithm();
        if (algorithm.startsWith(ALGORITHM_PREFIX_HMAC)) {
            return fallbackKey == null
                    ? Mono.error(new IllegalStateException("收到 HS256 令牌但网关未配置共享密钥"))
                    : Mono.just(fallbackKey);
        }
        if (!properties.isJwksEnabled()) {
            return Mono.error(new IllegalStateException("收到 " + algorithm + " 令牌但网关未启用 JWKS"));
        }
        Map<String, Key> cached = cache.getIfPresent(CACHE_KEY);
        Key key = select(cached, header.keyId());
        if (key != null) {
            return Mono.just(key);
        }
        if (cached == null && System.currentTimeMillis() < retryAfterMillis) {
            return Mono.error(new IllegalStateException("JWKS 拉取失败后的负缓存期内，拒绝非对称令牌"));
        }
        return refresh(false).flatMap(keys -> {
            Key first = select(keys, header.keyId());
            if (first != null) {
                return Mono.just(first);
            }
            // 未知 kid：可能是刚轮换的新密钥，强制刷新一次再判定
            return refresh(true).map(latest -> {
                Key retried = select(latest, header.keyId());
                if (retried == null) {
                    throw new IllegalStateException("JWKS 中不存在 kid=" + header.keyId());
                }
                return retried;
            });
        });
    }

    /**
     * 拉取 JWKS（可能命中缓存或复用进行中的请求）
     *
     * @param force 忽略缓存与失败负缓存，强制重新拉取
     * @return kid → 公钥快照
     */
    private Mono<Map<String, Key>> refresh(boolean force) {
        if (!force) {
            Map<String, Key> cached = cache.getIfPresent(CACHE_KEY);
            if (cached != null) {
                return Mono.just(cached);
            }
            Mono<Map<String, Key>> existing = inFlight.get();
            if (existing != null) {
                return existing;
            }
        } else {
            // 强制刷新（未知 kid）时清掉在途引用：上一次拉取的 doFinally 要等下游处理完才执行，
            // 此时复用 inFlight 会拿到「刚解析完的旧快照」，导致轮换后的新 kid 永远找不到。
            inFlight.set(null);
        }
        Mono<Map<String, Key>> fetch = fetchJwks()
                .doOnNext(keys -> cache.put(CACHE_KEY, keys))
                .doFinally(signal -> inFlight.set(null))
                .cache();
        if (inFlight.compareAndSet(null, fetch)) {
            return fetch;
        }
        Mono<Map<String, Key>> winner = inFlight.get();
        return winner == null ? fetch : winner;
    }

    /**
     * 实际 HTTP 拉取与解析
     *
     * @return kid → 公钥快照
     */
    private Mono<Map<String, Key>> fetchJwks() {
        String uri = properties.getJwksUri();
        if (!StringUtils.hasText(uri)) {
            return Mono.error(new IllegalStateException("网关 JWKS 地址未配置"));
        }
        return webClientBuilder.build()
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(Math.max(1L, properties.getJwksTimeoutMillis())))
                .map(this::parseJwks)
                .doOnError(e -> {
                    retryAfterMillis = System.currentTimeMillis() + FAILURE_BACKOFF.toMillis();
                    log.warn("拉取 JWKS 失败，{} 秒内不再重试: reason={}", FAILURE_BACKOFF.toSeconds(), e.getMessage());
                });
    }

    /**
     * 解析 JWKS JSON：仅保留公钥（RSA/EC），忽略对称密钥
     *
     * @param json JWKS 响应体
     * @return kid → 公钥
     */
    private Map<String, Key> parseJwks(String json) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalStateException("JWKS 响应体为空");
        }
        JwkSet jwkSet = Jwks.setParser().build().parse(json);
        Map<String, Key> keys = new LinkedHashMap<>();
        for (Jwk<?> jwk : jwkSet.getKeys()) {
            Key key;
            try {
                key = jwk.toKey();
            } catch (RuntimeException e) {
                log.warn("忽略无法解析的 JWK: kty={}, reason={}", jwk.getType(), e.getMessage());
                continue;
            }
            if (!(key instanceof PublicKey)) {
                log.warn("忽略 JWKS 中的非公钥条目 kty={}：对称密钥不应通过 JWKS 发布", jwk.getType());
                continue;
            }
            String kid = StringUtils.hasText(jwk.getId()) ? jwk.getId() : SINGLE_KEY_ID;
            keys.put(kid, key);
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException("JWKS 未包含任何可用公钥");
        }
        return keys;
    }

    /**
     * 按 kid 选择公钥；无 kid 时仅在只有一把公钥时使用它
     *
     * @param keys 公钥快照，可能为 {@code null}
     * @param kid  令牌头中的 kid，可能为空
     * @return 公钥；无法唯一确定时返回 {@code null}
     */
    private static Key select(Map<String, Key> keys, String kid) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(kid)) {
            return keys.get(kid);
        }
        return keys.size() == 1 ? keys.values().iterator().next() : null;
    }

    /**
     * 构造 HS256 回落密钥
     *
     * @param secret 配置的共享密钥
     * @return 对称密钥；为空或不足 32 字节时返回 {@code null}
     */
    private static SecretKey buildFallbackKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_HMAC_SECRET_BYTES) {
            return null;
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
