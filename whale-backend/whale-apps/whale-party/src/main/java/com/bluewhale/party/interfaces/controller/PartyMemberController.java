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
import com.bluewhale.party.member.application.command.MemberSaveCommand;
import com.bluewhale.party.member.application.command.MemberTransferCommand;
import com.bluewhale.party.member.application.service.MemberAppService;
import com.bluewhale.party.member.application.view.MemberView;
import com.bluewhale.party.member.domain.model.PartyMemberQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 党员台账接口（M-P1）。
 *
 * <p>组织关系转出与预备党员转正各用独立动作接口，而不是打包进通用编辑：
 * 这两件事都带日期、都有前置条件、都会改变档案状态，混在编辑里会让
 * 「谁在什么时候办的手续」无从追溯。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/party/v{version}/member", version = "1")
@RequiredArgsConstructor
public class PartyMemberController {

    private final MemberAppService memberAppService;

    /**
     * 党员档案分页。
     *
     * @param keyword 关键字
     * @param branchId 支部 ID
     * @param politicalStatus 政治面貌
     * @param status 状态
     * @param probationaryPending 是否只看待转正
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(PartyPermissions.MEMBER_LIST)
    public Result<PageResult<MemberView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "politicalStatus", required = false) String politicalStatus,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "probationaryPending", required = false) Boolean probationaryPending,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        PartyMemberQuery query = new PartyMemberQuery();
        query.setKeyword(keyword);
        query.setBranchId(branchId);
        query.setPoliticalStatus(politicalStatus);
        query.setStatus(status);
        query.setProbationaryPending(probationaryPending);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(memberAppService.page(query, actor(request)));
    }

    /**
     * 党员档案详情。
     *
     * @param id 档案 ID
     * @param request 当前请求
     * @return 档案视图
     */
    @GetMapping("/{id}")
    @RequirePermission(PartyPermissions.MEMBER_LIST)
    public Result<MemberView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(memberAppService.detail(id, actor(request)));
    }

    /**
     * 党员建档。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 档案视图
     */
    @PostMapping
    @RequirePermission(PartyPermissions.MEMBER_MANAGE)
    @AuditLog(operation = "党员建档", type = OperateType.CREATE, targetType = "pty_member")
    public Result<MemberView> create(@Valid @RequestBody MemberSaveCommand command,
                                     HttpServletRequest request) {
        return Result.ok(memberAppService.create(command, actor(request)));
    }

    /**
     * 编辑党员档案。
     *
     * @param id 档案 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 档案视图
     */
    @PutMapping("/{id}")
    @RequirePermission(PartyPermissions.MEMBER_MANAGE)
    @AuditLog(operation = "编辑党员档案", type = OperateType.UPDATE, targetType = "pty_member")
    public Result<MemberView> update(@PathVariable("id") Long id,
                                     @Valid @RequestBody MemberSaveCommand command,
                                     HttpServletRequest request) {
        return Result.ok(memberAppService.update(id, command, actor(request)));
    }

    /**
     * 办理组织关系转出。
     *
     * @param id 档案 ID
     * @param command 转出命令
     * @param request 当前请求
     * @return 档案视图
     */
    @PostMapping("/{id}/transfer")
    @RequirePermission(PartyPermissions.MEMBER_MANAGE)
    @AuditLog(operation = "组织关系转出", type = OperateType.UPDATE, targetType = "pty_member")
    public Result<MemberView> transfer(@PathVariable("id") Long id,
                                       @RequestBody(required = false) MemberTransferCommand command,
                                       HttpServletRequest request) {
        return Result.ok(memberAppService.transfer(id, command, actor(request)));
    }

    /**
     * 办理预备党员转正。
     *
     * @param id 档案 ID
     * @param fullMemberDate 转正日期（空表示当天）
     * @param request 当前请求
     * @return 档案视图
     */
    @PostMapping("/{id}/promote")
    @RequirePermission(PartyPermissions.MEMBER_MANAGE)
    @AuditLog(operation = "预备党员转正", type = OperateType.UPDATE, targetType = "pty_member")
    public Result<MemberView> promote(
            @PathVariable("id") Long id,
            @RequestParam(value = "fullMemberDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fullMemberDate,
            HttpServletRequest request) {
        return Result.ok(memberAppService.promote(id, fullMemberDate, actor(request)));
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
