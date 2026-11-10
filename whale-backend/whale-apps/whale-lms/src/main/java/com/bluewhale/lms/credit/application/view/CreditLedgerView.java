package com.bluewhale.lms.credit.application.view;

import com.bluewhale.lms.credit.domain.model.CreditLedger;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学分台账视图。
 *
 * <p>{@link #ruleCode} 是记账当时的快照，规则后来改名或删除都不影响这条记录的可追溯性。</p>
 *
 * @author BlueWhale
 */
@Data
public class CreditLedgerView {

    /** 台账 ID */
    private Long id;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 来源类型 */
    private String sourceType;

    /** 来源业务 ID */
    private Long sourceId;

    /** 命中规则编码 */
    private String ruleCode;

    /** 本次学分 */
    private BigDecimal credit;

    /** 业务发生时间 */
    private LocalDateTime occurredAt;

    /** 归属年度 */
    private Integer periodYear;

    /** 记账人 */
    private Long recordedBy;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param ledger 台账记录
     * @return 视图
     */
    public static CreditLedgerView from(CreditLedger ledger) {
        CreditLedgerView view = new CreditLedgerView();
        view.setId(ledger.getId());
        view.setUserId(ledger.getUserId());
        view.setUsername(ledger.getUsername());
        CreditSource source = ledger.getSourceType();
        view.setSourceType(source == null ? null : source.name());
        view.setSourceId(ledger.getSourceId());
        view.setRuleCode(ledger.getRuleCode());
        view.setCredit(ledger.getCredit());
        view.setOccurredAt(ledger.getOccurredAt());
        view.setPeriodYear(ledger.getPeriodYear());
        view.setRecordedBy(ledger.getRecordedBy());
        view.setRemark(ledger.getRemark());
        return view;
    }
}
