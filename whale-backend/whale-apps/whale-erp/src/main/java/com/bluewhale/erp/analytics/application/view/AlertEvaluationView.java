package com.bluewhale.erp.analytics.application.view;

import lombok.Data;

import java.util.List;

/**
 * 预警评估结果视图。
 *
 * @author BlueWhale
 */
@Data
public class AlertEvaluationView {

    /** 参与评估的规则数 */
    private Integer evaluatedRules;

    /** 新触发事件数 */
    private Integer createdEvents;

    /** 同规则同对象同周期去重跳过数 */
    private Integer duplicateSkipped;

    /** 因数据源缺失而跳过的规则编号 */
    private List<String> skippedRules;

    /** 说明 */
    private String note;
}
