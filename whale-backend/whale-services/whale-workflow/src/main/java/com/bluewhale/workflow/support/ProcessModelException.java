package com.bluewhale.workflow.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.Getter;

/**
 * 流程模型异常：携带 {@link ValidationCode} 的业务异常，供解析失败时定位具体校验码。
 *
 * <p>继承 {@link BizException} 并保持 {@code code=400 (PARAM_ERROR)} 与原有文案
 * （legacy 非法定义仍为「流程定义缺少有效审批节点」），因此既有调用方与旧用例的
 * {@code getCode()/getMessage()} 断言不受影响。</p>
 *
 * @author BlueWhale
 */
@Getter
public class ProcessModelException extends BizException {

    private static final long serialVersionUID = 1L;

    /** 校验码 */
    private final ValidationCode validationCode;

    /** 关联元素键（节点键/流转键/元素名），可为 {@code null} */
    private final String refKey;

    /**
     * 以校验码 + 文案构造异常。
     *
     * @param validationCode 校验码
     * @param message 错误文案
     */
    public ProcessModelException(ValidationCode validationCode, String message) {
        this(validationCode, message, null);
    }

    /**
     * 以校验码 + 文案 + 关联元素键构造异常。
     *
     * @param validationCode 校验码
     * @param message 错误文案
     * @param refKey 关联元素键，可为 {@code null}
     */
    public ProcessModelException(ValidationCode validationCode, String message, String refKey) {
        super(ErrorCode.PARAM_ERROR.getCode(), message);
        this.validationCode = validationCode;
        this.refKey = refKey;
    }
}
