package com.bluewhale.oa.culture.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.service.PollAppService;
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
 * 投票问卷接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/poll")
@RequiredArgsConstructor
public class PollController {

    private static final String TARGET_TYPE = "oa_poll";

    private final PollAppService pollAppService;

    /**
     * 投票分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:poll:list")
    public Result<PageResult<CultureViews.Poll>> page(CultureQuery query) {
        return Result.ok(pollAppService.page(query));
    }

    /**
     * 投票详情。
     *
     * @param id 投票ID
     * @param request 请求上下文
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:poll:list")
    public Result<CultureViews.PollDetail> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(pollAppService.detail(id, UserContext.requireUserId(request)));
    }

    /**
     * 新建投票。
     *
     * @param command 保存命令
     * @param request 请求上下文
     * @return 投票
     */
    @PostMapping
    @RequirePermission("sys:oa:poll:add")
    @AuditLog(operation = "新建投票", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CultureViews.Poll> create(@Valid @RequestBody CultureCommands.PollSave command,
                                            HttpServletRequest request) {
        return Result.ok(pollAppService.create(command,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 编辑投票。
     *
     * @param id 投票ID
     * @param command 保存命令
     * @return 投票
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:poll:add")
    @AuditLog(operation = "编辑投票", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CultureViews.Poll> update(@PathVariable Long id,
                                            @Valid @RequestBody CultureCommands.PollSave command) {
        return Result.ok(pollAppService.update(id, command));
    }

    /**
     * 删除投票。
     *
     * @param id 投票ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:poll:add")
    @AuditLog(operation = "删除投票", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> delete(@PathVariable Long id) {
        pollAppService.delete(id);
        return Result.ok();
    }

    /**
     * 发布投票。
     *
     * @param id 投票ID
     * @return 投票
     */
    @PutMapping("/{id}/publish")
    @RequirePermission("sys:oa:poll:add")
    @AuditLog(operation = "发布投票", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CultureViews.Poll> publish(@PathVariable Long id) {
        return Result.ok(pollAppService.publish(id));
    }

    /**
     * 关闭投票。
     *
     * @param id 投票ID
     * @return 投票
     */
    @PutMapping("/{id}/close")
    @RequirePermission("sys:oa:poll:add")
    @AuditLog(operation = "关闭投票", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CultureViews.Poll> close(@PathVariable Long id) {
        return Result.ok(pollAppService.close(id));
    }

    /**
     * 投票。
     *
     * @param id 投票ID
     * @param command 投票命令
     * @param request 请求上下文
     * @return 空结果
     */
    @PostMapping("/{id}/vote")
    @RequirePermission("sys:oa:poll:vote")
    @AuditLog(operation = "参与投票", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<Void> vote(@PathVariable Long id, @Valid @RequestBody CultureCommands.VoteCommand command,
                             HttpServletRequest request) {
        pollAppService.vote(id, command,
                UserContext.requireUserId(request), WebHeaders.username(request));
        return Result.ok();
    }
}
