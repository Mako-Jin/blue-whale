package com.bluewhale.crm.service.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.service.application.command.SurveyResponseCommand;
import com.bluewhale.crm.service.application.command.SurveySendCommand;
import com.bluewhale.crm.service.application.service.SatisfactionAppService;
import com.bluewhale.crm.service.application.view.SatisfactionSurveyView;
import com.bluewhale.crm.service.application.view.SurveyStatsView;
import com.bluewhale.crm.service.domain.model.SurveyQuery;
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
 * 满意度问卷接口（令牌提交，匿名 H5 不在本批）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/crm/v{version}/satisfaction", version = "1")
@RequiredArgsConstructor
public class CrmSatisfactionController {

    /** 未知来源 IP 占位值 */
    private static final String UNKNOWN_IP = "unknown";

    private final SatisfactionAppService satisfactionAppService;

    /**
     * 分页查询问卷。
     *
     * @param ticketId 工单 ID
     * @param status 问卷状态
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.SURVEY_MANAGE)
    public Result<PageResult<SatisfactionSurveyView>> page(
            @RequestParam(value = "ticketId", required = false) Long ticketId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        SurveyQuery query = new SurveyQuery();
        query.setTicketId(ticketId);
        query.setStatus(status);
        query.setCurrent((int) PageParams.current(current, pageNum));
        query.setSize((int) PageParams.size(size, pageSize));
        return Result.ok(satisfactionAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 满意度统计。
     *
     * @param request 当前请求
     * @return 统计视图
     */
    @GetMapping("/stats")
    @RequirePermission(CrmPermissions.SURVEY_MANAGE)
    public Result<SurveyStatsView> stats(HttpServletRequest request) {
        return Result.ok(satisfactionAppService.stats(CrmActorContext.from(request)));
    }

    /**
     * 查看问卷详情（含答卷）。
     *
     * @param id 主键
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.SURVEY_MANAGE)
    public Result<SatisfactionSurveyView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(satisfactionAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 按工单生成或重发问卷。
     *
     * @param command 命令
     * @param request 当前请求
     * @return 视图（含一次性令牌）
     */
    @PostMapping("/send")
    @RequirePermission(CrmPermissions.SURVEY_MANAGE)
    @AuditLog(operation = "生成满意度问卷", type = OperateType.CREATE, targetType = "crm_satisfaction_survey")
    public Result<SatisfactionSurveyView> send(@Valid @RequestBody SurveySendCommand command,
                                               HttpServletRequest request) {
        return Result.ok(satisfactionAppService.send(command, CrmActorContext.from(request)));
    }

    /**
     * 按一次性令牌提交答卷。
     *
     * @param command 命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/response")
    @RequirePermission(CrmPermissions.SURVEY_MANAGE)
    @AuditLog(operation = "录入满意度答卷", type = OperateType.CREATE, targetType = "crm_satisfaction_response")
    public Result<SatisfactionSurveyView> response(@Valid @RequestBody SurveyResponseCommand command,
                                                   HttpServletRequest request) {
        return Result.ok(satisfactionAppService.submit(command, clientIp(request)));
    }

    /**
     * 作废问卷。
     *
     * @param id 主键
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/revoke")
    @RequirePermission(CrmPermissions.SURVEY_MANAGE)
    @AuditLog(operation = "作废满意度问卷", type = OperateType.UPDATE,
            targetType = "crm_satisfaction_survey", targetId = "#id")
    public Result<Void> revoke(@PathVariable("id") Long id, HttpServletRequest request) {
        satisfactionAppService.revoke(id, CrmActorContext.from(request));
        return Result.ok();
    }

    /**
     * 解析来源 IP（优先取代理转发链首段）。
     *
     * @param request 当前请求
     * @return 来源 IP
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? UNKNOWN_IP : remote;
    }
}
