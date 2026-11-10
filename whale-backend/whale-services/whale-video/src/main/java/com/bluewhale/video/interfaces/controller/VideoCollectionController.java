package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.command.CollectionQueryCommand;
import com.bluewhale.video.application.service.MovieCollectionAppService;
import com.bluewhale.video.application.view.MovieCollectionDetailView;
import com.bluewhale.video.application.view.MovieCollectionView;
import com.bluewhale.video.interfaces.support.PageParams;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电影片单用户侧接口：已上架片单列表与详情。
 *
 * <p>片单封面通过 {@code /video/collections/{id}/cover} 代理访问；
 * 片单内电影仍按内容自身可见性过滤。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoCollectionController {

    private final MovieCollectionAppService collectionAppService;

    /**
     * 已上架片单分页。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @return 片单分页
     */
    @GetMapping("/collections/page")
    @RequirePermission("sys:video:browse")
    public Result<PageResult<MovieCollectionView>> page(
            @ModelAttribute CollectionQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        command.setCurrent(PageParams.current(current, pageNum));
        command.setSize(PageParams.size(size, pageSize));
        return Result.ok(collectionAppService.publicPage(command));
    }

    /**
     * 已上架片单详情（含片单内可见电影）。
     *
     * @param id 片单 ID
     * @return 片单详情
     */
    @GetMapping("/collections/{id}")
    @RequirePermission("sys:video:browse")
    public Result<MovieCollectionDetailView> detail(@PathVariable Long id) {
        return Result.ok(collectionAppService.publicDetail(id));
    }
}
