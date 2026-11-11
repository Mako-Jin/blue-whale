package com.bluewhale.auth.application.security;

import com.bluewhale.common.core.exception.BizException;
import lombok.Getter;

/**
 * 账号锁定异常（Wave 6.28）
 *
 * <p>在通用 {@link BizException} 之上多带一个「剩余锁定秒数」，让前端能把锁定状态
 * 渲染成可倒计时的禁用态，而不是只弹一条很快消失的提示（用户会继续连点提交）。</p>
 *
 * <p>业务码与 {@link LoginLockService#LOCKED_CODE} 一致；HTTP 状态仍为 200
 * （auth 全体系「200 + 业务码」契约），由 starter 的 advice 把秒数塞进 {@code data}。</p>
 *
 * @author BlueWhale
 */
@Getter
public class AccountLockedException extends BizException {

    private static final long serialVersionUID = 1L;

    /** 账号锁定的业务码（HTTP 语义参考 423 Locked，与 42801 同一命名习惯） */
    public static final int CODE = 42301;

    /** 剩余锁定秒数（&gt; 0） */
    private final long retryAfterSeconds;

    /**
     * 构造账号锁定异常
     *
     * @param message 面向用户的提示文案
     * @param retryAfterSeconds 剩余锁定秒数
     */
    public AccountLockedException(String message, long retryAfterSeconds) {
        super(CODE, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
