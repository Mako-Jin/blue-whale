package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.command.SeriesQueryCommand;
import com.bluewhale.video.application.service.SeriesAppService;
import com.bluewhale.video.application.view.SeriesDetailView;
import com.bluewhale.video.application.view.SeriesView;
import com.bluewhale.video.interfaces.support.PageParams;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系列用户侧接口：系列详情与已发布集数列表。
 *
 * <p>仅已上架系列可见，未上架系列统一按不存在处理；集数只返回已发布且公开内容。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/video/v{version}", version = "1")
@RequiredArgsConstructor
public class VideoSeriesController {

    private final SeriesAppService seriesAppService;

    /**
     * 已上架系列分页（供视频列表展示系列入口与上传时选择系列）。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @return 系列分页结果
     */
    @GetMapping("/series/page")
    @RequirePermission("sys:video:browse")
    public Result<PageResult<SeriesView>> page(
            @ModelAttribute SeriesQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        command.setCurrent(PageParams.current(current, pageNum));
        command.setSize(PageParams.size(size, pageSize));
        return Result.ok(seriesAppService.publishedPage(command));
    }

    /**
     * 系列详情（含已发布集数）。
     *
     * @param id 系列 ID
     * @return 系列详情视图
     */
    @GetMapping("/series/{id}")
    @RequirePermission("sys:video:browse")
    public Result<SeriesDetailView> detail(@PathVariable Long id) {
        return Result.ok(seriesAppService.detail(id));
    }
}
