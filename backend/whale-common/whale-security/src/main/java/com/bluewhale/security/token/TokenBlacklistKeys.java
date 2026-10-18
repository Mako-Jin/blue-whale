package com.bluewhale.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 令牌黑名单键约定（whale-auth 写入 / whale-gateway 校验共用同一实现）。
 * <p>键结构：{@code auth:token:blacklist:<sha256(token)>}，值为固定占位符，
 * 仅以键是否存在判定命中；使用摘要而非 token 原文作为键名，避免明文凭据落入
 * Redis keyspace 与日志。两侧必须共用本类构造键，避免各自实现导致格式漂移、
 * 校验静默失效。</p>
 * <p>另提供 refreshToken 一次性轮换（AUTH-39 / ADR-001）所需的两类键：
 * {@code whale:auth:refresh:used:<jti>}（已消费标记）与
 * {@code whale:auth:refresh:family:<familyId>}（family 吊销标记）；两类键前缀独立于
 * 既有登出黑名单前缀，避免影响 gateway 侧既有校验。</p>
 *
 * @author BlueWhale
 */
public final class TokenBlacklistKeys {

    /** 黑名单键前缀，便于运维按前缀批量查看/清理 */
    public static final String KEY_PREFIX = "auth:token:blacklist:";

    /** refreshToken 已消费标记键前缀（AUTH-39） */
    public static final String REFRESH_USED_PREFIX = "whale:auth:refresh:used:";

    /** refreshToken family 吊销标记键前缀（AUTH-39） */
    public static final String REFRESH_FAMILY_PREFIX = "whale:auth:refresh:family:";

    /** 摘要算法：SHA-256 */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    /**
     * 私有构造器，禁止实例化
     */
    private TokenBlacklistKeys() {
    }

    /**
     * 构造黑名单键：token 经 SHA-256 摘要后十六进制编码，前缀固定
     *
     * @param token token 原文（access 或 refresh 均可）
     * @return 形如 {@code auth:token:blacklist:<64 位十六进制>} 的 Redis 键
     */
    public static String blacklistKey(String token) {
        try {
            byte[] digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // JDK 标准实现必然支持 SHA-256，此处为不可达分支
            throw new IllegalStateException(DIGEST_ALGORITHM + " 算法不可用", e);
        }
    }

    /**
     * 构造 refreshToken 已消费标记键（AUTH-39）：以 {@code jti} 为标识，写入成功即表示已消费
     *
     * @param jti refreshToken 的 JWT ID（每枚令牌唯一）
     * @return 形如 {@code whale:auth:refresh:used:<jti>} 的 Redis 键
     */
    public static String refreshUsedKey(String jti) {
        return REFRESH_USED_PREFIX + jti;
    }

    /**
     * 构造 refreshToken family 吊销标记键（AUTH-39）：键存在即视为该 family 全部 refreshToken 已吊销
     *
     * @param familyId 令牌 family 标识（一次登录生成，轮换时原样沿用）
     * @return 形如 {@code whale:auth:refresh:family:<familyId>} 的 Redis 键
     */
    public static String refreshFamilyKey(String familyId) {
        return REFRESH_FAMILY_PREFIX + familyId;
    }
}
