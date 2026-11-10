package com.bluewhale.erp.scm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.ErpUserContext;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.scm.application.command.StocktakeCreateCommand;
import com.bluewhale.erp.scm.application.service.StocktakeAppService;
import com.bluewhale.erp.scm.application.view.StocktakeView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
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
 * 库存盘点接口：查询、建档、过账、取消。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/scm/stocktake")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeAppService stocktakeAppService;

    /**
     * 盘点单分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 盘点单分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:stocktake:list")
    public Result<PageResult<StocktakeView>> page(
            @ModelAttribute ScmQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(stocktakeAppService.page(query));
    }

    /**
     * 盘点单详情。
     *
     * @param id 盘点单 ID
     * @return 盘点单视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:stocktake:list")
    public Result<StocktakeView> detail(@PathVariable Long id) {
        return Result.ok(stocktakeAppService.detail(id));
    }

    /**
     * 新增盘点单。
     *
     * @param command 创建命令
     * @return 盘点单视图
     */
    @PostMapping
    @RequirePermission("sys:erp:stocktake:add")
    @AuditLog(operation = "新增库存盘点单", targetType = "erp_scm_stocktake", type = OperateType.CREATE)
    public Result<StocktakeView> create(@Valid @RequestBody StocktakeCreateCommand command) {
        return Result.ok(stocktakeAppService.create(command));
    }

    /**
     * 盘点过账（生成差异流水与凭证）。
     *
     * @param id 盘点单 ID
     * @param request 当前请求
     * @return 盘点单视图
     */
    @PostMapping("/{id}/post")
    @RequirePermission("sys:erp:stocktake:post")
    @AuditLog(operation = "库存盘点过账", targetType = "erp_scm_stocktake", type = OperateType.UPDATE)
    public Result<StocktakeView> post(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(stocktakeAppService.post(id, ErpUserContext.userId(request)));
    }

    /**
     * 取消盘点单。
     *
     * @param id 盘点单 ID
     * @return 盘点单视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:stocktake:cancel")
    @AuditLog(operation = "取消库存盘点单", targetType = "erp_scm_stocktake", type = OperateType.UPDATE)
    public Result<StocktakeView> cancel(@PathVariable Long id) {
        return Result.ok(stocktakeAppService.cancel(id));
    }
}
