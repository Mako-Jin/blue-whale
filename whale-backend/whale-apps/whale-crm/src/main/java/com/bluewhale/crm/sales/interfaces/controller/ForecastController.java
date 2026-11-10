package com.bluewhale.crm.sales.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.sales.application.command.ForecastAdjustCommand;
import com.bluewhale.crm.sales.application.command.ForecastGenerateCommand;
import com.bluewhale.crm.sales.application.service.ForecastAppService;
import com.bluewhale.crm.sales.application.view.ForecastAdjustmentView;
import com.bluewhale.crm.sales.application.view.ForecastSnapshotView;
import com.bluewhale.crm.sales.domain.model.ForecastQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 销售预测接口（M2 增量）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastAppService forecastAppService;

    /**
     * 预测快照分页。
     *
     * @param period 周期
     * @param periodType 周期类型
     * @param scopeType 范围类型
     * @param ownerId 负责人
     * @param deptId 部门
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 预测分页
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.FORECAST_LIST)
    public Result<PageResult<ForecastSnapshotView>> page(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "periodType", required = false) String periodType,
            @RequestParam(value = "scopeType", required = false) String scopeType,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        ForecastQuery query = new ForecastQuery();
        query.setPeriod(period);
        query.setPeriodType(periodType);
        query.setScopeType(scopeType);
        query.setOwnerId(ownerId);
        query.setDeptId(deptId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(forecastAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 预测快照详情。
     *
     * @param id 快照 ID
     * @param request 当前请求
     * @return 预测详情
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.FORECAST_LIST)
    public Result<ForecastSnapshotView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(forecastAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 预测调整记录。
     *
     * @param id 快照 ID
     * @param request 当前请求
     * @return 调整记录
     */
    @GetMapping("/{id}/adjustments")
    @RequirePermission(CrmPermissions.FORECAST_LIST)
    public Result<List<ForecastAdjustmentView>> adjustments(@PathVariable("id") Long id,
                                                            HttpServletRequest request) {
        return Result.ok(forecastAppService.adjustments(id, CrmActorContext.from(request)));
    }

    /**
     * 生成 / 重算预测快照。
     *
     * @param command 生成命令
     * @param request 当前请求
     * @return 预测详情
     */
    @PostMapping("/generate")
    @RequirePermission(CrmPermissions.FORECAST_MANAGE)
    @AuditLog(operation = "生成销售预测快照", type = OperateType.CREATE, targetType = "crm_forecast_snapshot")
    public Result<ForecastSnapshotView> generate(@Valid @RequestBody ForecastGenerateCommand command,
                                                 HttpServletRequest request) {
        return Result.ok(forecastAppService.generate(command, CrmActorContext.from(request)));
    }

    /**
     * 手工调整预测金额。
     *
     * @param id 快照 ID
     * @param command 调整命令
     * @param request 当前请求
     * @return 预测详情
     */
    @PutMapping("/{id}/adjust")
    @RequirePermission(CrmPermissions.FORECAST_MANAGE)
    @AuditLog(operation = "调整销售预测", type = OperateType.UPDATE,
            targetType = "crm_forecast_snapshot", targetId = "#id")
    public Result<ForecastSnapshotView> adjust(@PathVariable("id") Long id,
                                               @Valid @RequestBody ForecastAdjustCommand command,
                                               HttpServletRequest request) {
        return Result.ok(forecastAppService.adjust(id, command, CrmActorContext.from(request)));
    }
}
