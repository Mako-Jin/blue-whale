package com.bluewhale.oa.approval.application.view;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 业务详情字段。
 *
 * @author BlueWhale
 */
@Data
@AllArgsConstructor
public class ApprovalDetailFieldView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段标签 */
    private String label;

    /** 字段值 */
    private String value;
}
