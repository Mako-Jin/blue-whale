package com.bluewhale.oa.culture.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.service.CultureEventAppService;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业文化动态接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/culture/events", version = "1")
@RequiredArgsConstructor
public class CultureEventController {

    private static final String TARGET_TYPE = "oa_culture_event";

    private final CultureEventAppService eventAppService;

    /**
     * 文化动态分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:culture:list")
    public Result<PageResult<CultureViews.Event>> page(CultureQuery query) {
        return Result.ok(eventAppService.page(query));
    }

    /**
     * 文化动态详情。
     *
     * @param id 动态ID
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:culture:list")
    public Result<CultureViews.Event> detail(@PathVariable Long id) {
        return Result.ok(eventAppService.detail(id));
    }

    /**
     * 新建文化动态。
     *
     * @param command 保存命令
     * @return 动态
     */
    @PostMapping
    @RequirePermission("sys:oa:culture:add")
    @AuditLog(operation = "新建文化动态", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CultureViews.Event> create(@Valid @RequestBody CultureCommands.EventSave command) {
        return Result.ok(eventAppService.create(command));
    }

    /**
     * 编辑文化动态。
     *
     * @param id 动态ID
     * @param command 保存命令
     * @return 动态
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:culture:add")
    @AuditLog(operation = "编辑文化动态", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CultureViews.Event> update(@PathVariable Long id,
                                             @Valid @RequestBody CultureCommands.EventSave command) {
        return Result.ok(eventAppService.update(id, command));
    }

    /**
     * 删除文化动态。
     *
     * @param id 动态ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:culture:add")
    @AuditLog(operation = "删除文化动态", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> delete(@PathVariable Long id) {
        eventAppService.delete(id);
        return Result.ok();
    }

    /**
     * 发布文化动态。
     *
     * @param id 动态ID
     * @param request 请求上下文
     * @return 动态
     */
    @PutMapping("/{id}/publish")
    @RequirePermission("sys:oa:culture:add")
    @AuditLog(operation = "发布文化动态", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CultureViews.Event> publish(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(eventAppService.publish(id,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }
}
