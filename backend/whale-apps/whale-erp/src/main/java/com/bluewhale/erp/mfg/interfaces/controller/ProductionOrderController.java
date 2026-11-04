package com.bluewhale.erp.mfg.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.ErpUserContext;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.mfg.application.command.ProductionCompleteCommand;
import com.bluewhale.erp.mfg.application.command.ProductionIssueCommand;
import com.bluewhale.erp.mfg.application.command.ProductionOrderCreateCommand;
import com.bluewhale.erp.mfg.application.service.ProductionOrderAppService;
import com.bluewhale.erp.mfg.application.view.ProductionOrderView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import jakarta.servlet.http.HttpServletRequest;
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
 * 生产订单接口：查询、建单、下达、领料、完工入库、结案、取消。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/mfg/production")
@RequiredArgsConstructor
public class ProductionOrderController {

    private final ProductionOrderAppService productionOrderAppService;

    /**
     * 生产订单分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 生产订单分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:production:list")
    public Result<PageResult<ProductionOrderView>> page(
            @ModelAttribute MfgQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(productionOrderAppService.page(query));
    }

    /**
     * 生产订单详情。
     *
     * @param id 生产订单 ID
     * @return 生产订单视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:production:list")
    public Result<ProductionOrderView> detail(@PathVariable Long id) {
        return Result.ok(productionOrderAppService.detail(id));
    }

    /**
     * 新增生产订单。
     *
     * @param command 创建命令
     * @return 生产订单视图
     */
    @PostMapping
    @RequirePermission("sys:erp:production:add")
    @AuditLog(operation = "新增生产订单", targetType = "erp_mfg_production_order", type = OperateType.CREATE)
    public Result<ProductionOrderView> create(@Valid @RequestBody ProductionOrderCreateCommand command) {
        return Result.ok(productionOrderAppService.create(command));
    }

    /**
     * 下达生产订单。
     *
     * @param id 生产订单 ID
     * @return 生产订单视图
     */
    @PostMapping("/{id}/release")
    @RequirePermission("sys:erp:production:release")
    @AuditLog(operation = "下达生产订单", targetType = "erp_mfg_production_order", type = OperateType.UPDATE)
    public Result<ProductionOrderView> release(@PathVariable Long id) {
        return Result.ok(productionOrderAppService.release(id));
    }

    /**
     * 生产领料。
     *
     * @param id 生产订单 ID
     * @param command 领料命令
     * @return 生产订单视图
     */
    @PostMapping("/{id}/issue")
    @RequirePermission("sys:erp:production:issue")
    @AuditLog(operation = "生产领料", targetType = "erp_mfg_production_order", type = OperateType.UPDATE)
    public Result<ProductionOrderView> issue(@PathVariable Long id,
                                             @Valid @RequestBody ProductionIssueCommand command) {
        return Result.ok(productionOrderAppService.issue(id, command));
    }

    /**
     * 完工入库。
     *
     * @param id 生产订单 ID
     * @param command 完工命令
     * @param request HTTP 请求（取操作人）
     * @return 生产订单视图
     */
    @PostMapping("/{id}/complete")
    @RequirePermission("sys:erp:production:complete")
    @AuditLog(operation = "生产完工入库", targetType = "erp_mfg_production_order", type = OperateType.UPDATE)
    public Result<ProductionOrderView> complete(@PathVariable Long id,
                                                @Valid @RequestBody ProductionCompleteCommand command,
                                                HttpServletRequest request) {
        return Result.ok(productionOrderAppService.complete(id, command, ErpUserContext.userId(request)));
    }

    /**
     * 生产订单结案。
     *
     * @param id 生产订单 ID
     * @return 生产订单视图
     */
    @PostMapping("/{id}/close")
    @RequirePermission("sys:erp:production:close")
    @AuditLog(operation = "生产订单结案", targetType = "erp_mfg_production_order", type = OperateType.UPDATE)
    public Result<ProductionOrderView> close(@PathVariable Long id) {
        return Result.ok(productionOrderAppService.close(id));
    }

    /**
     * 取消生产订单。
     *
     * @param id 生产订单 ID
     * @return 生产订单视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:production:cancel")
    @AuditLog(operation = "取消生产订单", targetType = "erp_mfg_production_order", type = OperateType.UPDATE)
    public Result<ProductionOrderView> cancel(@PathVariable Long id) {
        return Result.ok(productionOrderAppService.cancel(id));
    }
}
