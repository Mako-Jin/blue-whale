package com.bluewhale.crm.service.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.service.application.command.TicketAssignCommand;
import com.bluewhale.crm.service.application.command.TicketCreateCommand;
import com.bluewhale.crm.service.application.command.TicketHandleCommand;
import com.bluewhale.crm.service.application.service.TicketAppService;
import com.bluewhale.crm.service.application.view.TicketView;
import com.bluewhale.crm.service.domain.model.TicketQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 售后服务工单接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/ticket")
@RequiredArgsConstructor
public class CrmTicketController {

    private final TicketAppService ticketAppService;

    /**
     * 分页查询工单。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param priority 优先级
     * @param customerId 客户 ID
     * @param assigneeId 处理人 ID
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.TICKET_LIST)
    public Result<PageResult<TicketView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "assigneeId", required = false) Long assigneeId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        TicketQuery query = new TicketQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setPriority(priority);
        query.setCustomerId(customerId);
        query.setAssigneeId(assigneeId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(ticketAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 查看工单详情（含处理时间线）。
     *
     * @param id 工单 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.TICKET_LIST)
    public Result<TicketView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(ticketAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建工单。
     *
     * @param command 建单命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.TICKET_CREATE)
    @AuditLog(operation = "新建售后工单", type = OperateType.CREATE, targetType = "crm_ticket")
    public Result<TicketView> create(@Valid @RequestBody TicketCreateCommand command,
                                     HttpServletRequest request) {
        return Result.ok(ticketAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 分派处理人。
     *
     * @param id 工单 ID
     * @param command 分派命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/assign")
    @RequirePermission(CrmPermissions.TICKET_ASSIGN)
    @AuditLog(operation = "分派售后工单", type = OperateType.UPDATE, targetType = "crm_ticket", targetId = "#id")
    public Result<TicketView> assign(@PathVariable("id") Long id,
                                     @Valid @RequestBody TicketAssignCommand command,
                                     HttpServletRequest request) {
        return Result.ok(ticketAppService.assign(id, command, CrmActorContext.from(request)));
    }

    /**
     * 记录处理进展。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/process")
    @RequirePermission(CrmPermissions.TICKET_HANDLE)
    @AuditLog(operation = "处理售后工单", type = OperateType.UPDATE, targetType = "crm_ticket", targetId = "#id")
    public Result<TicketView> process(@PathVariable("id") Long id,
                                      @Valid @RequestBody TicketHandleCommand command,
                                      HttpServletRequest request) {
        return Result.ok(ticketAppService.process(id, command, CrmActorContext.from(request)));
    }

    /**
     * 追加备注。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/comment")
    @RequirePermission(CrmPermissions.TICKET_HANDLE)
    @AuditLog(operation = "添加工单备注", type = OperateType.UPDATE, targetType = "crm_ticket", targetId = "#id")
    public Result<TicketView> comment(@PathVariable("id") Long id,
                                      @Valid @RequestBody TicketHandleCommand command,
                                      HttpServletRequest request) {
        return Result.ok(ticketAppService.comment(id, command, CrmActorContext.from(request)));
    }

    /**
     * 暂停工单（等待客户）。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/pause")
    @RequirePermission(CrmPermissions.TICKET_HANDLE)
    @AuditLog(operation = "暂停售后工单", type = OperateType.UPDATE, targetType = "crm_ticket", targetId = "#id")
    public Result<TicketView> pause(@PathVariable("id") Long id,
                                    @Valid @RequestBody TicketHandleCommand command,
                                    HttpServletRequest request) {
        return Result.ok(ticketAppService.pause(id, command, CrmActorContext.from(request)));
    }

    /**
     * 恢复工单。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/resume")
    @RequirePermission(CrmPermissions.TICKET_HANDLE)
    @AuditLog(operation = "恢复售后工单", type = OperateType.UPDATE, targetType = "crm_ticket", targetId = "#id")
    public Result<TicketView> resume(@PathVariable("id") Long id,
                                     @Valid @RequestBody TicketHandleCommand command,
                                     HttpServletRequest request) {
        return Result.ok(ticketAppService.resume(id, command, CrmActorContext.from(request)));
    }

    /**
     * 升级优先级。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/escalate")
    @RequirePermission(CrmPermissions.TICKET_HANDLE)
    @AuditLog(operation = "升级售后工单", type = OperateType.UPDATE, targetType = "crm_ticket", targetId = "#id")
    public Result<TicketView> escalate(@PathVariable("id") Long id,
                                       @Valid @RequestBody TicketHandleCommand command,
                                       HttpServletRequest request) {
        return Result.ok(ticketAppService.escalate(id, command, CrmActorContext.from(request)));
    }

    /**
     * 标记已解决。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/resolve")
    @RequirePermission(CrmPermissions.TICKET_HANDLE)
    @AuditLog(operation = "解决售后工单", type = OperateType.UPDATE, targetType = "crm_ticket", targetId = "#id")
    public Result<TicketView> resolve(@PathVariable("id") Long id,
                                      @Valid @RequestBody TicketHandleCommand command,
                                      HttpServletRequest request) {
        return Result.ok(ticketAppService.resolve(id, command, CrmActorContext.from(request)));
    }

    /**
     * 关闭工单。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/close")
    @RequirePermission(CrmPermissions.TICKET_HANDLE)
    @AuditLog(operation = "关闭售后工单", type = OperateType.UPDATE, targetType = "crm_ticket", targetId = "#id")
    public Result<TicketView> close(@PathVariable("id") Long id,
                                    @Valid @RequestBody TicketHandleCommand command,
                                    HttpServletRequest request) {
        return Result.ok(ticketAppService.close(id, command, CrmActorContext.from(request)));
    }

    /**
     * 重开工单。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/reopen")
    @RequirePermission(CrmPermissions.TICKET_HANDLE)
    @AuditLog(operation = "重开售后工单", type = OperateType.UPDATE, targetType = "crm_ticket", targetId = "#id")
    public Result<TicketView> reopen(@PathVariable("id") Long id,
                                     @Valid @RequestBody TicketHandleCommand command,
                                     HttpServletRequest request) {
        return Result.ok(ticketAppService.reopen(id, command, CrmActorContext.from(request)));
    }

    /**
     * 取消工单。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(CrmPermissions.TICKET_HANDLE)
    @AuditLog(operation = "取消售后工单", type = OperateType.UPDATE, targetType = "crm_ticket", targetId = "#id")
    public Result<TicketView> cancel(@PathVariable("id") Long id,
                                     @Valid @RequestBody TicketHandleCommand command,
                                     HttpServletRequest request) {
        return Result.ok(ticketAppService.cancel(id, command, CrmActorContext.from(request)));
    }
}
