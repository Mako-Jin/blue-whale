package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.AuthenticatedUser;
import com.bluewhale.auth.application.model.result.ReauthResult;
import com.bluewhale.auth.application.port.CredentialAuthenticator;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.port.ReauthTokenStore;
import com.bluewhale.auth.application.security.InvalidCredentialException;
import com.bluewhale.auth.application.service.LoginLockService;
import com.bluewhale.auth.application.service.ReauthAppService;
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
public class ReauthAppServiceImpl implements ReauthAppService {

    /** 当前登录用户提供者 */
    private final CurrentUserProvider currentUserProvider;

    /** 凭据认证端口（与登录同一套校验） */
    private final CredentialAuthenticator credentialAuthenticator;

    /** step-up 凭证存储端口 */
    private final ReauthTokenStore reauthTokenStore;

    /** 登录失败锁定（与登录共用同一计数，Wave 6.27） */
    private final LoginLockService loginLockService;

    /**
     * 校验当前用户的明文口令并发放凭证
     *
     * <p>失败次数与登录共用同一计数键：否则拿到会话的攻击者可以绕过登录锁定，
     * 改用 reauth 继续爆破口令。</p>
     *
     * @param rawPassword 当前登录用户的明文口令
     * @return 新凭证与有效期
     * @throws BizException 口令错误（401）、账号已锁定（42301）或认证结果与当前用户不一致（401）时抛出
     */
    public ReauthResult reauthenticate(String rawPassword) {
        AuthenticatedUser current = currentUserProvider.current();
        loginLockService.ensureUnlocked(current.tenantId(), current.username());
        AuthenticatedUser verified;
        try {
            verified = credentialAuthenticator.authenticate(
                    current.tenantId(), current.username(), rawPassword);
        } catch (InvalidCredentialException e) {
            loginLockService.recordFailure(current.tenantId(), current.username());
            throw e;
        }
        loginLockService.clear(current.tenantId(), current.username());
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
