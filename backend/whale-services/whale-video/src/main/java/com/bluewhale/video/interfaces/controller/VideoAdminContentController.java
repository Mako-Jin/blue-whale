package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.command.ContentQueryCommand;
import com.bluewhale.video.application.command.TakedownCommand;
import com.bluewhale.video.application.service.ContentAppService;
import com.bluewhale.video.application.view.ContentView;
import com.bluewhale.video.interfaces.support.PageParams;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容运营接口：分页查询、发布、下架与版权下架。
 *
 * <p>全部接口要求视频运营权限码，写操作记录审计；版权下架后内容立即不可播放。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video/admin/content")
@RequiredArgsConstructor
public class VideoAdminContentController {

    private final ContentAppService contentAppService;

    /**
     * 运营内容分页（含草稿与已下线）。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @return 运营视图分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:video:content:list")
    public Result<PageResult<ContentView>> page(
            @ModelAttribute ContentQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        command.setCurrent(PageParams.current(current, pageNum));
        command.setSize(PageParams.size(size, pageSize));
        return Result.ok(contentAppService.adminPage(command));
    }

    /**
     * 运营发布内容。
     *
     * @param id 内容 ID
     * @return 运营视图
     */
    @PostMapping("/{id}/publish")
    @RequirePermission("sys:video:content:publish")
    @AuditLog(operation = "发布视频内容", type = OperateType.UPDATE, targetType = "vd_content", targetId = "#id")
    public Result<ContentView> publish(@PathVariable Long id) {
        return Result.ok(contentAppService.adminPublish(id));
    }

    /**
     * 运营下架内容。
     *
     * @param id 内容 ID
     * @return 运营视图
     */
    @PostMapping("/{id}/offline")
    @RequirePermission("sys:video:content:offline")
    @AuditLog(operation = "下架视频内容", type = OperateType.UPDATE, targetType = "vd_content", targetId = "#id")
    public Result<ContentView> offline(@PathVariable Long id) {
        return Result.ok(contentAppService.adminOffline(id));
    }

    /**
     * 运营版权下架内容（强制下线并阻断播放）。
     *
     * @param id 内容 ID
     * @param command 版权下架命令
     * @return 运营视图
     */
    @PostMapping("/{id}/takedown")
    @RequirePermission("sys:video:content:takedown")
    @AuditLog(operation = "版权下架视频内容", type = OperateType.UPDATE, targetType = "vd_content", targetId = "#id")
    public Result<ContentView> takedown(@PathVariable Long id, @Valid @RequestBody TakedownCommand command) {
        return Result.ok(contentAppService.adminTakedown(id, command.getReason()));
    }
}
