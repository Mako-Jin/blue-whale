package com.bluewhale.crm.customer.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.customer.application.command.FollowUpCreateCommand;
import com.bluewhale.crm.customer.application.service.CustomerFollowUpAppService;
import com.bluewhale.crm.customer.application.view.FollowUpView;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户跟进接口（append-only，不提供修改 / 删除）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/crm/v{version}/follow-up", version = "1")
@RequiredArgsConstructor
public class CustomerFollowUpController {

    private final CustomerFollowUpAppService followUpAppService;

    /**
     * 客户跟进分页。
     *
     * @param customerId 客户 ID
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 跟进记录分页
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.FOLLOW_UP_LIST)
    public Result<PageResult<FollowUpView>> page(
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setCurrent(PageParams.current(current, pageNum));
        pageQuery.setSize(PageParams.size(size, pageSize));
        return Result.ok(followUpAppService.page(customerId, pageQuery,
                CrmActorContext.from(request)));
    }

    /**
     * 新增跟进记录。
     *
     * @param command 新增命令
     * @param request 当前请求
     * @return 跟进视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.FOLLOW_UP_CREATE)
    @AuditLog(operation = "新增客户跟进", type = OperateType.CREATE,
            targetType = "crm_follow_up", targetId = "#command.customerId")
    public Result<FollowUpView> create(@Valid @RequestBody FollowUpCreateCommand command,
                                       HttpServletRequest request) {
        return Result.ok(followUpAppService.create(command, CrmActorContext.from(request)));
    }
}
