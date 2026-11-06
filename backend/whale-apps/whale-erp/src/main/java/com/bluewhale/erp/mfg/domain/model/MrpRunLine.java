package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MRP 输入需求行。
 *
 * <p>持久化用于重跑与结果复现。</p>
 *
 * @author BlueWhale
 */
@Data
public class MrpRunLine {

    /** 行号（运行内唯一） */
    private Integer lineNo;

    /** 需求物料编码 */
    private String itemCode;

    /** PDM BOM 编号 */
    private String bomNo;

    /** PDM BOM 版本 */
    private String bomVersion;

    /** 需求数量 */
    private BigDecimal quantity;

    /** 需求日期 */
    private LocalDate dueDate;
}
