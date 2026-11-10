package com.bluewhale.crm.customer.application.view;

import lombok.Data;

/**
 * CSV 导入行结果视图（M1-9）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerImportRowView {

    /** 行号（含表头，从 1 开始） */
    private Integer lineNumber;

    /** 客户名称 */
    private String name;

    /** 状态：OK / INVALID / DUPLICATE */
    private String status;

    /** 原因（INVALID / DUPLICATE 时填写） */
    private String reason;
}
