package com.bluewhale.crm.sales.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.sales.application.command.LeadActivityCreateCommand;
import com.bluewhale.crm.sales.application.command.LeadAssignCommand;
import com.bluewhale.crm.sales.application.command.LeadConvertCommand;
import com.bluewhale.crm.sales.application.command.LeadInvalidCommand;
import com.bluewhale.crm.sales.application.command.LeadSaveCommand;
import com.bluewhale.crm.sales.application.command.LeadStatusCommand;
import com.bluewhale.crm.sales.application.service.LeadAppService;
import com.bluewhale.crm.sales.application.view.LeadActivityView;
import com.bluewhale.crm.sales.application.view.LeadConvertResultView;
import com.bluewhale.crm.sales.application.view.LeadView;
import com.bluewhale.crm.sales.domain.model.LeadQuery;
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

import java.util.List;

/**
 * 线索接口（M2）。
 *
 * <p>数据范围在应用层强制；联系电话 / 邮箱按 {@code sys:crm:sensitive:view} 脱敏。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/crm/v{version}/lead", version = "1")
@RequiredArgsConstructor
public class LeadController {

    private final LeadAppService leadAppService;

    /**
     * 线索分页。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param source 来源
     * @param ownerId 负责人
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 线索分页
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.LEAD_LIST)
    public Result<PageResult<LeadView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        LeadQuery query = new LeadQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setSource(source);
        query.setOwnerId(ownerId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(leadAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 线索查重。
     *
     * @param phone 电话
     * @param email 邮箱
     * @return 疑似重复线索
     */
    @GetMapping("/duplicate-check")
    @RequirePermission(CrmPermissions.LEAD_CREATE)
    public Result<List<LeadView>> duplicateCheck(
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "email", required = false) String email) {
        return Result.ok(leadAppService.duplicateCheck(phone, email));
    }

    /**
     * 线索详情。
     *
     * @param id 线索 ID
     * @param request 当前请求
     * @return 线索视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.LEAD_LIST)
    public Result<LeadView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(leadAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建线索。
     *
     * @param command 新建命令
     * @param request 当前请求
     * @return 线索视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.LEAD_CREATE)
    @AuditLog(operation = "新建线索", type = OperateType.CREATE, targetType = "crm_lead", recordParams = false)
    public Result<LeadView> create(@Valid @RequestBody LeadSaveCommand command, HttpServletRequest request) {
        return Result.ok(leadAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 编辑线索。
     *
     * @param id 线索 ID
     * @param command 编辑命令
     * @param request 当前请求
     * @return 线索视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.LEAD_UPDATE)
    @AuditLog(operation = "编辑线索", type = OperateType.UPDATE, targetType = "crm_lead",
            targetId = "#id", recordParams = false)
    public Result<LeadView> update(@PathVariable("id") Long id, @Valid @RequestBody LeadSaveCommand command,
                                   HttpServletRequest request) {
        return Result.ok(leadAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 推进线索状态。
     *
     * @param id 线索 ID
     * @param command 状态命令
     * @param request 当前请求
     * @return 线索视图
     */
    @PutMapping("/{id}/status")
    @RequirePermission(CrmPermissions.LEAD_UPDATE)
    @AuditLog(operation = "推进线索状态", type = OperateType.UPDATE, targetType = "crm_lead", targetId = "#id")
    public Result<LeadView> advance(@PathVariable("id") Long id, @Valid @RequestBody LeadStatusCommand command,
                                    HttpServletRequest request) {
        return Result.ok(leadAppService.advance(id, command, CrmActorContext.from(request)));
    }

    /**
     * 分配线索负责人。
     *
     * @param id 线索 ID
     * @param command 分配命令
     * @param request 当前请求
     * @return 线索视图
     */
    @PostMapping("/{id}/assign")
    @RequirePermission(CrmPermissions.LEAD_ASSIGN)
    @AuditLog(operation = "分配线索", type = OperateType.UPDATE, targetType = "crm_lead", targetId = "#id")
    public Result<LeadView> assign(@PathVariable("id") Long id, @Valid @RequestBody LeadAssignCommand command,
                                   HttpServletRequest request) {
        return Result.ok(leadAppService.assign(id, command, CrmActorContext.from(request)));
    }

    /**
     * 标记线索无效。
     *
     * @param id 线索 ID
     * @param command 无效命令
     * @param request 当前请求
     * @return 线索视图
     */
    @PostMapping("/{id}/invalid")
    @RequirePermission(CrmPermissions.LEAD_INVALID)
    @AuditLog(operation = "标记线索无效", type = OperateType.UPDATE, targetType = "crm_lead", targetId = "#id")
    public Result<LeadView> invalidate(@PathVariable("id") Long id, @Valid @RequestBody LeadInvalidCommand command,
                                       HttpServletRequest request) {
        return Result.ok(leadAppService.invalidate(id, command, CrmActorContext.from(request)));
    }

    /**
     * 线索转化。
     *
     * @param id 线索 ID
     * @param command 转化命令
     * @param request 当前请求
     * @return 转化结果
     */
    @PostMapping("/{id}/convert")
    @RequirePermission(CrmPermissions.LEAD_CONVERT)
    @AuditLog(operation = "线索转化", type = OperateType.UPDATE, targetType = "crm_lead",
            targetId = "#id", recordParams = false)
    public Result<LeadConvertResultView> convert(@PathVariable("id") Long id,
                                                 @RequestBody(required = false) LeadConvertCommand command,
                                                 HttpServletRequest request) {
        LeadConvertCommand effective = command == null ? new LeadConvertCommand() : command;
        return Result.ok(leadAppService.convert(id, effective, CrmActorContext.from(request)));
    }

    /**
     * 线索活动分页。
     *
     * @param id 线索 ID
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 活动分页
     */
    @GetMapping("/{id}/activity")
    @RequirePermission(CrmPermissions.LEAD_LIST)
    public Result<PageResult<LeadActivityView>> activityPage(
            @PathVariable("id") Long id,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setCurrent(PageParams.current(current, pageNum));
        pageQuery.setSize(PageParams.size(size, pageSize));
        return Result.ok(leadAppService.activityPage(id, pageQuery, CrmActorContext.from(request)));
    }

    /**
     * 追加线索活动。
     *
     * @param id 线索 ID
     * @param command 活动命令
     * @param request 当前请求
     * @return 活动视图
     */
    @PostMapping("/{id}/activity")
    @RequirePermission(CrmPermissions.LEAD_UPDATE)
    @AuditLog(operation = "追加线索活动", type = OperateType.CREATE, targetType = "crm_lead",
            targetId = "#id", recordParams = false)
    public Result<LeadActivityView> addActivity(@PathVariable("id") Long id,
                                                @Valid @RequestBody LeadActivityCreateCommand command,
                                                HttpServletRequest request) {
        return Result.ok(leadAppService.addActivity(id, command, CrmActorContext.from(request)));
    }
}
