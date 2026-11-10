package com.bluewhale.crm.marketing.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.marketing.application.command.PartnerCreateCommand;
import com.bluewhale.crm.marketing.application.command.PartnerReportCreateCommand;
import com.bluewhale.crm.marketing.application.command.PartnerReportResolveCommand;
import com.bluewhale.crm.marketing.application.command.PartnerUpdateCommand;
import com.bluewhale.crm.marketing.application.service.PartnerAppService;
import com.bluewhale.crm.marketing.application.view.PartnerReportView;
import com.bluewhale.crm.marketing.application.view.PartnerView;
import com.bluewhale.crm.marketing.domain.model.PartnerQuery;
import com.bluewhale.crm.marketing.domain.model.PartnerReportQuery;
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
 * 渠道伙伴与渠道报单接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/partner")
@RequiredArgsConstructor
public class CrmPartnerController {

    private final PartnerAppService partnerAppService;

    /**
     * 分页查询渠道伙伴。
     *
     * @param keyword 关键字
     * @param partnerType 伙伴类型
     * @param status 状态
     * @param region 区域
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.PARTNER_MANAGE)
    public Result<PageResult<PartnerView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "partnerType", required = false) String partnerType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        PartnerQuery query = new PartnerQuery();
        query.setKeyword(keyword);
        query.setPartnerType(partnerType);
        query.setStatus(status);
        query.setRegion(region);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(partnerAppService.pagePartners(query, CrmActorContext.from(request)));
    }

    /**
     * 查看渠道伙伴详情。
     *
     * @param id 伙伴 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.PARTNER_MANAGE)
    public Result<PartnerView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(partnerAppService.partnerDetail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建渠道伙伴。
     *
     * @param command 新建命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.PARTNER_MANAGE)
    @AuditLog(operation = "新建渠道伙伴", type = OperateType.CREATE, targetType = "crm_partner")
    public Result<PartnerView> create(@Valid @RequestBody PartnerCreateCommand command,
                                      HttpServletRequest request) {
        return Result.ok(partnerAppService.createPartner(command, CrmActorContext.from(request)));
    }

    /**
     * 编辑渠道伙伴。
     *
     * @param id 伙伴 ID
     * @param command 编辑命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.PARTNER_MANAGE)
    @AuditLog(operation = "编辑渠道伙伴", type = OperateType.UPDATE, targetType = "crm_partner", targetId = "#id")
    public Result<PartnerView> update(@PathVariable("id") Long id,
                                      @Valid @RequestBody PartnerUpdateCommand command,
                                      HttpServletRequest request) {
        return Result.ok(partnerAppService.updatePartner(id, command, CrmActorContext.from(request)));
    }

    /**
     * 分页查询渠道报单。
     *
     * @param keyword 关键字
     * @param partnerId 伙伴 ID
     * @param customerId 客户 ID
     * @param status 状态
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/report/page")
    @RequirePermission(CrmPermissions.PARTNER_REPORT)
    public Result<PageResult<PartnerReportView>> reportPage(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "partnerId", required = false) Long partnerId,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        PartnerReportQuery query = new PartnerReportQuery();
        query.setKeyword(keyword);
        query.setPartnerId(partnerId);
        query.setCustomerId(customerId);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(partnerAppService.pageReports(query, CrmActorContext.from(request)));
    }

    /**
     * 查看渠道报单详情。
     *
     * @param id 报单 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/report/{id}")
    @RequirePermission(CrmPermissions.PARTNER_REPORT)
    public Result<PartnerReportView> reportDetail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(partnerAppService.reportDetail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建渠道报单。
     *
     * @param command 新建命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/report")
    @RequirePermission(CrmPermissions.PARTNER_REPORT)
    @AuditLog(operation = "新建渠道报单", type = OperateType.CREATE, targetType = "crm_partner_report")
    public Result<PartnerReportView> createReport(@Valid @RequestBody PartnerReportCreateCommand command,
                                                  HttpServletRequest request) {
        return Result.ok(partnerAppService.createReport(command, CrmActorContext.from(request)));
    }

    /**
     * 裁决冲突报单。
     *
     * @param id 报单 ID
     * @param command 裁决命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/report/{id}/resolve")
    @RequirePermission(CrmPermissions.PARTNER_REPORT)
    @AuditLog(operation = "裁决渠道报单", type = OperateType.UPDATE, targetType = "crm_partner_report", targetId = "#id")
    public Result<PartnerReportView> resolveReport(@PathVariable("id") Long id,
                                                   @Valid @RequestBody PartnerReportResolveCommand command,
                                                   HttpServletRequest request) {
        return Result.ok(partnerAppService.resolveReport(id, command, CrmActorContext.from(request)));
    }
}
