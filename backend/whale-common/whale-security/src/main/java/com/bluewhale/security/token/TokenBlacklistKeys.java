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
 *
 * @author BlueWhale
 */
public final class TokenBlacklistKeys {

    /** 黑名单键前缀，便于运维按前缀批量查看/清理 */
    public static final String KEY_PREFIX = "auth:token:blacklist:";

    /** 摘要算法：SHA-256 */
    private static final String DIGEST_ALGORITHM = "SHA-256";

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
}
