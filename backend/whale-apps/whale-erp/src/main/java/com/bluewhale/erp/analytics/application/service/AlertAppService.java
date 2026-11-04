package com.bluewhale.erp.analytics.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.analytics.application.command.AlertHandleCommand;
import com.bluewhale.erp.analytics.application.command.AlertRuleSaveCommand;
import com.bluewhale.erp.analytics.application.view.AlertEvaluationView;
import com.bluewhale.erp.analytics.application.view.AlertEventView;
import com.bluewhale.erp.analytics.application.view.AlertRuleView;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;

/**
 * 预警中心应用服务。
 *
 * @author BlueWhale
 */
public interface AlertAppService {

    /**
     * 预警规则分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AlertRuleView> pageRules(AnalyticsQuery query);

    /**
     * 新增预警规则。
     *
     * @param command 保存命令
     * @return 规则
     */
    AlertRuleView createRule(AlertRuleSaveCommand command);

    /**
     * 编辑预警规则。
     *
     * @param ruleNo 规则编号
     * @param command 保存命令
     * @return 规则
     */
    AlertRuleView updateRule(String ruleNo, AlertRuleSaveCommand command);

    /**
     * 预警事件分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AlertEventView> pageEvents(AnalyticsQuery query);

    /**
     * 评估预警规则并生成事件（同规则同对象同周期去重）。
     *
     * @param ruleNo 规则编号，可空（空则评估全部启用规则）
     * @param operator 操作人
     * @return 评估结果
     */
    AlertEvaluationView evaluate(String ruleNo, String operator);

    /**
     * 确认预警事件。
     *
     * @param id 事件主键
     * @param command 处理命令
     * @return 事件
     */
    AlertEventView acknowledge(Long id, AlertHandleCommand command);

    /**
     * 关闭预警事件。
     *
     * @param id 事件主键
     * @param command 处理命令
     * @return 事件
     */
    AlertEventView close(Long id, AlertHandleCommand command);
}
