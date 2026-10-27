package com.bluewhale.security.token;

import io.jsonwebtoken.Claims;

import java.util.Date;

/**
 * 令牌签发时间与撤销时间比较工具。
 *
 * <p>历史实现把撤销时刻存为 Unix 秒，而 JWT {@code iat} 也只有秒级精度，
 * 导致「撤销后同一秒内重新登录」签发的新令牌被误判为已撤销（S-01）。现在：</p>
 * <ul>
 *   <li>新令牌额外携带毫秒级 {@code iatMs} claim；</li>
 *   <li>撤销键写入毫秒时间戳；</li>
 *   <li>旧格式撤销键（秒级）仍按秒粒度比较，保证滚动升级期间的兼容与不放松。</li>
 * </ul>
 *
 * <p>网关与 whale-service-auth 共用本工具，避免两侧口径漂移。</p>
 *
 * @author BlueWhale
 */
public final class TokenTimeSupport {

    /** 令牌签发毫秒时间戳 claim（兼容旧令牌缺失） */
    public static final String CLAIM_ISSUED_AT_MILLIS = "iatMs";

    /**
     * 秒级与毫秒级时间戳的分界：小于该值视为 Unix 秒。
     *
     * <p>秒级时间戳在 5138 年前不会达到 1e11，毫秒级时间戳在 1973 年后已超过该值。</p>
     */
    private static final long SECONDS_THRESHOLD = 100_000_000_000L;

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private TokenTimeSupport() {
    }

    /**
     * 解析令牌签发毫秒时间：优先 {@code iatMs}，旧令牌回退到 {@code iat} 秒级时间。
     *
     * @param claims 已验签的 JWT 载荷
     * @return 签发毫秒时间；缺失时返回 {@code 0}
     */
    public static long issuedAtMillis(Claims claims) {
        if (claims == null) {
            return 0L;
        }
        Object claim = claims.get(CLAIM_ISSUED_AT_MILLIS);
        if (claim instanceof Number number && number.longValue() > 0L) {
            return number.longValue();
        }
        return issuedAtMillis(claims.getIssuedAt(), null);
    }

    /**
     * 解析令牌签发毫秒时间（显式毫秒优先）。
     *
     * @param issuedAt       JWT {@code iat}
     * @param issuedAtMillis 毫秒 claim，可为 {@code null}
     * @return 签发毫秒时间；均缺失时返回 {@code 0}
     */
    public static long issuedAtMillis(Date issuedAt, Long issuedAtMillis) {
        if (issuedAtMillis != null && issuedAtMillis > 0L) {
            return issuedAtMillis;
        }
        return issuedAt == null ? 0L : issuedAt.getTime();
    }

    /**
     * 判断令牌签发时间是否不晚于撤销时间。
     *
     * <p>撤销值兼容两种格式：毫秒（新）与秒（旧）。旧格式按秒粒度比较，
     * 因此同一秒内签发的旧令牌仍会被判定为已撤销，不放松历史安全语义。</p>
     *
     * @param issuedAtMillis 令牌签发毫秒时间
     * @param revokedAtRaw   撤销键原值（Unix 秒或毫秒文本）
     * @return 已撤销返回 {@code true}
     * @throws NumberFormatException 撤销值非法
     */
    public static boolean isRevoked(long issuedAtMillis, String revokedAtRaw) {
        if (revokedAtRaw == null || revokedAtRaw.isBlank() || issuedAtMillis <= 0L) {
            return false;
        }
        long revokedAt = Long.parseLong(revokedAtRaw.trim());
        if (revokedAt < SECONDS_THRESHOLD) {
            return issuedAtMillis / 1000L <= revokedAt;
        }
        return issuedAtMillis <= revokedAt;
    }
}
