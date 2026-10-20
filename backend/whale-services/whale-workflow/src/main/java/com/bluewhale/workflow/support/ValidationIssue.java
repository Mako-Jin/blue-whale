package com.bluewhale.workflow.support;

import lombok.Data;

/**
 * 校验问题条目：{@code code} + {@code message} + {@code level} + {@code refKey}。
 *
 * @author BlueWhale
 */
@Data
public class ValidationIssue {

    /** 校验码 */
    private ValidationCode code;

    /** 提示文案 */
    private String message;

    /** 级别（由 {@link ValidationCode#getLevel()} 派生） */
    private ValidationLevel level;

    /** 关联元素键（节点键/流转键/元素名），可为 {@code null} */
    private String refKey;

    /**
     * 以校验码与文案构造问题条目，级别取自校验码。
     *
     * @param code 校验码
     * @param message 提示文案
     * @param refKey 关联元素键，可为 {@code null}
     */
    public ValidationIssue(ValidationCode code, String message, String refKey) {
        this.code = code;
        this.message = message;
        this.level = code.getLevel();
        this.refKey = refKey;
    }

    /**
     * 以校验码默认文案构造问题条目。
     *
     * @param code 校验码
     * @param refKey 关联元素键，可为 {@code null}
     * @return 问题条目
     */
    public static ValidationIssue of(ValidationCode code, String refKey) {
        return new ValidationIssue(code, code.getDefaultMessage(), refKey);
    }

    /**
     * 以校验码 + 自定义文案构造问题条目。
     *
     * @param code 校验码
     * @param message 提示文案
     * @param refKey 关联元素键，可为 {@code null}
     * @return 问题条目
     */
    public static ValidationIssue of(ValidationCode code, String message, String refKey) {
        return new ValidationIssue(code, message, refKey);
    }
}
