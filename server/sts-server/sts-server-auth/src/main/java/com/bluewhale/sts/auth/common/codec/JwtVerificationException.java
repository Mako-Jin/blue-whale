package com.bluewhale.sts.auth.common.codec;

/**
 * 令牌校验失败。
 *
 * <p>与 {@link JwtExpiredException} 分开，是为了让上层能区分
 * "签名不对（可能是伪造/篡改）"与"只是过期了（重新登录即可）" ——
 * 前者的日志级别与告警策略应与后者不同。
 */
public class JwtVerificationException extends RuntimeException {

    public JwtVerificationException(String message) {
        super(message, null, false, false);
    }

}
