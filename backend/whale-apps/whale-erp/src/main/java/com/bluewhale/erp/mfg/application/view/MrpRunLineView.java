package com.bluewhale.erp.mfg.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MRP 输入需求行视图。
 *
 * @author BlueWhale
 */
@Data
public class MrpRunLineView {

    /** 行号 */
    private Integer lineNo;

    /** 需求物料编码 */
    private String itemCode;

    /** BOM 编号 */
    private String bomNo;

    /** BOM 版本 */
    private String bomVersion;

    /** 需求数量 */
    private BigDecimal quantity;

    /** 需求日期 */
    private LocalDate dueDate;
}
