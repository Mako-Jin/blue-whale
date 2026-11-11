package com.bluewhale.auth.application.security;

import com.bluewhale.auth.application.model.port.LoginAttemptStore;
import com.bluewhale.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
@Service
@RequiredArgsConstructor
public class LoginLockService {

    /** 账号锁定的业务码（HTTP 语义参考 423 Locked，与 42801 同一命名习惯） */
    public static final int LOCKED_CODE = 42301;

    /** 每分钟秒数（用于把剩余秒数向上取整成分钟） */
    private static final long SECONDS_PER_MINUTE = 60L;

    /** 登录失败计数端口 */
    private final LoginAttemptStore loginAttemptStore;

    /**
     * 校验（租户, 用户名）当前是否处于锁定状态
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @throws BizException 已锁定时抛出（业务码 {@value #LOCKED_CODE}）
     */
    public void ensureUnlocked(Long tenantId, String username) {
        long remainingSeconds = loginAttemptStore.lockRemainingSeconds(tenantId, username);
        if (remainingSeconds > 0L) {
            throw locked(remainingSeconds);
        }
    }

    /**
     * 记录一次凭据校验失败，并在此次失败导致锁定时直接抛出锁定异常
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @throws BizException 本次失败已触达上限时抛出（业务码 {@value #LOCKED_CODE}）
     */
    public void recordFailure(Long tenantId, String username) {
        loginAttemptStore.recordFailure(tenantId, username);
        long remainingSeconds = loginAttemptStore.lockRemainingSeconds(tenantId, username);
        if (remainingSeconds > 0L) {
            throw locked(remainingSeconds);
        }
    }

    /**
     * 凭据校验通过后清除失败计数
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     */
    public void clear(Long tenantId, String username) {
        loginAttemptStore.clear(tenantId, username);
    }

    /**
     * 构造锁定异常（剩余秒数向上取整为分钟，最少提示 1 分钟）
     *
     * @param remainingSeconds 剩余锁定秒数
     * @return 业务异常
     */
    private BizException locked(long remainingSeconds) {
        long minutes = Math.max(1L, (remainingSeconds + SECONDS_PER_MINUTE - 1L) / SECONDS_PER_MINUTE);
        return new BizException(LOCKED_CODE, "账号已锁定，请 " + minutes + " 分钟后再试");
    }
}
