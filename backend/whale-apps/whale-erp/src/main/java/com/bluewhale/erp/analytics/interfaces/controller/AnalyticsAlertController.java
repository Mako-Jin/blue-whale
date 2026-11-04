package com.bluewhale.erp.analytics.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.analytics.application.command.AlertHandleCommand;
import com.bluewhale.erp.analytics.application.command.AlertRuleSaveCommand;
import com.bluewhale.erp.analytics.application.service.AlertAppService;
import com.bluewhale.erp.analytics.application.view.AlertEvaluationView;
import com.bluewhale.erp.analytics.application.view.AlertEventView;
import com.bluewhale.erp.analytics.application.view.AlertRuleView;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;
import com.bluewhale.erp.interfaces.support.PageParams;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预警中心接口：规则、事件、评估与处置。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/bi/alert")
@RequiredArgsConstructor
public class AnalyticsAlertController {

    private final AlertAppService alertAppService;

    /**
     * 预警规则分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/rule/page")
    @RequirePermission("sys:erp:bi:alert:list")
    public Result<PageResult<AlertRuleView>> pageRules(
            @ModelAttribute AnalyticsQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(alertAppService.pageRules(query));
    }

    /**
     * 新增预警规则。
     *
     * @param command 保存命令
     * @return 规则
     */
    @PostMapping("/rule")
    @RequirePermission("sys:erp:bi:alert:save")
    @AuditLog(operation = "新增预警规则", targetType = "erp_bi_alert_rule", type = OperateType.CREATE)
    public Result<AlertRuleView> createRule(@RequestBody AlertRuleSaveCommand command) {
        return Result.ok(alertAppService.createRule(command));
    }

    /**
     * 编辑预警规则。
     *
     * @param ruleNo 规则编号
     * @param command 保存命令
     * @return 规则
     */
    @PutMapping("/rule/{ruleNo}")
    @RequirePermission("sys:erp:bi:alert:save")
    @AuditLog(operation = "编辑预警规则", targetType = "erp_bi_alert_rule", type = OperateType.UPDATE)
    public Result<AlertRuleView> updateRule(@PathVariable String ruleNo,
                                            @RequestBody AlertRuleSaveCommand command) {
        return Result.ok(alertAppService.updateRule(ruleNo, command));
    }

    /**
     * 预警事件分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/event/page")
    @RequirePermission("sys:erp:bi:alert:list")
    public Result<PageResult<AlertEventView>> pageEvents(
            @ModelAttribute AnalyticsQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(alertAppService.pageEvents(query));
    }

    /**
     * 评估预警规则。
     *
     * @param ruleNo 规则编号，可空
     * @param operator 操作人，可空
     * @return 评估结果
     */
    @PostMapping("/evaluate")
    @RequirePermission("sys:erp:bi:alert:evaluate")
    @AuditLog(operation = "评估预警规则", targetType = "erp_bi_alert_event", type = OperateType.UPDATE)
    public Result<AlertEvaluationView> evaluate(
            @RequestParam(value = "ruleNo", required = false) String ruleNo,
            @RequestParam(value = "operator", required = false) String operator) {
        return Result.ok(alertAppService.evaluate(ruleNo, operator));
    }

    /**
     * 确认预警事件。
     *
     * @param id 事件主键
     * @param command 处理命令
     * @return 事件
     */
    @PostMapping("/event/{id}/acknowledge")
    @RequirePermission("sys:erp:bi:alert:handle")
    @AuditLog(operation = "确认预警事件", targetType = "erp_bi_alert_event", type = OperateType.UPDATE)
    public Result<AlertEventView> acknowledge(@PathVariable Long id,
                                             @RequestBody(required = false) AlertHandleCommand command) {
        return Result.ok(alertAppService.acknowledge(id, command == null ? new AlertHandleCommand() : command));
    }

    /**
     * 关闭预警事件。
     *
     * @param id 事件主键
     * @param command 处理命令
     * @return 事件
     */
    @PostMapping("/event/{id}/close")
    @RequirePermission("sys:erp:bi:alert:handle")
    @AuditLog(operation = "关闭预警事件", targetType = "erp_bi_alert_event", type = OperateType.UPDATE)
    public Result<AlertEventView> close(@PathVariable Long id,
                                        @RequestBody(required = false) AlertHandleCommand command) {
        return Result.ok(alertAppService.close(id, command == null ? new AlertHandleCommand() : command));
    }
}
