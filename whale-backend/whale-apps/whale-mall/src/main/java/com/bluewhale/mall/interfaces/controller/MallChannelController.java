package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.channel.application.command.ChannelSaveCommand;
import com.bluewhale.mall.channel.application.service.ChannelAppService;
import com.bluewhale.mall.channel.application.view.ChannelView;
import com.bluewhale.mall.channel.domain.model.ChannelQuery;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import com.bluewhale.mall.interfaces.support.PageParams;
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
 * 商城渠道接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mall/v{version}/channel", version = "1")
@RequiredArgsConstructor
public class MallChannelController {

    /** 单页上限 */
    private static final int PAGE_LIMIT = 100;

    private final ChannelAppService channelAppService;

    /**
     * 渠道分页。
     *
     * @param keyword 关键字
     * @param mode 形态
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(MallPermissions.CHANNEL_LIST)
    public Result<PageResult<ChannelView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        ChannelQuery query = new ChannelQuery();
        query.setKeyword(keyword);
        query.setMode(mode);
        query.setStatus(status);
        return Result.ok(channelAppService.page(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize, PAGE_LIMIT),
                MallActorContext.from(request)));
    }

    /**
     * 新建渠道。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 渠道视图
     */
    @PostMapping
    @RequirePermission(MallPermissions.CHANNEL_MANAGE)
    @AuditLog(operation = "新建商城渠道", type = OperateType.CREATE, targetType = "mall_channel")
    public Result<ChannelView> create(@Valid @RequestBody ChannelSaveCommand command,
                                      HttpServletRequest request) {
        return Result.ok(channelAppService.create(command, MallActorContext.from(request)));
    }

    /**
     * 编辑渠道。
     *
     * @param id 渠道 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 渠道视图
     */
    @PutMapping("/{id}")
    @RequirePermission(MallPermissions.CHANNEL_MANAGE)
    @AuditLog(operation = "编辑商城渠道", type = OperateType.UPDATE, targetType = "mall_channel")
    public Result<ChannelView> update(@PathVariable("id") Long id,
                                      @Valid @RequestBody ChannelSaveCommand command,
                                      HttpServletRequest request) {
        return Result.ok(channelAppService.update(id, command, MallActorContext.from(request)));
    }

    /**
     * 启用 / 停用渠道。
     *
     * @param id 渠道 ID
     * @param status 目标状态（ACTIVE / DISABLED）
     * @param request 当前请求
     * @return 空结果
     */
    @PutMapping("/{id}/status")
    @RequirePermission(MallPermissions.CHANNEL_MANAGE)
    @AuditLog(operation = "商城渠道启停", type = OperateType.UPDATE, targetType = "mall_channel")
    public Result<Void> changeStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") String status,
                                     HttpServletRequest request) {
        channelAppService.changeStatus(id, status, MallActorContext.from(request));
        return Result.ok();
    }
}
