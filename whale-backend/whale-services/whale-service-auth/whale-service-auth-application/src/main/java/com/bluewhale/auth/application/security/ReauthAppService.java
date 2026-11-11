package com.bluewhale.auth.application.security;

import com.bluewhale.auth.application.model.AuthenticatedUser;
import com.bluewhale.auth.application.model.port.CredentialAuthenticator;
import com.bluewhale.auth.application.model.port.CurrentUserProvider;
import com.bluewhale.auth.application.model.port.ReauthTokenStore;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 二次认证应用服务（Wave 6.22）
 *
 * <p>验密复用 {@link CredentialAuthenticator}（Spring Security 认证链，含账号停用判断），
 * 通过后发放与（租户, 用户）绑定的短时效凭证；凭证有效期内的危险操作不再重复验密。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ReauthAppService {

    /** 当前登录用户提供者 */
    private final CurrentUserProvider currentUserProvider;

    /** 凭据认证端口（与登录同一套校验） */
    private final CredentialAuthenticator credentialAuthenticator;

    /** step-up 凭证存储端口 */
    private final ReauthTokenStore reauthTokenStore;

    /**
     * 校验当前用户的明文口令并发放凭证
     *
     * @param rawPassword 当前登录用户的明文口令
     * @return 新凭证与有效期
     * @throws BizException 口令错误（401）或认证结果与当前用户不一致（401）时抛出
     */
    public ReauthResult reauthenticate(String rawPassword) {
        AuthenticatedUser current = currentUserProvider.current();
        AuthenticatedUser verified = credentialAuthenticator.authenticate(
                current.tenantId(), current.username(), rawPassword);
        // 防御：认证结果必须与当前登录身份一致，避免租户/账号错位时误发凭证
        if (!current.userId().equals(verified.userId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String token = reauthTokenStore.issue(current.tenantId(), current.userId());
        return new ReauthResult(token, reauthTokenStore.ttlSeconds());
    }

    /**
     * 校验请求头中的凭证是否属于当前用户且未过期
     *
     * @param token 请求头 {@code X-Reauth-Token} 的值，可为空
     * @return 有效返回 {@code true}
     */
    public boolean verify(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        AuthenticatedUser current = currentUserProvider.current();
        return reauthTokenStore.matches(current.tenantId(), current.userId(), token);
    }
}
