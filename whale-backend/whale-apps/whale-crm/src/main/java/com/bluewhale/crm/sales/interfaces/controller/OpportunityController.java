package com.bluewhale.crm.sales.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.sales.application.command.OpportunitySaveCommand;
import com.bluewhale.crm.sales.application.command.OpportunityStageCommand;
import com.bluewhale.crm.sales.application.service.OpportunityAppService;
import com.bluewhale.crm.sales.application.view.FunnelView;
import com.bluewhale.crm.sales.application.view.OpportunityStageHistoryView;
import com.bluewhale.crm.sales.application.view.OpportunityView;
import com.bluewhale.crm.sales.domain.model.OpportunityQuery;
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

import java.time.LocalDate;
import java.util.List;

/**
 * 商机接口（M2）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/opportunity")
@RequiredArgsConstructor
public class OpportunityController {

    private final OpportunityAppService opportunityAppService;

    /**
     * 商机分页。
     *
     * @param keyword 关键字
     * @param customerId 客户 ID
     * @param stage 阶段
     * @param ownerId 负责人
     * @param expectedCloseFrom 预计关闭日期起
     * @param expectedCloseTo 预计关闭日期止
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 商机分页
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.OPPORTUNITY_LIST)
    public Result<PageResult<OpportunityView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "stage", required = false) String stage,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "expectedCloseFrom", required = false) LocalDate expectedCloseFrom,
            @RequestParam(value = "expectedCloseTo", required = false) LocalDate expectedCloseTo,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        OpportunityQuery query = new OpportunityQuery();
        query.setKeyword(keyword);
        query.setCustomerId(customerId);
        query.setStage(stage);
        query.setOwnerId(ownerId);
        query.setExpectedCloseFrom(expectedCloseFrom);
        query.setExpectedCloseTo(expectedCloseTo);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(opportunityAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 销售漏斗。
     *
     * @param ownerId 负责人
     * @param expectedCloseFrom 预计关闭日期起
     * @param expectedCloseTo 预计关闭日期止
     * @param request 当前请求
     * @return 漏斗视图
     */
    @GetMapping("/funnel")
    @RequirePermission(CrmPermissions.OPPORTUNITY_LIST)
    public Result<FunnelView> funnel(
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "expectedCloseFrom", required = false) LocalDate expectedCloseFrom,
            @RequestParam(value = "expectedCloseTo", required = false) LocalDate expectedCloseTo,
            HttpServletRequest request) {
        OpportunityQuery query = new OpportunityQuery();
        query.setOwnerId(ownerId);
        query.setExpectedCloseFrom(expectedCloseFrom);
        query.setExpectedCloseTo(expectedCloseTo);
        return Result.ok(opportunityAppService.funnel(query, CrmActorContext.from(request)));
    }

    /**
     * 商机详情。
     *
     * @param id 商机 ID
     * @param request 当前请求
     * @return 商机视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.OPPORTUNITY_LIST)
    public Result<OpportunityView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(opportunityAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 商机阶段历史。
     *
     * @param id 商机 ID
     * @param request 当前请求
     * @return 阶段历史
     */
    @GetMapping("/{id}/history")
    @RequirePermission(CrmPermissions.OPPORTUNITY_LIST)
    public Result<List<OpportunityStageHistoryView>> history(@PathVariable("id") Long id,
                                                             HttpServletRequest request) {
        return Result.ok(opportunityAppService.history(id, CrmActorContext.from(request)));
    }

    /**
     * 新建商机。
     *
     * @param command 新建命令
     * @param request 当前请求
     * @return 商机视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.OPPORTUNITY_CREATE)
    @AuditLog(operation = "新建商机", type = OperateType.CREATE, targetType = "crm_opportunity")
    public Result<OpportunityView> create(@Valid @RequestBody OpportunitySaveCommand command,
                                          HttpServletRequest request) {
        return Result.ok(opportunityAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 编辑商机。
     *
     * @param id 商机 ID
     * @param command 编辑命令
     * @param request 当前请求
     * @return 商机视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.OPPORTUNITY_UPDATE)
    @AuditLog(operation = "编辑商机", type = OperateType.UPDATE, targetType = "crm_opportunity", targetId = "#id")
    public Result<OpportunityView> update(@PathVariable("id") Long id,
                                          @Valid @RequestBody OpportunitySaveCommand command,
                                          HttpServletRequest request) {
        return Result.ok(opportunityAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 商机阶段流转。
     *
     * @param id 商机 ID
     * @param command 流转命令
     * @param request 当前请求
     * @return 商机视图
     */
    @PostMapping("/{id}/stage")
    @RequirePermission(CrmPermissions.OPPORTUNITY_STAGE)
    @AuditLog(operation = "商机阶段流转", type = OperateType.UPDATE, targetType = "crm_opportunity", targetId = "#id")
    public Result<OpportunityView> changeStage(@PathVariable("id") Long id,
                                               @Valid @RequestBody OpportunityStageCommand command,
                                               HttpServletRequest request) {
        return Result.ok(opportunityAppService.changeStage(id, command, CrmActorContext.from(request)));
    }
}
