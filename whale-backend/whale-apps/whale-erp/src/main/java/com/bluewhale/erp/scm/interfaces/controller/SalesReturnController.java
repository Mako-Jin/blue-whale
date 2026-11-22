package com.bluewhale.erp.scm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.ErpUserContext;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.scm.application.command.SalesReturnCreateCommand;
import com.bluewhale.erp.scm.application.service.SalesReturnAppService;
import com.bluewhale.erp.scm.application.view.SalesReturnView;
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
 * 销售退货接口：查询、建单、过账、取消。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/scm/sales-return", version = "1")
@RequiredArgsConstructor
public class SalesReturnController {

    private final SalesReturnAppService salesReturnAppService;

    /**
     * 销售退货分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:sales-return:list")
    public Result<PageResult<SalesReturnView>> page(
            @ModelAttribute ScmQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(salesReturnAppService.page(query));
    }

    /**
     * 销售退货详情。
     *
     * @param id 主键
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:sales-return:list")
    public Result<SalesReturnView> detail(@PathVariable Long id) {
        return Result.ok(salesReturnAppService.detail(id));
    }

    /**
     * 新增销售退货单。
     *
     * @param command 创建命令
     * @return 视图
     */
    @PostMapping
    @RequirePermission("sys:erp:sales-return:add")
    @AuditLog(operation = "新增销售退货单", targetType = "erp_scm_sales_return", type = OperateType.CREATE)
    public Result<SalesReturnView> create(@Valid @RequestBody SalesReturnCreateCommand command) {
        return Result.ok(salesReturnAppService.create(command));
    }

    /**
     * 销售退货过账。
     *
     * @param id 主键
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/post")
    @RequirePermission("sys:erp:sales-return:post")
    @AuditLog(operation = "销售退货过账", targetType = "erp_scm_sales_return", type = OperateType.UPDATE)
    public Result<SalesReturnView> post(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(salesReturnAppService.post(id, ErpUserContext.userId(request)));
    }

    /**
     * 取消销售退货单。
     *
     * @param id 主键
     * @return 视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:sales-return:cancel")
    @AuditLog(operation = "取消销售退货单", targetType = "erp_scm_sales_return", type = OperateType.UPDATE)
    public Result<SalesReturnView> cancel(@PathVariable Long id) {
        return Result.ok(salesReturnAppService.cancel(id));
    }
}
