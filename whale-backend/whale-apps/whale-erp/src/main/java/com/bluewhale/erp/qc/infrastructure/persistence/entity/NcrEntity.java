package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 不合格品单实体，对应表 {@code erp_qc_ncr}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_ncr")
public class NcrEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 不合格品单号 */
    private String ncrNo;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据号 */
    private String sourceNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料名称 */
    private String itemName;

    /** 批次号 */
    private String lotNo;

    /** 不合格数量 */
    private BigDecimal quantity;

    /** 缺陷描述 */
    private String defectDesc;

    /** 处置方式 */
    private String disposition;

    /** 状态 */
    private String status;

    /** 质量成本金额 */
    private BigDecimal costAmount;

    /** 处置日期 */
    private LocalDate disposeDate;

    /** 关闭日期 */
    private LocalDate closeDate;

    /** 备注 */
    private String remark;
}
