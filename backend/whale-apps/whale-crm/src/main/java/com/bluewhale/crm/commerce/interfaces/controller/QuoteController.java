package com.bluewhale.crm.commerce.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.commerce.application.command.QuoteSaveCommand;
import com.bluewhale.crm.commerce.application.service.QuoteAppService;
import com.bluewhale.crm.commerce.application.view.QuoteView;
import com.bluewhale.crm.commerce.domain.model.QuoteQuery;
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
 * 报价接口（M2 增量）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/quote")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteAppService quoteAppService;

    /**
     * 报价分页。
     *
     * @param keyword 关键字
     * @param opportunityId 商机 ID
     * @param customerId 客户 ID
     * @param status 状态
     * @param ownerId 负责人
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 报价分页
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.QUOTE_LIST)
    public Result<PageResult<QuoteView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "opportunityId", required = false) Long opportunityId,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        QuoteQuery query = new QuoteQuery();
        query.setKeyword(keyword);
        query.setOpportunityId(opportunityId);
        query.setCustomerId(customerId);
        query.setStatus(status);
        query.setOwnerId(ownerId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(quoteAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 报价详情。
     *
     * @param id 报价 ID
     * @param request 当前请求
     * @return 报价视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.QUOTE_LIST)
    public Result<QuoteView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(quoteAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建报价。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 报价视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.QUOTE_CREATE)
    @AuditLog(operation = "新建报价", type = OperateType.CREATE, targetType = "crm_quote")
    public Result<QuoteView> create(@Valid @RequestBody QuoteSaveCommand command,
                                    HttpServletRequest request) {
        return Result.ok(quoteAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 编辑草稿报价。
     *
     * @param id 报价 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 报价视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.QUOTE_UPDATE)
    @AuditLog(operation = "编辑报价", type = OperateType.UPDATE, targetType = "crm_quote", targetId = "#id")
    public Result<QuoteView> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody QuoteSaveCommand command,
                                    HttpServletRequest request) {
        return Result.ok(quoteAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 提交审批。
     *
     * @param id 报价 ID
     * @param request 当前请求
     * @return 报价视图
     */
    @PostMapping("/{id}/submit")
    @RequirePermission(CrmPermissions.QUOTE_SUBMIT)
    @AuditLog(operation = "提交报价审批", type = OperateType.UPDATE, targetType = "crm_quote", targetId = "#id")
    public Result<QuoteView> submit(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(quoteAppService.submit(id, CrmActorContext.from(request)));
    }

    /**
     * 改价（生成新版本草稿）。
     *
     * @param id 源报价 ID
     * @param request 当前请求
     * @return 新版本报价视图
     */
    @PostMapping("/{id}/revise")
    @RequirePermission(CrmPermissions.QUOTE_REVISE)
    @AuditLog(operation = "报价改价新版本", type = OperateType.UPDATE, targetType = "crm_quote", targetId = "#id")
    public Result<QuoteView> revise(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(quoteAppService.revise(id, CrmActorContext.from(request)));
    }
}
