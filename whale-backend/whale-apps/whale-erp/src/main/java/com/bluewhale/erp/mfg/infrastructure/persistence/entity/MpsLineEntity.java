package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 主生产计划分录实体，对应表 {@code erp_mfg_mps_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_mps_line")
public class MpsLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主生产计划 ID */
    private Long mpsId;

    /** 行号 */
    private Integer lineNo;

    /** 物料 ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 计划数量 */
    private BigDecimal plannedQuantity;

    /** 已下达数量 */
    private BigDecimal releasedQuantity;
}
