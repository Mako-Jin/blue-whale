package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.command.MovieQueryCommand;
import com.bluewhale.video.application.command.MovieSaveCommand;
import com.bluewhale.video.application.command.TakedownCommand;
import com.bluewhale.video.application.service.MovieAppService;
import com.bluewhale.video.application.view.MovieAdminView;
import com.bluewhale.video.interfaces.support.PageParams;
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
 * 电影运营接口：分页、详情、入库、编辑、删除、上下架与版权下架。
 *
 * <p>入库必须登记授权来源 / 到期时间与视频文件；发布前校验授权仍在有效期。
 * 写操作均记录审计，目标类型为 {@code vd_movie}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video/admin/movies")
@RequiredArgsConstructor
public class VideoAdminMovieController {

    private final MovieAppService movieAppService;

    /**
     * 运营电影分页（含草稿与已下线）。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @return 电影运营视图分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:video:content:list")
    public Result<PageResult<MovieAdminView>> page(
            @ModelAttribute MovieQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        command.setCurrent(PageParams.current(current, pageNum));
        command.setSize(PageParams.size(size, pageSize));
        return Result.ok(movieAppService.adminPage(command));
    }

    /**
     * 运营读取单部电影（供编辑回填）。
     *
     * @param id 内容 ID
     * @return 电影运营视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:video:movie:manage")
    public Result<MovieAdminView> detail(@PathVariable Long id) {
        return Result.ok(movieAppService.adminDetail(id));
    }

    /**
     * 电影入库（创建内容草稿与元数据）。
     *
     * @param command 保存命令
     * @return 电影运营视图
     */
    @PostMapping
    @RequirePermission("sys:video:content:add")
    @AuditLog(operation = "电影入库", type = OperateType.CREATE, targetType = "vd_movie")
    public Result<MovieAdminView> create(@Valid @RequestBody MovieSaveCommand command) {
        return Result.ok(movieAppService.create(command));
    }

    /**
     * 编辑电影元数据与授权。
     *
     * @param id 内容 ID
     * @param command 保存命令
     * @return 电影运营视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:video:content:edit")
    @AuditLog(operation = "编辑电影", type = OperateType.UPDATE,
            targetType = "vd_movie", targetId = "#id")
    public Result<MovieAdminView> update(@PathVariable Long id,
                                         @Valid @RequestBody MovieSaveCommand command) {
        return Result.ok(movieAppService.update(id, command));
    }

    /**
     * 删除电影（内容逻辑删除 + 元数据物理删除）。
     *
     * @param id 内容 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:video:content:delete")
    @AuditLog(operation = "删除电影", type = OperateType.DELETE,
            targetType = "vd_movie", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        movieAppService.delete(id);
        return Result.ok();
    }

    /**
     * 发布电影（授权必须仍在有效期）。
     *
     * @param id 内容 ID
     * @return 电影运营视图
     */
    @PostMapping("/{id}/publish")
    @RequirePermission("sys:video:content:publish")
    @AuditLog(operation = "发布电影", type = OperateType.UPDATE,
            targetType = "vd_movie", targetId = "#id")
    public Result<MovieAdminView> publish(@PathVariable Long id) {
        return Result.ok(movieAppService.publish(id));
    }

    /**
     * 下架电影。
     *
     * @param id 内容 ID
     * @return 电影运营视图
     */
    @PostMapping("/{id}/offline")
    @RequirePermission("sys:video:content:offline")
    @AuditLog(operation = "下架电影", type = OperateType.UPDATE,
            targetType = "vd_movie", targetId = "#id")
    public Result<MovieAdminView> offline(@PathVariable Long id) {
        return Result.ok(movieAppService.offline(id));
    }

    /**
     * 版权下架电影（强制下线并阻断播放）。
     *
     * @param id 内容 ID
     * @param command 版权下架命令
     * @return 电影运营视图
     */
    @PostMapping("/{id}/takedown")
    @RequirePermission("sys:video:content:takedown")
    @AuditLog(operation = "版权下架电影", type = OperateType.UPDATE,
            targetType = "vd_movie", targetId = "#id")
    public Result<MovieAdminView> takedown(@PathVariable Long id,
                                           @Valid @RequestBody TakedownCommand command) {
        return Result.ok(movieAppService.takedown(id, command.getReason()));
    }
}
