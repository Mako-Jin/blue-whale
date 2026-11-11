package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.security.AccountLockedException;
import com.bluewhale.common.core.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 账号锁定异常处理（Wave 6.28）
 *
 * <p>{@link AccountLockedException} 是 {@code BizException} 的子类，而全局
 * {@code GlobalExceptionHandler} 只返回 {@code code + message}，会把「剩余锁定秒数」丢掉，
 * 前端就只能从中文文案里抠分钟数。本 advice 按更具体的异常类型命中（Spring 选最具体的
 * handler，先于 BizException 那个），把秒数放进响应体 {@code data}。</p>
 *
 * <p>HTTP 状态保持 200：auth 全体系是「200 + 业务码」，前端 {@code send()} 会按
 * {@code code !== 0} 抛 {@code ApiError}，调用方从 {@code error.code === 42301} 分流。</p>
 *
 * <p><b>必须 {@code @Order(HIGHEST_PRECEDENCE)}</b>：advice 是「先遍历到的先用」，
 * 不是全局按异常具体度排序——全局 advice 注册的是 {@code BizException}，同样能处理本异常，
 * 不加顺序会让它抢先返回，秒数照样丢。</p>
 *
 * @author BlueWhale
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class LoginLockedExceptionHandler {

    /**
     * 账号锁定：业务码 42301 + 剩余秒数
     *
     * @param e 账号锁定异常
     * @return 含 {@code retryAfterSeconds} 的失败响应
     */
    @ExceptionHandler(AccountLockedException.class)
    public Result<LockoutInfo> handleAccountLocked(AccountLockedException e) {
        Result<LockoutInfo> result = Result.fail(AccountLockedException.CODE, e.getMessage());
        result.setData(new LockoutInfo(e.getRetryAfterSeconds()));
        return result;
    }

    /**
     * 锁定信息（供前端倒计时禁用态使用）
     *
     * @param retryAfterSeconds 剩余锁定秒数
     * @author BlueWhale
     */
    public record LockoutInfo(long retryAfterSeconds) {
    }
}
