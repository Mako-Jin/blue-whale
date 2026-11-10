package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MRP 需求追溯实体，对应表 {@code erp_mfg_mrp_trace}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_mrp_trace")
public class MrpTraceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 运行记录 ID */
    private Long runId;

    /** 计划订单 ID */
    private Long plannedOrderId;

    /** 来源需求行号 */
    private Integer demandLineNo;

    /** BOM 编号 */
    private String bomNo;

    /** BOM 版本 */
    private String bomVersion;

    /** BOM 路径 */
    private String bomPath;

    /** BOM 层级 */
    private Integer depth;

    /** 单位累计用量 */
    private BigDecimal unitQty;

    /** 损耗率 */
    private BigDecimal lossRate;

    /** 贡献毛需求量 */
    private BigDecimal grossQuantity;
}
