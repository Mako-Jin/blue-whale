package com.bluewhale.auth.infrastructure.adapter.auth;

import com.bluewhale.auth.application.model.TokenPayload;
import com.bluewhale.auth.application.port.TokenIssuer;
import com.bluewhale.auth.infrastructure.jwt.JwtTokenProvider;
import com.bluewhale.security.token.TokenTimeSupport;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 令牌签发适配器：包装 {@link JwtTokenProvider}，并在 Claims 与应用层载荷之间转换。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class JwtTokenIssuerAdapter implements TokenIssuer {

    private final JwtTokenProvider tokenProvider;

    @Override
    public String issueAccessToken(Long tenantId, Long userId, String username, List<String> roles,
                                   boolean pwdChangeRequired) {
        return tokenProvider.generateAccessToken(tenantId, userId, username, roles, pwdChangeRequired);
    }

    @Override
    public String issueRefreshToken(Long tenantId, Long userId, String username, String familyId) {
        return tokenProvider.generateRefreshToken(tenantId, userId, username, familyId);
    }

    @Override
    public long accessExpireSeconds() {
        return tokenProvider.getAccessExpireSeconds();
    }

    @Override
    public long refreshExpireSeconds() {
        return tokenProvider.getRefreshExpireSeconds();
    }

    @Override
    public long remainingValiditySeconds(String token) {
        return tokenProvider.getRemainingValiditySeconds(token);
    }

    @Override
    public TokenPayload parse(String token) {
        Claims claims = tokenProvider.parse(token);
        if (claims == null) {
            return null;
        }
        return new TokenPayload(claims.getSubject(),
                resolveLongClaim(claims, JwtTokenProvider.CLAIM_TENANT_ID),
                resolveLongClaim(claims, JwtTokenProvider.CLAIM_USER_ID),
                claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class),
                claims.get(JwtTokenProvider.CLAIM_FAMILY_ID, String.class),
                claims.getId(),
                TokenTimeSupport.issuedAtMillis(claims));
    }

    /**
     * 解析数值型 claim
     *
     * @param claims 令牌载荷
     * @param claimName claim 名称
     * @return 数值；缺失或非法时返回 {@code null}
     */
    private static Long resolveLongClaim(Claims claims, String claimName) {
        Object raw = claims.get(claimName);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
