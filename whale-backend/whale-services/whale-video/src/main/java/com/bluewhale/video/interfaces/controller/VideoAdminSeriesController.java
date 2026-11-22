package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.command.SeriesQueryCommand;
import com.bluewhale.video.application.command.SeriesSaveCommand;
import com.bluewhale.video.application.service.SeriesAppService;
import com.bluewhale.video.application.view.SeriesView;
import com.bluewhale.video.interfaces.support.PageParams;
import com.bluewhale.video.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系列运营接口：分页、新增、编辑、删除、上架与下架。
 *
 * <p>全部接口要求系列管理权限码，写操作记录审计；存在已发布集数的系列
 * 不允许删除（需先移除或下架集数）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/video/v{version}/admin/series", version = "1")
@RequiredArgsConstructor
public class VideoAdminSeriesController {

    private final SeriesAppService seriesAppService;

    /**
     * 运营系列分页（含草稿与已下架）。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @return 系列分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:video:series:manage")
    public Result<PageResult<SeriesView>> page(
            @ModelAttribute SeriesQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        command.setCurrent(PageParams.current(current, pageNum));
        command.setSize(PageParams.size(size, pageSize));
        return Result.ok(seriesAppService.adminPage(command));
    }

    /**
     * 新增系列草稿。
     *
     * @param command 保存命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 系列视图
     */
    @PostMapping
    @RequirePermission("sys:video:series:manage")
    @AuditLog(operation = "新增视频系列", type = OperateType.CREATE, targetType = "vd_series")
    public Result<SeriesView> create(@Valid @RequestBody SeriesSaveCommand command, HttpServletRequest request) {
        return Result.ok(seriesAppService.create(command, UserContext.requireUserId(request)));
    }

    /**
     * 编辑系列。
     *
     * @param id 系列 ID
     * @param command 保存命令
     * @return 系列视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:video:series:manage")
    @AuditLog(operation = "编辑视频系列", type = OperateType.UPDATE, targetType = "vd_series", targetId = "#id")
    public Result<SeriesView> update(@PathVariable Long id, @Valid @RequestBody SeriesSaveCommand command) {
        return Result.ok(seriesAppService.update(id, command));
    }

    /**
     * 删除系列（存在已发布集数时拒绝）。
     *
     * @param id 系列 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:video:series:manage")
    @AuditLog(operation = "删除视频系列", type = OperateType.DELETE, targetType = "vd_series", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        seriesAppService.delete(id);
        return Result.ok();
    }

    /**
     * 上架系列。
     *
     * @param id 系列 ID
     * @return 系列视图
     */
    @PostMapping("/{id}/publish")
    @RequirePermission("sys:video:series:manage")
    @AuditLog(operation = "上架视频系列", type = OperateType.UPDATE, targetType = "vd_series", targetId = "#id")
    public Result<SeriesView> publish(@PathVariable Long id) {
        return Result.ok(seriesAppService.publish(id));
    }

    /**
     * 下架系列。
     *
     * @param id 系列 ID
     * @return 系列视图
     */
    @PostMapping("/{id}/offline")
    @RequirePermission("sys:video:series:manage")
    @AuditLog(operation = "下架视频系列", type = OperateType.UPDATE, targetType = "vd_series", targetId = "#id")
    public Result<SeriesView> offline(@PathVariable Long id) {
        return Result.ok(seriesAppService.offline(id));
    }
}
