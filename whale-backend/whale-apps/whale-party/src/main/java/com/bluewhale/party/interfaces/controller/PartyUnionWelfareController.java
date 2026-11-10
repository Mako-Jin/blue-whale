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
import com.bluewhale.party.union.application.command.WelfareIssueCommand;
import com.bluewhale.party.union.application.command.WelfareSaveCommand;
import com.bluewhale.party.union.application.service.UnionWelfareAppService;
import com.bluewhale.party.union.application.view.UnionWelfareItemView;
import com.bluewhale.party.union.application.view.UnionWelfareView;
import com.bluewhale.party.union.domain.model.UnionWelfareQuery;
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
 * 节日福利接口（M-P3）。
 *
 * <p>发放与关闭是资金类动作，只走管理权限；签收与「我的福利」走园地浏览权限，
 * 签收的本人归属由服务层按当前用户会员 ID 校验，不依赖前端传参。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/party/union/welfare")
@RequiredArgsConstructor
public class PartyUnionWelfareController {

    private final UnionWelfareAppService unionWelfareAppService;

    /**
     * 批次分页。
     *
     * @param keyword 关键字（批次编号 / 名称）
     * @param year 福利年度
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(PartyPermissions.UNION_WELFARE_LIST)
    public Result<PageResult<UnionWelfareView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        UnionWelfareQuery query = new UnionWelfareQuery();
        query.setKeyword(keyword);
        query.setYear(year);
        query.setStatus(status);
        return Result.ok(unionWelfareAppService.page(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize),
                actor(request)));
    }

    /**
     * 批次详情。
     *
     * @param id 批次 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(PartyPermissions.UNION_WELFARE_LIST)
    public Result<UnionWelfareView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionWelfareAppService.detail(id, actor(request)));
    }

    /**
     * 新建批次（草稿）。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(PartyPermissions.UNION_WELFARE_MANAGE)
    @AuditLog(operation = "新建节日福利批次", type = OperateType.CREATE, targetType = "pty_union_welfare")
    public Result<UnionWelfareView> create(@Valid @RequestBody WelfareSaveCommand command,
                                           HttpServletRequest request) {
        return Result.ok(unionWelfareAppService.create(command, actor(request)));
    }

    /**
     * 编辑批次（仅草稿）。
     *
     * @param id 批次 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(PartyPermissions.UNION_WELFARE_MANAGE)
    @AuditLog(operation = "编辑节日福利批次", type = OperateType.UPDATE, targetType = "pty_union_welfare")
    public Result<UnionWelfareView> update(@PathVariable("id") Long id,
                                           @Valid @RequestBody WelfareSaveCommand command,
                                           HttpServletRequest request) {
        return Result.ok(unionWelfareAppService.update(id, command, actor(request)));
    }

    /**
     * 发放批次；名单为空表示全部在册会员。
     *
     * @param id 批次 ID
     * @param command 发放命令（可空）
     * @param request 当前请求
     * @return 视图（含本次新增条数）
     */
    @PostMapping("/{id}/issue")
    @RequirePermission(PartyPermissions.UNION_WELFARE_MANAGE)
    @AuditLog(operation = "发放节日福利", type = OperateType.UPDATE, targetType = "pty_union_welfare")
    public Result<UnionWelfareView> issue(@PathVariable("id") Long id,
                                          @RequestBody(required = false) WelfareIssueCommand command,
                                          HttpServletRequest request) {
        return Result.ok(unionWelfareAppService.issue(id, command, actor(request)));
    }

    /**
     * 批次明细分页（可按签收状态过滤）。
     *
     * @param id 批次 ID
     * @param signStatus 签收状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/{id}/items")
    @RequirePermission(PartyPermissions.UNION_WELFARE_LIST)
    public Result<PageResult<UnionWelfareItemView>> items(
            @PathVariable("id") Long id,
            @RequestParam(value = "signStatus", required = false) String signStatus,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(unionWelfareAppService.items(id, signStatus,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize),
                actor(request)));
    }

    /**
     * 签收明细（本人或工会管理员代签）。
     *
     * @param itemId 明细 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/welfare/item/{itemId}/sign")
    @RequirePermission(PartyPermissions.UNION_BROWSE)
    @AuditLog(operation = "签收节日福利", type = OperateType.UPDATE, targetType = "pty_union_welfare_item")
    public Result<UnionWelfareItemView> sign(@PathVariable("itemId") Long itemId, HttpServletRequest request) {
        return Result.ok(unionWelfareAppService.sign(itemId, actor(request)));
    }

    /**
     * 关闭批次（仅已发放）。
     *
     * @param id 批次 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/close")
    @RequirePermission(PartyPermissions.UNION_WELFARE_MANAGE)
    @AuditLog(operation = "关闭节日福利批次", type = OperateType.UPDATE, targetType = "pty_union_welfare")
    public Result<UnionWelfareView> close(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionWelfareAppService.close(id, actor(request)));
    }

    /**
     * 我的福利分页。
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
    public Result<PageResult<UnionWelfareItemView>> my(
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(unionWelfareAppService.my(
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
