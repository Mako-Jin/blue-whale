package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.command.CollectionQueryCommand;
import com.bluewhale.video.application.command.MovieCollectionItemCommand;
import com.bluewhale.video.application.command.MovieCollectionSaveCommand;
import com.bluewhale.video.application.service.MovieCollectionAppService;
import com.bluewhale.video.application.view.MovieCollectionDetailView;
import com.bluewhale.video.application.view.MovieCollectionView;
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
 * 电影片单运营接口：分页、详情、维护、上下架与加片 / 移除。
 *
 * <p>同一片单同一电影唯一（重复加片幂等），单片单最多 200 部；写操作记录审计，
 * 目标类型为 {@code vd_collection}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/video/v{version}/admin/collections", version = "1")
@RequiredArgsConstructor
public class VideoAdminCollectionController {

    private final MovieCollectionAppService collectionAppService;

    /**
     * 运营片单分页（含草稿与已下架）。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @return 片单分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:video:content:list")
    public Result<PageResult<MovieCollectionView>> page(
            @ModelAttribute CollectionQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        command.setCurrent(PageParams.current(current, pageNum));
        command.setSize(PageParams.size(size, pageSize));
        return Result.ok(collectionAppService.adminPage(command));
    }

    /**
     * 运营读取单个片单（含全部条目）。
     *
     * @param id 片单 ID
     * @return 片单详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:video:movie:manage")
    public Result<MovieCollectionDetailView> detail(@PathVariable Long id) {
        return Result.ok(collectionAppService.adminDetail(id));
    }

    /**
     * 新增片单草稿。
     *
     * @param command 保存命令
     * @return 片单视图
     */
    @PostMapping
    @RequirePermission("sys:video:content:add")
    @AuditLog(operation = "新增电影片单", type = OperateType.CREATE, targetType = "vd_collection")
    public Result<MovieCollectionView> create(@Valid @RequestBody MovieCollectionSaveCommand command) {
        return Result.ok(collectionAppService.create(command));
    }

    /**
     * 编辑片单。
     *
     * @param id 片单 ID
     * @param command 保存命令
     * @return 片单视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:video:content:edit")
    @AuditLog(operation = "编辑电影片单", type = OperateType.UPDATE,
            targetType = "vd_collection", targetId = "#id")
    public Result<MovieCollectionView> update(@PathVariable Long id,
                                              @Valid @RequestBody MovieCollectionSaveCommand command) {
        return Result.ok(collectionAppService.update(id, command));
    }

    /**
     * 删除片单（逻辑删除并物理删除条目）。
     *
     * @param id 片单 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:video:content:delete")
    @AuditLog(operation = "删除电影片单", type = OperateType.DELETE,
            targetType = "vd_collection", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        collectionAppService.delete(id);
        return Result.ok();
    }

    /**
     * 上架片单。
     *
     * @param id 片单 ID
     * @return 片单视图
     */
    @PostMapping("/{id}/publish")
    @RequirePermission("sys:video:movie:manage")
    @AuditLog(operation = "上架电影片单", type = OperateType.UPDATE,
            targetType = "vd_collection", targetId = "#id")
    public Result<MovieCollectionView> publish(@PathVariable Long id) {
        return Result.ok(collectionAppService.publish(id));
    }

    /**
     * 下架片单（不影响片单内电影状态）。
     *
     * @param id 片单 ID
     * @return 片单视图
     */
    @PostMapping("/{id}/offline")
    @RequirePermission("sys:video:movie:manage")
    @AuditLog(operation = "下架电影片单", type = OperateType.UPDATE,
            targetType = "vd_collection", targetId = "#id")
    public Result<MovieCollectionView> offline(@PathVariable Long id) {
        return Result.ok(collectionAppService.offline(id));
    }

    /**
     * 片单加片（重复加片幂等）。
     *
     * @param id 片单 ID
     * @param command 加片命令
     * @return 片单详情
     */
    @PostMapping("/{id}/item")
    @RequirePermission("sys:video:movie:manage")
    @AuditLog(operation = "电影片单加片", type = OperateType.UPDATE,
            targetType = "vd_collection", targetId = "#id")
    public Result<MovieCollectionDetailView> addItem(@PathVariable Long id,
                                                     @Valid @RequestBody
                                                     MovieCollectionItemCommand command) {
        return Result.ok(collectionAppService.addItem(id, command));
    }

    /**
     * 移除片单内电影（不存在时幂等）。
     *
     * @param id 片单 ID
     * @param contentId 电影内容 ID
     * @return 片单详情
     */
    @DeleteMapping("/{id}/item/{contentId}")
    @RequirePermission("sys:video:movie:manage")
    @AuditLog(operation = "电影片单移除电影", type = OperateType.UPDATE,
            targetType = "vd_collection", targetId = "#id")
    public Result<MovieCollectionDetailView> removeItem(@PathVariable Long id,
                                                        @PathVariable Long contentId) {
        return Result.ok(collectionAppService.removeItem(id, contentId));
    }
}
