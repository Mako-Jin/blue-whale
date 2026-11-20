package com.bluewhale.common.core.handler;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.i18n.CommonMessageResolver;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;

/**
 * 全局异常处理器
 *
 * <p>错误文案按请求 Locale 解析，优先级：主 {@code MessageSource}
 * （已接入 i18n SDK 时为数据库 &gt; 属性文件）→ 公共错误资源包 → {@link ErrorCode} 中文常量。
 * 主资源缺失时仍返回中文，保证展示层永不空白。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 主消息源未命中时的哨兵，用于区分「未命中」与「命中空文案」 */
    private static final String MISSING = "__WHALE_I18N_MISSING__";

    /** 主消息源（已接入 i18n SDK 时为三级回退实现） */
    private MessageSource messageSource;

    /** 公共错误资源包解析器 */
    private CommonMessageResolver commonMessageResolver;

    /**
     * 注入主消息源（可选：无 i18n 能力的环境保持中文兜底）
     *
     * @param messageSource 主消息源
     */
    @Autowired(required = false)
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 注入公共错误资源包解析器（可选：单元测试可直接构造处理器）
     *
     * @param commonMessageResolver 公共文案解析器
     */
    @Autowired(required = false)
    public void setCommonMessageResolver(CommonMessageResolver commonMessageResolver) {
        this.commonMessageResolver = commonMessageResolver;
    }

    /**
     * 处理业务异常
     *
     * @param e 业务异常
     * @return 携带错误码与消息的失败响应
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        if (e.getMessageKey() != null) {
            return Result.fail(e.getCode(),
                    resolve(e.getMessageKey(), e.getMessageArgs(), e.getMessage()));
        }
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
        String message = fieldError == null
                ? resolve(ErrorCode.PARAM_ERROR.getMessageKey(), null, ErrorCode.PARAM_ERROR.getMessage())
                : fieldError.getDefaultMessage();
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
                .orElseGet(() -> resolve(ErrorCode.PARAM_ERROR.getMessageKey(), null,
                        ErrorCode.PARAM_ERROR.getMessage()));
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
        return fail(ErrorCode.PARAM_ERROR);
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
        String fallback = ErrorCode.MISSING_PARAMETER.getMessage() + ": " + e.getParameterName();
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(),
                resolve(ErrorCode.MISSING_PARAMETER.getMessageKey(), new Object[]{e.getParameterName()}, fallback));
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
        String fallback = ErrorCode.PARAM_TYPE_MISMATCH.getMessage() + ": " + e.getName();
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(),
                resolve(ErrorCode.PARAM_TYPE_MISMATCH.getMessageKey(), new Object[]{e.getName()}, fallback));
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
        return fail(ErrorCode.METHOD_NOT_ALLOWED);
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
        return fail(ErrorCode.NOT_FOUND);
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
        return fail(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 以错误码枚举构建本地化失败响应
     *
     * @param errorCode 错误码枚举
     * @return 失败响应
     */
    private Result<Void> fail(ErrorCode errorCode) {
        return Result.fail(errorCode.getCode(),
                resolve(errorCode.getMessageKey(), null, errorCode.getMessage()));
    }

    /**
     * 按当前请求 Locale 解析文案：主消息源 → 公共资源包 → 调用方兜底。
     *
     * @param key         文案键
     * @param args        占位参数
     * @param fallbackText 未命中时的中文兜底
     * @return 解析结果
     */
    private String resolve(String key, Object[] args, String fallbackText) {
        Locale locale = LocaleContextHolder.getLocale();
        if (messageSource != null) {
            String text = messageSource.getMessage(key, args, MISSING, locale);
            if (text != null && !MISSING.equals(text)) {
                return text;
            }
        }
        if (commonMessageResolver != null) {
            String text = commonMessageResolver.resolve(key, args, locale, null);
            if (text != null) {
                return text;
            }
        }
        return fallbackText;
    }
}
