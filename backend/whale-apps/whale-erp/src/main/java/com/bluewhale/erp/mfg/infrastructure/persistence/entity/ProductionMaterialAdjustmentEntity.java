package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产用料调整实体，对应表 {@code erp_mfg_production_material_adj}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_production_material_adj")
public class ProductionMaterialAdjustmentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 生产订单 ID */
    private Long orderId;

    /** 调整单号 */
    private String docNo;

    /** 调整类型：RETURN / SCRAP */
    private String adjType;

    /** 用料行号 */
    private Integer lineNo;

    /** 物料 ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 调整数量 */
    private BigDecimal quantity;

    /** 调整金额 */
    private BigDecimal amount;

    /** 原因 */
    private String reason;

    /** 操作人 ID */
    private Long operatorId;

    /** 发生时间 */
    private LocalDateTime occurredAt;
}
