package com.bluewhale.erp.scm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.ErpUserContext;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.scm.application.command.StockAdjustCommand;
import com.bluewhale.erp.scm.application.service.InventoryAppService;
import com.bluewhale.erp.scm.application.view.StockBalanceView;
import com.bluewhale.erp.scm.application.view.StockMovementView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存接口：余额查询、流水查询与库存调整。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/scm/stock", version = "1")
@RequiredArgsConstructor
public class StockController {

    private final InventoryAppService inventoryAppService;

    /**
     * 库存余额分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 余额分页
     */
    @GetMapping("/balance/page")
    @RequirePermission("sys:erp:stock:list")
    public Result<PageResult<StockBalanceView>> balancePage(
            @ModelAttribute ScmQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(inventoryAppService.balancePage(query));
    }

    /**
     * 库存流水分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 流水分页
     */
    @GetMapping("/movement/page")
    @RequirePermission("sys:erp:stock:list")
    public Result<PageResult<StockMovementView>> movementPage(
            @ModelAttribute ScmQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(inventoryAppService.movementPage(query));
    }

    /**
     * 库存调整（盘盈/盘亏）。
     *
     * @param command 调整命令
     * @param request 当前请求
     * @return 生成的流水
     */
    @PostMapping("/adjust")
    @RequirePermission("sys:erp:stock:adjust")
    @AuditLog(operation = "库存调整", targetType = "erp_scm_stock_movement", type = OperateType.UPDATE)
    public Result<StockMovementView> adjust(@Valid @RequestBody StockAdjustCommand command,
                                            HttpServletRequest request) {
        return Result.ok(inventoryAppService.adjust(command, ErpUserContext.userId(request)));
    }
}
