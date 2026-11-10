package com.bluewhale.crm.commerce.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.commerce.application.command.CollectionActionCommand;
import com.bluewhale.crm.commerce.application.command.CollectionPlanSaveCommand;
import com.bluewhale.crm.commerce.application.command.CollectionPromiseCommand;
import com.bluewhale.crm.commerce.application.service.CollectionAppService;
import com.bluewhale.crm.commerce.application.view.CollectionActionView;
import com.bluewhale.crm.commerce.application.view.CollectionPlanView;
import com.bluewhale.crm.commerce.domain.model.CollectionQuery;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
 * 回款计划接口（M3 / 7f-1）。
 *
 * <p>CRM 只维护计划、承诺与催收记录；实收、税额、记账归 ERP。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/collection")
@RequiredArgsConstructor
public class CrmCollectionController {

    private final CollectionAppService collectionAppService;

    /**
     * 回款计划分页。
     *
     * @param contractId 合同台账 ID
     * @param customerId 客户 ID
     * @param status 状态
     * @param overdueOnly 是否仅看逾期
     * @param plannedFrom 计划回款起始日
     * @param plannedTo 计划回款截止日
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 回款计划分页
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.COLLECTION_LIST)
    public Result<PageResult<CollectionPlanView>> page(
            @RequestParam(value = "contractId", required = false) Long contractId,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "overdueOnly", required = false) Boolean overdueOnly,
            @RequestParam(value = "plannedFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plannedFrom,
            @RequestParam(value = "plannedTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plannedTo,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CollectionQuery query = new CollectionQuery();
        query.setContractId(contractId);
        query.setCustomerId(customerId);
        query.setStatus(status);
        query.setOverdueOnly(overdueOnly);
        query.setPlannedFrom(plannedFrom);
        query.setPlannedTo(plannedTo);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(collectionAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 回款计划详情。
     *
     * @param id 计划 ID
     * @param request 当前请求
     * @return 计划视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.COLLECTION_LIST)
    public Result<CollectionPlanView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(collectionAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 按合同全量保存回款分期。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 保存后的分期
     */
    @PutMapping("/plan")
    @RequirePermission(CrmPermissions.COLLECTION_MANAGE)
    @AuditLog(operation = "保存回款计划", type = OperateType.UPDATE, targetType = "crm_collection_plan",
            targetId = "#command.contractId")
    public Result<List<CollectionPlanView>> savePlan(@Valid @RequestBody CollectionPlanSaveCommand command,
                                                     HttpServletRequest request) {
        return Result.ok(collectionAppService.savePlan(command, CrmActorContext.from(request)));
    }

    /**
     * 登记承诺还款。
     *
     * @param id 计划 ID
     * @param command 承诺命令
     * @param request 当前请求
     * @return 计划视图
     */
    @PostMapping("/{id}/promise")
    @RequirePermission(CrmPermissions.COLLECTION_ACTION)
    @AuditLog(operation = "登记承诺还款", type = OperateType.UPDATE, targetType = "crm_collection_plan",
            targetId = "#id")
    public Result<CollectionPlanView> promise(@PathVariable("id") Long id,
                                              @Valid @RequestBody CollectionPromiseCommand command,
                                              HttpServletRequest request) {
        return Result.ok(collectionAppService.promise(id, command, CrmActorContext.from(request)));
    }

    /**
     * 追加催收记录。
     *
     * @param id 计划 ID
     * @param command 催收命令
     * @param request 当前请求
     * @return 催收记录视图
     */
    @PostMapping("/{id}/action")
    @RequirePermission(CrmPermissions.COLLECTION_ACTION)
    @AuditLog(operation = "新增催收记录", type = OperateType.CREATE, targetType = "crm_collection_action",
            targetId = "#id")
    public Result<CollectionActionView> addAction(@PathVariable("id") Long id,
                                                  @Valid @RequestBody CollectionActionCommand command,
                                                  HttpServletRequest request) {
        return Result.ok(collectionAppService.addAction(id, command, CrmActorContext.from(request)));
    }

    /**
     * 查询催收记录。
     *
     * @param id 计划 ID
     * @param request 当前请求
     * @return 催收记录列表
     */
    @GetMapping("/{id}/actions")
    @RequirePermission(CrmPermissions.COLLECTION_LIST)
    public Result<List<CollectionActionView>> actions(@PathVariable("id") Long id,
                                                      HttpServletRequest request) {
        return Result.ok(collectionAppService.actions(id, CrmActorContext.from(request)));
    }
}
