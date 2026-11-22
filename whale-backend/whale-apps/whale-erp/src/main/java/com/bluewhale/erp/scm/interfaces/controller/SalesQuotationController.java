package com.bluewhale.erp.scm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.scm.application.command.SalesQuotationCreateCommand;
import com.bluewhale.erp.scm.application.service.SalesQuotationAppService;
import com.bluewhale.erp.scm.application.view.SalesQuotationView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
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
 * 销售报价接口：查询、建单、客户接受、转单、取消。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/scm/sales-quotation", version = "1")
@RequiredArgsConstructor
public class SalesQuotationController {

    private final SalesQuotationAppService salesQuotationAppService;

    /**
     * 销售报价分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:sales-quotation:list")
    public Result<PageResult<SalesQuotationView>> page(
            @ModelAttribute ScmQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(salesQuotationAppService.page(query));
    }

    /**
     * 销售报价详情。
     *
     * @param id 主键
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:sales-quotation:detail")
    public Result<SalesQuotationView> detail(@PathVariable Long id) {
        return Result.ok(salesQuotationAppService.detail(id));
    }

    /**
     * 新增销售报价单。
     *
     * @param command 创建命令
     * @return 视图
     */
    @PostMapping
    @RequirePermission("sys:erp:sales-quotation:create")
    @AuditLog(operation = "新增销售报价单", targetType = "erp_scm_sales_quotation",
            type = OperateType.CREATE)
    public Result<SalesQuotationView> create(@Valid @RequestBody SalesQuotationCreateCommand command) {
        return Result.ok(salesQuotationAppService.create(command));
    }

    /**
     * 客户接受销售报价。
     *
     * @param id 主键
     * @return 视图
     */
    @PostMapping("/{id}/accept")
    @RequirePermission("sys:erp:sales-quotation:accept")
    @AuditLog(operation = "客户接受销售报价", targetType = "erp_scm_sales_quotation",
            type = OperateType.UPDATE)
    public Result<SalesQuotationView> accept(@PathVariable Long id) {
        return Result.ok(salesQuotationAppService.accept(id));
    }

    /**
     * 销售报价转销售订单。
     *
     * @param id 主键
     * @return 视图
     */
    @PostMapping("/{id}/convert")
    @RequirePermission("sys:erp:sales-quotation:convert")
    @AuditLog(operation = "销售报价转销售订单", targetType = "erp_scm_sales_quotation",
            type = OperateType.UPDATE)
    public Result<SalesQuotationView> convert(@PathVariable Long id) {
        return Result.ok(salesQuotationAppService.convert(id));
    }

    /**
     * 取消销售报价单。
     *
     * @param id 主键
     * @return 视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:sales-quotation:cancel")
    @AuditLog(operation = "取消销售报价单", targetType = "erp_scm_sales_quotation",
            type = OperateType.UPDATE)
    public Result<SalesQuotationView> cancel(@PathVariable Long id) {
        return Result.ok(salesQuotationAppService.cancel(id));
    }
}
