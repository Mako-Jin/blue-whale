package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 校验问题视图对象。
 *
 * @author BlueWhale
 */
@Data
public class ValidationIssueVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 校验码（{@code ValidationCode} 名称） */
    private String code;

    /** 提示文案 */
    private String message;

    /** 级别：ERROR / WARNING */
    private String level;

    /** 关联元素键，可为 {@code null} */
    private String refKey;
}
