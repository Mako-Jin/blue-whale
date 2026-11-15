package com.bluewhale.form.model.request;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单主从数据对账差异分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FormReconcileDiffQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 对账批次号（精确匹配） */
    private String batchNo;

    /** 状态：0 待处理 / 1 已修复 / 2 已忽略 */
    private Integer status;

    /** 物理表名（精确匹配） */
    private String physicalTable;
}
