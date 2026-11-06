package com.bluewhale.party.fee.application.view;

import com.bluewhale.party.fee.domain.model.FeeRule;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 党费标准视图。
 *
 * @author BlueWhale
 */
@Data
public class FeeRuleView {

    /** 档位 ID */
    private Long id;

    /** 档位名称 */
    private String ruleName;

    /** 基数下限（含） */
    private BigDecimal minBase;

    /** 基数上限（不含，空表示无上限） */
    private BigDecimal maxBase;

    /** 缴费比例 */
    private BigDecimal rate;

    /** 比例百分比文本（如 0.5%） */
    private String ratePercent;

    /** 状态 */
    private String status;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /**
     * 由领域模型构建视图。
     *
     * @param rule 档位
     * @return 视图
     */
    public static FeeRuleView from(FeeRule rule) {
        FeeRuleView view = new FeeRuleView();
        view.setId(rule.getId());
        view.setRuleName(rule.getRuleName());
        view.setMinBase(rule.getMinBase());
        view.setMaxBase(rule.getMaxBase());
        view.setRate(rule.getRate());
        view.setRatePercent(rule.getRate() == null ? null
                : rule.getRate().multiply(BigDecimal.valueOf(100))
                        .stripTrailingZeros().toPlainString() + "%");
        view.setStatus(rule.getStatus());
        view.setSortNo(rule.getSortNo());
        view.setRemark(rule.getRemark());
        return view;
    }
}
