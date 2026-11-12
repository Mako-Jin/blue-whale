package com.bluewhale.auth.application.model;

/**
 * 令牌载荷：由基础设施适配器从 JWT Claims 转换，应用层不感知 jjwt 类型。
 *
 * <p>可空性口径：{@code tenantId}/{@code userId}/{@code familyId}/{@code jti} 为包装类型可空
 * （由用例校验）；{@code subject}/{@code tokenType}/{@code issuedAtMillis} 必有。</p>
 *
 * @param subject 令牌主体（用户名）
 * @param tenantId 租户 ID（可能为空，由用例校验）
 * @param userId 用户 ID（可能为空，由用例校验）
 * @param tokenType 令牌类型（access / refresh，JWT claim 存小写字符串）
 * @param familyId 轮换 family 标识（可空）
 * @param jti 令牌唯一标识（可空）
 * @param issuedAtMillis 签发时间（毫秒时间戳，必有）
 * @author BlueWhale
 */
public record TokenPayload(String subject, Long tenantId, Long userId, String tokenType,
                           String familyId, String jti, long issuedAtMillis) {

    /** accessToken 类型标识 */
    public static final String TYPE_ACCESS = "access";

    /** refreshToken 类型标识 */
    public static final String TYPE_REFRESH = "refresh";
}
