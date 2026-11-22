package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyPermissions;
import com.bluewhale.party.interfaces.support.PageParams;
import com.bluewhale.party.interfaces.support.PartyActorContext;
import com.bluewhale.party.union.application.command.ProposalFeedbackCommand;
import com.bluewhale.party.union.application.command.ProposalHandleCommand;
import com.bluewhale.party.union.application.command.ProposalRateCommand;
import com.bluewhale.party.union.application.command.ProposalSaveCommand;
import com.bluewhale.party.union.application.service.UnionProposalAppService;
import com.bluewhale.party.union.application.view.UnionProposalView;
import com.bluewhale.party.union.domain.model.UnionProposalQuery;
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
 * 职代会提案接口（M-P3）。
 *
 * <p>提案提交与评价属于提交人本人的园地动作，走浏览权限；立案、办理、反馈是
 * 官方办理链路，走管理权限。园地列表在服务层强制只看本人，前端无需也不能传他人 ID。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/party/v{version}/union/proposal", version = "1")
@RequiredArgsConstructor
public class PartyUnionProposalController {

    private final UnionProposalAppService unionProposalAppService;

    /**
     * 管理端提案分页。
     *
     * @param keyword 关键字（编号 / 标题）
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(PartyPermissions.UNION_PROPOSAL_LIST)
    public Result<PageResult<UnionProposalView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        UnionProposalQuery query = new UnionProposalQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        return Result.ok(unionProposalAppService.page(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize),
                actor(request)));
    }

    /**
     * 管理端提案详情。
     *
     * @param id 提案 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(PartyPermissions.UNION_PROPOSAL_LIST)
    public Result<UnionProposalView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionProposalAppService.detail(id, actor(request)));
    }

    /**
     * 立案（可同时指定办理人）。
     *
     * @param id 提案 ID
     * @param command 办理人（可空）
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/file")
    @RequirePermission(PartyPermissions.UNION_PROPOSAL_MANAGE)
    @AuditLog(operation = "职代会提案立案", type = OperateType.UPDATE, targetType = "pty_union_proposal")
    public Result<UnionProposalView> file(@PathVariable("id") Long id,
                                          @RequestBody(required = false) ProposalHandleCommand command,
                                          HttpServletRequest request) {
        return Result.ok(unionProposalAppService.file(id, command, actor(request)));
    }

    /**
     * 办理。
     *
     * @param id 提案 ID
     * @param command 办理命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/handle")
    @RequirePermission(PartyPermissions.UNION_PROPOSAL_MANAGE)
    @AuditLog(operation = "职代会提案办理", type = OperateType.UPDATE, targetType = "pty_union_proposal")
    public Result<UnionProposalView> handle(@PathVariable("id") Long id,
                                            @Valid @RequestBody ProposalHandleCommand command,
                                            HttpServletRequest request) {
        return Result.ok(unionProposalAppService.handle(id, command, actor(request)));
    }

    /**
     * 反馈。
     *
     * @param id 提案 ID
     * @param command 反馈命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/feedback")
    @RequirePermission(PartyPermissions.UNION_PROPOSAL_MANAGE)
    @AuditLog(operation = "职代会提案反馈", type = OperateType.UPDATE, targetType = "pty_union_proposal")
    public Result<UnionProposalView> feedback(@PathVariable("id") Long id,
                                              @Valid @RequestBody ProposalFeedbackCommand command,
                                              HttpServletRequest request) {
        return Result.ok(unionProposalAppService.feedback(id, command, actor(request)));
    }

    /**
     * 园地提交提案。
     *
     * @param command 提交命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(PartyPermissions.UNION_BROWSE)
    @AuditLog(operation = "提交职代会提案", type = OperateType.CREATE, targetType = "pty_union_proposal")
    public Result<UnionProposalView> create(@Valid @RequestBody ProposalSaveCommand command,
                                            HttpServletRequest request) {
        return Result.ok(unionProposalAppService.create(command, actor(request)));
    }

    /**
     * 园地我的提案分页。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/portal/page")
    @RequirePermission(PartyPermissions.UNION_BROWSE)
    public Result<PageResult<UnionProposalView>> portalPage(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        UnionProposalQuery query = new UnionProposalQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        return Result.ok(unionProposalAppService.portalPage(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize),
                actor(request)));
    }

    /**
     * 园地提案详情。
     *
     * @param id 提案 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/portal/{id}")
    @RequirePermission(PartyPermissions.UNION_BROWSE)
    public Result<UnionProposalView> portalDetail(@PathVariable("id") Long id,
                                                  HttpServletRequest request) {
        return Result.ok(unionProposalAppService.portalDetail(id, actor(request)));
    }

    /**
     * 满意度评价（仅提交人本人）。
     *
     * @param id 提案 ID
     * @param command 评价命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/rate")
    @RequirePermission(PartyPermissions.UNION_BROWSE)
    @AuditLog(operation = "评价职代会提案", type = OperateType.UPDATE, targetType = "pty_union_proposal")
    public Result<UnionProposalView> rate(@PathVariable("id") Long id,
                                          @Valid @RequestBody ProposalRateCommand command,
                                          HttpServletRequest request) {
        return Result.ok(unionProposalAppService.rate(id, command, actor(request)));
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
