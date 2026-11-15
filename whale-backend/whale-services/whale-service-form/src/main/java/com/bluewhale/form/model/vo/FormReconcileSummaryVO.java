package com.bluewhale.form.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 表单主从数据对账执行摘要视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class FormReconcileSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对账批次号（{@code yyyyMMddHHmmss}） */
    private String batchNo;

    /** 本次对账遍历的物理表数量 */
    private int tableCount;

    /** 本次登记的新差异条数 */
    private int diffCount;

    /** 是否发生截断（物理表扫描超上限或差异登记超上限） */
    private boolean truncated;
}
