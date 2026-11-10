package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.service.CatalogAppService;
import com.bluewhale.video.application.view.CategoryView;
import com.bluewhale.video.application.view.CreatorView;
import com.bluewhale.video.application.view.TagView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目录接口：分类、标签与创作者资料（浏览权限）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoCatalogController {

    private final CatalogAppService catalogAppService;

    /**
     * 查询启用分类。
     *
     * @return 分类列表
     */
    @GetMapping("/categories")
    @RequirePermission("sys:video:browse")
    public Result<List<CategoryView>> categories() {
        return Result.ok(catalogAppService.categories());
    }

    /**
     * 查询全部标签。
     *
     * @return 标签列表
     */
    @GetMapping("/tags")
    @RequirePermission("sys:video:browse")
    public Result<List<TagView>> tags() {
        return Result.ok(catalogAppService.tags());
    }

    /**
     * 查询创作者资料。
     *
     * @param id 创作者 ID
     * @return 创作者视图
     */
    @GetMapping("/creator/{id}")
    @RequirePermission("sys:video:browse")
    public Result<CreatorView> creator(@PathVariable Long id) {
        return Result.ok(catalogAppService.creator(id));
    }
}
