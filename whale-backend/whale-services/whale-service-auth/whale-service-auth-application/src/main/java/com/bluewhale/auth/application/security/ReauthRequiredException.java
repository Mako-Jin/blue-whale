package com.bluewhale.auth.application.security;

/**
 * 二次认证缺失/过期异常（Wave 6.22）
 *
 * <p>由 {@code ReauthInterceptor} 在危险操作缺少有效 step-up 凭证时抛出，
 * 由 auth 服务的异常处理器映射为 <b>HTTP 428</b> + 业务码 {@value #CODE}，
 * 前端据此引导重新验密并重放请求。</p>
 *
 * @author BlueWhale
 */
public class ReauthRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 二次认证业务码（与 HTTP 428 配套） */
    public static final int CODE = 42801;

    /**
     * 构造异常（无堆栈，属预期控制流，避免日志噪声）
     */
    public ReauthRequiredException() {
        super("REAUTH_REQUIRED", null, false, false);
    }
}
