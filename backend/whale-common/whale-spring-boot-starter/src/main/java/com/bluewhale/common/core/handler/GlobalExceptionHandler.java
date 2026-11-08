package com.bluewhale.common.core.handler;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 *
 * @author BlueWhale
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * @param e 业务异常
     * @return 携带错误码与消息的失败响应
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常，取首个字段错误消息
     *
     * @param e 参数校验异常
     * @return 携带参数错误码与消息的失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? ErrorCode.PARAM_ERROR.getMessage() : fieldError.getDefaultMessage();
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理约束校验异常，取首个违规消息
     *
     * @param e 约束校验异常
     * @return 携带参数错误码与消息的失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理请求体不可解析异常（JSON 语法错误、字段类型或日期格式不匹配）。
     *
     * <p>这是调用方的错，不是系统故障：不单独处理会被兜底成 500「系统异常」，
     * 让前端和排障者都误以为服务端崩了。</p>
     *
     * @param e 请求体不可解析异常
     * @return 携带参数错误码与消息的失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), ErrorCode.PARAM_ERROR.getMessage());
    }

    /**
     * 处理缺少必填请求参数异常
     *
     * @param e 缺少请求参数异常
     * @return 携带参数错误码与消息的失败响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), "缺少参数: " + e.getParameterName());
    }

    /**
     * 处理请求参数类型不匹配异常（如路径变量传入非数字）
     *
     * @param e 参数类型不匹配异常
     * @return 携带参数错误码与消息的失败响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("请求参数类型不匹配: name={}, value={}", e.getName(), e.getValue());
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), "参数类型不匹配: " + e.getName());
    }

    /**
     * 处理请求方法不支持异常（路径存在但 HTTP 方法不匹配）
     *
     * @param e 请求方法不支持异常
     * @return 携带 405 错误码与消息的失败响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: method={}, supported={}", e.getMethod(), e.getSupportedHttpMethods());
        return Result.fail(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 处理静态资源 / 路径不存在异常，避免被兜底处理器包装成 500
     *
     * @param e 资源不存在异常
     * @return 携带 404 错误码与消息的失败响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return Result.fail(ErrorCode.NOT_FOUND);
    }

    /**
     * 兜底处理未捕获异常
     *
     * @param e 系统异常
     * @return 携带系统错误码与消息的失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
