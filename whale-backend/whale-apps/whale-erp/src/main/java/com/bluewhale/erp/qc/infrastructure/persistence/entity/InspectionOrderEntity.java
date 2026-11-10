package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 质量检验单实体，对应表 {@code erp_qc_inspection_order}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_inspection_order")
public class InspectionOrderEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 检验单号 */
    private String orderNo;

    /** 检验类型 */
    private String inspectionType;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据号 */
    private String sourceNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 批次号 */
    private String lotNo;

    /** 送检数量 */
    private BigDecimal quantity;

    /** 已检验数量 */
    private BigDecimal inspectedQuantity;

    /** 供应商编码 */
    private String supplierCode;

    /** 状态 */
    private String status;

    /** 检验员 */
    private String inspector;

    /** 检验日期 */
    private LocalDate inspectDate;

    /** 量具主键 */
    private Long toolId;

    /** 量具编号 */
    private String toolNo;

    /** 不合格品单主键 */
    private Long ncrId;

    /** 备注 */
    private String remark;
}
