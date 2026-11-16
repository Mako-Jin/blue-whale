package com.bluewhale.auth.infrastructure.jwt;

import com.bluewhale.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 签发与验签密钥材料（支持 HS256 与 RS256 双模式）。
 *
 * <p><b>为什么需要双模式</b>：存量部署只有 {@code whale.auth.jwt.secret}（HS256）。直接切 RS256 会让
 * 所有存量令牌在切换瞬间失效，用户全量重登。因此配置了 {@code private-key} 才启用 RS256，
 * 同时始终保留 HS256 密钥用于验签过渡期的旧令牌；签发只使用当前模式。</p>
 *
 * <p><b>轮换</b>：当前私钥 + {@code key-id} 签名；{@code previous-public-keys} 中的旧公钥与当前公钥
 * 一起发布到 {@code /oauth2/jwks}。轮换时只需替换私钥、保留旧公钥至少一个 accessToken 有效期，
 * 网关按 {@code kid} 选 key，存量令牌自然过期，无需全量重登。</p>
 *
 * <p><b>安全约束</b>：HS256 模式下 {@code /oauth2/jwks} 会暴露对称密钥（谁拿到谁能伪造令牌），
 * 因此该 HTTP 端点由 {@code JwksExposureGuardFilter} 在 HS256 模式下关闭；RS256 模式只发布公钥。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class JwtKeyMaterial {

    /** RSA 密钥算法名 */
    private static final String RSA_ALGORITHM = "RSA";

    /** 允许的最小 RSA 密钥长度（NIST SP 800-57 建议下限） */
    private static final int MIN_RSA_KEY_BITS = 2048;

    /** PEM 头尾行的通用匹配（BEGIN/END 均可，算法名不参与解析） */
    private static final String PEM_HEADER_FOOTER_PATTERN = "-----(BEGIN|END)[^-]+-----";

    /** HS256 密钥最小字节数（256 bit） */
    private static final int MIN_HMAC_SECRET_BYTES = 32;

    /** 非对称签名算法标识 */
    private static final String ALGORITHM_PREFIX_RSA = "RS";

    /** 对称签名算法标识 */
    private static final String ALGORITHM_PREFIX_HMAC = "HS";

    /** HS256 验签密钥（RS256 模式下仍保留，用于过渡期旧令牌） */
    private final SecretKey secretKey;

    /** 当前 RSA 私钥；为空表示 HS256 模式 */
    private final PrivateKey signingKey;

    /** kid → 公钥（当前公钥 + previous-public-keys），仅 RS256 模式非空 */
    private final Map<String, PublicKey> verificationKeys;

    /** 当前签名密钥标识 */
    private final String keyId;

    /**
     * 构造密钥材料：解析并校验配置中的密钥
     *
     * @param properties JWT 配置
     */
    public JwtKeyMaterial(JwtProperties properties) {
        this.keyId = normalize(properties.getKeyId());
        this.secretKey = buildSecretKey(properties.getSecret());
        this.verificationKeys = buildVerificationKeys(properties);
        this.signingKey = properties.getPrivateKey() == null || properties.getPrivateKey().isBlank()
                ? null : parsePrivateKey(properties.getPrivateKey());
        if (signingKey == null && secretKey == null) {
            throw new IllegalStateException("JWT 配置缺失：必须配置 whale.auth.jwt.private-key（RS256）或共享密钥 secret（HS256）");
        }
        if (signingKey == null) {
            log.warn("JWT 未配置 RSA 私钥，保持 HS256 模式；/oauth2/jwks 将关闭，网关须继续共享 HS256 密钥");
        } else {
            // 当前公钥必须能验签当前私钥；配置漏了当前公钥会导致签出的令牌连自己都无法校验
            verificationKeys.put(keyId, derivePublicKey(signingKey));
            log.info("JWT 启用 RS256 签发: keyId={}, 可验签公钥数={}", keyId, verificationKeys.size());
        }
    }

    /**
     * 是否启用 RS256 非对称签发
     *
     * @return 配置了 RSA 私钥返回 {@code true}
     */
    public boolean isAsymmetric() {
        return signingKey != null;
    }

    /**
     * 用当前模式签名令牌：RS256 时写入 {@code kid} 头，HS256 时使用共享密钥
     *
     * @param builder 已装配 claims 的 JWT 构造器
     * @return 签名后的令牌字符串
     */
    public String sign(JwtBuilder builder) {
        if (signingKey != null) {
            return builder.header().keyId(keyId).and()
                    .signWith(signingKey, Jwts.SIG.RS256)
                    .compact();
        }
        if (secretKey == null) {
            throw new IllegalStateException("HS256 签名密钥未配置");
        }
        return builder.signWith(secretKey, Jwts.SIG.HS256).compact();
    }

    /**
     * 解析令牌头选择验签密钥：HS* 一律用共享密钥（过渡期旧令牌），RS* 按 kid 选公钥。
     *
     * @param header JWT 头
     * @return 验签密钥
     */
    public Key verificationKey(io.jsonwebtoken.Header header) {
        String algorithm = header.getAlgorithm();
        if (algorithm == null || algorithm.isBlank()) {
            throw new UnsupportedJwtException("JWT 头缺少 alg");
        }
        if (algorithm.startsWith(ALGORITHM_PREFIX_HMAC)) {
            if (secretKey == null) {
                throw new UnsupportedJwtException("收到 HS256 令牌但未配置共享密钥（已配置 RS256 私钥）");
            }
            return secretKey;
        }
        if (!algorithm.startsWith(ALGORITHM_PREFIX_RSA)) {
            throw new UnsupportedJwtException("不支持的 JWT 签名算法: " + algorithm);
        }
        if (signingKey == null) {
            throw new UnsupportedJwtException("未配置 RSA 私钥，拒绝非对称令牌");
        }
        // Locator 的入参类型是 Header，签名令牌在运行期实际是 JwsHeader（ProtectedHeader）
        String tokenKeyId = header instanceof io.jsonwebtoken.ProtectedHeader protectedHeader
                ? protectedHeader.getKeyId() : null;
        if (tokenKeyId != null && !tokenKeyId.isBlank()) {
            PublicKey key = verificationKeys.get(tokenKeyId);
            if (key != null) {
                return key;
            }
            throw new UnsupportedJwtException("未知 kid: " + tokenKeyId);
        }
        if (verificationKeys.size() == 1) {
            return verificationKeys.values().iterator().next();
        }
        throw new UnsupportedJwtException("令牌缺少 kid 且存在多把验签公钥");
    }

    /**
     * 当前签名密钥标识
     *
     * @return kid
     */
    public String getKeyId() {
        return keyId;
    }

    /**
     * HS256 共享密钥（RS256 模式下仅用于验签过渡期旧令牌）
     *
     * @return 对称密钥
     */
    public SecretKey getSecretKey() {
        return secretKey;
    }

    /**
     * kid → 公钥（当前公钥 + 旧公钥），供 JWKS 发布使用
     *
     * @return 只读映射
     */
    public Map<String, PublicKey> getVerificationKeys() {
        return Collections.unmodifiableMap(verificationKeys);
    }

    /**
     * 构造 HS256 密钥；长度不足直接启动失败，避免首个请求才抛 WeakKeyException
     *
     * @param secret 配置的共享密钥
     * @return 对称密钥
     */
    private static SecretKey buildSecretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            // RS256-only 部署可以只配置私钥；HS256 模式会在构造期由「两者皆空」检查拦截
            return null;
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_HMAC_SECRET_BYTES) {
            throw new IllegalStateException("JWT 共享密钥不足 32 字节（HS256 要求 256 bit）");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析当前公钥与历史公钥
     *
     * @param properties JWT 配置
     * @return kid → 公钥（保持配置顺序）
     */
    private static Map<String, PublicKey> buildVerificationKeys(JwtProperties properties) {
        Map<String, PublicKey> keys = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.getPreviousPublicKeys().entrySet()) {
            String kid = entry.getKey();
            String pem = entry.getValue();
            if (kid == null || kid.isBlank() || pem == null || pem.isBlank()) {
                continue;
            }
            keys.put(kid.trim(), parsePublicKey(pem));
        }
        return keys;
    }

    /**
     * 解析 PKCS#8 RSA 私钥（PEM 或 Base64 DER）
     *
     * @param pem PEM 文本
     * @return RSA 私钥
     */
    private static PrivateKey parsePrivateKey(String pem) {
        try {
            PrivateKey key = KeyFactory.getInstance(RSA_ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(decodeBase64(pem)));
            int bits = ((RSAPrivateKey) key).getModulus().bitLength();
            if (bits < MIN_RSA_KEY_BITS) {
                throw new IllegalStateException("RSA 私钥长度不足 " + MIN_RSA_KEY_BITS + " bit: " + bits);
            }
            return key;
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("解析 JWT RSA 私钥失败，请确认配置为 PKCS#8 格式", e);
        }
    }

    /**
     * 解析 X.509 编码的 RSA 公钥（PEM 或 Base64 DER）
     *
     * @param pem PEM 文本
     * @return RSA 公钥
     */
    private static PublicKey parsePublicKey(String pem) {
        try {
            return KeyFactory.getInstance(RSA_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(decodeBase64(pem)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("解析 JWT RSA 公钥失败，请确认配置为 X.509 格式", e);
        }
    }

    /**
     * 由 RSA 私钥派生公钥（仅用于 JWKS 发布与自校验）
     *
     * @param privateKey RSA 私钥
     * @return RSA 公钥
     */
    private static PublicKey derivePublicKey(PrivateKey privateKey) {
        try {
            // PKCS#8 RSA 私钥通常携带 CRT 参数，公钥指数只能从 CRT 私钥读取；非 CRT 私钥无法派生
            java.security.interfaces.RSAPrivateCrtKey crtKey =
                    (java.security.interfaces.RSAPrivateCrtKey) privateKey;
            return KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(
                    new java.security.spec.RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("无法由 RSA 私钥派生公钥，请改用携带 CRT 参数的 PKCS#8 私钥", e);
        }
    }

    /**
     * 去除 PEM 头尾与空白后做 Base64 解码
     *
     * @param pem PEM 或 Base64 文本
     * @return DER 字节
     */
    private static byte[] decodeBase64(String pem) {
        String normalized = pem
                .replaceAll(PEM_HEADER_FOOTER_PATTERN, "")
                .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT 密钥不是合法的 Base64/PEM 文本", e);
        }
    }

    /**
     * 规范化 kid：空白时回落默认值
     *
     * @param keyId 配置值
     * @return 非空 kid
     */
    private static String normalize(String keyId) {
        return keyId == null || keyId.isBlank() ? JwtProperties.DEFAULT_KEY_ID : keyId.trim();
    }
}
