package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 库存调拨单头实体，对应表 {@code erp_scm_transfer}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_transfer")
public class TransferEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 调拨单号 */
    private String docNo;

    /** 调拨日期 */
    private LocalDate transferDate;

    /** 调出仓库 ID */
    private Long fromWarehouseId;

    /** 调出仓库名称 */
    private String fromWarehouseName;

    /** 调入仓库 ID */
    private Long toWarehouseId;

    /** 调入仓库名称 */
    private String toWarehouseName;

    /** 状态 */
    private String status;

    /** 调拨总数量 */
    private BigDecimal totalQuantity;

    /** 调拨总金额 */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;
}
