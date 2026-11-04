package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 库存盘点单头实体，对应表 {@code erp_scm_stocktake}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_stocktake")
public class StocktakeEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 盘点单号 */
    private String docNo;

    /** 盘点日期 */
    private LocalDate stocktakeDate;

    /** 盘点仓库 ID */
    private Long warehouseId;

    /** 仓库名称 */
    private String warehouseName;

    /** 状态 */
    private String status;

    /** 盘盈金额 */
    private BigDecimal gainAmount;

    /** 盘亏金额 */
    private BigDecimal lossAmount;

    /** 备注 */
    private String remark;
}
