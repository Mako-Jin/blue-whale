package com.bluewhale.crm.commerce.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.commerce.application.command.InvoiceIssueCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceRedFlushCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceSaveCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceSendCommand;
import com.bluewhale.crm.commerce.application.service.InvoiceAppService;
import com.bluewhale.crm.commerce.application.view.InvoiceView;
import com.bluewhale.crm.commerce.domain.model.InvoiceQuery;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
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

/**
 * 开票申请接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/invoice")
@RequiredArgsConstructor
public class CrmInvoiceController {

    private final InvoiceAppService invoiceAppService;

    /**
     * 分页查询开票申请。
     *
     * @param keyword 关键字
     * @param customerId 客户 ID
     * @param contractId 合同台账 ID
     * @param status 状态
     * @param requestType 申请类型
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.INVOICE_LIST)
    public Result<PageResult<InvoiceView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "contractId", required = false) Long contractId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "requestType", required = false) String requestType,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        InvoiceQuery query = new InvoiceQuery();
        query.setKeyword(keyword);
        query.setCustomerId(customerId);
        query.setContractId(contractId);
        query.setStatus(status);
        query.setRequestType(requestType);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(invoiceAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 查看开票申请详情。
     *
     * @param id 申请 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.INVOICE_LIST)
    public Result<InvoiceView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(invoiceAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建开票申请。
     *
     * @param command 新建命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.INVOICE_CREATE)
    @AuditLog(operation = "新建开票申请", type = OperateType.CREATE, targetType = "crm_invoice_request")
    public Result<InvoiceView> create(@Valid @RequestBody InvoiceSaveCommand command,
                                      HttpServletRequest request) {
        return Result.ok(invoiceAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 编辑草稿开票申请。
     *
     * @param id 申请 ID
     * @param command 编辑命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.INVOICE_CREATE)
    @AuditLog(operation = "编辑开票申请", type = OperateType.UPDATE, targetType = "crm_invoice_request",
            targetId = "#id")
    public Result<InvoiceView> update(@PathVariable("id") Long id,
                                      @Valid @RequestBody InvoiceSaveCommand command,
                                      HttpServletRequest request) {
        return Result.ok(invoiceAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 提交开票审批。
     *
     * @param id 申请 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/submit")
    @RequirePermission(CrmPermissions.INVOICE_SUBMIT)
    @AuditLog(operation = "提交开票审批", type = OperateType.UPDATE, targetType = "crm_invoice_request",
            targetId = "#id")
    public Result<InvoiceView> submit(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(invoiceAppService.submit(id, CrmActorContext.from(request)));
    }

    /**
     * 登记开票记录。
     *
     * @param id 申请 ID
     * @param command 开票命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/issue")
    @RequirePermission(CrmPermissions.INVOICE_ISSUE)
    @AuditLog(operation = "登记开票记录", type = OperateType.UPDATE, targetType = "crm_invoice_request",
            targetId = "#id")
    public Result<InvoiceView> issue(@PathVariable("id") Long id,
                                     @Valid @RequestBody InvoiceIssueCommand command,
                                     HttpServletRequest request) {
        return Result.ok(invoiceAppService.issue(id, command, CrmActorContext.from(request)));
    }

    /**
     * 登记发票寄送。
     *
     * @param id 申请 ID
     * @param command 寄送命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/send")
    @RequirePermission(CrmPermissions.INVOICE_SEND)
    @AuditLog(operation = "登记发票寄送", type = OperateType.UPDATE, targetType = "crm_invoice_request",
            targetId = "#id")
    public Result<InvoiceView> send(@PathVariable("id") Long id,
                                    @Valid @RequestBody InvoiceSendCommand command,
                                    HttpServletRequest request) {
        return Result.ok(invoiceAppService.send(id, command, CrmActorContext.from(request)));
    }

    /**
     * 发票红冲。
     *
     * @param id 原申请 ID
     * @param command 红冲命令
     * @param request 当前请求
     * @return 红冲单据视图
     */
    @PostMapping("/{id}/red-flush")
    @RequirePermission(CrmPermissions.INVOICE_RED_FLUSH)
    @AuditLog(operation = "发票红冲", type = OperateType.CREATE, targetType = "crm_invoice_request",
            targetId = "#id")
    public Result<InvoiceView> redFlush(@PathVariable("id") Long id,
                                        @Valid @RequestBody InvoiceRedFlushCommand command,
                                        HttpServletRequest request) {
        return Result.ok(invoiceAppService.redFlush(id, command, CrmActorContext.from(request)));
    }

    /**
     * 取消开票申请。
     *
     * @param id 申请 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(CrmPermissions.INVOICE_CREATE)
    @AuditLog(operation = "取消开票申请", type = OperateType.UPDATE, targetType = "crm_invoice_request",
            targetId = "#id")
    public Result<InvoiceView> cancel(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(invoiceAppService.cancel(id, CrmActorContext.from(request)));
    }
}
