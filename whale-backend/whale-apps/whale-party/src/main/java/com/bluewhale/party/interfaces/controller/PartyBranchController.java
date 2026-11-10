package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.branch.application.command.BranchSaveCommand;
import com.bluewhale.party.branch.application.service.BranchAppService;
import com.bluewhale.party.branch.application.view.BranchTermDueView;
import com.bluewhale.party.branch.application.view.BranchTreeView;
import com.bluewhale.party.branch.application.view.BranchView;
import com.bluewhale.party.branch.domain.model.BranchQuery;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyPermissions;
import com.bluewhale.party.interfaces.support.PageParams;
import com.bluewhale.party.interfaces.support.PartyActorContext;
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
 * 支部台账接口（M-P1）。
 *
 * <p>支部编码不接受前端传值，由编号流水生成；因此没有「按编码新建」的入口，
 * 避免出现两套编码规则。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/party/branch")
@RequiredArgsConstructor
public class PartyBranchController {

    private final BranchAppService branchAppService;

    /**
     * 支部分页。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param parentId 上级支部 ID
     * @param orgCode 组织编码
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(PartyPermissions.BRANCH_LIST)
    public Result<PageResult<BranchView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "orgCode", required = false) String orgCode,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        BranchQuery query = new BranchQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setParentId(parentId);
        query.setOrgCode(orgCode);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(branchAppService.page(query, actor(request)));
    }

    /**
     * 支部树。
     *
     * @param request 当前请求
     * @return 树根列表
     */
    @GetMapping("/tree")
    @RequirePermission(PartyPermissions.BRANCH_LIST)
    public Result<List<BranchTreeView>> tree(HttpServletRequest request) {
        return Result.ok(branchAppService.tree(actor(request)));
    }

    /**
     * 换届到期支部（M-P4，落在看板查看权限下）。
     *
     * @param withinDays 提前天数
     * @param request 当前请求
     * @return 到期支部列表
     */
    @GetMapping("/term-due")
    @RequirePermission(PartyPermissions.METRIC_LIST)
    public Result<List<BranchTermDueView>> termDue(
            @RequestParam(value = "withinDays", required = false) Integer withinDays,
            HttpServletRequest request) {
        return Result.ok(branchAppService.termDue(withinDays, actor(request)));
    }

    /**
     * 支部详情。
     *
     * @param id 支部 ID
     * @param request 当前请求
     * @return 支部视图
     */
    @GetMapping("/{id}")
    @RequirePermission(PartyPermissions.BRANCH_LIST)
    public Result<BranchView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(branchAppService.detail(id, actor(request)));
    }

    /**
     * 新建支部。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 支部视图
     */
    @PostMapping
    @RequirePermission(PartyPermissions.BRANCH_MANAGE)
    @AuditLog(operation = "新建党支部", type = OperateType.CREATE, targetType = "pty_branch")
    public Result<BranchView> create(@Valid @RequestBody BranchSaveCommand command,
                                     HttpServletRequest request) {
        return Result.ok(branchAppService.create(command, actor(request)));
    }

    /**
     * 编辑支部。
     *
     * @param id 支部 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 支部视图
     */
    @PutMapping("/{id}")
    @RequirePermission(PartyPermissions.BRANCH_MANAGE)
    @AuditLog(operation = "编辑党支部", type = OperateType.UPDATE, targetType = "pty_branch")
    public Result<BranchView> update(@PathVariable("id") Long id,
                                     @Valid @RequestBody BranchSaveCommand command,
                                     HttpServletRequest request) {
        return Result.ok(branchAppService.update(id, command, actor(request)));
    }

    /**
     * 启用 / 停用支部。
     *
     * @param id 支部 ID
     * @param active 是否启用
     * @param request 当前请求
     * @return 支部视图
     */
    @PostMapping("/{id}/status")
    @RequirePermission(PartyPermissions.BRANCH_MANAGE)
    @AuditLog(operation = "变更支部状态", type = OperateType.UPDATE, targetType = "pty_branch")
    public Result<BranchView> changeStatus(@PathVariable("id") Long id,
                                           @RequestParam("active") boolean active,
                                           HttpServletRequest request) {
        return Result.ok(branchAppService.changeStatus(id, active, actor(request)));
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private PartyActor actor(HttpServletRequest request) {
        return PartyActorContext.from(request);
    }
}
