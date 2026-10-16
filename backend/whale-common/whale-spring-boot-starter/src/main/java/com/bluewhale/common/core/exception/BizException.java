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

    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage());
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BizException(String message) {
        this(ErrorCode.SYSTEM_ERROR.getCode(), message);
    }
}
