package com.bluewhale.crm.customer.application.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 客户导入预检视图（M1-9）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerImportPreviewView {

    /** 总行数 */
    private int total;

    /** 可导入行数 */
    private int valid;

    /** 校验失败行数 */
    private int invalid;

    /** 重复行数 */
    private int duplicate;

    /** 单次导入行数上限 */
    private int maxRows;

    /** 逐行结果 */
    private List<CustomerImportRowView> rows = new ArrayList<>();
}
