package com.bluewhale.common.core.exception;

import lombok.Getter;

/**
 * 业务异常
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

    /**
     * 以错误码枚举构造业务异常
     *
     * @param errorCode 错误码枚举
     */
    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 以错误码枚举与消息构造业务异常
     *
     * <p>保留枚举的状态码，仅覆盖提示文案，供同一状态码下按场景给出更具体的说明。</p>
     *
     * @param errorCode 错误码枚举
     * @param message 错误消息
     */
    public BizException(ErrorCode errorCode, String message) {
        this(errorCode.getCode(), message);
    }

    /**
     * 以错误码与消息构造业务异常
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 以消息构造业务异常，错误码取系统错误
     *
     * @param message 错误消息
     */
    public BizException(String message) {
        this(ErrorCode.SYSTEM_ERROR.getCode(), message);
    }
}
