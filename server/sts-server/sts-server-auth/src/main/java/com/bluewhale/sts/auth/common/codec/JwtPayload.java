package com.bluewhale.sts.auth.common.codec;

/**
 * JWT 校验通过后的载荷。
 *
 * <p>只保留判定所需的字段，不把整个 Map 抛给调用方 ——
 * 那样每个使用点都要自己 {@code get("uid")} 再转型，一处拼错只在运行时暴露。
 */
public record JwtPayload(
        long userId,
        long tenantId,
        String username,
        /** 令牌类型：access / refresh */
        String tokenType,
        /** 令牌唯一标识 */
        String jti,
        long issuedAt,
        long expiresAt) {

    public boolean isAccessToken() {
        return "access".equals(tokenType);
    }

    public boolean isRefreshToken() {
        return "refresh".equals(tokenType);
    }

    /** 有效性起点：对刷新令牌，必须早于当前时间 Token 才有效 */
    public boolean isActiveAt(long nowMillis) {
        return nowMillis < expiresAt;
    }

}
