package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.port.LoginAttemptStore;
import com.bluewhale.auth.application.security.AccountLockedException;
import com.bluewhale.common.core.exception.BizException;

/**
 * 登录失败锁定服务（Wave 6.27）
 *
 * <p>把「连续凭据校验失败到上限即锁定」的策略收敛在一处，登录（{@code /auth/v1/login}）与
 * 二次认证（{@code /auth/v1/reauth}）共用同一计数键：step-up 验密若不走同一计数，
 * 攻击者拿到会话后就能改用 reauth 继续爆破口令。</p>
 *
 * <p>锁定是<b>按（租户, 用户名）</b>且在窗口到期后自动解除的，不做永久封号：
 * 永久封号需要人工解封流程，而分布式计数一旦被恶意打满，会把正常用户长期挡在门外。</p>
 *
 * @author BlueWhale
 */
public interface LoginLockService {

    /** 账号锁定的业务码（唯一来源在 {@link AccountLockedException#CODE}） */
    public static final int LOCKED_CODE = AccountLockedException.CODE;

    /**
     * 校验（租户, 用户名）当前是否处于锁定状态
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @throws BizException 已锁定时抛出（业务码 {@value #LOCKED_CODE}）
     */
    void ensureUnlocked(Long tenantId, String username);

    /**
     * 记录一次凭据校验失败，并在此次失败导致锁定时直接抛出锁定异常
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @throws BizException 本次失败已触达上限时抛出（业务码 {@value #LOCKED_CODE}）
     */
    void recordFailure(Long tenantId, String username);

    /**
     * 凭据校验通过后清除失败计数
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     */
    void clear(Long tenantId, String username);
}
