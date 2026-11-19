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
        this(errorCode.getCode(), errorCode.getMessage(), errorCode.getMessageKey(), new Object[0]);
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
        this(errorCode.getCode(), message, null, new Object[0]);
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
        this(errorCode.getCode(), message, messageKey, args);
    }

    /**
     * 以错误码与消息构造业务异常
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public BizException(int code, String message) {
        this(code, message, null, new Object[0]);
    }

    /**
     * 以消息构造业务异常，错误码取系统错误
     *
     * @param message 错误消息
     */
    public BizException(String message) {
        this(ErrorCode.SYSTEM_ERROR.getCode(), message, null, new Object[0]);
    }

    /**
     * 全量构造器
     *
     * @param code       错误码
     * @param message    错误消息
     * @param messageKey 国际化文案键，可为 {@code null}
     * @param messageArgs 文案占位参数
     */
    private BizException(int code, String message, String messageKey, Object[] messageArgs) {
        super(message);
        this.code = code;
        this.message = message;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
    }
}
