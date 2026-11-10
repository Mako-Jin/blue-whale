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
import com.bluewhale.party.union.application.command.UnionMemberSaveCommand;
import com.bluewhale.party.union.application.command.UnionMemberWithdrawCommand;
import com.bluewhale.party.union.application.service.UnionMemberAppService;
import com.bluewhale.party.union.application.view.UnionMemberView;
import com.bluewhale.party.union.domain.model.UnionMemberQuery;
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

/**
 * 工会会员接口（M-P3）。
 *
 * <p>入会与退会是独立动作而不是「把状态字段改成 WITHDRAWN」：前者要取号建档，
 * 后者要写退会日期且不可回退，合成一个保存接口会让终态逻辑散落在前端。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/party/union/member")
@RequiredArgsConstructor
public class PartyUnionMemberController {

    private final UnionMemberAppService unionMemberAppService;

    /**
     * 会员台账分页。
     *
     * @param keyword 关键字（编号 / 工号 / 姓名）
     * @param status 会籍状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(PartyPermissions.UNION_MEMBER_LIST)
    public Result<PageResult<UnionMemberView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        UnionMemberQuery query = new UnionMemberQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        return Result.ok(unionMemberAppService.page(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize),
                actor(request)));
    }

    /**
     * 会员详情。
     *
     * @param id 会员 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(PartyPermissions.UNION_MEMBER_LIST)
    public Result<UnionMemberView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionMemberAppService.detail(id, actor(request)));
    }

    /**
     * 办理入会。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(PartyPermissions.UNION_MEMBER_MANAGE)
    @AuditLog(operation = "办理工会会员入会", type = OperateType.CREATE, targetType = "pty_union_member")
    public Result<UnionMemberView> create(@Valid @RequestBody UnionMemberSaveCommand command,
                                          HttpServletRequest request) {
        return Result.ok(unionMemberAppService.create(command, actor(request)));
    }

    /**
     * 编辑会员。
     *
     * @param id 会员 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(PartyPermissions.UNION_MEMBER_MANAGE)
    @AuditLog(operation = "编辑工会会员", type = OperateType.UPDATE, targetType = "pty_union_member")
    public Result<UnionMemberView> update(@PathVariable("id") Long id,
                                          @Valid @RequestBody UnionMemberSaveCommand command,
                                          HttpServletRequest request) {
        return Result.ok(unionMemberAppService.update(id, command, actor(request)));
    }

    /**
     * 办理退会。
     *
     * @param id 会员 ID
     * @param command 退会命令（可空，默认当天）
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/withdraw")
    @RequirePermission(PartyPermissions.UNION_MEMBER_MANAGE)
    @AuditLog(operation = "办理工会会员退会", type = OperateType.UPDATE, targetType = "pty_union_member")
    public Result<UnionMemberView> withdraw(@PathVariable("id") Long id,
                                           @RequestBody(required = false) UnionMemberWithdrawCommand command,
                                           HttpServletRequest request) {
        return Result.ok(unionMemberAppService.withdraw(id, command, actor(request)));
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
