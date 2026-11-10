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
import com.bluewhale.party.union.application.command.ActivitySaveCommand;
import com.bluewhale.party.union.application.service.UnionActivityAppService;
import com.bluewhale.party.union.application.view.UnionActivityView;
import com.bluewhale.party.union.application.view.UnionSignupView;
import com.bluewhale.party.union.domain.model.UnionActivityQuery;
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
 * 工会活动接口（M-P3）。
 *
 * <p>管理端与园地分开：管理端能看全部状态，园地只暴露已发布活动；
 * 报名 / 取消归属当前用户，服务层按「活动 + 在册会员」定位记录，不信任前端会员 ID。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/party/union/activity")
@RequiredArgsConstructor
public class PartyUnionActivityController {

    private final UnionActivityAppService unionActivityAppService;

    /**
     * 管理端活动分页。
     *
     * @param keyword 关键字（编号 / 名称）
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(PartyPermissions.UNION_ACTIVITY_LIST)
    public Result<PageResult<UnionActivityView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        UnionActivityQuery query = new UnionActivityQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        return Result.ok(unionActivityAppService.page(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize),
                actor(request)));
    }

    /**
     * 管理端活动详情。
     *
     * @param id 活动 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(PartyPermissions.UNION_ACTIVITY_LIST)
    public Result<UnionActivityView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionActivityAppService.detail(id, actor(request)));
    }

    /**
     * 新建活动（草稿）。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(PartyPermissions.UNION_ACTIVITY_MANAGE)
    @AuditLog(operation = "新建工会活动", type = OperateType.CREATE, targetType = "pty_union_activity")
    public Result<UnionActivityView> create(@Valid @RequestBody ActivitySaveCommand command,
                                            HttpServletRequest request) {
        return Result.ok(unionActivityAppService.create(command, actor(request)));
    }

    /**
     * 编辑活动（仅草稿）。
     *
     * @param id 活动 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(PartyPermissions.UNION_ACTIVITY_MANAGE)
    @AuditLog(operation = "编辑工会活动", type = OperateType.UPDATE, targetType = "pty_union_activity")
    public Result<UnionActivityView> update(@PathVariable("id") Long id,
                                            @Valid @RequestBody ActivitySaveCommand command,
                                            HttpServletRequest request) {
        return Result.ok(unionActivityAppService.update(id, command, actor(request)));
    }

    /**
     * 发布活动。
     *
     * @param id 活动 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/publish")
    @RequirePermission(PartyPermissions.UNION_ACTIVITY_MANAGE)
    @AuditLog(operation = "发布工会活动", type = OperateType.UPDATE, targetType = "pty_union_activity")
    public Result<UnionActivityView> publish(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionActivityAppService.publish(id, actor(request)));
    }

    /**
     * 结束活动。
     *
     * @param id 活动 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/close")
    @RequirePermission(PartyPermissions.UNION_ACTIVITY_MANAGE)
    @AuditLog(operation = "结束工会活动", type = OperateType.UPDATE, targetType = "pty_union_activity")
    public Result<UnionActivityView> close(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionActivityAppService.close(id, actor(request)));
    }

    /**
     * 取消活动。
     *
     * @param id 活动 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(PartyPermissions.UNION_ACTIVITY_MANAGE)
    @AuditLog(operation = "取消工会活动", type = OperateType.UPDATE, targetType = "pty_union_activity")
    public Result<UnionActivityView> cancel(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionActivityAppService.cancel(id, actor(request)));
    }

    /**
     * 活动报名名单分页。
     *
     * @param id 活动 ID
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/{id}/signups")
    @RequirePermission(PartyPermissions.UNION_ACTIVITY_LIST)
    public Result<PageResult<UnionSignupView>> signups(
            @PathVariable("id") Long id,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(unionActivityAppService.signups(id,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize),
                actor(request)));
    }

    /**
     * 园地活动分页（仅已发布）。
     *
     * @param keyword 关键字
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/portal/page")
    @RequirePermission(PartyPermissions.UNION_BROWSE)
    public Result<PageResult<UnionActivityView>> portalPage(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        UnionActivityQuery query = new UnionActivityQuery();
        query.setKeyword(keyword);
        return Result.ok(unionActivityAppService.portalPage(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize),
                actor(request)));
    }

    /**
     * 园地活动详情。
     *
     * @param id 活动 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/portal/{id}")
    @RequirePermission(PartyPermissions.UNION_BROWSE)
    public Result<UnionActivityView> portalDetail(@PathVariable("id") Long id,
                                                  HttpServletRequest request) {
        return Result.ok(unionActivityAppService.portalDetail(id, actor(request)));
    }

    /**
     * 报名。
     *
     * @param id 活动 ID
     * @param request 当前请求
     * @return 报名视图
     */
    @PostMapping("/portal/{id}/signup")
    @RequirePermission(PartyPermissions.UNION_BROWSE)
    @AuditLog(operation = "报名工会活动", type = OperateType.CREATE, targetType = "pty_union_signup")
    public Result<UnionSignupView> signup(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionActivityAppService.signup(id, actor(request)));
    }

    /**
     * 取消报名。
     *
     * @param id 活动 ID
     * @param request 当前请求
     * @return 报名视图
     */
    @PostMapping("/portal/{id}/cancel")
    @RequirePermission(PartyPermissions.UNION_BROWSE)
    @AuditLog(operation = "取消工会活动报名", type = OperateType.UPDATE, targetType = "pty_union_signup")
    public Result<UnionSignupView> cancelSignup(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionActivityAppService.cancelSignup(id, actor(request)));
    }

    /**
     * 我的报名分页。
     *
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/my")
    @RequirePermission(PartyPermissions.UNION_BROWSE)
    public Result<PageResult<UnionSignupView>> my(
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(unionActivityAppService.my(
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize),
                actor(request)));
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
