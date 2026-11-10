package com.bluewhale.common.domain.exception;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 领域异常：表达领域不变量违规或领域规则冲突，继承 {@link BizException} 以便统一异常处理。
 *
 * @author BlueWhale
 */
public class DomainException extends BizException {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /**
     * 以错误码构造领域异常
     *
     * @param errorCode 错误码枚举
     */
    public DomainException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 以错误码与具体消息构造领域异常
     *
     * @param errorCode 错误码枚举
     * @param message 错误消息
     */
    public DomainException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
