package com.bluewhale.auth.starter.web.advice;

import com.bluewhale.auth.application.security.AccountLockedException;
import com.bluewhale.common.core.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 账号锁定异常处理测试（Wave 6.28）
 *
 * <p>验证剩余秒数确实进了响应体 {@code data}：前端只能从这里拿倒计时，
 * 文案里的「请 N 分钟」不可解析。</p>
 *
 * @author BlueWhale
 */
@DisplayName("账号锁定异常处理")
class LoginLockedExceptionHandlerTest {

    /** 被测处理器 */
    private final LoginLockedExceptionHandler handler = new LoginLockedExceptionHandler();

    @Test
    @DisplayName("42301：业务码与剩余秒数同时返回")
    void shouldCarryRetryAfterSeconds() {
        Result<LoginLockedExceptionHandler.LockoutInfo> result =
                handler.handleAccountLocked(new AccountLockedException("账号已锁定，请 15 分钟后再试", 872L));

        assertEquals(AccountLockedException.CODE, result.getCode());
        assertEquals(42301, result.getCode());
        assertEquals("账号已锁定，请 15 分钟后再试", result.getMessage());
        assertNotNull(result.getData(), "data 必须携带 retryAfterSeconds，否则前端无法倒计时");
        assertEquals(872L, result.getData().retryAfterSeconds());
    }

    @Test
    @DisplayName("业务码唯一来源：LoginLockService.LOCKED_CODE 与异常常量一致")
    void codeShouldBeSingleSourced() {
        assertEquals(
                com.bluewhale.auth.application.security.LoginLockService.LOCKED_CODE,
                AccountLockedException.CODE);
    }
}
