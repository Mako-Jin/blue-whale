package com.bluewhale.crm.service.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.service.application.command.ServiceCalendarCommand;
import com.bluewhale.crm.service.application.command.SlaPoliciesCommand;
import com.bluewhale.crm.service.application.service.ServiceConfigAppService;
import com.bluewhale.crm.service.application.view.ServiceCalendarView;
import com.bluewhale.crm.service.application.view.SlaPolicyView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 售后服务配置接口（SLA 策略与工作日历）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/crm/v{version}/service", version = "1")
@RequiredArgsConstructor
public class CrmServiceConfigController {

    private final ServiceConfigAppService serviceConfigAppService;

    /**
     * 查询 SLA 策略。
     *
     * @param request 当前请求
     * @return 策略列表
     */
    @GetMapping("/sla")
    @RequirePermission(CrmPermissions.SERVICE_SLA_MANAGE)
    public Result<List<SlaPolicyView>> listPolicies(HttpServletRequest request) {
        return Result.ok(serviceConfigAppService.listPolicies(CrmActorContext.from(request)));
    }

    /**
     * 保存 SLA 策略。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 最新策略列表
     */
    @PutMapping("/sla")
    @RequirePermission(CrmPermissions.SERVICE_SLA_MANAGE)
    @AuditLog(operation = "保存 SLA 策略", type = OperateType.UPDATE, targetType = "crm_ticket_sla")
    public Result<List<SlaPolicyView>> savePolicies(@Valid @RequestBody SlaPoliciesCommand command,
                                                    HttpServletRequest request) {
        return Result.ok(serviceConfigAppService.savePolicies(command, CrmActorContext.from(request)));
    }

    /**
     * 查询工作日历。
     *
     * @param request 当前请求
     * @return 日历视图
     */
    @GetMapping("/calendar")
    @RequirePermission(CrmPermissions.SERVICE_SLA_MANAGE)
    public Result<ServiceCalendarView> getCalendar(HttpServletRequest request) {
        return Result.ok(serviceConfigAppService.getCalendar(CrmActorContext.from(request)));
    }

    /**
     * 保存工作日历。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 日历视图
     */
    @PutMapping("/calendar")
    @RequirePermission(CrmPermissions.SERVICE_SLA_MANAGE)
    @AuditLog(operation = "保存工作日历", type = OperateType.UPDATE, targetType = "crm_service_calendar")
    public Result<ServiceCalendarView> saveCalendar(@Valid @RequestBody ServiceCalendarCommand command,
                                                    HttpServletRequest request) {
        return Result.ok(serviceConfigAppService.saveCalendar(command, CrmActorContext.from(request)));
    }
}
