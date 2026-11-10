package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MRP 输入需求行实体，对应表 {@code erp_mfg_mrp_run_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_mrp_run_line")
public class MrpRunLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 运行记录 ID */
    private Long runId;

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
