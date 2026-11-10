package com.bluewhale.erp.mfg.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.mfg.application.command.ForecastCreateCommand;
import com.bluewhale.erp.mfg.application.service.ForecastAppService;
import com.bluewhale.erp.mfg.application.view.ForecastView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
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
 * 需求预测接口：查询、建单、发布、取消。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/mfg/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastAppService forecastAppService;

    /**
     * 预测分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 预测分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:forecast:list")
    public Result<PageResult<ForecastView>> page(
            @ModelAttribute MfgQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(forecastAppService.page(query));
    }

    /**
     * 预测详情。
     *
     * @param id 预测 ID
     * @return 预测视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:forecast:list")
    public Result<ForecastView> detail(@PathVariable Long id) {
        return Result.ok(forecastAppService.detail(id));
    }

    /**
     * 新增预测批次。
     *
     * @param command 创建命令
     * @return 预测视图
     */
    @PostMapping
    @RequirePermission("sys:erp:forecast:add")
    @AuditLog(operation = "新增需求预测", targetType = "erp_mfg_forecast", type = OperateType.CREATE)
    public Result<ForecastView> create(@Valid @RequestBody ForecastCreateCommand command) {
        return Result.ok(forecastAppService.create(command));
    }

    /**
     * 发布预测。
     *
     * @param id 预测 ID
     * @return 预测视图
     */
    @PostMapping("/{id}/publish")
    @RequirePermission("sys:erp:forecast:publish")
    @AuditLog(operation = "发布需求预测", targetType = "erp_mfg_forecast", type = OperateType.UPDATE)
    public Result<ForecastView> publish(@PathVariable Long id) {
        return Result.ok(forecastAppService.publish(id));
    }

    /**
     * 取消预测。
     *
     * @param id 预测 ID
     * @return 预测视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:forecast:cancel")
    @AuditLog(operation = "取消需求预测", targetType = "erp_mfg_forecast", type = OperateType.UPDATE)
    public Result<ForecastView> cancel(@PathVariable Long id) {
        return Result.ok(forecastAppService.cancel(id));
    }
}
