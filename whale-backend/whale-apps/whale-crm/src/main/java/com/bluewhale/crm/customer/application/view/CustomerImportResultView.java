package com.bluewhale.crm.customer.application.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 客户导入执行结果视图（M1-9）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerImportResultView {

    /** 成功创建行数 */
    private int created;

    /** 跳过行数（校验失败或重复） */
    private int skipped;

    /** 写入失败行数 */
    private int failed;

    /** 逐行结果 */
    private List<CustomerImportRowView> rows = new ArrayList<>();
}
