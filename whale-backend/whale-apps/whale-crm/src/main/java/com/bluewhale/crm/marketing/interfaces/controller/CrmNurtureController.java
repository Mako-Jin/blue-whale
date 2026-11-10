package com.bluewhale.crm.marketing.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.marketing.application.command.NurtureCreateCommand;
import com.bluewhale.crm.marketing.application.command.NurtureStatusCommand;
import com.bluewhale.crm.marketing.application.command.NurtureUpdateCommand;
import com.bluewhale.crm.marketing.application.service.NurtureAppService;
import com.bluewhale.crm.marketing.application.view.NurtureTaskView;
import com.bluewhale.crm.marketing.domain.model.NurtureQuery;
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
 * 客户培育任务接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/nurture")
@RequiredArgsConstructor
public class CrmNurtureController {

    private final NurtureAppService nurtureAppService;

    /**
     * 分页查询培育任务。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param customerId 客户 ID
     * @param assigneeId 执行人 ID
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.CAMPAIGN_LIST)
    public Result<PageResult<NurtureTaskView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "assigneeId", required = false) Long assigneeId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        NurtureQuery query = new NurtureQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setCustomerId(customerId);
        query.setAssigneeId(assigneeId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(nurtureAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 查看培育任务详情。
     *
     * @param id 任务 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.CAMPAIGN_LIST)
    public Result<NurtureTaskView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(nurtureAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建培育任务。
     *
     * @param command 新建命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.NURTURE_MANAGE)
    @AuditLog(operation = "新建培育任务", type = OperateType.CREATE, targetType = "crm_nurture_task")
    public Result<NurtureTaskView> create(@Valid @RequestBody NurtureCreateCommand command,
                                          HttpServletRequest request) {
        return Result.ok(nurtureAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 编辑培育任务。
     *
     * @param id 任务 ID
     * @param command 编辑命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.NURTURE_MANAGE)
    @AuditLog(operation = "编辑培育任务", type = OperateType.UPDATE, targetType = "crm_nurture_task", targetId = "#id")
    public Result<NurtureTaskView> update(@PathVariable("id") Long id,
                                          @Valid @RequestBody NurtureUpdateCommand command,
                                          HttpServletRequest request) {
        return Result.ok(nurtureAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 变更培育任务状态。
     *
     * @param id 任务 ID
     * @param command 状态命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/status")
    @RequirePermission(CrmPermissions.NURTURE_MANAGE)
    @AuditLog(operation = "变更培育任务状态", type = OperateType.UPDATE, targetType = "crm_nurture_task", targetId = "#id")
    public Result<NurtureTaskView> changeStatus(@PathVariable("id") Long id,
                                                @Valid @RequestBody NurtureStatusCommand command,
                                                HttpServletRequest request) {
        return Result.ok(nurtureAppService.changeStatus(id, command, CrmActorContext.from(request)));
    }
}
