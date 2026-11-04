package com.bluewhale.erp.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.hr.application.command.AppraisalSaveCommand;
import com.bluewhale.erp.hr.application.command.KpiCreateCommand;
import com.bluewhale.erp.hr.application.command.PerformanceCycleCreateCommand;
import com.bluewhale.erp.hr.application.service.PerformanceAppService;
import com.bluewhale.erp.hr.application.view.PerformanceCycleView;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.interfaces.support.PageParams;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 绩效管理接口：周期、指标、考核与关闭。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/hr/performance")
@RequiredArgsConstructor
public class HrPerformanceController {

    private final PerformanceAppService performanceAppService;

    /**
     * 绩效周期分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 周期分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:hr:performance:list")
    public Result<PageResult<PerformanceCycleView>> page(
            @ModelAttribute HrQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(performanceAppService.page(query));
    }

    /**
     * 周期详情（含指标与考核结果）。
     *
     * @param id 周期主键
     * @return 周期视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:hr:performance:list")
    public Result<PerformanceCycleView> detail(@PathVariable Long id) {
        return Result.ok(performanceAppService.detail(id));
    }

    /**
     * 新建绩效周期。
     *
     * @param command 创建命令
     * @return 周期视图
     */
    @PostMapping
    @RequirePermission("sys:erp:hr:performance:create")
    @AuditLog(operation = "新建绩效周期", targetType = "erp_hr_performance_cycle", type = OperateType.CREATE)
    public Result<PerformanceCycleView> create(@Valid @RequestBody PerformanceCycleCreateCommand command) {
        return Result.ok(performanceAppService.create(command));
    }

    /**
     * 新增绩效指标。
     *
     * @param id 周期主键
     * @param command 指标命令
     * @return 周期视图
     */
    @PostMapping("/{id}/kpi")
    @RequirePermission("sys:erp:hr:performance:appraise")
    @AuditLog(operation = "新增绩效指标", targetType = "erp_hr_kpi", type = OperateType.CREATE)
    public Result<PerformanceCycleView> addKpi(@PathVariable Long id,
                                               @Valid @RequestBody KpiCreateCommand command) {
        return Result.ok(performanceAppService.addKpi(id, command));
    }

    /**
     * 录入考核结果。
     *
     * @param id 周期主键
     * @param command 考核命令
     * @return 周期视图
     */
    @PostMapping("/{id}/appraise")
    @RequirePermission("sys:erp:hr:performance:appraise")
    @AuditLog(operation = "录入绩效考核", targetType = "erp_hr_appraisal", type = OperateType.UPDATE)
    public Result<PerformanceCycleView> appraise(@PathVariable Long id,
                                                 @Valid @RequestBody AppraisalSaveCommand command) {
        return Result.ok(performanceAppService.appraise(id, command));
    }

    /**
     * 关闭绩效周期。
     *
     * @param id 周期主键
     * @return 周期视图
     */
    @PostMapping("/{id}/close")
    @RequirePermission("sys:erp:hr:performance:close")
    @AuditLog(operation = "关闭绩效周期", targetType = "erp_hr_performance_cycle", type = OperateType.UPDATE)
    public Result<PerformanceCycleView> close(@PathVariable Long id) {
        return Result.ok(performanceAppService.close(id));
    }
}
