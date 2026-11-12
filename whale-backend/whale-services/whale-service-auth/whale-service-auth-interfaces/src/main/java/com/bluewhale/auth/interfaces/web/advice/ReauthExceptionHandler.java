package com.bluewhale.auth.interfaces.web.advice;

import com.bluewhale.auth.application.security.ReauthRequiredException;
import com.bluewhale.common.core.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 二次认证异常处理（Wave 6.22）
 *
 * <p>把 {@link ReauthRequiredException} 映射为 HTTP 428 + 业务码 {@code 42801}；
 * 独立 advice 只处理该类型，与全局 {@code GlobalExceptionHandler}（BizException → HTTP 200）
 * 无歧义。</p>
 *
 * @author BlueWhale
 */
@RestControllerAdvice
public class ReauthExceptionHandler {

    /**
     * 缺少有效 step-up 凭证
     *
     * @param e 二次认证异常
     * @return 428 响应体（含业务码，供前端分流）
     */
    @ExceptionHandler(ReauthRequiredException.class)
    @ResponseStatus(HttpStatus.PRECONDITION_REQUIRED)
    public Result<Void> handleReauthRequired(ReauthRequiredException e) {
        return Result.fail(ReauthRequiredException.CODE, "REAUTH_REQUIRED");
    }
}
