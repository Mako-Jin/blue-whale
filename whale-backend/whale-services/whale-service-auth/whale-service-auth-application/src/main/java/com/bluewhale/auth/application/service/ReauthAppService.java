package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.AuthenticatedUser;
import com.bluewhale.auth.application.model.result.ReauthResult;
import com.bluewhale.auth.application.port.CredentialAuthenticator;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.port.ReauthTokenStore;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.util.StringUtils;

/**
 * 二次认证应用服务（Wave 6.22）
 *
 * <p>验密复用 {@link CredentialAuthenticator}（Spring Security 认证链，含账号停用判断），
 * 通过后发放与（租户, 用户）绑定的短时效凭证；凭证有效期内的危险操作不再重复验密。</p>
 *
 * @author BlueWhale
 */
public interface ReauthAppService {

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
    ReauthResult reauthenticate(String rawPassword);

    /**
     * 校验请求头中的凭证是否属于当前用户且未过期
     *
     * @param token 请求头 {@code X-Reauth-Token} 的值，可为空
     * @return 有效返回 {@code true}
     */
    boolean verify(String token);
}
