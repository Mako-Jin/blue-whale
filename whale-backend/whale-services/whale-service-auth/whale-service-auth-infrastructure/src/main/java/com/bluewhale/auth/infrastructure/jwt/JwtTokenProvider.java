package com.bluewhale.auth.infrastructure.jwt;

import com.bluewhale.auth.infrastructure.config.JwtProperties;
import com.bluewhale.security.contract.GatewayContract;
import com.bluewhale.security.token.TokenTimeSupport;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT Token 提供者（jjwt 0.12 API；RS256 优先，HS256 过渡期兼容）
 * <p>与 whale-service-gateway 约定：</p>
 * <ul>
 *   <li>算法：配置 RSA 私钥时 RS256（JWT 头携带 {@code kid}）；未配置时 HS256，secret = whale.auth.jwt.secret</li>
 *   <li>claims：sub=username、userId、roles（逗号分隔角色 code）、tokenType（access/refresh）、jti、
 *       familyId（仅 refreshToken 携带，AUTH-39 轮换复用检测用；accessToken 不带）</li>
 *   <li>Header：Authorization: Bearer &lt;token&gt;</li>
 * </ul>
 * <p>{@code jti} 为每枚令牌唯一的随机标识（标准 JWT ID claim），便于登出黑名单按令牌粒度
 * 追踪与审计（REQ-SEC-004）；access 与 refresh 令牌均携带。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public static final String CLAIM_USER_ID = GatewayContract.Claims.USER_ID;
    public static final String CLAIM_TENANT_ID = GatewayContract.Claims.TENANT_ID;
    public static final String CLAIM_ROLES = GatewayContract.Claims.ROLES;
    public static final String CLAIM_TOKEN_TYPE = GatewayContract.Claims.TOKEN_TYPE;
    public static final String TOKEN_TYPE_ACCESS = GatewayContract.Claims.TYPE_ACCESS;
    public static final String TOKEN_TYPE_REFRESH = GatewayContract.Claims.TYPE_REFRESH;

    /** refreshToken 的 family 标识 claim（AUTH-39：一次登录生成，轮换时原样沿用，用于复用检测后整族吊销） */
    public static final String CLAIM_FAMILY_ID = GatewayContract.Claims.FAMILY_ID;

    /**
     * 强制改密标记 claim（REQ-SEC-023）：为 true 时 auth 服务与网关仅放行改密/登出/刷新/当前用户等
     * 白名单接口，其余请求返回 403，避免未改密用户继续访问业务功能。
     */
    public static final String CLAIM_PWD_CHANGE_REQUIRED = GatewayContract.Claims.PWD_CHANGE_REQUIRED;

    /** Authorization 头凭据前缀，签发与校验两侧共用，避免各自定义 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 毫秒换算秒的进制 */
    private static final long MILLIS_PER_SECOND = 1000L;

    private final JwtProperties properties;

    /** 签发/验签密钥材料（RS256 优先，HS256 过渡期兼容） */
    private final JwtKeyMaterial keyMaterial;

    @Getter
    private final long accessExpireSeconds;

    /** refreshToken 有效期（秒），供 refresh 轮换时确定 family 吊销标记 TTL 使用 */
    @Getter
    private final long refreshExpireSeconds;

    /**
     * 构造器：依据配置中的密钥派生签名材料，并换算 accessToken 有效期
     *
     * @param properties  JWT 配置（密钥、有效期等）
     * @param keyMaterial 签发/验签密钥材料
     */
    public JwtTokenProvider(JwtProperties properties, JwtKeyMaterial keyMaterial) {
        this.properties = properties;
        this.keyMaterial = keyMaterial;
        this.accessExpireSeconds = properties.getExpireMinutes() * 60;
        this.refreshExpireSeconds = properties.getRefreshExpireMinutes() * 60;
    }

    /**
     * 生成 accessToken：sub=username、userId、tenantId、roles（逗号分隔角色 code），带唯一 jti。
     * <p>不提供省略 {@code pwdChangeRequired} 的便捷重载：调用方必须显式传值，
     * 避免漏传导致「强制改密用户拿到全量 token」（应用层端口 {@code TokenIssuer} 的
     * default 重载已显式以 {@code false} 委托，职责边界在应用层）。</p>
     *
     * @param tenantId 租户 ID（网关据此注入 X-Tenant-Id）
     * @param userId 用户 ID
     * @param username 用户名（作为 sub）
     * @param roles 角色 code 列表（逗号分隔写入 roles claim）
     * @param pwdChangeRequired 是否强制改密（REQ-SEC-023）
     * @return 签名后的 accessToken 字符串
     */
    public String generateAccessToken(Long tenantId, Long userId, String username, List<String> roles,
                                      boolean pwdChangeRequired) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpireMinutes() * 60_000);
        return keyMaterial.sign(Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TENANT_ID, tenantId)
                .claim(CLAIM_ROLES, String.join(",", roles))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_PWD_CHANGE_REQUIRED, pwdChangeRequired)
                .claim(TokenTimeSupport.CLAIM_ISSUED_AT_MILLIS, now.getTime())
                .issuedAt(now)
                .expiration(expiry));
    }

    /**
     * 生成 refreshToken，带唯一 jti；{@code familyId} 存在时携入 {@code familyId} claim（AUTH-39）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param username 用户名（作为 sub）
     * @param familyId 令牌 family 标识；{@code null} 或空白时不含该 claim（轮换关闭/旧令牌场景）
     * @return 签名后的 refreshToken 字符串
     */
    public String generateRefreshToken(Long tenantId, Long userId, String username, String familyId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getRefreshExpireMinutes() * 60_000);
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TENANT_ID, tenantId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .claim(TokenTimeSupport.CLAIM_ISSUED_AT_MILLIS, now.getTime());
        if (familyId != null && !familyId.isBlank()) {
            builder.claim(CLAIM_FAMILY_ID, familyId);
        }
        return keyMaterial.sign(builder
                .issuedAt(now)
                .expiration(expiry));
    }

    /**
     * 解析并校验 token，无效 / 过期 / 签名不符返回 null（不抛异常）。
     * <p>失败会分级记录日志：过期（{@link ExpiredJwtException}）是登出等场景的正常路径，仅 debug；
     * 其余（签名不符 / 结构非法）可能是伪造尝试，记 warn 并输出异常类型供审计与告警。</p>
     *
     * @param token 待解析的 token
     * @return 令牌载荷 claims；无效时返回 {@code null}
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .keyLocator(keyMaterial::verificationKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT 已过期: subject={}", e.getClaims().getSubject());
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 解析失败，疑似伪造或非法令牌: type={}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 计算 token 剩余有效期（秒）。
     * 供登出黑名单确定 Redis 键 TTL 使用：无法解析或已过期一律返回 0。
     *
     * @param token 待计算的 token
     * @return 剩余有效秒数，非正数表示已失效
     */
    public long getRemainingValiditySeconds(String token) {
        Claims claims = parse(token);
        if (claims == null || claims.getExpiration() == null) {
            return 0L;
        }
        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        return remainingMillis > 0L ? remainingMillis / MILLIS_PER_SECOND : 0L;
    }
}
