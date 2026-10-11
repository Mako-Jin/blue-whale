package com.bluewhale.sts.auth.common.codec;

/**
 * 令牌已过期。
 *
 * <p>继承 {@link JwtVerificationException}，因此"当作校验失败处理"的代码仍然正确；
 * 需要区分时再精确捕获本类。
 */
public class JwtExpiredException extends JwtVerificationException {

    public JwtExpiredException(String message) {
        super(message);
    }

}
