package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 质量成本记录。
 *
 * <p>不变量：成本类型只能为返工/报废/检验；金额为正；成本日期必填。</p>
 *
 * @author BlueWhale
 */
@Data
public class QualityCost {

    /** 主键 */
    private Long id;

    /** 质量成本单号 */
    private String costNo;

    /** 来源类型：NCR / MANUAL */
    private String sourceType;

    /** 来源单据号 */
    private String sourceNo;

    /** 成本类型：REWORK / SCRAP / INSPECTION */
    private String costType;

    /** 质量成本金额 */
    private BigDecimal amount;

    /** 成本日期 */
    private LocalDate costDate;

    /** 备注 */
    private String remark;

    /**
     * 校验建单数据。
     */
    public void validateForCreate() {
        if (sourceNo == null || sourceNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "质量成本来源单据号不能为空");
        }
        if (!QcStatus.COST_REWORK.equals(costType)
                && !QcStatus.COST_SCRAP.equals(costType)
                && !QcStatus.COST_INSPECTION.equals(costType)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "质量成本类型只能为 REWORK、SCRAP 或 INSPECTION");
        }
        amount = Amounts.requirePositive(amount, "质量成本金额");
        if (costDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "质量成本日期不能为空");
        }
    }
}
