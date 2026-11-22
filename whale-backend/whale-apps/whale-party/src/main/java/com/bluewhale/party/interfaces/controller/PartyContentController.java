package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyPermissions;
import com.bluewhale.party.content.application.command.ContentRejectCommand;
import com.bluewhale.party.content.application.command.ContentSaveCommand;
import com.bluewhale.party.content.application.service.ContentAppService;
import com.bluewhale.party.content.application.view.ContentAuditView;
import com.bluewhale.party.content.application.view.ContentReadView;
import com.bluewhale.party.content.application.view.ContentView;
import com.bluewhale.party.content.domain.model.ContentPortalQuery;
import com.bluewhale.party.content.domain.model.ContentQuery;
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
 * 党建内容接口（M-P2，先审后发）。
 *
 * <p>管理接口看全部状态；园地接口只返回已发布且当前用户可见的内容。
 * 「已发布内容修改后退回待审」是服务端行为，接口不提供「直接保存并生效」的入口。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/party/v{version}/content", version = "1")
@RequiredArgsConstructor
public class PartyContentController {

    private final ContentAppService contentAppService;

    /**
     * 管理列表分页（含全部状态）。
     *
     * @param keyword 关键字
     * @param category 分类
     * @param status 状态
     * @param visibility 可见范围
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(PartyPermissions.CONTENT_LIST)
    public Result<PageResult<ContentView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "visibility", required = false) String visibility,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        ContentQuery query = new ContentQuery();
        query.setKeyword(keyword);
        query.setCategory(category);
        query.setStatus(status);
        query.setVisibility(visibility);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(contentAppService.page(query, actor(request)));
    }

    /**
     * 管理详情（未审内容仅作者与审核人可见）。
     *
     * @param id 内容 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(PartyPermissions.CONTENT_LIST)
    public Result<ContentView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.detail(id, actor(request)));
    }

    /**
     * 新建内容（草稿）。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(PartyPermissions.CONTENT_MANAGE)
    @AuditLog(operation = "新建党建内容", type = OperateType.CREATE, targetType = "pty_content")
    public Result<ContentView> create(@Valid @RequestBody ContentSaveCommand command,
                                      HttpServletRequest request) {
        return Result.ok(contentAppService.create(command, actor(request)));
    }

    /**
     * 编辑内容；已发布内容编辑后自动退回待审。
     *
     * @param id 内容 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(PartyPermissions.CONTENT_MANAGE)
    @AuditLog(operation = "编辑党建内容", type = OperateType.UPDATE, targetType = "pty_content")
    public Result<ContentView> update(@PathVariable("id") Long id,
                                      @Valid @RequestBody ContentSaveCommand command,
                                      HttpServletRequest request) {
        return Result.ok(contentAppService.update(id, command, actor(request)));
    }

    /**
     * 提交审核（草稿 → 待审）。
     *
     * @param id 内容 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/submit")
    @RequirePermission(PartyPermissions.CONTENT_MANAGE)
    @AuditLog(operation = "提交党建内容审核", type = OperateType.UPDATE, targetType = "pty_content")
    public Result<ContentView> submit(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.submit(id, actor(request)));
    }

    /**
     * 审核通过（待审 → 已发布）。
     *
     * @param id 内容 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/approve")
    @RequirePermission(PartyPermissions.CONTENT_AUDIT)
    @AuditLog(operation = "审核通过党建内容", type = OperateType.UPDATE, targetType = "pty_content")
    public Result<ContentView> approve(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.approve(id, actor(request)));
    }

    /**
     * 审核退回（待审 → 草稿），意见必填。
     *
     * @param id 内容 ID
     * @param command 退回命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/reject")
    @RequirePermission(PartyPermissions.CONTENT_AUDIT)
    @AuditLog(operation = "退回党建内容", type = OperateType.UPDATE, targetType = "pty_content_audit")
    public Result<ContentView> reject(@PathVariable("id") Long id,
                                      @Valid @RequestBody ContentRejectCommand command,
                                      HttpServletRequest request) {
        return Result.ok(contentAppService.reject(id, command, actor(request)));
    }

    /**
     * 下线（已发布 → 已下线，终态，保留阅读数）。
     *
     * @param id 内容 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/offline")
    @RequirePermission(PartyPermissions.CONTENT_AUDIT)
    @AuditLog(operation = "下线党建内容", type = OperateType.UPDATE, targetType = "pty_content")
    public Result<ContentView> offline(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.offline(id, actor(request)));
    }

    /**
     * 审核轨迹。
     *
     * @param id 内容 ID
     * @param request 当前请求
     * @return 轨迹列表
     */
    @GetMapping("/{id}/audits")
    @RequirePermission(PartyPermissions.CONTENT_LIST)
    public Result<List<ContentAuditView>> audits(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.audits(id, actor(request)));
    }

    /**
     * 党建园地阅读列表（仅已发布且当前用户可见）。
     *
     * @param keyword 关键字
     * @param category 分类
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/portal/page")
    @RequirePermission(PartyPermissions.BROWSE)
    public Result<PageResult<ContentView>> portalPage(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        ContentPortalQuery query = new ContentPortalQuery();
        query.setKeyword(keyword);
        query.setCategory(category);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(contentAppService.portalPage(query, actor(request)));
    }

    /**
     * 党建园地阅读详情（仅已发布且当前用户可见）。
     *
     * @param id 内容 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/portal/{id}")
    @RequirePermission(PartyPermissions.BROWSE)
    public Result<ContentView> portalDetail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.portalDetail(id, actor(request)));
    }

    /**
     * 记录阅读（同一人同一内容只计一次）。
     *
     * @param id 内容 ID
     * @param request 当前请求
     * @return 视图（含最新阅读人数）
     */
    @PostMapping("/{id}/read")
    @RequirePermission(PartyPermissions.BROWSE)
    @AuditLog(operation = "阅读党建内容", type = OperateType.QUERY, targetType = "pty_content_read")
    public Result<ContentView> read(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.read(id, actor(request)));
    }

    /**
     * 阅读记录（内容作者与审核人可查）。
     *
     * @param id 内容 ID
     * @param request 当前请求
     * @return 阅读记录列表
     */
    @GetMapping("/{id}/reads")
    @RequirePermission(PartyPermissions.CONTENT_LIST)
    public Result<List<ContentReadView>> reads(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.reads(id, actor(request)));
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
