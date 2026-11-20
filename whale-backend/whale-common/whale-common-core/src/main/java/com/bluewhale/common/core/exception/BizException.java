package com.bluewhale.common.core.exception;

import lombok.Getter;

/**
 * 业务异常
 *
 * <p>{@code messageKey} 为可选的国际化文案键：以 {@link ErrorCode} 构造时自动带上枚举键，
 * 传入自定义文案时为空（自定义文案按调用方原文返回，不做二次翻译）。</p>
 *
 * @author BlueWhale
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final int code;

    /** 错误消息 */
    private final String message;

    /** 国际化文案键；自定义文案时为 {@code null} */
    private final String messageKey;

    /** 国际化文案的占位参数 */
    private final Object[] messageArgs;

    /**
     * 以错误码枚举构造业务异常
     *
     * @param errorCode 错误码枚举
     */
    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessageKey(), errorCode.getMessage());
    }

    /**
     * 以错误码枚举与消息构造业务异常
     *
     * <p>保留枚举的状态码，仅覆盖提示文案，供同一状态码下按场景给出更具体的说明；
     * 自定义文案不做国际化翻译。</p>
     *
     * @param errorCode 错误码枚举
     * @param message 错误消息
     */
    public BizException(ErrorCode errorCode, String message) {
        this(errorCode.getCode(), null, message);
    }

    /**
     * 以错误码枚举、国际化键与中文兜底构造业务异常。
     *
     * <p>{@code message} 同时作为中文兜底与国际化资源缺失时的展示文案；键在
     * {@code GlobalExceptionHandler} 中按请求 Locale 解析。</p>
     *
     * @param errorCode 错误码枚举
     * @param messageKey 国际化文案键
     * @param message 中文兜底文案
     * @param args 文案占位参数
     */
    public BizException(ErrorCode errorCode, String messageKey, String message, Object... args) {
        this(errorCode.getCode(), messageKey, message, args);
    }

    /**
     * 以业务错误码、国际化键与中文兜底构造业务异常。
     *
     * <p>供服务内自定义错误码常量（非 {@link ErrorCode} 枚举）使用：状态码保持不变，
     * 提示文案按请求 Locale 解析，资源缺失时回落到 {@code message}。</p>
     *
     * @param code 业务错误码
     * @param messageKey 国际化文案键
     * @param message 中文兜底文案
     * @param args 文案占位参数
     */
    public BizException(int code, String messageKey, String message, Object... args) {
        super(message);
        this.code = code;
        this.message = message;
        this.messageKey = messageKey;
        this.messageArgs = args == null ? new Object[0] : args.clone();
    }

    /**
     * 以错误码与消息构造业务异常
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public BizException(int code, String message) {
        this(code, null, message);
    }

    /**
     * 以消息构造业务异常，错误码取系统错误
     *
     * @param message 错误消息
     */
    public BizException(String message) {
        this(ErrorCode.SYSTEM_ERROR.getCode(), null, message);
    }
}
