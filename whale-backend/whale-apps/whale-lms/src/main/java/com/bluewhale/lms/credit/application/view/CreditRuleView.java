package com.bluewhale.lms.credit.application.view;

import com.bluewhale.lms.credit.domain.model.CreditRule;
import com.bluewhale.lms.credit.domain.model.CreditRuleStatus;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学分规则视图。
 *
 * @author BlueWhale
 */
@Data
public class CreditRuleView {

    /** 规则 ID */
    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String name;

    /** 来源类型 */
    private String sourceType;

    /** 匹配对象 ID（0 为该来源全部） */
    private Long matchId;

    /** 是否通配规则 */
    private boolean matchAll;

    /** 单次学分 */
    private BigDecimal credit;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由领域对象构建视图。
     *
     * @param rule 规则
     * @return 视图
     */
    public static CreditRuleView from(CreditRule rule) {
        CreditRuleView view = new CreditRuleView();
        view.setId(rule.getId());
        view.setRuleCode(rule.getRuleCode());
        view.setName(rule.getName());
        CreditSource source = rule.getSourceType();
        view.setSourceType(source == null ? null : source.name());
        view.setMatchId(rule.getMatchId());
        view.setMatchAll(rule.isMatchAll());
        view.setCredit(rule.getCredit());
        CreditRuleStatus status = rule.getStatus() == null ? CreditRuleStatus.DISABLED : rule.getStatus();
        view.setStatus(status.name());
        view.setRemark(rule.getRemark());
        view.setCreateTime(rule.getCreateTime());
        view.setUpdateTime(rule.getUpdateTime());
        return view;
    }
}
