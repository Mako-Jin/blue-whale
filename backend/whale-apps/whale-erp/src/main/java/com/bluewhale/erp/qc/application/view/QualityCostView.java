package com.bluewhale.erp.qc.application.view;

import com.bluewhale.erp.qc.domain.model.QualityCost;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 质量成本视图。
 *
 * @author BlueWhale
 */
@Data
public class QualityCostView {

    /** 主键 */
    private Long id;

    /** 质量成本单号 */
    private String costNo;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据号 */
    private String sourceNo;

    /** 成本类型 */
    private String costType;

    /** 金额 */
    private BigDecimal amount;

    /** 成本日期 */
    private LocalDate costDate;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param cost 质量成本
     * @return 视图
     */
    public static QualityCostView of(QualityCost cost) {
        QualityCostView view = new QualityCostView();
        view.setId(cost.getId());
        view.setCostNo(cost.getCostNo());
        view.setSourceType(cost.getSourceType());
        view.setSourceNo(cost.getSourceNo());
        view.setCostType(cost.getCostType());
        view.setAmount(cost.getAmount());
        view.setCostDate(cost.getCostDate());
        view.setRemark(cost.getRemark());
        return view;
    }
}
