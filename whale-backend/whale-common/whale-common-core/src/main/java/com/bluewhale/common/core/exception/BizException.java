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
     * @param message 中文兜底模板（构造时按 {@code args} 渲染，{@code getMessage()} 即可直接展示）
     * @param args 文案占位参数
     */
    public BizException(int code, String messageKey, String message, Object... args) {
        super(render(message, args));
        this.code = code;
        this.message = render(message, args);
        this.messageKey = messageKey;
        this.messageArgs = args == null ? new Object[0] : args.clone();
    }

    /**
     * 用占位参数渲染兜底文案。
     *
     * <p>{@code getMessage()} 必须仍是可直接展示的中文（日志、单测、旧调用方都按文案判断），
     * 因此兜底模板在构造时就地展开；未提供的占位符原样保留。这里不用
     * {@code MessageFormat}，避免长整型被插成 {@code 1,234,567} 这类本地化数字。</p>
     *
     * @param message 模板文案
     * @param args 占位参数
     * @return 渲染后的文案
     */
    private static String render(String message, Object... args) {
        if (message == null || args == null || args.length == 0 || message.indexOf('{') < 0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 16);
        int i = 0;
        while (i < message.length()) {
            char ch = message.charAt(i);
            if (ch == '{') {
                int end = message.indexOf('}', i + 1);
                if (end > i + 1) {
                    String raw = message.substring(i + 1, end);
                    if (raw.chars().allMatch(Character::isDigit)) {
                        int idx = Integer.parseInt(raw);
                        if (idx < args.length) {
                            out.append(args[idx]);
                            i = end + 1;
                            continue;
                        }
                    }
                }
            }
            out.append(ch);
            i++;
        }
        return out.toString();
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

    /**
     * 以国际化键与中文兜底构造业务异常，错误码取系统错误。
     *
     * <p>用于原先「只传中文文案」的调用迁移；键由 {@code GlobalExceptionHandler} 按请求
     * Locale 解析，资源缺失时回落到中文兜底。</p>
     *
     * @param messageKey 国际化文案键
     * @param message 中文兜底文案
     * @param args 文案占位参数
     */
    public BizException(String messageKey, String message, Object... args) {
        this(ErrorCode.SYSTEM_ERROR.getCode(), messageKey, message, args);
    }
}
