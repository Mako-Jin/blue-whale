package com.bluewhale.erp.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质量异常事实只读投影（来源视图 {@code v_erp_bi_quality_fact}）。
 *
 * @author BlueWhale
 */
@Data
public class QualityFact {

    /** 不合格品单号 */
    private String ncrNo;

    /** 来源单据号 */
    private String sourceNo;

    /** 物料ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 不合格数量 */
    private BigDecimal quantity;

    /** 状态 */
    private String status;

    /** 质量成本金额 */
    private BigDecimal costAmount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
