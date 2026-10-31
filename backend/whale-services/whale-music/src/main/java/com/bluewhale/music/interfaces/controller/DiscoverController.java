package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.music.application.command.SearchCommand;
import com.bluewhale.music.application.service.DiscoverAppService;
import com.bluewhale.music.application.view.HomeView;
import com.bluewhale.music.application.view.PublicSongView;
import com.bluewhale.music.application.view.SearchResultView;
import com.bluewhale.music.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发现与榜单接口：首页聚合、热门 / 新歌榜单、站内搜索。
 *
 * <p>全部接口要求 {@code sys:music:browse}；首页聚合走租户级 Redis 缓存，
 * 榜单与搜索只返回上架且公开的歌曲。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/music")
@RequiredArgsConstructor
public class DiscoverController {

    private final DiscoverAppService discoverAppService;

    /**
     * 首页聚合：热门歌曲、新歌、官方歌单与热门标签。
     *
     * @param request 当前请求（读取网关注入的用户头）
     * @return 首页聚合视图
     */
    @GetMapping("/home")
    @RequirePermission("sys:music:browse")
    public Result<HomeView> home(HttpServletRequest request) {
        return Result.ok(discoverAppService.home(UserContext.requireUserId(request)));
    }

    /**
     * 热门榜单。
     *
     * @param current 页码
     * @param size 每页条数
     * @return 歌曲分页结果
     */
    @GetMapping("/chart/hot")
    @RequirePermission("sys:music:browse")
    public Result<PageResult<PublicSongView>> hotChart(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return Result.ok(discoverAppService.hotChart(current, size));
    }

    /**
     * 新歌榜单。
     *
     * @param current 页码
     * @param size 每页条数
     * @return 歌曲分页结果
     */
    @GetMapping("/chart/new")
    @RequirePermission("sys:music:browse")
    public Result<PageResult<PublicSongView>> newChart(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return Result.ok(discoverAppService.newChart(current, size));
    }

    /**
     * 站内搜索；关键词为空时返回稳定的空结果。
     *
     * @param command 搜索命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 搜索结果
     */
    @GetMapping("/search")
    @RequirePermission("sys:music:browse")
    public Result<SearchResultView> search(@ModelAttribute SearchCommand command, HttpServletRequest request) {
        return Result.ok(discoverAppService.search(command, UserContext.requireUserId(request)));
    }
}
